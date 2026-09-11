(ns co.multiply.scoped-test-clj
  "CLJ-only tests for scoped values library.

   Tests for JVM-specific features:
   - Implementation detection (ScopedValue vs ThreadLocal)
   - Virtual thread integration"
  (:require
    [clojure.test :refer [deftest is testing]]
    [co.multiply.scoped :refer [ask assoc-scope current-scope scoping with-scope]])
  (:import [co.multiply.scoped ScopedRuntime]))


;; Test vars
(def ^:dynamic *with-default* :default-value)
(def ^:dynamic *another* :another-default)


;; # Implementation detection
;; ################################################################################
(deftest implementation-test
  (testing "Java backend matches the runtime and fallback property"
    (let [force-fallback? (= (System/getProperty "co.multiply.scoped.force-fallback") "true")
          jdk-25+?        (>= (.major (Runtime/version)) 25)]
      (is (= (if (and jdk-25+? (not force-fallback?))
               "ScopedValueBackend" "ThreadLocalBackend")
             (ScopedRuntime/backendName))))))


(deftest dynamic-binding-fallback-test
  (binding [*with-default* :thread-bound]
    (is (= :thread-bound (ask *with-default*)))
    (is (= :thread-bound (ask *with-default* :fallback)))
    (scoping [*with-default* nil]
      (is (nil? (ask *with-default* :fallback))))
    (is (= :thread-bound (ask *with-default*)))))


(deftest throwable-cleanup-test
  (let [failure (Error. "body failed")]
    (scoping [*with-default* :outer]
      (is (identical? failure
            (try (with-scope (assoc-scope {} *with-default* :inner) (throw failure))
              (catch Throwable t t))))
      (is (= :outer (ask *with-default*))))))


;; # Virtual thread integration
;; ################################################################################
(deftest virtual-thread-test
  (testing "scope does NOT auto-propagate to virtual threads"
    (let [result (promise)]
      (scoping [*with-default* :parent-scope]
        (-> (Thread/startVirtualThread
              (fn []
                ;; Without explicit scope restoration, we get root binding
                (deliver result (ask *with-default*))))
          (.join)))
      (is (= :default-value @result)
        "Virtual thread sees root binding, not parent scope")))

  (testing "scope propagates to virtual thread via capture/restore"
    (let [result (promise)]
      (scoping [*with-default* :parent-scope]
        (let [scope (current-scope)]
          (-> (Thread/startVirtualThread
                (fn []
                  (with-scope scope
                    (deliver result (ask *with-default*)))))
            (.join))))
      (is (= :parent-scope @result))))

  (testing "multiple virtual threads can share captured scope"
    (let [results  (atom [])
          captured (scoping [*with-default* :shared]
                     (current-scope))
          threads  (mapv (fn [i]
                           (Thread/startVirtualThread
                             (fn []
                               (with-scope captured
                                 (swap! results conj [(ask *with-default*) i])))))
                     (range 5))]
      (run! #(.join %) threads)
      (is (= 5 (count @results)))
      (is (every? #(= :shared (first %)) @results))))

  (testing "each virtual thread can have its own scope"
    (let [results (atom {})]
      (doseq [i (range 3)]
        (let [thread-scope (scoping [*another* (keyword (str "thread-" i))]
                             (current-scope))]
          (-> (Thread/startVirtualThread
                (fn []
                  (with-scope thread-scope
                    (swap! results assoc i (ask *another*)))))
            (.join))))
      (is (= {0 :thread-0, 1 :thread-1, 2 :thread-2} @results)))))


;; # with-scope callback pattern (uses promise)
;; ################################################################################
(deftest with-scope-callback-test
  (testing "with-scope can be used to pass scope to callbacks"
    (let [result   (promise)
          captured (scoping [*with-default* :from-outer]
                     (current-scope))]
      ;; Simulate callback execution
      (with-scope captured
        (deliver result (ask *with-default*)))
      (is (= :from-outer @result)))))
