(ns co.multiply.scoped-extend-bench
  "Compare argument-array transport with unrolled scope-map updates."
  (:require [co.multiply.scoped-extend-kernels :as kernels]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [criterium.core :as criterium])
  (:import [co.multiply.scoped.experiment ExtendKernel]
    [co.multiply.scoped MapUpdates ScopedRuntime]
    [java.lang.management ManagementFactory]
    [java.security MessageDigest]
    [java.time Instant]))


(def models [:array :helpers :inline])


(def screen-options
  {:samples 16 :warmup-jit-period 3000000000 :target-execution-time 50000000
   :bootstrap-size 300 :overhead 0})


(def default-specs
  (vec (for [n [11 20] workload [:add :override]]
         {:bindings n :parent-size (if (= workload :add) 8 32) :workload workload})))


(def source-files
  ["java/co/multiply/scoped/experiment/ExtendKernel.java"
   "clj/co/multiply/scoped_extend_kernels.clj"
   "clj/co/multiply/scoped_extend_bench.clj"
   "../../../src/co/multiply/scoped/impl.cljc"
   "../../../src/co/multiply/scoped/helpers.cljc"
   "../../../src/co/multiply/scoped/MapUpdates.java"
   "../../../src-java/jdk25/co/multiply/scoped/ScopedRuntime.java"
   "deps.edn" "run.sh"])


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
                   (for [f (file-seq (io/file "../../../target/bench/extend-scope/classes"))
                         :when (and (.isFile f) (str/ends-with? (.getName f) ".class"))]
                     [(.getPath f) (sha256 f)]))})


