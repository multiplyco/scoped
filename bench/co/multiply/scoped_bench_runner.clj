(ns co.multiply.scoped-bench-runner
  "Isolated JVM forks, checkpointing and aggregation for the public runtime suite."
  (:require [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [co.multiply.scoped :as s]
    [co.multiply.scoped-runtime-bench :as bench]
    [criterium.core :as criterium])
  (:import [java.lang.management ManagementFactory]
    [java.nio.file Files StandardCopyOption]
    [java.time Instant]
    [java.util ArrayList Collections Locale Random]
    [java.util.concurrent TimeUnit]))


(def fingerprint-keys
  [:source-sha256 :suite-sha256 :deps-sha256 :clojure-version :java-version
   :java-vm :compiler-options :benchmark-jvm-arguments :os :os-version :arch :processors])


(defn fingerprint
  [environment]
  (select-keys environment fingerprint-keys))


(defn save-edn!
  [path value]
  (io/make-parents path)
  (let [target (.toPath (io/file path))
        temp (.toPath (io/file (str path ".tmp")))]
    (spit (.toFile temp) (with-out-str (pprint/pprint value)))
    (Files/move temp target (into-array StandardCopyOption
                              [StandardCopyOption/ATOMIC_MOVE StandardCopyOption/REPLACE_EXISTING]))))


(defn quality
  [options]
  (if (and (>= (:forks options) 5)
        (>= (get-in options [:criterium :warmup-jit-period]) 10000000000)
        (>= (get-in options [:criterium :samples]) 60)
        (>= (get-in options [:criterium :target-execution-time]) 1000000000))
    :full :exploratory))


(defn validate-options!
  [options backends]
  (doseq [k [:forks :allocation-iterations :allocation-samples]]
    (bench/ensure! (pos-int? (get options k)) (str k " must be a positive integer")))
  (doseq [k [:samples :warmup-jit-period :target-execution-time :bootstrap-size]]
    (bench/ensure! (pos-int? (get-in options [:criterium k])) (str k " must be a positive integer")))
  (bench/ensure! (>= (get-in options [:criterium :samples]) 4) "At least four samples are required")
  (bench/ensure! (integer? (:seed options)) ":seed must be an integer")
  (bench/ensure! (and (seq backends) (= (count backends) (count (distinct backends)))
                   (every? #{:scoped-value :thread-local} backends)) "Invalid or duplicate backends")
  (when (some #{:scoped-value} backends)
    (bench/ensure! (>= (.major (Runtime/version)) 25) "ScopedValue benchmarks require JDK 25+")))


(defn plan
  "Shuffle case order reproducibly per round; alternate backend order within each case."
  [ids backends forks seed]
  (vec
    (mapcat (fn [round]
              (let [shuffled (ArrayList. ids)]
                (Collections/shuffle shuffled (Random. (+ (long seed) round)))
                (mapcat (fn [index id]
                          (for [backend (if (even? (+ round index)) backends (reverse backends))
                                :let [stem (str (name backend) "/" (namespace id) "-" (name id)
                                             "/fork-" (inc round))]]
                            {:case-id id :backend backend :fork (inc round)
                             :file (str "forks/" stem ".edn") :log (str "logs/" stem ".log")}))
                  (range) shuffled)))
      (range forks))))


(defn child-command
  [options backend]
  (let [inherited (.getInputArguments (ManagementFactory/getRuntimeMXBean))
        args (remove #(or (str/starts-with? % "-Dclojure.basis=")
                        (str/starts-with? % "-Dco.multiply.scoped.force-fallback=")) inherited)]
    (vec (concat [(str (System/getProperty "java.home") "/bin/java")]
           args
           [(str "-Dco.multiply.scoped.force-fallback=" (= backend :thread-local))
            "-cp" (System/getProperty "java.class.path") "clojure.main"
            "-m" "co.multiply.scoped-runtime-bench" (pr-str options)]))))


(defn execute-child!
  [options backend log label]
  (io/make-parents log)
  (let [command (child-command options backend)
        process (-> (ProcessBuilder. ^java.util.List command)
                  (.redirectErrorStream true)
                  (.redirectOutput (io/file log))
                  (.start))
        start (System/nanoTime)]
    (try
      (loop []
        (when-not (.waitFor process 30 TimeUnit/SECONDS)
          (printf "  %s running (%.0f seconds); log %s%n" label (/ (- (System/nanoTime) start) 1e9) log)
          (flush)
          (recur)))
      (bench/ensure! (zero? (.exitValue process)) (str label " failed; inspect " log))
      (finally
        (when (.isAlive process)
          (.destroy process)
          (when-not (.waitFor process 5 TimeUnit/SECONDS) (.destroyForcibly process)))))))


(defn run-worker!
  [options]
  (bench/ensure! (keyword? (:case-id options)) "A worker must measure exactly one case")
  (let [environment (bench/environment)
        selected (bench/select-cases (bench/cases) {:cases [(:case-id options)]})
        before (s/current-scope)
        _ (bench/ensure! (= (:expected-fingerprint options) (fingerprint environment))
            "Sources or environment changed before the worker started")
        _ (bench/ensure! (= (:backend options) (:backend environment)) "Wrong worker backend")
        result (binding [criterium/*report-progress* true]
                 (bench/run-case options (first selected)))]
    (bench/ensure! (= before (s/current-scope)) "Measured case leaked its scope")
    (save-edn! (:output options)
      {:schema-version 2 :suite :scoped-runtime :protocol :isolated-case
       :complete? true :case-id (:case-id options) :backend (:backend options) :fork (:fork options)
       :environment environment :options (dissoc options :expected-fingerprint)
       :result result :finished-at (str (Instant/now))})))


(defn median
  [xs]
  (let [values (vec (sort xs)) n (count values) middle (quot n 2)]
    (if (odd? n) (nth values middle) (/ (+ (nth values (dec middle)) (nth values middle)) 2.0))))


(defn diagnostic-flags
  [result]
  (let [d (:diagnostics result)]
    (cond-> []
      (not (<= 0.95 (:last-first-ratio d) 1.05)) (conj :sample-drift)
      (not (<= 0.8 (:measurement-warmup-ratio d) 1.2)) (conj :warmup-shift)
      (pos? (get-in d [:sampling-delta :compilation-ms] 0)) (conj :compilation-during-sampling)
      (pos? (get-in d [:sampling-delta :gc-count] 0)) (conj :gc-during-sampling))))


(defn aggregate
  [completed]
  (vec
    (for [[[backend id] jobs] (sort-by key (group-by (juxt :backend :case-id) completed))
          :let [means (map #(get-in % [:result :mean-ns]) jobs)
                allocations (keep #(get-in % [:result :allocation :bytes-per-call]) jobs)]]
      {:backend backend :id id :fork-count (count jobs)
       :mean-ns (/ (reduce + means) (count means))
       :median-ns (median means) :range-ns [(apply min means) (apply max means)]
       :allocation (when (= (count allocations) (count jobs))
                     {:median-bytes-per-call (median allocations)
                      :range-bytes-per-call [(apply min allocations) (apply max allocations)]})
       :wide-fork-spread? (> (/ (apply max means) (apply min means)) 1.2)
       :flagged-forks (count (filter #(seq (diagnostic-flags (:result %))) jobs))})))


(defn format-number
  [x]
  (String/format Locale/ROOT "%.2f" (to-array [(double x)])))


(defn write-report!
  [output manifest]
  (spit (str output ".md")
    (str "# Isolated JVM runtime measurements\n\n"
      "Status: " (if (:complete? manifest) "complete" "in progress") ". Completed "
      (count (:completed manifest)) " / " (count (:plan manifest)) " forks.\n\n"
      "Quality profile: `" (name (:quality manifest)) "`. Warm-up: "
      (/ (get-in manifest [:options :criterium :warmup-jit-period]) 1e9)
      " seconds; " (get-in manifest [:options :criterium :samples]) " samples targeting "
      (/ (get-in manifest [:options :criterium :target-execution-time]) 1e9) " seconds each.\n\n"
      "Every measured fork runs one case. Values below summarize fork means; ranges are not confidence intervals. "
      "Flags identify drift, warm-up shifts or compilation/GC during sampling; they are diagnostic signals, not automatic exclusions. "
      "All forks remain included. The EDN manifest links raw results and logs.\n\n"
      "| Backend / case | Forks | Median ns/call | Min–max ns/call | Median B/call | Flagged forks |\n"
      "| --- | ---: | ---: | ---: | ---: | ---: |\n"
      (str/join "\n"
        (for [{:keys [backend id fork-count median-ns range-ns allocation flagged-forks]} (:results manifest)]
          (str "| " (name backend) " / `" (subs (str id) 1) "` | " fork-count " | "
            (format-number median-ns) " | " (str/join "–" (map format-number range-ns)) " | "
            (if allocation (format-number (:median-bytes-per-call allocation)) "unavailable") " | " flagged-forks " |")))
      "\n")))


(defn worker-options
  [options environment job output]
  (merge (select-keys options [:criterium :allocation-iterations :allocation-samples :label])
    (select-keys job [:case-id :backend :fork])
    {:worker? true :output output :expected-fingerprint (fingerprint environment)}))


(defn validate-worker!
  [worker job environment options]
  (bench/ensure! (and (= 2 (:schema-version worker)) (= :isolated-case (:protocol worker)) (:complete? worker))
    "Incomplete or incompatible worker result")
  (bench/ensure! (= (select-keys job [:case-id :backend :fork])
                    (select-keys worker [:case-id :backend :fork])) "Worker result does not match its job")
  (bench/ensure! (= (fingerprint environment) (fingerprint (:environment worker))) "Worker fingerprint changed")
  (bench/ensure! (= (select-keys options [:criterium :allocation-iterations :allocation-samples])
                    (select-keys (:options worker) [:criterium :allocation-iterations :allocation-samples]))
    "Worker measurement settings changed")
  (let [result (:result worker) c (:criterium result)]
    (bench/ensure! (= (:case-id job) (:id result)) "Wrong measured case")
    (bench/ensure! (and (number? (:mean-ns result)) (Double/isFinite (double (:mean-ns result)))
                     (pos? (:mean-ns result))) "Invalid measured mean")
    (bench/ensure! (= (get-in options [:criterium :samples]) (count (:samples c))) "Incomplete samples")
    (bench/ensure! (>= (:warmup-time c) (get-in options [:criterium :warmup-jit-period])) "Incomplete warm-up")))


(defn run-forks!
  [options]
  (let [environment (bench/environment)
        backends (or (:backends options) [(:backend environment)])
        selected (bench/select-cases (bench/cases) options)
        ids (mapv :id selected)
        _ (validate-options! options backends)
        jobs (plan ids backends (:forks options) (:seed options))
        output (.getCanonicalPath (io/file (:output options)))
        directory (.getParentFile (io/file output))
        existing? (.exists (io/file output))
        _ (bench/ensure! (or (not existing?) (:resume? options)) "Output exists; use :resume? true or a new output path")
        saved (when existing? (edn/read-string (slurp output)))
        settings (select-keys options [:forks :seed :criterium :allocation-iterations :allocation-samples])
        manifest (atom (or saved
                         {:schema-version 2 :suite :scoped-runtime :protocol :isolated-forks
                          :quality (quality options) :environment environment :options options
                          :started-at (str (Instant/now)) :plan jobs :completed [] :results [] :complete? false}))
        checkpoint! #(do (save-edn! output @manifest) (write-report! output @manifest))]
    (when saved
      (bench/ensure! (= :isolated-forks (:protocol saved)) "Incompatible resume protocol")
      (bench/ensure! (= (fingerprint environment) (fingerprint (:environment saved))) "Cannot resume after source/environment changes")
      (bench/ensure! (= settings (select-keys (:options saved) (keys settings))) "Cannot resume with different settings")
      (bench/ensure! (= jobs (:plan saved)) "Cannot resume with a different case/backend schedule"))
    (when-not saved
      (bench/ensure! (not-any? #(.exists (io/file directory (:file %))) jobs)
        "Worker files already exist; choose a new output directory or resume their manifest"))
    (checkpoint!)
    ;; Verification has its own JVM and cannot warm any measured case or harness.
    (doseq [backend backends]
      (execute-child! {:verify-only? true} backend (io/file directory (str "verify-" (name backend) ".log"))
        (str "verification / " (name backend))))
    (println "Starting" (count jobs) "isolated forks; quality" (:quality @manifest) "; output" output)
    (flush)
    (doseq [[index job] (map-indexed vector jobs)]
      (let [path (io/file directory (:file job))
            log (io/file directory (:log job))
            label (str (inc index) "/" (count jobs) " " (:backend job) " " (:case-id job) " fork " (:fork job))]
        (println label)
        (flush)
        (when-not (.exists path)
          (execute-child! (worker-options options environment job (.getCanonicalPath path)) (:backend job) log label))
        (let [worker (edn/read-string (slurp path))]
          (validate-worker! worker job environment options)
          (swap! manifest update :completed
            (fn [completed]
              (conj (vec (remove #(= (:file %) (:file job)) completed))
                (assoc job :result (:result worker)))))
          (swap! manifest assoc :results (aggregate (:completed @manifest)))
          (checkpoint!)
          (println "  mean" (format-number (get-in worker [:result :mean-ns])) "ns; flags"
            (diagnostic-flags (:result worker)))
          (flush))))
    (swap! manifest assoc :complete? true :finished-at (str (Instant/now)))
    (checkpoint!)
    (println "Completed baseline:" output)))


(defn compare-runs!
  [[before-path after-path :as paths]]
  (bench/ensure! (= 2 (count paths)) "Provide two result paths")
  (let [before (edn/read-string (slurp before-path)) after (edn/read-string (slurp after-path))]
    (if (= 1 (:schema-version before) (:schema-version after))
      (bench/compare-results! paths)
      (do
        (bench/ensure! (= :isolated-forks (:protocol before) (:protocol after)) "Compare results from the same isolated-fork protocol")
        (bench/ensure! (and (:complete? before) (:complete? after)) "Both runs must be complete")
        (doseq [field [:suite-sha256 :java-version :clojure-version :compiler-options
                       :benchmark-jvm-arguments :arch :os :os-version :processors]]
          (bench/ensure! (= (get-in before [:environment field]) (get-in after [:environment field]))
            (str "Different environment or suite field: " field)))
        (bench/ensure! (= (select-keys (:options before) [:forks :seed :criterium :allocation-iterations :allocation-samples])
                          (select-keys (:options after) [:forks :seed :criterium :allocation-iterations :allocation-samples]))
          "Different measurement settings")
        (let [index #(into {} (map (juxt (juxt :backend :id) identity) (:results %)))
              a (index before) b (index after)]
          (bench/ensure! (= (set (keys a)) (set (keys b))) "Different case/backend selection")
          (println "Backend / case                        before median ns   after median ns   ratio")
          (doseq [k (sort (keys a))]
            (println k (format-number (:median-ns (a k))) (format-number (:median-ns (b k)))
              (format-number (/ (:median-ns (b k)) (:median-ns (a k))))))
          (println "Ratios use fork medians. Inspect fork ranges and diagnostics before drawing conclusions."))))))
