package com.example.gsb.latemat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Eager (early materialization) baseline: reads every needed column in full up front
 * and assembles row tuples, then filters the tuples and projects. This is the
 * "直接全列读取再筛选" strategy used as correctness reference and as the alternative
 * the cost model switches to when selectivity is poor.
 */
public final class EarlyMaterializationEngine {

  public QueryResult query(ColumnStore store, List<Predicate> predicates, int[] outputColumns) {
    ReadStats stats = new ReadStats();
    long start = System.nanoTime();
    int rowCount = store.rowCount();
    int filterCount = predicates.size();

    // 1) Eagerly read all needed columns (filter + output) in full and assemble the
    //    tuples up front — the defining cost of early materialization.
    Object[][] tuples = new Object[rowCount][filterCount + outputColumns.length];
    int slot = 0;
    for (Predicate predicate : predicates) {
      Column<?> column = store.column(predicate.columnIndex());
      stats.recordScan(rowCount);
      int columnSlot = slot++;
      column.forEach(0, rowCount, (row, value) -> tuples[row][columnSlot] = value);
    }
    for (int outputColumn : outputColumns) {
      Column<?> column = store.column(outputColumn);
      stats.recordScan(rowCount);
      int columnSlot = slot++;
      column.forEach(0, rowCount, (row, value) -> tuples[row][columnSlot] = value);
    }

    // 2) Filter the fully materialized tuples row by row.
    IntBuf ids = new IntBuf(1 << 12);
    for (int row = 0; row < rowCount; row++) {
      boolean match = true;
      for (int i = 0; i < filterCount; i++) {
        if (!predicates.get(i).test((Comparable<?>) tuples[row][i])) {
          match = false;
          break;
        }
      }
      if (match) {
        ids.add(row);
      }
    }
    int[] rowIds = ids.toArray();

    // 3) Project from the assembled tuples (no extra reads).
    Object[][] rows = new Object[rowIds.length][outputColumns.length];
    for (int c = 0; c < outputColumns.length; c++) {
      for (int r = 0; r < rowIds.length; r++) {
        rows[r][c] = tuples[rowIds[r]][filterCount + c];
      }
    }

    long elapsed = System.nanoTime() - start;
    Set<Integer> needed = new LinkedHashSet<>();
    for (Predicate p : predicates) {
      needed.add(p.columnIndex());
    }
    for (int c : outputColumns) {
      needed.add(c);
    }
    QueryStats snapshot = new QueryStats(List.of(rowIds.length), rowIds.length,
        stats.columnReads(), stats.cellsRead(), 0, 0,
        (long) rowCount * needed.size(), store.totalCells(), elapsed);
    return new QueryResult(rowIds, rows, outputColumns, snapshot);
  }
}
