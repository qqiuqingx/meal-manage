# 智能排查Agent服务设计方案

## 1. 背景与目标

当前系统的客服排查工作，主要依赖人工逐步核对客户档案、订单、排餐计划、忌口和菜单配置。对于“某客户为什么没有生成排餐计划”这类问题，排查路径长、规则分散、解释成本高。

本方案目标是新增一个独立的 `agent-service`，为后台客服提供“排餐未生成原因诊断”能力，并通过现有后台集成给客服使用。

第一阶段目标只聚焦一件事：

- 输入客户、日期、餐次
- 输出可能原因、证据、建议排查顺序

第一阶段明确不做：

- 自动修复订单或排餐数据
- 自主修改数据库
- 面向外部客户的聊天机器人
- 任意自然语言查全库

---

## 2. 总体方案

采用“独立 Agent 服务 + 现有业务系统集成”的方案。

### 2.1 方案结论

- 现有 `eladmin-system` 保持业务主系统定位不变
- 新增独立 `agent-service` 作为诊断编排层
- 客服仍然通过现有 `eladmin-web` 进入“智能排查助手”页面
- 第一阶段采用“结构化规则 + 业务上下文 + AI 直接分析”的方案

### 2.2 选择该方案的原因

- 当前功能不着急上线，允许优先做可扩展架构
- 现有系统基线较老，直接在主系统内引入 Agent 生态会增加改造成本
- 独立服务可以使用更新的 JDK 与 AI 依赖，而不影响现有主业务系统
- 后续可从“排餐诊断”逐步扩展到“订单排查”“客服知识问答”“运营助手”

---

## 3. 系统边界

### 3.1 eladmin-system 职责

- 继续作为客户、订单、排餐、忌口、套餐等业务数据的唯一真相源
- 提供内部诊断所需的数据查询接口
- 负责统一登录、权限、菜单、审计日志
- 负责后台页面入口与基础交互

### 3.2 agent-service 职责

- 接收诊断请求
- 拉取诊断所需业务上下文
- 加载当前生效的 rule registry
- 组装“用户问题 + 规则 + 业务数据”的提示词
- 调用大模型直接分析原因、证据与建议
- 校验 AI 输出结构，并在失败时返回兜底结果

### 3.3 前端职责

- 在现有后台增加“智能排查助手”菜单
- 提供输入区、诊断结果区、证据展开区
- 支持跳转客户档案、订单详情、排餐记录页

---

## 4. 整体架构

```text
eladmin-web
  -> eladmin-system
      -> agent-service
          -> 诊断编排服务
          -> rule registry
          -> prompt builder
          -> LLM client
          -> result validator
      -> 内部诊断数据接口
          -> 客户数据
          -> 订单数据
          -> 排餐数据
          -> 菜单候选数据
```

### 4.1 调用原则

- 客服页面优先调用 `eladmin-system`
- `eladmin-system` 调用 `agent-service`
- `agent-service` 再调用 `eladmin-system` 暴露的内部诊断接口取数

### 4.2 为什么不建议第一阶段直接查主库

- 主系统已有大量业务语义，直接查库容易复制一套规则
- 订单生效、排餐模式、排除日期等逻辑后续可能变化
- 通过内部接口取数，后续更容易保持规则口径一致

### 4.3 第一阶段可接受的折中

如果为了验证速度，`agent-service` 也可以在第一阶段用只读账号直连数据库，但要满足以下约束：

- 只读权限
- 只允许访问诊断相关表
- 所有核心业务判断仍优先依赖业务接口返回的标准字段

建议中长期仍收敛到“内部接口取数”模式。

---

## 5. 技术选型建议

### 5.1 eladmin-system

- 保持现状
- JDK 17
- Spring Boot 2.7.18

### 5.2 agent-service

建议使用：

- JDK 17
- Spring Boot 3.5.x
- Spring AI 1.1.6

理由：

- Java 团队接手成本低
- 方便接入后续 AI SDK、流式响应、HTTP 客户端能力
- 与现有主系统分离，升级风险可控
- 即使主系统已升级到 JDK 17，独立服务仍然可以隔离 AI 依赖、发布节奏和运行风险

### 5.3 AI 接入策略

第一阶段建议采用“AI 直接分析 + 结构化输出校验”：

- `agent-service` 不再把原因判断硬编码在多个分析器里
- 业务规则以 rule registry 形式维护，作为 AI 的真相源之一
- `agent-service` 负责把“规则 + 数据 + 用户问题”发给 AI
- AI 必须输出固定 JSON 结构，便于前端展示和审计
- 模型失败或输出不合法时，回退为兜底结构化结果

