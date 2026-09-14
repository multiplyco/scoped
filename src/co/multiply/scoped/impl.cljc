(ns co.multiply.scoped.impl
  #?(:cljs (:require-macros co.multiply.scoped.impl))
  (:require [co.multiply.scoped.helpers :as h]))


#?(:cljs (defonce ^:dynamic carrier {}))
(defonce ^:no-doc not-found #?(:clj (Object.) :cljs (js/Object.)))


(defmacro current-scope
  []
  (h/if-cljs
    `carrier
    `(co.multiply.scoped.ScopedRuntime/currentScope)))


(defmacro ^:no-doc -get-scoped-var
  [v default]
  (h/if-cljs
    `(let [not-found# not-found
           value# (h/getOrDefault (current-scope) ~v not-found#)]
       (if (identical? not-found# value#)
         (if-some [value# (deref ~v)] value# ~default)
         value#))
    ;; Keep only the lazy default expression in the caller; lookup lives in Java.
    `(let [value# (co.multiply.scoped.ScopedRuntime/lookup ~v)]
       (if (identical? co.multiply.scoped.ScopedRuntime/UNBOUND value#)
         ~default
         value#))))


(defn get-scoped-var
  ([v]
   (co.multiply.scoped.impl/-get-scoped-var v
     #?(:clj (throw (IllegalStateException. (str "Unbound: " v)))
        :cljs (throw (js/Error. (str "Unbound: " v))))))
  ([v default]
   (co.multiply.scoped.impl/-get-scoped-var v default)))


#?(:cljs
   (defn ^:no-doc merge-resolved-bindings
     [scope bindings]
     (let [bindings-count (h/vecCount bindings)]
       (loop [var-idx (unchecked-int 0)
              scope (h/asTransient scope)]
         (if (< var-idx bindings-count)
           (let [value-idx (unchecked-inc-int var-idx)]
             (recur (unchecked-inc-int value-idx)
               (h/transientAssocSkip scope
                 (h/vecNth bindings var-idx)
                 (h/vecNth bindings value-idx))))
           (h/asPersistent scope))))))


(defmacro extend-scope
  [scope bindings]
  (assert (even? (count bindings)) "`bindings` must contain an even number of forms.")
  (let [is-cljs (some? (:ns &env))
        resolved (vec (mapcat (fn [[sym value]]
                                [(if is-cljs
                                   (if (symbol? sym)
                                     `(var ~sym)
                                     (throw (IllegalArgumentException. (str "Cannot resolve: " sym))))
                                   (or (and (symbol? sym) (resolve sym))
                                     (throw (IllegalArgumentException. (str "Cannot resolve: " sym)))))
                                 value])
                        (partition 2 bindings)))
        n (quot (count resolved) 2)]
    (if is-cljs
      (case n
        0 scope
        1 `(h/persistentAssocSkip ~scope ~@resolved)
        (if (< n 10)
          `(-> (h/asTransient ~scope)
             ~@(for [[k v] (partition 2 resolved)] `(h/transientAssocSkip ~k ~v))
             (h/asPersistent))
          `(merge-resolved-bindings ~scope ~resolved)))
      (case n
        0 scope
        1 `(co.multiply.scoped.ScopedRuntime/assoc ~scope ~@resolved)
        2 `(co.multiply.scoped.ScopedRuntime/assocTwo ~scope ~@resolved)
        3 `(co.multiply.scoped.ScopedRuntime/assocThree ~scope ~@resolved)
        4 `(co.multiply.scoped.ScopedRuntime/assocFour ~scope ~@resolved)
        5 `(co.multiply.scoped.ScopedRuntime/assocFive ~scope ~@resolved)
        6 `(co.multiply.scoped.ScopedRuntime/assocSix ~scope ~@resolved)
        7 `(co.multiply.scoped.ScopedRuntime/assocSeven ~scope ~@resolved)
        8 `(co.multiply.scoped.ScopedRuntime/assocEight ~scope ~@resolved)
        9 `(co.multiply.scoped.ScopedRuntime/assocNine ~scope ~@resolved)
        10 `(co.multiply.scoped.ScopedRuntime/assocTen ~scope ~@resolved)
        ;; Fill one JVM array directly, without constructing a Clojure vector first.
        (let [scope-sym (gensym "scope")
              array-sym (with-meta (gensym "bindings") {:tag 'objects})]
          `(let [~scope-sym ~scope
                 ~array-sym (object-array ~(count resolved))]
             ~@(map-indexed (fn [i form] `(aset ~array-sym ~i ~form)) resolved)
             (co.multiply.scoped.ScopedRuntime/extendScope ~scope-sym ~array-sym)))))))


(defmacro with-scope
  [scope & body]
  (h/if-cljs
    `(let [prev# carrier]
       (try (set! carrier ~scope)
         ~@body
         (finally (set! carrier prev#))))
    `(co.multiply.scoped.ScopedRuntime/withScope ~scope (fn scope-call# [] ~@body))))
