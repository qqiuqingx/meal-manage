# Phase 03 字典变更自动更新闭环

## 执行前规则检查

读取 status.yaml、00-overview.md 和本阶段文件；完整检查用户级及本阶段文件目录链 AGENTS.md。规则变化先更新本阶段和 overview。确认前置 Phase 已完成，读取其阶段交付记录和本阶段依赖产物。

## 目标

实际字典变更后自动保存已有客户的新命中对象；人工删除始终有效；无新增基础设施、依赖或生产环境操作。

## 前置依赖

Phase 01、02；依赖客户锁下合并服务、游标读取契约及可复用匹配快照。

## 输入与输出

输入：已审阅设计、overview 公共契约，以及上述前置阶段交付物。
输出：本阶段范围内可独立验证的生产能力、相关测试及下一阶段依赖的稳定契约。

## 本阶段实施约束

Java 8 / fastjson2、现有分层与权限审计；保留 D 列只录原文和无关未提交工作。方法补充业务注释，不加测试专用生产逻辑，不新增依赖。禁止清表/无条件删除，不自动运行迁移、部署或提交。涉及客户写入沿用行锁与 overview 的锁顺序；字段和 API 变化仅限已审阅设计。

## 涉及文件与责任

- 在 meal/domain/event 新增统一 DietDictionaryChangedEvent，字典服务只发布字典变化，避免直接调用客户业务。
- 修改 DishServiceImpl、DishIngredientServiceImpl、DishTagServiceImpl、DishIngredientTagServiceImpl、DishIngredientCategoryServiceImpl，覆盖实际字典新增/改名/启停/删除入口；只更改绑定关系不触发。
- 在客户模块新增字典事件监听与 CustomerDietRematchServiceImpl，复用 taskAsync。客户模块依赖字典事件，字典服务不依赖客户存储服务。
- 扩展 CustomerProfileMapper 游标查询，批量读取有禁忌原文的客户；不使用 offset 全量排序分页、不一次加载所有客户。
- 自动操作人用明确后台标识，不读取异步线程登录态；单客户事务继续由独立服务承担。

## 实施步骤

1. 发布事务内事件并在成功提交后处理；回滚不启动任务。
2. 单轮运行并合并通知；运行中再收到变更必须在结束后消费最新快照，最终收尾不能丢请求。
3. 建一份字典与分词器快照，游标处理客户，锁后读当前原文、对象和排除集合；按稳定键去重追加，无变化不写。
4. 单客户失败记录主键并继续其他客户；记录批次摘要，不记录手机号或整段敏感原文。
5. 应用就绪请求一轮；重复执行、重复通知和多实例重复处理均幂等。
6. 测试字典事务成功/回滚、运行中变更、人工移除竞态、部分失败、重启恢复与无变化写零次。

## 验证方式

新增 CustomerDietRematchServiceImplTest、CustomerDietDictionaryChangeListenerTest；更新五类字典服务对应测试。用受控测试线程/任务队列验证合并与重入，测试辅助放在测试代码，不向生产注入测试开关。

## 完成标准

实际字典变更后自动保存已有客户的新命中对象；人工删除始终有效；无新增基础设施、依赖或生产环境操作。

## 状态

complete

## 阶段交付记录

五类字典写入口发布事务内事件，提交成功监听与应用就绪监听复用 taskAsync；后台合并通知、按主键游标读取，CustomerDietRestrictionWriter 在 REQUIRES_NEW 客户事务中读取最新原文/排除后保存。47 项字典、监听、任务和保存测试通过，包括真实 Spring 事务事件的提交/回滚语义。事务监听用法核对 Spring 5.3.31 官方 TransactionalEventListener 与 TransactionSynchronization 文档；未新增依赖。迁移未执行，后台自动写入须部署前先执行迁移。
