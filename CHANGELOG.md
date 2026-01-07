# Changelog

## 0.1.14 - 2026-01-07

- Add `default` arity to `ask`: optionally return a default value rather than throwing when var is unbound.

Note: since `nil` and "unbound" is indistinguishable in ClojureScript, returns default when the var is unbound or contains
`nil`.

## 0.1.13 - 2026-01-02

- When binding 11+ forms, fall over to loop construction to avoid inlining huge forms at the callsite.

## 0.1.12 - 2026-01-02

Initial release.

- `scoping` - establish scoped bindings (CLJ + CLJS).
- `ask` - access scoped values (CLJ + CLJS).
- `current-scope` - capture current scope map (CLJ only).
- `with-scope` - restore a captured scope (CLJ only).
- `assoc-scope` - extend a captured scope with additional bindings (CLJ only).