package co.multiply.scoped.experiment;

import clojure.lang.IFn;

/** Identical fixture rotation and escaping result storage for every implementation. */
public final class ExtendKernel {
    private final IFn operation;
    private final Object[] parents;
    private final Object[][] values;
    private final Object[] results;
    private int cursor;

    public ExtendKernel(IFn operation, Object[] parents, Object[][] values) {
        if (parents.length != 128 || values.length != parents.length) {
            throw new IllegalArgumentException("Expected 128 fixtures");
        }
        this.operation = operation;
        this.parents = parents;
        this.values = values;
        this.results = new Object[parents.length];
    }

    public Object run() {
        int index = cursor++ & 127;
        Object result = operation.invoke(parents[index], values[index]);
        results[index] = result;
        return result;
    }
}
