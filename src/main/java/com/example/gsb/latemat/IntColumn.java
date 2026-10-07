package com.example.gsb.latemat;

public final class IntColumn extends Column {

    private final int[] data;

    public IntColumn(String name, int[] data) {
        super(name, ColumnType.INT);
        this.data = data;
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public Integer get(int rowId) {
        countRead();
        return data[rowId];
    }
}
