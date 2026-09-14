(ns co.multiply.scoped-batch-bench
  "Locate the persistent/transient crossover without classifying updates in the hot path."
  (:require [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [co.multiply.scoped-representation-bench :as representation]
    [criterium.core :as criterium])
  (:import [co.multiply.scoped.experiment BatchBench$Kernel]
    [java.lang.management ManagementFactory]
    [java.time Instant]))


(def screen-options
  {:samples 12 :warmup-jit-period 3000000000 :target-execution-time 50000000
   :bootstrap-size 300 :overhead 0})


(def models ["clojure-persistent" "clojure-transient"])


(def default-specs
  (into (vec (for [parent [0 4 8 32]
                   batch [2 4 8 16]
                   workload [:add :override :mixed]
                   :when (case workload
                           :add true
                           :override (<= batch parent)
                           :mixed (<= (quot (inc batch) 2) parent))]
               {:parent-size parent :batch-size batch :workload workload}))
    (for [workload [:half-skip :all-skip :duplicate :same-value]]
      {:parent-size 32 :batch-size 8 :workload workload})))


(defn case-id
  [{:keys [parent-size batch-size workload]}]
  (str (name workload) "-p" parent-size "-k" batch-size))


(defn result-file
  [output {:keys [spec model fork]}]
  (io/file output (str (case-id spec) "--" model "--f" fork ".edn")))


(defn environment
  []
  (let [extra ["bench/experiments/representation/java/co/multiply/scoped/experiment/BatchBench.java"
               "bench/experiments/representation/clj/co/multiply/scoped_batch_bench.clj"]
        classes (filter #(and (.isFile %) (str/ends-with? (.getName %) ".class"))
                  (file-seq (io/file "target/bench/representation/classes")))]
    (-> (representation/environment)
      (update :source-sha256 into (map (juxt identity representation/sha256) extra))
      (assoc :all-class-sha256 (into (sorted-map)
                                 (map (fn [file] [(.getPath file) (representation/sha256 file)]) classes))))))


(defn kernel
  [{:keys [spec model]}]
  (let [{:keys [parent-size batch-size workload]} spec]
    (BatchBench$Kernel. model parent-size batch-size (name workload))))


(defn plain-result
  [result]
  (walk/postwalk #(if (record? %) (into {} %) %) (dissoc result :results)))


(defn measure
  [f protocol]
  (case protocol
    :screen (let [result (criterium/benchmark* f screen-options)]
              (criterium/report-result result :verbose)
              (plain-result result))
    :full (let [captured (atom nil)
                report criterium/report-result]
            (with-redefs [criterium/report-result
                          (fn [result & options]
                            (reset! captured (plain-result result))
                            (apply report result options))]
              (criterium/bench (f) :verbose))
            @captured)))


(defn allocations
  [f]
  (let [bean (ManagementFactory/getThreadMXBean)]
    (if (and (instance? com.sun.management.ThreadMXBean bean)
          (.isThreadAllocatedMemorySupported ^com.sun.management.ThreadMXBean bean))
      (let [bean ^com.sun.management.ThreadMXBean bean
            thread (.getId (Thread/currentThread))
            iterations 100000]
        (.setThreadAllocatedMemoryEnabled bean true)
        (let [samples (mapv (fn [_]
                              (let [before (.getThreadAllocatedBytes bean thread)]
                                (criterium/execute-expr iterations f)
                                (- (.getThreadAllocatedBytes bean thread) before))) (range 3))]
          {:iterations iterations :samples-bytes samples
           :bytes-per-call (/ (double (reduce + samples)) (* iterations 3))}))
      {:status :unsupported})))


(defn worker!
  [{:keys [output job protocol]}]
  (let [file (result-file output job)
        _ (representation/ensure! (not (.exists file)) "Result already exists")
        env (environment)
        k (kernel job)
        f #(.run ^BatchBench$Kernel k)]
    (.verify ^BatchBench$Kernel k)
    (println "Batch crossover" protocol job)
    (println "Settings:" (if (= protocol :screen) screen-options criterium/*default-benchmark-opts*))
    (let [result (assoc job :schema-version 1 :suite :assoc-crossover :protocol protocol
                   :environment env :finished-at (str (Instant/now))
                   :parent-class (.parentClass ^BatchBench$Kernel k)
                   :result-size (.resultSize ^BatchBench$Kernel k)
                   :measurement (measure f protocol) :allocation (allocations f))]
      (representation/ensure! (= env (environment)) "Sources or classes changed during measurement")
      (spit file (pr-str (assoc result :finished-at (str (Instant/now))))))
    (println "Saved" (str file))))


(defn write-summary!
  [output plan]
  (let [rows (vec (keep (fn [job]
                          (let [file (result-file output job)]
                            (when (.exists file) (edn/read-string (slurp file))))) (:jobs plan)))]
    (doseq [row rows]
      (representation/ensure! (and (= (:environment plan) (:environment row))
                                (= (:protocol plan) (:protocol row))) "Incompatible worker result"))
    (spit (io/file output "summary.md")
      (str "# Persistent/transient batch crossover\n\n"
        "Protocol: `" (name (:protocol plan)) "`. Completed " (count rows) "/" (count (:jobs plan))
        " isolated JVM runs. Screen times include overhead; full times use normal Criterium subtraction.\n\n"
        "| Workload | Parent | Batch | Model | Fork | ns/op | B/op |\n"
        "| --- | ---: | ---: | --- | ---: | ---: | ---: |\n"
        (apply str (for [{:keys [spec model fork measurement allocation]} rows]
                     (format "| %s | %d | %d | %s | %d | %.2f | %.2f |\n"
                       (name (:workload spec)) (:parent-size spec) (:batch-size spec) model fork
                       (* 1e9 (first (:mean measurement))) (:bytes-per-call allocation))))))
    rows))


(defn run-suite!
  [{:keys [output protocol specs forks resume? verify-only?]
    :or {output "target/bench/representation/batch-screen" protocol :screen forks 1}}]
  (representation/ensure! (#{:screen :full} protocol) "Protocol must be :screen or :full")
  (representation/ensure! (and (integer? forks) (pos? forks)) "Forks must be positive")
  (let [specs (vec (or specs default-specs))
        _ (representation/ensure! (and (seq specs) (= (count specs) (count (distinct specs))))
            "Select distinct nonempty cases")
        _ (doseq [spec specs model models] (.verify ^BatchBench$Kernel (kernel {:spec spec :model model})))
        jobs (vec (for [fork (range 1 (inc forks))
                        [index spec] (map-indexed vector specs)
                        model (if (odd? (+ index fork)) models (reverse models))]
                    {:spec spec :model model :fork fork}))
        plan {:schema-version 1 :suite :assoc-crossover :environment (environment)
              :protocol protocol :settings (if (= protocol :screen) screen-options criterium/*default-benchmark-opts*)
              :jobs jobs}
        manifest (io/file output "plan.edn")]
    (println "Verified" (count specs) "workloads for both update strategies.")
    (when-not verify-only?
      (if (.exists manifest)
        (representation/ensure! (and resume? (= plan (edn/read-string (slurp manifest))))
          "Existing run requires identical plan and :resume? true")
        (do (io/make-parents manifest) (spit manifest (pr-str plan))))
      (doseq [[index job] (map-indexed vector jobs)]
        (let [file (result-file output job)]
          (if (.exists file)
            (representation/ensure! resume? "Result already exists")
            (let [log (io/file (str file ".log"))
                  args (vec (concat [(str (System/getProperty "java.home") "/bin/java")]
                              (get-in plan [:environment :jvm-arguments])
                              ["-cp" (System/getProperty "java.class.path") "clojure.main"
                               "-m" "co.multiply.scoped-batch-bench"
                               (pr-str {:worker? true :job job :protocol protocol :output output})]))]
              (println (str (inc index) "/" (count jobs)) (case-id (:spec job)) (:model job) "fork" (:fork job))
              (flush)
              (let [process (-> (ProcessBuilder. ^java.util.List args)
                              (.redirectErrorStream true) (.redirectOutput log) (.start))]
                (representation/ensure! (zero? (.waitFor process)) (str "Worker failed: " log)))))
          (let [rows (write-summary! output plan)
                row (some #(when (= job (select-keys % [:spec :model :fork])) %) rows)]
            (printf "  %.2f ns/op, %.2f B/op%n"
              (* 1e9 (first (get-in row [:measurement :mean])))
              (get-in row [:allocation :bytes-per-call]))
            (flush))))
      (println "Completed:" (str (io/file output "summary.md"))))))


(defn -main
  [& [arg]]
  (try
    (let [options (if arg (edn/read-string arg) {})]
      (if (:worker? options) (worker! options) (run-suite! options)))
    (finally (shutdown-agents))))
