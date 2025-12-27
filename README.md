# Scoped

[![Clojars Project](https://img.shields.io/clojars/v/co.multiply/scoped.svg)](https://clojars.org/co.multiply/scoped)
[![cljdoc](https://cljdoc.org/badge/co.multiply/scoped)](https://cljdoc.org/d/co.multiply/scoped)

A Clojure/ClojureScript library for scoped values. On JDK 25+, uses Java's `ScopedValue` API for efficient context
propagation with virtual threads. On older JDKs, falls back to `ThreadLocal`. In ClojureScript, falls back to `binding`.

## Requirements

**Clojure:**

- Clojure 1.12+
- JDK 9+ (uses `ThreadLocal`)
- JDK 25+ recommended for optimal performance (uses `ScopedValue`)

On JDK 25+, the library uses Java's `ScopedValue` API for maximum performance. On older JDKs, it
automatically falls back to a `ThreadLocal`-based implementation with identical semantics.

**ClojureScript:**

- Any supported ClojureScript version (uses `binding`)

## Installation

```clojure
;; deps.edn
co.multiply/scoped {:mvn/version "0.1.12"}
```

## Why scoped values?

This library emerged while working on async code where it became clear that extracting and setting thread bindings
accounted for the vast majority of the overhead involved. While I can't make broad claims about the efficiency of
`ScopedValue` (introduced in Java 21, GA in Java 25), it made a significant difference for my own code. Switching to
scoped values cut about 95% of the overhead: from ~20μs to ~1μs per async operation.

This library provides a way to use `ScopedValue` when running on JDK 25, while providing a semantically identical
fallback to `ThreadLocal` when running on older versions of the JDK. It also provides some basic support for
ClojureScript, to ease use in CLJC contexts.

## API

### `scoping` (CLJ + CLJS)

Establish scoped bindings, similar to `binding`:

```clojure
(require '[co.multiply.scoped :refer [scoping ask]])

(def ^:dynamic *user-id* nil)
(def ^:dynamic *request-id* nil)

(scoping [*user-id* 123
          *request-id* "abc"]
  (ask *user-id*))

;; => 123
```

Scopes nest naturally; inner bindings shadow outer ones:

```clojure
(scoping [*user-id* 1]
  (scoping [*user-id* 2]
    (ask *user-id*)))

;; => 2
```

### `ask` (CLJ + CLJS)

Access a scoped value. Falls back to the var's root binding if not in scope:

```clojure
(require '[co.multiply.scoped :refer [ask scoping]])

(def ^:dynamic *user-id* :default)

(ask *user-id*)  ; => :default

(scoping [*user-id* 123]
  (ask *user-id*))

;; => 123
```

Throws `IllegalStateException` if the var is unbound and not in scope.

**Gotcha:** It can be easy to forget `ask` and reference the var directly. With a default value, this fails silently:

```clojure
(def ^:dynamic *user-id* :default)

(scoping [*user-id* 123]
  (str "User: " *user-id*))  ; Oops, forgot `ask`

;; => "User: :default"  (wrong!)
```

Prefer unbound vars. They're more likely to fail when used, making the mistake obvious:

```clojure
(def ^:dynamic *user-id*)

(scoping [*user-id* 123]
  (+ *user-id* 1))  ; Forgot `ask`

;; => ClassCastException: Var$Unbound cannot be cast to Number
```

### `current-scope` (CLJ only)

Capture the current scope map for later restoration:

```clojure
(require '[co.multiply.scoped :refer [current-scope scoping]])

(def ^:dynamic *user-id*)

(scoping [*user-id* 123]
  (current-scope))

;; => {#'*user-id* 123}
```

### `assoc-scope` (CLJ only)

Extend a captured scope with additional bindings without creating another lambda:

```clojure
(require '[co.multiply.scoped :refer [assoc-scope current-scope scoping]])

(def ^:dynamic *user-id*)
(def ^:dynamic *request-id*)

(def captured
  (scoping [*user-id* 123]
    (current-scope)))

(assoc-scope captured *request-id* "abc")

;; => {#'*user-id* 123, #'*request-id* "abc"}
```

This is useful when you have a captured scope and want to add bindings before restoring it, avoiding the overhead of
nesting `scoping` inside `with-scope`.

### `with-scope` (CLJ only)

Restore a previously captured scope:

```clojure
(require '[co.multiply.scoped :refer [ask current-scope scoping with-scope]])

(def ^:dynamic *user-id*)

(def captured
  (scoping [*user-id* 123]
    (current-scope)))

(with-scope captured
  (ask *user-id*))

;; => 123
```

Combined with `assoc-scope`:

```clojure
(with-scope (assoc-scope captured *request-id* "abc")
  (ask *request-id*))

;; => "abc"
```

## Virtual thread example (CLJ only)

Capture and restore scope across virtual thread boundaries:

```clojure
(require '[co.multiply.scoped :refer [ask current-scope scoping with-scope]])

(defmacro vt
  [& body]
  `(let [scope# (current-scope)]
     (Thread/startVirtualThread
       (fn run# [] (with-scope scope# ~@body)))))

(def ^:dynamic *user-id*)

(scoping [*user-id* 123]
  (vt (println "User:" (ask *user-id*))))

;; Prints: "User: 123"

(scoping [*user-id* 123]
  ;; Nested virtual threads
  (vt (vt (println "User:" (ask *user-id*)))))

;; Prints: "User: 123"
```

## License

Eclipse Public License 2.0. Copyright (c) 2025 Multiply. See [LICENSE](LICENSE).

Authored by [@eneroth](https://github.com/eneroth)