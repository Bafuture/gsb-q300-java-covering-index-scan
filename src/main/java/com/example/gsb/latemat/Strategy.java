package com.example.gsb.latemat;

/**
 * 物化策略。
 *
 * <ul>
 *   <li>{@link #LATE} 延迟物化：先在筛选列上构造并缩小候选集，最后才按行号物化输出列；</li>
 *   <li>{@link #EARLY} 提前物化：顺序读取剩余所需列并逐行筛选，避免大量随机访问；</li>
 *   <li>{@link #AUTO} 由代价模型在每个筛选阶段后自动决定是否切换为提前物化。</li>
 * </ul>
 */
public enum Strategy {
    LATE,
    EARLY,
    AUTO
}
