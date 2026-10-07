package com.example.gsb.latemat;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 要求 2：筛选完成后才按行号物化输出列，并与全量读取做对比。 */
class LateMaterializationTest {

    @Test
    void materializesOnlyOutputColumnsAtCandidatePositions() {
        ColumnStoreTable table = TestData.deterministicTable();
        LateMaterializationEngine engine = new LateMaterializationEngine(table);

        Query query = Query.of(
                List.of(Predicate.on("a", Op.EQ, 3), Predicate.on("b", Op.EQ, 0)),
                List.of("c", "d"));
        QueryResult result = engine.execute(query, Strategy.LATE);

        assertThat(result.rowIds()).containsExactly(3, 33, 63, 93);
        assertThat(result.asLists()).containsExactly(
                List.of(30, "r3"),
                List.of(330, "r33"),
                List.of(630, "r63"),
                List.of(930, "r93"));

        assertThat(table.column("a").cellReads()).isEqualTo(100);
        assertThat(table.column("b").cellReads()).isEqualTo(10);
        assertThat(table.column("c").cellReads()).isEqualTo(4);
        assertThat(table.column("d").cellReads()).isEqualTo(4);
        assertThat(table.column("e").cellReads()).isZero();
        assertThat(table.column("f").cellReads()).isZero();

        QueryStats stats = engine.lastStats();
        assertThat(stats.materializedColumns()).isEqualTo(2);
        assertThat(stats.readColumnCount()).isEqualTo(4);
        assertThat(stats.skippedColumnCount()).isEqualTo(2);
        assertThat(stats.sequentialCellReads()).isEqualTo(100);
        assertThat(stats.randomCellReads()).isEqualTo(18);
        assertThat(stats.cellsRead()).isEqualTo(118);
        assertThat(stats.skippedCellReads()).isEqualTo(482);
    }

    @Test
    void lateMaterializationReadsFarLessThanFullScan() {
        ColumnStoreTable table = TestData.deterministicTable();
        LateMaterializationEngine late = new LateMaterializationEngine(table);
        FullScanExecutor fullScan = new FullScanExecutor(table);
        Query query = Query.of(
                List.of(Predicate.on("a", Op.EQ, 3), Predicate.on("b", Op.EQ, 0)),
                List.of("c", "d"));

        QueryResult lateResult = late.execute(query, Strategy.LATE);
        QueryResult fullResult = fullScan.execute(query);

        TestData.assertResultsEqual(lateResult, fullResult);

        QueryStats lateStats = late.lastStats();
        QueryStats fullStats = fullScan.lastStats();
        assertThat(lateStats.cellsRead()).isLessThan(fullStats.cellsRead());
        assertThat(lateStats.materializedColumns()).isEqualTo(2);
        assertThat(fullStats.materializedColumns()).isEqualTo(6);
        assertThat(fullStats.readColumnCount()).isEqualTo(6);
        assertThat(fullStats.cellsRead()).isEqualTo(600L);
        assertThat(fullStats.skippedColumnCount()).isZero();
        System.out.printf("[物化列数对比] 延迟物化=%d 列/%d 单元格 vs 全量读取=%d 列/%d 单元格，省 %.1f%%%n",
                lateStats.materializedColumns(), lateStats.cellsRead(),
                fullStats.materializedColumns(), fullStats.cellsRead(),
                100.0 * (1.0 - (double) lateStats.cellsRead() / fullStats.cellsRead()));
    }
}
