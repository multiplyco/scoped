(ns co.multiply.scoped-test
  "ClojureScript tests for scoped values library.

   Note: In CLJS, only `scoping` and `ask` are supported.
   `current-scope` and `with-scope` are CLJ-only features."
  (:require
    [clojure.test :refer [deftest is testing]]
    [co.multiply.scoped :refer [ask scoping]]))


;; Test vars with different initial states
(def ^:dynamic *with-default* :default-value)
(def ^:dynamic *another* :another-default)


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


;; # Nil and falsy values
;; ################################################################################
(deftest nil-and-falsy-test
  (testing "nil is a valid scoped value"
    (scoping [*with-default* nil]
      (is (nil? (ask *with-default*)))))

  (testing "false is a valid scoped value"
    (scoping [*with-default* false]
      (is (= false (ask *with-default*))))))


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
        (catch :default _))
      (is (= :outer (ask *with-default*))))))
