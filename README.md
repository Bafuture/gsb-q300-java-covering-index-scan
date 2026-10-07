# 列存延迟物化组件

在空 Maven 工程上从零实现的列存（column-store）延迟物化查询组件：先在筛选列上构造候选行号集，过滤完成后再按行号回取输出列，避免宽表场景下读取无关列。

## 运行方式

```bash
./mvnw -q verify   # 或 mvn -q verify
```

## 结构（`com.example.gsb.latemat`）

| 类 | 职责 |
|----|------|
| `Column` / `ColumnStore` | 列存表。列采用行程编码（RLE）：顺序扫描走 `forEach` 线性过 run，按行号点查走 `get` 二分定位 run，两者单次成本不同（真实列存的典型特征） |
| `Predicate` | 单列条件（EQ/NE/LT/LE/GT/GE/BETWEEN/IN），只作用于一个列 |
| `LateMaterializationEngine` | 延迟物化：阶段一只读筛选列构造候选集（首个谓词顺序扫，后续谓词只对幸存行点查），阶段二才按行号物化输出列 |
| `EarlyMaterializationEngine` | 提前物化基线：全量读取所有需要的列、组装行组后再过滤投影（"直接全列读取再筛选"） |
| `ParallelLateMaterializationEngine` | 把行区间切给多线程各自过滤+物化，按区间顺序拼接，结果保持原行序 |
| `CostModel` | 策略选择成本模型（见下） |
| `QueryEngine` | 门面：`queryLate` / `queryEarly` / `queryParallel` / `queryAuto` |
| `ReadStats` / `QueryStats` / `QueryResult` | 读放大统计、查询统计快照、结果集 |

## 关键设计

**候选集构造（需求 1）**：`buildCandidates` 只访问谓词涉及的列；首个谓词顺序扫描该列，之后每个谓词只对当前候选行做点查，候选集逐步缩小（需求 3）。

**延迟物化（需求 2）**：`materialize` 在过滤结束后才被调用，仅对幸存行号取输出列。`QueryStats` 直接给出对比：`materializedColumns`/`materializedCells`（实际物化量）vs `baselineCells`（全量读取等量成本）vs `skippedCellReads`（相对整表读取跳过的单元格数）。

**策略切换（需求 5）**：设 N=行数，F=筛选列数，O=输出列数，s=谓词通过率（通过率越高即"选择性越低"），k=点查相对顺序扫的惩罚系数（`RANDOM_ACCESS_PENALTY=4`）：

- 延迟物化成本 ≈ `N + (F-1)·s·N + k·O·s·N`
- 提前物化成本 ≈ `(F+O)·N`
- 切换条件：`s ≤ (F+O-1) / ((F-1) + k·O)` 时选延迟物化，否则提前物化更划算

`StrategySwitchTest` 在通过率 ~0.1% 与 ~99% 两个场景实测两种策略耗时并打印对比（典型结果：选择性高时 late 快 10 倍以上；选择性极低即通过率 ~99% 时 early 快约 2 倍），并断言与成本模型方向一致。

**并发（需求 6）**：行空间切分为连续区间，各线程独立执行过滤+物化，按区间顺序合并；区间不相交且递增，因此合并结果保持原行序，且总读取量与顺序执行完全一致（有测试断言）。

**统计（需求 7）**：`QueryStats` 提供候选集各阶段大小、物化列数、读取列数/单元格数、被跳过的列读取量与耗时。

## 测试（`src/test/java/...`，共 20 个用例）

- `CandidateSetConstructionTest` — 候选集构造只读筛选列
- `LateMaterializationTest` — 物化发生在过滤后、物化量与全量读取对比
- `MultiColumnFilterTest` — 多列组合筛选逐步缩小候选集
- `CorrectnessPropertyTest` — 30 组随机数据/随机谓词下与全量读取再筛选结果完全一致
- `StrategySwitchTest` — 成本模型切换条件 + 两种策略实测耗时对比
- `ConcurrencyTest` — 1~16 线程结果与顺序执行一致、行序保持、统计一致
