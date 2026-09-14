(ns co.multiply.scoped-dev-bench
  "Short shared-JVM measurements for development; never a baseline-quality run."
  (:require [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [co.multiply.scoped :as s]
    [co.multiply.scoped-bench-runner :as runner]
    [co.multiply.scoped-runtime-bench :as bench])
  (:import [java.time Instant]))


(def default-options
  {:output "target/bench/dev/latest.edn"
   :allocation-iterations 10000
   :allocation-samples 2
   :criterium {:samples 8 :warmup-jit-period 1000000000
               :target-execution-time 25000000 :bootstrap-size 200 :overhead 0}})


(defn options
  [provided]
  (bench/ensure! (map? provided) "Options must be one EDN map")
  (doseq [k (keys provided)]
    (bench/ensure! (contains? (into (set (keys default-options))
                                [:cases :groups :reverse? :verify-only? :list? :label :baseline :compare]) k)
      (str "Unknown development benchmark option: " k)))
  (let [opts (-> (merge default-options provided)
               (assoc :criterium (assoc (merge (:criterium default-options) (:criterium provided)) :overhead 0)))]
    (runner/validate-options! (assoc opts :forks 1 :seed 0) [:thread-local])
    (bench/ensure! (and (string? (:output opts)) (not (str/blank? (:output opts))))
      ":output must be a nonempty path")
    (when (:baseline opts)
      (bench/ensure! (not= (.getCanonicalPath (io/file (:baseline opts)))
                           (.getCanonicalPath (io/file (:output opts))))
        "Baseline and output must be different files"))
    opts))


(defn environment
  []
  (assoc-in (bench/environment)
    [:suite-sha256 "bench/co/multiply/scoped_dev_bench.clj"]
    (bench/sha256 "bench/co/multiply/scoped_dev_bench.clj")))


(defn development-run!
  [run]
  (bench/ensure! (and (= 1 (:schema-version run))
                   (= :scoped-runtime-dev (:suite run))
                   (= :shared-jvm-dev (:protocol run)))
    "Expected development results; isolated and historical runs are incompatible"))


(def measurement-keys [:criterium :allocation-iterations :allocation-samples])


(defn compatible!
  [before after]
  (doseq [run [before after]] (development-run! run))
  (bench/ensure! (:complete? before) "Baseline run must be complete")
  (doseq [k [:suite-sha256 :backend :multi-release-version :multi-release-enabled?
             :java-version :java-vm :clojure-version
             :compiler-options :benchmark-jvm-arguments :arch :os :os-version :processors]]
    (bench/ensure! (= (get-in before [:environment k]) (get-in after [:environment k]))
      (str "Different environment or suite field: " k)))
  (bench/ensure! (= (select-keys (:options before) measurement-keys)
                    (select-keys (:options after) measurement-keys))
    "Different development measurement settings")
  ;; Shared call-site profiles depend on all preceding kernels, including order.
  (bench/ensure! (= (:case-order before) (:case-order after)) "Different case selection or order"))


(defn comparison
  [before after]
  (compatible! before after)
  (bench/ensure! (:complete? after) "Candidate run must be complete")
  (let [ids (:case-order before)
        a (into {} (map (juxt :id identity) (:results before)))
        b (into {} (map (juxt :id identity) (:results after)))]
    (doseq [run [before after]]
      (bench/ensure! (= ids (mapv :id (:results run))) "Incomplete or reordered results"))
    (->> ids
      (mapv (fn [id]
              (let [old (a id) new (b id)
                    old-bytes (get-in old [:allocation :bytes-per-call])
                    new-bytes (get-in new [:allocation :bytes-per-call])
                    ratio (/ (:mean-ns new) (:mean-ns old))]
                {:id id :before-ns (:mean-ns old) :after-ns (:mean-ns new) :ratio ratio
                 :delta-bytes (when (every? number? [old-bytes new-bytes]) (- new-bytes old-bytes))
                 :large-slowdown? (>= ratio 1.5)})))
      (sort-by :ratio >)
      vec)))


(defn print-comparison!
  [rows]
  (println "Case                              before ns   after ns     ratio   delta B/call")
  (doseq [{:keys [id before-ns after-ns ratio delta-bytes large-slowdown?]} rows]
    (printf "%-33s %9.2f %10.2f %9.2fx %14s%s%n"
      (subs (str id) 1) before-ns after-ns ratio
      (if (number? delta-bytes) (format "%+.2f" delta-bytes) "unavailable")
      (if large-slowdown? "  CHECK (>=1.5x)" "")))
  (println "Ratios are exploratory. CHECK highlights large slowdowns for investigation; repeat before drawing conclusions."))


(defn write-report!
  [output run]
  (spit (str output ".md")
    (str "# JVM development benchmarks\n\n"
      "Status: " (if (:complete? run) "complete" "in progress") ". Backend: `"
      (name (get-in run [:environment :backend])) "`. Completed " (count (:results run))
      "/" (count (:case-order run)) " cases.\n\n"
      "Exploratory, short warm-up, one shared JVM. Values include invocation/sink overhead. "
      "Use matching development runs to spot large regressions; confirm small differences with isolated forks.\n\n"
      "| Case | ns/call | B/call | Diagnostics |\n| --- | ---: | ---: | --- |\n"
      (str/join "\n"
        (for [{:keys [id mean-ns allocation] :as result} (:results run)]
          (str "| `" (subs (str id) 1) "` | " (runner/format-number mean-ns) " | "
            (if-let [bytes (:bytes-per-call allocation)] (runner/format-number bytes) "unavailable")
            " | " (str/join ", " (map name (runner/diagnostic-flags result))) " |")))
      "\n"
      (when-let [rows (:comparison run)]
        (str "\n## Comparison with " (get-in run [:options :baseline]) "\n\n"
          "After/before; CHECK means at least 1.5x slower, a prompt to investigate, not a test failure.\n\n"
          "| Case | Time ratio | Delta B/call | Signal |\n| --- | ---: | ---: | --- |\n"
          (str/join "\n"
            (for [{:keys [id ratio delta-bytes large-slowdown?]} rows]
              (str "| `" (subs (str id) 1) "` | " (runner/format-number ratio) "x | "
                (if (number? delta-bytes) (runner/format-number delta-bytes) "unavailable")
                " | " (when large-slowdown? "CHECK") " |"))) "\n")))))


(defn run-suite!
  [opts specs]
  (let [output (:output opts)
        baseline (when (:baseline opts) (edn/read-string (slurp (:baseline opts))))
        run (atom {:schema-version 1 :suite :scoped-runtime-dev :protocol :shared-jvm-dev
                   :quality :exploratory :complete? false :environment (environment) :options opts
                   :case-order (mapv :id specs) :started-at (str (Instant/now)) :results []})
        start (System/nanoTime)
        outer (s/current-scope)
        checkpoint! #(do (runner/save-edn! output @run) (write-report! output @run))]
    ;; Repeated development runs replace their own output, never another suite's artifacts.
    (when (.exists (io/file output)) (development-run! (edn/read-string (slurp output))))
    (when baseline (compatible! baseline @run))
    (println "Development suite:" (count specs) "cases in one JVM; backend" (get-in @run [:environment :backend]))
    (println "Short, exploratory measurements. Output:" output)
    (bench/verify! specs)
    (checkpoint!)
    (doseq [[i spec] (map-indexed vector specs)]
      (println (str "[" (inc i) "/" (count specs) "]"))
      (let [result (bench/run-case opts spec)]
        (bench/ensure! (= outer (s/current-scope)) (str "Measured case leaked its scope: " (:id spec)))
        (swap! run update :results conj result)
        (checkpoint!)))
    (swap! run assoc :complete? true :finished-at (str (Instant/now))
      :elapsed-seconds (/ (- (System/nanoTime) start) 1e9))
    (when baseline
      (swap! run assoc :comparison (comparison baseline @run))
      (print-comparison! (:comparison @run)))
    (checkpoint!)
    (printf "Completed %d cases in %.1f seconds. Report: %s.md%n"
      (count specs) (:elapsed-seconds @run) output)
    @run))


(defn -main
  [& args]
  (try
    (bench/ensure! (<= (count args) 1) "Provide at most one EDN options map")
    (let [opts (options (if (seq args) (edn/read-string (first args)) {}))]
      (if-let [paths (:compare opts)]
        (do
          (bench/ensure! (= 2 (count paths)) ":compare requires [before.edn after.edn]")
          (print-comparison! (apply comparison (map #(edn/read-string (slurp %)) paths))))
        (let [specs (bench/select-cases (bench/cases) opts)]
          (cond
            (:list? opts) (doseq [{:keys [id description]} specs] (println id "—" description))
            (:verify-only? opts) (bench/verify! specs)
            :else (run-suite! opts specs)))))
    (finally (shutdown-agents))))
