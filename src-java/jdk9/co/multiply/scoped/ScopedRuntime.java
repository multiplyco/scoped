package co.multiply.scoped;

import clojure.lang.Associative;
import clojure.lang.IFn;
import clojure.lang.IEditableCollection;
import clojure.lang.ITransientAssociative;
import clojure.lang.ITransientCollection;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Var;
import java.util.Map;

/** JVM runtime. The Clojure macros only resolve Vars and preserve evaluation semantics. */
public final class ScopedRuntime {
    public static final Object SKIP = new Object();
    public static final Object UNBOUND = new Object();
    private static final Object ABSENT = new Object();
    private static final Backend BACKEND = createBackend();

    private ScopedRuntime() {}

    public interface Backend {
        Object currentScope();
        Object withScope(Object scope, IFn body);
    }

    private static Backend createBackend() {
        if (Runtime.version().major() >= 25
                && !"true".equals(System.getProperty("co.multiply.scoped.force-fallback"))) {
            // Keep the Java 25 class out of the Java 9 runtime's linkage path.
            try {
                return (Backend) Class.forName("co.multiply.scoped.ScopedValueBackend", true,
                        ScopedRuntime.class.getClassLoader()).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        return new ThreadLocalBackend();
    }

    public static String backendName() {
        return BACKEND.getClass().getSimpleName();
    }

    public static Object currentScope() {
        return BACKEND.currentScope();
    }

    public static Object withScope(Object scope, IFn body) {
        return BACKEND.withScope(scope, body);
    }

    @SuppressWarnings("unchecked")
    public static Object lookup(Var variable) {
        Object value = ((Map<Object, Object>) currentScope()).getOrDefault(variable, ABSENT);
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

    public static Object assoc(Object scope, Object key, Object value) {
        return value == SKIP ? scope : ((Associative) scope).assoc(key, value);
    }

    public static ITransientAssociative assocTransient(ITransientAssociative scope,
                                                       Object key, Object value) {
        return value == SKIP ? scope : scope.assoc(key, value);
    }

    public static Object assocTwo(Object scope, Object key1, Object value1,
                                  Object key2, Object value2) {
        ITransientAssociative result = (ITransientAssociative) ((IEditableCollection) scope).asTransient();
        result = assocTransient(result, key1, value1);
        result = assocTransient(result, key2, value2);
        return ((ITransientCollection) result).persistent();
    }

    public static Object assocThree(Object scope, Object key1, Object value1,
            Object key2, Object value2,
            Object key3, Object value3) {
        ITransientAssociative result = (ITransientAssociative) ((IEditableCollection) scope).asTransient();
        result = assocTransient(result, key1, value1);
        result = assocTransient(result, key2, value2);
        result = assocTransient(result, key3, value3);
        return ((ITransientCollection) result).persistent();
    }

    public static Object assocFour(Object scope, Object key1, Object value1,
            Object key2, Object value2,
            Object key3, Object value3,
            Object key4, Object value4) {
        ITransientAssociative result = (ITransientAssociative) ((IEditableCollection) scope).asTransient();
        result = assocTransient(result, key1, value1);
        result = assocTransient(result, key2, value2);
        result = assocTransient(result, key3, value3);
        result = assocTransient(result, key4, value4);
        return ((ITransientCollection) result).persistent();
    }

    public static Object assocFive(Object scope, Object key1, Object value1,
            Object key2, Object value2,
            Object key3, Object value3,
            Object key4, Object value4,
            Object key5, Object value5) {
        ITransientAssociative result = (ITransientAssociative) ((IEditableCollection) scope).asTransient();
        result = assocTransient(result, key1, value1);
        result = assocTransient(result, key2, value2);
        result = assocTransient(result, key3, value3);
        result = assocTransient(result, key4, value4);
        result = assocTransient(result, key5, value5);
        return ((ITransientCollection) result).persistent();
    }

    public static Object assocSix(Object scope, Object key1, Object value1,
            Object key2, Object value2,
            Object key3, Object value3,
            Object key4, Object value4,
            Object key5, Object value5,
            Object key6, Object value6) {
        ITransientAssociative result = (ITransientAssociative) ((IEditableCollection) scope).asTransient();
        result = assocTransient(result, key1, value1);
        result = assocTransient(result, key2, value2);
        result = assocTransient(result, key3, value3);
        result = assocTransient(result, key4, value4);
        result = assocTransient(result, key5, value5);
        result = assocTransient(result, key6, value6);
        return ((ITransientCollection) result).persistent();
    }

    public static Object assocSeven(Object scope, Object key1, Object value1,
            Object key2, Object value2,
            Object key3, Object value3,
            Object key4, Object value4,
            Object key5, Object value5,
            Object key6, Object value6,
            Object key7, Object value7) {
        ITransientAssociative result = (ITransientAssociative) ((IEditableCollection) scope).asTransient();
        result = assocTransient(result, key1, value1);
        result = assocTransient(result, key2, value2);
        result = assocTransient(result, key3, value3);
        result = assocTransient(result, key4, value4);
        result = assocTransient(result, key5, value5);
        result = assocTransient(result, key6, value6);
        result = assocTransient(result, key7, value7);
        return ((ITransientCollection) result).persistent();
    }

    public static Object assocEight(Object scope, Object key1, Object value1,
            Object key2, Object value2,
            Object key3, Object value3,
            Object key4, Object value4,
            Object key5, Object value5,
            Object key6, Object value6,
            Object key7, Object value7,
            Object key8, Object value8) {
        ITransientAssociative result = (ITransientAssociative) ((IEditableCollection) scope).asTransient();
        result = assocTransient(result, key1, value1);
        result = assocTransient(result, key2, value2);
        result = assocTransient(result, key3, value3);
        result = assocTransient(result, key4, value4);
        result = assocTransient(result, key5, value5);
        result = assocTransient(result, key6, value6);
        result = assocTransient(result, key7, value7);
        result = assocTransient(result, key8, value8);
        return ((ITransientCollection) result).persistent();
    }

    public static Object assocNine(Object scope, Object key1, Object value1,
            Object key2, Object value2,
            Object key3, Object value3,
            Object key4, Object value4,
            Object key5, Object value5,
            Object key6, Object value6,
            Object key7, Object value7,
            Object key8, Object value8,
            Object key9, Object value9) {
        ITransientAssociative result = (ITransientAssociative) ((IEditableCollection) scope).asTransient();
        result = assocTransient(result, key1, value1);
        result = assocTransient(result, key2, value2);
        result = assocTransient(result, key3, value3);
        result = assocTransient(result, key4, value4);
        result = assocTransient(result, key5, value5);
        result = assocTransient(result, key6, value6);
        result = assocTransient(result, key7, value7);
        result = assocTransient(result, key8, value8);
        result = assocTransient(result, key9, value9);
        return ((ITransientCollection) result).persistent();
    }

    public static Object assocTen(Object scope, Object key1, Object value1,
            Object key2, Object value2,
            Object key3, Object value3,
            Object key4, Object value4,
            Object key5, Object value5,
            Object key6, Object value6,
            Object key7, Object value7,
            Object key8, Object value8,
            Object key9, Object value9,
            Object key10, Object value10) {
        ITransientAssociative result = (ITransientAssociative) ((IEditableCollection) scope).asTransient();
        result = assocTransient(result, key1, value1);
        result = assocTransient(result, key2, value2);
        result = assocTransient(result, key3, value3);
        result = assocTransient(result, key4, value4);
        result = assocTransient(result, key5, value5);
        result = assocTransient(result, key6, value6);
        result = assocTransient(result, key7, value7);
        result = assocTransient(result, key8, value8);
        result = assocTransient(result, key9, value9);
        result = assocTransient(result, key10, value10);
        return ((ITransientCollection) result).persistent();
    }

    public static Object extendScope(Object scope, Object[] bindings) {
        ITransientAssociative result = (ITransientAssociative) ((IEditableCollection) scope).asTransient();
        for (int i = 0; i < bindings.length; i += 2) {
            result = assocTransient(result, bindings[i], bindings[i + 1]);
        }
        return ((ITransientCollection) result).persistent();
    }

    private static final class ThreadLocalBackend implements Backend {
        private final ThreadLocal<Object> carrier = ThreadLocal.withInitial(() -> PersistentArrayMap.EMPTY);

        @Override
        public Object currentScope() {
            return carrier.get();
        }

        @Override
        public Object withScope(Object scope, IFn body) {
            Object previous = carrier.get();
            carrier.set(scope);
            try {
                return body.invoke();
            } finally {
                carrier.set(previous);
            }
        }
    }
}
