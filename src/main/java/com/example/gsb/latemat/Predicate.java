package com.example.gsb.latemat;

import java.util.Objects;

/**
 * 单列筛选条件，例如 {@code Predicate.on("age", Op.GE, 18)}。
 */
public record Predicate(String column, Op op, Object value) {

    public Predicate {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(value, "value");
    }

    public static Predicate on(String column, Op op, Object value) {
        return new Predicate(column, op, value);
    }

    public boolean test(Object actual) {
        return op.test(actual, value);
    }
}
