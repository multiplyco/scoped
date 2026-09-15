# Scoped

[![Clojars Project](https://img.shields.io/clojars/v/co.multiply/scoped.svg)](https://clojars.org/co.multiply/scoped)
[![cljdoc](https://cljdoc.org/badge/co.multiply/scoped)](https://cljdoc.org/d/co.multiply/scoped)

A Clojure/ClojureScript library for scoped values. On JDK 25+, uses Java's `ScopedValue` API for efficient context
propagation with virtual threads. On older JDKs, falls back to `ThreadLocal`. Full API support in ClojureScript.

## Requirements

**Clojure:**

- Clojure 1.12+
- JDK 17+ (uses `ThreadLocal`)
- JDK 25+ is the primary performance target (uses `ScopedValue`)

On JDK 25+, the library uses Java's `ScopedValue` API. On JDK 17–24, it
automatically uses `ThreadLocal` with identical public semantics. ThreadLocal
performance is secondary to the JDK 25+ implementation.

The JVM implementation is Java-owned: scope storage, lookup, root fallback,
construction and entry/restoration sit behind `ScopedRuntime`. Clojure macros
resolve Vars, preserve lazy default expressions and wrap lexical bodies in an
`IFn` callback. They do not select or implement the carrier. CLJS retains its
own implementation of the public API.

A single multi-release JAR contains two independent implementations of
`ScopedRuntime`: a Java 17 ThreadLocal runtime and a Java 25 ScopedValue runtime.
Java's class loader selects the complete implementation; no reflective backend
loading or JDK check is needed in the Clojure API. Both implementations expose
the same Java contract and run the same behavioral tests. Their internals,
including lookup and map construction, can evolve independently.

**ClojureScript:**

- Any supported ClojureScript version

## Installation

```clojure
;; deps.edn
co.multiply/scoped {:mvn/version "0.1.18"}
```

## Why scoped values?

This library emerged while working on async code where it became clear that extracting and setting thread bindings
accounted for the vast majority of the overhead involved. While I can't make broad claims about the efficiency of
`ScopedValue` (introduced in Java 21, GA in Java 25), it made a significant difference for my own code. Switching to
scoped values cut about 95% of the overhead: from ~20μs to ~1μs per async operation.

This library provides a way to use `ScopedValue` when running on JDK 25, while providing a semantically identical
fallback to `ThreadLocal` when running on older versions of the JDK. Full API support is available in ClojureScript,
enabling scope capture and restoration patterns in async contexts.

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

### `skip` (CLJ + CLJS)

Use `skip` to conditionally omit a binding in `scoping` or `assoc-scope`:

```clojure
(require '[co.multiply.scoped :refer [ask scoping skip]])

(def ^:dynamic *user*)

(let [available? false
      user nil]
  (scoping [*user* (if available? user skip)]
    (ask *user* :anonymous)))
;; => :anonymous

(scoping [*user* nil]
  (ask *user* :anonymous))
;; => nil
```

`skip` leaves an existing outer binding intact, including `nil` or `false`.
When there is no scoped binding, normal fallback to the var's value or the
reader's default still applies. It never adds the sentinel to the scope map.
Use `if` to choose `skip` explicitly; `when` still returns `nil` when false.

```clojure
(scoping [*user* :outer]
  (scoping [*user* skip]
    (ask *user* :anonymous)))
;; => :outer

(scoping [*user* skip]
  [(ask *user* :anonymous)
   (ask *user* :guest)])
;; => [:anonymous :guest] (the var remains unbound)
```

With no scoped binding or var value, `(ask *user*)` still throws. Skipping a
binding does not choose a default on behalf of its readers.

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

The two-arity form returns a default value instead of throwing:

```clojure
(def ^:dynamic *user*)

(ask *user* :anonymous)  ; => :anonymous (var is unbound)

(scoping [*user* 123]
  (ask *user* :anonymous))  ; => 123 (default not used)
```

This is useful for optional context that should be a no-op when not established.

The default expression is evaluated only when needed:

```clojure
(ask *user* (load-default-user))  ; Loads the default only if *user* is unbound

(scoping [*user* 123]
  (ask *user* (load-default-user)))  ; => 123, without calling load-default-user
```

> **Note:** In CLJS, a var with value `nil` is indistinguishable from an unbound var when not
> in scope. However, explicitly scoping to `nil` works correctly and returns `nil` (not the default).
>
> ```clojure
> (def ^:dynamic *opt* nil)
>
> ;; Outside scope, CLJS can't tell nil-bound from unbound:
> (ask *opt* :fallback)
> ;; CLJ  => nil       (var is bound to nil)
> ;; CLJS => :fallback  (nil looks unbound)
>
> ;; But explicitly scoping to nil works on both:
> (scoping [*opt* nil]
>   (ask *opt* :fallback))
> ;; => nil (both CLJ and CLJS)
> ```

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

### `current-scope` (CLJ + CLJS)

Capture the current scope map for later restoration:

```clojure
(require '[co.multiply.scoped :refer [current-scope scoping]])

(def ^:dynamic *user-id*)

(scoping [*user-id* 123]
  (current-scope))

;; => {#'*user-id* 123}
```

### `assoc-scope` (CLJ + CLJS)

Extend a captured scope with additional bindings without creating another lambda:

```clojure
(require '[co.multiply.scoped :refer [assoc-scope current-scope scoping skip]])

(def ^:dynamic *user-id*)
(def ^:dynamic *request-id*)

(def captured
  (scoping [*user-id* 123]
    (current-scope)))

(assoc-scope captured *request-id* "abc")

;; => {#'*user-id* 123, #'*request-id* "abc"}
```

`skip` also leaves captured bindings unchanged, while `nil` remains an explicit value:

```clojure
(assoc-scope captured *user-id* skip *request-id* nil)

;; => {#'*user-id* 123, #'*request-id* nil}
```

This is useful when you have a captured scope and want to add bindings before restoring it, avoiding the overhead of
nesting `scoping` inside `with-scope`.

### `with-scope` (CLJ + CLJS)

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

## Async example (CLJS)

In JavaScript, async callbacks (`setTimeout`, Promises, event handlers) run after the current scope exits.
You must capture and restore the scope explicitly:

```clojure
(require '[co.multiply.scoped :refer [ask current-scope scoping with-scope]])

(def ^:dynamic *user-id*)

;; This WON'T work - scope exits before callback runs:
(scoping [*user-id* 123]
  (js/setTimeout
    #(println "User:" (ask *user-id*))  ; Runs with empty scope!
    100))

;; This WILL work - capture and restore:
(scoping [*user-id* 123]
  (let [scope (current-scope)]
    (js/setTimeout
      #(with-scope scope
         (println "User:" (ask *user-id*)))  ; Prints: "User: 123"
      100)))
```

This pattern applies to all async boundaries: `setTimeout`, `js/Promise`, `core.async` channels, etc.

## Benchmarks

The [development suite](bench/DEV.md) runs all 31 public-API JVM workloads in
roughly a minute per backend, reporting time and allocations for spotting large
regressions during implementation work. The tasks compile Java first:

```sh
bb bench:dev
bb bench:dev:thread-local
```

Save a reference, then compare after making changes:

```sh
bb bench:dev '{:output "target/bench/dev/before.edn"}'
bb bench:dev '{:baseline "target/bench/dev/before.edn" :output "target/bench/dev/after.edn"}'
```

Without Babashka, use `clojure -T:build compile-java` followed by
`clojure -M:bench-dev`. Compilation requires JDK 25+ and produces
`target/scoped-runtime.jar`, a Java-only multi-release JAR used by the local
classpath. The base classes target Java 17 and the carrier replacement targets
Java 25. Local and Git dependencies support `clojure -X:deps prep`; the `bb`
development and benchmark tasks compile automatically. Rebuilding Java code
requires a fresh JVM/REPL to load the new classes. `clojure -T:build jar` builds
the complete distributable JAR with both Java implementations and Clojure sources.

These short, shared-JVM results are exploratory.
The [runtime benchmark suite](bench/RUNTIME.md) provides longer confirmation
with isolated JVMs, extended warm-up, one-minute sampling and repeated forks:

```sh
clojure -M:bench-runtime '{:backends [:scoped-value :thread-local]}'
```

The [conditional-binding benchmark guide](bench/README.md) documents `clojure -M:bench`;
see its [recorded measurements](bench/RESULTS.md) for comparisons of the original
associations, function wrappers, and macro wrappers.

## JVM build and tests

The Java 17 compatibility implementation lives alongside Clojure at
`src/co/multiply/scoped/ScopedRuntime.java`. The primary Java 25 implementation
is `src-java/jdk25/co/multiply/scoped/ScopedRuntime.java`, packaged at
`META-INF/versions/25`. Each owns its carrier and lookup logic; duplication is
intentional so ThreadLocal concerns do not constrain ScopedValue development.
Map updates live in `src/co/multiply/scoped/MapUpdates.java`, compiled once for
Java 17 and shared across both backends. The macros call its static methods
directly: fixed helpers handle one through ten bindings, and larger updates
thread a transient through the same class's `assocTransient` helper.
Both JARs declare `Multi-Release: true`, and compilation runs
`jar --validate` to check that their versioned Java APIs agree.

Keep `target/classes` off runtime classpaths so it cannot bypass multi-release
selection. The build preserves benchmark results under `target/bench`.

```sh
bb compile:java         # Build the Java-only JAR with JDK 25+
bb test:clj             # Build once; test the same complete JAR in three JVMs
bb test:clj:jdk17       # Actual JDK 17, ThreadLocal
bb test:clj:scoped-value # Actual JDK 25, ScopedValue
bb test:clj:thread-local # Actual JDK 25, base ThreadLocal runtime
```

Tests use the packaged library, the `test` directory and test dependencies;
local production sources and loose class files are excluded. The launcher
checks artifact origins, selected runtime bytecode and backend, and the single
shared Java 17 map helper before running the suite. Platform-thread isolation
runs on both JDKs; virtual-thread tests run where available (JDK 21+).

There is no library-specific backend switch. For same-JDK ThreadLocal tests
and benchmarks, the tasks use `-Djdk.util.jar.version=17` to make Java select
the base runtime. This setting affects all multi-release JARs in that JVM;
actual JDK 17 runs remain the compatibility check. The Java 25 runtime contains
only the ScopedValue implementation.

The runner finds JDKs in the current Java installation or SDKMAN candidates.
For other installations, set `JAVA17_HOME` and `JAVA25_HOME`, or pass paths:

```sh
clojure -T:build test-jar :jdk17-home '"/path/to/jdk17"' :jdk25-home '"/path/to/jdk25"'
```

The build process still runs on JDK 25+; those settings choose the test JVMs.
`bb test` also runs the CLJS tests.

## License

Eclipse Public License 2.0. Copyright (c) 2025 Multiply. See [LICENSE](LICENSE).

Authored by [@eneroth](https://github.com/eneroth)
