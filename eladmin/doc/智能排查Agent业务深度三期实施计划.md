# 智能排查 Agent 业务深度三期实施计划

## 1. 背景与目标

当前智能排查 Agent 已完成排餐未生成诊断的基础能力：聊天式槽位收集、规则加载、只读工具调用、结构化诊断结果、兜底返回、诊断链路与工具摘要展示。

下一阶段不建议直接扩展为通用客服机器人，而是继续围绕当前业务做深，建设为“排餐 / 订单 / 客户异常诊断专家”。

总体目标：

- 提升排餐诊断准确率和可解释性
- 扩展订单、客户、核销、退餐等相关异常诊断能力
- 建立真实案例评测、规则治理、客服反馈闭环
- 在不允许 AI 直接写库的前提下，提供可人工确认的动作草稿

## 2. 总体原则

- AI 只做诊断、建议和草稿生成，不直接修改业务数据
- 业务数据必须通过主系统只读工具获取，不允许 AI 直连数据库
- 诊断结论必须有规则 ID、证据字段和建议动作
- 规则文件、提示词策略、工具定义和评测用例要同步演进
- 所有高风险动作必须由客服人工确认后走主系统正式接口
- 先保证排餐诊断质量，再逐步扩展到订单、客户、核销等异常

## 3. 三期路线

### 第一期：诊断可信度建设

目标：让当前排餐未生成诊断可评测、可回归、可治理。

#### 3.1 建真实案例评测集

建设内容：

- 新增 `agent-service/src/test/resources/evaluation/meal-plan-diagnosis-cases.yaml`
- 收集 30-100 个真实客服排查案例
- 每个案例包含：
  - 用户输入
  - 结构化槽位：客户、日期、餐次
  - 期望原因码
  - 期望证据字段
  - 期望建议动作
  - 是否允许 fallback
  - 备注和人工结论

建议用例分类：

- 客户停送或排除日期导致未排餐
- 订单未生效或已结束
- 剩余餐数不足
- 餐次类型不匹配
- 排餐记录生成失败
- 候选菜不足
- 套餐规格不完整
- 客户不存在或编号错误
- 数据缺失，需要人工核对

#### 3.2 建评测执行器

建设内容：

- 新增 Agent 评测测试类，例如 `MealPlanDiagnosisEvaluationTest`
- 支持 mock 工具返回，避免测试依赖真实数据库和真实模型
- 支持对模型输出做结构校验和业务断言
- 断言维度：
  - 是否命中期望 `reason.code`
  - 是否包含期望 `ruleIds`
  - 是否包含期望证据字段
  - 是否包含建议动作
  - fallback 是否符合预期

#### 3.3 强化规则中心

建设内容：

- 统一 `agent-service/rules/meal-plan/*.yaml` 规则结构
- 每条规则至少包含：
  - `ruleId`
  - `reasonCode`
  - `version`
  - `title`
  - `description`
  - `triggerConditions`
  - `requiredTools`
  - `evidenceFields`
  - `nextActions`
  - `severity`
  - `owner`
- 新增规则文件校验测试：
  - ruleId 不允许重复
  - reasonCode 不允许为空
  - requiredTools 必须是已注册工具
  - evidenceFields 不允许为空
  - nextActions 不允许为空
  - owner 不允许为空

#### 3.4 强化证据约束

建设内容：

- `DiagnosisResultValidator` 增加规则一致性校验
- 禁止模型返回不存在的 `ruleId`
- `evidence.label` 必须命中规则定义的 `evidenceFields`
- 非 fallback 结果必须至少有一个真实业务证据字段
- 兜底结果必须明确 `fallbackReason`

建议证据字段示例：

- `profile.id`
- `profile.customerCode`
- `profile.excludedDates`
- `order.status`
- `order.startDate`
- `order.endDate`
- `order.breakfastCount`
- `order.lunchDinnerCount`
- `order.remainingCount`
- `order.mealType`
- `mealPlan.status`
- `mealPlan.failReason`
- `candidateDishStats.availableCount`

#### 3.5 一期验收标准

