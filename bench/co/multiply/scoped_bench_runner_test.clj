(ns co.multiply.scoped-bench-runner-test
  (:require [clojure.test :refer [deftest is run-tests testing]]
    [co.multiply.scoped-bench-runner :as runner]
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


(defn -main
  [& _]
  (let [result (run-tests 'co.multiply.scoped-bench-runner-test)]
    (shutdown-agents)
    (when (pos? (+ (:fail result) (:error result))) (System/exit 1))))
