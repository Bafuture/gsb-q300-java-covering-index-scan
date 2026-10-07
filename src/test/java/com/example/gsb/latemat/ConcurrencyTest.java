package com.example.gsb.latemat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Requirement 6: worker threads filter disjoint row ranges in parallel; the merged
 * result must be identical to the sequential one and keep the original row order.
 */
class ConcurrencyTest {

  private static final int ROWS = 50_000;

  private static ColumnStore store() {
    return TestData.randomIntStore(new Random(1234), ROWS, 8, 100);
  }

  private static List<Predicate> predicates() {
    return List.of(
        Predicate.lt(1, 40),
        Predicate.ge(3, 60),
        Predicate.between(5, 10, 30));
  }

  @Test
  void parallelResultIsIdenticalToSequentialForAnyThreadCount() {
    ColumnStore store = store();
    QueryEngine engine = new QueryEngine(store);
    int[] outputs = {0, 2, 7};

    QueryResult sequential = engine.queryLate(predicates(), outputs);
    for (int threads : new int[] {1, 2, 3, 4, 8, 16}) {
      QueryResult parallel = engine.queryParallel(threads, predicates(), outputs);
      assertThat(parallel)
          .as("threads=%d", threads)
          .isEqualTo(sequential);
    }
  }

  @Test
  void mergedResultKeepsAscendingRowOrder() {
    ColumnStore store = store();
    QueryResult result = new QueryEngine(store)
        .queryParallel(8, predicates(), 0, 2, 7);

    assertThat(result.rowIds()).isSorted();
    assertThat(result.size()).isGreaterThan(0);
  }

  @Test
  void parallelExecutionReadsTheSameCellsAsSequential() {
    ColumnStore store = store();
    QueryEngine engine = new QueryEngine(store);
    int[] outputs = {0, 2, 7};

    QueryResult sequential = engine.queryLate(predicates(), outputs);
    QueryResult parallel = engine.queryParallel(4, predicates(), outputs);

    // Each worker scans only its own row range, so the totals must match exactly.
    assertThat(parallel.stats().cellsRead()).isEqualTo(sequential.stats().cellsRead());
    assertThat(parallel.stats().candidateSizes())
        .isEqualTo(sequential.stats().candidateSizes());
    assertThat(parallel.stats().materializedCells())
        .isEqualTo(sequential.stats().materializedCells());
  }

  @Test
  void parallelWorksWhenRowCountIsSmallerThanThreadCount() {
    ColumnStore store = TestData.randomIntStore(new Random(5), 10, 4, 100);
    QueryEngine engine = new QueryEngine(store);

    QueryResult sequential = engine.queryLate(List.of(Predicate.lt(0, 50)), 1);
    QueryResult parallel = engine.queryParallel(8, List.of(Predicate.lt(0, 50)), 1);

    assertThat(parallel).isEqualTo(sequential);
    assertThat(parallel.rowIds()).isSorted();
  }
}
