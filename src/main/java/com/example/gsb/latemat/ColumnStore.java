package com.example.gsb.latemat;

import java.util.List;
import java.util.Objects;

/**
 * An in-memory column store: a fixed set of equally-sized columns addressed by index.
 * The store itself never counts reads; engines report every column access to a
 * {@link ReadStats} so that "which columns / how many cells were actually read" stays
 * observable.
 */
public final class ColumnStore {
  private final List<Column<?>> columns;
  private final int rowCount;

  public ColumnStore(List<Column<?>> columns) {
    if (columns == null || columns.isEmpty()) {
      throw new IllegalArgumentException("a column store needs at least one column");
    }
    int n = columns.get(0).size();
    for (Column<?> c : columns) {
      if (c.size() != n) {
        throw new IllegalArgumentException(
            "column '" + c.name() + "' has " + c.size() + " rows, expected " + n);
      }
    }
    this.columns = List.copyOf(columns);
    this.rowCount = n;
  }

  public int rowCount() {
    return rowCount;
  }

  public int columnCount() {
    return columns.size();
  }

  public Column<?> column(int index) {
    return columns.get(index);
  }

  /** Total number of cells a naive "read every column of every row" scan would touch. */
  public long totalCells() {
    return (long) rowCount * columns.size();
  }

  @Override
  public String toString() {
    return "ColumnStore{rows=" + rowCount + ", columns=" + columns.size() + "}";
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ColumnStore other && Objects.equals(columns, other.columns);
  }

  @Override
  public int hashCode() {
    return Objects.hash(columns);
  }
}
