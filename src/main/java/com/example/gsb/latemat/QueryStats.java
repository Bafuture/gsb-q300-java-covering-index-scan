package com.example.gsb.latemat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单次查询执行的统计信息：候选集大小、读取/跳过的列与单元格数量、
 * 物化列数、使用的策略与耗时。支持跨线程合并（并发执行时每个分片各有一份）。
 */
public final class QueryStats {

    private final int rowCount;
    private final int totalColumns;
    private final double randomAccessPenalty;
    private final List<Long> candidateSetSizes = new ArrayList<>();
    private final Set<String> columnsRead = new LinkedHashSet<>();
    private final Map<Strategy, Integer> strategyUsage = new EnumMap<>(Strategy.class);
    private long sequentialCellReads;
    private long randomCellReads;
    private int materializedColumns;
    private long elapsedNanos;

    public QueryStats(int rowCount, int totalColumns, double randomAccessPenalty) {
        this.rowCount = rowCount;
        this.totalColumns = totalColumns;
        this.randomAccessPenalty = randomAccessPenalty;
    }

    synchronized void recordStage(int candidateCount) {
        candidateSetSizes.add((long) candidateCount);
    }

    synchronized void recordColumnRead(String column) {
        columnsRead.add(column);
    }

    synchronized void addSequentialCellReads(long cells) {
        sequentialCellReads += cells;
    }

    synchronized void addRandomCellReads(long cells) {
        randomCellReads += cells;
    }

    synchronized void recordStrategy(Strategy strategy) {
        strategyUsage.merge(strategy, 1, Integer::sum);
    }

    synchronized void setMaterializedColumns(int materializedColumns) {
        this.materializedColumns = materializedColumns;
    }

    synchronized void setElapsedNanos(long elapsedNanos) {
        this.elapsedNanos = elapsedNanos;
    }

    /** 合并其它分片的统计（并发执行时使用）。 */
    public synchronized void merge(QueryStats other) {
        for (int i = 0; i < other.candidateSetSizes.size(); i++) {
            if (i < candidateSetSizes.size()) {
                candidateSetSizes.set(i, candidateSetSizes.get(i) + other.candidateSetSizes.get(i));
            } else {
                candidateSetSizes.add(other.candidateSetSizes.get(i));
            }
        }
        columnsRead.addAll(other.columnsRead);
        other.strategyUsage.forEach((k, v) -> strategyUsage.merge(k, v, Integer::sum));
        sequentialCellReads += other.sequentialCellReads;
        randomCellReads += other.randomCellReads;
    }

    /** 每个筛选阶段结束后的候选集大小（多列筛选时逐步缩小）。 */
    public List<Long> candidateSetSizes() {
        return Collections.unmodifiableList(candidateSetSizes);
    }

    /** 最终候选集大小，即结果行数。 */
    public long finalCandidateCount() {
        return candidateSetSizes.isEmpty() ? rowCount : candidateSetSizes.get(candidateSetSizes.size() - 1);
    }

    /** 实际读取过的列名。 */
    public Set<String> columnsRead() {
        return Collections.unmodifiableSet(columnsRead);
    }

    /** 读取列数。 */
    public int readColumnCount() {
        return columnsRead.size();
    }

    /** 被跳过的列数（全表列数 - 读取列数）。 */
    public int skippedColumnCount() {
        return totalColumns - columnsRead.size();
    }

    /** 顺序读取的单元格数（整列/整段扫描）。 */
    public long sequentialCellReads() {
        return sequentialCellReads;
    }

    /** 随机读取的单元格数（按候选行号回列取值）。 */
    public long randomCellReads() {
        return randomCellReads;
    }

    public long cellsRead() {
        return sequentialCellReads + randomCellReads;
    }

    /** 被跳过的列读取量：相对"全表全列读取"基线省下的单元格读取数。 */
    public long skippedCellReads() {
        return Math.max(0L, (long) totalColumns * rowCount - cellsRead());
    }

    /** 物化的输出列数。 */
    public int materializedColumns() {
        return materializedColumns;
    }

    /** 代价模型估值：顺序读取代价为 1，随机读取代价为 randomAccessPenalty。 */
    public double estimatedCost() {
        return sequentialCellReads + randomAccessPenalty * randomCellReads;
    }

    /** 执行过程中使用过的策略（AUTO 模式下可能先 LATE 后 EARLY）。 */
    public Set<Strategy> strategiesUsed() {
        return Collections.unmodifiableSet(strategyUsage.keySet());
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    public double randomAccessPenalty() {
        return randomAccessPenalty;
    }

    @Override
    public String toString() {
        return "QueryStats{candidates=" + candidateSetSizes
                + ", columnsRead=" + columnsRead.size() + "/" + totalColumns
                + ", cellsRead=" + cellsRead()
                + ", skippedCells=" + skippedCellReads()
                + ", materializedColumns=" + materializedColumns
                + ", strategies=" + strategyUsage.keySet()
                + ", elapsedMs=" + elapsedNanos / 1_000_000.0 + '}';
    }
}
