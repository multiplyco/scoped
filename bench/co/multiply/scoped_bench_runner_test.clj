(ns co.multiply.scoped-bench-runner-test
  (:require [clojure.test :refer [deftest is run-tests testing]]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [co.multiply.scoped-bench-runner :as runner]
    [co.multiply.scoped-dev-bench :as dev]
    [co.multiply.scoped-runtime-bench :as bench]))


(deftest scheduling-test
  (let [ids [:ask/hit :ask/root :ask/nil :entry/scoping-one :workload/read-100]
        backends [:scoped-value :thread-local]
        jobs (runner/plan ids backends 5 42)]
    (is (= 50 (count jobs) (count (distinct (map :file jobs)))))
    (is (= (set (for [id ids backend backends fork (range 1 6)] [id backend fork]))
           (set (map (juxt :case-id :backend :fork) jobs))))
    (is (= jobs (runner/plan ids backends 5 42)))
    (is (not= jobs (runner/plan ids backends 5 43)))))


(deftest result-integrity-test
  (let [options bench/default-options
        environment {:source-sha256 {"src/example.clj" "original"}}
        job {:case-id :ask/hit :backend :thread-local :fork 1}
        worker (merge job
                 {:schema-version 2 :protocol :isolated-case :complete? true
                  :environment environment :options options
                  :result {:id :ask/hit :mean-ns 10.0
                           :criterium {:samples (repeat 60 1000000000)
                                       :warmup-time 20000000001}}})]
    (is (nil? (runner/validate-worker! worker job environment options)))
    (doseq [bad [(assoc worker :complete? false)
                 (assoc worker :fork 2)
                 (assoc-in worker [:environment :source-sha256 "src/example.clj"] "changed")
                 (assoc-in worker [:options :criterium :samples] 20)
                 (assoc-in worker [:result :mean-ns] Double/NaN)
                 (assoc-in worker [:result :criterium :samples] [1 2])
                 (assoc-in worker [:result :criterium :warmup-time] 3000000000)]]
      (is (thrown? clojure.lang.ExceptionInfo (runner/validate-worker! bad job environment options))))))


(deftest aggregation-test
  (let [diagnostics {:last-first-ratio 1.0 :measurement-warmup-ratio 1.0 :sampling-delta {}}
        jobs (for [n [10.0 11.0 100.0]]
               {:case-id :ask/hit :backend :thread-local
                :result {:mean-ns n :diagnostics diagnostics :allocation {:bytes-per-call 0}}})
        result (first (runner/aggregate jobs))]
    (is (= 3 (:fork-count result)))
    (is (= 11.0 (:median-ns result)))
    (is (= [10.0 100.0] (:range-ns result)))
    (is (:wide-fork-spread? result))
    (is (= 0 (:flagged-forks result)))
    (is (= #{:sample-drift :warmup-shift :compilation-during-sampling :gc-during-sampling}
           (set (runner/diagnostic-flags
                  {:diagnostics {:last-first-ratio 0.8 :measurement-warmup-ratio 2.7
                                 :sampling-delta {:compilation-ms 3 :gc-count 1}}}))))))


(deftest worker-isolation-test
  (let [seen (atom [])
        saved (atom nil)
        environment {:source-sha256 {} :backend :thread-local}
        options {:case-id :ask/hit :backend :thread-local :fork 1 :output "unused"
                 :expected-fingerprint (runner/fingerprint environment)}]
    (with-redefs [bench/environment (constantly environment)
                  bench/cases (fn [] [{:id :ask/hit} {:id :ask/root}])
                  bench/verify! (fn [& _] (throw (Exception. "Verification must not execute in a measured worker")))
                  bench/run-case (fn [_ spec] (swap! seen conj (:id spec)) {:id (:id spec)})
                  runner/save-edn! (fn [_ value] (reset! saved value))]
      (runner/run-worker! options))
    (is (= [:ask/hit] @seen))
    (is (:complete? @saved))
    (is (= :ask/hit (get-in @saved [:result :id])))))


(deftest options-test
  (is (= :full (runner/quality bench/default-options)))
  (is (= :exploratory (runner/quality (assoc bench/default-options :forks 1))))
  (is (thrown? clojure.lang.ExceptionInfo
        (runner/validate-options! (assoc bench/default-options :forks 0) [:thread-local])))
  (is (thrown? clojure.lang.ExceptionInfo
        (runner/validate-options! bench/default-options [:thread-local :thread-local]))))


