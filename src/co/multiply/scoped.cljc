(ns co.multiply.scoped
  #?(:cljs (:require-macros co.multiply.scoped))
  #?(:clj (:require [co.multiply.scoped.impl :as impl])))


#?(:clj (defmacro current-scope
          "Returns the current scope map, or an empty map if no scope is active.

           The scope map contains var->value bindings set via `scoping`.

           CLJ only. Not available in CLJS."
          []
          `(impl/current-scope)))


#?(:clj (defmacro assoc-scope
          "Extend an existing scope map with additional bindings.

           Takes a scope (as returned by `current-scope`) and var-value pairs,
           returns a new scope with the bindings added. Does not establish the
           scope - use `with-scope` for that.

           ```clojure
           (with-scope (assoc-scope captured-scope *user-id* 123)
             (ask *user-id*))  ;=> 123
           ```

           CLJ only. Not available in CLJS."
          [scope & bindings]
          `(impl/extend-scope ~scope ~bindings)))


#?(:clj (defmacro with-scope
          "Execute body with a pre-built scope map. Returns the value of body.

           Unlike `scoping`, this takes a scope map (as returned by `current-scope`)
           rather than a bindings vector. Useful for restoring a previously captured
           scope in a different execution context.

           CLJ only. Not available in CLJS."
          [scope & body]
          `(impl/with-scope ~scope ~@body)))


#?(:clj (defmacro scoping
          "Execute body with additional scoped bindings. Returns the value of body.

           Bindings are merged into the current scope. In CLJS, falls back to `binding`.

           ```clojure
           (scoping [*user-id* 123
                     *request-id* \"abc\"]
             (ask *user-id*))  ;=> 123
           ```

           Scopes can be nested; inner bindings shadow outer ones for the same var."
          [bindings & body]
          (if (:ns &env)
            `(binding ~bindings ~@body)
            `(impl/with-scope (impl/extend-scope (impl/current-scope) ~bindings) ~@body))))


#?(:clj (defmacro ask
          "Access a scoped value by symbol.

           Returns the value bound via `scoping` if present, otherwise falls back
           to the var's root binding. If no default is given, throws when the var
           is unbound and not in scope.

           In CLJS, returns the var's current binding value.

           ```clojure
           (def ^:dynamic *user-id* :default)

           (scoping [*user-id* 123]
             (ask *user-id*))  ;=> 123

           (ask *user-id*)     ;=> :default (falls back to var value)
           ```

           The two-arity form returns `default` if the var is unbound:

           ```clojure
           (def ^:dynamic *user*)

           (ask *user* :anonymous)  ;=> :anonymous (var is unbound)
           ```

           Note: In CLJS, `nil` is treated as unbound since CLJS does not
           distinguish between uninitialized vars and vars bound to `nil`."
          ([sym]
           (if (:ns &env)
             sym
             `(impl/get-scoped-var ~(resolve sym))))
          ([sym default]
           (if (:ns &env)
             `(if (some? ~sym) ~sym ~default)
             `(impl/get-scoped-var ~(resolve sym) ~default)))))
