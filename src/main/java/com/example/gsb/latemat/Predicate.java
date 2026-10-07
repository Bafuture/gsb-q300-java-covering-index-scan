package com.example.gsb.latemat;

import java.util.Arrays;
import java.util.Objects;

/**
 * A single-column condition used to build the candidate set. Predicates are evaluated
 * against cells of exactly one column, so constructing the candidate set never touches
 * any other column.
 */
public final class Predicate {
  public enum Op { EQ, NE, LT, LE, GT, GE, BETWEEN, IN }

  private final int columnIndex;
  private final Op op;
  private final Comparable<?>[] values;

  private Predicate(int columnIndex, Op op, Comparable<?>... values) {
    if (columnIndex < 0) {
      throw new IllegalArgumentException("columnIndex must be >= 0");
    }
    this.columnIndex = columnIndex;
    this.op = Objects.requireNonNull(op, "op");
    this.values = Objects.requireNonNull(values, "values").clone();
    if (values.length == 0) {
      throw new IllegalArgumentException("predicate needs at least one value");
    }
  }

  public static Predicate eq(int column, Comparable<?> value) {
    return new Predicate(column, Op.EQ, value);
  }

  public static Predicate ne(int column, Comparable<?> value) {
    return new Predicate(column, Op.NE, value);
  }

  public static Predicate lt(int column, Comparable<?> value) {
    return new Predicate(column, Op.LT, value);
  }

  public static Predicate le(int column, Comparable<?> value) {
    return new Predicate(column, Op.LE, value);
  }

  public static Predicate gt(int column, Comparable<?> value) {
    return new Predicate(column, Op.GT, value);
  }

  public static Predicate ge(int column, Comparable<?> value) {
    return new Predicate(column, Op.GE, value);
  }

  public static Predicate between(int column, Comparable<?> low, Comparable<?> high) {
    return new Predicate(column, Op.BETWEEN, low, high);
  }

  public static Predicate in(int column, Comparable<?>... values) {
    return new Predicate(column, Op.IN, values);
  }

  public int columnIndex() {
    return columnIndex;
  }

  public Op op() {
    return op;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public boolean test(Comparable<?> cell) {
    if (cell == null) {
      return false;
    }
    Comparable v = cell;
    return switch (op) {
      case EQ -> v.compareTo(values[0]) == 0;
      case NE -> v.compareTo(values[0]) != 0;
      case LT -> v.compareTo(values[0]) < 0;
      case LE -> v.compareTo(values[0]) <= 0;
      case GT -> v.compareTo(values[0]) > 0;
      case GE -> v.compareTo(values[0]) >= 0;
      case BETWEEN -> v.compareTo(values[0]) >= 0 && v.compareTo(values[1]) <= 0;
      case IN -> {
        for (Comparable<?> candidate : values) {
          if (v.compareTo(candidate) == 0) {
            yield true;
          }
        }
        yield false;
      }
    };
  }

  @Override
  public String toString() {
    return "Predicate{col=" + columnIndex + ", op=" + op + ", values="
        + Arrays.toString(values) + '}';
  }
}
