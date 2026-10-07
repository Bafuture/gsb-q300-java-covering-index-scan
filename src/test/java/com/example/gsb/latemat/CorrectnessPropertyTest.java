package com.example.gsb.latemat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Requirement 4: on randomized data, late materialization must produce exactly the
 * same rows as reading all needed columns fully and then filtering.
 */
class CorrectnessPropertyTest {

  @Test
  void lateMaterializationMatchesFullReadOnRandomData() {
    for (int seed = 0; seed < 30; seed++) {
      Random rnd = new Random(seed);
      int rows = 500 + rnd.nextInt(4_500);
      int columns = 3 + rnd.nextInt(8);
      ColumnStore store = TestData.randomMixedStore(rnd, rows, columns);

      int predicateCount = 1 + rnd.nextInt(4);
      List<Predicate> predicates = new ArrayList<>();
      for (int i = 0; i < predicateCount; i++) {
        predicates.add(TestData.randomPredicate(rnd, store));
      }
      int[] outputColumns = TestData.randomOutputColumns(rnd, columns);

      QueryEngine engine = new QueryEngine(store);
      QueryResult late = engine.queryLate(predicates, outputColumns);
      QueryResult early = engine.queryEarly(predicates, outputColumns);

      assertThat(late)
          .as("seed=%d rows=%d cols=%d predicates=%s", seed, rows, columns, predicates)
          .isEqualTo(early);
      assertThat(late.rowIds()).isSorted();
    }
  }

  @Test
  void lateMaterializationMatchesBruteForceRowByRow() {
    Random rnd = new Random(42);
    ColumnStore store = TestData.randomMixedStore(rnd, 3_000, 6);
    List<Predicate> predicates = List.of(
        Predicate.lt(0, 50),
        Predicate.gt(1, 0.5),
        Predicate.ne(2, "alpha"));
    int[] outputColumns = {0, 1, 2, 3, 4, 5};

    QueryResult late = new QueryEngine(store).queryLate(predicates, outputColumns);

    // Independent brute-force reference computed directly from the columns.
    List<Integer> expectedIds = new ArrayList<>();
    for (int row = 0; row < store.rowCount(); row++) {
      boolean match = true;
      for (Predicate p : predicates) {
        if (!p.test(store.column(p.columnIndex()).get(row))) {
          match = false;
          break;
        }
      }
      if (match) {
        expectedIds.add(row);
      }
    }
    assertThat(late.size()).isEqualTo(expectedIds.size());
    for (int r = 0; r < late.size(); r++) {
      int rowId = expectedIds.get(r);
      assertThat(late.rowId(r)).isEqualTo(rowId);
      for (int c = 0; c < outputColumns.length; c++) {
        assertThat(late.value(r, c)).isEqualTo(store.column(outputColumns[c]).get(rowId));
      }
    }
  }
}