- 至少 30 个真实案例进入评测集
- 评测集可在单元测试或集成测试中稳定执行
- 规则文件有自动校验
- 模型输出不存在未知 ruleId
- 非 fallback 诊断结果必须包含规则和字段级证据
- 当前排餐未生成诊断主流程不回退

## 4. 第二期：业务工具扩展与诊断覆盖

目标：补齐排餐、订单、客户、核销、退餐相关只读工具，让 Agent 能解释更复杂的业务异常。

#### 4.1 扩充只读工具

新增工具建议：

- `getCustomerExcludeDates`
  - 查询客户停送、排除日期、排除餐次
- `getOrderMealBalance`
  - 查询订单有效期、餐次类型、剩余早餐数、剩余午晚餐数
- `getPackageSpec`
  - 查询父套餐、子套餐、餐品线、菜品规格
- `getDishCandidateDetail`
  - 查询候选菜池、套餐过滤后数量、过敏忌口过滤结果
- `listVerificationLogs`
  - 查询指定客户、订单、日期范围内核销记录
- `listMealRefunds`
  - 查询退餐、停餐、退款相关记录
- `getMealPlanGenerationSnapshot`
  - 查询排餐生成日志、失败原因快照、生成批次信息

#### 4.2 主系统内部接口改造

建设内容：

- 在 `eladmin-system` 的内部 Agent 接口中增加对应只读接口
- 所有内部接口继续使用 `X-Agent-Internal-Token`
- 接口返回 DTO 要面向诊断场景，避免返回过宽对象
- 敏感字段不返回给 agent-service
- 日志只记录 requestId、结果条数、耗时和错误类型

#### 4.3 扩展规则和原因码

新增原因码建议：

- `CUSTOMER_EXCLUDE_DATE_HIT`
- `ORDER_NOT_EFFECTIVE`
- `ORDER_EXPIRED`
- `ORDER_MEAL_TYPE_MISMATCH`
- `ORDER_REMAINING_COUNT_NOT_ENOUGH`
- `PACKAGE_SPEC_MISSING`
- `CANDIDATE_DISH_EMPTY`
- `DISH_FILTERED_BY_ALLERGY`
- `MEAL_PLAN_GENERATION_FAILED`
- `MEAL_PLAN_ALREADY_EXISTS_BUT_CUSTOMER_MISSING`
- `REFUND_OR_STOP_MEAL_HIT`
- `VERIFICATION_CONSUMED_COUNT`

#### 4.4 前端诊断结果增强

建设内容：

- 结果区增加“证据字段”展示
- 工具摘要支持按工具类型分组
- 原因列表支持按高 / 中 / 低优先级排序
- 支持从诊断结果跳转客户档案、订单详情、排餐详情
- fallback 时展示人工核对清单

#### 4.5 二期验收标准

- 新增只读工具不少于 5 个
- 新增原因码不少于 8 个
- 真实案例评测集扩展到 60 个以上
- 工具调用失败时能稳定 fallback
- 复杂异常至少能覆盖客户、订单、套餐、候选菜、核销、退餐 6 类信息
- 前端能展示工具摘要、证据字段和跳转入口

## 5. 第三期：人工确认动作草稿与反馈闭环

目标：让 Agent 从“解释原因”升级为“辅助客服处理”，但仍然保持人工确认和主系统执行。

#### 5.1 人工确认动作草稿

建设内容：

- 新增 `actionDrafts` 结构化字段
- Agent 只生成草稿，不执行动作
- 每个动作草稿包含：
  - `actionCode`
  - `title`
  - `description`
  - `riskLevel`
  - `targetType`
  - `targetId`
  - `beforeSnapshot`
  - `afterPreview`
  - `requiredPermission`
  - `confirmApi`

动作草稿类型建议：

- `RESUME_CUSTOMER_DELIVERY`
  - 建议恢复客户配送
- `ADJUST_ORDER_EFFECTIVE_DATE`
  - 建议调整订单有效期
- `RECALCULATE_ORDER_BALANCE`
  - 建议重新计算订单剩余餐数
- `SUPPLEMENT_DISH_CANDIDATES`
  - 建议补充候选菜
