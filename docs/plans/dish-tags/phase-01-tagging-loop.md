# Phase 01 菜品标签选择与保存最小闭环

## 执行前规则检查

重新读取当前用户级、仓库与目标目录生效的 AGENTS.md；加载 status.yaml、00-overview.md 和本阶段前置交付记录。规则变化先同步计划，不加载无关阶段全文。

## 目标

新增两张表、查询标签与菜品绑定能力，并通过现有菜品弹窗选择和保存已有标签。

## 前置依赖

无；输入为 overview 契约、现有配料标签实现及菜品读写路径。

## 输入与输出

输入：overview 中已明确的数据、接口和交互契约，以及上述前置产物。
输出：下列文件及可验证行为、验证记录和后续阶段所需契约。

## 本阶段实施约束

不增加独立页面，不使用配料标签表；保持旧请求不传 tagIds 的行为。按 overview 权限与事务约定实施，不扩大业务范围。新增方法/字段有注释，新增实体不使用 @Data。无新增依赖、测试专用生产入口或无条件清表；保留用户已有修改。涉及契约变化先记录调用方影响，超出确认范围时先更新方案。

## 涉及文件

以下 Java 简写路径均相对 eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/；测试放在对应 src/test/java 路径。

新增：
- eladmin/sql/20260928_dish_tag.sql（两张表，按钮权限在 Phase 02 补齐）。
- eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/ 下 domain/DishTag.java、DishTagRelation.java；domain/dto/DishTagDto.java、DishTagRelationDto.java、DishTagQueryCriteria.java；mapper/DishTagMapper.java、DishTagRelationMapper.java；service/DishTagService.java、service/impl/DishTagServiceImpl.java；rest/DishTagController.java。
- eladmin/eladmin-system/src/main/resources/mapper/DishTagMapper.xml、DishTagRelationMapper.xml。
- eladmin-web/src/api/dishTag.js、eladmin-web/src/views/meal/dish/components/DishTagEditor.vue。
修改：domain/Dish.java、service/impl/DishServiceImpl.java、必要时 service/DishService.java 与 rest/DishController.java；eladmin-web/src/views/meal/dish/dish.vue。
新增相关 Service/Controller 与 DishTagEditor/DishForm 测试，路径沿用现有测试目录。

## 实施步骤

1. 核查 DishService 的 create/update/getById/queryAll/deleteAll 和调用方；锁定详情、分页读写范围，确认原接口状态码及配料关联保持语义。
2. 编写幂等 DDL，新增实体/DTO/Mapper；标签字典查询与关联查询分别职责明确，批量查询对空ID集合直接返回。
3. 给 Dish 增加 exist=false 的 tagIds、tags；写入仅认 tagIds，不信任客户端 tags 的名称。请求 tagIds 缺省不能初始化成空列表，以免旧编辑请求误清空。
4. 实现标签存在性校验、去重、锁定及关联替换，嵌入菜品创建/更新事务；同菜品更新和删除先锁菜品行，避免并发替换合并出错误集合或产生孤立关系。按菜品限定清理关系。
5. 维护详情读取与分页查询回填两个字段；列表单次批量读取。注意详情现有 getById 也被内部调用，不引入循环或递归。
6. 子组件通过 value/input 管理所选ID，接收详情 tags 回显名称；实现搜索分页及已选缓存；表单新增/编辑/关闭重置 tagIds，提交沿用既有保存按钮。无字典记录时为空态，既有菜品能力始终可用。
7. 测试使用仅属于本次的字典数据验证闭环，不向部署 SQL 写示例业务标签。

## 验证方式

- 后端编译受影响模块及必要依赖，运行新增/编辑/详情/列表/删除相关测试；执行前读取 maven 技能和本地开发指南确定命令。
- 验证 null/缺省保留、[]清空、去重、无效ID回滚、删除菜品留字典、两菜品独立关联、批量查询；保留原配料和套餐等字段。
- 对修改 Vue/JS 定向 lint、组件测试；从矩阵和主档列表两个入口确认同一弹窗可回显、保存、取消。

## 完成标准

阶段目标与上述验证全部有明确结果，符合 overview 契约，无仅为测试存在的生产逻辑；缺失验证有原因和影响说明，不得将跳过测试报告为通过。

## 状态

complete

## 阶段交付记录

### 实际交付（2026-09-28）

- 新增 `eladmin/sql/20260928_dish_tag.sql`，创建独立的菜品标签字典表与关联表；本次未执行数据库脚本。
- 新增 `DishTag`、`DishTagRelation`、展示/查询 DTO、两个 Mapper 及 XML、`DishTagService`、查询 Controller；新增 `/api/dish-tags` 分页查询接口，响应为 `{content,totalElements}`。
- 修改 `Dish`、`DishMapper`、`DishServiceImpl`：新增可选 `tagIds` 与返回 `tags`；新增和详情/分页查询覆盖标签读写，菜品更新缺省或 null 保持关系、空数组清空、非空数组去重后整体替换；无效 ID 以 400 拒绝且事务回滚。删除菜品时按菜品 ID 清理关系并保留字典。
- 菜品编辑与删除先按菜品 ID 升序锁行，绑定时按标签 ID 升序锁行；分页查询使用一次关系批量查询。仅维护用的菜品详情/分页列表回填标签，不扩展排餐等查询契约。
- 新增 `eladmin-web/src/api/dishTag.js` 和 `DishTagEditor.vue`，并在矩阵页与主档列表共用的 `dish.vue` 表单中增加搜索、分页加载、选择、移除和表单重置。字典维护操作将在 Phase 02 补齐。
- 新增后端 `DishTagServiceImplTest`、`DishServiceImplDishTagTest`、`DishTagControllerTest` 与前端选择器/表单测试。

验证：Java 8 / Maven 3.5.2 执行 `mvn -pl eladmin-system -am compile -DskipTests` 成功；执行 `mvn -pl eladmin-system -am -Dtest=DishTagServiceImplTest,DishServiceImplDishTagTest,DishTagControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`，9 个测试通过；前端定向 Jest 2 个套件、7 个测试通过；新增及修改的前端源码 ESLint 通过，`git diff --check` 通过。MySQL 集成和两个入口的浏览器验收留到 Phase 03；这些检查尚未执行。

接口/权限契约：查询接口目前允许 `dish:list`、`dish:add` 或 `dish:edit` 权限；标签写权限将在 Phase 02 增加。后续实现必须继续使用同一锁协议和空值语义。
