# Phase 02 标签维护与配料多选闭环

## 执行前规则检查

重新读取用户级、仓库及目标路径 AGENTS.md，规则变化先修订本阶段。读取 status.yaml、00-overview.md 及前置阶段交付记录。

## 目标

实现创建标签、给配料绑定多个标签、保存和重新打开回显的最小完整闭环。

## 前置依赖

Phase 01 完成，提供清理清单和编译结果；采用 overview 中表结构、权限、null/空数组和删除契约。

## 输入与输出

输入：现有配料 CRUD、Vue 表单、分类管理风格。
输出：可重复执行的新建表/菜单脚本、标签管理 API/页面、配料多选保存及回显、相关测试和接口说明。

## 本阶段实施约束

只添加标签领域，不恢复同义词表或转换旧数据。不新增库。事务和行锁必须覆盖生产调用路径，拒绝无条件关系删除；Getter/Setter、字段/方法注释及 fastjson2 规则适用。

## 涉及文件

- 新增 `eladmin/sql/20260927_ingredient_tag.sql`。
- 在 `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/` 新增 `domain/DishIngredientTag.java`、`domain/DishIngredientTagRelation.java`，标签 QueryCriteria/保存及响应 DTO，Tag/TagRelation Mapper，TagService/Impl、TagController；标签关联读写收敛在标签服务中，不能反向依赖配料 Service 形成环。
- 新增 `eladmin/eladmin-system/src/main/resources/mapper/DishIngredientTagMapper.xml`（名称/引用查询及行锁，关联 SQL 按现有 Mapper 职责分配）。
- 修改 `DishIngredient.java`、`service/impl/DishIngredientServiceImpl.java`；必要的行锁 SQL 放现有 `DishIngredientMapper.java/xml`。
- 新增 `eladmin-web/src/api/dishIngredientTag.js`、`eladmin-web/src/views/meal/dishIngredientTag/index.vue`；修改 `eladmin-web/src/views/meal/dishIngredient/form.vue`、`index.vue`。
- 新增对应后端 TagService/IngredientService/Controller 测试，前端 Tag 页面和 Ingredient 表单测试，以及 `eladmin/doc/apidoc/配料标签接口文档.md`。

## 实施步骤

1. 新脚本仅创建两张标签表及菜单权限，不放业务种子。沿用现有菜单父级定位方式，以 permission 查重，不硬编码环境 ID；无父菜单时提示核查，不默默写成根菜单。不授予角色权限。
2. 实现标签 GET/POST/PUT/DELETE；校验名称长度、空值和重名，使用数据库唯一约束兜底。删除标签锁定该标签后检查关系，有引用拒绝。API 文档同时写明权限和错误。
3. 配料实体增加非持久字段 tagIds/tags（@TableField(exist=false)），调整为 Getter/Setter。新增配料保存成功后取得 ID，同事务绑定标签；更新先锁配料行，再按契约处理 null/空数组/整体替换。按 tagId 升序锁定要绑定的标签并验证存在，避免删除竞争；删除配料限定 ID 清理关联。
4. 列表、详情、按菜品查询返回标签；列表先分页查配料，再按本页 ID 批量加载标签，避免 N+1 和多表 JOIN 影响 total。空集合直接返回，不产生 IN ()。
5. 标签管理页实现分页搜索、新增、改名、删除；配料页提供权限控制的管理入口。表单用 Element UI multiple 选择器，远程搜索分页可继续加载，详情 tags 合并进选项缓存；新增重置、编辑回填、清空、加载失败均明确处理。
6. 配料列表以 el-tag 展示名称；标签名称变化按查询实时回显。同步新接口及配料接口字段文档，保证这一阶段已可独立演示。

## 验证方式

- 后端：两个标签绑定同一配料、同标签复用于多个配料、重复 ID 去重、无效 ID 回滚、null 保持/[] 清空、名称冲突、标签引用删除保护、删除配料清理、停用保留关系。
- 前端：多选请求、详情回填、超出选项首页的选中标签、清空后重开、新增重置和失败不误报成功；lint 修改文件并运行相关 Jest 测试。
- Maven 编译受影响模块及依赖，运行新增相关测试；事务及锁的数据库语义在 Phase 03 使用隔离测试数据验证，不能将纯 mock 测试报告为验证了数据库事务。

## 完成标准

端到端多选保存回显通过，既有分类编辑仍可用，后端实际鉴权生效；无 N+1、无同义词兼容接口、无测试专用生产分支。

## 状态

completed

## 阶段交付记录

### 实际交付记录（2026-09-27）

- 新增 `dish_ingredient_tag` / `dish_ingredient_tag_relation` DDL、可重复执行的标签菜单权限 SQL、标签实体/DTO/Mapper/Service/Controller 和标签管理页面/API。
- 配料新增、详情、编辑、分页列表及按菜品查询支持批量标签 ID/详情；新增 `tagIds` 为空时不绑定，编辑 `null` 保持原关系、空数组清空、非空数组整体替换。关系读写收敛在 `DishIngredientTagService`，未形成 Service 循环依赖。
- 更新配料时先锁配料行，再按标签ID升序加锁、校验并替换；标签删除锁标签后检查引用。配料删除按升序锁定传入配料ID，同事务清理范围限定的关联，标签字典保留。
- 标签名称 trim、按 64 个 Unicode 字符校验；数据库 `utf8mb4_unicode_ci` 唯一键负责大小写不敏感并发兜底，转换为友好错误。标签被引用时拒绝删除。
- 新增权限 `dishIngredientTag:list/add/edit/del`。标签 GET 允许配料 list/add/edit 权限加载选项；写操作要求对应标签权限。SQL 未授予角色权限；测试环境执行情况见 Phase 04 交付记录。
- 验证：Maven 受影响模块测试、前端 8 项相关 Jest 测试和修改源码 ESLint 均通过；测试覆盖标签重名/长度、引用保护、绑定去重和锁顺序、`null`/空数组语义、表单远程分页和详情回填。事务与并发仍需 Phase 03 真实 MySQL 验证。

下游：Phase 03 已补标签筛选及导出列；数据库关联和并发验收必须使用隔离测试数据库，不把 mock 测试当作事务验证。
