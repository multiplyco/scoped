package co.multiply.scoped;

import clojure.lang.IFn;
import clojure.lang.PersistentArrayMap;

/** Java 17 carrier. The multi-release JAR replaces this class on Java 25+. */
public final class Carrier {
    private static final ThreadLocal<Object> CARRIER =
            ThreadLocal.withInitial(() -> PersistentArrayMap.EMPTY);

    private Carrier() {}

    public static String backendName() {
        return "ThreadLocalBackend";
    }

    public static Object currentScope() {
        return CARRIER.get();
    }

    public static Object withScope(Object scope, IFn body) {
        Object previous = CARRIER.get();
        CARRIER.set(scope);
        try {
            return body.invoke();
        } finally {
            CARRIER.set(previous);
        }
    }
}
