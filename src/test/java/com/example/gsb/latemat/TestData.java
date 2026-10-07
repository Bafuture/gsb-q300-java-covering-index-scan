package com.example.gsb.latemat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Builders for deterministic-but-random column stores and predicates. */
final class TestData {
  private TestData() {}

  private static final String[] DICTIONARY =
      {"alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta"};

  static ColumnStore randomIntStore(Random rnd, int rows, int columns, int bound) {
    List<Column<?>> cols = new ArrayList<>();
    for (int c = 0; c < columns; c++) {
      cols.add(randomIntColumn(rnd, "int" + c, rows, bound));
    }
    return new ColumnStore(cols);
  }

  /** Mixed-type store: repeating pattern of int / double / string columns. */
  static ColumnStore randomMixedStore(Random rnd, int rows, int columns) {
    List<Column<?>> cols = new ArrayList<>();
    for (int c = 0; c < columns; c++) {
      switch (c % 3) {
        case 0 -> cols.add(randomIntColumn(rnd, "int" + c, rows, 100));
        case 1 -> cols.add(randomDoubleColumn(rnd, "dbl" + c, rows));
        default -> cols.add(randomStringColumn(rnd, "str" + c, rows));
      }
    }
    return new ColumnStore(cols);
  }

  static Column<Integer> randomIntColumn(Random rnd, String name, int rows, int bound) {
    Integer[] data = new Integer[rows];
    for (int i = 0; i < rows; i++) {
      data[i] = rnd.nextInt(bound);
    }
    return new Column<>(name, Integer.class, data);
  }

  static Column<Double> randomDoubleColumn(Random rnd, String name, int rows) {
    Double[] data = new Double[rows];
    for (int i = 0; i < rows; i++) {
      data[i] = rnd.nextDouble();
    }
    return new Column<>(name, Double.class, data);
  }

  static Column<String> randomStringColumn(Random rnd, String name, int rows) {
    String[] data = new String[rows];
    for (int i = 0; i < rows; i++) {
      data[i] = DICTIONARY[rnd.nextInt(DICTIONARY.length)];
    }
    return new Column<>(name, String.class, data);
  }

  /** A random predicate that is guaranteed type-compatible with the store's columns. */
  static Predicate randomPredicate(Random rnd, ColumnStore store) {
    int col = rnd.nextInt(store.columnCount());
    Class<?> type = store.column(col).type();
    if (type == Integer.class) {
      return switch (rnd.nextInt(5)) {
        case 0 -> Predicate.eq(col, rnd.nextInt(100));
        case 1 -> Predicate.lt(col, rnd.nextInt(100));
        case 2 -> Predicate.ge(col, rnd.nextInt(100));
        case 3 -> Predicate.between(col, rnd.nextInt(50), 50 + rnd.nextInt(50));
        default -> Predicate.in(col, rnd.nextInt(100), rnd.nextInt(100), rnd.nextInt(100));
      };
    }
    if (type == Double.class) {
      return switch (rnd.nextInt(3)) {
        case 0 -> Predicate.lt(col, rnd.nextDouble());
        case 1 -> Predicate.ge(col, rnd.nextDouble());
        default -> {
          double low = rnd.nextDouble();
          yield Predicate.between(col, low, low + rnd.nextDouble() * (1.0 - low));
        }
      };
    }
    if (type == String.class) {
      return switch (rnd.nextInt(3)) {
        case 0 -> Predicate.eq(col, DICTIONARY[rnd.nextInt(DICTIONARY.length)]);
        case 1 -> Predicate.ne(col, DICTIONARY[rnd.nextInt(DICTIONARY.length)]);
        default -> Predicate.in(col,
            DICTIONARY[rnd.nextInt(DICTIONARY.length)],
            DICTIONARY[rnd.nextInt(DICTIONARY.length)]);
      };
    }
    throw new IllegalStateException("unexpected column type " + type);
  }

  static int[] randomOutputColumns(Random rnd, int columnCount) {
    int count = 1 + rnd.nextInt(columnCount);
    List<Integer> indexes = new ArrayList<>();
    for (int i = 0; i < columnCount; i++) {
      indexes.add(i);
    }
    java.util.Collections.shuffle(indexes, rnd);
    return indexes.subList(0, count).stream().mapToInt(Integer::intValue).sorted().toArray();
  }
}
