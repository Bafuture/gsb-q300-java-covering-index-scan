package com.example.gsb.latemat;

public enum Op {
    EQ,
    NE,
    LT,
    LE,
    GT,
    GE;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean test(Object actual, Object expected) {
        int cmp = ((Comparable) actual).compareTo(expected);
        return switch (this) {
            case EQ -> cmp == 0;
            case NE -> cmp != 0;
            case LT -> cmp < 0;
            case LE -> cmp <= 0;
            case GT -> cmp > 0;
            case GE -> cmp >= 0;
        };
    }
}