(deftest development-comparison-test
  (let [result {:id :ask/hit :mean-ns 10.0 :allocation {:bytes-per-call 8.0}}
        before {:schema-version 1 :suite :scoped-runtime-dev :protocol :shared-jvm-dev
                :complete? true :options dev/default-options :case-order [:ask/hit]
                :environment {:backend :thread-local :source-sha256 {"src/example.clj" "old"}}
                :results [result]}
        after (-> before
                (assoc-in [:environment :source-sha256 "src/example.clj"] "new")
                (assoc :results [(assoc result :mean-ns 25.0 :allocation {:bytes-per-call 24.0})]))]
    (is (= [{:id :ask/hit :before-ns 10.0 :after-ns 25.0 :ratio 2.5
             :delta-bytes 16.0 :large-slowdown? true}]
           (dev/comparison before after)))
    (is (nil? (:delta-bytes (first (dev/comparison before
                                     (assoc-in after [:results 0 :allocation] {:status :unsupported}))))))
    (doseq [bad [(assoc after :protocol :isolated-forks)
                 (assoc after :complete? false)
                 (assoc after :case-order [:ask/root])
                 (assoc after :results [])
                 (assoc-in after [:environment :backend] :scoped-value)
                 (assoc-in after [:environment :suite-sha256] {"suite" "changed"})
                 (assoc-in after [:options :criterium :samples] 60)]]
      (is (thrown? clojure.lang.ExceptionInfo (dev/comparison before bad))))))


(deftest development-options-test
  (is (< (get-in (dev/options {}) [:criterium :warmup-jit-period]) 2000000000))
  (is (= 4 (get-in (dev/options {:criterium {:samples 4}}) [:criterium :samples])))
  (is (= 0 (get-in (dev/options {:criterium {:overhead 100}}) [:criterium :overhead])))
  (doseq [bad [{:forks 5} {:backends [:thread-local]} {:criterium {:samples 0}}
               {:allocation-iterations 0} {:baseline "target/a.edn" :output "target/./a.edn"}]]
    (is (thrown? clojure.lang.ExceptionInfo (dev/options bad)))))


(deftest development-checkpoint-test
  (let [directory (.toFile (java.nio.file.Files/createTempDirectory "scoped-dev-test-"
                             (make-array java.nio.file.attribute.FileAttribute 0)))
        output (str (io/file directory "result.edn"))
        specs [{:id :ask/hit} {:id :build/ten}]
        events (atom [])
        opts (dev/options {:output output})
        result (fn [id]
                 {:id id :mean-ns 10.0 :allocation {:bytes-per-call 0.0}
                  :diagnostics {:last-first-ratio 1.0 :measurement-warmup-ratio 1.0
                                :sampling-delta {}}})]
    (try
      (with-redefs [dev/environment (constantly {:backend :thread-local})
                    bench/verify! #(swap! events conj [:verify (mapv :id %)])
                    bench/run-case (fn [_ spec]
                                     (swap! events conj [:measure (:id spec)])
                                     (if (= :build/ten (:id spec))
                                       (throw (ex-info "Expected failure" {}))
                                       (result (:id spec))))
                    runner/run-forks! (fn [& _] (throw (Exception. "Must never launch isolated forks")))]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Expected failure"
              (with-out-str (dev/run-suite! opts specs))))
        (let [partial (edn/read-string (slurp output))]
          (is (false? (:complete? partial)))
          (is (= [:ask/hit] (mapv :id (:results partial)))))
        (is (= [[:verify [:ask/hit :build/ten]] [:measure :ask/hit] [:measure :build/ten]] @events))
        (with-redefs [bench/run-case (fn [_ spec] (result (:id spec)))]
          (with-out-str (dev/run-suite! opts specs))
          (is (:complete? (edn/read-string (slurp output)))))
        (is (.exists (io/file (str output ".md"))))
        (runner/save-edn! output {:suite :scoped-runtime :protocol :isolated-forks})
        (is (thrown? clojure.lang.ExceptionInfo (dev/run-suite! opts specs))))
      (finally
        (doseq [file (reverse (file-seq directory))] (io/delete-file file))))))


(defn -main
  [& _]
  (let [result (run-tests 'co.multiply.scoped-bench-runner-test)]
    (shutdown-agents)
    (when (pos? (+ (:fail result) (:error result))) (System/exit 1))))
