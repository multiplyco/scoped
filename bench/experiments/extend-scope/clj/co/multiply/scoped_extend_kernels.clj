(ns co.multiply.scoped-extend-kernels
  (:import [clojure.lang IEditableCollection ITransientAssociative ITransientCollection]
    [co.multiply.scoped MapUpdates]))


(doseq [i (range 32)]
  (intern *ns* (symbol (str "key" i)))
  (intern *ns* (symbol (str "ambient" i))))


(def ^:dynamic *events* nil)


(defn key-symbol
  [i]
  (symbol "co.multiply.scoped-extend-kernels" (str "key" i)))


(defn operation-form
  "Expand the same literal bindings three ways. Input evaluation precedes editing."
  [model keys scope values]
  (let [forms (vec (mapcat (fn [k v] [(list 'var k) v]) keys values))]
    (if (= model :array)
      ;; Freeze the pre-unrolling macro so future production changes cannot
      ;; silently turn the array baseline into another candidate.
      (let [parent (gensym "parent")
            array (with-meta (gensym "bindings") {:tag 'objects})]
        `(let [~parent ~scope
               ~array (object-array ~(count forms))]
           ~@(map-indexed (fn [i form] `(aset ~array ~i ~form)) forms)
           (MapUpdates/extendScope ~parent ~array)))
      (let [parent (gensym "parent")
            locals (vec (repeatedly (* 2 (count keys)) #(gensym "input")))
            evaluated (mapcat vector locals forms)
            initial `(IEditableCollection/.asTransient ~parent)
            updates (reduce
                      (fn [result [k v]]
                        (case model
                          :helpers `(MapUpdates/assocTransient ~result ~k ~v)
                          :inline (let [t (gensym "transient")]
                                    `(let [~t ~result]
                                       (if (identical? MapUpdates/SKIP ~v)
                                         ~t
                                         (ITransientAssociative/.assoc ~t ~k ~v))))))
                      initial (partition 2 locals))]
        `(let [~parent ~scope ~@evaluated]
           (ITransientCollection/.persistent ~updates))))))


(defmacro define-operations
  []
  `(do
     ~@(for [n [11 20] model [:array :helpers :inline]
             :let [parent (gensym "parent") values (gensym "values")]]
         `(defn ~(symbol (str (name model) "-" n)) [~parent ~values]
            ~(operation-form model (mapv key-symbol (range n)) parent
               (mapv (fn [i] `(aget ~(with-meta values {:tag 'objects}) ~(int i)))
                 (range n)))))))


(define-operations)


(defn operation
  [model n]
  (var-get (ns-resolve 'co.multiply.scoped-extend-kernels
             (symbol (str (name model) "-" n)))))


(defn custom-operation
  [model keys scope values]
  (binding [*ns* (the-ns 'co.multiply.scoped-extend-kernels)]
    (eval `(fn [] ~(operation-form model keys scope values)))))
