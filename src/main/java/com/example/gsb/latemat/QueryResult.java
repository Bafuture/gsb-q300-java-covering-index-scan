package com.example.gsb.latemat;

import java.util.Arrays;

/**
 * Result rows produced by an engine, in ascending row-id order. {@code rows[i][j]} is
 * the value of output column {@code outputColumns[j]} for row {@code rowIds[i]}.
 */
public final class QueryResult {
  private final int[] rowIds;
  private final Object[][] rows;
  private final int[] outputColumns;
  private final QueryStats stats;

  public QueryResult(int[] rowIds, Object[][] rows, int[] outputColumns, QueryStats stats) {
    this.rowIds = rowIds;
    this.rows = rows;
    this.outputColumns = outputColumns;
    this.stats = stats;
  }

  public int size() {
    return rowIds.length;
  }

  public int[] rowIds() {
    return rowIds.clone();
  }

  public int rowId(int i) {
    return rowIds[i];
  }

  public Object[] row(int i) {
    return rows[i].clone();
  }

  public Object value(int i, int outputColumnPos) {
    return rows[i][outputColumnPos];
  }

  public int[] outputColumns() {
    return outputColumns.clone();
  }

  public QueryStats stats() {
    return stats;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof QueryResult other
        && Arrays.equals(rowIds, other.rowIds)
        && Arrays.deepEquals(rows, other.rows);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(rowIds) + Arrays.deepHashCode(rows);
  }
}
