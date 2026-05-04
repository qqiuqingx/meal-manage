# 排餐计划生成接口支持手工指定菜单槽位设计

**日期**: 2026-05-04
**状态**: 已确认

## 1. 背景

当前排餐计划生成接口 `POST /api/meal-plan/generate` 只支持根据 `recordDate` 自动推导菜单槽位：

- `weekNum = ScheduleKeyUtil.calcWeek(recordDate)`
- `dayOfWeek = ScheduleKeyUtil.calcDay(recordDate)`

然后用该槽位去查询 `meal_schedule_plan` 生成当天候选菜。

业务侧新增需求：生成某一天的排餐记录时，需要允许运营手工指定“第几周周几”的菜单模板，而不是强制使用 `recordDate` 自然映射出的模板。

典型场景：

- 2026-05-04 的午餐排餐，实际仍然是给 2026-05-04 这一天的订单生成记录
- 但候选菜单改为使用 “第 2 周周 3” 的模板，而不是 2026-05-04 自动推导出的模板

## 2. 目标

在不改变排餐日期业务语义的前提下，为排餐计划生成接口增加“手工指定菜单槽位”的能力。

本次改造后，系统需要同时支持两种模式：

1. 默认模式：不传新参数，沿用当前逻辑，按 `recordDate` 自动推导菜单槽位
2. 手工模式：传入 `menuWeekNum + menuDayOfWeek`，候选菜单改为按指定槽位查询

## 3. 范围

### 3.1 本期包含

- 扩展排餐生成接口请求参数
- 在服务层归一化最终生效的菜单槽位
- 候选菜查询改为使用“最终生效菜单槽位”
- 生成日志增加请求值与最终生效值记录
- 同步更新业务文档和接口文档
- 补充对应测试

### 3.2 本期不包含

- 不修改 `meal_plan`、`meal_plan_customer`、`meal_plan_customer_item` 表结构
- 不在数据库中保存本次生成使用的 `menuWeekNum/menuDayOfWeek`
- 不改变幂等删除键、加锁键、订单过滤逻辑
- 不修改定时任务的默认调用方式
- 不扩展为“按另一日期取菜单”

## 4. 核心语义

### 4.1 recordDate 的语义保持不变

`recordDate` 继续表示“实际排餐日期 / 配送日期”，仍然驱动以下逻辑：

- 有效订单过滤
- 同一天同餐次幂等删除
- 同一天同餐次并发锁控制
- 客户排除日期过滤
- 工作日 / 周末地址选择

### 4.2 菜单槽位语义

新增两个可选字段：

- `menuWeekNum`
- `menuDayOfWeek`

它们只用于查询 `meal_schedule_plan`，决定本次排餐从哪个菜单模板槽位加载候选菜。

### 4.3 最终生效规则

- 当 `menuWeekNum` 和 `menuDayOfWeek` 都不传时：
  - `effectiveMenuWeekNum = ScheduleKeyUtil.calcWeek(recordDate)`
  - `effectiveMenuDayOfWeek = ScheduleKeyUtil.calcDay(recordDate)`
- 当两者都传时：
  - 直接使用请求值作为最终生效菜单槽位
- 只传其中一个字段时：
  - 返回 400，提示参数必须同时传入

## 5. 接口设计

### 5.1 请求体

现有请求体：

```json
{
  "recordDate": "2026-04-01",
  "mealType": "LUNCH",
  "customerId": 123
}
```

改造后请求体：

```json
{
  "recordDate": "2026-04-01",
  "mealType": "LUNCH",
  "customerId": 123,
  "menuWeekNum": 2,
  "menuDayOfWeek": 3
}
```

### 5.2 字段约束

| 字段 | 必填 | 说明 |
|------|------|------|
| `recordDate` | 是 | 实际排餐日期，格式 `yyyy-MM-dd` |
| `mealType` | 是 | 餐次，支持 `BREAKFAST` / `LUNCH` / `DINNER` |
| `customerId` | 否 | 指定客户ID |
| `menuWeekNum` | 否 | 菜单模板周序号，范围 `1-4` |
| `menuDayOfWeek` | 否 | 菜单模板星期，范围 `1-7` |

### 5.3 校验规则

- `menuWeekNum` 和 `menuDayOfWeek` 要么都不传，要么都传
- `menuWeekNum` 只能为 `1-4`
- `menuDayOfWeek` 只能为 `1-7`
- 非法输入统一返回 400

建议错误提示：

- `menuWeekNum 和 menuDayOfWeek 必须同时传入`
- `menuWeekNum 仅支持 1-4`
- `menuDayOfWeek 仅支持 1-7`

## 6. 后端设计

### 6.1 DTO 层

修改 `MealPlanGenerateRequest`，新增：

- `private Integer menuWeekNum;`
- `private Integer menuDayOfWeek;`

字段只承载请求参数，不在 DTO 层编写复杂业务逻辑。

### 6.2 Controller 层

`MealPlanController.generateMealPlan(...)` 继续只做参数接收与透传，调用 service 时同时传递：

- `recordDate`
- `mealType`
- `customerId`
- `menuWeekNum`
- `menuDayOfWeek`

### 6.3 Service 层

`MealPlanService` 和 `MealPlanServiceImpl` 的生成方法签名扩展为支持两个可选菜单槽位参数。

在 `MealPlanServiceImpl.generateMealPlan(...)` 开头新增一个“菜单槽位归一化”步骤：

1. 校验新字段组合是否合法
2. 计算最终生效的 `effectiveMenuWeekNum`
3. 计算最终生效的 `effectiveMenuDayOfWeek`

归一化完成后：

