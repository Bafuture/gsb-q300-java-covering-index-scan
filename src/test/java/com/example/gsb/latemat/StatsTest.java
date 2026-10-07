package com.example.gsb.latemat;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 要求 7：候选集大小、物化列数、读取列数、被跳过的列读取量等统计。 */
class StatsTest {

    @Test
    void reportsAllLateMaterializationStats() {
        ColumnStoreTable table = TestData.deterministicTable();
        LateMaterializationEngine engine = new LateMaterializationEngine(table);
        Query query = Query.of(
                List.of(Predicate.on("a", Op.EQ, 3), Predicate.on("b", Op.EQ, 0)),
                List.of("c", "d"));

        engine.execute(query, Strategy.LATE);
        QueryStats stats = engine.lastStats();

        assertThat(stats.candidateSetSizes()).containsExactly(10L, 4L);
        assertThat(stats.finalCandidateCount()).isEqualTo(4);
        assertThat(stats.materializedColumns()).isEqualTo(2);
        assertThat(stats.readColumnCount()).isEqualTo(4);
        assertThat(stats.columnsRead()).containsExactly("a", "b", "c", "d");
        assertThat(stats.skippedColumnCount()).isEqualTo(2);
        assertThat(stats.sequentialCellReads()).isEqualTo(100);
        assertThat(stats.randomCellReads()).isEqualTo(18);
        assertThat(stats.cellsRead()).isEqualTo(118);
        assertThat(stats.skippedCellReads()).isEqualTo(482);
        assertThat(stats.elapsedNanos()).isPositive();
        assertThat(stats.toString()).contains("candidates=");
    }

    @Test
    void parallelMergedStatsMatchSequentialTotals() {
        ColumnStoreTable table = TestData.deterministicTable();
        LateMaterializationEngine sequential = new LateMaterializationEngine(table);
        LateMaterializationEngine parallel = new LateMaterializationEngine(table);
        Query query = Query.of(
                List.of(Predicate.on("a", Op.EQ, 3), Predicate.on("b", Op.EQ, 0)),
                List.of("c", "d"));

        sequential.execute(query, Strategy.LATE);
        parallel.executeParallel(query, 4);

        QueryStats seq = sequential.lastStats();
        QueryStats par = parallel.lastStats();
        assertThat(par.candidateSetSizes()).isEqualTo(seq.candidateSetSizes());
        assertThat(par.finalCandidateCount()).isEqualTo(seq.finalCandidateCount());
        assertThat(par.readColumnCount()).isEqualTo(seq.readColumnCount());
        assertThat(par.columnsRead()).isEqualTo(seq.columnsRead());
        assertThat(par.skippedColumnCount()).isEqualTo(seq.skippedColumnCount());
        assertThat(par.cellsRead()).isEqualTo(seq.cellsRead());
        assertThat(par.skippedCellReads()).isEqualTo(seq.skippedCellReads());
        assertThat(par.materializedColumns()).isEqualTo(seq.materializedColumns());
        assertThat(par.elapsedNanos()).isPositive();
    }

    @Test
    void earlyMaterializationStatsAreSequentialFullReads() {
        ColumnStoreTable table = TestData.deterministicTable();
        LateMaterializationEngine engine = new LateMaterializationEngine(table);
        Query query = Query.of(
                List.of(Predicate.on("a", Op.GE, 0)),
                List.of("c", "d"));

        engine.execute(query, Strategy.EARLY);
        QueryStats stats = engine.lastStats();

        assertThat(stats.strategiesUsed()).containsExactly(Strategy.EARLY);
        assertThat(stats.readColumnCount()).isEqualTo(3);
        assertThat(stats.columnsRead()).containsExactlyInAnyOrder("a", "c", "d");
        assertThat(stats.sequentialCellReads()).isEqualTo(300);
        assertThat(stats.randomCellReads()).isZero();
        assertThat(stats.skippedColumnCount()).isEqualTo(3);
        assertThat(stats.skippedCellReads()).isEqualTo(300);
        assertThat(stats.materializedColumns()).isEqualTo(2);
    }
}
