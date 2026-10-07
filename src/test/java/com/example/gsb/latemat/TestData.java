package com.example.gsb.latemat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** 测试数据构造工具：确定性小表、宽表与随机查询。 */
final class TestData {

    private TestData() {
    }

    /**
     * 100 行 6 列的确定性表：
     * a=i%10, b=i%3, c=i*10, d="r"+i, e=i*2, f=i%7。
     */
    static ColumnStoreTable deterministicTable() {
        int rows = 100;
        int[] a = new int[rows];
        int[] b = new int[rows];
        int[] c = new int[rows];
        String[] d = new String[rows];
        int[] e = new int[rows];
        int[] f = new int[rows];
        for (int i = 0; i < rows; i++) {
            a[i] = i % 10;
            b[i] = i % 3;
            c[i] = i * 10;
            d[i] = "r" + i;
            e[i] = i * 2;
            f[i] = i % 7;
        }
        return ColumnStoreTable.builder()
                .addColumn(new IntColumn("a", a))
                .addColumn(new IntColumn("b", b))
                .addColumn(new IntColumn("c", c))
                .addColumn(new StringColumn("d", d))
                .addColumn(new IntColumn("e", e))
                .addColumn(new IntColumn("f", f))
                .build();
    }

    /** cols 个 int 列（值 0..99）的宽表，用于策略切换与耗时对比。 */
    static ColumnStoreTable wideTable(int rows, int cols, Random random) {
        ColumnStoreTable.Builder builder = ColumnStoreTable.builder();
        for (int col = 0; col < cols; col++) {
            int[] data = new int[rows];
            for (int i = 0; i < rows; i++) {
                data[i] = random.nextInt(100);
            }
            builder.addColumn(new IntColumn("c" + col, data));
        }
        return builder.build();
    }

    /** 混合类型随机表：int/long/double/string 列交替。 */
    static ColumnStoreTable randomTable(Random random, int rows) {
        ColumnStoreTable.Builder builder = ColumnStoreTable.builder();
        int[] i1 = new int[rows];
        int[] i2 = new int[rows];
        long[] l1 = new long[rows];
        double[] d1 = new double[rows];
        double[] d2 = new double[rows];
        String[] s1 = new String[rows];
        String[] s2 = new String[rows];
        String[] dict = {"red", "green", "blue", "cyan", "yellow"};
        for (int i = 0; i < rows; i++) {
            i1[i] = random.nextInt(100);
            i2[i] = random.nextInt(7);
            l1[i] = random.nextInt(100);
            d1[i] = Math.round(random.nextDouble() * 1000.0) / 100.0;
            d2[i] = random.nextInt(10);
            s1[i] = dict[random.nextInt(dict.length)];
            s2[i] = dict[random.nextInt(3)];
        }
        return builder.addColumn(new IntColumn("i1", i1))
                .addColumn(new IntColumn("i2", i2))
                .addColumn(new LongColumn("l1", l1))
                .addColumn(new DoubleColumn("d1", d1))
                .addColumn(new DoubleColumn("d2", d2))
                .addColumn(new StringColumn("s1", s1))
                .addColumn(new StringColumn("s2", s2))
                .build();
    }

    /** 随机生成 1~3 个不同列条件 + 0~4 个输出列的查询。 */
    static Query randomQuery(Random random, ColumnStoreTable table) {
        List<String> names = new ArrayList<>(table.columnNames());
        Collections.shuffle(names, random);

        int predCount = 1 + random.nextInt(3);
        List<Predicate> predicates = new ArrayList<>();
        for (int i = 0; i < predCount; i++) {
            String name = names.get(i);
            Column column = table.column(name);
            boolean inDomain = random.nextInt(5) != 0;
            predicates.add(randomPredicate(random, name, column.type(), inDomain));
        }

        List<String> outputs = new ArrayList<>();
        int outCount = random.nextInt(5);
        Collections.shuffle(names, random);
        for (int i = 0; i < outCount; i++) {
            outputs.add(names.get(i));
        }
        return Query.of(predicates, outputs);
    }

    private static Predicate randomPredicate(
            Random random, String name, ColumnType type, boolean inDomain) {
        return switch (type) {
            case INT -> new Predicate(name, pickOp(random),
                    random.nextInt(inDomain ? 100 : 1000) - (inDomain ? 0 : 200));
            case LONG -> new Predicate(name, pickOp(random),
                    (long) random.nextInt(inDomain ? 100 : 1000));
            case DOUBLE -> new Predicate(name, pickOp(random),
                    Math.round(random.nextDouble() * (inDomain ? 10.0 : 1000.0) * 100.0) / 100.0);
            case STRING -> {
                String[] dict = {"red", "green", "blue", "cyan", "yellow", "black"};
                Op op = random.nextBoolean() ? Op.EQ : Op.NE;
                yield new Predicate(name, op, dict[random.nextInt(dict.length)]);
            }
        };
    }

    private static Op pickOp(Random random) {
        return switch (random.nextInt(6)) {
            case 0 -> Op.EQ;
            case 1 -> Op.NE;
            case 2 -> Op.LT;
            case 3 -> Op.LE;
            case 4 -> Op.GT;
            default -> Op.GE;
        };
    }

    static void assertResultsEqual(QueryResult actual, QueryResult expected) {
        org.assertj.core.api.Assertions.assertThat(actual.rowIds())
                .as("row ids")
                .isEqualTo(expected.rowIds());
        org.assertj.core.api.Assertions.assertThat(actual.asLists())
                .as("projected rows")
                .isEqualTo(expected.asLists());
        org.assertj.core.api.Assertions.assertThat(actual.columns())
                .isEqualTo(expected.columns());
    }
}
