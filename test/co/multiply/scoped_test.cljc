(ns co.multiply.scoped-test
  "Tests for scoped values library."
  (:require
    [clojure.test :refer [deftest is testing]]
    [co.multiply.scoped :refer [ask assoc-scope current-scope scoping skip with-scope]]
    [co.multiply.scoped.helpers :as h]))


;; Test vars with different initial states
(def ^:dynamic *with-default* :default-value)
(def ^:dynamic *another* :another-default)
(def ^:dynamic *unbound*)
(def ^:dynamic *nil-root* nil)
(def ^:dynamic *false-root* false)


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
             :result))))

  (testing "scoping restores root binding after block exits"
    (is (= :default-value (ask *with-default*)))
    (scoping [*with-default* :temporary]
      (is (= :temporary (ask *with-default*))))
    (is (= :default-value (ask *with-default*))))

  (testing "single binding (exercises 1-binding path)"
    (scoping [*with-default* :single]
      (is (= :single (ask *with-default*))))))


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
    (is (thrown? #?(:clj IllegalStateException :cljs js/Error)
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


(deftest opaque-values-test
  (testing "scoped values are returned by identity, including internal-looking keywords"
    (doseq [value [:co.multiply.scoped.impl/not-found
                   (fn [] :value)
                   #?(:clj (Object.) :cljs (js/Object.))]]
      (scoping [*unbound* value]
        (is (identical? value (ask *unbound*)))
        (is (identical? value (ask *unbound* :fallback)))))))


(deftest binding-evaluation-failure-test
  (testing "a failing binding leaves the parent scope intact and skips later forms"
    (let [calls (atom [])
          failure (ex-info "binding failed" {})]
      (scoping [*with-default* :outer]
        (is (identical? failure
              (try
                (scoping [*with-default* (do (swap! calls conj :first) :inner)
                          *another* (throw failure)
                          *unbound* (swap! calls conj :last)]
                  (swap! calls conj :body))
                (catch #?(:clj Exception :cljs :default) e e))))
        (is (= [:first] @calls))
        (is (= :outer (ask *with-default*)))))))


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

  (testing "ask with default returns nil when explicitly scoped to nil"
    (scoping [*with-default* nil]
      (is (nil? (ask *with-default* :fallback))
        "scoping to nil explicitly 'unsets' the var")))

  (testing "ask with default does not use default when scoped to false"
    (scoping [*with-default* false]
      (is (= false (ask *with-default* :fallback)))))

  (testing "ask with default and var bound to nil at root"
    ;; CLJ can distinguish nil-bound from unbound, CLJS cannot
    #?(:clj  (is (nil? (ask *nil-root* :fallback))
               "CLJ: var bound to nil returns nil, not default")
       :cljs (is (= :fallback (ask *nil-root* :fallback))
               "CLJS: var bound to nil is indistinguishable from unbound"))))


(deftest ask-default-evaluation-test
  (testing "default is not evaluated when a root binding is available"
    (let [calls     (atom 0)
          otherwise (fn [] (swap! calls inc) :fallback)]
      (is (= :default-value (ask *with-default* (otherwise))))
      (is (false? (ask *false-root* (otherwise))))
      (is (zero? @calls))))

  (testing "default is not evaluated when a scoped value is available"
    (doseq [value [:scoped nil false]]
      (let [calls (atom 0)]
        (scoping [*unbound* value]
          (is (= value (ask *unbound* (swap! calls inc)))))
        (is (zero? @calls)))))

  (testing "default is evaluated once per ask when needed"
    (let [calls     (atom 0)
          otherwise (fn [] (swap! calls inc) :fallback)]
      (is (= :fallback (ask *unbound* (otherwise))))
      (is (= 1 @calls))
      (is (= :fallback (ask *unbound* (otherwise))))
      (is (= 2 @calls))))

  (testing "nil root binding only evaluates the default in CLJS"
    (let [calls (atom 0)]
      (is (= #?(:clj nil :cljs 1) (ask *nil-root* (swap! calls inc))))
      (is (= #?(:clj 0 :cljs 1) @calls))))

  (testing "default results are returned as-is, including nil, false and functions"
    (doseq [fallback [nil false (fn [] :fallback)]]
      (let [calls (atom 0)]
        (is (= fallback (ask *unbound* (do (swap! calls inc) fallback))))
        (is (= 1 @calls)))))

  (testing "a throwing default is only evaluated when needed"
    (is (= :default-value
           (ask *with-default* (throw (ex-info "fallback" {})))))
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo)
                          #"fallback"
          (ask *unbound* (throw (ex-info "fallback" {})))))))


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
          (catch #?(:clj Exception :cljs :default) _)))
      (is (= :outer (ask *with-default*)))))

  (testing "with-scope restores root binding after block exits"
    (let [scope (assoc-scope {} *with-default* :temporary)]
      (is (= :default-value (ask *with-default*)))
      (with-scope scope
        (is (= :temporary (ask *with-default*))))
      (is (= :default-value (ask *with-default*)))))

  (testing "with-scope with empty scope"
    (scoping [*with-default* :outer]
      (with-scope {}
        (is (= :default-value (ask *with-default*)))
        "empty scope means no bindings, falls back to root")))

  (testing "current-scope inside with-scope reflects restored scope"
    (let [captured (scoping [*with-default* :captured
                             *another*      :also-captured]
                     (current-scope))]
      (with-scope captured
        (let [inner-scope (current-scope)]
          (is (= :captured (get inner-scope #'*with-default*)))
          (is (= :also-captured (get inner-scope #'*another*)))))))

  (testing "nested with-scope"
    (let [outer-scope (assoc-scope {} *with-default* :outer)
          inner-scope (assoc-scope {} *with-default* :inner)]
      (with-scope outer-scope
        (is (= :outer (ask *with-default*)))
        (with-scope inner-scope
          (is (= :inner (ask *with-default*))))
        (is (= :outer (ask *with-default*)))))))


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
        (is (= :original (ask *with-default*))))))

  (testing "assoc-scope with single binding (exercises 1-binding path)"
    (let [extended (assoc-scope {} *with-default* :single)]
      (with-scope extended
        (is (= :single (ask *with-default*))))))

  (testing "assoc-scope with zero additional bindings returns scope unchanged"
    (let [original (scoping [*with-default* :original] (current-scope))
          same     (assoc-scope original)]
      (is (= original same)))))


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
      ;; Verify scope contains all bindings
      (let [scope (current-scope)]
        (is (= 1 (get scope #'*var-01*)))
        (is (= 12 (get scope #'*var-12*))))))

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
        (catch #?(:clj Exception :cljs :default) _))
      (is (= :outer (ask *with-default*)))))

  (testing "expression values in bindings (not just literals)"
    (let [compute-value (fn [] :computed)]
      (scoping [*with-default* (compute-value)
                *another*      (keyword (str "dynamic-" 123))]
        (is (= :computed (ask *with-default*)))
        (is (= :dynamic-123 (ask *another*))))))

  (testing "with-scope inside scoping"
    (let [captured (assoc-scope {} *another* :from-with-scope)]
      (scoping [*with-default* :from-scoping]
        (with-scope captured
          ;; with-scope replaces entire scope, not additive
          (is (= :default-value (ask *with-default*)))
          (is (= :from-with-scope (ask *another*)))))))

  (testing "scoping inside with-scope"
    (let [captured (assoc-scope {} *with-default* :from-with-scope)]
      (with-scope captured
        (scoping [*another* :from-scoping]
          ;; scoping extends current scope
          (is (= :from-with-scope (ask *with-default*)))
          (is (= :from-scoping (ask *another*))))))))


;; # Binding count boundary cases
;; ################################################################################
(deftest binding-count-boundaries-test
  (testing "9 bindings (upper boundary of 2-9 transient path)"
    (scoping [*var-01* :a
              *var-02* :b
              *var-03* :c
              *var-04* :d
              *var-05* :e
              *var-06* :f
              *var-07* :g
              *var-08* :h
              *var-09* :i]
      (is (= :a (ask *var-01*)))
      (is (= :i (ask *var-09*)))))

  (testing "10 bindings (lower boundary of merge-bindings path)"
    (scoping [*var-01* 1
              *var-02* 2
              *var-03* 3
              *var-04* 4
              *var-05* 5
              *var-06* 6
              *var-07* 7
              *var-08* 8
              *var-09* 9
              *var-10* 10]
      (is (= 1 (ask *var-01*)))
      (is (= 10 (ask *var-10*)))))

  (testing "assoc-scope with 9 bindings"
    (let [scope (assoc-scope {}
                  *var-01* :a *var-02* :b *var-03* :c
                  *var-04* :d *var-05* :e *var-06* :f
                  *var-07* :g *var-08* :h *var-09* :i)]
      (with-scope scope
        (is (= :a (ask *var-01*)))
        (is (= :i (ask *var-09*))))))

  (testing "assoc-scope with 10 bindings"
    (let [scope (assoc-scope {}
                  *var-01* 1 *var-02* 2 *var-03* 3
                  *var-04* 4 *var-05* 5 *var-06* 6
                  *var-07* 7 *var-08* 8 *var-09* 9
                  *var-10* 10)]
      (with-scope scope
        (is (= 1 (ask *var-01*)))
        (is (= 10 (ask *var-10*)))))))


;; # Conditional bindings
;; ################################################################################
(deftest skip-test
  (testing "skip preserves absence and leaves defaults up to each reader"
    (scoping [*unbound* skip]
      (is (not (contains? (current-scope) #'*unbound*)))
      (is (= [:a :b] [(ask *unbound* :a) (ask *unbound* :b)]))
      (is (thrown? #?(:clj IllegalStateException :cljs js/Error)
            (ask *unbound*)))))

  (testing "skip preserves fallback to the var's value"
    (scoping [*with-default* skip]
      (is (= :default-value (ask *with-default* :fallback)))))

  (testing "skip inherits nil, false, and ordinary values without using defaults"
    (doseq [value [nil false :outer]]
      (scoping [*unbound* value]
        (let [outer (current-scope)
              captured (scoping [*unbound* skip *another* :inner]
                         (is (= value (ask *unbound* (throw (ex-info "unexpected default" {})))))
                         (current-scope))]
          (with-scope captured
            (is (= value (ask *unbound* :fallback)))
            (is (= :inner (ask *another*))))
          (is (= outer (current-scope)))))))

  (testing "a skipped duplicate binding preserves the preceding binding"
    (scoping [*unbound* :first *unbound* skip]
      (is (= :first (ask *unbound*)))))

  (testing "binding expressions can choose to skip, including when values are nil"
    (doseq [available? [false true]]
      (scoping [*unbound* (if available? nil skip)]
        (is (= (when-not available? :fallback) (ask *unbound* :fallback)))))))


(deftest skip-construction-paths-test
  (doseq [[n extend] [[1 (fn [scope value record]
                           (assoc-scope (record 0 scope) *var-01* (record 1 value)))]
                      [2 (fn [scope value record]
                           (assoc-scope (record 0 scope)
                             *var-01* (record 1 value) *var-02* (record 2 skip)))]
                      [9 (fn [scope value record]
                           (assoc-scope (record 0 scope)
                             *var-01* (record 1 value) *var-02* (record 2 skip)
                             *var-03* (record 3 skip) *var-04* (record 4 skip)
                             *var-05* (record 5 skip) *var-06* (record 6 skip)
                             *var-07* (record 7 skip) *var-08* (record 8 skip)
                             *var-09* (record 9 skip)))]
                      [10 (fn [scope value record]
                            (assoc-scope (record 0 scope)
                              *var-01* (record 1 value) *var-02* (record 2 skip)
                              *var-03* (record 3 skip) *var-04* (record 4 skip)
                              *var-05* (record 5 skip) *var-06* (record 6 skip)
                              *var-07* (record 7 skip) *var-08* (record 8 skip)
                              *var-09* (record 9 skip) *var-10* (record 10 skip)))]]
          scope [{} (assoc-scope {} *var-01* :outer *var-02* :inherited)]
          value [skip nil false :new #?(:clj (Object.) :cljs (js/Object.))]]
    (testing (str n " bindings preserve scope contents and evaluate forms once, in order")
      (let [calls (atom [])
            record (fn [i v] (swap! calls conj i) v)
            result (extend scope value record)]
        (is (= (if (identical? skip value) scope (assoc scope #'*var-01* value))
               result))
        (is (= (vec (range (inc n))) @calls))))))


(deftest assoc-wrapper-evaluation-test
  (doseq [value [skip nil false :value]]
    (let [calls (atom [])
          record (fn [k v] (swap! calls conj k) v)
          expected (if (identical? skip value) {} {:key value})]
      (is (= expected (h/persistentAssocSkip (record :map {})
                        (record :key :key) (record :value value))))
      (is (= [:map :key :value] @calls))
      (reset! calls [])
      (is (= expected (persistent!
                        (h/transientAssocSkip (record :map (transient {}))
                          (record :key :key) (record :value value)))))
      (is (= [:map :key :value] @calls)))))


(deftest fixed-arity-values-test
  (doseq [[n extend]
          [[3 (fn [scope values]
                (assoc-scope scope
                  *var-01* (nth values 0)
                  *var-02* (nth values 1)
                  *var-03* (nth values 2)))]
           [4 (fn [scope values]
                (assoc-scope scope
                  *var-01* (nth values 0)
                  *var-02* (nth values 1)
                  *var-03* (nth values 2)
                  *var-04* (nth values 3)))]
           [5 (fn [scope values]
                (assoc-scope scope
                  *var-01* (nth values 0)
                  *var-02* (nth values 1)
                  *var-03* (nth values 2)
                  *var-04* (nth values 3)
                  *var-05* (nth values 4)))]
           [6 (fn [scope values]
                (assoc-scope scope
                  *var-01* (nth values 0)
                  *var-02* (nth values 1)
                  *var-03* (nth values 2)
                  *var-04* (nth values 3)
                  *var-05* (nth values 4)
                  *var-06* (nth values 5)))]
           [7 (fn [scope values]
                (assoc-scope scope
                  *var-01* (nth values 0)
                  *var-02* (nth values 1)
                  *var-03* (nth values 2)
                  *var-04* (nth values 3)
                  *var-05* (nth values 4)
                  *var-06* (nth values 5)
                  *var-07* (nth values 6)))]
           [8 (fn [scope values]
                (assoc-scope scope
                  *var-01* (nth values 0)
                  *var-02* (nth values 1)
                  *var-03* (nth values 2)
                  *var-04* (nth values 3)
                  *var-05* (nth values 4)
                  *var-06* (nth values 5)
                  *var-07* (nth values 6)
                  *var-08* (nth values 7)))]]
          base [{} (assoc-scope {} *var-01* :inherited *var-02* :outer)]
          mode [:mixed :skip]]
    (let [vars [#'*var-01* #'*var-02* #'*var-03* #'*var-04*
                #'*var-05* #'*var-06* #'*var-07* #'*var-08*]
          values (mapv (fn [i]
                         (if (= mode :skip) skip
                             (case (mod i 4) 0 nil 1 false 2 skip 3 [i :value])))
                   (range n))
          expected (reduce (fn [scope [k v]]
                             (if (identical? skip v) scope (assoc scope k v)))
                     base (map vector vars values))]
      (is (= expected (extend base values)) (str "Fixed arity " n ", " mode)))))


(deftest large-scope-updates-test
  (let [vars [#'*var-01* #'*var-02* #'*var-03* #'*var-04*
              #'*var-05* #'*var-06* #'*var-07* #'*var-08*
              #'*var-09* #'*var-10* #'*var-11* #'*var-12*]
        cases [[11 (fn [scope parent value]
                     (assoc-scope (parent scope)
                       *var-01* (value 0) *var-02* (value 1)
                       *var-03* (value 2) *var-04* (value 3)
                       *var-05* (value 4) *var-06* (value 5)
                       *var-07* (value 6) *var-08* (value 7)
                       *var-09* (value 8) *var-10* (value 9)
                       *var-11* (value 10)))]
               [20 (fn [scope parent value]
                     (assoc-scope (parent scope)
                       *var-01* (value 0) *var-02* (value 1)
                       *var-03* (value 2) *var-04* (value 3)
                       *var-05* (value 4) *var-06* (value 5)
                       *var-07* (value 6) *var-08* (value 7)
                       *var-09* (value 8) *var-10* (value 9)
                       *var-11* (value 10) *var-12* (value 11)
                       *var-01* (value 12) *var-02* (value 13)
                       *var-03* (value 14) *var-04* (value 15)
                       *var-05* (value 16) *var-06* (value 17)
                       *var-07* (value 18) *var-08* (value 19)))]]
        parents [{}
                 (zipmap (take 8 vars) (repeat :inherited))
                 (zipmap (concat vars (range 20)) (repeat :inherited))]]
    (doseq [[n extend] cases scope parents mode [:mixed :skip]]
      (testing (str n " bindings on " (count scope) " entries, " mode)
        (let [calls (atom [])
              parent (fn [p] (swap! calls conj :parent) p)
              values (mapv (fn [i]
                             (if (= mode :skip) skip
                                 (case (mod i 4) 0 nil 1 false 2 skip 3 [i :value])))
                       (range n))
              value (fn [i] (swap! calls conj i) (nth values i))
              snapshot (vec scope)
              expected (reduce (fn [m [k v]] (if (identical? skip v) m (assoc m k v)))
                         scope (map vector (cycle vars) values))
              result (extend scope parent value)]
          (is (= expected result))
          (is (= (into [:parent] (range n)) @calls))
          (is (= snapshot (vec scope)))
          (extend result identity (constantly :later))
          (is (= expected result) "Later updates preserve the captured result"))))
    (doseq [[_ extend] cases]
      (let [calls (atom [])
            parent (fn [p] (swap! calls conj :parent) p)
            failure (ex-info "binding failed" {})]
        (testing "An input exception prevents subsequent inputs from being evaluated"
          (is (identical? failure
                (try
                  (extend {} parent
                          (fn [i] (if (= i 5) (throw failure) (do (swap! calls conj i) i))))
                  (catch #?(:clj Exception :cljs :default) e e))))
          (is (= (into [:parent] (range 5)) @calls)))))))
