package co.multiply.scoped;

import clojure.lang.IFn;
import clojure.lang.ILookup;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Var;

import java.util.Map;

/**
 * Java 17 compatibility runtime, backed by ThreadLocal.
 * The multi-release JAR replaces this entire class on Java 25+.
 * Both implementations must preserve the same public contract; their internals are independent.
 */
public final class ScopedRuntime {
    private static final ThreadLocal<Object> CARRIER =
            ThreadLocal.withInitial(() -> PersistentArrayMap.EMPTY);

    public static final Object UNBOUND = new Object();
    private static final Object ABSENT = new Object();

    private ScopedRuntime() {
    }

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

    @SuppressWarnings("unchecked")
    public static Object lookup(Var variable) {
        Object scope = currentScope();
        // Clojure's lookup distinguishes nil from absence in a single search.
        Object value = scope instanceof ILookup
                ? ((ILookup) scope).valAt(variable, ABSENT)
                : ((Map<Object, Object>) scope).getOrDefault(variable, ABSENT);
        if (value != ABSENT) {
            return value;
        }
        value = variable.deref(); // Includes ordinary Clojure thread bindings.
        return value instanceof Var.Unbound ? UNBOUND : value;
    }

    public static Object get(Var variable) {
        Object value = lookup(variable);
        if (value == UNBOUND) {
            throw new IllegalStateException("Unbound: " + variable);
        }
        return value;
    }
}
