package co.multiply.scoped;

import clojure.lang.*;

import java.util.Map;

/**
 * JVM runtime. The Clojure macros only resolve Vars and preserve evaluation semantics.
 */
public final class ScopedRuntime {
    public static final Object SKIP = new Object();
    public static final Object UNBOUND = new Object();
    private static final Object ABSENT = new Object();

    private ScopedRuntime() {
    }

    public static String backendName() {
        return Carrier.backendName();
    }

    public static Object currentScope() {
        return Carrier.currentScope();
    }

    public static Object withScope(Object scope, IFn body) {
        return Carrier.withScope(scope, body);
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

    public static Object assoc(Object scope, Object key, Object value) {
        return value == SKIP ? scope : ((Associative) scope).assoc(key, value);
    }

    public static ITransientAssociative assocTransient(ITransientAssociative scope,
                                                       Object key, Object value) {
        return value == SKIP ? scope : scope.assoc(key, value);
    }

    public static Object assocTwo(Object scope,
                                  Object key1, Object value1,
                                  Object key2, Object value2) {
        ITransientAssociative result = (ITransientAssociative) ((IEditableCollection) scope).asTransient();
        result = assocTransient(result, key1, value1);
        result = assocTransient(result, key2, value2);
        return ((ITransientCollection) result).persistent();
    }

    public static Object assocThree(Object scope,
                                    Object key1, Object value1,
                                    Object key2, Object value2,
                                    Object key3, Object value3) {
        ITransientAssociative result = (ITransientAssociative) ((IEditableCollection) scope).asTransient();
        result = assocTransient(result, key1, value1);
        result = assocTransient(result, key2, value2);
        result = assocTransient(result, key3, value3);
        return ((ITransientCollection) result).persistent();
    }

    public static Object assocFour(Object scope,
                                   Object key1, Object value1,
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

    public static Object assocFive(Object scope,
                                   Object key1, Object value1,
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

    public static Object assocSix(Object scope,
                                  Object key1, Object value1,
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

    public static Object assocSeven(Object scope,
                                    Object key1, Object value1,
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

    public static Object assocEight(Object scope,
                                    Object key1, Object value1,
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

    public static Object assocNine(Object scope,
                                   Object key1, Object value1,
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

    public static Object assocTen(Object scope,
                                  Object key1, Object value1,
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

}
