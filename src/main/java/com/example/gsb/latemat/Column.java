package com.example.gsb.latemat;

import java.util.concurrent.atomic.LongAdder;

/**
 * 列存中的一列。每次通过 {@link #get(int)} 读取单元格都会计入 {@link #cellReads()}，
 * 用于统计"读取了多少列数据 / 跳过了多少列数据"。计数器线程安全，支持并发扫描。
 */
public abstract class Column {

    private final String name;
    private final ColumnType type;
    private final LongAdder cellReads = new LongAdder();

    protected Column(String name, ColumnType type) {
        this.name = name;
        this.type = type;
    }

    public String name() {
        return name;
    }

    public ColumnType type() {
        return type;
    }

    public abstract int size();

    public abstract Object get(int rowId);

    protected final void countRead() {
        cellReads.increment();
    }

    public long cellReads() {
        return cellReads.sum();
    }

    public void resetStats() {
        cellReads.reset();
    }
}
