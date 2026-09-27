# Phase 01 定向撤除同义词

## 执行前规则检查

重新读取用户级、仓库及目标路径 AGENTS.md，规则变化先修订本阶段。读取 status.yaml、00-overview.md 及前置阶段交付记录。

## 目标

移除未上线的同义词实现，使原有配料及分类管理不再依赖同义词表。

## 前置依赖

无。用户已确认同义词 SQL 未执行；执行者须复核工作区是否出现新的调用方。

## 输入与输出

输入：当前混合未提交工作区、旧计划、同义词模块。
输出：定向清理清单、无旧运行时依赖的应用、旧计划暂停说明。

## 本阶段实施约束

仅撤除同义词增量，不回退整个已修改文件，不动客户导入、部署、日志等改动。SQL 未执行不等于证明所有外部调用不存在；发现实际外部调用时先明确影响再移除。无数据库操作。

## 涉及文件

删除前逐一确认是本次废弃模块，再删除：

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/` 下 domain、domain/dto、mapper、rest、service、service/impl 中的 `DishIngredientSynonym*.java`。
- `eladmin/eladmin-system/src/main/resources/mapper/DishIngredientSynonymMapper.xml`。
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/meal/` 下同义词控制器和服务测试。
- `eladmin-web/src/api/dishIngredientSynonym.js`、`eladmin-web/src/views/meal/dishIngredientSynonym/`、`eladmin-web/tests/unit/views/meal/dishIngredientSynonym.spec.js`。
- `eladmin/sql/20260926_ingredient_synonym.sql`、`eladmin/doc/apidoc/配料同义词接口文档.md`。

修改：两个现有 `DishIngredientServiceImpl` / `DishIngredientCategoryServiceImpl`、`eladmin/doc/business/配菜管理业务说明.md`、旧计划 `docs/plans/ingredient-synonym-mapping/00-overview.md` 和 `status.yaml`。

## 实施步骤

1. 记录 git status 和相关 tracked 文件 diff，备份要删的未跟踪源码；检索 synonym/Synonym/同义词/食材别名的引用，分类记录运行时代码、文档及历史计划。复核 agent-service 和动态菜单脚本。
2. 删除上述独立新增文件。两个已有 Service 仅移除 import、构造器依赖和 verifyTargetsCanBeDeleted/verifyTargetCanBeDisabled 调用；保留分类映射、既有校验和其他用户改动。
3. 业务文档移除旧解析方案的现行描述，改为本计划链接。检查客户文档仅在存在旧方案描述时定向修正。
4. 旧计划总体标记 superseded 并链接新计划；旧同义词阶段停止执行，客户/排餐等未确认范围标注需重新规划，不机械删除历史需求。
5. 核对一级改名 SQL 只有四个一级映射及一个 level=1、parent_id IS NULL 的 UPDATE，保留默认预览开关；不改分类兼容映射。

## 验证方式

- 定向 rg 确认主源码、前端 API 和运行 SQL 不再引用旧模块，历史计划中的文字引用可保留并注明废弃。
- 使用 Maven 技能选择环境后编译 eladmin-system 及必要依赖；检查仍存测试的构造器调用，运行相关配料/分类测试。前端仅对受改文件 lint。
- 对比实施前快照，确保无关差异保留；git diff --check 及未跟踪文件空白检查。

## 完成标准

原有配料和分类源码可编译、同义词表不再是运行依赖，无旧菜单创建脚本残留，无数据库删除动作。交付记录明确实际清理范围和调用方检索结论。

## 状态

completed

## 阶段交付记录

### 实际交付记录（2026-09-27）

- 删除同义词实体、DTO、Mapper、Service、Controller、XML、测试、前端 API/页面/测试、DDL 和接口文档；从配料与分类 Service 移除同义词保护依赖。保留 `DishIngredientServiceImpl` 中既有配料 ID 校验、分类兼容映射及其他工作区改动。
- 更新配菜管理业务说明，将尚未交付的标签能力标为计划中，并链接本计划。旧同义词总计划标记为 superseded；客户、订单、排餐和 Excel 需求需独立重规划，不随本次实施。
- 调用方复核：仓库主源码、前端、agent-service 和脚本中没有发现同义词模块以外的调用；原 SQL 为未跟踪文件且计划确认未执行。此检索不能证明仓库外部署没有调用方。
- `supermarket_category_name_rename.sql` 复核为四项一级名称映射，并限定 `level=1 AND parent_id IS NULL`；默认预览开关保留。未修改该脚本。
- 非 clean 编译后发现并定向移除了生成目录中残留的 `target/classes/mapper/DishIngredientSynonymMapper.xml`，避免 MyBatis 运行时继续加载已废弃 Mapper；未清理其他构建产物。
- 变更前快照保存在 `/private/tmp/ingredient-tags-phase01-before.tar`、`/private/tmp/ingredient-tags-phase01-before.status` 和 `/private/tmp/ingredient-tags-phase01-before-services.diff`。
- 验证：定向源码检索确认同义词运行时引用和菜单 SQL 已移除；`mvn -pl eladmin-system -am compile -DskipTests` 通过；`git diff --check` 结果记录于总体验收。无数据库操作。

下游：进入 Phase 02，新增独立标签表和 API；不迁移或恢复同义词数据，也不自动启用旧客户禁忌/排餐计划。
