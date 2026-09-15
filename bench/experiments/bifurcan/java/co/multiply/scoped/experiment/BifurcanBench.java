package co.multiply.scoped.experiment;

import clojure.lang.IEditableCollection;
import clojure.lang.ILookup;
import clojure.lang.IPersistentMap;
import clojure.lang.ITransientMap;
import clojure.lang.Namespace;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Symbol;
import clojure.lang.Var;
import io.lacuna.bifurcan.Map;
import io.lacuna.bifurcan.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BinaryOperator;

/** Scope storage only: direct Java calls, real Var keys, persistent snapshots. */
public final class BifurcanBench {
    private static final Object ABSENT = new Object();
    private static final Object SKIP = new Object();
    private static final Var[] KEYS = keys();
    @SuppressWarnings("unchecked")
    private static final BinaryOperator<Object> LAST = Maps.MERGE_LAST_WRITE_WINS;

    private static Var[] keys() {
        Namespace ns = Namespace.findOrCreate(Symbol.intern("scoped.bifurcan.fixtures"));
        Var[] keys = new Var[256];
        // Assign every identity hash before any model-specific construction/verification.
        for (int i = 0; i < keys.length; i++) {
            keys[i] = Var.intern(ns, Symbol.intern("key-" + i));
            keys[i].hashCode();
        }
        return keys;
    }

    public static int[] keyHashes() {
        int[] result = new int[KEYS.length];
        for (int i = 0; i < result.length; i++) result[i] = KEYS[i].hashCode();
        return result;
    }

    interface Model {
        Object empty();
        Object lookup(Object scope, Var key);
        Object assoc(Object scope, Var key, Object value);
        Object batch(Object scope, Object[] bindings);
        int size(Object scope);
    }

    static final class Clojure implements Model {
        public Object empty() { return PersistentArrayMap.EMPTY; }
        public Object lookup(Object scope, Var key) { return ((ILookup) scope).valAt(key, ABSENT); }
        public int size(Object scope) { return ((IPersistentMap) scope).count(); }
        public Object assoc(Object scope, Var key, Object value) {
            return value == SKIP ? scope : ((IPersistentMap) scope).assoc(key, value);
        }
        public Object batch(Object scope, Object[] bindings) {
            if (bindings.length == 0) return scope;
            ITransientMap result = (ITransientMap) ((IEditableCollection) scope).asTransient();
            for (int i = 0; i < bindings.length; i += 2) {
                if (bindings[i + 1] != SKIP) result = result.assoc(bindings[i], bindings[i + 1]);
            }
            return result.persistent();
        }
    }

    static final class Bifurcan implements Model {
        @SuppressWarnings("unchecked")
        private Map<Var, Object> map(Object scope) { return (Map<Var, Object>) scope; }
        public Object empty() { return Map.<Var, Object>empty(); }
        public Object lookup(Object scope, Var key) { return map(scope).get(key, ABSENT); }
        public int size(Object scope) { return Math.toIntExact(map(scope).size()); }
        public Object assoc(Object scope, Var key, Object value) {
            return value == SKIP ? scope : map(scope).put(key, value, LAST);
        }
        public Object batch(Object scope, Object[] bindings) {
            if (bindings.length == 0) return scope;
            Map<Var, Object> result = map(scope).linear();
            for (int i = 0; i < bindings.length; i += 2) {
                if (bindings[i + 1] != SKIP) {
                    result = result.put((Var) bindings[i], bindings[i + 1], LAST);
                }
            }
            // Discard the builder. Never publish it or reuse it after forked().
            return result.forked();
        }
    }

    private static Model model(String name) {
        return switch (name) {
            case "clojure" -> new Clojure();
            case "bifurcan" -> new Bifurcan();
            default -> throw new IllegalArgumentException(name);
        };
    }

    public static final class Kernel {
        private final Model model;
        private final String operation;
        private final int parentSize;
        private final Object[] parents = new Object[128];
        private final IPersistentMap[] expectedParents = new IPersistentMap[128];
        private final Var[] queries = new Var[128];
        private final Object[][] updates = new Object[128][];
        private final Object[][] snapshots = new Object[128][10];
        private final Object[] sink = new Object[128];
        private int cursor;