(defn binding-keys
  [n]
  (mapv #(ns-resolve 'co.multiply.scoped-extend-kernels (symbol (str "key" %))) (range n)))


(defn fixtures
  [{:keys [bindings parent-size workload]}]
  (let [parent-keys (mapv #(ns-resolve 'co.multiply.scoped-extend-kernels
                             (symbol (str (if (= workload :add) "ambient" "key") %)))
                      (range parent-size))]
    (mapv (fn [i]
            {:parent (reduce (fn [m [k v]] (assoc m k v)) {}
                       (map vector parent-keys
                         (mapv #(case (mod (+ i %) 8) 0 nil 1 false (Object.))
                           (range parent-size))))
             :values (object-array (repeatedly bindings #(Object.)))})
      (range 128))))


(defn expected-map
  [parent keys values]
  (reduce (fn [m [k v]] (if (identical? MapUpdates/SKIP v) m (assoc m k v)))
    parent (map vector keys values)))


(defn verify!
  [spec model]
  (let [op (kernels/operation model (:bindings spec)) ks (binding-keys (:bindings spec))]
    (doseq [{:keys [parent values]} (fixtures spec)]
      (let [snapshot (vec parent)
            result (op parent values)]
        (ensure! (= result (expected-map parent ks values)) "Incorrect map update")
        (ensure! (= snapshot (vec parent)) "Parent changed")
        (doseq [vs [(object-array (repeat (count ks) MapUpdates/SKIP))
                    (object-array (take (count ks) (cycle [nil false MapUpdates/SKIP])))]]
          (ensure! (= (op parent vs) (expected-map parent ks vs)) "SKIP/nil/false changed"))
        (op parent (object-array (repeat (count ks) :later)))
        (ensure! (= result (expected-map parent ks values)) "Retained result changed"))))
  (binding [kernels/*events* (atom [])]
    (let [n (:bindings spec)
          keys (mapv #(kernels/key-symbol (mod % 3)) (range n))
          values (mapv (fn [i] `(do (swap! kernels/*events* conj ~i) ~i)) (range n))
          f (kernels/custom-operation model keys
              `(do (swap! kernels/*events* conj :parent) {}) values)
          actual (f)]
      (ensure! (= actual (expected-map {} (take n (cycle (binding-keys 3))) (range n)))
        "Ordered duplicate updates changed")
      (ensure! (= @kernels/*events* (into [:parent] (range n))) "Evaluation order changed")
      (reset! kernels/*events* [])
      (let [bad-parent (kernels/custom-operation model keys
                         `(do (swap! kernels/*events* conj :parent) :invalid) values)]
        (ensure! (try (bad-parent) false (catch ClassCastException _ true)) "Expected invalid parent")
        (ensure! (= @kernels/*events* (into [:parent] (range n))) "Parent cast moved before inputs"))
      (reset! kernels/*events* [])
      (let [bad-values (assoc values 5 `(throw (ex-info "input failed" {:input 5})))
            throws (kernels/custom-operation model keys {} bad-values)]
        (ensure! (= {:input 5} (try (throws) nil (catch clojure.lang.ExceptionInfo e (ex-data e))))
          "Input exception changed")
        (ensure! (= @kernels/*events* (vec (range 5))) "Inputs evaluated after exception")))))


(defn kernel
  [{:keys [spec model]}]
  (let [fs (fixtures spec)]
    (ExtendKernel. (kernels/operation model (:bindings spec))
      (object-array (map :parent fs))
      (into-array (class (object-array 0)) (map :values fs)))))


(defn result-file
  [output {:keys [spec model fork]}]
  (io/file output (str (name (:workload spec)) "-p" (:parent-size spec) "-n" (:bindings spec)
                    "--" (name model) "--f" fork ".edn")))


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
        f #(.run ^ExtendKernel k)]
    (ensure! (not (.exists file)) "Result already exists")
    (verify! (:spec job) (:model job))
    (println "Scope-extension comparison" protocol job)
    (let [result (assoc job :schema-version 1 :protocol protocol :environment env
                   :parent-class (.getName (class (:parent (first (fixtures (:spec job))))))
                   :key-hashes (mapv #(.hashCode ^Object %) (binding-keys 32))
                   :fixture-key-hashes (into (sorted-map)
                                         (for [k (concat (keys (:parent (first (fixtures (:spec job)))))
                                                   (binding-keys (get-in job [:spec :bindings])))]
                                           [(str k) (.hashCode ^Object k)]))
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
      (str "# Scope-extension call shape\n\nProtocol: `" (name (:protocol plan)) "`. Completed "
        (count rows) "/" (count (:jobs plan)) " fresh JVMs. Time and bytes are per map update.\n\n"
        "| Workload | Parent entries | Bindings | Model | Fork | ns/update | B/update |\n"
        "| --- | ---: | ---: | --- | ---: | ---: | ---: |\n"
        (apply str (for [{:keys [spec model fork measurement allocation]} rows]
                     (format "| %s | %d | %d | %s | %d | %.2f | %.2f |\n"
                       (name (:workload spec)) (:parent-size spec) (:bindings spec) (name model) fork
                       (* 1e9 (first (:mean measurement))) (:bytes-per-call allocation))))))
    rows))


(defn run-suite!
  [{:keys [output protocol specs forks selected-models resume? verify-only?]
    :or {output "../../../target/bench/extend-scope/screen" protocol :screen forks 2}}]
  (ensure! (= 25 (.feature (Runtime/version))) "Measure this experiment on actual JDK 25")
  (ensure! (= "ScopedValueBackend" (ScopedRuntime/backendName)) "Expected primary runtime")
  (ensure! (#{:screen :full} protocol) "Protocol must be :screen or :full")
  (ensure! (and (integer? forks) (pos? forks)) "Forks must be positive")
  (let [specs (vec (or specs default-specs))
        selected (vec (or selected-models models))
        _ (ensure! (and (seq specs) (= (count specs) (count (distinct specs)))
                     (every? (set default-specs) specs)
                     (seq selected) (= (count selected) (count (distinct selected)))
                     (every? (set models) selected)) "Select distinct cases and known models")
        jobs (vec (for [fork (range 1 (inc forks)) [index spec] (map-indexed vector specs)
                        model (if (odd? (+ index fork)) selected (reverse selected))]
                    {:spec spec :model model :fork fork}))
        plan {:schema-version 1 :protocol protocol :environment (environment)
              :settings (if (= protocol :screen) screen-options criterium/*default-benchmark-opts*) :jobs jobs}
        manifest (io/file output "plan.edn")]
    (doseq [spec specs model selected] (verify! spec model))
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
                               "-m" "co.multiply.scoped-extend-bench"
                               (pr-str {:worker? true :output output :job job :protocol protocol})]))]
              (println (str (inc index) "/" (count jobs)) job)
              (flush)
              (let [process (-> (ProcessBuilder. ^java.util.List args)
                              (.redirectErrorStream true) (.redirectOutput log) (.start))]
                (ensure! (zero? (.waitFor process)) (str "Worker failed: " log)))))
          (let [rows (write-summary! output plan)
                row (some #(when (= job (select-keys % [:spec :model :fork])) %) rows)]
            (printf "  %.2f ns/update, %.2f B/update%n"
              (* 1e9 (first (get-in row [:measurement :mean])))
              (get-in row [:allocation :bytes-per-call]))
            (flush)))))))


(defn -main
  [& [arg]]
  (try
    (let [options (if arg (edn/read-string arg) {})]
      (if (:worker? options) (worker! options) (run-suite! options)))
    (finally (shutdown-agents))))
