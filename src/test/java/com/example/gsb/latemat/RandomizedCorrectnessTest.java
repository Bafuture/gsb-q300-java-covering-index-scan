package com.example.gsb.latemat;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** 要求 4：随机数据下，延迟物化 / 提前物化 / 并发执行结果与全量读取再筛选完全一致。 */
class RandomizedCorrectnessTest {

    @Test
    void allStrategiesMatchFullScanOnRandomData() {
        Random random = new Random(20261007L);
        for (int iteration = 0; iteration < 60; iteration++) {
            int rows = 1 + random.nextInt(2000);
            ColumnStoreTable table = TestData.randomTable(random, rows);
            Query query = TestData.randomQuery(random, table);

            LateMaterializationEngine engine = new LateMaterializationEngine(table);
            FullScanExecutor fullScan = new FullScanExecutor(table);

            QueryResult expected = fullScan.execute(query);
            QueryResult late = engine.execute(query, Strategy.LATE);
            QueryResult early = engine.execute(query, Strategy.EARLY);
            QueryResult auto = engine.execute(query, Strategy.AUTO);
            QueryResult parallel = engine.executeParallel(query, 4);

            TestData.assertResultsEqual(late, expected);
            TestData.assertResultsEqual(early, expected);
            TestData.assertResultsEqual(auto, expected);
            TestData.assertResultsEqual(parallel, expected);
        }
    }

    @Test
    void emptyAndFullMatchQueriesAreConsistent() {
        ColumnStoreTable table = TestData.deterministicTable();
        LateMaterializationEngine engine = new LateMaterializationEngine(table);
        FullScanExecutor fullScan = new FullScanExecutor(table);

        Query noMatch = Query.of(
                java.util.List.of(Predicate.on("a", Op.EQ, 999)), java.util.List.of("c", "d"));
        Query matchAll = Query.of(
                java.util.List.of(Predicate.on("a", Op.GE, 0)), java.util.List.of("c", "d"));
        Query noPredicate = Query.of(java.util.List.of(), java.util.List.of("a", "d"));

        for (Query query : java.util.List.of(noMatch, matchAll, noPredicate)) {
            QueryResult expected = fullScan.execute(query);
            TestData.assertResultsEqual(engine.execute(query, Strategy.LATE), expected);
            TestData.assertResultsEqual(engine.execute(query, Strategy.EARLY), expected);
            TestData.assertResultsEqual(engine.execute(query, Strategy.AUTO), expected);
            TestData.assertResultsEqual(engine.executeParallel(query, 3), expected);
        }
    }
}
