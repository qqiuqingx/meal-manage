# Phase 03 标签筛选与数据一致性

## 执行前规则检查

重新读取用户级、仓库及目标路径 AGENTS.md，规则变化先修订本阶段。读取 status.yaml、00-overview.md 及前置阶段交付记录。

## 目标

补齐标签筛选、导出与并发回归，确保新能力不改变既有分页和编辑语义。

## 前置依赖

Phase 02 完成，提供 API、菜单、表结构及事务契约和测试结果。

## 输入与输出

输入：标签闭环、现有配料查询和导出。
输出：tagId 筛选、标签导出列、一致性与回归验证记录。

## 本阶段实施约束

仅筛选一个标签，与其他条件 AND；不新增多标签交并集模式、客户禁忌解析或排餐逻辑。数据库测试不使用 Docker，不写凭据，不清表；无可用测试数据库时明确记录集成验证未完成。

## 涉及文件

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/domain/dto/DishIngredientQueryCriteria.java`。
- `eladmin/eladmin-system/src/main/resources/mapper/DishIngredientMapper.xml`。
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/service/impl/DishIngredientServiceImpl.java`。
- `eladmin-web/src/views/meal/dishIngredient/index.vue` 及相关前后端测试。
- 配料标签接口文档和现有菜品管理接口文档中的配料部分。

## 实施步骤

1. QueryCriteria 增加 tagId；共享查询使用 EXISTS 关联标签关系。分页/非分页保持同一条件，非法参数校验沿用项目规范。
2. 页面增加可清空标签筛选，与分类、名称、状态一起提交；筛选变动重置页码，下载复用当前条件。
3. 导出增加标签名称列，按稳定 ID 顺序连接标签名；非分页查询同样批量装配 tags，避免逐条查询。
4. 使用唯一测试标识验证分页不重复、总数正确、多标签配料只返回一次、无标签及不存在 tagId 为空结果、分类与标签组合筛选准确。
5. 在可用测试数据库验证事务失败不残留关系；两个并发配料更新得到完整的最后提交集合；绑定与删除同标签竞争不得产生悬空关系。检查所有调用路径遵循 overview 锁顺序。
6. 验证标签改名后各配料统一显示、被引用标签拒绝删除，原有配料新增/编辑/停用/删除和菜品配料读取行为未被误改。

## 验证方式

运行相关 Mapper/Service/前端筛选测试及文件 lint；真实 MySQL 测试限定本次创建 ID 并按关系→配料/标签顺序清理。记录实际测试环境版本、命令和结果；不重复跑 Phase 02 已覆盖且未变化的检查。

## 完成标准

筛选和导出一致，分页数量不膨胀，无悬空或重复标签关系；已执行与未执行的集成验证清楚区分。未验证的事务/并发行为作为发布阻断项记录。

## 状态

completed

## 阶段交付记录

### 实际交付记录（2026-09-27）

- `DishIngredientQueryCriteria` 增加 `tagId`；分页和非分页共享 Mapper 查询均使用 `EXISTS` 与分类、名称、状态条件按 AND 过滤，保持分页总数且避免标签关联重复行。
- 配料列表新增可清空标签远程搜索，筛选变化回到第一页；导出复用相同参数，并按标签ID顺序输出顿号连接的标签名称。
- 更新菜品管理接口文档，记录 `tagId` 和导出字段。前端 11 项相关 Jest 测试、后端 21 项相关测试、3 项真实 MySQL 集成测试、修改源码 ESLint、Mapper XML 解析和 `git diff --check` 均通过。
- 使用用户指定的 `eladmin/eladmin-system/.env.local` 测试环境，MySQL 8.0。成功执行标签 DDL/菜单 SQL 两次；第二次仍为 4 条权限菜单，无重复，表排序规则均为 `utf8mb4_unicode_ci`。配菜菜单父项ID为 120，新建标签权限菜单ID为 172（list）、173（add）、174（edit）、175（del）。未给任何角色授权。
- `DishIngredientTagMySqlIntegrationTest` 实际调用 Spring Service/Mapper，验证多标签和标签复用持久化、已绑定标签改名回显、大小写不敏感重名、`EXISTS` 筛选和分页总数、Excel“标签”列、null 保持/空数组清空、无效ID事务回滚、已引用标签删除保护、同配料并发整体替换、绑定与删除竞争、方法级权限拒绝/允许；3 项全部通过。测试清理仅按本次生成的配料/标签主键执行，结束后标签和关系表记录数均为 0。
- 清理 Phase 01 删除同义词模块后残留的单个生成资源 `target/classes/mapper/DishIngredientSynonymMapper.xml`，未执行全量 clean；旧 Surefire 历史报告保留。

下游：Phase 04 已完成业务/API 文档、幂等脚本检查和发布/回滚清单。生产数据库、生产部署和角色授权未执行。
