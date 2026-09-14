(ns co.multiply.scoped-task-slot-bench
  "Compare per-task map updates with a separate ScopedValue on Java 25."
  (:require [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [criterium.core :as criterium])
  (:import [co.multiply.scoped.experiment TaskSlotBench$Kernel]
    [java.lang.management ManagementFactory]
    [java.security MessageDigest]
    [java.time Instant]))


(def models ["map" "split" "split-cached"])


(def screen-options
  {:samples 16 :warmup-jit-period 3000000000 :target-execution-time 50000000
   :bootstrap-size 300 :overhead 0})


(def default-specs
  (vec (for [context-size [0 7 8 32] mode [:nested :handoff]]
         {:context-size context-size :depth 10 :mode mode})))


(def source-files
  ["bench/experiments/task-slot/java/co/multiply/scoped/experiment/TaskSlotBench.java"
   "bench/experiments/task-slot/clj/co/multiply/scoped_task_slot_bench.clj"
   "deps.edn" "build.clj"])


(defn ensure!
  [condition message]
  (when-not condition (throw (ex-info message {}))))


(defn sha256
  [file]
  (apply str (map #(format "%02x" (bit-and 255 %))
               (.digest (MessageDigest/getInstance "SHA-256")
                 (java.nio.file.Files/readAllBytes (.toPath (io/file file)))))))


(defn environment
  []
  {:java-runtime (System/getProperty "java.runtime.version")
   :java-vm (System/getProperty "java.vm.name")
   :clojure-version (clojure-version) :criterium-version "0.4.6"
   :os (System/getProperty "os.name") :arch (System/getProperty "os.arch")
   :jvm-arguments (vec (remove #(str/starts-with? % "-Dclojure.basis=")
                         (.getInputArguments (ManagementFactory/getRuntimeMXBean))))
   :source-sha256 (into (sorted-map) (map (juxt identity sha256) source-files))
   :class-sha256 (into (sorted-map)
                   (for [f (file-seq (io/file "target/bench/task-slot/classes"))
                         :when (and (.isFile f) (str/ends-with? (.getName f) ".class"))]
                     [(.getPath f) (sha256 f)]))})


(defn kernel
  [{:keys [spec model]}]
  (TaskSlotBench$Kernel. model (name (:mode spec)) (:context-size spec) (:depth spec)))


(defn result-file
  [output {:keys [spec model fork]}]
  (io/file output (str (name (:mode spec)) "-c" (:context-size spec) "-d" (:depth spec)
                    "--" model "--f" fork ".edn")))


(defn plain-result
  [result]
  (walk/postwalk #(if (record? %) (into {} %) %) (dissoc result :results)))


(defn measure
  [f protocol]
  (case protocol
    :screen (let [result (criterium/benchmark* f screen-options)]
              (criterium/report-result result :verbose)
              (plain-result result))
    :full (let [captured (atom nil) report criterium/report-result]
            (with-redefs [criterium/report-result
                          (fn [result & options]
                            (reset! captured (plain-result result))
                            (apply report result options))]
              (criterium/bench (f) :verbose))
            @captured)))


(defn allocations
  [f]
  (let [bean (ManagementFactory/getThreadMXBean)]
    (ensure! (and (instance? com.sun.management.ThreadMXBean bean)
               (.isThreadAllocatedMemorySupported ^com.sun.management.ThreadMXBean bean))
      "Thread allocation measurements unsupported")
    (let [bean ^com.sun.management.ThreadMXBean bean
          thread (.threadId (Thread/currentThread)) iterations 30000]
      (.setThreadAllocatedMemoryEnabled bean true)
      (let [samples (mapv (fn [_]
                            (let [before (.getThreadAllocatedBytes bean thread)]
                              (criterium/execute-expr iterations f)
                              (- (.getThreadAllocatedBytes bean thread) before))) (range 3))]
        {:iterations iterations :samples-bytes samples
         :bytes-per-call (/ (double (reduce + samples)) (* iterations 3))}))))


(defn worker!
  [{:keys [output job protocol]}]
  (let [file (result-file output job) env (environment) k (kernel job)
        f #(.run ^TaskSlotBench$Kernel k)]
    (ensure! (not (.exists file)) "Result already exists")
    (.verify ^TaskSlotBench$Kernel k)
    (println "Task-slot comparison" protocol job)
    (let [result (assoc job :schema-version 1 :protocol protocol :environment env
                   :parent-class (.parentClass ^TaskSlotBench$Kernel k)
                   :context-class (.contextClass ^TaskSlotBench$Kernel k)
                   :measurement (measure f protocol) :allocation (allocations f)
                   :finished-at (str (Instant/now)))]
      (ensure! (= env (environment)) "Sources changed during measurement")
      (spit file (pr-str result)))))


(defn write-summary!
  [output plan]
  (let [rows (vec (keep (fn [job]
                          (let [file (result-file output job)]
                            (when (.exists file) (edn/read-string (slurp file))))) (:jobs plan)))]
    (doseq [row rows]
      (ensure! (and (= (:environment plan) (:environment row))
                 (= (:protocol plan) (:protocol row))) "Incompatible worker result"))
    (spit (io/file output "summary.md")
      (str "# Separate task slot\n\nProtocol: `" (name (:protocol plan)) "`. Completed "
        (count rows) "/" (count (:jobs plan)) " fresh JVMs. Time and bytes are per complete sequence.\n\n"
        "| Mode | App entries | Depth | Model | Fork | ns/sequence | B/sequence |\n"
        "| --- | ---: | ---: | --- | ---: | ---: | ---: |\n"
        (apply str (for [{:keys [spec model fork measurement allocation]} rows]
                     (format "| %s | %d | %d | %s | %d | %.2f | %.2f |\n"
                       (name (:mode spec)) (:context-size spec) (:depth spec) model fork
                       (* 1e9 (first (:mean measurement))) (:bytes-per-call allocation))))))
    rows))


(defn run-suite!
  [{:keys [output protocol specs forks selected-models resume? verify-only?]
    :or {output "target/bench/task-slot/screen" protocol :screen forks 2}}]
  (ensure! (= 25 (.feature (Runtime/version))) "Measure this experiment on actual JDK 25")
  (ensure! (#{:screen :full} protocol) "Protocol must be :screen or :full")
  (ensure! (and (integer? forks) (pos? forks)) "Forks must be positive")
  (let [specs (vec (or specs default-specs))
        selected (vec (or selected-models models))
        _ (ensure! (and (seq specs) (= (count specs) (count (distinct specs)))
                     (seq selected) (= (count selected) (count (distinct selected)))
                     (every? (set models) selected)) "Select distinct cases and known models")
        jobs (vec (for [fork (range 1 (inc forks)) [index spec] (map-indexed vector specs)
                        model (if (odd? (+ index fork)) selected (reverse selected))]
                    {:spec spec :model model :fork fork}))
        plan {:schema-version 1 :protocol protocol :environment (environment)
              :settings (if (= protocol :screen) screen-options criterium/*default-benchmark-opts*) :jobs jobs}
        manifest (io/file output "plan.edn")]
    (doseq [spec specs model selected]
      (.verify ^TaskSlotBench$Kernel (kernel {:spec spec :model model})))
    (println "Verified" (count specs) "workloads with" (count selected) "models.")
    (when-not verify-only?
      (if (.exists manifest)
        (ensure! (and resume? (= plan (edn/read-string (slurp manifest))))
          "Existing run requires identical plan and :resume? true")
        (do (io/make-parents manifest) (spit manifest (pr-str plan))))
      (doseq [[index job] (map-indexed vector jobs)]
        (let [file (result-file output job)]
          (when-not (.exists file)
            (let [log (io/file (str file ".log"))
                  args (vec (concat [(str (System/getProperty "java.home") "/bin/java")]
                              (get-in plan [:environment :jvm-arguments])
                              ["-cp" (System/getProperty "java.class.path") "clojure.main"
                               "-m" "co.multiply.scoped-task-slot-bench"
                               (pr-str {:worker? true :output output :job job :protocol protocol})]))]
              (println (str (inc index) "/" (count jobs)) job)
              (flush)
              (let [process (-> (ProcessBuilder. ^java.util.List args)
                              (.redirectErrorStream true) (.redirectOutput log) (.start))]
                (ensure! (zero? (.waitFor process)) (str "Worker failed: " log)))))
          (let [rows (write-summary! output plan)
                row (some #(when (= job (select-keys % [:spec :model :fork])) %) rows)]
            (printf "  %.2f ns/sequence, %.2f B/sequence%n"
              (* 1e9 (first (get-in row [:measurement :mean])))
              (get-in row [:allocation :bytes-per-call]))
            (flush)))))))


(defn -main
  [& [arg]]
  (try
    (let [options (if arg (edn/read-string arg) {})]
      (if (:worker? options) (worker! options) (run-suite! options)))
    (finally (shutdown-agents))))
