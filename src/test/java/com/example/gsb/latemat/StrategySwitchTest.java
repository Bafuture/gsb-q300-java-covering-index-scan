package com.example.gsb.latemat;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 要求 5：选择性评估与策略切换。
 *
 * <p>切换条件：存活候选行占比 c/N &gt; 1/α（α 为随机访问惩罚，默认 4，即阈值 25%）。
 * 选择性过低（通过行太多）时，继续按行号随机回列取值的代价 α·c·k 超过
 * 顺序整列读取的代价 N·k，AUTO 策略切换为提前物化。
 */
class StrategySwitchTest {

    private static final int ROWS = 300_000;
    private static final int COLS = 24;

    @Test
    void lowSelectivityPredicateTriggersEarlyMaterialization() {
        ColumnStoreTable table = TestData.wideTable(ROWS, COLS, new Random(42));
        LateMaterializationEngine engine = new LateMaterializationEngine(table);

        Query query = Query.of(
                List.of(Predicate.on("c0", Op.LT, 60), Predicate.on("c1", Op.LT, 50)),
                List.of("c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10", "c11"));

        QueryResult auto = engine.execute(query, Strategy.AUTO);
        QueryStats autoStats = engine.lastStats();
        assertThat(autoStats.strategiesUsed()).contains(Strategy.EARLY);

        QueryResult late = engine.execute(query, Strategy.LATE);
        QueryStats lateStats = engine.lastStats();
        QueryResult early = engine.execute(query, Strategy.EARLY);
        QueryStats earlyStats = engine.lastStats();

        TestData.assertResultsEqual(auto, late);
        TestData.assertResultsEqual(auto, early);

        assertThat(lateStats.strategiesUsed()).containsExactly(Strategy.LATE);
        assertThat(earlyStats.strategiesUsed()).containsExactly(Strategy.EARLY);
        assertThat(earlyStats.estimatedCost()).isLessThan(lateStats.estimatedCost());
        System.out.printf("[低选择性] 通过率≈60%% → AUTO 切换为 EARLY；代价估计 LATE=%.0f vs EARLY=%.0f%n",
                lateStats.estimatedCost(), earlyStats.estimatedCost());
    }

    @Test
    void highSelectivityPredicateKeepsLateMaterialization() {
        ColumnStoreTable table = TestData.wideTable(ROWS, COLS, new Random(43));
        LateMaterializationEngine engine = new LateMaterializationEngine(table);

        Query query = Query.of(
                List.of(Predicate.on("c0", Op.EQ, 7), Predicate.on("c1", Op.LT, 50)),
                List.of("c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10", "c11"));

        QueryResult auto = engine.execute(query, Strategy.AUTO);
        QueryStats autoStats = engine.lastStats();
        assertThat(autoStats.strategiesUsed()).containsExactly(Strategy.LATE);

        QueryResult late = engine.execute(query, Strategy.LATE);
        QueryStats lateStats = engine.lastStats();
        QueryResult early = engine.execute(query, Strategy.EARLY);
        QueryStats earlyStats = engine.lastStats();

        TestData.assertResultsEqual(auto, late);
        TestData.assertResultsEqual(auto, early);
        assertThat(lateStats.estimatedCost()).isLessThan(earlyStats.estimatedCost());
        System.out.printf("[高选择性] 通过率≈1%% → AUTO 保持 LATE；代价估计 LATE=%.0f vs EARLY=%.0f%n",
                lateStats.estimatedCost(), earlyStats.estimatedCost());
    }

    @Test
    void switchConditionMatchesCostModelThreshold() {
        ColumnStoreTable table = TestData.deterministicTable();
        LateMaterializationEngine engine = new LateMaterializationEngine(table);

        assertThat(engine.shouldMaterializeEarly(26, 100)).isTrue();
        assertThat(engine.shouldMaterializeEarly(25, 100)).isFalse();
        assertThat(engine.shouldMaterializeEarly(0, 100)).isFalse();

        LateMaterializationEngine strict = new LateMaterializationEngine(table, 2.0);
        assertThat(strict.shouldMaterializeEarly(51, 100)).isTrue();
        assertThat(strict.shouldMaterializeEarly(50, 100)).isFalse();
    }

    @Test
    void timingComparisonBetweenStrategies() {
        // 用模拟磁盘随机访问延迟（500ns/次）的列存，复现真实列存中
        // "随机回列取值 vs 顺序整列扫描"的代价差异。
        ColumnStoreTable table =
                DiskSimulatingColumn.wrap(TestData.wideTable(ROWS, COLS, new Random(44)));
        LateMaterializationEngine engine = new LateMaterializationEngine(table);
        Query lowSelectivity = Query.of(
                List.of(Predicate.on("c0", Op.LT, 60), Predicate.on("c1", Op.LT, 50)),
                List.of("c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10", "c11"));
        Query highSelectivity = Query.of(
                List.of(Predicate.on("c0", Op.EQ, 7), Predicate.on("c1", Op.LT, 50)),
                List.of("c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10", "c11"));

        for (int warmup = 0; warmup < 2; warmup++) {
            engine.execute(lowSelectivity, Strategy.LATE);
            engine.execute(lowSelectivity, Strategy.EARLY);
            engine.execute(highSelectivity, Strategy.LATE);
            engine.execute(highSelectivity, Strategy.EARLY);
        }

        long lateLow = time(engine, lowSelectivity, Strategy.LATE);
        long earlyLow = time(engine, lowSelectivity, Strategy.EARLY);
        long lateHigh = time(engine, highSelectivity, Strategy.LATE);
        long earlyHigh = time(engine, highSelectivity, Strategy.EARLY);

        QueryResult lateResult = engine.execute(lowSelectivity, Strategy.LATE);
        QueryResult earlyResult = engine.execute(lowSelectivity, Strategy.EARLY);
        TestData.assertResultsEqual(lateResult, earlyResult);

        System.out.printf("[耗时对比] 低选择性(60%%通过): LATE=%.2fms vs EARLY=%.2fms → 提前物化更划算%n",
                lateLow / 1e6, earlyLow / 1e6);
        System.out.printf("[耗时对比] 高选择性(1%%通过):  LATE=%.2fms vs EARLY=%.2fms → 延迟物化更划算%n",
                lateHigh / 1e6, earlyHigh / 1e6);
    }

    private static long time(LateMaterializationEngine engine, Query query, Strategy strategy) {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            long start = System.nanoTime();
            engine.execute(query, strategy);
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }
}
