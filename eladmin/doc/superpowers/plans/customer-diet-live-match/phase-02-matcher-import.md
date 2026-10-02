# Phase 02 共享匹配与导入排除一致性

## 执行前规则检查

读取 status.yaml、00-overview.md 和本阶段文件；完整检查用户级及本阶段文件目录链 AGENTS.md。规则变化先更新本阶段和 overview。确认前置 Phase 已完成，读取其阶段交付记录和本阶段依赖产物。

## 目标

导入与原文重匹配复用同一核心，排除一致且预览准确；原导入交易、时间、餐数和原文规则不变。

## 前置依赖

Phase 01；依赖内部排除集合、对象身份和原子合并服务契约。

## 输入与输出

输入：已审阅设计、overview 公共契约，以及上述前置阶段交付物。
输出：本阶段范围内可独立验证的生产能力、相关测试及下一阶段依赖的稳定契约。

## 本阶段实施约束

Java 8 / fastjson2、现有分层与权限审计；保留 D 列只录原文和无关未提交工作。方法补充业务注释，不加测试专用生产逻辑，不新增依赖。禁止清表/无条件删除，不自动运行迁移、部署或提交。涉及客户写入沿用行锁与 overview 的锁顺序；字段和 API 变化仅限已审阅设计。

## 涉及文件与责任

- 修改 CustomerDietMatchService，提取接受原文及字典快照的可复用匹配核心，不依赖导入候选才能匹配客户原文。
- 修改 CustomerProfileImportServiceImpl、CustomerProfileImportWriter 和 CustomerDietMatchDto，预览与落库都应用排除规则。
- 调整 CustomerDietMatchServiceTest、CustomerProfileImportDietFlowTest、CustomerProfileImportDietOnlyTest 与类型处理器测试，保留原有未提交测试。
- 单轮只构建一次名称索引和 HanLP 分词器；Excel 行列来源由导入适配层附加，不丢失追踪能力。

## 实施步骤

1. 抽取现有精确匹配、分词及快照构建能力，保留表达前缀、同名多对象和完整原文规则。
2. 给已有客户候选载入排除集合，过滤 selectedItems；全部排除时为 EXCLUDED，部分排除按真实录入数量说明结果。
3. 完整导入与仅第二页写入在客户锁下再次过滤最新排除集合，重复导入不重建人工删除对象。
4. 不清除人工添加、历史停用/删除引用或原文，不新增确认参数。
5. 验证导入前后人工移除、部分候选排除、空字典/空原文以及 D 列不匹配。

## 验证方式

CustomerDietMatchServiceTest、CustomerOrderImportParserTest、CustomerProfileImportDietFlowTest、CustomerProfileImportDietOnlyTest 和共享禁忌服务测试。检查完全排除时不会访问不存在的 selectedItems[0]。

## 完成标准

导入与原文重匹配复用同一核心，排除一致且预览准确；原导入交易、时间、餐数和原文规则不变。

## 状态

complete

## 阶段交付记录

已完成 DictionarySnapshot 和独立原文匹配入口，导入继续补充真实 Excel 行列来源。预览过滤排除并新增 EXCLUDED/excludedItemCount，导入写入在客户锁下再次过滤。Java 8/Maven 的匹配、解析、完整饮食导入和仅第二页补录测试共 55 项通过；已有未提交测试保留。后续后台逐客户服务复用 matchRestrictions 和 mergeAutomatic，不使用虚假导入候选。
