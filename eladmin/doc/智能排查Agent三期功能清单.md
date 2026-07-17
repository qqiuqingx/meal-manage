# 智能排查 Agent 三期功能清单

## 1. 功能定位

智能排查 Agent 已从“排餐未生成原因解释”扩展为“排餐 / 订单 / 客户异常诊断专家”。当前能力覆盖诊断、规则评测、只读业务工具、人工确认动作草稿、客服反馈闭环和运营统计。

核心边界:

- Agent 只做诊断、解释、建议和动作草稿生成，不直接写业务数据
- 写操作必须由主系统接口在客服人工确认后执行
- 高风险动作必须二次确认
- 诊断结论必须带规则 ID、字段级证据和建议动作
- 诊断过程通过只读工具获取主系统数据，不允许 Agent 直连数据库

## 2. 诊断与会话能力

当前支持聊天式排查排餐未生成问题。系统会自动收集客户、日期、餐次等槽位，并在信息不足时继续追问。

诊断结果包含:

- 诊断摘要
- 可信度
- 原因列表
- 规则 ID
- 字段级证据
- 建议动作
- fallback 标识和 fallback 原因
- 动作草稿列表

诊断结果会经过规则一致性校验:

- 禁止返回不存在的 `ruleId`
- 非 fallback 结果必须有规则和字段级证据
- 证据字段必须命中规则定义
- 工具失败或证据不足时稳定 fallback

## 3. 只读业务工具

Agent 通过主系统内部接口读取诊断数据，内部接口继续使用 `X-Agent-Internal-Token`。

已支持工具:

| 工具 | 作用 |
| --- | --- |
| `getCustomerExcludeDates` | 查询客户停送、排除日期和排除餐次 |
| `getOrderMealBalance` | 查询订单有效期、餐次类型和早餐 / 午晚餐剩余餐数 |
| `getPackageSpec` | 查询父套餐、子套餐、餐品线和菜品规格 |
| `getDishCandidateDetail` | 查询候选菜池、套餐过滤和过敏忌口过滤结果 |
| `listVerificationLogs` | 查询客户或订单核销记录 |
| `listMealRefunds` | 查询退餐、停餐和退款记录 |
| `getMealPlanGenerationSnapshot` | 查询排餐生成快照、失败原因和生成批次 |

当前可解释异常范围:

- 客户不存在或编号错误
- 客户停送、请假、排除日期命中
- 订单未生效、已结束、餐次不匹配
- 订单早餐或午晚餐剩余餐数不足
- 套餐规格缺失或套餐配置异常
- 候选菜不足或为空
- 过敏忌口过滤后无可用菜
- 核销记录已消耗餐数
- 退餐、停餐、退款影响排餐
- 排餐生成任务失败或已有排餐但客户缺失

## 4. 规则中心和评测

规则文件已结构化维护在 `agent-service/rules/meal-plan/`。

每条规则至少包含:

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

自动校验覆盖:

- `ruleId` 不允许重复
- `reasonCode` 不允许为空
- `requiredTools` 必须是已注册工具
- `evidenceFields` 不允许为空
- `nextActions` 不允许为空
- `owner` 不允许为空

评测集位置:

```text
agent-service/src/test/resources/evaluation/meal-plan-diagnosis-cases.yaml
```

当前评测集已扩展到 101 条，覆盖客户、订单、套餐、候选菜、核销、退餐、fallback、动作草稿、反馈闭环和运营统计等场景。

## 5. 人工确认动作草稿

诊断结果新增 `actionDrafts` 字段。动作草稿只表示建议，不表示已经执行。

动作草稿字段:

- `actionCode`: 动作码
- `title`: 动作标题
- `description`: 动作说明
- `riskLevel`: 风险等级
- `targetType`: 目标对象类型
- `targetId`: 目标对象 ID
- `beforeSnapshot`: 变更前快照
- `afterPreview`: 变更后预览
- `requiredPermission`: 所需权限
- `confirmApi`: 确认接口

已支持动作:

| 动作码 | 说明 | 执行方式 |
| --- | --- | --- |
| `CREATE_CUSTOMER_PROFILE_DRAFT` | 建议补建客户档案草稿 | 人工确认后登记审计 |
| `CREATE_CUSTOMER_ORDER_DRAFT` | 建议补建客户订单草稿 | 人工确认后登记审计 |
| `RESUME_CUSTOMER_DELIVERY` | 恢复客户指定日期餐次配送 | 人工确认后调用客户服务执行 |
| `ADJUST_ORDER_EFFECTIVE_DATE` | 调整订单有效期 | 高风险二次确认后调用订单服务执行 |
| `RECALCULATE_ORDER_BALANCE` | 重算订单餐数余额 | 高风险二次确认后调用订单服务执行 |
| `SUPPLEMENT_DISH_CANDIDATES` | 补充候选菜或套餐规格配置 | 人工确认后登记审计 |
| `REGENERATE_MEAL_PLAN` | 重新生成排餐 | 人工确认后调用排餐服务执行 |
| `CREATE_MANUAL_RECHECK_TASK` | 创建人工复核任务 | 人工确认后登记审计 |

