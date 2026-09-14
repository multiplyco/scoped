package co.multiply.scoped.experiment;

import clojure.lang.AFn;
import clojure.lang.ILookup;
import clojure.lang.IPersistentMap;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Var;

/** Bulk construction control for Var keys: retain Clojure maps using only their public API. */
public final class ArrayMapBatch implements RepresentationBench.Model {
    private static final int ARRAY_LIMIT = 8;

    @Override public Object empty() { return PersistentArrayMap.EMPTY; }
    @Override public Object lookup(Object scope, Var key, Object notFound) {
        return ((ILookup) scope).valAt(key, notFound);
    }
    @Override public int size(Object scope) { return ((IPersistentMap) scope).count(); }

    private static final class CopyEntries extends AFn {
        private final Object[] target;
        private int offset;
        CopyEntries(Object[] target) { this.target = target; }
        @Override public Object invoke(Object ignored, Object key, Object value) {
            target[offset++] = key;
            target[offset++] = value;
            return null;
        }
    }

    private static IPersistentMap persistent(IPersistentMap parent, Object[] bindings) {
        for (int i = 0; i < bindings.length; i += 2) {
            if (bindings[i + 1] != VarScope.SKIP) parent = parent.assoc(bindings[i], bindings[i + 1]);
        }
        return parent;
    }

    @Override public Object extend(Object scope, Object[] bindings) {
        if ((bindings.length & 1) != 0) throw new IllegalArgumentException("Unpaired bindings");
        IPersistentMap parent = (IPersistentMap) scope;
        if (bindings.length == 0) return parent;
        if (bindings.length == 2 || !(parent instanceof PersistentArrayMap)) {
            return persistent(parent, bindings);
        }
        int added = 0;
        boolean any = false;
        for (int i = 0; i < bindings.length; i += 2) {
            if (bindings[i + 1] == VarScope.SKIP) continue;
            any = true;
            if (parent.containsKey(bindings[i])) continue;
            boolean earlier = false;
            for (int j = 0; j < i; j += 2) {
                if (bindings[j] == bindings[i] && bindings[j + 1] != VarScope.SKIP) {
                    earlier = true;
                    break;
                }
            }
            if (!earlier) added++;
        }
        if (!any) return parent;
        if (parent.count() + added > ARRAY_LIMIT) return persistent(parent, bindings);

        // The public constructor retains this array. Own it exclusively and never mutate it afterward.
        Object[] next = new Object[(parent.count() + added) * 2];
        PersistentArrayMap arrayMap = (PersistentArrayMap) parent;
        if (parent.count() != 0) arrayMap.kvreduce(new CopyEntries(next), null);
        int length = parent.count() * 2;
        for (int i = 0; i < bindings.length; i += 2) {
            Object value = bindings[i + 1];
            if (value == VarScope.SKIP) continue;
            int at = 0;
            while (at < length && next[at] != bindings[i]) at += 2;
            if (at == length) {
                next[at] = bindings[i];
                length += 2;
            }
            next[at + 1] = value;
        }
        return new PersistentArrayMap(arrayMap.meta(), next);
    }
}
