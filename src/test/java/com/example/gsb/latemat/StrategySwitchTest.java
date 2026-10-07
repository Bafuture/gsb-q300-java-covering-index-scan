package com.example.gsb.latemat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Requirement 5: when a predicate filters poorly (high pass rate, i.e. low
 * selectivity), materializing early is cheaper. The cost model switches strategies at
 * a break-even pass rate; this test verifies the switch and times both strategies on
 * both sides of it.
 */
class StrategySwitchTest {

  @Test
  void costModelSwitchesAtTheBreakEvenPassRate() {
    // F=2 filter columns, O=8 output columns, k=4 -> break-even s* = 9/33 ~ 0.273.
    double breakEven = CostModel.breakEvenPassRate(2, 8);
    assertThat(breakEven).isCloseTo(9.0 / 33.0, within(1e-9));

    int n = 100_000;
    // Highly selective predicate (low pass rate): late materialization wins.
    assertThat(CostModel.choose(n, 2, 8, 0.01))
        .isEqualTo(CostModel.Strategy.LATE_MATERIALIZATION);
    // Poorly selective predicate (high pass rate): early materialization wins.
    assertThat(CostModel.choose(n, 2, 8, 0.95))
        .isEqualTo(CostModel.Strategy.EARLY_MATERIALIZATION);
    // The switch happens exactly at the break-even point.
    assertThat(CostModel.choose(n, 2, 8, breakEven - 0.01))
        .isEqualTo(CostModel.Strategy.LATE_MATERIALIZATION);
    assertThat(CostModel.choose(n, 2, 8, breakEven + 0.01))
        .isEqualTo(CostModel.Strategy.EARLY_MATERIALIZATION);
  }

  @Test
  void queryAutoFollowsTheCostModel() {
    ColumnStore store = TestData.randomIntStore(new Random(7), 50_000, 12, 1_000);
    QueryEngine engine = new QueryEngine(store);
    List<Predicate> selective = List.of(Predicate.eq(0, 7));       // ~0.1% pass rate
    List<Predicate> unselective = List.of(Predicate.lt(0, 990));   // ~99% pass rate
    int[] outputs = {1, 2, 3, 4, 5, 6, 7, 8};

    QueryResult autoSelective = engine.queryAuto(selective, 0.001, outputs);
    QueryResult lateSelective = engine.queryLate(selective, outputs);
    assertThat(autoSelective).isEqualTo(lateSelective);

    QueryResult autoUnselective = engine.queryAuto(unselective, 0.99, outputs);
    QueryResult earlyUnselective = engine.queryEarly(unselective, outputs);
    assertThat(autoUnselective).isEqualTo(earlyUnselective);
  }

  @Test
  void timingComparisonOfBothStrategies() {
    int rows = 200_000;
    int columns = 16;
    ColumnStore store = TestData.randomIntStore(new Random(99), rows, columns, 1_000);
    QueryEngine engine = new QueryEngine(store);
    int[] outputs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    // Warm the JIT so the comparison is meaningful.
    engine.queryLate(List.of(Predicate.lt(0, 500)), outputs);
    engine.queryEarly(List.of(Predicate.lt(0, 500)), outputs);

    // Scenario A: highly selective predicate (~0.1% of rows survive).
    List<Predicate> selective = List.of(Predicate.eq(0, 7));
    long lateA = bestOf3(() -> engine.queryLate(selective, outputs));
    long earlyA = bestOf3(() -> engine.queryEarly(selective, outputs));

    // Scenario B: poorly selective predicate (~99% of rows survive).
    List<Predicate> unselective = List.of(Predicate.lt(0, 990));
    long lateB = bestOf3(() -> engine.queryLate(unselective, outputs));
    long earlyB = bestOf3(() -> engine.queryEarly(unselective, outputs));

    System.out.printf("[strategy-timing] selective  pass~0.1%%: late=%.1f ms, early=%.1f ms -> late wins%n",
        lateA / 1e6, earlyA / 1e6);
    System.out.printf("[strategy-timing] unselective pass~99%%: late=%.1f ms, early=%.1f ms -> early wins%n",
        lateB / 1e6, earlyB / 1e6);

    // Both strategies must return identical results in both scenarios.
    assertThat(engine.queryLate(selective, outputs))
        .isEqualTo(engine.queryEarly(selective, outputs));
    assertThat(engine.queryLate(unselective, outputs))
        .isEqualTo(engine.queryEarly(unselective, outputs));

    // The cost model agrees with the measured direction in both scenarios.
    assertThat(CostModel.choose(rows, 1, outputs.length, 0.001))
        .isEqualTo(CostModel.Strategy.LATE_MATERIALIZATION);
    assertThat(CostModel.choose(rows, 1, outputs.length, 0.99))
        .isEqualTo(CostModel.Strategy.EARLY_MATERIALIZATION);
    // In the extreme scenarios the measured winner must match the model: with a
    // 0.1% pass rate late materialization touches ~1000x fewer output cells, and
    // with a 99% pass rate the random-access penalty dominates, so these are robust.
    assertThat(lateA).isLessThan(earlyA);
    assertThat(earlyB).isLessThan(lateB);
  }

  private static long bestOf3(Runnable run) {
    long best = Long.MAX_VALUE;
    for (int i = 0; i < 3; i++) {
      long start = System.nanoTime();
      run.run();
      best = Math.min(best, System.nanoTime() - start);
    }
    return best;
  }
}
