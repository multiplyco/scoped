(ns co.multiply.scoped-allocation-profile
  "One warmed-up kernel per JVM, with JFR allocation stacks and thread byte counts."
  (:require [clojure.edn :as edn]
    [clojure.java.io :as io]
    [co.multiply.scoped-runtime-bench :as bench]
    [criterium.core :as criterium])
  (:import [java.time Instant]
    [jdk.jfr Recording]
    [jdk.jfr.consumer RecordedEvent RecordedFrame RecordingFile]))


(def default-options
  {:case :entry/with-scope
   :warmup-seconds 5
   :record-seconds 3
   :output "target/bench/allocation/with-scope"})


(def allocation-options
  {:allocation-iterations 1000000 :allocation-samples 5})


(defn run-window!
  [f seconds]
  (let [start (System/nanoTime)
        duration (long (* seconds 1000000000))]
    (loop [calls (long 0)]
      (criterium/execute-expr 100000 f)
      (let [elapsed (- (System/nanoTime) start)
            calls (+ calls 100000)]
        (if (< elapsed duration)
          (recur calls)
          {:calls calls :elapsed-ns elapsed})))))


(defn event-stack
  [^RecordedEvent event]
  (mapv (fn [^RecordedFrame frame]
          (let [method (.getMethod frame)]
            (str (.getName (.getType method)) "/" (.getName method)
              ":" (.getLineNumber frame))))
    (some-> event .getStackTrace .getFrames)))


(defn summarize-recording
  [path thread-id ^Instant window-start ^Instant window-end]
  (with-open [recording (RecordingFile. path)]
    (loop [samples {} refills {} outside {}]
      (if (.hasMoreEvents recording)
        (let [event (.readEvent recording)
              type (.getName (.getEventType event))]
          (if (and (contains? #{"jdk.ObjectAllocationSample"
                                "jdk.ObjectAllocationInNewTLAB"
                                "jdk.ObjectAllocationOutsideTLAB"} type)
                (= thread-id (.getJavaThreadId (.getThread event "eventThread")))
                (not (.isBefore (.getStartTime event) window-start))
                (not (.isAfter (.getStartTime event) window-end)))
            (let [key {:class (.getName (.getClass event "objectClass"))
                       :stack (event-stack event)}]
              (case type
                "jdk.ObjectAllocationSample"
                (recur (update samples key
                         (fn [row]
                           {:samples (inc (get row :samples 0))
                            :weight-bytes (+ (get row :weight-bytes 0) (.getLong event "weight"))}))
                  refills outside)
                "jdk.ObjectAllocationInNewTLAB"
                (recur samples (update refills (assoc key :object-bytes (.getLong event "allocationSize"))
                                 (fnil inc 0)) outside)
                "jdk.ObjectAllocationOutsideTLAB"
                (recur samples refills
                  (update outside (assoc key :object-bytes (.getLong event "allocationSize")) (fnil inc 0)))))
            (recur samples refills outside)))
        {:samples (vec (sort-by :weight-bytes > (map (fn [[k v]] (merge k v)) samples)))
         ;; Refill events identify object sizes/stacks, not the frequency of every allocation.
         :tlab-refills (vec (sort-by :events > (map (fn [[k v]] (assoc k :events v)) refills)))
         :outside-tlab (vec (sort-by :events > (map (fn [[k v]] (assoc k :events v)) outside)))}))))


(defn profile!
  [provided]
  (let [{:keys [case warmup-seconds record-seconds output] :as options}
        (merge default-options provided)
        spec (some #(when (= case (:id %)) %) (bench/cases))
        jfr-file (io/file (str output ".jfr"))
        edn-file (io/file (str output ".edn"))]
    (bench/ensure! (every? (set (keys default-options)) (keys provided)) "Unknown profiling option")
    (bench/ensure! spec (str "Unknown benchmark case: " case))
    (bench/ensure! (every? #(and (number? %) (pos? %)) [warmup-seconds record-seconds])
      "Warm-up and recording durations must be positive")
    (bench/ensure! (not-any? #(.exists ^java.io.File %) [jfr-file edn-file])
      "Choose a new output prefix; profile results are never overwritten")
    (io/make-parents jfr-file)
    (bench/verify! [spec])
    (println "Profiling" case "on" (System/getProperty "java.runtime.version"))
    (println "Warm-up:" warmup-seconds "seconds; recording:" record-seconds "seconds")
    (let [environment (bench/environment)
          thread-id (.getId (Thread/currentThread))
          measurement
          (bench/in-context (:context spec)
            (fn []
              (run-window! (:f spec) warmup-seconds)
              (let [before (bench/allocations (:f spec) allocation-options)]
                (with-open [recording (Recording.)]
                  (-> recording (.enable "jdk.ObjectAllocationSample")
                    (.withStackTrace) (.with "throttle" "1000/s"))
                  (-> recording (.enable "jdk.ObjectAllocationInNewTLAB") (.withStackTrace))
                  (-> recording (.enable "jdk.ObjectAllocationOutsideTLAB") (.withStackTrace))
                  (.start recording)
                  ;; Prime JFR's sampler: its first weight can include pre-recording allocations.
                  (run-window! (:f spec) 1)
                  (let [window-start (Instant/now)
                        counter (bench/allocation-counter)
                        bytes-before (when counter (counter))
                        window (run-window! (:f spec) record-seconds)
                        bytes-after (when counter (counter))
                        window-end (Instant/now)]
                    (.stop recording)
                    (.dump recording (.toPath jfr-file))
                    {:unrecorded-before before
                     :recorded (cond-> (assoc window :start (str window-start) :end (str window-end))
                                 counter (assoc :bytes-per-call
                                           (/ (double (- bytes-after bytes-before)) (:calls window))))
                     :unrecorded-after (bench/allocations (:f spec) allocation-options)})))))
          result {:schema-version 1 :quality :diagnostic :case case :options options
                  :environment environment
                  :profile-source-sha256 (bench/sha256 "bench/co/multiply/scoped_allocation_profile.clj")
                  :measurement measurement :jfr (.getPath jfr-file)
                  :allocations (summarize-recording (.toPath jfr-file) thread-id
                                 (Instant/parse (get-in measurement [:recorded :start]))
                                 (Instant/parse (get-in measurement [:recorded :end])))}]
      (spit edn-file (pr-str result))
      (println "Bytes/call before, during and after JFR:"
        (get-in measurement [:unrecorded-before :bytes-per-call])
        (get-in measurement [:recorded :bytes-per-call])
        (get-in measurement [:unrecorded-after :bytes-per-call]))
      (println "Top sampled classes by allocation weight (not object counts):")
      (doseq [[class weight] (->> (get-in result [:allocations :samples])
                               (group-by :class)
                               (map (fn [[class rows]] [class (reduce + (map :weight-bytes rows))]))
                               (sort-by second >) (take 8))]
        (println class weight))
      (println "Saved" (.getPath edn-file) "and" (.getPath jfr-file))
      result)))


(defn -main
  [& [arg]]
  (try (profile! (if arg (edn/read-string arg) {}))
    (finally (shutdown-agents))))
