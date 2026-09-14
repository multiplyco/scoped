(ns co.multiply.scoped-representation-bench
  "Standalone representation experiment using unmodified criterium/bench defaults."
  (:require [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [criterium.core :as criterium])
  (:import [co.multiply.scoped.experiment RepresentationBench RepresentationBench$Kernel]
    [java.lang.management ManagementFactory]
    [java.security MessageDigest]
    [java.time Instant]))


(def cases
  [{:id :lookup/small :operation "lookup" :parent-size 4}
   {:id :lookup/large :operation "lookup" :parent-size 32}
   {:id :extend/empty :operation "extend" :parent-size 0}
   {:id :extend/small :operation "extend" :parent-size 4}
   {:id :extend/crossover :operation "extend" :parent-size 7}
   {:id :extend/large :operation "extend" :parent-size 32}
   {:id :nested/small :operation "nested" :parent-size 4}
   {:id :extend/limit :operation "extend" :parent-size 6 :optional? true}
   {:id :overwrite/small :operation "overwrite" :parent-size 6 :optional? true}])


(def source-files
  ["bench/experiments/representation/java/co/multiply/scoped/experiment/VarScope.java"
   "bench/experiments/representation/java/co/multiply/scoped/experiment/RepresentationBench.java"
   "bench/experiments/representation/java/co/multiply/scoped/experiment/ArrayMapBatch.java"
   "bench/experiments/representation/clj/co/multiply/scoped_representation_bench.clj"])


(defn ensure!
  [condition message]
  (when-not condition (throw (ex-info message {}))))


(defn sha256
  [path]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                 (java.nio.file.Files/readAllBytes (.toPath (io/file path))))]
    (apply str (map #(format "%02x" (bit-and 255 %)) digest))))


(defn environment
  []
  {:java-runtime (System/getProperty "java.runtime.version")
   :java-vm (System/getProperty "java.vm.name")
   :clojure-version (clojure-version)
   :jvm-arguments (vec (remove #(str/starts-with? % "-Dclojure.basis=")
                         (.getInputArguments (ManagementFactory/getRuntimeMXBean))))
   :os (System/getProperty "os.name") :os-version (System/getProperty "os.version")
   :arch (System/getProperty "os.arch")
   :source-sha256 (into {} (map (juxt identity sha256) source-files))
   :class-sha256 (into {} (for [name ["VarScope" "RepresentationBench" "ArrayMapBatch"]
                                :let [path (str "target/bench/representation/classes/co/multiply/scoped/experiment/" name ".class")]]
                            [name (sha256 path)]))
   :criterium-version "0.4.6"
   :criterium-defaults criterium/*default-benchmark-opts*})


(defn allocations
  [f]
  (let [bean (ManagementFactory/getThreadMXBean)]
    (if (and (instance? com.sun.management.ThreadMXBean bean)
          (.isThreadAllocatedMemorySupported ^com.sun.management.ThreadMXBean bean))
      (let [bean ^com.sun.management.ThreadMXBean bean
            thread (.getId (Thread/currentThread))]
        (.setThreadAllocatedMemoryEnabled bean true)
        (let [samples (mapv (fn [_]
                              (let [before (.getThreadAllocatedBytes bean thread)]
                                (criterium/execute-expr 1000000 f)
                                (- (.getThreadAllocatedBytes bean thread) before)))
                        (range 3))]
          {:iterations 1000000 :samples-bytes samples
           :bytes-per-call (/ (double (reduce + samples)) 3000000)}))
      {:status :unsupported})))


(defn result-file
  [output case-id model]
  (io/file output (str (str/replace (subs (str case-id) 1) "/" "-") "--" model ".edn")))


(defn worker!
  [{:keys [case-id model output]}]
  (let [{:keys [operation parent-size] :as spec} (some #(when (= case-id (:id %)) %) cases)
        _ (ensure! spec (str "Unknown case: " case-id))
        file (result-file output case-id model)
        _ (ensure! (not (.exists file)) "Result already exists")
        kernel (RepresentationBench$Kernel. model operation parent-size)
        f (case operation
            "lookup" #(.lookup kernel)
            ("extend" "overwrite") #(.extend kernel)
            "nested" #(.nested kernel))
        captured (atom nil)
        report criterium/report-result
        env (environment)]
    (.verify kernel)
    (println "Standard criterium/bench:" case-id model "on" (:java-runtime env))
    (println "Default settings:" criterium/*default-benchmark-opts*)
    ;; Capture raw statistics without altering bench, its defaults, or the report.
    (with-redefs [criterium/report-result
                  (fn [result & options]
                    (reset! captured (walk/postwalk #(if (record? %) (into {} %) %)
                                       (dissoc result :results)))
                    (apply report result options))]
      (criterium/bench (f) :verbose))
    (let [result {:case case-id :model model :spec spec :environment env
                  :finished-at (str (Instant/now)) :protocol :criterium-bench-defaults
                  :measurement @captured :allocation (allocations f)}]
      (ensure! (= env (environment)) "Source/environment changed during measurement")
      (spit file (pr-str result))
      (println "Allocation:" (:allocation result))
      (println "Saved" (str file)))))


(defn write-summary!
  [output jobs]
  (let [rows (keep (fn [{:keys [case-id model]}]
                     (let [file (result-file output case-id model)]
                       (when (.exists file) (edn/read-string (slurp file))))) jobs)]
    (spit (io/file output "summary.md")
      (str "# Standalone representation comparison\n\n"
        "Completed " (count rows) "/" (count jobs) " standard Criterium runs. One fresh JVM per row.\n\n"
        "| Case | Implementation | ns/operation | B/operation |\n| --- | --- | ---: | ---: |\n"
        (apply str (for [row rows]
                     (format "| `%s` | `%s` | %.2f | %.2f |\n"
                       (subs (str (:case row)) 1) (:model row)
                       (* 1e9 (first (get-in row [:measurement :mean])))
                       (get-in row [:allocation :bytes-per-call] Double/NaN))))))
    rows))


(defn run-suite!
  [{:keys [output selected-cases selected-models resume? verify-only?]
    :or {output "target/bench/representation/default"}}]
  (RepresentationBench/verifyAll)
  (doseq [{:keys [operation parent-size]} cases
          model ["clojure-persistent" "clojure-transient" "var-scope" "clojure-rebuild"]]
    (.verify (RepresentationBench$Kernel. model operation parent-size)))
  (println "Verified randomized snapshot, duplicate/skip/nil/false and promotion semantics for all models.")
  (when-not verify-only?
    (let [selected (if selected-cases
                     (mapv (fn [id]
                             (or (some #(when (= id (:id %)) %) cases)
                               (throw (ex-info (str "Unknown case: " id) {})))) selected-cases)
                     (remove :optional? cases))
          requested-models (or selected-models ["clojure-persistent" "clojure-transient" "var-scope"])
          _ (ensure! (and (seq selected) (seq requested-models)
                       (= (count requested-models) (count (distinct requested-models)))
                       (every? #{"clojure-persistent" "clojure-transient" "var-scope" "clojure-rebuild"}
                         requested-models))
              "Select distinct known models and at least one case")
          jobs (vec (mapcat (fn [index {:keys [id operation]}]
                              (let [models (if (= operation "lookup")
                                             (filter #{"clojure-persistent" "var-scope"} requested-models)
                                             requested-models)]
                                (for [model (if (odd? index) (reverse models) models)]
                                  {:case-id id :model model})))
                      (range) selected))
          _ (ensure! (seq jobs) "Selection has no distinct workloads; Clojure lookup uses clojure-persistent")
          plan {:environment (environment) :jobs jobs}
          manifest (io/file output "plan.edn")]
      (if (.exists manifest)
        (ensure! (and resume? (= plan (edn/read-string (slurp manifest))))
          "Existing run requires :resume? true and identical sources, JVM and cases")
        (do (io/make-parents manifest) (spit manifest (pr-str plan))))
      (doseq [[index {:keys [case-id model] :as job}] (map-indexed vector jobs)]
        (let [file (result-file output case-id model)]
          (if (.exists file)
            (ensure! (and resume? (= (:environment plan) (:environment (edn/read-string (slurp file)))))
              "Existing result has incompatible environment")
            (let [log (io/file (str file ".log"))
                  arguments (vec (concat [(str (System/getProperty "java.home") "/bin/java")]
                                   (:jvm-arguments (:environment plan))
                                   ["-cp" (System/getProperty "java.class.path") "clojure.main"
                                    "-m" "co.multiply.scoped-representation-bench"
                                    (pr-str (assoc job :worker? true :output output))]))]
              (println (str (inc index) "/" (count jobs)) case-id model "Log:" (str log))
              (flush)
              (let [process (-> (ProcessBuilder. ^java.util.List arguments)
                              (.redirectErrorStream true) (.redirectOutput log) (.start))]
                (ensure! (zero? (.waitFor process)) (str "Benchmark failed; see " log)))))
          (let [rows (write-summary! output jobs)
                row (last (filter #(and (= case-id (:case %)) (= model (:model %))) rows))]
            (ensure! (= (:environment plan) (:environment row))
              "Worker result has incompatible sources, classes or JVM")
            (printf "  %.2f ns/op, %.2f B/op%n"
              (* 1e9 (first (get-in row [:measurement :mean])))
              (get-in row [:allocation :bytes-per-call] Double/NaN))
            (flush))))
      (println "Completed:" (str (io/file output "summary.md"))))))


(defn -main
  [& [arg]]
  (try
    (let [options (if arg (edn/read-string arg) {})]
      (if (:worker? options) (worker! options) (run-suite! options)))
    (finally (shutdown-agents))))