- `REGENERATE_MEAL_PLAN`
  - 建议重新生成排餐
- `CREATE_MANUAL_RECHECK_TASK`
  - 建议创建人工复核任务

#### 5.2 动作确认工作流

建设内容：

- 前端展示动作草稿和风险提示
- 客服点击确认前展示差异预览
- 后端校验权限、参数、业务状态和幂等键
- 主系统正式执行动作
- 执行结果写入审计日志
- Agent 会话记录动作建议和人工确认结果

关键限制：

- AI 不允许直接调用写接口
- 写操作必须通过主系统权限校验
- 高风险动作必须二次确认
- 动作执行失败必须返回明确失败原因

#### 5.3 客服反馈闭环

建设内容：

- 诊断结果增加反馈按钮：
  - 采纳
  - 不采纳
  - 部分正确
  - 真实原因
  - 备注
- 新增反馈表，例如 `agent_diagnosis_feedback`
- 反馈字段建议：
  - `request_id`
  - `session_id`
  - `customer_id`
  - `record_date`
  - `meal_type`
  - `predicted_reason_codes`
  - `accepted`
  - `actual_reason_code`
  - `comment`
  - `operator`
  - `create_time`

#### 5.4 运营分析

建设内容：

- 新增 Agent 运营看板
- 指标建议：
  - 诊断次数
  - fallback 率
  - 客服采纳率
  - 原因码分布
  - 工具失败率
  - 平均诊断耗时
  - 高频未知问题
  - 动作草稿确认率

#### 5.5 三期验收标准

- 支持至少 3 类人工确认动作草稿
- 所有动作都必须人工确认后执行
- 诊断反馈可记录、可查询、可统计
- 真实案例评测集扩展到 100 个以上
- 形成原因码命中率、fallback 率、采纳率等运营指标
- 高频错误和规则缺口能反向进入规则维护流程

## 6. 里程碑建议

### 第一期建议周期：2-3 周

- 第 1 周：案例格式、评测执行器、首批 30 个案例
- 第 2 周：规则结构升级、规则校验、证据约束
- 第 3 周：评测稳定、修复主流程回归问题

### 第二期建议周期：3-5 周

- 第 1 周：设计新增工具 DTO 和内部接口
- 第 2-3 周：实现客户、订单、套餐、候选菜、核销、退餐工具
- 第 4 周：扩展规则和前端证据展示
- 第 5 周：补充评测案例和回归

### 第三期建议周期：4-6 周

- 第 1-2 周：动作草稿结构和前端展示
- 第 3 周：主系统动作确认接口和审计
- 第 4 周：反馈表和反馈入口
- 第 5-6 周：运营看板、数据统计、评测补齐

## 7. 风险与控制

### 7.1 模型幻觉风险

控制方式：

- 强制 ruleId 存在性校验
- 强制 evidence 字段白名单校验
- 工具失败或证据不足必须 fallback
- 真实案例评测集持续回归

### 7.2 业务误操作风险

控制方式：

- AI 只生成动作草稿
- 写操作只能由主系统执行
- 客服必须人工确认
- 高风险动作二次确认
- 所有动作记录审计日志

### 7.3 规则漂移风险

控制方式：

- 规则文件结构化
- 规则变更必须跑评测集
- 诊断代码变更但规则未变更时沿用现有规则同步检查
- 规则 owner 明确到人或团队

### 7.4 数据过宽和隐私风险

控制方式：

- 工具 DTO 面向诊断场景裁剪字段
- 日志只记录摘要，不记录敏感原始数据
- 内部接口继续使用 token 校验
- 前端只展示客服排查必要字段

## 8. 推荐优先级

建议优先启动第一期，原因是当前最缺的不是更多模型能力，而是评测和约束。

第一期完成后，Agent 的每次升级都可以被验证；否则第二期直接扩工具和规则，容易出现诊断范围变大但准确性不可控的问题。

推荐执行顺序：

1. 真实案例评测集
2. 规则结构升级
3. 证据字段强校验
4. 扩充只读工具
5. 动作草稿
6. 客服反馈闭环
