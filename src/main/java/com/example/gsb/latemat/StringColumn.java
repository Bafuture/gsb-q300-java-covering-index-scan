package com.example.gsb.latemat;

public final class StringColumn extends Column {

    private final String[] data;

    public StringColumn(String name, String[] data) {
        super(name, ColumnType.STRING);
        this.data = data;
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public String get(int rowId) {
        countRead();
        return data[rowId];
    }
}
