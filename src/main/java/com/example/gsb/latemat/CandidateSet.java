package com.example.gsb.latemat;

import java.util.Arrays;

/**
 * 候选集：按行号升序排列的行号列表。构造候选集只需要读取筛选列，
 * 不需要触碰其它列；多列筛选时逐步缩小候选集。
 */
public final class CandidateSet {

    private int[] ids;
    private int size;

    public CandidateSet() {
        this(16);
    }

    public CandidateSet(int initialCapacity) {
        this.ids = new int[Math.max(1, initialCapacity)];
    }

    public void add(int rowId) {
        if (size == ids.length) {
            ids = Arrays.copyOf(ids, ids.length * 2);
        }
        ids[size++] = rowId;
    }

    public int get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
        return ids[index];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int[] toArray() {
        return Arrays.copyOf(ids, size);
    }

    /**
     * 用下一列条件过滤当前候选集：只读取该列在候选行号处的值，
     * 返回缩小后的新候选集（仍然按行号升序）。
     */
    public CandidateSet filter(Column column, Predicate predicate) {
        CandidateSet next = new CandidateSet(size);
        for (int i = 0; i < size; i++) {
            int rowId = ids[i];
            if (predicate.test(column.get(rowId))) {
                next.add(rowId);
            }
        }
        return next;
    }
}
