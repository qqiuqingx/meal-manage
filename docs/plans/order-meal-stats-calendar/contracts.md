# 字段、接口和订单日历契约

本文件是各 Phase 的共同输入。业务目标及用户确认见 [00-overview.md](00-overview.md)。以下接口是本次计划定义的目标契约，不表示当前已实现。

## 1. 列表字段与顺序

| 顺序 | 列名 | DTO/数据来源 | 展示及统计规则 |
|---|---|---|---|
| 1 | 手机号 | 客户 `phone` | 空值显示 `-` |
| 2 | 地址 | `addressText`，客户地址表 | 复用当前默认地址优先、工作日其次、周末再次的选择与多行格式 |
| 3 | 客户编号 | 订单 `customerCode` | 与当前订单标识一致；筛选同一来源字段 |
| 4 | 客户姓名 | 客户 `customerName` | 展示当前共享档案值 |
| 5 | 特殊要求 | 客户 `specialRequirements` | 只取该字段，不拼接医嘱 |
| 6 | 排餐模式 | 订单 `scheduleMode` | 使用现有中文枚举；空值显示规则与订单页保持一致，不改变生成时的缺省语义 |
| 7 | 餐次 | 订单 `mealType`，结合购买餐池 | 午/晚/午晚按枚举；ALL 根据早餐、午晚购买数展示实际覆盖餐池，不显示代码；未指定显示“待确认” |
| 8 | 规格 | 订单 `mainDishCount/sideDishCount/vegCount` | 沿用订单页“主/副/素”展示，不新增规格字段 |
| 9 | 含汤 | 订单 `soupCount` | `>=1` 含汤，否则不含汤 |
| 10 | 早餐 | 订单 `breakfastCount` | 订单购买早餐数 |
| 11 | 午晚 | 订单 `lunchDinnerCount` | 订单购买午晚合计数 |
| 12 | 合计 | `totalCount` | 早餐＋午晚 |
| 13 | 核销 | 订单 `verifiedCount` | 已含导入历史核销基数；有基数时可沿用订单页提示，不重复加总 |
| 14 | 已排餐 | `scheduledCount` | 复用 `countAllScheduledByOrderIds`：当前订单全部有效结果行，含成功/失败、不区分是否核销；不是应排计划格数 |
| 15 | 剩余 | 订单 `remainingCount` | 沿用订单当前剩余数，不使用旧餐池合并公式 |
| 16 | 预计剩余 | `estimatedRemainingCount` | `max(remainingCount - 今日成功已排且未核销份数, 0)`；今日按服务端业务日期，与所选月份无关 |
| 17 | 状态 | 订单 `status` | 当前筛选下为进行中/暂停；枚举展示沿用现有规则 |
| 18 | 基本情况 | 客户 `medicalRequirements` | 医嘱原文 |
| 19 | 成单时间 | 订单 `dealTime` | 当前订单成交时间，保留时间部分；不取最早一笔订单，不取创建时间 |
| 20 | 术后天数 | 客户 `postoperativeInfo` | 原文展示，例如“5天”“4个月”；不推算、不递增 |
| 21 | 菜品特殊要求 | 客户 `dishRequirements/dishRequirementsRaw` | 复用 `CustomerDietCell` 对象和完整原文展示 |
| 22 | 过敏食物 | 客户 `allergyTags` | 标签展示，与禁忌分开 |
| 23 | 禁忌食物 | 客户 `dietaryRestrictions/dietaryRestrictionsRaw` | 复用 `CustomerDietCell` |
| 24 | 自定义菜单 | 订单 `customMenuImage` | 当前订单图片缩略图及大图预览，复用现有安全路径处理；本次不增加上传编辑 |

24 个业务列之后保留“操作/排餐日历”。`orderId/customerId/orderCode` 为内部行身份与日历标题所需字段，其中 `orderCode` 不额外插入用户要求的业务列。相同客户各订单完整展示，不跨行合并共享字段。

列表保持滚动加载、首批 20 条、已有客户编号/姓名/手机号/月筛选。分页 `totalElements` 改为符合条件的订单数；早餐＋午晚订单也只占 1 条。排序稳定：客户编号、订单开始日期、成交时间、订单 ID，明确空值排序且始终以 ID 消除并列。

月筛选保留 `start_date < 次月首日`，不新增订单结束日期过滤；`status IN (1,4)` 且 `remaining_count > 0`。数值列是订单累计/当前值，不是月份历史快照。金额字段不进入本页 DTO。

## 2. API 目标契约

### 列表

保留 `GET /api/customerProfile/mealStats`，权限 `customerProfile:list`（沿用 admin 的现有处理）。

