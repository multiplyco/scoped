package co.multiply.scoped;

import clojure.lang.IFn;
import clojure.lang.ILookup;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Var;

import java.util.Map;

/**
 * Primary Java 25+ runtime, backed by ScopedValue.
 * Selected as a complete implementation by the multi-release JAR loader.
 * Both implementations must preserve the same public contract; their internals are independent.
 */
public final class ScopedRuntime {
    private static final ScopedValue<Object> CARRIER = ScopedValue.newInstance();

    public static final Object UNBOUND = new Object();
    private static final Object ABSENT = new Object();

    private ScopedRuntime() {
    }

    public static String backendName() {
        return "ScopedValueBackend";
    }

    public static Object currentScope() {
        return CARRIER.orElse(PersistentArrayMap.EMPTY);
    }

    public static Object withScope(Object scope, IFn body) {
        return ScopedValue.where(CARRIER, scope).call(body::invoke);
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
