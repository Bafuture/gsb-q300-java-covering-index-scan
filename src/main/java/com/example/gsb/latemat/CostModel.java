package com.example.gsb.latemat;

/**
 * Decides between late and early materialization for a query.
 *
 * <p>Terminology: {@code passRate} is the fraction of rows expected to survive all
 * predicates. A predicate with poor filtering power ("选择性低") has a high pass rate.
 *
 * <p>Cost model (in units of one sequential cell read):
 * <ul>
 *   <li>late  = N (first filter column scan)
 *             + (F-1) * s * N (later filter columns, point fetches on survivors)
 *             + k * O * s * N (output columns, point fetches on survivors)</li>
 *   <li>early = (F + O) * N (read every needed column in full, then filter)</li>
 * </ul>
 * where N = rows, F = filter columns, O = output columns, s = pass rate and
 * k = {@link #RANDOM_ACCESS_PENALTY}.
 *
 * <p>Switch condition: late materialization wins while
 * {@code s <= (F + O - 1) / ((F - 1) + k * O)}. Above that break-even pass rate the
 * survivor set is so large that re-fetching rows by id costs more than reading the
 * columns sequentially up front, so early materialization is cheaper.
 */
public final class CostModel {
  private CostModel() {}

  /**
   * A point lookup by row id costs this many sequential cell reads. In this store
   * columns are run-length encoded: a sequential scan walks runs linearly while a
   * point lookup must binary-search the run index per row, so isolated fetches are
   * genuinely more expensive per cell (the effect the strategy benchmark measures).
   */
  public static final double RANDOM_ACCESS_PENALTY = 4.0;

  public enum Strategy { LATE_MATERIALIZATION, EARLY_MATERIALIZATION }

  public static double lateCost(int rowCount, int filterColumns, int outputColumns,
                                double passRate) {
    double survivors = passRate * rowCount;
    return rowCount
        + Math.max(0, filterColumns - 1) * survivors
        + RANDOM_ACCESS_PENALTY * outputColumns * survivors;
  }

  public static double earlyCost(int rowCount, int filterColumns, int outputColumns) {
    return (double) rowCount * (filterColumns + outputColumns);
  }

  /** Pass rate at which the two strategies cost the same. */
  public static double breakEvenPassRate(int filterColumns, int outputColumns) {
    return (filterColumns + outputColumns - 1.0)
        / (Math.max(0, filterColumns - 1) + RANDOM_ACCESS_PENALTY * outputColumns);
  }

  public static Strategy choose(int rowCount, int filterColumns, int outputColumns,
                                double passRate) {
    return lateCost(rowCount, filterColumns, outputColumns, passRate)
            <= earlyCost(rowCount, filterColumns, outputColumns)
        ? Strategy.LATE_MATERIALIZATION
        : Strategy.EARLY_MATERIALIZATION;
  }
}
