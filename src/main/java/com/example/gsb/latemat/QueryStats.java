package com.example.gsb.latemat;

import java.util.List;

/**
 * Immutable statistics snapshot for one query execution.
 *
 * <ul>
 *   <li>{@code candidateSizes} — candidate row count after each filter-column stage;</li>
 *   <li>{@code columnReads} — number of column accesses actually issued;</li>
 *   <li>{@code cellsRead} — number of individual cells actually read;</li>
 *   <li>{@code materializedColumns} / {@code materializedCells} — output columns (and
 *       cells) fetched only for surviving rows after filtering;</li>
 *   <li>{@code baselineCells} — cells the eager "read all needed columns fully"
 *       baseline would read for the same query;</li>
 *   <li>{@code skippedCellReads} — cells avoided compared with reading the whole table.</li>
 * </ul>
 */
public final class QueryStats {
  private final List<Integer> candidateSizes;
  private final int finalCandidates;
  private final long columnReads;
  private final long cellsRead;
  private final long materializedColumns;
  private final long materializedCells;
  private final long baselineCells;
  private final long totalCellsInTable;
  private final long elapsedNanos;

  public QueryStats(List<Integer> candidateSizes, int finalCandidates, long columnReads,
                    long cellsRead, long materializedColumns, long materializedCells,
                    long baselineCells, long totalCellsInTable, long elapsedNanos) {
    this.candidateSizes = List.copyOf(candidateSizes);
    this.finalCandidates = finalCandidates;
    this.columnReads = columnReads;
    this.cellsRead = cellsRead;
    this.materializedColumns = materializedColumns;
    this.materializedCells = materializedCells;
    this.baselineCells = baselineCells;
    this.totalCellsInTable = totalCellsInTable;
    this.elapsedNanos = elapsedNanos;
  }

  public List<Integer> candidateSizes() {
    return candidateSizes;
  }

  public int finalCandidates() {
    return finalCandidates;
  }

  public long columnReads() {
    return columnReads;
  }

  public long cellsRead() {
    return cellsRead;
  }

  public long materializedColumns() {
    return materializedColumns;
  }

  public long materializedCells() {
    return materializedCells;
  }

  /** Cell reads an eager full read of all needed (filter + output) columns would cost. */
  public long baselineCells() {
    return baselineCells;
  }

  /** Cell reads avoided versus the eager baseline. */
  public long savedCellsVsFullRead() {
    return Math.max(0, baselineCells - cellsRead);
  }

  /** Cell reads avoided versus reading every column of the table. */
  public long skippedCellReads() {
    return Math.max(0, totalCellsInTable - cellsRead);
  }

  public long elapsedNanos() {
    return elapsedNanos;
  }

  public double elapsedMillis() {
    return elapsedNanos / 1_000_000.0;
  }

  @Override
  public String toString() {
    return "QueryStats{"
        + "candidateSizes=" + candidateSizes
        + ", finalCandidates=" + finalCandidates
        + ", columnReads=" + columnReads
        + ", cellsRead=" + cellsRead
        + ", materializedColumns=" + materializedColumns
        + ", materializedCells=" + materializedCells
        + ", baselineCells=" + baselineCells
        + ", savedVsFullRead=" + savedCellsVsFullRead()
        + ", skippedCellReads=" + skippedCellReads()
        + String.format(", elapsed=%.2f ms", elapsedMillis())
        + '}';
  }
}
