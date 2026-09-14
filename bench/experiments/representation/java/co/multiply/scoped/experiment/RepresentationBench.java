package co.multiply.scoped.experiment;

import clojure.lang.IEditableCollection;
import clojure.lang.ILookup;
import clojure.lang.IPersistentMap;
import clojure.lang.ITransientMap;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Symbol;
import clojure.lang.Namespace;
import clojure.lang.Var;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Matching Java call paths for all stores, with no scoped runtime or carrier involved. */
public final class RepresentationBench {
    private static final Object ABSENT = new Object();
    public interface Model {
        Object empty();
        Object extend(Object parent, Object[] bindings);
        Object lookup(Object scope, Var key, Object notFound);
        int size(Object scope);
    }
    private abstract static class ClojureModel implements Model {
        public Object empty() { return PersistentArrayMap.EMPTY; }
        public Object lookup(Object scope, Var key, Object notFound) {
            return ((ILookup) scope).valAt(key, notFound);
        }
        public int size(Object scope) { return ((IPersistentMap) scope).count(); }
    }
    private static final class Persistent extends ClojureModel {
        public Object extend(Object parent, Object[] bindings) {
            IPersistentMap result = (IPersistentMap) parent;
            for (int i = 0; i < bindings.length; i += 2) {
                if (bindings[i + 1] != VarScope.SKIP) result = result.assoc(bindings[i], bindings[i + 1]);
            }
            return result;
        }
    }
    private static final class Transient extends ClojureModel {
        public Object extend(Object parent, Object[] bindings) {
            if (bindings.length == 0) return parent;
            if (bindings.length == 2) {
                return bindings[1] == VarScope.SKIP ? parent
                        : ((IPersistentMap) parent).assoc(bindings[0], bindings[1]);
            }
            ITransientMap result = (ITransientMap) ((IEditableCollection) parent).asTransient();
            for (int i = 0; i < bindings.length; i += 2) {
                if (bindings[i + 1] != VarScope.SKIP) result = result.assoc(bindings[i], bindings[i + 1]);
            }
            return result.persistent();
        }
    }
    private static final class Specialized implements Model {
        public Object empty() { return VarScope.EMPTY; }
        public Object extend(Object parent, Object[] bindings) { return ((VarScope) parent).extend(bindings); }
        public Object lookup(Object scope, Var key, Object notFound) {
            return ((VarScope) scope).valAt(key, notFound);
        }
        public int size(Object scope) { return ((VarScope) scope).size(); }
    }
    public static Model model(String name) {
        return switch (name) {
            case "clojure-persistent" -> new Persistent();
            case "clojure-transient" -> new Transient();
            case "var-scope" -> new Specialized();
            case "clojure-rebuild" -> new ArrayMapBatch();
            default -> throw new IllegalArgumentException(name);
        };
    }
    private static Var[] keys(int n) {
        Namespace ns = Namespace.findOrCreate(Symbol.intern("scoped.representation.fixtures"));
        Var[] keys = new Var[n];
        for (int i = 0; i < n; i++) keys[i] = Var.intern(ns, Symbol.intern("key-" + i));
        return keys;
    }
    public static final class Kernel {
        private final Model model;
        private final String operation;
        private final Object[] parents = new Object[128];
        private final Object[][] updates = new Object[128][];
        private final Object[][] innerUpdates = new Object[128][];
        private final Var[] queries = new Var[128];
        private final Object[] expected = new Object[128];
        private final Object[] sink = new Object[3];
        private int cursor;

