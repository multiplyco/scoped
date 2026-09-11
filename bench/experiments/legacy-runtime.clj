(ns co.multiply.scoped-runtime-bench
  "Public-API JVM benchmarks, kept independent of the runtime implementation."
  (:require [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.java.shell :as shell]
    [clojure.pprint :as pprint]
    [co.multiply.scoped :as s]
    [criterium.core :as criterium])
  (:import (java.lang.management ManagementFactory)
    (java.security MessageDigest)
    (java.time Instant)))


(def ^:dynamic *root* (Object.))
(def ^:dynamic *a*)
(def ^:dynamic *b*)
(def ^:dynamic *c*)
(def ^:dynamic *d*)
(def ^:dynamic *e*)
(def ^:dynamic *f*)
(def ^:dynamic *g*)
(def ^:dynamic *h*)
(def ^:dynamic *i*)
(def ^:dynamic *j*)


(defn cases
  "Allocate fixtures once. Contexts wrap the entire measurement, not each call."
  []
  (let [values (object-array [(Object.) (Object.)])
        first-value (aget values 0)
        second-value (aget values 1)
        bound (s/assoc-scope {} *a* first-value)
        large (assoc (zipmap (map #(intern 'co.multiply.scoped-runtime-bench
                                     (symbol (str "extra-" %))) (range 31))
                       (repeat second-value)) #'*a* first-value)
        sink (object-array 100)
        spec (fn [id description context f expected]
               {:id id :description description :context context :f f
                :valid? #(identical? expected %)})
        built (fn [id description f expected]
                {:id id :description description :context nil :f f
                 :valid? #(= expected %)})
        expected-map (fn [vars] (zipmap vars (repeat first-value)))]
    [(spec :control/return "Array read and return; harness floor" nil
       #(aget values 0) first-value)
     (spec :current/unbound "Capture with no active scope" nil #(s/current-scope) {})
     (spec :current/empty "Capture explicitly bound empty scope" {} #(s/current-scope) {})
     (spec :current/bound "Capture one-binding scope" bound #(s/current-scope) bound)
     (spec :ask/hit "Scoped hit, one-argument ask" bound #(s/ask *a*) first-value)
     (spec :ask/hit-default "Scoped hit, lazy default" bound
       #(s/ask *a* (aget values 1)) first-value)
     (spec :ask/hit-large "Scoped hit in a 32-binding map" large #(s/ask *a*) first-value)
     (spec :ask/root "Fall back to root, one-argument ask" nil #(s/ask *root*) *root*)
     (spec :ask/root-default "Fall back to root, with default" nil
       #(s/ask *root* (aget values 1)) *root*)
     (spec :ask/unbound-default "Unbound var; evaluate default" nil
       #(s/ask *a* (aget values 1)) second-value)
     (spec :ask/nil "Explicit nil suppresses default" (s/assoc-scope {} *a* nil)
       #(s/ask *a* (aget values 1)) nil)
     (spec :ask/false "Explicit false suppresses default" (s/assoc-scope {} *a* false)
       #(s/ask *a* (aget values 1)) false)
     (built :build/zero "assoc-scope, zero bindings" #(s/assoc-scope bound) bound)
     (built :build/one "assoc-scope, one binding" #(s/assoc-scope {} *a* (aget values 0)) bound)
     (built :build/two "assoc-scope, two bindings"
       #(s/assoc-scope {} *a* (aget values 0) *b* (aget values 0))
       (expected-map [#'*a* #'*b*]))
     (built :build/nine "assoc-scope, nine bindings"
       #(s/assoc-scope {} *a* (aget values 0) *b* (aget values 0) *c* (aget values 0)
          *d* (aget values 0) *e* (aget values 0) *f* (aget values 0)
          *g* (aget values 0) *h* (aget values 0) *i* (aget values 0))
       (expected-map [#'*a* #'*b* #'*c* #'*d* #'*e* #'*f* #'*g* #'*h* #'*i*]))
     (built :build/ten "assoc-scope, ten bindings"
       #(s/assoc-scope {} *a* (aget values 0) *b* (aget values 0) *c* (aget values 0)
          *d* (aget values 0) *e* (aget values 0) *f* (aget values 0)
          *g* (aget values 0) *h* (aget values 0) *i* (aget values 0) *j* (aget values 0))
       (expected-map [#'*a* #'*b* #'*c* #'*d* #'*e* #'*f* #'*g* #'*h* #'*i* #'*j*]))
     (spec :entry/with-scope "Enter prebuilt scope, constant body" nil
       #(s/with-scope bound :done) :done)
     (spec :entry/with-scope-captured "Enter prebuilt scope, body captures a local" nil
       #(let [v (aget values 0)] (s/with-scope bound v)) first-value)
     (spec :entry/with-scope-read "Restore prebuilt scope and read" nil
       #(s/with-scope bound (s/ask *a*)) first-value)
     (spec :entry/scoping-zero "scoping with no bindings" bound
       #(s/scoping [] (s/ask *a*)) first-value)
     (spec :entry/scoping-one "Construct, enter, read one binding" nil
       #(s/scoping [*a* (aget values 0)] (s/ask *a*)) first-value)
     (spec :entry/scoping-captured "Construct, enter, read; body captures two locals" nil
       #(let [v (aget values 0) result (aget values 1)]
          (s/scoping [*a* v] (when (identical? v (s/ask *a*)) result))) second-value)
     (spec :entry/scoping-two "Construct, enter two bindings; read second" nil
       #(s/scoping [*a* (aget values 0) *b* (aget values 1)] (s/ask *b*)) second-value)
     (spec :entry/scoping-ten "Construct, enter ten bindings; read last" nil
       #(s/scoping [*a* (aget values 0) *b* (aget values 0) *c* (aget values 0)
                    *d* (aget values 0) *e* (aget values 0) *f* (aget values 0)
                    *g* (aget values 0) *h* (aget values 0) *i* (aget values 0)
                    *j* (aget values 1)] (s/ask *j*)) second-value)
     (spec :entry/skip-inherited "Skip a binding and read inherited value" bound
       #(s/scoping [*a* s/skip] (s/ask *a*)) first-value)
     (spec :entry/skip-absent "Skip absent binding and use default" nil
       #(s/scoping [*a* s/skip] (s/ask *a* (aget values 1))) second-value)
     (spec :nested/override "Two entries, inner override, three reads and restoration" nil
       #(s/scoping [*a* (aget values 0)]
          (let [outer (s/ask *a*)
                inner (s/scoping [*a* (aget values 1)] (s/ask *a*))]
            (and (identical? outer (s/ask *a*)) (identical? inner (aget values 1))))) true)
     (spec :capture/restore "Capture parent, enter empty context, restore and read" bound
       #(let [captured (s/current-scope)]
          (s/with-scope {} (s/with-scope captured (s/ask *a*)))) first-value)
     (assoc (spec :workload/read-100 "One scope, 100 reads into a preallocated sink" nil
              #(s/scoping [*a* (aget values 0)]
                 (dotimes [i 100] (aset sink i (s/ask *a*))) sink) sink)
       :reads-per-call 100
       :valid? #(and (identical? sink %) (every? (fn [v] (identical? v first-value)) sink)))]))


(defn ensure!
  [condition message]
  (when-not condition (throw (ex-info message {}))))


(defn in-context
  "nil leaves the CLI thread unscoped; a map establishes an explicit scope."
  [context f]
  (if (nil? context) (f) (s/with-scope context (f))))


(defn verify!
  [specs]
  (let [outer (s/current-scope)]
    (doseq [{:keys [id context f valid?]} specs]
      (in-context context
        (fn []
          (dotimes [_ 3]
            (ensure! (valid? (f)) (str "Incorrect result: " id))
            (ensure! (= (or context outer) (s/current-scope)) (str "Scope leaked: " id)))))
      (ensure! (= outer (s/current-scope)) (str "Outer scope leaked: " id)))
    (s/scoping [*a* nil]
      (ensure! (nil? (s/ask *a* (throw (Exception. "Default evaluated on hit"))))
        "Explicit nil lost")
      (let [failure (Exception. "Expected cleanup probe")]
        (try (s/scoping [*a* :inner] (throw failure))
          (catch Exception e (ensure! (identical? e failure) "Unexpected exception"))))
      (ensure! (nil? (s/ask *a*)) "Exceptional exit failed to restore scope")))
  (println "Verified" (count specs) "kernels, lazy default and exceptional restoration."))


(def default-options
  {:output "target/bench/runtime.edn"
   :reverse? false
   :allocation-iterations 100000
   :allocation-samples 3
   :criterium {:samples 20 :warmup-jit-period 3000000000
               :target-execution-time 50000000 :bootstrap-size 1000 :overhead 0}})


(defn allocation-counter
  []
  (let [bean (ManagementFactory/getThreadMXBean)]
    (when (and (instance? com.sun.management.ThreadMXBean bean)
            (.isThreadAllocatedMemorySupported ^com.sun.management.ThreadMXBean bean))
      (let [bean ^com.sun.management.ThreadMXBean bean
            thread-id (.getId (Thread/currentThread))]
        (when-not (.isThreadAllocatedMemoryEnabled bean)
          (.setThreadAllocatedMemoryEnabled bean true))
        (fn [] (.getThreadAllocatedBytes bean thread-id))))))


(defn allocations
  [f {:keys [allocation-iterations allocation-samples]}]
  (if-let [counter (allocation-counter)]
    (let [samples (mapv (fn [_]
                          (let [before (long (counter))]
                            (criterium/execute-expr allocation-iterations f)
                            (- (long (counter)) before)))
                    (range allocation-samples))]
      {:status :measured :iterations allocation-iterations :samples-bytes samples
       :bytes-per-call (/ (double (reduce + samples)) allocation-samples allocation-iterations)})
    {:status :unsupported}))


(defn sha256
  [file]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                 (java.nio.file.Files/readAllBytes (.toPath (io/file file))))]
    (apply str (map #(format "%02x" (bit-and 255 %)) digest))))


(defn git-output
  [& args]
  (let [{:keys [exit out]} (apply shell/sh "git" args)]
    (when (zero? exit) (.trim ^String out))))


(defn environment
  []
  {:recorded-at (str (Instant/now))
   :git {:head (git-output "rev-parse" "HEAD")
         :status (git-output "status" "--porcelain")}
   :source-sha256 (into (sorted-map)
                    (for [root ["src" "src-java" "java"]
                          file (file-seq (io/file root)) :when (.isFile ^java.io.File file)]
                      [(str file) (sha256 file)]))
   :suite-sha256 (sha256 "bench/co/multiply/scoped_runtime_bench.clj")
   :deps-sha256 (sha256 "deps.edn")
   :clojure-version (clojure-version)
   :java-version (System/getProperty "java.runtime.version")
   :java-vm (System/getProperty "java.vm.name")
   :os (System/getProperty "os.name")
   :os-version (System/getProperty "os.version")
   :arch (System/getProperty "os.arch")
   :processors (.availableProcessors (Runtime/getRuntime))
   :jvm-arguments (vec (.getInputArguments (ManagementFactory/getRuntimeMXBean)))
   :compiler-options *compiler-options*
   :backend (if (or (= "true" (System/getProperty "co.multiply.scoped.force-fallback"))
                  (< (.major (Runtime/version)) 25)) :thread-local :scoped-value)})


(defn run-case
  [options {:keys [id description context f] :as spec}]
  (println "Measuring" id "—" description)
  (flush)
  (in-context context
    (fn []
      (let [result (criterium/benchmark* f (:criterium options))
            mean-ns (* 1e9 (first (:mean result)))
            ci-ns (mapv #(* 1e9 %) (second (:mean result)))
            allocation (allocations f options)]
        (printf "  %.2f ns/call, 95%% CI [%.2f, %.2f], %s bytes/call\n"
          mean-ns (first ci-ns) (second ci-ns)
          (if-let [b (:bytes-per-call allocation)] (format "%.2f" b) "unavailable"))
        (flush)
        (merge (select-keys spec [:id :description :reads-per-call])
          {:context-mode (if (nil? context) :ambient :bound)
           :context-size (count context) :mean-ns mean-ns :ci-ns ci-ns
           :allocation allocation :criterium (dissoc result :results)})))))


(defn select-cases
  [specs {:keys [cases groups reverse?]}]
  (doseq [id cases]
    (ensure! (some #(= id (:id %)) specs) (str "Unknown case: " id)))
  (doseq [group groups]
    (ensure! (some #(= (name group) (namespace (:id %))) specs) (str "Unknown group: " group)))
  (let [selected (filterv #(and (or (nil? cases) ((set cases) (:id %)))
                             (or (nil? groups) ((set (map name groups)) (namespace (:id %))))) specs)]
    (ensure! (seq selected) "No benchmark cases selected")
    (cond-> selected reverse? reverse)))


(defn compare-results!
  [[before-path after-path :as paths]]
  (ensure! (= 2 (count paths)) ":compare requires [before.edn after.edn]")
  (let [before (edn/read-string (slurp before-path))
        after (edn/read-string (slurp after-path))
        indexed (into {} (map (juxt :id identity) (:results after)))]
    (ensure! (= 1 (:schema-version before) (:schema-version after)) "Unsupported result schema")
    (ensure! (= :scoped-runtime (:suite before) (:suite after)) "Different benchmark suite")
    (when-not (and (:complete? before) (:complete? after))
      (println "At least one run is incomplete."))
    (doseq [k [:backend :java-version :clojure-version :jvm-arguments :compiler-options
               :arch :os-version :processors :suite-sha256]]
      (when (not= (get-in before [:environment k]) (get-in after [:environment k]))
        (println "Different environment field:" k)))
    (when (not= (get-in before [:options :criterium]) (get-in after [:options :criterium]))
      (println "Different Criterium options."))
    (println "Case                              before ns   after ns   after/before   delta bytes")
    (doseq [a (:results before) :let [b (indexed (:id a))]]
      (if b
        (printf "%-33s %9.2f %10.2f %14.3f %13s\n" (str (:id a)) (:mean-ns a) (:mean-ns b)
          (/ (:mean-ns b) (:mean-ns a))
          (if (every? number? [(get-in a [:allocation :bytes-per-call])
                               (get-in b [:allocation :bytes-per-call])])
            (format "%+.2f" (- (get-in b [:allocation :bytes-per-call])
                              (get-in a [:allocation :bytes-per-call]))) "unavailable"))
        (println (:id a) "missing from after results")))
    (doseq [id (remove (set (map :id (:results before))) (keys indexed))]
      (println id "missing from before results"))
    (println "Ratios compare point estimates, not statistical significance; check fresh JVM repeats.")))


(defn run-suite!
  [& [options-edn]]
  (let [provided (if options-edn (edn/read-string options-edn) {})
        options (-> (merge default-options provided)
                  (update :criterium #(assoc (merge (:criterium default-options) %) :overhead 0)))]
    (if (:compare options)
      (compare-results! (:compare options))
      (let [specs (cases)
            selected (select-cases specs options)]
        (if (:list? options)
          (doseq [{:keys [id description]} selected] (println id "—" description))
          (do
            (verify! specs)
            (when-not (:verify-only? options)
              (doseq [k [:allocation-iterations :allocation-samples]]
                (ensure! (pos-int? (get options k)) (str k " must be a positive integer")))
              (let [output (:output options)
                    result (atom {:schema-version 1 :suite :scoped-runtime :options options
                                  :environment (environment) :complete? false
                                  :case-order (mapv :id selected) :results []})
                    save! #(spit output (with-out-str (pprint/pprint @result)))]
                (println "Environment:" (pr-str (:environment @result)))
                (io/make-parents output)
                (save!)
                (doseq [spec selected]
                  (swap! result update :results conj (run-case options spec))
                  (save!))
                (swap! result assoc :complete? true :finished-at (str (Instant/now)))
                (save!)
                (println "Results saved to" output)))))))))


(defn -main
  [& args]
  (try (apply run-suite! args)
    (finally (shutdown-agents))))