统一抽象建议为：

- `ChatClient`
- `RuleRegistryLoader`
- `DiagnosisPromptBuilder`
- `DiagnosisAiClient`
- `DiagnosisResultValidator`
- `AgentToolRegistry`
- `AgentAdvisorChain`

后续可接任意模型供应商，不把诊断链路绑定到某个厂商 SDK。

### 5.4 Spring AI 能力分层

建议 `agent-service` 直接基于 Spring AI 构建，避免后续再次重构为 Agent 形态。

- `ChatClient`
  - 作为统一模型调用入口
- `Structured Output`
  - 要求模型输出固定 JSON 结构，映射到 `DiagnosisResponse`
- `Tool Calling`
  - 将只读查询和未来的写操作包装成工具，而不是让模型直接访问数据库
- `Advisors`
  - 用于统一注入审计、提示词增强、权限上下文、兜底策略
- `ChatMemory`
  - 用于支持多轮补充问题、后续的客服会话连续排查

### 5.5 Spring AI 版本策略

`agent-service` 的 AI 依赖需要独立版本管理，不跟随 `eladmin-system` 的依赖节奏。

建议策略：

- Spring Boot 使用 `3.5.x`
- Spring AI 使用 `spring-ai-bom` 管理依赖版本
- Spring AI 使用 `1.1.6`
- 第一阶段只使用 GA 或 patch 版本，不使用 milestone 版本
- 模型供应商 starter 通过配置切换，业务代码只依赖内部 `DiagnosisAiClient` 抽象

截至 2026-05-18，Spring AI 官方文档说明 Spring AI 支持 Spring Boot 3.4.x 和 3.5.x，当前稳定文档版本为 Spring AI 1.1.6。Spring AI 已提供 `ChatClient`、结构化输出、Tool Calling、Advisors、ChatMemory 等能力。

---

## 6. agent-service 模块设计

建议目录结构如下：

```text
agent-service
├── src/main/java/com/xxx/agent
│   ├── controller
│   ├── service
│   ├── orchestrator
│   ├── client
│   ├── prompt
│   ├── rule
│   ├── tool
│   ├── advisor
│   ├── memory
│   ├── validator
│   ├── domain
│   │   ├── dto
│   │   ├── model
│   │   └── enum
│   └── config
├── rules
└── src/main/resources
```

各层职责如下：

- `controller`
  - 对外提供诊断接口
- `service`
  - 面向控制器的应用服务入口
- `orchestrator`
  - 负责编排取数、规则加载、提示词构建、AI 调用和结果校验
- `client`
  - 调用 `eladmin-system` 内部接口和大模型客户端
- `prompt`
  - 负责拼装提示词与结构化输出协议
- `rule`
  - 负责加载和管理 rule registry
- `tool`
  - 负责注册 Spring AI tool calling 所需的查询工具和写操作工具
- `advisor`
  - 负责统一注入审计、权限、兜底和模型策略
- `memory`
  - 负责会话记忆和多轮问题承接
- `validator`
  - 负责校验 AI 输出结构和字段完整性
- `domain`
  - 定义诊断请求、诊断结果、证据项等领域模型

---

## 7. 诊断流程设计

标准诊断流程如下：

### 7.1 输入

- 客户ID或客户编号
- 诊断日期
- 餐次

### 7.2 处理流程

1. 定位客户
2. 拉取客户档案、订单、排餐、候选菜等诊断上下文
3. 加载当前生效的 rule registry
4. 组装“用户问题 + 规则 + 数据”的提示词
5. 通过 Spring AI `ChatClient` 调用模型输出结构化诊断结果
6. 校验返回 JSON 的字段完整性和可追溯性
7. 输出结果；如 AI 失败则返回兜底结构化结果

### 7.3 输出原则

- 不直接给出“唯一结论”
- 输出“高概率原因 + 证据 + 人工确认建议”
- 每一项判断必须可追溯到字段、ruleId 或版本号

---

## 8. 第一阶段 rule registry 清单

第一阶段建议最少维护以下 9 类规则项，由 `agent-service` 统一加载后交给 AI 使用。

### 8.1 客户不存在规则

用于判断客户是否存在、是否被误输入。

证据示例：

- customerId 查询为空
- customerCode 未命中

### 8.2 订单缺失规则

