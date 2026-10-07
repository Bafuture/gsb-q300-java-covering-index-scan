package com.example.gsb.latemat;

import java.util.concurrent.atomic.LongAdder;

/**
 * Mutable, thread-safe counters recording which physical column accesses a query
 * actually performed. Engines call {@link #recordScan(long)} for a sequential read of
 * a column (whole column or a contiguous row range) and {@link #recordFetch(long)} for
 * point lookups of individual rows (used after filtering, including output-column
 * materialization).
 */
public final class ReadStats {
  private final LongAdder columnReads = new LongAdder();
  private final LongAdder cellsRead = new LongAdder();
  private final LongAdder materializedColumnReads = new LongAdder();
  private final LongAdder materializedCells = new LongAdder();

  /** One sequential column access reading {@code cells} cells. */
  public void recordScan(long cells) {
    columnReads.increment();
    cellsRead.add(cells);
  }

  /** One point-lookup column access reading {@code cells} individual rows. */
  public void recordFetch(long cells) {
    columnReads.increment();
    cellsRead.add(cells);
  }

  /** Marks one output column whose surviving rows were fetched after filtering. */
  public void recordMaterialization(long cells) {
    materializedColumnReads.increment();
    materializedCells.add(cells);
  }

  public long columnReads() {
    return columnReads.sum();
  }

  public long cellsRead() {
    return cellsRead.sum();
  }

  public long materializedColumnReads() {
    return materializedColumnReads.sum();
  }

  public long materializedCells() {
    return materializedCells.sum();
  }
}