请求保留 `customerCode/customerName/phone/statsMonth/page/size`；page 从 1 开始，前端 size=20。响应继续为 `PageResult`，`content` 是订单行，`totalElements` 是订单数。使用 MyBatis-Plus 对订单分页后批量补充当前页客户资料、地址和统计数，禁止先计算全部客户整月日历再分页。

移除旧行字段 `mealBucket/firstRowInGroup/groupRowSpan` 和随行返回的客户合并日历。`mealCount/remainingMealCount/purchaseDateText/deliveryInfo/soupLabel/remarkInfo` 等旧展示字段不作为新列表契约保留。

### 订单日历查询

新增 `GET /api/customerProfile/mealStats/orders/{orderId}/calendar?statsMonth=yyyy-MM`，权限 `customerProfile:list`。

响应 `CustomerOrderMealCalendarDto`：

- `orderId/customerId/orderCode/customerCode/customerName/statsMonth`：身份和标题。
- `status/mealType/startMealType/startDate/endDate`、`breakfastCount/lunchDinnerCount`、两餐池剩余量、`defaultIncludesSoup`：该订单摘要，不返回其他订单。
- `editable/readOnlyReason`：服务端业务可编辑性，前端还必须叠加编辑权限；暂停、未指定餐次或已关闭订单只读。
- `revision`：当前订单日历及相关约束状态的修订标记。
- `cells`：当前订单当月有业务意义的日期餐次格。复用并扩展 `CustomerMealScheduleCellDto`，支持 BREAKFAST/LUNCH/DINNER。
- `overrides`：该订单当月完整的持久化覆盖项，包含 0 份和被客户统一停餐暂时遮蔽的记录；供编辑器完整回传。

cell 至少包含 `orderId/date/mealType/baseQuantity/quantity/soupQuantity/defaultIncludesSoup/generatedCount/failedCount/verifiedCount/manualOverride/customerExcluded/orderExcluded`。两种 excluded 的来源独立；无计划不等于人工停餐。早餐可复用原有勾选交互，午晚保留份数/含汤编辑；底层都使用当前订单覆盖项。已有早餐正数覆盖原样读取，不能因 UI 假定 1 份而在无操作保存时覆盖历史数量。

`baseQuantity` 的定义：保留客户统一停餐、此前月份与其他格已生效的覆盖，仅移除当前格覆盖后计算出的默认量；不能简单取无任何历史调整的整单原始分配结果。删除覆盖后的最终计划需由后端重新计算。

### 订单日历保存

新增 `PUT /api/customerProfile/mealStats/orders/{orderId}/calendar`，权限 `customerProfile:edit`，保留 `@Log` 操作审计。

请求 `CustomerOrderMealCalendarSaveDto` 示例：

```json
{
  "statsMonth": "2026-10",
  "expectedRevision": "查询返回的修订标记",
  "overrides": [
    { "date": "2026-10-03", "mealType": "LUNCH", "quantity": 0, "soupQuantity": null, "remark": "" },
    { "date": "2026-10-04", "mealType": "DINNER", "quantity": 2, "soupQuantity": 1, "remark": "" }
  ]
}
```

- orderId 只来自 URL；customerId 从订单读取，不接收任意客户或其他订单作为写入身份。覆盖项不携带另一个 orderId，复用 DTO 时也必须严格校验一致。
- `statsMonth/expectedRevision/overrides` 必填；`overrides` 是当前订单当前月覆盖记录的完整快照。空数组表示恢复本订单本月全部可编辑覆盖为默认；缺失/null 是非法请求，不得解释为清空。
- 目标数量为非负整数；0 表示订单停餐；正数表示目标份数。0 份的 `soupQuantity` 归一化为 null；正数含汤数为 null 或 `[0,quantity]` 整数。
- 同日同餐次重复、越月、订单有效期外、开始餐次之前、订单不支持的餐次一律拒绝整次请求，不静默忽略、不自动改绑其他订单。
- 客户统一停餐格只允许原样保留持久化覆盖，任何更改/移除均拒绝，前端标注需到客户档案恢复。保存不调用 `profileMapper.updateById` 写排除日期。
- 暂停、未指定餐次、已取消/完成/退餐订单不可保存。打开后状态变更也在提交时重新校验。
- 成功返回 `orderId/statsMonth/revision/deletedUnverifiedPlanCount`；前端关闭日历并刷新列表及预警。发生修订冲突返回现有异常体系支持的 HTTP 409，保留草稿并提示重新加载，不强行覆盖。
- 同请求无变化保存幂等，不能新增重复记录或误删其他范围记录。

旧 `PUT /mealStats/scheduleAdjustments` 在客户端全部切换后删除，同时删除 `quantityMode` 和按客户解释请求的分支。不得接受旧请求并推断“默认订单”。

## 3. 生效规则和恢复规则

优先级：订单状态/有效期/餐次可用性 → 客户统一停餐 → 当前订单覆盖项 → 订单基础排餐；最后仍受各餐池购买额度和已核销/有效已排餐约束。