用于判断客户在目标日期附近是否存在可用订单。

证据示例：

- 客户无进行中订单
- 所有订单均非 `status=1`

### 8.3 订单生效规则

用于判断订单在目标日期和餐次是否满足生效条件。

重点规则：

- `start_date <= recordDate <= end_date`
- `meal_type` 是否包含当前餐次
- `start_meal_type` 是否允许目标日期当天该餐次生效

证据示例：

- 订单开始日期晚于目标日期
- 订单开始当天仅晚餐生效，但当前查询的是午餐

### 8.4 订单剩余餐数规则

用于判断是否仍有剩余可排餐数。

重点字段：

- `remaining_count`
- 已排餐数量
- 已核销量
- 早餐与午晚餐餐数池差异

证据示例：

- `remaining_count=0`
- 对应餐次已达到订单上限

### 8.5 排餐模式规则

用于判断 `schedule_mode` 是否允许该日期配送。

重点规则：

- `DAILY`
- `WEEKDAY`
- `WEEKEND`
- `SCHEDULE`

证据示例：

- 当前为周六，但订单为 `WEEKDAY`
- `delivery_dates` 中不包含该日期或该餐次

### 8.6 客户排除日期规则

用于判断客户是否明确配置了该日期和餐次不配送。

重点字段：

- `customer_profile.exclude_dates`

证据示例：

- `exclude_dates` 中包含 `2026-05-17 + LUNCH`

### 8.7 候选菜池规则

用于判断当天菜单和套餐候选池是否为空。

重点来源：

- `meal_schedule_plan`
- 父套餐筛选
- 菜品启用状态

证据示例：

- 当天该餐次无菜单候选
- 父套餐过滤后无可选菜

### 8.8 过滤后无菜可排规则

用于判断候选菜经过过敏、排除菜、替换规则等处理后是否被清空。

重点来源：

- `allergy_tags`
- `excluded_dish_ids`
- 菜品配料关联

证据示例：

- 过滤前 6 个候选菜，过滤后 0 个
- 命中过敏分类导致全部剔除

### 8.9 已生成失败规则

用于判断是否曾生成过该客户排餐，但结果失败。

重点来源：

- `meal_plan`
- `meal_plan_customer.status`
- `meal_plan_customer.fail_reason`

证据示例：

- 当天排餐主任务存在
- 客户记录存在且 `status=0`
- `fail_reason` 非空

### 8.10 rule registry 文件结构

规则文件不是普通说明文档，而是 `agent-service` 运行时会加载的结构化输入。

建议目录：

```text
agent-service/rules
└── meal-plan
    ├── customer.yaml
    ├── order.yaml
    ├── schedule.yaml
    ├── dish-candidate.yaml
    └── generated-result.yaml
```

单条规则建议字段：

```yaml
- ruleId: ORDER_START_MEAL_TYPE_NOT_EFFECTIVE
  version: 1
  scene: MEAL_PLAN_NOT_GENERATED
  title: 订单首日餐次未生效
  description: 目标日期等于订单开始日期时，需要判断 startMealType 是否允许当前餐次生效。
  requiredData:
    - orders.startDate
    - orders.endDate
    - orders.startMealType
    - request.recordDate
    - request.mealType
  decisionHints:
    - 如果 recordDate 等于 startDate，且 mealType 早于 startMealType，则认为当前餐次未生效。
    - 如果 recordDate 晚于 startDate，则不再受 startMealType 限制。
  evidenceFields:
    - orders.orderId
    - orders.startDate
    - orders.startMealType
  severity: HIGH
  owner: meal-plan
```

### 8.11 rule registry 维护原则

为了减少后期维护成本，规则文件应遵循以下原则：

- 不重复写业务代码里的完整实现，只描述 AI 诊断所需的业务口径、关键字段、判断提示和证据字段
- 每条规则必须有稳定 `ruleId`
- 每次规则含义变化必须递增 `version`
- 每条规则必须声明 `requiredData`，用于倒逼上下文接口补齐数据
- AI 输出的每个原因必须引用至少一个 `ruleId` 或明确的数据字段
- 规则文件作为运行时输入，不再依赖散落的人工说明文档

### 8.12 防止代码和规则漂移

业务规则如果只写在文档里，后续代码变更但文档未同步，AI 就会拿到过期规则。因此本项目不把普通文档作为规则真相源，而采用以下约束：

