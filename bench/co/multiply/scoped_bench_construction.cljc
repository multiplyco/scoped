(ns co.multiply.scoped-bench-construction
  "Frozen Clojure construction kernels for the historical skip comparison."
  (:require [co.multiply.scoped.helpers :as h]))


(defn ^:no-doc merge-resolved-bindings
  "Takes a scope and a vector of bindings. Adds those bindings to the scope.

   `bindings` must be a vector, and the symbols must be resolved."
  [scope bindings]
  (let [bindings-count (h/vecCount bindings)]
    (loop [var-idx (unchecked-int 0)
           scope   (h/asTransient scope)]
      (if (< var-idx bindings-count)
        (let [value-idx (unchecked-inc-int var-idx)]
          (recur (unchecked-inc-int value-idx)
            (h/transientAssocSkip scope
              (h/vecNth bindings var-idx)
              (h/vecNth bindings value-idx))))
        (h/asPersistent scope)))))


(defmacro extend-scope
  [scope bindings]
  (assert (even? (count bindings)) "`bindings` must contain an even number of forms.")
  (let [is-cljs    (some? (:ns &env))
        pairs      (partition 2 bindings)
        pair-count (count pairs)]
    (case pair-count
      0 scope

      1 (let [[sym val] (first pairs)]
          (if is-cljs
            ;; Var has to be resolved at runtime, in CLJS world.
            (if (symbol? sym)
              `(h/persistentAssocSkip ~scope (var ~sym) ~val)
              (throw (IllegalArgumentException. (str "Cannot resolve: " sym))))
            ;; Resolve var at compile time, then reuse.
            (if-let [resolved (and (symbol? sym) #?(:clj (resolve sym) :cljs nil))]
              `(h/persistentAssocSkip ~scope ~resolved ~val)
              (throw (IllegalArgumentException. (str "Cannot resolve: " sym))))))

      ;; Else
      (if (< pair-count 10)
        `(-> (h/asTransient ~scope)
           ~@(for [[sym val] pairs]
               (if is-cljs
                 (if (symbol? sym)
                   `(h/transientAssocSkip (var ~sym) ~val)
                   (throw (IllegalArgumentException. (str "Cannot resolve: " sym))))
                 (if-let [resolved (and (symbol? sym) #?(:clj (resolve sym) :cljs nil))]
                   `(h/transientAssocSkip ~resolved ~val)
                   (throw (IllegalArgumentException. (str "Cannot resolve: " sym))))))
           (h/asPersistent))
        `(merge-resolved-bindings ~scope
           ~(reduce (fn [v [sym value]]
                      (if is-cljs
                        (if (symbol? sym)
                          (conj v `(var ~sym) value)
                          (throw (IllegalArgumentException. (str "Cannot resolve: " sym))))
                        (if-let [resolved (and (symbol? sym) #?(:clj (resolve sym) :cljs nil))]
                          (conj v resolved value)
                          (throw (IllegalArgumentException. (str "Cannot resolve: " sym))))))
              []
              pairs))))))