| 情况 | 当前订单结果 | 同客户其他订单 |
|---|---|---|
| 客户统一停餐 | 0 份，标明统一停餐，不可在本页恢复 | 同样受统一停餐限制 |
| 当前订单覆盖 0 | 0 份，标明订单停餐 | 不变 |
| 当前订单覆盖正数 | 按数量及含汤数排餐，需通过餐数校验 | 不变 |
| 恢复当前订单默认 | 移除当前格覆盖，按当时的默认计划重算 | 不变 |
| 基础计划外人工新增后恢复默认 | 删除该格覆盖，通常回到未排餐；以重算结果为准 | 不变 |

“恢复默认”与“设置为 1 份”是不同动作。保存必须保留未编辑格的覆盖，即使其当前值恰好等于默认量；只有明确恢复默认才删除覆盖。

客户统一停餐历史值继续留在 `customer_profile.excluded_dates`；客户档案仍能统一设置/恢复。客户统一停餐解除后，之前被遮蔽的订单覆盖重新生效。不能把旧客户停餐批量搬迁为各订单停餐，也不新增自动迁移逻辑。

## 4. 数量预算、生成与清理

- 早餐独立餐池，午晚共享餐池。继续从订单开始日期累计分配，午晚先扣导入历史核销基数；0 份不占用购买餐数，释放出的计划额度可按现有规则顺延到后续日期。
- 保存校验沿用既有“历史已超额时允许不增加本月计划总量的减少/排除”规则；新增/增量仍必须在当前可用餐数内。预算需考虑其他月份已核销、成功已排未核销份，不能只看订单总剩余数。
- 修改当前月可能影响后续月份的计算结果，但本次保存不更新/删除其他月份的覆盖记录或已生成排餐；其他月份已经占用的份数必须纳入当前可用量，不能重复分配。
- 任何目标量不得低于当前格已核销量；取消有已核销份的格必须失败，不能把已核销记录删掉。
- 减少目标份数时复用按订单减份服务，优先删除失败份、再删除较大份序的未核销结果；删除关联菜品明细/手工换菜记录，刷新实际受影响排餐计划汇总。
- 保存增加/恢复只保存计划，不自动生成菜品；后续正常排餐生成读取同一覆盖规则。
- 遍历保存前后当前月所有受影响格，包含显式取消格和因数量累计重新分配而变成 0 的格。不能因 `excluded=true` 跳过清理。
- 生成侧不得把 0 覆盖视为缺省 1，不把 0 覆盖当作人工新增候选；也不得因存在 0 覆盖报错导致整个日期餐次生成失败。

## 5. 并发、查询范围和权限

- revision 包含当前订单身份、月份、影响排餐的订单字段、该订单截至月底的历史覆盖、相关客户统一停餐和用于数量校验的排餐/核销状态；以稳定排序生成。其他订单的覆盖变化不造成无意义冲突。
- 保存事务内锁定、重读并验证订单/覆盖/排餐事实，校验成功后再写入。复用已有订单锁和计划锁；实施时核对现有核销“排餐记录→订单”及行内编辑“订单→客户”锁顺序，避免新增相反锁序。多日期餐次按稳定次序处理。
- 日历清理增加未核销条件更新并检查实际影响行数；若核销并发抢先完成则整次保存回滚。沿用核销的 `markVerifiedIfPending` 中 deleted=0 条件，保证先删除时后续核销也会失败。revision 不能代替最终数据库约束。
- 为日历新增按 `order_id + 日期范围` 查询进度和覆盖的 Mapper 方法；按订单＋月份同步删除。禁止将“查全客户后前端过滤”作为隔离实现。
- GET/PUT 权限沿用本页面原有 `customerProfile:list/edit`，不复用金额权限，不给只读用户编辑按钮；后端始终独立校验。

## 6. 关键示例

客户 C 同时有订单 A、B，两单在 10 月 3 日都有午餐，10 月 4 日客户统一停餐。

1. 在 A 的日历把 10 月 3 日午餐改为 0：只保存 A 的 0 覆盖；只清理 A 的对应未核销份；B 的日历、覆盖、排餐全部不变。
2. 恢复 A 该格默认：删除 A 当前格覆盖；A 按默认计划重算，B 不变。
3. 10 月 4 日在 A/B 均显示“客户统一停餐”，不能从任一订单日历解除；去客户档案解除后，各订单自己的覆盖仍然生效。
4. 保存 A 的 10 月不能软删除 B 的 10 月，也不能软删除 A 的 9 月/11 月记录。
5. A 的某格已经核销 1 份，改 0 或改成低于 1 的目标量应失败且无部分写入；B 同日有核销不应阻止 A 无核销格的合法取消。
