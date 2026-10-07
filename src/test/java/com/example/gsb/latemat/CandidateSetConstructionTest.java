package com.example.gsb.latemat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Requirement 1: the candidate set is built from filter columns only. */
class CandidateSetConstructionTest {

  /** col0 = i % 100, other columns are fillers that must never be read. */
  private static ColumnStore store(int rows, int fillerColumns) {
    List<Column<?>> columns = new ArrayList<>();
    int[] key = new int[rows];
    for (int i = 0; i < rows; i++) {
      key[i] = i % 100;
    }
    columns.add(Column.ofInts("key", key));
    for (int c = 0; c < fillerColumns; c++) {
      int[] filler = new int[rows];
      for (int i = 0; i < rows; i++) {
        filler[i] = i * (c + 1);
      }
      columns.add(Column.ofInts("filler" + c, filler));
    }
    return new ColumnStore(columns);
  }

  @Test
  void candidateSetContainsExactlyTheMatchingRowIds() {
    ColumnStore store = store(1_000, 4);
    ReadStats stats = new ReadStats();
    List<Integer> stageSizes = new ArrayList<>();

    int[] candidates = LateMaterializationEngine.buildCandidates(
        store, List.of(Predicate.lt(0, 10)), 0, store.rowCount(), stats, stageSizes);

    assertThat(candidates).hasSize(100); // rows 0..9, 100..109, ..., 900..909
    for (int id : candidates) {
      assertThat(id % 100).isLessThan(10);
    }
    assertThat(candidates).isSorted();
    assertThat(stageSizes).containsExactly(100);
  }

  @Test
  void candidateConstructionReadsOnlyTheFilterColumn() {
    ColumnStore store = store(1_000, 4);
    ReadStats stats = new ReadStats();

    LateMaterializationEngine.buildCandidates(
        store, List.of(Predicate.lt(0, 10)), 0, store.rowCount(), stats, new ArrayList<>());

    // Exactly one column access, exactly rowCount cells: the other 4 columns are untouched.
    assertThat(stats.columnReads()).isEqualTo(1);
    assertThat(stats.cellsRead()).isEqualTo(1_000);
    assertThat(stats.materializedColumnReads()).isZero();
  }

  @Test
  void queryWithNoOutputColumnsNeverTouchesNonFilterColumns() {
    ColumnStore store = store(1_000, 4);
    QueryResult result = new QueryEngine(store)
        .queryLate(List.of(Predicate.lt(0, 10)));

    QueryStats stats = result.stats();
    assertThat(result.size()).isEqualTo(100);
    assertThat(stats.cellsRead()).isEqualTo(1_000);
    assertThat(stats.materializedColumns()).isZero();
    // 4 filler columns x 1000 rows were skipped entirely.
    assertThat(stats.skippedCellReads()).isEqualTo(4_000);
  }

  @Test
  void candidateConstructionOnRowRangeOnlyScansThatRange() {
    ColumnStore store = store(1_000, 4);
    ReadStats stats = new ReadStats();

    int[] candidates = LateMaterializationEngine.buildCandidates(
        store, List.of(Predicate.lt(0, 10)), 200, 500, stats, new ArrayList<>());

    assertThat(candidates).hasSize(30); // 200..209, 300..309, 400..409
    assertThat(candidates[0]).isEqualTo(200);
    assertThat(candidates[candidates.length - 1]).isEqualTo(409);
    assertThat(stats.cellsRead()).isEqualTo(300);
  }

  @Test
  void noPredicatesMeansEveryRowIsACandidate() {
    ColumnStore store = store(500, 2);
    QueryResult result = new QueryEngine(store).queryLate(List.of(), 1);
    assertThat(result.size()).isEqualTo(500);
    assertThat(result.stats().cellsRead()).isEqualTo(500); // only the output column fetch
  }
}
