package com.example.gsb.latemat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基线执行器：全量读取——把所有列的所有行全部读出并物化成完整行，
 * 再在内存中筛选、投影。用于与延迟物化做正确性与读取量对比。
 */
public final class FullScanExecutor {

    private final ColumnStoreTable table;
    private volatile QueryStats lastStats;

    public FullScanExecutor(ColumnStoreTable table) {
        this.table = table;
    }

    public QueryStats lastStats() {
        return lastStats;
    }

    public QueryResult execute(Query query) {
        for (Predicate predicate : query.predicates()) {
            table.column(predicate.column());
        }
        for (String output : query.outputColumns()) {
            table.column(output);
        }
        QueryStats stats =
                new QueryStats(table.rowCount(), table.columnCount(),
                        LateMaterializationEngine.DEFAULT_RANDOM_ACCESS_PENALTY);
        long start = System.nanoTime();

        int rows = table.rowCount();
        Map<String, Object[]> buffers = new LinkedHashMap<>();
        for (Column column : table.columns()) {
            stats.recordColumnRead(column.name());
            Object[] buffer = new Object[rows];
            for (int rowId = 0; rowId < rows; rowId++) {
                buffer[rowId] = column.get(rowId);
            }
            stats.addSequentialCellReads(rows);
            buffers.put(column.name(), buffer);
        }

        List<String> outputs = query.outputColumns();
        List<Integer> outIds = new ArrayList<>();
        List<Object[]> result = new ArrayList<>();
        for (int rowId = 0; rowId < rows; rowId++) {
            boolean pass = true;
            for (Predicate predicate : query.predicates()) {
                if (!predicate.test(buffers.get(predicate.column())[rowId])) {
                    pass = false;
                    break;
                }
            }
            if (!pass) {
                continue;
            }
            Object[] row = new Object[outputs.size()];
            for (int c = 0; c < outputs.size(); c++) {
                row[c] = buffers.get(outputs.get(c))[rowId];
            }
            outIds.add(rowId);
            result.add(row);
        }
        stats.setElapsedNanos(System.nanoTime() - start);
        stats.setMaterializedColumns(table.columnCount());
        lastStats = stats;
        int[] rowIds = outIds.stream().mapToInt(Integer::intValue).toArray();
        return new QueryResult(outputs, rowIds, result);
    }
}
