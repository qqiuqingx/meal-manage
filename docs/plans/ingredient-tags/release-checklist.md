# 配料多标签发布与回滚清单

状态：代码、文档和测试环境数据库验收已完成；未部署生产，也未向任何角色授权。

## 发布顺序

1. 在隔离测试环境执行 `eladmin/sql/20260927_ingredient_tag.sql`，确认两张标签表、配料管理子菜单和三个写权限菜单均存在且没有重复。记录新生成的菜单ID。
2. 在该测试环境部署后端和前端。
3. 通过系统现有角色授权流程，只给需要维护标签的角色分配 `dishIngredientTag:list/add/edit/del`。可编辑配料但不维护字典的角色使用现有 `dishIngredient:list/add/edit` 读取标签选项。
4. 完成下方功能与数据库验收并记录日志、测试数据主键和清理结果后，才评估生产上线。生产 DDL、部署和授权需单独获准。

## 验收记录

| 项目 | 当前证据 | 发布状态 |
|---|---|---|
| 新建、改名、删除未使用标签；空名、超长名及重名错误 | 后端单元/控制器测试；MySQL 唯一键及改名集成测试；前端标签页测试通过 | 通过 |
| 一个配料绑定多个标签、多个配料复用同一标签、编辑回显及清空 | MySQL 服务集成测试与前端表单测试通过 | 通过 |
| null 保持、空数组清空、重复ID去重、无效ID事务回滚 | 后端单元与 MySQL 服务集成测试通过 | 通过 |
| 标签筛选 AND 条件、分页总数不膨胀、导出标签列 | MySQL Mapper/Service 查询及 Excel 内容集成断言通过；前端筛选/下载参数测试通过 | 通过 |
| 标签删除与绑定竞争、同一配料并发替换、删除配料清理关系 | 真实 MySQL 并发测试及限定ID清理通过 | 通过 |
| 权限拒绝和允许路径 | Spring 方法安全集成测试验证无权限拒绝、配料新增权限可读取标签；写接口分别使用标签权限注解 | 方法级验证通过；未在测试库给角色授权 |
| 原配料分类和菜品配料查询 | Phase 01 编译通过；原分类控制器回归测试通过；标签字段为增量 | 通过 |

### 自动化验证证据

- Maven：`mvn -pl eladmin-system -am -Dtest=DishIngredientTagServiceImplTest,DishIngredientServiceImplTest,DishIngredientTagControllerTest,DishIngredientCategoryControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`，23 项通过（21 项标签/配料相关、2 项分类控制器回归）。
- MySQL：在用户指定的 `eladmin/eladmin-system/.env.local` 测试环境执行 `eladmin/sql/20260927_ingredient_tag.sql`；第二次执行仍只有 4 条对应菜单权限，父菜单ID 120，新增菜单ID 172–175，未授予角色权限。MySQL 版本 8.0，两表 collation 均为 `utf8mb4_unicode_ci`。
- 菜单脚本遵循当前 `sys_menu.name` 唯一约束，将按钮节点 `name` 写为 `NULL`。首次测试执行触发唯一键冲突后在事务中回滚菜单写入；修正脚本后执行成功并重复执行验证无重复。
- MySQL 服务集成：`mvn -pl eladmin-system -am -Dtest=DishIngredientTagMySqlIntegrationTest -DingredientTag.mysql.integration=true -Dsurefire.failIfNoSpecifiedTests=false test`，3 项通过。该类由 `@EnabledIfSystemProperty` 默认禁用；显式属性开启后使用真实 Spring Service/Mapper，按本次生成主键清理。未设置该属性的单独检查为 1 项跳过，未启动 Spring 上下文或连接数据库。结束时标签、关系及本测试命名的配料记录数均为 0。
- 前端：`vue-cli-service test:unit --runInBand tests/unit/views/meal/dishIngredientTag.spec.js tests/unit/views/meal/dishIngredientForm.spec.js tests/unit/views/meal/dishIngredient.spec.js`，11 项通过。
- 修改源码 ESLint、三个 Mapper XML 解析及 `git diff --check` 通过。
- MySQL 服务测试通过随机标签名和配料名运行；结束后只按对应配料ID清理关系/配料，并删除无引用的本次标签ID。验证没有残留测试标签、关系或配料。

## 上线前操作记录

| 环境 | SQL执行 | 标签表 | 菜单ID | 应用部署 | 角色授权 |
|---|---|---|---|---|---|
| 测试环境（用户指定配置） | 2026-09-27；重复执行验证无重复 | 两表存在，测试记录已清理 | 父级120；标签页172；新增173；编辑174；删除175 | 未部署 | 未授权 |
| 生产环境 | 未执行 | 未执行 | 待执行时记录 | 未部署 | 未授权 |

## 一级分类改名（独立可选）

`eladmin/sql/supermarket_category_name_rename.sql` 只含“肉类→肉禽蛋、调料→调味品、主食→粮油米面、坚果→干货坚果”四项一级分类改名，限定 `level=1 AND parent_id IS NULL`，默认预览开关为 0。执行前保存来源分类ID、名称、覆盖数量和冲突结果；此步骤不属于标签部署前置条件，也未在本次执行。

## 回滚

- 应用异常时回到部署前的后端和前端版本，保留标签表及用户创建的标签数据。
- 如需隐藏新菜单，使用本次 SQL 执行记录的标签菜单ID定向停用或调整可见性；不得删除整表、清理角色外数据或生成无条件关系删除。
- 一级分类改名若单独执行，根据执行前记录的分类ID定向恢复名称；不移动二级分类或配料。