## 6. 动作确认和审计

主系统确认接口:

```text
POST /api/agent/action-drafts/confirm
```

动作审计查询接口:

```text
GET /api/agent/action-drafts/audits
```

确认工作流:

1. 前端展示动作草稿、风险等级和差异预览
2. 客服点击确认
3. 高风险动作要求二次确认
4. 后端校验权限、参数和幂等键
5. 可执行动作调用主系统正式业务 Service
6. 执行结果写入 `agent_action_audit`
7. 前端按会话展示动作确认记录

权限控制:

- `customerProfile:add`
- `customerOrder:add`
- `customerProfile:edit`
- `customerOrder:edit`
- `dish:edit`
- `mealPlan:generate`
- `agentDiagnosis:confirm`

## 7. 客服反馈闭环

诊断结果区支持客服提交反馈:

- 采纳
- 不采纳
- 部分正确
- 真实原因码
- 备注

接口:

```text
POST /api/agent/feedback
GET /api/agent/feedback
GET /api/agent/feedback/stats
```

反馈数据会用于:

- 统计采纳率
- 统计真实原因分布
- 识别高频错误
- 沉淀规则缺口
- 反向补充规则和评测集

## 8. 运营统计

运营看板接口:

```text
GET /api/agent/operation/stats
```

当前指标:

- 诊断次数
- fallback 率
- 客服采纳率
- 原因码分布
- 工具失败率
- 平均诊断耗时
- 动作草稿确认率
- 真实原因分布
- 高频规则缺口

## 9. 规则缺口维护

规则缺口来源:

- 客服反馈为不采纳
- 客服反馈为部分正确
- 客服录入了当前规则库未知的真实原因码

接口:

```text
GET /api/agent/rule-gaps
PUT /api/agent/rule-gaps/{id}/status
```

状态流转:

- `OPEN`: 待处理
- `IN_PROGRESS`: 处理中，必须指定处理人
- `RESOLVED`: 已解决，必须填写规则、评测或发布证据
- `IGNORED`: 忽略

## 10. 前端入口

主要页面:

```text
eladmin-web/src/views/agent/diagnosis/index.vue
```

页面能力:

- 聊天式诊断
- 证据字段展示
- 工具摘要展示
- 原因列表按优先级展示
- 客户档案、订单详情、排餐详情跳转
- fallback 人工核对清单
- 动作草稿展示和人工确认
- 高风险动作二次确认
- 当前会话动作审计展示
- 诊断反馈提交
- 运营统计展示
- 规则缺口列表和状态维护

## 11. 数据库脚本

三期能力依赖 4 张新表，需要在目标库执行:

```text
eladmin/sql/agent_action_audit.sql
eladmin/sql/agent_diagnosis_feedback.sql
eladmin/sql/agent_diagnosis_metric.sql
eladmin/sql/agent_rule_gap.sql
```

表用途:

| 表 | 用途 |
| --- | --- |
| `agent_action_audit` | 动作草稿人工确认审计 |
| `agent_diagnosis_feedback` | 客服反馈记录 |
| `agent_diagnosis_metric` | 诊断运营指标 |
| `agent_rule_gap` | 规则缺口维护池 |

这 4 个脚本均使用 `CREATE TABLE IF NOT EXISTS`，当前三期提交没有要求修改已有业务表结构。

## 12. 验证范围

已验证内容:

- agent-service 诊断评测和动作草稿测试通过
- eladmin-system Agent 动作确认、运营统计、规则缺口相关测试通过
- 前端诊断页面单元测试通过
- eladmin-system 编译通过
- eladmin-web lint 通过

重点测试命令:

```bash
cd agent-service
mvn -Dtest=MealPlanDiagnosisEvaluationTest,MealPlanDiagnosisEvaluationCasesTest,RuleBasedDiagnosisActionDraftServiceTest test
```

```bash
cd eladmin
mvn -pl eladmin-system -am -DskipTests=false -DfailIfNoTests=false -Dtest=AgentActionDraftControllerTest,AgentActionConfirmServiceImplTest,AgentOperationStatsServiceImplTest,AgentRuleGapServiceImplTest test
```

```bash
cd eladmin-web
npm run test:unit -- tests/unit/views/agent/diagnosis/index.spec.js --runInBand
```
