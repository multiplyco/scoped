# Changelog

## 0.1.16 - 2026-02-09

- Fix cljdoc analysis failure by hiding `resolve` from the ClojureScript analyzer via reader conditionals.

## 0.1.15 - 2026-01-30

**Breaking**

- Add full ClojureScript support for `current-scope`, `with-scope`, and `assoc-scope`.
- All API functions now work on both CLJ and CLJS.

Note: In CLJS, a var with value `nil` is indistinguishable from an unbound var when not in scope.
However, explicitly scoping to `nil` works correctly and returns `nil` (not the default).

## 0.1.14 - 2026-01-07

- Add `default` arity to `ask`: optionally return a default value rather than throwing when var is unbound.

Note: since `nil` and "unbound" is indistinguishable in ClojureScript, returns default when the var is unbound or contains
`nil`.

## 0.1.13 - 2026-01-02

- When binding 10+ forms, fall back to loop construction to avoid inlining huge forms at the callsite.

## 0.1.12 - 2026-01-02

Initial release.

- `scoping` - establish scoped bindings (CLJ + CLJS).
- `ask` - access scoped values (CLJ + CLJS).
- `current-scope` - capture current scope map (CLJ only).
- `with-scope` - restore a captured scope (CLJ only).
- `assoc-scope` - extend a captured scope with additional bindings (CLJ only).