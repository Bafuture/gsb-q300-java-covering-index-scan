package com.example.gsb.latemat;

import java.util.List;

/**
 * 一个只包含合取筛选条件与输出列的查询。
 *
 * @param predicates    依次应用的多列组合筛选条件（AND 语义）
 * @param outputColumns 需要物化输出的列
 */
public record Query(List<Predicate> predicates, List<String> outputColumns) {

    public Query {
        predicates = List.copyOf(predicates);
        outputColumns = List.copyOf(outputColumns);
    }

    public static Query of(List<Predicate> predicates, List<String> outputColumns) {
        return new Query(predicates, outputColumns);
    }
}
