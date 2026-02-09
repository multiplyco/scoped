(ns co.multiply.scoped.impl
  #?(:cljs (:require-macros co.multiply.scoped.impl))
  (:require [co.multiply.scoped.helpers :as h])
  #?(:clj (:import
            [clojure.lang Associative Counted IDeref IEditableCollection ITransientAssociative ITransientCollection Indexed Var$Unbound]
            [java.lang Runtime$Version])))


;; ## Determine codepath
;; ############################################################
(defmacro use-scoped-value?
  []
  (h/if-cljs
    false
    `(and (not= (System/getProperty "co.multiply.scoped.force-fallback") "true")
       (>= (Runtime$Version/.feature (Runtime/version)) 25))))


(def ^:private use-scoped-value (co.multiply.scoped.impl/use-scoped-value?))


;; ## Impl
;; ############################################################
(defmacro create-carrier
  "Instantiates a new carrier object.

   On JDK 25+, this is a ScopedValue instance. On older JDKs, this is a ThreadLocal."
  []
  (if use-scoped-value
    `(java.lang.ScopedValue/newInstance)
    `(java.lang.ThreadLocal/withInitial (constantly {}))))


#?(:clj  (defonce ^{:doc "The carrier holding the current scope map."} carrier (create-carrier))
   :cljs (defonce ^:dynamic carrier {}))


(defmacro current-scope
  "Returns the current scope map, or an empty map if no scope is active."
  []
  (cond
    (:ns &env)
    `carrier

    use-scoped-value
    `(java.lang.ScopedValue/.orElse carrier {})

    :else
    `(java.lang.ThreadLocal/.get carrier)))


(defmacro ^:no-doc -get-scoped-var
  [v default]
  `(let [value# (h/getOrDefault (current-scope) ~v ::not-found)]
     (h/if-cljs
       (if (cljs.core/keyword-identical? ::not-found value#)
         (if-some [value# (deref ~v)] value# ~default)
         value#)
       (if (identical? ::not-found value#)
         (let [value# (IDeref/.deref ~v)]
           (if (instance? Var$Unbound value#) ~default value#))
         value#))))


(defn get-scoped-var
  "Retrieve a scoped value, falling back to the var's root binding.

   If the var is in the current scope, returns the scoped value.
   If not in scope, returns the var's current value.
   If the var is unbound and not in scope, throws IllegalStateException.

   This is the runtime implementation for the `ask` macro."
  ([v]
   (co.multiply.scoped.impl/-get-scoped-var v
     #?(:clj  (throw (IllegalStateException. (str "Unbound: " v)))
        :cljs (throw (js/Error. (str "Unbound: " v))))))
  ([v default]
   (co.multiply.scoped.impl/-get-scoped-var v default)))


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
            (h/transientAssoc scope
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
              `(h/persistentAssoc ~scope (var ~sym) ~val)
              (throw (IllegalArgumentException. (str "Cannot resolve: " sym))))
            ;; Resolve var at compile time, then reuse.
            (if-let [resolved (and (symbol? sym) #?(:clj (resolve sym) :cljs nil))]
              `(h/persistentAssoc ~scope ~resolved ~val)
              (throw (IllegalArgumentException. (str "Cannot resolve: " sym))))))

      ;; Else
      (if (< pair-count 10)
        `(-> (h/asTransient ~scope)
           ~@(for [[sym val] pairs]
               (if is-cljs
                 (if (symbol? sym)
                   `(h/transientAssoc (var ~sym) ~val)
                   (throw (IllegalArgumentException. (str "Cannot resolve: " sym))))
                 (if-let [resolved (and (symbol? sym) #?(:clj (resolve sym) :cljs nil))]
                   `(h/transientAssoc ~resolved ~val)
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


(defmacro with-scope
  "Execute body with a pre-built scope map. Returns the value of body."
  [scope & body]
  (cond
    (:ns &env)
    `(let [prev# carrier]
       (try (set! carrier ~scope)
         ~@body
         (finally (set! carrier prev#))))

    use-scoped-value
    `(-> (java.lang.ScopedValue/where carrier ~scope)
       (java.lang.ScopedValue$Carrier/.call
         (fn scope-call# [] ~@body)))

    :else
    `(let [prev# (java.lang.ThreadLocal/.get carrier)]
       (try (java.lang.ThreadLocal/.set carrier ~scope)
         ~@body
         (finally
           (java.lang.ThreadLocal/.set carrier prev#))))))
