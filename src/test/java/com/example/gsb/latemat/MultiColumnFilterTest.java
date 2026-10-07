package com.example.gsb.latemat;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 要求 3：多列组合条件依次过滤，候选集逐步缩小。 */
class MultiColumnFilterTest {

    @Test
    void predicatesShrinkCandidateSetStageByStage() {
        ColumnStoreTable table = TestData.deterministicTable();
        LateMaterializationEngine engine = new LateMaterializationEngine(table);

        Query query = Query.of(
                List.of(
                        Predicate.on("a", Op.EQ, 3),
                        Predicate.on("b", Op.EQ, 0),
                        Predicate.on("f", Op.EQ, 0)),
                List.of("c", "d"));
        QueryResult result = engine.execute(query, Strategy.LATE);

        assertThat(engine.lastStats().candidateSetSizes()).containsExactly(10L, 4L, 1L);
        assertThat(result.rowIds()).containsExactly(63);
        assertThat(result.asLists()).containsExactly(List.of(630, "r63"));

        assertThat(table.column("a").cellReads()).isEqualTo(100);
        assertThat(table.column("b").cellReads()).isEqualTo(10);
        assertThat(table.column("f").cellReads()).isEqualTo(4);
        assertThat(table.column("c").cellReads()).isEqualTo(1);
        assertThat(table.column("d").cellReads()).isEqualTo(1);
        assertThat(table.column("e").cellReads()).isZero();
    }

    @Test
    void laterPredicateCanEmptyCandidateSetAndSkipsMaterialization() {
        ColumnStoreTable table = TestData.deterministicTable();
        LateMaterializationEngine engine = new LateMaterializationEngine(table);

        Query query = Query.of(
                List.of(Predicate.on("a", Op.EQ, 3), Predicate.on("e", Op.EQ, 5)),
                List.of("c", "d"));
        QueryResult result = engine.execute(query, Strategy.LATE);

        assertThat(engine.lastStats().candidateSetSizes()).containsExactly(10L, 0L);
        assertThat(result.rowIds()).isEmpty();
        assertThat(table.column("c").cellReads()).isZero();
        assertThat(table.column("d").cellReads()).isZero();
    }
}
