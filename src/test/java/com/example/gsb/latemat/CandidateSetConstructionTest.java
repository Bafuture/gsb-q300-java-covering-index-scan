package com.example.gsb.latemat;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 要求 1：只用筛选列构造候选集，其它列完全不读。 */
class CandidateSetConstructionTest {

    @Test
    void buildsCandidateSetFromFilterColumnOnly() {
        ColumnStoreTable table = TestData.deterministicTable();
        LateMaterializationEngine engine = new LateMaterializationEngine(table);

        Query query = Query.of(List.of(Predicate.on("a", Op.EQ, 3)), List.of());
        QueryResult result = engine.execute(query, Strategy.LATE);

        assertThat(result.rowIds()).containsExactly(3, 13, 23, 33, 43, 53, 63, 73, 83, 93);
        assertThat(result.rowCount()).isEqualTo(10);
        assertThat(engine.lastStats().candidateSetSizes()).containsExactly(10L);

        assertThat(table.column("a").cellReads()).isEqualTo(100);
        for (String untouched : List.of("b", "c", "d", "e", "f")) {
            assertThat(table.column(untouched).cellReads())
                    .as("column %s must not be read while building candidates", untouched)
                    .isZero();
        }
    }

    @Test
    void emptyResultYieldsEmptyCandidateSet() {
        ColumnStoreTable table = TestData.deterministicTable();
        LateMaterializationEngine engine = new LateMaterializationEngine(table);

        Query query = Query.of(List.of(Predicate.on("a", Op.EQ, 42)), List.of());
        QueryResult result = engine.execute(query, Strategy.LATE);

        assertThat(result.rowIds()).isEmpty();
        assertThat(engine.lastStats().candidateSetSizes()).containsExactly(0L);
        assertThat(table.column("b").cellReads()).isZero();
    }
}
