(ns co.multiply.scoped-bench
  "Compare the original associations, function wrappers, and macro wrappers."
  (:require [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [clojure.walk :as walk]
    [co.multiply.scoped.helpers :as h]
    [co.multiply.scoped.impl :as impl]
    [criterium.core :as criterium]))


(defn persistent-assoc-function
  [m k v]
  (if (identical? h/skip v) m (h/persistentAssoc m k v)))


(defn transient-assoc-function
  [m k v]
  (if (identical? h/skip v) m (h/transientAssoc m k v)))


;; Match impl/merge-resolved-bindings, changing only the association operation.
(defmacro define-merge
  [name assoc-op]
  `(defn ~name
     [scope# bindings#]
     (let [bindings-count# (h/vecCount bindings#)]
       (loop [var-idx# (unchecked-int 0)
              scope# (h/asTransient scope#)]
         (if (< var-idx# bindings-count#)
           (let [value-idx# (unchecked-inc-int var-idx#)]
             (recur (unchecked-inc-int value-idx#)
               (~assoc-op scope#
                          (h/vecNth bindings# var-idx#)
                          (h/vecNth bindings# value-idx#))))
           (h/asPersistent scope#))))))


(define-merge merge-baseline h/transientAssoc)
(define-merge merge-function transient-assoc-function)


(defmacro extend-variant
  [variant scope bindings]
  (let [replacements (case variant
                       :baseline {'co.multiply.scoped.helpers/persistentAssocSkip
                                  'co.multiply.scoped.helpers/persistentAssoc
                                  'co.multiply.scoped.helpers/transientAssocSkip
                                  'co.multiply.scoped.helpers/transientAssoc
                                  'co.multiply.scoped.impl/merge-resolved-bindings
                                  'co.multiply.scoped-bench/merge-baseline}
                       :function {'co.multiply.scoped.helpers/persistentAssocSkip
                                  'co.multiply.scoped-bench/persistent-assoc-function
                                  'co.multiply.scoped.helpers/transientAssocSkip
                                  'co.multiply.scoped-bench/transient-assoc-function
                                  'co.multiply.scoped.impl/merge-resolved-bindings
                                  'co.multiply.scoped-bench/merge-function}
                       :macro    {})]
    (walk/postwalk-replace replacements
      (macroexpand-1 `(impl/extend-scope ~scope ~bindings)))))


;; Real, distinct Var keys, resolved at compile time as in ordinary scoping.
(doseq [i (range 12)]
  (intern *ns* (symbol (str "*value-" i "*"))))


