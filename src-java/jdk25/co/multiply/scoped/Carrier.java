package co.multiply.scoped;

import clojure.lang.IFn;
import clojure.lang.PersistentArrayMap;

/** Java 25 carrier, selected by the JVM's multi-release JAR loader. */
public final class Carrier {
    private static final ScopedValue<Object> CARRIER = ScopedValue.newInstance();

    private Carrier() {}

    public static String backendName() {
        return "ScopedValueBackend";
    }

    public static Object currentScope() {
        return CARRIER.orElse(PersistentArrayMap.EMPTY);
    }

    public static Object withScope(Object scope, IFn body) {
        return ScopedValue.where(CARRIER, scope).call(body::invoke);
    }
}