        public Kernel(String modelName, String operation, int parentSize, int batchSize) {
            this.model = model(modelName);
            this.operation = operation;
            this.parentSize = parentSize;
            check(parentSize >= 0 && parentSize <= 64, "parent size 0..64");
            check(List.of("override", "add", "hit", "miss", "batch", "chain").contains(operation), "operation");
            check(parentSize > 0 || List.of("add", "miss", "batch").contains(operation), "nonempty parent required");
            check(batchSize >= 1 && batchSize <= 32, "batch size 1..32");
            for (int frame = 0; frame < 128; frame++) {
                Object parent = model.empty();
                IPersistentMap expected = PersistentArrayMap.EMPTY;
                for (int j = 0; j < parentSize; j++) {
                    Var key = KEYS[frame + j];
                    Object value = j == 0 ? null : j == 1 ? Boolean.FALSE : new Object();
                    parent = model.assoc(parent, key, value);
                    expected = expected.assoc(key, value);
                }
                parents[frame] = parent;
                expectedParents[frame] = expected;
                int slot = parentSize == 0 ? 0 : frame % parentSize;
                boolean absent = operation.equals("add") || operation.equals("miss");
                queries[frame] = KEYS[frame + (absent ? parentSize : slot)];
                int count = operation.equals("batch") ? batchSize : operation.equals("chain") ? 10 : 1;
                Object[] bindings = new Object[count * 2];
                for (int j = 0; j < count; j++) {
                    // Batch: alternate override/add. From empty all are additions.
                    Var key = operation.equals("batch")
                            ? KEYS[frame + (parentSize > 0 && j % 2 == 0
                                ? (slot + j / 2) % parentSize : parentSize + j / 2)]
                            : queries[frame];
                    bindings[j * 2] = key;
                    bindings[j * 2 + 1] = new Object();
                }
                // Empty construction needs distinct keys, not alternating duplicates.
                if (parentSize == 0 && operation.equals("batch")) {
                    for (int j = 0; j < count; j++) bindings[j * 2] = KEYS[frame + j];
                }
                updates[frame] = bindings;
            }
        }

        public Object run() {
            int i = cursor++ & 127;
            Object result;
            switch (operation) {
                case "hit", "miss" -> result = model.lookup(parents[i], queries[i]);
                case "override", "add" -> result = model.assoc(parents[i], queries[i], updates[i][1]);
                case "batch" -> result = model.batch(parents[i], updates[i]);
                case "chain" -> {
                    result = parents[i];
                    for (int j = 0; j < 10; j++) {
                        result = model.assoc(result, queries[i], updates[i][j * 2 + 1]);
                        snapshots[i][j] = result;
                    }
                }
                default -> throw new AssertionError(operation);
            }
            // Updates escape, including every intermediate child in a chain.
            sink[i] = result;
            return result;
        }

        public String parentClass() { return parents[0].getClass().getName(); }

        public void verify() {
            for (int i = 0; i < 128; i++) {
                cursor = i;
                Object actual = run();
                IPersistentMap expected = expectedParents[i];
                if (operation.equals("hit") || operation.equals("miss")) {
                    check(actual == expected.valAt(queries[i], ABSENT), "lookup");
                } else {
                    for (int j = 0; j < updates[i].length; j += 2) {
                        expected = expected.assoc(updates[i][j], updates[i][j + 1]);
                        if (operation.equals("chain")) equivalent(model, snapshots[i][j / 2], expected);
                    }
                    equivalent(model, actual, expected);
                }
                equivalent(model, parents[i], expectedParents[i]);
                check(model.size(parents[i]) == parentSize, "retained parent size");
            }
            cursor = 0;
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private static void equivalent(Model model, Object actual, IPersistentMap expected) {
        check(model.size(actual) == expected.count(), "size");
        if (actual instanceof Map<?, ?>) check(!((Map<?, ?>) actual).isLinear(), "published map is persistent");
        for (Var key : KEYS) check(model.lookup(actual, key) == expected.valAt(key, ABSENT), "value/absence");
    }

    /** Branch from retained ancestors, then recheck every ancestor after later mutations. */
    public static void verifyModel(String name) {
        Model model = model(name);
        Random random = new Random(73491);
        List<Object> snapshots = new ArrayList<>();
        List<IPersistentMap> expected = new ArrayList<>();
        snapshots.add(model.empty());
        expected.add(PersistentArrayMap.EMPTY);
        for (int step = 0; step < 500; step++) {
            int parent = random.nextInt(snapshots.size());
            int count = step % 21;
            Object[] updates = new Object[count * 2];
            IPersistentMap reference = expected.get(parent);
            for (int i = 0; i < count; i++) {
                Var key = KEYS[random.nextInt(40)];
                Object value = switch (random.nextInt(5)) {
                    case 0 -> null;
                    case 1 -> Boolean.FALSE;
                    case 2 -> SKIP;
                    default -> new Object();
                };
                updates[i * 2] = key;
                updates[i * 2 + 1] = value;
                if (value != SKIP) reference = reference.assoc(key, value);
            }
            Object scope = snapshots.get(parent);
            Object result = count == 1 ? model.assoc(scope, (Var) updates[0], updates[1]) : model.batch(scope, updates);
            check(model.assoc(result, KEYS[0], SKIP) == result, "single SKIP preserves identity");
            equivalent(model, model.batch(result, new Object[] {KEYS[0], SKIP, KEYS[41], SKIP}), reference);
            snapshots.add(result);
            expected.add(reference);
            java.util.Arrays.fill(updates, null);
        }
        for (int i = 0; i < snapshots.size(); i++) equivalent(model, snapshots.get(i), expected.get(i));
    }
}
