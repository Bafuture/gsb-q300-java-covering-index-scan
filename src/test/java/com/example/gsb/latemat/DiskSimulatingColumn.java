package com.example.gsb.latemat;

/**
 * 模拟磁盘/页式列存的列装饰器：顺序访问（行号连续）近乎免费，
 * 随机访问（行号跳跃）产生固定的寻址延迟。用于在耗时对比中
 * 真实体现"随机回列取值 vs 顺序整列扫描"的存储层代价差异。
 */
final class DiskSimulatingColumn extends Column {

    static final long DEFAULT_RANDOM_LATENCY_NANOS = 500;

    private final Column delegate;
    private final long randomLatencyNanos;
    private final ThreadLocal<Integer> lastRowId = ThreadLocal.withInitial(() -> -1);

    DiskSimulatingColumn(Column delegate) {
        this(delegate, DEFAULT_RANDOM_LATENCY_NANOS);
    }

    DiskSimulatingColumn(Column delegate, long randomLatencyNanos) {
        super(delegate.name(), delegate.type());
        this.delegate = delegate;
        this.randomLatencyNanos = randomLatencyNanos;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public Object get(int rowId) {
        if (lastRowId.get() != rowId - 1) {
            simulateSeek();
        }
        lastRowId.set(rowId);
        return delegate.get(rowId);
    }

    private void simulateSeek() {
        long deadline = System.nanoTime() + randomLatencyNanos;
        while (System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    static ColumnStoreTable wrap(ColumnStoreTable table) {
        ColumnStoreTable.Builder builder = ColumnStoreTable.builder();
        for (Column column : table.columns()) {
            builder.addColumn(new DiskSimulatingColumn(column));
        }
        return builder.build();
    }
}
