# Phase 01 客户结构化饮食字段

## 执行前规则检查
用户于 2026-09-28 明确授权按总计划实施。实施时已核对仓库规则、阶段依赖和实际代码契约；未连接业务数据库。

## 目标
客户结构化饮食字段形成可验证闭环。

## 前置依赖
用户认可本执行计划，无前置代码阶段。

## 输入与输出
输入：五类对象与字段语义。输出：增量SQL、字段序列化、客户详情/维护接口及字典选项契约。

## 本阶段实施约束
遵循overview已确认业务方向；保留无关未提交修改；沿用POI、fastjson2和现有权限；不新增排餐匹配规则，仅适配术后字段的已有查询/显示，不新增依赖，不操作业务数据库。新方法补充说明。不得为测试增加专用生产接口。公共契约变更限定在本计划列出的客户字段与导入接口。

## 涉及文件
新增：
- `eladmin/sql/20260928_customer_diet_items.sql`
- `.../customer/profile/domain/dto/CustomerDietItemDto.java`
- `.../customer/profile/handler/CustomerDietItemsTypeHandler.java`
- 客户饮食字典只读Mapper、Service及选项Controller（按现有分层命名）
修改：
- `CustomerProfile.java`、`CustomerProfileSaveDto.java`、`CustomerProfileDetailDto.java`
- `CustomerProfileServiceImpl.java`、`resources/mapper/CustomerProfileMapper.xml`
其中`...`统一指`eladmin/eladmin-system/src/main/java/me/zhengjie/modules`。

## 实施步骤
1. 新增两个结构化JSON列和两个原文块JSON列，提供独立增量SQL；不改已有建表脚本中的无关变更，不执行SQL。
2. 定义类型/ID/名称引用及原文字符串数组映射，均使用fastjson2；覆盖自定义XML和MyBatis-Plus读取路径。
3. 复用现有五类字典，补充客户权限下的只读聚合选项查询；二级分类限定level=2。现有菜品/配料/标签写接口不变。
4. 客户详情返回结构化字段及只读原文块；原文由导入写入，普通客户编辑不修改原文。新增与编辑校验类型和ID，名称取字典。落实null保留、[]清空、非空替换与历史引用保留语义。
5. 客户维护和导入权限均可读取所需选项；不扩大字典写权限。

## 验证方式与完成标准
- JSON数组往返、同ID不同类型、去重、非法类型/ID拒绝、客户端名称不可信。
- DTO未传不擦除、结构化[]清空、详情和列表读取类型正确。
- 原文标点和换行往返无损；重复原文块不重复保存，结构化编辑不覆盖原文。
- 编译受影响模块及必要依赖；不启动应用、不连接业务库。


## 本次需求变更补充
- 将客户productionDate/production_date统一改为术后String字段postoperativeInfo/postoperative_info，日期选择器改文本输入；G列保留文本。
- 涉及CustomerIntakeParseServiceImpl、MealPlanCustomer、MealPlanDetailVO、MealPlanCustomerMapper.xml、MealPlanServiceImpl及相关DTO/测试，完整核对生产日期读取与日期计算，不增加日期兼容回退。
- 原日期数据先备份，不能当作术后天数直接推算；迁移保留原值供核对，迁移策略和调用方影响在实际实施前检查。
- 订单Entity非持久字段、CustomerOrderDetailDto、CustomerOrderMapper.xml和CustomerOrderServiceImpl补充客户饮食原文与对象投影，不在订单表复制客户共享字段。
- 验证术后“5天”“4个月”的String往返、空值保留、日期计算调用已正确调整、订单列表和详情共享展示一致。
- 用户已确认取消排餐页原0～3天标记，直接展示术后原文；移除日期差计算、nearProductionDate/productionDateDiffDays及旧标记调用点，验证不再依赖LocalDate生产日期。

## C列医嘱补充
复用客户现有medicalRequirements/medical_requirements文本字段及查询编辑映射，不新增医嘱列或改变原有普通编辑契约。导入草稿增加医嘱来源文本与目标值。

## 排餐计划展示契约补充
排餐客户查询与详情补齐medicalRequirements、dishRequirementsRaw、dishRequirements、dietaryRestrictionsRaw、dietaryRestrictions，从关联客户读取当前值。覆盖MealPlanCustomer、MealPlanDetailVO、MealPlanCustomerMapper.xml与MealPlanServiceImpl的实际查询/组装路径；JSON使用fastjson2映射，不产生逐客户额外查询，不在排餐表复制共享字段。与术后字段适配一起验证列表/详情返回完整。

## 状态
complete

## 阶段交付记录
已完成。交付包括客户饮食引用与原文 JSON 类型处理、五类字典只读选项和服务端类型/ID/名称校验、客户详情/维护映射、术后字符串字段替换、订单与排餐计划的客户共享资料投影，以及增量 SQL `eladmin/sql/20260928_customer_diet_items.sql`。普通编辑不修改导入原文；`null` 保留饮食引用、空数组清空；旧引用保留名称快照。

验证：`mvn -pl eladmin-system -am -DskipTests compile` 通过；类型处理器、字典引用校验、客户编辑保留/清空、订单/排餐 Mapper 映射、话术术后文本回归通过。迁移 SQL 未执行；上线前需先按客户主键备份旧 `production_date`。
