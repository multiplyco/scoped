(ns co.multiply.scoped-test
  "Tests for scoped values library."
  (:require
    [clojure.test :refer [deftest is testing]]
    [co.multiply.scoped :refer [ask assoc-scope current-scope scoping with-scope]]
    [co.multiply.scoped.impl :as impl]))


;; Test vars with different initial states
(def ^:dynamic *with-default* :default-value)
(def ^:dynamic *another* :another-default)
(def ^:dynamic *unbound*)


;; # Implementation detection
;; ################################################################################
(deftest implementation-test
  (testing "carrier type matches expected implementation"
    (let [force-fallback? (= (System/getProperty "co.multiply.scoped.force-fallback") "true")
          jdk-25+?        (>= (.feature (Runtime/version)) 25)
          carrier         @#'impl/carrier]
      (if (and jdk-25+? (not force-fallback?))
        (is (instance? java.lang.ScopedValue carrier)
          "Expected ScopedValue on JDK 25+ without force-fallback")
        (is (instance? ThreadLocal carrier)
          "Expected ThreadLocal on JDK < 25 or with force-fallback")))))


;; # Basic scoping and ask
;; ################################################################################
(deftest basic-scoping-test
  (testing "ask returns scoped value within scoping block"
    (scoping [*with-default* :scoped-value]
      (is (= :scoped-value (ask *with-default*)))))

  (testing "ask returns root binding outside scoping block"
    (is (= :default-value (ask *with-default*))))

  (testing "multiple bindings in single scoping block"
    (scoping [*with-default* :one
              *another*      :two]
      (is (= :one (ask *with-default*)))
      (is (= :two (ask *another*)))))

  (testing "scoping returns value of body"
    (is (= :result
          (scoping [*with-default* :ignored]
            :result)))))


;; # Nested scoping
;; ################################################################################
(deftest nested-scoping-test
  (testing "inner scoping shadows outer"
    (scoping [*with-default* :outer]
      (is (= :outer (ask *with-default*)))
      (scoping [*with-default* :inner]
        (is (= :inner (ask *with-default*))))
      (is (= :outer (ask *with-default*)))))

  (testing "inner scoping can add new bindings while preserving outer"
    (scoping [*with-default* :outer]
      (scoping [*another* :inner-another]
        (is (= :outer (ask *with-default*)))
        (is (= :inner-another (ask *another*))))))

  (testing "deeply nested scoping"
    (scoping [*with-default* :level-1]
      (scoping [*with-default* :level-2]
        (scoping [*with-default* :level-3]
          (is (= :level-3 (ask *with-default*))))
        (is (= :level-2 (ask *with-default*))))
      (is (= :level-1 (ask *with-default*))))))


;; # Unbound var behavior
;; ################################################################################
(deftest unbound-var-test
  (testing "ask throws for unbound var outside scoping"
    (is (thrown? IllegalStateException
          (ask *unbound*))))

  (testing "ask returns scoped value for otherwise unbound var"
    (scoping [*unbound* :now-bound]
      (is (= :now-bound (ask *unbound*)))))

  (testing "nil is a valid scoped value"
    (scoping [*with-default* nil]
      (is (nil? (ask *with-default*)))))

  (testing "false is a valid scoped value"
    (scoping [*with-default* false]
      (is (= false (ask *with-default*))))))


;; # ask with default value
;; ################################################################################
(deftest ask-with-default-test
  (testing "ask with default returns scoped value when in scope"
    (scoping [*with-default* :scoped]
      (is (= :scoped (ask *with-default* :fallback)))))

  (testing "ask with default returns root binding when not in scope"
    (is (= :default-value (ask *with-default* :fallback))))

  (testing "ask with default returns default for unbound var"
    (is (= :fallback (ask *unbound* :fallback))))

  (testing "ask with default does not use default when scoped to nil"
    (scoping [*with-default* nil]
      (is (nil? (ask *with-default* :fallback)))))

  (testing "ask with default does not use default when scoped to false"
    (scoping [*with-default* false]
      (is (= false (ask *with-default* :fallback))))))


;; # current-scope and with-scope
;; ################################################################################
(deftest current-scope-test
  (testing "current-scope returns empty map outside scoping"
    (is (= {} (current-scope))))

  (testing "current-scope returns scope map with bindings"
    (scoping [*with-default* :value]
      (let [scope (current-scope)]
        (is (map? scope))
        (is (= :value (get scope #'*with-default*))))))

  (testing "current-scope reflects all active bindings"
    (scoping [*with-default* :one
              *another*      :two]
      (let [scope (current-scope)]
        (is (= :one (get scope #'*with-default*)))
        (is (= :two (get scope #'*another*)))))))


(deftest with-scope-test
  (testing "with-scope restores a captured scope"
    (let [captured (scoping [*with-default* :captured]
                     (current-scope))]
      ;; Outside original scoping block
      (is (= :default-value (ask *with-default*)))
      ;; Restore the captured scope
      (with-scope captured
        (is (= :captured (ask *with-default*))))))

  (testing "with-scope can be used to pass scope to callbacks"
    (let [result   (promise)
          captured (scoping [*with-default* :from-outer]
                     (current-scope))]
      ;; Simulate callback execution
      (with-scope captured
        (deliver result (ask *with-default*)))
      (is (= :from-outer @result))))

  (testing "with-scope returns value of body"
    (let [scope (scoping [*with-default* :x] (current-scope))]
      (is (= :body-result
            (with-scope scope
              :body-result)))))

  (testing "with-scope restores previous scope after normal exit"
    (scoping [*with-default* :outer]
      (let [inner-scope (assoc-scope {} *with-default* :inner)]
        (with-scope inner-scope
          (is (= :inner (ask *with-default*))))
        (is (= :outer (ask *with-default*))))))

  (testing "with-scope restores previous scope after exception"
    (scoping [*with-default* :outer]
      (let [inner-scope (assoc-scope {} *with-default* :inner)]
        (try
          (with-scope inner-scope
            (is (= :inner (ask *with-default*)))
            (throw (ex-info "test error" {})))
          (catch Exception _)))
      (is (= :outer (ask *with-default*))))))


(deftest assoc-scope-test
  (testing "assoc-scope adds binding to captured scope"
    (let [base     (scoping [*with-default* :base] (current-scope))
          extended (assoc-scope base *another* :added)]
      (with-scope extended
        (is (= :base (ask *with-default*)))
        (is (= :added (ask *another*))))))

  (testing "assoc-scope can override existing binding"
    (let [base     (scoping [*with-default* :original] (current-scope))
          extended (assoc-scope base *with-default* :overridden)]
      (with-scope extended
        (is (= :overridden (ask *with-default*))))))

  (testing "assoc-scope with multiple bindings"
    (let [extended (assoc-scope {} *with-default* :one *another* :two)]
      (with-scope extended
        (is (= :one (ask *with-default*)))
        (is (= :two (ask *another*))))))

  (testing "assoc-scope does not modify original scope"
    (let [original (scoping [*with-default* :original] (current-scope))
          _        (assoc-scope original *with-default* :modified)]
      (with-scope original
        (is (= :original (ask *with-default*)))))))


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


;; # Many bindings (exercises merge-bindings path)
;; ################################################################################
(def ^:dynamic *var-01*)
(def ^:dynamic *var-02*)
(def ^:dynamic *var-03*)
(def ^:dynamic *var-04*)
(def ^:dynamic *var-05*)
(def ^:dynamic *var-06*)
(def ^:dynamic *var-07*)
(def ^:dynamic *var-08*)
(def ^:dynamic *var-09*)
(def ^:dynamic *var-10*)
(def ^:dynamic *var-11*)
(def ^:dynamic *var-12*)

(deftest many-bindings-test
  (testing "10+ bindings triggers merge-bindings path"
    (scoping [*var-01* 1
              *var-02* 2
              *var-03* 3
              *var-04* 4
              *var-05* 5
              *var-06* 6
              *var-07* 7
              *var-08* 8
              *var-09* 9
              *var-10* 10
              *var-11* 11
              *var-12* 12]
      (is (= 1 (ask *var-01*)))
      (is (= 6 (ask *var-06*)))
      (is (= 12 (ask *var-12*)))
      (is (= {#'*var-01* 1 #'*var-02* 2 #'*var-03* 3
              #'*var-04* 4 #'*var-05* 5 #'*var-06* 6
              #'*var-07* 7 #'*var-08* 8 #'*var-09* 9
              #'*var-10* 10 #'*var-11* 11 #'*var-12* 12}
            (current-scope)))))

  (testing "many bindings via assoc-scope"
    (let [scope (assoc-scope {}
                  *var-01* :a *var-02* :b *var-03* :c
                  *var-04* :d *var-05* :e *var-06* :f
                  *var-07* :g *var-08* :h *var-09* :i
                  *var-10* :j *var-11* :k *var-12* :l)]
      (with-scope scope
        (is (= :a (ask *var-01*)))
        (is (= :f (ask *var-06*)))
        (is (= :l (ask *var-12*))))))

  (testing "nested scoping with many bindings restores correctly"
    (scoping [*var-01* :outer-1
              *var-02* :outer-2
              *var-03* :outer-3
              *var-04* :outer-4
              *var-05* :outer-5
              *var-06* :outer-6
              *var-07* :outer-7
              *var-08* :outer-8
              *var-09* :outer-9
              *var-10* :outer-10]
      (scoping [*var-01* :inner-1
                *var-02* :inner-2
                *var-03* :inner-3
                *var-04* :inner-4
                *var-05* :inner-5
                *var-06* :inner-6
                *var-07* :inner-7
                *var-08* :inner-8
                *var-09* :inner-9
                *var-10* :inner-10]
        (is (= :inner-1 (ask *var-01*)))
        (is (= :inner-10 (ask *var-10*))))
      (is (= :outer-1 (ask *var-01*)))
      (is (= :outer-10 (ask *var-10*))))))


;; # Edge cases
;; ################################################################################
(deftest edge-cases-test
  (testing "empty scoping block is valid"
    (is (= :body (scoping [] :body))))

  (testing "scoping with same var multiple times uses last value"
    (scoping [*with-default* :first
              *with-default* :second]
      (is (= :second (ask *with-default*)))))

  (testing "scope survives exception in body"
    (scoping [*with-default* :outer]
      (try
        (scoping [*with-default* :inner]
          (throw (ex-info "test" {})))
        (catch Exception _))
      (is (= :outer (ask *with-default*))))))
