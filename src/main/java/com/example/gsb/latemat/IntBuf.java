package com.example.gsb.latemat;

/** Minimal growable buffer of primitive ints (avoids boxing for candidate row-id lists). */
final class IntBuf {
  private int[] data;
  private int size;

  IntBuf(int initialCapacity) {
    this.data = new int[Math.max(1, initialCapacity)];
  }

  void add(int value) {
    if (size == data.length) {
      int[] grown = new int[data.length + (data.length >> 1) + 1];
      System.arraycopy(data, 0, grown, 0, size);
      data = grown;
    }
    data[size++] = value;
  }

  void addAll(int[] values) {
    for (int value : values) {
      add(value);
    }
  }

  int size() {
    return size;
  }

  int[] toArray() {
    int[] copy = new int[size];
    System.arraycopy(data, 0, copy, 0, size);
    return copy;
  }
}
