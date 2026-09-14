package co.multiply.scoped.experiment;

import clojure.lang.IPersistentMap;
import clojure.lang.Namespace;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Symbol;
import clojure.lang.Var;

/** Isolated Java 25 kernels; no changes to Scoped or Quiescent. */
public final class TaskSlotBench {
    private static final ScopedValue<IPersistentMap> MAP = ScopedValue.newInstance();
    private static final ScopedValue<IPersistentMap> CONTEXT = ScopedValue.newInstance();
    private static final ScopedValue<Object> TASK = ScopedValue.newInstance();
    private static final Object ABSENT = new Object();
    private static final Var TASK_KEY;
    private static final Var[] KEYS = new Var[64];

    static {
        Namespace ns = Namespace.findOrCreate(Symbol.intern("scoped.task-slot.fixtures"));
        TASK_KEY = Var.intern(ns, Symbol.intern("task"));
        for (int i = 0; i < KEYS.length; i++) {
            KEYS[i] = Var.intern(ns, Symbol.intern("context-" + i));
        }
    }

    private TaskSlotBench() {}

    private static final class Frame {
        final IPersistentMap context;
        final IPersistentMap combined;
        final ScopedValue.Carrier contextCarrier;
        final Object[] tasks;
        final Object[] captured;
        final Var readKey;
        final Object readValue;

        Frame(int size, int depth, int index) {
            IPersistentMap context = PersistentArrayMap.EMPTY;
            Object initialTask = new Object();
            // Rotate the task's array-map position; hash maps also see varying values.
            int taskPosition = index % (size + 1);
            IPersistentMap combined = PersistentArrayMap.EMPTY;
            for (int i = 0; i <= size; i++) {
                if (i == taskPosition) combined = combined.assoc(TASK_KEY, initialTask);
                if (i < size) {
                    Object value = i % 3 == 0 ? null : i % 3 == 1 ? Boolean.FALSE : new Object();
                    context = context.assoc(KEYS[i], value);
                    combined = combined.assoc(KEYS[i], value);
                }
            }
            this.context = context;
            this.combined = combined;
            this.contextCarrier = ScopedValue.where(CONTEXT, context);
            this.tasks = new Object[depth];
            for (int i = 0; i < depth; i++) tasks[i] = new Object();
            this.captured = new Object[depth];
            this.readKey = KEYS[index % Math.max(1, size)];
            this.readValue = context.valAt(readKey, ABSENT);
        }
    }

    public static final class Kernel {
        private final String model;
        private final String mode;
        private final int depth;
        private final Frame[] frames = new Frame[128];
        private int cursor;

        public Kernel(String model, String mode, int contextSize, int depth) {
            if (!model.equals("map") && !model.equals("split") && !model.equals("split-cached"))
                throw new IllegalArgumentException("Unknown model: " + model);
            if (!mode.equals("nested") && !mode.equals("handoff"))
                throw new IllegalArgumentException("Unknown mode: " + mode);
            if (contextSize < 0 || contextSize > KEYS.length || depth < 1 || depth > 100)
                throw new IllegalArgumentException("Invalid size/depth");
            this.model = model;
            this.mode = mode;
            this.depth = depth;
            for (int i = 0; i < frames.length; i++) frames[i] = new Frame(contextSize, depth, i);
        }

        public String parentClass() { return frames[0].combined.getClass().getSimpleName(); }
        public String contextClass() { return frames[0].context.getClass().getSimpleName(); }

        public Object run() {
            Frame frame = frames[cursor++ & (frames.length - 1)];
            if (mode.equals("handoff")) return handoff(frame);
            return model.equals("map") ? mapNested(frame, frame.combined, 0)
                    : splitNested(frame, frame.context, 0);
        }

        private Object mapNested(Frame frame, IPersistentMap parent, int level) {
            IPersistentMap next = parent.assoc(TASK_KEY, frame.tasks[level]);
            return ScopedValue.where(MAP, next).call(() -> {
                IPersistentMap current = MAP.orElse(PersistentArrayMap.EMPTY);
                check(current.valAt(TASK_KEY, ABSENT) == frame.tasks[level], "task lookup");
                check(current.valAt(frame.readKey, ABSENT) == frame.readValue, "context lookup");
                frame.captured[level] = current;
                return level + 1 == depth ? frame.tasks[level] : mapNested(frame, current, level + 1);
            });
        }

