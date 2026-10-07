package com.example.gsb.latemat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Late materialization engine.
 *
 * <p>Stage 1 (candidate construction): only the filter columns are read. The first
 * predicate scans its column sequentially; every subsequent predicate point-fetches
 * only the rows still in the candidate set, so the candidate set shrinks stage by
 * stage. No output column is touched.
 *
 * <p>Stage 2 (materialization): once the final row-id list is known, each output
 * column is fetched only for those rows.
 */
public final class LateMaterializationEngine {

  public QueryResult query(ColumnStore store, List<Predicate> predicates, int[] outputColumns) {
    ReadStats stats = new ReadStats();
    long start = System.nanoTime();
    List<Integer> stageSizes = new ArrayList<>();
    int[] candidates = buildCandidates(store, predicates, 0, store.rowCount(), stats, stageSizes);
    Object[][] rows = materialize(store, candidates, outputColumns, stats);
    long elapsed = System.nanoTime() - start;
    QueryStats snapshot = snapshot(store, predicates, outputColumns, stageSizes,
        candidates.length, stats, elapsed);
    return new QueryResult(candidates, rows, outputColumns, snapshot);
  }

  /**
   * Builds the candidate row-id list for the row range {@code [startRow, endRow)}
   * touching only the filter columns. Package-private so the parallel engine can run
   * it per row range.
   */
  static int[] buildCandidates(ColumnStore store, List<Predicate> predicates,
                               int startRow, int endRow, ReadStats stats,
                               List<Integer> stageSizesOut) {
    int[] candidates = null; // null means "every row in [startRow, endRow)"
    for (Predicate predicate : predicates) {
      Column<?> column = store.column(predicate.columnIndex());
      IntBuf next;
      if (candidates == null) {
        // First filter column: cheap sequential scan of the row range.
        stats.recordScan(endRow - startRow);
        next = new IntBuf(Math.min(endRow - startRow, 1 << 12));
        IntBuf sink = next;
        column.forEach(startRow, endRow, (row, value) -> {
          if (predicate.test(value)) {
            sink.add(row);
          }
        });
      } else {
        // Later filter columns: point-fetch only the surviving rows.
        stats.recordFetch(candidates.length);
        next = new IntBuf(candidates.length);
        for (int rowId : candidates) {
          if (predicate.test(column.get(rowId))) {
            next.add(rowId);
          }
        }
      }
      candidates = next.toArray();
      stageSizesOut.add(candidates.length);
    }
    if (candidates == null) {
      // No predicates at all: every row in the range qualifies.
      candidates = new int[endRow - startRow];
      for (int i = 0; i < candidates.length; i++) {
        candidates[i] = startRow + i;
      }
    }
    return candidates;
  }

  /**
   * Materializes the output columns for the given (ascending) row ids. Called only
   * after filtering is complete — this is the "late" in late materialization.
   */
  static Object[][] materialize(ColumnStore store, int[] rowIds, int[] outputColumns,
                                ReadStats stats) {
    Object[][] rows = new Object[rowIds.length][outputColumns.length];
    for (int c = 0; c < outputColumns.length; c++) {
      Column<?> column = store.column(outputColumns[c]);
      stats.recordFetch(rowIds.length);
      stats.recordMaterialization(rowIds.length);
      for (int r = 0; r < rowIds.length; r++) {
        rows[r][c] = column.get(rowIds[r]);
      }
    }
    return rows;
  }

  static QueryStats snapshot(ColumnStore store, List<Predicate> predicates,
                             int[] outputColumns, List<Integer> stageSizes,
                             int finalCandidates, ReadStats stats, long elapsedNanos) {
    Set<Integer> needed = new LinkedHashSet<>();
    for (Predicate p : predicates) {
      needed.add(p.columnIndex());
    }
    for (int c : outputColumns) {
      needed.add(c);
    }
    long baselineCells = (long) store.rowCount() * needed.size();
    return new QueryStats(stageSizes, finalCandidates, stats.columnReads(),
        stats.cellsRead(), stats.materializedColumnReads(), stats.materializedCells(),
        baselineCells, store.totalCells(), elapsedNanos);
  }
}
