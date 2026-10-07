package com.example.gsb.latemat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Requirement 2: output columns are fetched by row id only after filtering. */
class LateMaterializationTest {

  private static ColumnStore store(int rows, int columns) {
    List<Column<?>> cols = new ArrayList<>();
    for (int c = 0; c < columns; c++) {
      int[] data = new int[rows];
      for (int i = 0; i < rows; i++) {
        data[i] = (i * 31 + c * 17) % 1_000;
      }
      cols.add(Column.ofInts("c" + c, data));
    }
    return new ColumnStore(cols);
  }

  @Test
  void outputColumnsAreMaterializedOnlyForSurvivingRows() {
    ColumnStore store = store(2_000, 6);
    QueryEngine engine = new QueryEngine(store);

    QueryResult result = engine.queryLate(List.of(Predicate.lt(0, 200)), 2, 4);

    // Values must be exactly the output columns' cells at the surviving row ids.
    for (int r = 0; r < result.size(); r++) {
      int rowId = result.rowId(r);
      assertThat(result.value(r, 0)).isEqualTo(store.column(2).get(rowId));
      assertThat(result.value(r, 1)).isEqualTo(store.column(4).get(rowId));
    }

    QueryStats stats = result.stats();
    int candidates = result.size();
    assertThat(candidates).isLessThan(2_000);
    // Materialization happened after filtering: 2 output columns x surviving rows only.
    assertThat(stats.materializedColumns()).isEqualTo(2);
    assertThat(stats.materializedCells()).isEqualTo(2L * candidates);
    // Total reads: full filter-column scan + 2 point-fetch materializations.
    assertThat(stats.cellsRead()).isEqualTo(2_000L + 2L * candidates);
  }

  @Test
  void materializedCellsAreFarBelowTheEagerFullRead() {
    ColumnStore store = store(2_000, 6);
    QueryEngine engine = new QueryEngine(store);
    List<Predicate> predicates = List.of(Predicate.lt(0, 200));

    QueryResult late = engine.queryLate(predicates, 2, 4);
    QueryResult early = engine.queryEarly(predicates, 2, 4);

    QueryStats stats = late.stats();
    // The eager baseline reads columns {0,2,4} in full: 3 x 2000 cells.
    assertThat(stats.baselineCells()).isEqualTo(6_000);
    assertThat(early.stats().cellsRead()).isEqualTo(6_000);
    // Late reads much less, and the saving is reported.
    assertThat(stats.cellsRead()).isLessThan(stats.baselineCells());
    assertThat(stats.savedCellsVsFullRead())
        .isEqualTo(stats.baselineCells() - stats.cellsRead());
    // Skipped vs reading the whole table: untouched columns {1,3,5} (3 x 2000) plus
    // the non-surviving rows of output columns {2,4} (2 x (2000 - candidates)).
    int candidates = late.size();
    assertThat(stats.skippedCellReads())
        .isEqualTo(3L * 2_000 + 2L * (2_000 - candidates))
        .isEqualTo(6L * 2_000 - stats.cellsRead());

    System.out.println("[late-vs-full] " + stats);
    System.out.println("[late-vs-full] eager baseline: " + early.stats());
  }

  @Test
  void emptyCandidateSetMaterializesNothing() {
    ColumnStore store = store(1_000, 4);
    QueryResult result = new QueryEngine(store)
        .queryLate(List.of(Predicate.lt(0, -1)), 1, 2);

    assertThat(result.size()).isZero();
    assertThat(result.stats().materializedColumns()).isEqualTo(2);
    assertThat(result.stats().materializedCells()).isZero();
    assertThat(result.stats().cellsRead()).isEqualTo(1_000); // only the filter scan
  }
}