- 订单过滤仍按 `recordDate`
- 候选菜查询改为按 `effectiveMenuWeekNum + effectiveMenuDayOfWeek + mealType`

### 6.4 候选菜加载

当前实现：

- `loadScheduledDishes(LocalDate targetDate, String mealType)`

建议改为：

- `loadScheduledDishes(Integer weekNum, Integer dayOfWeek, String mealType)`

这样方法职责更清晰：

- `recordDate -> menu slot` 的推导逻辑只在 service 入口出现一次
- `loadScheduledDishes` 只负责按明确槽位查菜单

底层仍继续使用 `mealSchedulePlanMapper.findBySchedule(weekNum, dayOfWeek, mealType)`，无需改数据库结构。

## 7. 不变的业务行为

以下行为本次必须保持不变：

### 7.1 幂等删除

重复生成时，仍然按：

- `recordDate`
- `mealType`
- `customerId`（若指定）

做软删除和重建。

不能因为指定了不同菜单槽位，就生成第二套同日期同餐次的并行计划。

### 7.2 并发锁

锁键仍然只基于：

- `recordDate`
- `mealType`

原因：

- 同一天同餐次本质上仍是同一轮排餐
- 手工指定菜单槽位只是候选菜来源不同，不应允许并发跑出两套结果

### 7.3 订单过滤

`loadValidOrders(...)` 仍按 `recordDate` 判断：

- 日期范围
- `schedule_mode`
- `delivery_dates`
- 剩余餐数
- `meal_type`

不得误改为按 `menuWeekNum/menuDayOfWeek` 或其隐含日期进行过滤。

### 7.4 客户排除日期和地址选择

以下逻辑继续以 `recordDate` 为准：

- `exclude_dates`
- 工作日 / 周末地址自动选择

## 8. 日志设计

本次不落库保存菜单槽位，只通过日志保留审计线索。

建议至少增加三类日志：

### 8.1 入口日志

记录原始请求参数：

- `recordDate`
- `mealType`
- `customerId`
- `menuWeekNum`
- `menuDayOfWeek`

### 8.2 归一化日志

记录最终生效值，并标明来源：

- 来源为 `AUTO`：表示由 `recordDate` 自动推导
- 来源为 `MANUAL`：表示运营手工指定

### 8.3 候选菜查询日志

记录实际用于查询 `meal_schedule_plan` 的：

- `effectiveMenuWeekNum`
- `effectiveMenuDayOfWeek`
- `mealType`

这样当运营反馈“某日菜单不对”时，可以直接从日志确认本次生成到底使用了哪套模板。

## 9. 文档更新

需要同步更新以下文档：

### 9.1 接口文档

文件：

- `eladmin/doc/apidoc/排餐计划生成接口.md`

需要补充：

- 新增请求字段说明
- 新参数组合校验规则
- 示例请求
- 明确说明：`recordDate` 是配送日期，`menuWeekNum/menuDayOfWeek` 是菜单模板槽位

### 9.2 业务文档

文件：

- `eladmin/doc/business/排餐管理业务说明.md`

需要补充：

- 生成排餐计划时支持手工指定菜单槽位
- 候选菜查询优先使用手工指定槽位，否则按 `recordDate` 推导
- 订单过滤、地址选择、排除日期过滤仍按 `recordDate`

## 10. 测试设计

至少覆盖以下场景：

### 10.1 默认兼容场景

不传 `menuWeekNum/menuDayOfWeek`：

- 仍然按 `recordDate` 自动推导菜单槽位
- 行为与当前版本一致

### 10.2 手工指定生效场景

传入 `menuWeekNum=2`、`menuDayOfWeek=3`：

- 候选菜查询按 `2-3` 加载
- 其他业务逻辑继续按 `recordDate`

### 10.3 参数组合错误

- 只传 `menuWeekNum`
- 只传 `menuDayOfWeek`

以上都应返回 400。

### 10.4 参数越界

- `menuWeekNum=0`、`5`
- `menuDayOfWeek=0`、`8`

以上都应返回 400。

### 10.5 业务边界回归

指定菜单槽位后，验证以下行为不受影响：

- 幂等删除仍按 `recordDate + mealType`
- 锁键仍按 `recordDate + mealType`
- `exclude_dates` 仍按 `recordDate`
- 地址选择仍按 `recordDate`
- 订单过滤仍按 `recordDate`

## 11. 涉及文件

### 11.1 后端修改

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/domain/dto/MealPlanGenerateRequest.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/rest/MealPlanController.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/service/MealPlanService.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/service/impl/MealPlanServiceImpl.java`

### 11.2 文档修改

- `eladmin/doc/apidoc/排餐计划生成接口.md`
- `eladmin/doc/business/排餐管理业务说明.md`

### 11.3 测试修改

- 相关 controller / service 测试文件
- 如果当前模块暂无成型单测，可优先补 service 层参数归一化与候选菜查询测试

## 12. 风险与约束

### 12.1 风险

- 如果只改候选菜查询、不明确保护其他逻辑，后续维护者可能误把 `recordDate` 和菜单槽位语义混淆
- 如果日志不完整，线上问题会很难确认本次生成到底使用了自动模板还是手工模板

### 12.2 约束

- 本次必须保持接口向后兼容
- 不允许引入数据库结构变更
- 不允许改变现有排餐主键语义和幂等策略

## 13. 结论

采用“新增 `menuWeekNum + menuDayOfWeek` 可选参数”的方案，以最小改动支持手工指定菜单模板。

该方案具备以下特点：

- 向后兼容，不传新参数时行为不变
- 语义清晰，配送日期与菜单模板槽位职责分离
- 风险可控，只影响候选菜定位，不改变排餐核心业务边界
- 无需落库，只通过日志保留操作线索