        private ScopedValue.Carrier carrier(Frame frame, IPersistentMap context, int level) {
            // Always extend the task-free base. Never append to a previous child's carrier.
            ScopedValue.Carrier base = model.equals("split-cached")
                    ? frame.contextCarrier : ScopedValue.where(CONTEXT, context);
            return base.where(TASK, frame.tasks[level]);
        }

        private Object splitNested(Frame frame, IPersistentMap context, int level) {
            return carrier(frame, context, level).call(() -> {
                IPersistentMap current = CONTEXT.orElse(PersistentArrayMap.EMPTY);
                check(TASK.get() == frame.tasks[level], "task lookup");
                check(current.valAt(frame.readKey, ABSENT) == frame.readValue, "context lookup");
                frame.captured[level] = current;
                return level + 1 == depth ? frame.tasks[level] : splitNested(frame, current, level + 1);
            });
        }

        private Object handoff(Frame frame) {
            IPersistentMap captured = model.equals("map") ? frame.combined : frame.context;
            for (int i = 0; i < depth; i++) {
                final int level = i;
                if (model.equals("map")) {
                    IPersistentMap next = captured.assoc(TASK_KEY, frame.tasks[level]);
                    captured = ScopedValue.where(MAP, next).call(() -> {
                        IPersistentMap current = MAP.orElse(PersistentArrayMap.EMPTY);
                        check(current.valAt(TASK_KEY, ABSENT) == frame.tasks[level], "task lookup");
                        check(current.valAt(frame.readKey, ABSENT) == frame.readValue, "context lookup");
                        return current;
                    });
                } else {
                    captured = carrier(frame, captured, level).call(() -> {
                        IPersistentMap current = CONTEXT.orElse(PersistentArrayMap.EMPTY);
                        check(TASK.get() == frame.tasks[level], "task lookup");
                        check(current.valAt(frame.readKey, ABSENT) == frame.readValue, "context lookup");
                        return current;
                    });
                }
                // Bounded retention models task-owned snapshots across non-overlapping bindings.
                frame.captured[level] = captured;
            }
            return frame.tasks[depth - 1];
        }

        public void verify() {
            check(!MAP.isBound() && !CONTEXT.isBound() && !TASK.isBound(), "initially unbound");
            for (int i = 0; i < frames.length; i++) {
                cursor = i;
                Frame frame = frames[i];
                Object original = frame.tasks[0];
                frame.tasks[0] = i % 2 == 0 ? null : Boolean.FALSE;
                check(run() == frame.tasks[depth - 1], "result");
                check(!MAP.isBound() && !CONTEXT.isBound() && !TASK.isBound(), "restoration");
                for (int level = 0; level < depth; level++) {
                    IPersistentMap saved = (IPersistentMap) frame.captured[level];
                    if (model.equals("map")) {
                        check(saved.count() == frame.context.count() + 1, "map count");
                        check(saved.valAt(TASK_KEY, ABSENT) == frame.tasks[level], "retained task");
                    } else {
                        check(saved == frame.context, "shared context identity");
                        check(saved.valAt(TASK_KEY, ABSENT) == ABSENT, "task-free context");
                    }
                    for (int k = 0; k < KEYS.length; k++)
                        check(saved.valAt(KEYS[k], ABSENT) == frame.context.valAt(KEYS[k], ABSENT), "ambient snapshot");
                }
                check(frame.combined.valAt(TASK_KEY, ABSENT) != frame.tasks[0], "unchanged parent");
                frame.tasks[0] = original;
            }
            Frame frame = frames[0];
            Object outerTask = new Object();
            RuntimeException expected = new RuntimeException("expected");
            ScopedValue.where(MAP, frame.combined).where(CONTEXT, frame.context).where(TASK, outerTask).run(() -> {
                run();
                check(MAP.get() == frame.combined && CONTEXT.get() == frame.context && TASK.get() == outerTask,
                        "restore outer bindings");
                try {
                    ScopedValue.Carrier c = model.equals("map")
                            ? ScopedValue.where(MAP, frame.combined.assoc(TASK_KEY, frame.tasks[0]))
                            : carrier(frame, frame.context, 0);
                    c.run(() -> { throw expected; });
                    throw new AssertionError("exception not propagated");
                } catch (RuntimeException actual) {
                    check(actual == expected, "exception identity");
                }
                check(MAP.get() == frame.combined && CONTEXT.get() == frame.context && TASK.get() == outerTask,
                        "exception restoration");
            });
            cursor = 0;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
