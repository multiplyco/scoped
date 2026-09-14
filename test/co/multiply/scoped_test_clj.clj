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
  (testing "Java backend matches multi-release JAR selection"
    (let [multi-release? (not= "false" (System/getProperty "jdk.util.jar.enableMultiRelease"))
          version (.feature (java.util.jar.JarFile/runtimeVersion))]
      (is (= (if (and multi-release? (>= version 25))
               "ScopedValueBackend" "ThreadLocalBackend")
             (ScopedRuntime/backendName))))))


(deftest dynamic-binding-fallback-test
  (binding [*with-default* :thread-bound]
    (is (= :thread-bound (ask *with-default*)))
    (is (= :thread-bound (ask *with-default* :fallback)))
    (scoping [*with-default* nil]
      (is (nil? (ask *with-default* :fallback))))
    (is (= :thread-bound (ask *with-default*)))))


(deftest scope-map-lookup-test
  (doseq [[label make-map] [[:array-map identity]
                            [:hash-map #(into (zipmap (range 16) (repeat :padding)) %)]
                            [:java-map #(java.util.HashMap. ^java.util.Map %)]]]
    (testing (str "Lookup semantics for " label)
      (with-scope (make-map {#'*with-default* nil #'*another* false})
        (is (nil? (ask *with-default* (throw (Exception. "Default evaluated"))))))
      (with-scope (make-map {#'*another* false})
        (is (false? (ask *another* :fallback)))
        (is (= :default-value (ask *with-default* :fallback))))
      (binding [*with-default* :thread-bound]
        (with-scope (make-map {})
          (is (= :thread-bound (ask *with-default* :fallback))))))))


(deftest throwable-cleanup-test
  (let [failure (Error. "body failed")]
    (scoping [*with-default* :outer]
      (is (identical? failure
            (try (with-scope (assoc-scope {} *with-default* :inner) (throw failure))
              (catch Throwable t t))))
      (is (= :outer (ask *with-default*))))))


;; # Thread integration
;; ################################################################################
(defn thread-isolation-checks
  [start-thread]
  (testing "scope does NOT auto-propagate to new threads"
    (let [result (promise)]
      (scoping [*with-default* :parent-scope]
        (-> (start-thread
              (fn []
                ;; Without explicit scope restoration, we get root binding
                (deliver result (ask *with-default*))))
          (.join)))
      (is (= :default-value @result)
        "New thread sees root binding, not parent scope")))

  (testing "scope propagates to a thread via capture/restore"
    (let [result (promise)]
      (scoping [*with-default* :parent-scope]
        (let [scope (current-scope)]
          (-> (start-thread
                (fn []
                  (with-scope scope
                    (deliver result (ask *with-default*)))))
            (.join))))
      (is (= :parent-scope @result))))

  (testing "multiple threads can share captured scope"
    (let [results  (atom [])
          captured (scoping [*with-default* :shared]
                     (current-scope))
          threads  (mapv (fn [i]
                           (start-thread
                             (fn []
                               (with-scope captured
                                 (swap! results conj [(ask *with-default*) i])))))
                     (range 5))]
      (run! #(.join %) threads)
      (is (= 5 (count @results)))
      (is (every? #(= :shared (first %)) @results))))

  (testing "each thread can have its own scope"
    (let [results (atom {})]
      (doseq [i (range 3)]
        (let [thread-scope (scoping [*another* (keyword (str "thread-" i))]
                             (current-scope))]
          (-> (start-thread
                (fn []
                  (with-scope thread-scope
                    (swap! results assoc i (ask *another*)))))
            (.join))))
      (is (= {0 :thread-0, 1 :thread-1, 2 :thread-2} @results)))))


(deftest platform-thread-test
  (thread-isolation-checks #(doto (Thread. ^Runnable %) (.start))))


(when (>= (.major (Runtime/version)) 21)
  (deftest virtual-thread-test
    ;; Resolve in the test at runtime so the same test namespace loads on JDK 17.
    (let [start (.getMethod Thread "startVirtualThread" (into-array Class [Runnable]))]
      (thread-isolation-checks #(.invoke start nil (object-array [%]))))))


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
