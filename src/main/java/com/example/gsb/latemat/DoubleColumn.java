package com.example.gsb.latemat;

public final class DoubleColumn extends Column {

    private final double[] data;

    public DoubleColumn(String name, double[] data) {
        super(name, ColumnType.DOUBLE);
        this.data = data;
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public Double get(int rowId) {
        countRead();
        return data[rowId];
    }
}
