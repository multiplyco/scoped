package co.multiply.scoped.experiment;

import clojure.lang.IPersistentMap;
import clojure.lang.Namespace;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Symbol;
import clojure.lang.Var;

/** Crossover fixtures. Classification happens only here, never in the measured update loop. */
public final class BatchBench {
    private static final Object ABSENT = new Object();
    private static final int FRAMES = 128;

    public static final class Kernel {
        private final RepresentationBench.Model model;
        private final IPersistentMap[] parents = new IPersistentMap[FRAMES];
        private final Object[][] batches = new Object[FRAMES][];
        private final IPersistentMap[] expected = new IPersistentMap[FRAMES];
        private final Var[] vars = new Var[512];
        private int cursor;

        public Kernel(String modelName, int parentSize, int batchSize, String workload) {
            if (parentSize < 0 || parentSize > 128 || (parentSize != 0 && Integer.bitCount(parentSize) != 1)
                    || batchSize < 2 || batchSize > 64)
                throw new IllegalArgumentException("Unsupported fixture size");
            model = RepresentationBench.model(modelName);
            if (!modelName.equals("clojure-persistent") && !modelName.equals("clojure-transient"))
                throw new IllegalArgumentException("Crossover compares Clojure update strategies");
            Namespace ns = Namespace.findOrCreate(Symbol.intern("scoped.batch.fixtures"));
            for (int i = 0; i < vars.length; i++) vars[i] = Var.intern(ns, Symbol.intern("key-" + i));
            for (int frame = 0; frame < FRAMES; frame++) {
                IPersistentMap parent = PersistentArrayMap.EMPTY;
                for (int i = 0; i < parentSize; i++) {
                    parent = parent.assoc(vars[frame + i], i == 0 ? null : i == 1 ? Boolean.FALSE : new Object());
                }
                parents[frame] = parent;
                Object[] bindings = new Object[batchSize * 2];
                int nextExisting = 0;
                int nextNew = 0;
                for (int i = 0; i < batchSize; i++) {
                    boolean existing = switch (workload) {
                        case "add", "half-skip", "all-skip" -> false;
                        case "override", "same-value" -> true;
                        case "mixed" -> (i & 1) == 0;
                        case "duplicate" -> false;
                        default -> throw new IllegalArgumentException(workload);
                    };
                    int keyIndex;
                    if (existing) {
                        if (nextExisting >= parentSize) throw new IllegalArgumentException("Too many distinct overrides");
                        // Odd stride visits all slots of the power-of-two parents; rotate positions per frame.
                        keyIndex = (frame + nextExisting++ * 5) % parentSize;
                    } else if (workload.equals("duplicate")) {
                        keyIndex = parentSize + (i & 1);
                    } else {
                        keyIndex = parentSize + nextNew++;
                    }
                    Var key = vars[frame + keyIndex];
                    Object value = switch (workload) {
                        case "all-skip" -> VarScope.SKIP;
                        case "half-skip" -> (i & 1) == 0 ? VarScope.SKIP : new Object();
                        case "same-value" -> parent.valAt(key, ABSENT);
                        default -> i % 7 == 0 ? null : i % 7 == 1 ? Boolean.FALSE : new Object();
                    };
                    // Overrides are real changes; same-value is an explicit separate sensitivity case.
                    if (existing && !workload.equals("same-value") && parent.valAt(key, ABSENT) == value)
                        value = new Object();
                    bindings[i * 2] = key;
                    bindings[i * 2 + 1] = value;
                }
                batches[frame] = bindings;
                IPersistentMap result = parent;
                for (int i = 0; i < bindings.length; i += 2) {
                    if (bindings[i + 1] != VarScope.SKIP) result = result.assoc(bindings[i], bindings[i + 1]);
                }
                expected[frame] = result;
            }
        }

        public Object run() {
            int frame = cursor++ & (FRAMES - 1);
            return model.extend(parents[frame], batches[frame]);
        }

        public String parentClass() { return parents[0].getClass().getName(); }
        public int resultSize() { return expected[0].count(); }

        public void verify() {
            for (int frame = 0; frame < FRAMES; frame++) {
                IPersistentMap parent = parents[frame];
                Object[] parentValues = new Object[vars.length];
                for (int i = 0; i < vars.length; i++) parentValues[i] = parent.valAt(vars[i], ABSENT);
                cursor = frame;
                Object result = run();
                if (model.size(result) != expected[frame].count()) throw new AssertionError("Result count");
                for (Var key : vars) {
                    if (model.lookup(result, key, ABSENT) != expected[frame].valAt(key, ABSENT))
                        throw new AssertionError("Result value");
                }
                // The parent and borrowed argument array remain unchanged across repeated calls.
                Object[] borrowed = batches[frame].clone();
                Object second = model.extend(parent, borrowed);
                java.util.Arrays.fill(borrowed, null);
                for (int i = 0; i < vars.length; i++) {
                    if (parent.valAt(vars[i], ABSENT) != parentValues[i]) throw new AssertionError("Parent mutated");
                    if (model.lookup(second, vars[i], ABSENT) != expected[frame].valAt(vars[i], ABSENT))
                        throw new AssertionError("Borrowed array retained");
                }
            }
            cursor = 0;
        }
    }
}
