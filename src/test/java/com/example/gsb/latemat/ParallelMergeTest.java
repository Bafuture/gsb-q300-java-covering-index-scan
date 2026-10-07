package com.example.gsb.latemat;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 要求 6：多线程处理行号区间候选集，合并后保持原有行序。 */
class ParallelMergeTest {

    private static final int ROWS = 50_000;

    @Test
    void parallelResultsEqualSequentialAndStayInRowOrder() {
        ColumnStoreTable table = TestData.randomTable(new Random(7), ROWS);
        LateMaterializationEngine engine = new LateMaterializationEngine(table);
        FullScanExecutor fullScan = new FullScanExecutor(table);

        Query query = Query.of(
                List.of(Predicate.on("i2", Op.EQ, 3), Predicate.on("s2", Op.EQ, "red")),
                List.of("i1", "d1", "s1"));

        QueryResult expected = fullScan.execute(query);
        QueryResult sequential = engine.execute(query, Strategy.LATE);
        TestData.assertResultsEqual(sequential, expected);

        long baselineCells = engine.lastStats().cellsRead();
        List<Long> baselineStages = engine.lastStats().candidateSetSizes();
        for (int threads : List.of(1, 2, 3, 4, 8)) {
            QueryResult parallel = engine.executeParallel(query, threads);
            TestData.assertResultsEqual(parallel, expected);

            int[] ids = parallel.rowIds();
            for (int i = 1; i < ids.length; i++) {
                assertThat(ids[i]).as("row order at index %d (threads=%d)", i, threads)
                        .isGreaterThan(ids[i - 1]);
            }

            QueryStats stats = engine.lastStats();
            assertThat(stats.cellsRead())
                    .as("total cell reads must be independent of thread count (threads=%d)", threads)
                    .isEqualTo(baselineCells);
            assertThat(stats.candidateSetSizes())
                    .as("merged stage sizes (threads=%d)", threads)
                    .isEqualTo(baselineStages);
        }
    }

    @Test
    void matchesAcrossEveryChunkAreMergedInOrder() {
        ColumnStoreTable table = TestData.randomTable(new Random(9), ROWS);
        LateMaterializationEngine engine = new LateMaterializationEngine(table);
        FullScanExecutor fullScan = new FullScanExecutor(table);

        Query query = Query.of(
                List.of(Predicate.on("i2", Op.GE, 0)),
                List.of("i1", "d1", "s1"));

        QueryResult expected = fullScan.execute(query);
        for (int threads : List.of(2, 4, 8)) {
            TestData.assertResultsEqual(engine.executeParallel(query, threads), expected);
            int[] ids = engine.executeParallel(query, threads).rowIds();
            assertThat(ids[0]).isZero();
            assertThat(ids[ids.length - 1]).isEqualTo(ROWS - 1);
        }
    }

    @Test
    void moreThreadsThanRowsAndSingleRowTable() {
        ColumnStoreTable table = TestData.randomTable(new Random(11), 3);
        LateMaterializationEngine engine = new LateMaterializationEngine(table);
        FullScanExecutor fullScan = new FullScanExecutor(table);

        Query query = Query.of(
                List.of(Predicate.on("i2", Op.GE, 0)), List.of("i1", "s1"));

        TestData.assertResultsEqual(engine.executeParallel(query, 8), fullScan.execute(query));
        assertThat(engine.lastStats().cellsRead()).isPositive();

        ColumnStoreTable oneRow = TestData.randomTable(new Random(12), 1);
        LateMaterializationEngine oneRowEngine = new LateMaterializationEngine(oneRow);
        Query oneQuery = Query.of(List.of(Predicate.on("i1", Op.GE, 0)), List.of("d1"));
        TestData.assertResultsEqual(
                oneRowEngine.executeParallel(oneQuery, 4),
                new FullScanExecutor(oneRow).execute(oneQuery));
    }
}
