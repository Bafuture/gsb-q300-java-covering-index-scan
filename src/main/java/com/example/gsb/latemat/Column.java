package com.example.gsb.latemat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A single typed column of the column store, run-length encoded (RLE) like a stripe
 * in a real columnar format (Parquet/ORC style). Values of consecutive equal cells
 * are stored once as runs.
 *
 * <p>Two read paths are deliberately exposed, with different real costs:
 * <ul>
 *   <li>{@link #forEach(int, int, BiConsumer)} walks runs linearly — a cheap
 *       sequential scan used for the first filter column;</li>
 *   <li>{@link #get(int)} locates the run containing one logical row by binary
 *       search — the more expensive point-lookup path used after filtering when only
 *       surviving rows are fetched by id.</li>
 * </ul>
 *
 * @param <T> cell type, must be comparable so range predicates can be evaluated
 */
public final class Column<T extends Comparable<T>> {
  private final String name;
  private final Class<T> type;
  private final Object[] runValues;
  private final int[] runEnds; // exclusive logical end offset of each run
  private final int size;

  public Column(String name, Class<T> type, T[] values) {
    this.name = Objects.requireNonNull(name, "name");
    this.type = Objects.requireNonNull(type, "type");
    Objects.requireNonNull(values, "values");

    List<Object> compressedValues = new ArrayList<>();
    List<Integer> compressedEnds = new ArrayList<>();
    for (int i = 0; i < values.length; i++) {
      if (compressedValues.isEmpty()
          || !Objects.equals(compressedValues.get(compressedValues.size() - 1), values[i])) {
        compressedValues.add(values[i]);
        compressedEnds.add(i + 1);
      } else {
        compressedEnds.set(compressedEnds.size() - 1, i + 1);
      }
    }
    this.runValues = compressedValues.toArray();
    this.runEnds = new int[compressedEnds.size()];
    for (int i = 0; i < this.runEnds.length; i++) {
      this.runEnds[i] = compressedEnds.get(i);
    }
    this.size = values.length;
  }

  public static Column<Integer> ofInts(String name, int[] data) {
    Integer[] boxed = new Integer[data.length];
    for (int i = 0; i < data.length; i++) {
      boxed[i] = data[i];
    }
    return new Column<>(name, Integer.class, boxed);
  }

  public static Column<Long> ofLongs(String name, long[] data) {
    Long[] boxed = new Long[data.length];
    for (int i = 0; i < data.length; i++) {
      boxed[i] = data[i];
    }
    return new Column<>(name, Long.class, boxed);
  }

  public static Column<Double> ofDoubles(String name, double[] data) {
    Double[] boxed = new Double[data.length];
    for (int i = 0; i < data.length; i++) {
      boxed[i] = data[i];
    }
    return new Column<>(name, Double.class, boxed);
  }

  public static Column<String> ofStrings(String name, String[] data) {
    return new Column<>(name, String.class, data);
  }

  public String name() {
    return name;
  }

  public Class<T> type() {
    return type;
  }

  public int size() {
    return size;
  }

  int runCount() {
    return runValues.length;
  }

  /**
   * Point lookup of one logical row by id. Runs are located with binary search
   * (O(log runs)), modelling the per-id decode cost of reading isolated cells from a
   * compressed column — much more expensive per cell than the sequential scan path.
   */
  @SuppressWarnings("unchecked")
  public T get(int row) {
    if (row < 0 || row >= size) {
      throw new IndexOutOfBoundsException("row " + row + " out of bounds for size " + size);
    }
    return (T) runValues[upperBound(runEnds, row)];
  }

  /**
   * Sequential scan over {@code [startRow, endRow)}: the starting run is located
   * once with binary search, then runs are walked linearly. The consumer receives
   * every logical row exactly once.
   */
  @SuppressWarnings("unchecked")
  public void forEach(int startRow, int endRow, BiConsumer<Integer, T> consumer) {
    if (startRow < 0 || endRow > size || startRow > endRow) {
      throw new IndexOutOfBoundsException(
          "range [" + startRow + ", " + endRow + ") out of bounds for size " + size);
    }
    int runIndex = upperBound(runEnds, startRow);
    int row = startRow;
    while (row < endRow) {
      int runEnd = Math.min(endRow, runEnds[runIndex]);
      T value = (T) runValues[runIndex];
      for (; row < runEnd; row++) {
        consumer.accept(row, value);
      }
      runIndex++;
    }
  }

  /** Index of the first run whose exclusive end offset is greater than {@code row}. */
  private static int upperBound(int[] ends, int row) {
    int low = 0;
    int high = ends.length;
    while (low < high) {
      int mid = (low + high) >>> 1;
      if (ends[mid] <= row) {
        low = mid + 1;
      } else {
        high = mid;
      }
    }
    return low;
  }

  @Override
  public String toString() {
    return "Column{name='" + name + "', rows=" + size + ", runs=" + runValues.length + '}';
  }
}
