(ns co.multiply.scoped.impl
  (:import
    [clojure.lang Associative IDeref IEditableCollection ITransientAssociative ITransientCollection Var$Unbound]
    [java.lang Runtime$Version]
    [java.util Map]))


(def ^:private use-scoped-value
  (and (not= (System/getProperty "co.multiply.scoped.force-fallback") "true")
    (>= (Runtime$Version/.feature (Runtime/version)) 25)))


(defmacro create-carrier
  "Instantiates a new carrier object.

   On JDK 25+, this is a ScopedValue instance. On older JDKs, this is a ThreadLocal."
  []
  (if use-scoped-value
    `(java.lang.ScopedValue/newInstance)
    `(java.lang.ThreadLocal/withInitial (constantly {}))))


(defonce ^{:doc "The carrier holding the current scope map."}
  carrier (create-carrier))


(defmacro current-scope
  "Returns the current scope map, or an empty map if no scope is active."
  []
  (if use-scoped-value
    `(java.lang.ScopedValue/.orElse carrier {})
    `(java.lang.ThreadLocal/.get carrier)))


(defn get-scoped-var
  "Retrieve a scoped value, falling back to the var's root binding.

   If the var is in the current scope, returns the scoped value.
   If not in scope, returns the var's current value.
   If the var is unbound and not in scope, throws IllegalStateException.

   This is the runtime implementation for the `ask` macro."
  [v]
  (let [scope (current-scope)
        value (Map/.getOrDefault scope v ::not-found)]
    (if (identical? ::not-found value)
      ;; No value in the given scope; attempt to use default value.
      (let [value (IDeref/.deref v)]
        (if (instance? Var$Unbound value)
          (throw (IllegalStateException. (str "Unbound: " v)))
          value))
      ;; A value is available in the scope.
      value)))


(defmacro extend-scope
  [scope bindings]
  (assert (even? (count bindings)) "`bindings` must contain an even number of forms.")
  (let [pairs (partition 2 bindings)]
    (case (count pairs)
      0 scope

      1 (let [[sym val] (first pairs)]
          (if-let [resolved (and (symbol? sym) (resolve sym))]
            `(Associative/.assoc ~scope ~resolved ~val)
            (throw (IllegalArgumentException. (str "Cannot resolve: " sym)))))

      ;; Else
      `(-> (IEditableCollection/.asTransient ~scope)
         ~@(for [[sym val] pairs]
             (if-let [resolved (and (symbol? sym) (resolve sym))]
               `(ITransientAssociative/.assoc ~resolved ~val)
               (throw (IllegalArgumentException. (str "Cannot resolve: " sym)))))
         (ITransientCollection/.persistent)))))


(defmacro with-scope
  "Execute body with a pre-built scope map. Returns the value of body."
  [scope & body]
  (if use-scoped-value
    `(-> (java.lang.ScopedValue/where carrier ~scope)
       (java.lang.ScopedValue$Carrier/.call
         (fn scope-call# [] ~@body)))
    `(let [prev# (java.lang.ThreadLocal/.get carrier)]
       (try (java.lang.ThreadLocal/.set carrier ~scope)
         ~@body
         (finally
           (java.lang.ThreadLocal/.set carrier prev#))))))