(defn input-frames
  "Precompute a repeatable 50% skip workload; no RNG calls are timed."
  [values]
  (let [rng (java.util.Random. 20260911)]
    (mapv (fn [_]
            (mapv #(if (.nextBoolean rng) h/skip %) values))
      (range 256))))


(defn make-kernel
  "Compile outside the timed region. Values come from a runtime object array."
  ([variant binding-count scope values]
   (make-kernel variant binding-count scope values false))
  ([variant binding-count scope values changing?]
   (let [scope-sym (gensym "scope")
         values-sym (with-meta (gensym "values") {:tag 'objects})
         bindings (vec (mapcat (fn [i]
                                 [(symbol "co.multiply.scoped-bench" (str "*value-" i "*"))
                                  `(aget ~values-sym ~i)])
                         (range binding-count)))
         body `(extend-variant ~variant ~scope-sym ~bindings)]
     (if changing?
       (let [frames-sym (with-meta (gensym "frames") {:tag 'objects})
             cursor-sym (with-meta (gensym "cursor") {:tag 'longs})
             factory (eval `(fn [~scope-sym ~frames-sym ~cursor-sym]
                              (fn []
                                (let [i# (aget ~cursor-sym 0)
                                      ~values-sym (aget ~frames-sym i#)]
                                  (aset ~cursor-sym 0 (bit-and 255 (unchecked-inc i#)))
                                  ~body))))]
         (factory scope (object-array (map object-array (input-frames values)))
           (long-array [0])))
       (let [factory (eval `(fn [~scope-sym ~values-sym] (fn [] ~body)))]
         (factory scope (object-array values)))))))


(defn binding-vars
  [n]
  (mapv #(ns-resolve 'co.multiply.scoped-bench (symbol (str "*value-" % "*")))
    (range n)))


(defn input-values
  [workload n]
  (mapv (fn [i]
          (case workload
            (:values :changing) (case (mod i 3) 0 :new 1 nil 2 false)
            :mixed  (if (even? i) h/skip :new)
            :skip   h/skip))
    (range n)))


(defn verify!
  "Check both skip variants and all construction boundaries before measuring."
  []
  (doseq [n [0 1 2 9 10 11]
          scope [{} (zipmap (binding-vars 12) (repeat :outer))]
          workload [:values :mixed :skip]]
    (let [values (input-values workload n)
          pairs (map vector (binding-vars n) values)
          expected (reduce (fn [m [k v]]
                             (if (identical? h/skip v) m (assoc m k v)))
                     scope pairs)]
      (doseq [variant [:function :macro]]
        (assert (= expected ((make-kernel variant n scope values)))
          (str "Incorrect result: " [variant n workload])))
      (when (= workload :values)
        (assert (= expected ((make-kernel :baseline n scope values)))))))
  (println "Verified values, nil, false, absence, and inheritance at 0/1/2/9/10/11 bindings."))


(defn verify-changing!
  []
  (doseq [n [1 2 10]
          variant [:function :macro]
          scope [{} (zipmap (binding-vars 12) (repeat :outer))]]
    (let [values (input-values :values n)
          kernel (make-kernel variant n scope values true)]
      (doseq [frame (input-frames values)]
        (assert (= (reduce (fn [m [k v]]
                             (if (identical? h/skip v) m (assoc m k v)))
                     scope (map vector (binding-vars n) frame))
                   (kernel))))))
  (println "Verified all 256 changing input frames."))


(def default-options
  {:bindings [1 2 10]
   :workloads [:values :mixed :skip]
   :scope :empty
   :reverse? false
   :criterium {:samples 30
               :warmup-jit-period 5000000000
               :target-execution-time 100000000
               :bootstrap-size 1000}})


(defn run-group
  [{:keys [scope reverse? criterium]} n workload]
  (let [scope-map (case scope
                    :empty {}
                    :inherited (zipmap (binding-vars 12) (repeat :outer)))
        values (input-values workload n)
        variants (cond-> (if (= workload :values)
                           [:baseline :function :macro]
                           [:function :macro])
                   reverse? reverse)
        kernels (mapv #(hash-map :f (make-kernel % n scope-map values (= workload :changing))
                         :expr-string (name %)) variants)]
    (printf "\n%d bindings, %s, %s scope; order %s\n" n (name workload) (name scope) variants)
    (flush)
    ;; At most three expressions: Criterium 0.4.6 preserves their order here.
    ;; Return each map to Criterium's result sink. No overhead subtraction.
    (mapv (fn [variant result]
            (let [mean-ns (* 1e9 (first (:mean result)))
                  ci-ns (mapv #(* 1e9 %) (second (:mean result)))]
              (printf "  %-8s %9.2f ns  95%% CI [%9.2f, %9.2f]\n"
                (name variant) mean-ns (first ci-ns) (second ci-ns))
              (flush)
              {:bindings n :workload workload :scope scope :variant variant
               :mean-ns mean-ns :ci-ns ci-ns
               :criterium (dissoc result :results)}))
      variants
      (criterium/benchmark-round-robin* kernels criterium))))


(defn -main
  [& [options-edn]]
  (let [provided (if options-edn (edn/read-string options-edn) {})
        options (-> (merge default-options provided)
                  (update :criterium #(merge (:criterium default-options) %)))
        output (or (:output options) "target/bench/skip.edn")]
    (verify!)
    (when (some #{:changing} (:workloads options))
      (verify-changing!))
    (when-not (:verify-only? options)
      (println "Java" (System/getProperty "java.runtime.version")
        "Clojure" (clojure-version) "on" (System/getProperty "os.arch"))
      (println "Options:" (pr-str options))
      (io/make-parents output)
      (let [results (atom [])
            save! #(spit output
                     (with-out-str
                       (pprint/pprint
                         {:recorded-at (str (java.time.Instant/now))
                          :options options :results @results})))]
        (doseq [n (:bindings options)
                workload (:workloads options)
                ;; For one binding :mixed is identical to :skip.
                :when (not (and (= n 1) (= workload :mixed)))]
          (swap! results into (run-group options n workload))
          (save!))
        (println "\nResults saved to" output)))))
