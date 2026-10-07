package com.example.gsb.latemat;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询结果：输出行按行号升序排列，{@link #rowIds()} 与 {@link #rows()} 一一对应。
 */
public final class QueryResult {

    private final List<String> columns;
    private final int[] rowIds;
    private final List<Object[]> rows;

    public QueryResult(List<String> columns, int[] rowIds, List<Object[]> rows) {
        if (rowIds.length != rows.size()) {
            throw new IllegalArgumentException("rowIds and rows size mismatch");
        }
        this.columns = List.copyOf(columns);
        this.rowIds = rowIds.clone();
        this.rows = new ArrayList<>(rows);
    }

    public List<String> columns() {
        return columns;
    }

    public int[] rowIds() {
        return rowIds.clone();
    }

    public List<Object[]> rows() {
        return rows;
    }

    public int rowCount() {
        return rowIds.length;
    }

    /** 便于断言比较的视图：每行转为 List。 */
    public List<List<Object>> asLists() {
        List<List<Object>> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            out.add(List.of(row));
        }
        return out;
    }
}