- `agent-service/rules/` 是 AI 诊断规则真相源
- 与排餐诊断相关的代码变更，需要同步检查规则文件是否需要变更
- 如果确认规则无需变化，需要在提交信息或变更说明中明确写明 `rules-no-change`
- 部署脚本检测到诊断代码变更但规则文件未变更，且提交信息没有 `rules-no-change` 时，应拒绝部署
- 单测需要覆盖规则文件可加载、`ruleId` 唯一、`requiredData` 非空、版本号合法
- 诊断日志需要记录本次使用的规则文件版本摘要，便于追溯

建议优先在 `/Users/qqx/job/code/eladmin-mp/scripts/deploy-from-github.sh` 中增加轻量校验。后续如果接入 CI，再把同一套校验前移到 pull request 阶段。

---

## 9. 诊断结果模型

建议统一返回结构如下：

```json
{
  "requestId": "diag-20260517-001",
  "customerId": 1001,
  "customerName": "张三",
  "recordDate": "2026-05-17",
  "mealType": "LUNCH",
  "summary": "客户 1001 在 2026-05-17 午餐未生成排餐，最可能原因是命中排除日期。",
  "reasons": [
    {
      "code": "EXCLUDE_DATE_HIT",
      "title": "命中客户排除日期",
      "level": "HIGH",
      "description": "客户档案配置了该日午餐不配送。",
      "evidence": [
        {
          "label": "排除日期",
          "value": "2026-05-17"
        },
        {
          "label": "排除餐次",
          "value": "LUNCH"
        }
      ],
      "suggestion": "先核对客户是否临时请假或已手工登记停送。"
    }
  ]
}
```

### 9.1 结果字段说明

- `summary`
  - 给客服看的简短结论
- `reasons`
  - 可能原因列表
- `level`
  - 建议分为 `HIGH`、`MEDIUM`、`LOW`
- `evidence`
  - 字段级证据
- `suggestion`
  - 人工下一步核对建议

### 9.2 AI 输出约束

AI 必须输出可被服务端解析的固定结构，服务端不能直接把模型原文透传给前端。

最低约束：

- `summary` 不能为空
- `reasons` 可以为空，但不能为空值
- 每个 `reason.code` 必须稳定
- 每个 `reason.level` 只能是 `HIGH`、`MEDIUM`、`LOW`
- 每个 `reason` 至少包含一个 `evidence` 或一个 `ruleId`
- 不允许输出“已修复”“已修改数据”等实际未发生动作

校验失败时，`agent-service` 返回兜底结果：

- 摘要提示“AI 诊断结果不可用”
- 附带上下文是否获取成功、规则是否加载成功、模型是否超时
- 建议客服按固定清单人工核对

---

## 10. eladmin-system 需要提供的内部接口

建议不要让 `agent-service` 直接拼装太多零散接口，而是由 `eladmin-system` 提供面向诊断场景的聚合接口。

### 10.1 诊断上下文查询接口

建议新增：

- `POST /internal/agent/meal-plan-diagnosis/context`

输入：

- `customerId`
- `recordDate`
- `mealType`

输出建议包含：

- 客户基本信息
- 客户排除日期
- 客户过敏标签
- 排除菜品
- 目标日期相关订单
- 订单生效判断所需字段
- 排餐主记录
- 排餐客户记录
- 菜单候选统计信息

### 10.2 可选的细分接口

如果聚合接口暂时太重，也可以先拆成以下接口：

- 客户档案上下文接口
- 订单诊断上下文接口
- 排餐记录诊断接口
- 候选菜统计接口

但中长期建议仍回到聚合接口，避免 `agent-service` 端编排过多业务取数细节。

---

## 11. 页面方案

页面放在现有后台中，菜单名建议使用：

- `智能排查助手`

页面结构建议：

- 查询区
  - 客户编号/客户ID
  - 日期
  - 餐次
  - 开始分析按钮
- 结果区
  - 诊断摘要
  - 原因列表
  - 每条原因的证据明细
- 跳转区
  - 客户档案
  - 订单详情
  - 排餐记录

页面风格第一阶段以实用为主，不追求聊天式交互。

---

## 12. 安全与审计

### 12.1 权限控制

- 仅后台客服/运营角色可访问
- 使用现有菜单权限体系控制入口

### 12.2 审计日志

建议记录：

- 谁发起了诊断
- 诊断了哪个客户
- 查询日期和餐次
- 使用了哪版 rule registry
- 调用了哪个模型、是否命中兜底

### 12.3 数据安全

