package com.example.gsb.latemat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按列存储的表：数据以列为单位组织，读取某一列不会触碰其它列，
 * 因此可以只用筛选列构造候选集（行号列表）而不读取无关列。
 */
public final class ColumnStoreTable {

    private final List<Column> columns;
    private final Map<String, Column> byName;
    private final int rowCount;

    private ColumnStoreTable(List<Column> columns) {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("table must have at least one column");
        }
        this.columns = List.copyOf(columns);
        this.byName = new LinkedHashMap<>();
        int rows = -1;
        for (Column column : columns) {
            if (byName.put(column.name(), column) != null) {
                throw new IllegalArgumentException("duplicate column: " + column.name());
            }
            if (rows < 0) {
                rows = column.size();
            } else if (column.size() != rows) {
                throw new IllegalArgumentException("column length mismatch: " + column.name());
            }
        }
        this.rowCount = rows;
    }

    public Column column(String name) {
        Column column = byName.get(name);
        if (column == null) {
            throw new IllegalArgumentException("unknown column: " + name);
        }
        return column;
    }

    public List<Column> columns() {
        return columns;
    }

    public List<String> columnNames() {
        return Collections.unmodifiableList(new ArrayList<>(byName.keySet()));
    }

    public int columnCount() {
        return columns.size();
    }

    public int rowCount() {
        return rowCount;
    }

    public long totalCellReads() {
        long total = 0;
        for (Column column : columns) {
            total += column.cellReads();
        }
        return total;
    }

    public void resetStats() {
        for (Column column : columns) {
            column.resetStats();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<Column> columns = new ArrayList<>();

        public Builder addColumn(Column column) {
            columns.add(column);
            return this;
        }

        public ColumnStoreTable build() {
            return new ColumnStoreTable(columns);
        }
    }
}
