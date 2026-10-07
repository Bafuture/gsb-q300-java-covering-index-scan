package com.example.gsb.latemat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Requirement 3: multiple column predicates shrink the candidate set stage by stage. */
class MultiColumnFilterTest {

  private static ColumnStore store(int rows) {
    List<Column<?>> cols = new ArrayList<>();
    int[] a = new int[rows];
    int[] b = new int[rows];
    int[] c = new int[rows];
    int[] payload = new int[rows];
    for (int i = 0; i < rows; i++) {
      a[i] = i % 10;
      b[i] = i % 4;
      c[i] = i % 2;
      payload[i] = i;
    }
    cols.add(Column.ofInts("a", a));
    cols.add(Column.ofInts("b", b));
    cols.add(Column.ofInts("c", c));
    cols.add(Column.ofInts("payload", payload));
    return new ColumnStore(cols);
  }

  @Test
  void candidateSetShrinksStageByStage() {
    ColumnStore store = store(10_000);
    List<Predicate> predicates = List.of(
        Predicate.lt(0, 8),   // a < 8  -> 8000 rows
        Predicate.lt(1, 2),   // b < 2  -> 4000 of those
        Predicate.eq(2, 1));  // c == 1 -> 2000 of those

    QueryResult result = new QueryEngine(store).queryLate(predicates, 3);

    assertThat(result.stats().candidateSizes()).containsExactly(8_000, 4_000, 2_000);
    assertThat(result.stats().finalCandidates()).isEqualTo(2_000);
    for (int r = 0; r < result.size(); r++) {
      int rowId = result.rowId(r);
      assertThat(rowId % 10).isLessThan(8);
      assertThat(rowId % 4).isLessThan(2);
      assertThat(rowId % 2).isEqualTo(1);
      assertThat(result.value(r, 0)).isEqualTo(rowId); // payload == row id
    }
  }

  @Test
  void laterFilterColumnsAreOnlyReadForSurvivingRows() {
    ColumnStore store = store(10_000);
    List<Predicate> predicates = List.of(
        Predicate.lt(0, 8),
        Predicate.lt(1, 2),
        Predicate.eq(2, 1));

    QueryResult result = new QueryEngine(store).queryLate(predicates, 3);

    // cells: 10000 (scan a) + 8000 (fetch b on survivors) + 4000 (fetch c) + 2000 (payload)
    assertThat(result.stats().cellsRead())
        .isEqualTo(10_000 + 8_000 + 4_000 + 2_000);
    assertThat(result.stats().columnReads()).isEqualTo(4);
  }

  @Test
  void multiColumnResultMatchesEagerBaseline() {
    ColumnStore store = store(10_000);
    List<Predicate> predicates = List.of(
        Predicate.between(0, 2, 7),
        Predicate.ne(1, 0),
        Predicate.eq(2, 0));
    QueryEngine engine = new QueryEngine(store);

    QueryResult late = engine.queryLate(predicates, 3);
    QueryResult early = engine.queryEarly(predicates, 3);

    assertThat(late).isEqualTo(early);
  }
}