- `agent-service` 不允许写业务数据库
- 所有诊断接口默认为只读
- 模型提示词中避免无必要输出手机号等敏感字段
- 所有写操作必须通过 Spring AI tool calling 调用受控业务工具
- “新增客户”类动作必须采用“生成草稿 -> 人工确认 -> 正式提交”的两段式流程

### 12.4 未来 AI 新增客户的边界

未来如果扩展到“客户也通过 AI 来新增”，建议仍然沿用 Spring AI，但不能让模型直接写数据库。

推荐流程：

1. 客服或客户输入自然语言需求
2. AI 调用只读工具查询套餐、地址、配送规则等上下文
3. AI 生成 `CustomerCreateDraft`
4. 服务端校验草稿字段、权限、必填项和业务冲突
5. 前端展示草稿差异和风险提示
6. 人工确认后，由 `eladmin-system` 的正式业务接口创建客户
7. 审计日志记录 AI 原始输入、草稿、确认人和最终提交结果

这类能力应放在第二阶段之后实现，不与第一阶段排餐诊断混在一起。

---

## 13. 部署建议

### 13.1 第一阶段部署

- `eladmin-system` 保持原部署方式
- `agent-service` 单独部署为一个内部服务
- 仅内网访问

### 13.2 调用方式

- `eladmin-system -> agent-service` 使用 HTTP
- `agent-service -> eladmin-system` 使用 HTTP

### 13.3 可用性建议

- `agent-service` 超时不应影响主业务系统
- AI 调用超时时，直接回退为兜底结构化诊断结果

### 13.4 规则同步部署校验

当前项目尚未接入 CI，已有部署入口为：

- `/Users/qqx/job/code/eladmin-mp/scripts/deploy-from-github.sh`

建议先在该脚本中加入部署前校验：

- 检测本次变更是否包含 `agent-service/src/main/java/me/zhengjie/agent/analyzer/`
- 检测本次变更是否包含 `agent-service/src/main/java/me/zhengjie/agent/orchestrator/`
- 检测本次变更是否包含 `agent-service/src/main/java/me/zhengjie/agent/context/`
- 检测本次变更是否包含 `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/`
- 如果包含以上诊断相关代码变更，但不包含 `agent-service/rules/` 变更，则读取提交信息
- 如果提交信息不包含 `rules-no-change`，部署失败并提示补充规则文件或显式说明无需变更

这样不能做到绝对强制，但能在没有 CI 的前提下先建立“改诊断代码必须想一遍规则”的工程习惯。

---

## 14. 分期实施建议

### 第一期：排餐未生成诊断

- 建立 `agent-service`
- 打通 `eladmin-system` 内部诊断上下文接口
- 建立 rule registry 并接入 AI 直接分析
- 后台新增“智能排查助手”页面
- 输出结构化诊断结果

### 第二期：AI 诊断增强

- 优化提示词与结构化输出质量
- 增加多轮追问与补充字段
- 增加诊断建议模板
- 引入 ChatMemory 和 Advisors，支持连续排查会话

### 第三期：扩展更多排查场景

- 订单异常排查
- 核销异常排查
- 退餐异常排查
- 客服知识问答
- AI 辅助新增客户、编辑客户、发起审批

---

## 15. 风险与应对

### 15.1 风险：规则口径不一致

应对：

- 核心业务判断尽量收敛到 `eladmin-system` 的聚合接口
- `agent-service/rules/` 只描述 AI 诊断所需口径，不复制主系统完整业务实现
- 部署脚本校验诊断代码和规则文件是否同步变更

### 15.2 风险：Agent 结果不可信

应对：

- 规则以 rule registry 为真相源，不直接依赖手工文档
- 所有结论必须附证据、ruleId 或字段引用
- 对 AI 输出执行结构校验和兜底降级
- 诊断页面明确展示“AI 建议，需要人工确认”

### 15.3 风险：范围膨胀

应对：

- 第一阶段只做“未生成排餐诊断”
- 不把知识问答、自动修复、多轮对话一起塞进去
- “AI 新增客户”只预留工具调用架构，不在第一期实现写操作

---

## 16. 最终建议

本项目建议采用：

- `eladmin-system` 继续做业务底座
- 新建独立 `agent-service`
- 第一阶段只做“排餐未生成原因诊断”
- 技术路线采用“rule registry + 业务上下文 + AI 直接分析”的方案

这样既不会强行升级老系统，也给后续 AI 能力扩展留出了清晰空间。
