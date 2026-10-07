package com.example.gsb.latemat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 列存延迟物化执行引擎。
 *
 * <p>执行流程：
 * <ol>
 *   <li>只读取筛选列，构造候选集（行号列表）；多列条件依次过滤，候选集逐步缩小；</li>
 *   <li>筛选完成后才按候选行号物化输出列（延迟物化）；</li>
 *   <li>AUTO 模式下，每个筛选阶段后根据代价模型判断：若存活行占比
 *       {@code candidates/rows > 1/randomAccessPenalty}（即选择性过低），
 *       继续按行号随机回列取值的代价已超过顺序整列读取，切换为提前物化。</li>
 * </ol>
 *
 * <p>代价模型：顺序读取一个单元格代价为 1，按行号随机读取代价为
 * {@code randomAccessPenalty}（默认 {@value #DEFAULT_RANDOM_ACCESS_PENALTY}）。
 * 对剩余 {@code k} 个待读列：延迟物化代价 ≈ α·c·k，提前物化代价 ≈ 1·N·k，
 * 故切换条件为 {@code c/N > 1/α}。
 */
public final class LateMaterializationEngine {

    public static final double DEFAULT_RANDOM_ACCESS_PENALTY = 4.0;

    private final ColumnStoreTable table;
    private final double randomAccessPenalty;
    private volatile QueryStats lastStats;

    public LateMaterializationEngine(ColumnStoreTable table) {
        this(table, DEFAULT_RANDOM_ACCESS_PENALTY);
    }

    public LateMaterializationEngine(ColumnStoreTable table, double randomAccessPenalty) {
        if (randomAccessPenalty <= 1.0) {
            throw new IllegalArgumentException("randomAccessPenalty must be > 1");
        }
        this.table = table;
        this.randomAccessPenalty = randomAccessPenalty;
    }

    public ColumnStoreTable table() {
        return table;
    }

    public double randomAccessPenalty() {
        return randomAccessPenalty;
    }

    /** 最近一次执行的统计信息。 */
    public QueryStats lastStats() {
        return lastStats;
    }

    /** AUTO 策略执行。 */
    public QueryResult execute(Query query) {
        return execute(query, Strategy.AUTO);
    }

    /** 指定策略执行（LATE / EARLY / AUTO）。 */
    public QueryResult execute(Query query, Strategy strategy) {
        validate(query);
        QueryStats stats = new QueryStats(table.rowCount(), table.columnCount(), randomAccessPenalty);
        long start = System.nanoTime();
        QueryResult result = executeRange(query, 0, table.rowCount(), strategy, stats);
        stats.setElapsedNanos(System.nanoTime() - start);
        stats.setMaterializedColumns(distinctOutputCount(query));
        lastStats = stats;
        return result;
    }

    /**
     * 并发执行：把行号区间 [0, N) 切成 {@code threads} 段，每段独立构造候选集并物化，
     * 最后按区间顺序合并结果，因此输出行保持原有行序（行号升序）。
     */
    public QueryResult executeParallel(Query query, int threads) {
        validate(query);
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be >= 1");
        }
        int rows = table.rowCount();
        int chunks = Math.max(1, Math.min(threads, rows));
        int chunkSize = (rows + chunks - 1) / chunks;

        List<Callable<ChunkResult>> tasks = new ArrayList<>();
        for (int start = 0; start < rows; start += chunkSize) {
            int from = start;
            int to = Math.min(rows, start + chunkSize);
            tasks.add(() -> {
                QueryStats chunkStats =
                        new QueryStats(to - from, table.columnCount(), randomAccessPenalty);
                QueryResult chunk = executeRange(query, from, to, Strategy.AUTO, chunkStats);
                return new ChunkResult(chunk, chunkStats);
            });
        }

        QueryStats stats = new QueryStats(rows, table.columnCount(), randomAccessPenalty);
        long start = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(chunks);
        try {
            List<Future<ChunkResult>> futures = pool.invokeAll(tasks);
            List<String> columns = query.outputColumns();
            int total = 0;
            List<ChunkResult> parts = new ArrayList<>(futures.size());
            for (Future<ChunkResult> future : futures) {
                ChunkResult part = future.get();
                parts.add(part);
                total += part.result().rowCount();
            }
            int[] rowIds = new int[total];
            List<Object[]> rowsOut = new ArrayList<>(total);
            int offset = 0;
            for (ChunkResult part : parts) {
                int[] ids = part.result().rowIds();
                System.arraycopy(ids, 0, rowIds, offset, ids.length);
                offset += ids.length;
                rowsOut.addAll(part.result().rows());
                stats.merge(part.stats());
            }
            stats.setElapsedNanos(System.nanoTime() - start);
            stats.setMaterializedColumns(distinctOutputCount(query));
            lastStats = stats;
            return new QueryResult(columns, rowIds, rowsOut);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("parallel execution interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("parallel execution failed", e.getCause());
        } finally {
            pool.shutdown();
        }
    }

    /**
     * 切换条件：存活候选行占比超过 1/α（选择性过低）时，提前物化更划算。
     * 延迟物化剩余代价 α·c·k 与提前物化代价 N·k 相等时 c/N = 1/α。
     */
    public boolean shouldMaterializeEarly(int candidateCount, int rangeSize) {
        return candidateCount * randomAccessPenalty > rangeSize;
    }

    private QueryResult executeRange(
            Query query, int start, int end, Strategy strategy, QueryStats stats) {
        List<Predicate> predicates = query.predicates();
        CandidateSet candidates = null;
        int applied = 0;
        boolean early = strategy == Strategy.EARLY
                || (strategy == Strategy.AUTO && predicates.isEmpty());

        while (!early && applied < predicates.size()) {
            if (candidates != null && strategy == Strategy.AUTO
                    && shouldMaterializeEarly(candidates.size(), end - start)) {
                early = true;
                break;
            }
            Predicate predicate = predicates.get(applied);
            Column column = table.column(predicate.column());
            stats.recordColumnRead(column.name());
            if (candidates == null) {
                candidates = new CandidateSet();
                for (int rowId = start; rowId < end; rowId++) {
                    if (predicate.test(column.get(rowId))) {
                        candidates.add(rowId);
                    }
                }
                stats.addSequentialCellReads(end - start);
            } else {
                int before = candidates.size();
                candidates = candidates.filter(column, predicate);
                stats.addRandomCellReads(before);
            }
            stats.recordStage(candidates.size());
            applied++;
        }

        if (early) {
            stats.recordStrategy(Strategy.EARLY);
            return materializeEarly(query, predicates, applied, candidates, start, end, stats);
        }
        stats.recordStrategy(Strategy.LATE);
        return materializeLate(query, candidates, start, end, stats);
    }

    /** 延迟物化：筛选完成后，按最终候选行号物化输出列。 */
    private QueryResult materializeLate(
            Query query, CandidateSet candidates, int start, int end, QueryStats stats) {
        int[] positions = candidates == null ? allPositions(start, end) : candidates.toArray();
        List<String> outputs = query.outputColumns();
        List<Object[]> rows = new ArrayList<>(positions.length);
        Object[][] buffers = new Object[outputs.size()][];
        for (int c = 0; c < outputs.size(); c++) {
            Column column = table.column(outputs.get(c));
            stats.recordColumnRead(column.name());
            Object[] buffer = new Object[positions.length];
            for (int i = 0; i < positions.length; i++) {
                buffer[i] = column.get(positions[i]);
            }
            stats.addRandomCellReads(positions.length);
            buffers[c] = buffer;
        }
        for (int i = 0; i < positions.length; i++) {
            Object[] row = new Object[outputs.size()];
            for (int c = 0; c < outputs.size(); c++) {
                row[c] = buffers[c][i];
            }
            rows.add(row);
        }
        return new QueryResult(outputs, positions, rows);
    }

    /**
     * 提前物化：顺序读取剩余所需列（未应用的筛选列 + 输出列），
     * 在候选位图上逐行完成剩余筛选并直接产出结果行。
     */
    private QueryResult materializeEarly(
            Query query, List<Predicate> predicates, int applied,
            CandidateSet candidates, int start, int end, QueryStats stats) {
        int length = end - start;
        Set<String> needed = new LinkedHashSet<>();
        for (int i = applied; i < predicates.size(); i++) {
            needed.add(predicates.get(i).column());
        }
        needed.addAll(query.outputColumns());

        java.util.Map<String, Object[]> buffers = new java.util.HashMap<>();
        for (String name : needed) {
            Column column = table.column(name);
            stats.recordColumnRead(name);
            Object[] buffer = new Object[length];
            for (int i = 0; i < length; i++) {
                buffer[i] = column.get(start + i);
            }
            stats.addSequentialCellReads(length);
            buffers.put(name, buffer);
        }

        boolean[] eligible = null;
        if (candidates != null) {
            eligible = new boolean[length];
            for (int i = 0; i < candidates.size(); i++) {
                eligible[candidates.get(i) - start] = true;
            }
        }

        List<String> outputs = query.outputColumns();
        List<Integer> outIds = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            if (eligible != null && !eligible[i]) {
                continue;
            }
            boolean pass = true;
            for (int p = applied; p < predicates.size(); p++) {
                Predicate predicate = predicates.get(p);
                if (!predicate.test(buffers.get(predicate.column())[i])) {
                    pass = false;
                    break;
                }
            }
            if (!pass) {
                continue;
            }
            Object[] row = new Object[outputs.size()];
            for (int c = 0; c < outputs.size(); c++) {
                row[c] = buffers.get(outputs.get(c))[i];
            }
            outIds.add(start + i);
            rows.add(row);
        }
        int[] rowIds = outIds.stream().mapToInt(Integer::intValue).toArray();
        return new QueryResult(outputs, rowIds, rows);
    }

    private static int[] allPositions(int start, int end) {
        int[] positions = new int[end - start];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = start + i;
        }
        return positions;
    }

    private static int distinctOutputCount(Query query) {
        return (int) query.outputColumns().stream().distinct().count();
    }

    private void validate(Query query) {
        for (Predicate predicate : query.predicates()) {
            table.column(predicate.column());
        }
        for (String output : query.outputColumns()) {
            table.column(output);
        }
    }

    private record ChunkResult(QueryResult result, QueryStats stats) {
    }
}
