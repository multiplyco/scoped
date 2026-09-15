package co.multiply.scoped;

import clojure.lang.Associative;
import clojure.lang.IEditableCollection;
import clojure.lang.ITransientAssociative;
import clojure.lang.ITransientCollection;

/**
 * Map updates shared by both scope runtimes, compiled once for Java 17+.
 * Clojure macros call these methods directly, independently of the carrier.
 * Transient updates always use the returned map, including array-map promotion.
 */
public final class MapUpdates {
    public static final Object SKIP = new Object();

    private MapUpdates() {
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
