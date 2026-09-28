# Phase 02 弹窗内字典编辑和权限闭环

## 执行前规则检查

重新读取当前用户级、仓库与目标目录生效的 AGENTS.md；加载 status.yaml、00-overview.md 和本阶段前置交付记录。规则变化先同步计划，不加载无关阶段全文。

## 目标

在同一个菜品弹窗完成字典新增、重命名和删除，补齐按钮权限及即时保存反馈。

## 前置依赖

依赖 Phase 01 完成；读取其交付记录、DDL、DishTagService 锁协议、前端 tagIds 契约。

## 输入与输出

输入：overview 中已明确的数据、接口和交互契约，以及上述前置产物。
输出：下列文件及可验证行为、验证记录和后续阶段所需契约。

## 本阶段实施约束

不增加独立页面，不使用配料标签表；保持旧请求不传 tagIds 的行为。按 overview 权限与事务约定实施，不扩大业务范围。新增方法/字段有注释，新增实体不使用 @Data。无新增依赖、测试专用生产入口或无条件清表；保留用户已有修改。涉及契约变化先记录调用方影响，超出确认范围时先更新方案。

## 涉及文件

以下 Java 简写路径均相对 eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/；测试放在对应 src/test/java 路径。

新增：domain/dto/DishTagSaveDto.java（位于 meal 模块）；DishTagControllerTest、DishTagServiceImplTest 中相关用例。
修改：Phase 01 的 DishTagController、DishTagService/Impl、DishTagMapper/XML、20260928_dish_tag.sql、dishTag.js、DishTagEditor.vue 及相关组件测试。
不修改配料标签 API 或 tagManager.vue，不新增 router 页面。

## 实施步骤

1. 增加 POST/PUT/DELETE 字典端点，使用校验 DTO、项目异常与操作日志；名称规范化、64字符限制、数据库唯一索引并发兜底。
2. 新增返回生成ID；改名锁定字典行，保留关联；删除锁定字典行后检查所有菜品引用，非零拒绝。遵循 Phase 01 锁协议。
3. 菜品弹窗内提供搜索无结果新增、每条选项重命名/删除；确认提示、失败保留输入、提交中禁重复点击，正确合并当前已选ID与名称缓存。
4. 新增字典成功自动选中；字典删除成功清理未保存选择；绑定在主表单保存时才提交。若移除当前已保存引用后立即删字典，服务端仍拒绝，并提示先保存菜品。
5. 固定展示字典操作立即生效、取消不撤销的说明。菜品保存因他人删除标签失败时刷新字典并提示重新选择，不静默忽略无效标签。
6. 定向核实 sys_menu 中菜品权限定位方式，幂等新增 dishTag:add/edit/del 按钮权限；缺父菜单时诊断并不插入，保持无角色默认授权。
7. 前后端一致控制权限；仅有 dish:edit 可绑定已有标签，缺少 dishTag 权限不显示相应字典写操作，越权直调后端应拒绝。

## 验证方式

- 标签重名（包含大小写/去空白）、空名称、超长、不存在ID、改名后重新回显、已引用拒绝删除、未引用成功删除。
- Controller 覆盖查询/字典写入/管理员/无权限；前端覆盖按钮权限及403/400失败反馈。
- 组件覆盖新菜品创建标签后取消、重命名后取消、移除绑定后保存、搜索翻页保持已选和双击提交。
- 定向 lint 和相关后端/前端测试。

## 完成标准

阶段目标与上述验证全部有明确结果，符合 overview 契约，无仅为测试存在的生产逻辑；缺失验证有原因和影响说明，不得将跳过测试报告为通过。

## 状态

complete

## 阶段交付记录

### 实际交付（2026-09-28）

- 新增 `DishTagSaveDto`；扩展 `DishTagService` 和实现类，支持新增、改名和删除。名称统一去除首尾空白，校验非空及最多64个 Unicode 字符，依赖数据库唯一索引处理并发重名。改名和删除先锁标签行；删除在锁内检查所有菜品引用，有引用时返回明确的移除并保存提示。
- 扩展 `DishTagController`：POST 返回 201 和生成ID，PUT 返回 204，DELETE 返回 200；分别校验 `dishTag:add/edit/del`。新增和删除带操作日志。新增方法安全测试覆盖标签权限允许、普通菜品编辑权限拒绝以及管理员允许。
- 扩展 `dishTag.js` 和 `DishTagEditor.vue`：无匹配时可新增标签并自动加入待保存选择；已有字典项支持内联改名及确认、删除确认；重命名提示影响使用该标签的全部菜品；成功重命名同步更新当前选择展示，成功删除清理当前未保存选择。失败保留输入/选择并显示后端业务错误，提交中阻止重复点击。
- 字典操作即时保存，固定显示“标签字典操作立即生效；标签选择在保存菜品后生效。”新建菜品取消只丢弃关系选择，字典变更保留。
- 在 `eladmin/sql/20260928_dish_tag.sql` 追加幂等的 `dishTag:add/edit/del` 按钮权限记录。按 `type=1 AND permission='dish:list'` 动态定位菜品管理父菜单；缺失时输出诊断且不插入菜单，不增加页面菜单，不变更角色授权。按钮节点 `name=NULL` 以满足 `sys_menu.name` 唯一约束。
- 扩充 Service、Controller 和前端测试，覆盖名称规范化/重复/长度、改名/删除、引用拒删、权限、即时字典操作失败、双击、取消、搜索分页及已选项保留。

验证：Java 8 / Maven 3.5.2 执行 `mvn -pl eladmin-system -am -Dtest=DishTagServiceImplTest,DishServiceImplDishTagTest,DishTagControllerTest,DishTagControllerPermissionTest -Dsurefire.failIfNoSpecifiedTests=false test`，21 个测试通过；前端定向 Jest 2 个套件、16 个测试通过；相关源码 ESLint 与 `git diff --check` 通过。尚未执行 MySQL 集成/并发测试、部署 SQL 或浏览器验收，转 Phase 03 处理。

接口/权限契约：字典写接口只接受 `dishTag:add/edit/del`；菜品绑定仍受原 `dish:add/edit` 保护；标签查询接受 `dish:list/add/edit`。按钮仅挂在现有菜品管理菜单下，脚本不默认向任何角色授权。
