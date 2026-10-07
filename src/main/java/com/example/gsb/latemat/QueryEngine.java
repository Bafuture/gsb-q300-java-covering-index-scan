package com.example.gsb.latemat;

import java.util.List;

/**
 * Facade tying the pieces together: sequential late materialization, the eager
 * baseline, parallel execution, and cost-model-driven strategy selection.
 */
public final class QueryEngine {
  private final ColumnStore store;
  private final LateMaterializationEngine late = new LateMaterializationEngine();
  private final EarlyMaterializationEngine early = new EarlyMaterializationEngine();

  public QueryEngine(ColumnStore store) {
    this.store = store;
  }

  public ColumnStore store() {
    return store;
  }

  /** Late materialization: filter columns only, then fetch output columns by row id. */
  public QueryResult queryLate(List<Predicate> predicates, int... outputColumns) {
    return late.query(store, predicates, outputColumns);
  }

  /** Eager baseline: read all needed columns fully, then filter and project. */
  public QueryResult queryEarly(List<Predicate> predicates, int... outputColumns) {
    return early.query(store, predicates, outputColumns);
  }

  /** Parallel late materialization over row ranges; merged result keeps row order. */
  public QueryResult queryParallel(int threads, List<Predicate> predicates,
                                   int... outputColumns) {
    return new ParallelLateMaterializationEngine(threads).query(store, predicates, outputColumns);
  }

  /**
   * Picks a strategy with the {@link CostModel} using the estimated pass rate
   * (fraction of rows expected to survive all predicates) and executes it.
   */
  public QueryResult queryAuto(List<Predicate> predicates, double estimatedPassRate,
                               int... outputColumns) {
    int filterColumns = (int) predicates.stream().mapToInt(Predicate::columnIndex).distinct().count();
    CostModel.Strategy strategy = CostModel.choose(
        store.rowCount(), filterColumns, outputColumns.length, estimatedPassRate);
    return strategy == CostModel.Strategy.LATE_MATERIALIZATION
        ? queryLate(predicates, outputColumns)
        : queryEarly(predicates, outputColumns);
  }
}
