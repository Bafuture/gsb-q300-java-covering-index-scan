package com.example.gsb.latemat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Parallel late materialization: the row space is split into contiguous ranges, each
 * worker builds the candidate set and materializes the output columns for its own
 * range, and the per-range pieces are concatenated in range order. Because ranges are
 * disjoint and ascending, the merged result keeps the original row order.
 */
public final class ParallelLateMaterializationEngine {
  private final int threads;

  public ParallelLateMaterializationEngine(int threads) {
    if (threads < 1) {
      throw new IllegalArgumentException("threads must be >= 1");
    }
    this.threads = threads;
  }

  private record Partial(int[] rowIds, Object[][] rows, List<Integer> stageSizes) {}

  public QueryResult query(ColumnStore store, List<Predicate> predicates, int[] outputColumns) {
    ReadStats stats = new ReadStats();
    long start = System.nanoTime();
    int rowCount = store.rowCount();
    int parts = Math.max(1, Math.min(threads, rowCount));

    ExecutorService pool = Executors.newFixedThreadPool(parts);
    List<Future<Partial>> futures = new ArrayList<>(parts);
    try {
      for (int p = 0; p < parts; p++) {
        int rangeStart = (int) ((long) rowCount * p / parts);
        int rangeEnd = (int) ((long) rowCount * (p + 1) / parts);
        futures.add(pool.submit(() -> {
          List<Integer> stageSizes = new ArrayList<>();
          int[] ids = LateMaterializationEngine.buildCandidates(
              store, predicates, rangeStart, rangeEnd, stats, stageSizes);
          Object[][] rows = LateMaterializationEngine.materialize(store, ids, outputColumns, stats);
          return new Partial(ids, rows, stageSizes);
        }));
      }

      // Await in submission order, then merge range by range: ranges are disjoint and
      // ascending, so concatenation preserves the original row order.
      List<Partial> partials = new ArrayList<>(parts);
      for (Future<Partial> future : futures) {
        partials.add(getUnchecked(future));
      }
      return merge(store, predicates, outputColumns, stats, partials, start);
    } finally {
      pool.shutdown();
    }
  }

  private QueryResult merge(ColumnStore store, List<Predicate> predicates,
                            int[] outputColumns, ReadStats stats,
                            List<Partial> partials, long startNanos) {
    int total = 0;
    for (Partial partial : partials) {
      total += partial.rowIds().length;
    }
    int[] rowIds = new int[total];
    Object[][] rows = new Object[total][];
    int offset = 0;
    List<Integer> stageSizes = new ArrayList<>();
    for (Partial partial : partials) {
      System.arraycopy(partial.rowIds(), 0, rowIds, offset, partial.rowIds().length);
      System.arraycopy(partial.rows(), 0, rows, offset, partial.rowIds().length);
      offset += partial.rowIds().length;
      for (int stage = 0; stage < partial.stageSizes().size(); stage++) {
        if (stageSizes.size() == stage) {
          stageSizes.add(0);
        }
        stageSizes.set(stage, stageSizes.get(stage) + partial.stageSizes().get(stage));
      }
    }
    long elapsed = System.nanoTime() - startNanos;
    QueryStats snapshot = LateMaterializationEngine.snapshot(
        store, predicates, outputColumns, stageSizes, total, stats, elapsed);
    return new QueryResult(rowIds, rows, outputColumns, snapshot);
  }

  private static Partial getUnchecked(Future<Partial> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while merging parallel results", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("parallel worker failed", e.getCause());
    }
  }
}
