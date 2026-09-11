package co.multiply.scoped;

import clojure.lang.IFn;
import clojure.lang.PersistentArrayMap;

/** Loaded only on Java 25+, where ScopedValue is a final API. */
public final class ScopedValueBackend implements ScopedRuntime.Backend {
    private static final ScopedValue<Object> CARRIER = ScopedValue.newInstance();

    @Override
    public Object currentScope() {
        return CARRIER.orElse(PersistentArrayMap.EMPTY);
    }

    @Override
    public Object withScope(Object scope, IFn body) {
        return ScopedValue.where(CARRIER, scope).call(body::invoke);
    }
}
