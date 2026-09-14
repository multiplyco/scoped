package co.multiply.scoped.experiment;

import clojure.lang.ILookup;
import clojure.lang.IPersistentMap;
import clojure.lang.ITransientMap;
import clojure.lang.PersistentHashMap;
import clojure.lang.Var;
import clojure.lang.IEditableCollection;
import java.util.Arrays;

/** Experimental immutable Var-keyed store; deliberately not a complete Clojure map. */
public final class VarScope implements ILookup {
    public static final Object SKIP = new Object();
    public static final VarScope EMPTY = new VarScope(new Object[0], null);
    public static final int ARRAY_LIMIT = 8;
    private final Object[] entries;
    private final IPersistentMap large;

    private VarScope(Object[] entries, IPersistentMap large) {
        this.entries = entries;
        this.large = large;
    }

    private static int index(Object[] pairs, int length, Object key) {
        for (int i = 0; i < length; i += 2) {
            if (pairs[i] == key) return i;
        }
        return -1;
    }

    public int size() {
        return entries == null ? large.count() : entries.length / 2;
    }

    @Override public Object valAt(Object key) { return valAt(key, null); }

    @Override public Object valAt(Object key, Object notFound) {
        if (entries == null) return large.valAt(key, notFound);
        int i = index(entries, entries.length, key);
        return i < 0 ? notFound : entries[i + 1];
    }

    public VarScope assoc(Var key, Object value) {
        if (value == SKIP) return this;
        if (entries == null) {
            IPersistentMap next = large.assoc(key, value);
            return next == large ? this : new VarScope(null, next);
        }
        int i = index(entries, entries.length, key);
        if (i >= 0 && entries[i + 1] == value) return this;
        if (i < 0 && size() == ARRAY_LIMIT) {
            return new VarScope(null, promote().assoc(key, value).persistent());
        }
        Object[] next = Arrays.copyOf(entries, entries.length + (i < 0 ? 2 : 0));
        if (i < 0) {
            i = entries.length;
            next[i] = key;
        }
        next[i + 1] = value;
        return new VarScope(next, null);
    }

    private ITransientMap promote() {
        ITransientMap map = PersistentHashMap.EMPTY.asTransient();
        for (int i = 0; i < entries.length; i += 2) {
            map = map.assoc(entries[i], entries[i + 1]);
        }
        return map;
    }

    /** Borrow input only during this call; never retain or mutate its array. */
    public VarScope extend(Object[] bindings) {
        if ((bindings.length & 1) != 0) throw new IllegalArgumentException("Unpaired bindings");
        if (bindings.length == 0) return this;
        if (bindings.length == 2) return assoc((Var) bindings[0], bindings[1]);
        boolean any = false;
        int added = 0;
        for (int i = 0; i < bindings.length; i += 2) {
            if (bindings[i + 1] == SKIP) continue;
            any = true;
            if (entries == null || index(entries, entries.length, bindings[i]) >= 0) continue;
            boolean earlier = false;
            for (int j = 0; j < i; j += 2) {
                if (bindings[j] == bindings[i] && bindings[j + 1] != SKIP) {
                    earlier = true;
                    break;
                }
            }
            if (!earlier) added++;
        }
        if (!any) return this;
        if (entries == null || size() + added > ARRAY_LIMIT) {
            ITransientMap result = entries == null
                    ? (ITransientMap) ((IEditableCollection) large).asTransient() : promote();
            for (int i = 0; i < bindings.length; i += 2) {
                if (bindings[i + 1] != SKIP) result = result.assoc(bindings[i], bindings[i + 1]);
            }
            return new VarScope(null, result.persistent());
        }
        // Count new distinct keys first, then copy once into an exactly sized array.
        Object[] next = Arrays.copyOf(entries, entries.length + added * 2);
        int length = entries.length;
        for (int i = 0; i < bindings.length; i += 2) {
            Object value = bindings[i + 1];
            if (value == SKIP) continue;
            int at = index(next, length, bindings[i]);
            if (at < 0) {
                at = length;
                length += 2;
                next[at] = bindings[i];
            }
            next[at + 1] = value;
        }
        return new VarScope(next, null);
    }
}
