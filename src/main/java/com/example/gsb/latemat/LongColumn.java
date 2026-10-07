package com.example.gsb.latemat;

public final class LongColumn extends Column {

    private final long[] data;

    public LongColumn(String name, long[] data) {
        super(name, ColumnType.LONG);
        this.data = data;
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public Long get(int rowId) {
        countRead();
        return data[rowId];
    }
}