        public Kernel(String modelName, String operation, int parentSize) {
            this.model = model(modelName);
            this.operation = operation;
            Var[] vars = keys(256);
            for (int frame = 0; frame < 128; frame++) {
                int offset = frame;
                Object[] values = new Object[parentSize * 2];
                for (int i = 0; i < parentSize; i++) {
                    values[2 * i] = vars[(offset + i) % vars.length];
                    values[2 * i + 1] = i == 0 ? null : i == 1 ? Boolean.FALSE : new Object();
                }
                parents[frame] = model.extend(model.empty(), values);
                // Lookup workload rotates nil, false, non-null last hit, and a miss.
                int q = parentSize == 0 ? 3 : switch (frame & 3) {
                    case 0 -> 0; case 1 -> 1; case 2 -> parentSize - 1; default -> parentSize + 3;
                };
                queries[frame] = vars[(offset + q) % vars.length];
                expected[frame] = q < parentSize ? values[q * 2 + 1] : ABSENT;
                updates[frame] = new Object[] {vars[(offset + parentSize) % vars.length], new Object(),
                        vars[(offset + parentSize + 1) % vars.length], new Object()};
                if (operation.equals("overwrite")) {
                    updates[frame] = new Object[] {vars[offset], new Object(),
                            vars[(offset + parentSize - 1) % vars.length], new Object()};
                }
                innerUpdates[frame] = new Object[] {vars[offset], new Object(),
                        vars[(offset + parentSize + 2) % vars.length], new Object()};
            }
        }
        public Object lookup() {
            int i = cursor++ & 127;
            return model.lookup(parents[i], queries[i], ABSENT);
        }
        public Object extend() {
            int i = cursor++ & 127;
            return model.extend(parents[i], updates[i]);
        }
        public Object nested() {
            int i = cursor++ & 127;
            Object captured = model.extend(parents[i], updates[i]);
            Object inner = model.extend(captured, innerUpdates[i]);
            // Escape both snapshots and a restored-parent read, preventing dead work.
            sink[0] = captured;
            sink[1] = inner;
            sink[2] = model.lookup(captured, (Var) updates[i][0], ABSENT);
            return sink;
        }
        private Object run() {
            return switch (operation) {
                case "lookup" -> lookup();
                case "extend", "overwrite" -> extend();
                case "nested" -> nested();
                default -> throw new IllegalArgumentException(operation);
            };
        }
        public void verify() {
            for (int i = 0; i < 128; i++) {
                cursor = i;
                Object value = run();
                if (operation.equals("lookup")) {
                    check(value == expected[i], "lookup fixture");
                } else if (operation.equals("extend") || operation.equals("overwrite")) {
                    int added = operation.equals("overwrite") ? 0 : 2;
                    check(model.size(value) == model.size(parents[i]) + added, "extension size");
                    check(model.lookup(value, (Var) updates[i][0], ABSENT) == updates[i][1], "extension value");
                } else {
                    check(sink[2] == updates[i][1], "capture/restore");
                    check(model.lookup(sink[0], (Var) innerUpdates[i][0], ABSENT) != innerUpdates[i][1], "parent immutable");
                }
            }
            cursor = 0;
        }
    }
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
    private static void equivalent(Model model, Object actual, IPersistentMap expected, Var[] vars) {
        check(model.size(actual) == expected.count(), "count");
        for (Var var : vars) check(model.lookup(actual, var, ABSENT) == expected.valAt(var, ABSENT), "value/absence");
    }
    public static void verifyAll() {
        Var[] vars = keys(64);
        for (String name : new String[] {"clojure-persistent", "clojure-transient", "var-scope", "clojure-rebuild"}) {
            Model model = model(name);
            Model oracle = model("clojure-persistent");
            Random random = new Random(40219);
            List<Object> snapshots = new ArrayList<>();
            List<IPersistentMap> expectedSnapshots = new ArrayList<>();
            Object scope = model.empty();
            IPersistentMap expected = PersistentArrayMap.EMPTY;
            snapshots.add(scope);
            expectedSnapshots.add(expected);
            // Force every size, including 8 -> 9 promotion, before randomized updates.
            for (int i = 0; i < vars.length; i++) {
                Object[] batch = {vars[i], new Object()};
                scope = model.extend(scope, batch);
                expected = (IPersistentMap) oracle.extend(expected, batch);
                equivalent(model, scope, expected, vars);
                snapshots.add(scope);
                expectedSnapshots.add(expected);
            }
            for (int iteration = 0; iteration < 2000; iteration++) {
                int parent = random.nextInt(snapshots.size());
                Object[] batch = new Object[random.nextInt(6) * 2];
                for (int i = 0; i < batch.length; i += 2) {
                    batch[i] = vars[random.nextInt(vars.length)];
                    batch[i + 1] = switch (random.nextInt(5)) {
                        case 0 -> null; case 1 -> Boolean.FALSE; case 2 -> VarScope.SKIP; default -> new Object();
                    };
                }
                scope = model.extend(snapshots.get(parent), batch);
                expected = (IPersistentMap) oracle.extend(expectedSnapshots.get(parent), batch);
                equivalent(model, scope, expected, vars);
                equivalent(model, snapshots.get(parent), expectedSnapshots.get(parent), vars);
                // Mutating the caller-owned input afterward must not mutate the returned snapshot.
                java.util.Arrays.fill(batch, null);
                equivalent(model, scope, expected, vars);
                snapshots.add(scope);
                expectedSnapshots.add(expected);
            }
            for (int i = 0; i < snapshots.size(); i++) equivalent(model, snapshots.get(i), expectedSnapshots.get(i), vars);
        }
    }
}
