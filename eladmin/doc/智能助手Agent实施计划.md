# 智能助手 Agent 实施计划

## 一、目标与约束

### 1.1 目标

建设一个可在现有 ELADMIN 体系内落地的业务智能助手，首期围绕客户档案场景，后续可平滑扩展到：

- 客户订单
- 饮食限制
- 排餐计划
- 其他受权限控制的业务操作

### 1.2 硬约束

1. **保留流式返回**
   前端需要实时展示模型输出、工具调用状态和执行结果。

2. **严格受现有权限体系约束**
   Agent 不得绕过现有权限模型，不得因为调用 Service 而跳过权限校验。

3. **复用现有业务模型**
   Agent 工具层必须围绕现有 `Controller / Service / DTO / QueryCriteria / permission code` 设计，不重新发明一套简化业务模型。

4. **支持后续多业务域扩展**
   设计必须从单场景能力演进为统一的“受控业务编排层”，避免后续接入订单、排餐计划时推翻重做。

---

## 二、技术方案概述

### 2.1 技术选型

| 组件 | 选型 | 说明 |
|------|------|------|
| AI 框架 | LangChain4j 0.35.x | Java 版 LangChain |
| LLM | MiniMax API | 兼容 OpenAI 风格接口 |
| 对话存储 | Redis | 多轮会话上下文、待确认动作状态 |
| 流式传输 | `fetch` + `ReadableStream` | 保留 `Authorization` 请求头 |
| 服务端流式协议 | SSE 格式文本流 或 NDJSON | 推荐统一为 `text/event-stream` |
| 审计 | 复用现有日志体系 + Agent 专用审计记录 | 区分“模型建议”和“真实执行” |

### 2.2 为什么不使用原生 EventSource

当前前端认证通过 `Authorization` 请求头传递 JWT，原生 `EventSource` 无法自定义该请求头，因此不能直接复用现有登录态。

因此流式方案调整为：

- 前端：`fetch('/api/agent/chat/stream', { method: 'POST', headers: { Authorization } })`
- 后端：返回流式响应
- 前端：按 chunk 增量解析事件并渲染

这样既保留流式体验，也不破坏当前安全模型。

### 2.3 系统架构

```text
┌──────────────────────────────────────────────────────────────────┐
│                       Vue2 Chat 前端                              │
│                                                                  │
│  - fetch 流式请求                                                │
│  - 消息渲染                                                      │
│  - 工具结果卡片                                                  │
│  - 待确认动作弹窗                                                │
└──────────────────────────────────────────────────────────────────┘
                               ↓ POST /api/agent/chat/stream
┌──────────────────────────────────────────────────────────────────┐
│                    Spring Boot 后端（MVC）                        │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ AgentController                                            │  │
│  │ - POST /api/agent/chat/stream                              │  │
│  │ - POST /api/agent/actions/{actionId}/confirm               │  │
│  │ - POST /api/agent/actions/{actionId}/cancel                │  │
│  └────────────────────────────────────────────────────────────┘  │
│                               ↓                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ AgentOrchestratorService                                   │  │
│  │ - 会话管理                                                 │  │
│  │ - Prompt 组装                                              │  │
│  │ - Tool Calling 编排                                        │  │
│  │ - 流式事件输出                                             │  │
│  └────────────────────────────────────────────────────────────┘  │
│                               ↓                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ AgentActionExecutor                                        │  │
│  │ - 权限校验                                                 │  │
│  │ - 参数校验                                                 │  │
│  │ - 调用现有业务 Service                                     │  │
│  │ - 审计记录                                                 │  │
│  └────────────────────────────────────────────────────────────┘  │
│                               ↓                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ Tools（按业务域分组）                                       │  │
│  │ - CustomerProfileTool                                      │  │
│  │ - CustomerOrderTool                                        │  │
│  │ - MealPlanTool                                             │  │
│  │ - CustomerDietaryRestrictionTool                           │  │
│  └────────────────────────────────────────────────────────────┘  │
│                               ↓                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ 现有业务服务层                                              │  │
│  │ - CustomerProfileService                                   │  │
│  │ - CustomerOrderService                                     │  │
│  │ - MealPlanService / 相关 Service                           │  │
│  └────────────────────────────────────────────────────────────┘  │
│                               ↓                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ MiniMax LLM + Redis                                        │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 三、核心设计原则

### 3.1 工具不是新的业务入口，而是现有业务能力的受控代理

Agent Tool 不直接面向数据库或 Mapper，不自行拼装越权逻辑，而是：

1. 接收模型请求
2. 转换为受控动作
3. 校验当前用户是否具备对应权限
4. 调用现有 Service / DTO
5. 返回结构化结果

### 3.2 读写分离

为降低首期风险，工具能力分为两类：

- **只读工具**：查询类操作，可直接执行
- **写操作工具**：新增、修改、删除、生成计划等，必须先生成待确认动作，再由用户显式确认

### 3.3 写操作采用“待确认动作”协议

流程如下：

1. 用户提出写操作诉求
2. LLM 收集必要参数
3. Tool 生成 `pending_action`
4. 前端展示差异、风险提示、关键字段摘要
5. 用户点击确认
6. 后端调用确认接口，执行真实业务写入
7. 记录审计日志并回传结果

### 3.4 权限校验必须后置到工具执行层

现有权限多定义在 Controller 的 `@PreAuthorize` 上，Agent 直接调 Service 会绕过这层控制。

因此新增统一能力：

- `AgentPermissionEvaluator`
- `AgentActionExecutor`

由执行器在每次工具调用前显式校验权限码，例如：

- `customerProfile:list`
- `customerProfile:add`
- `customerProfile:edit`
- `customerOrder:list`
- `mealPlan:list`

### 3.5 统一结构化事件流

前后端采用统一流式消息协议，建议事件类型如下：

- `token`：模型文本增量
- `tool_call`：工具调用开始
- `tool_result`：工具执行结果
- `pending_action`：待确认动作
- `action_confirmed`：动作已确认执行
- `done`：本轮结束
- `error`：异常

推荐事件负载示例：

```json
{
  "type": "pending_action",
  "actionId": "act_xxx",
  "actionType": "customerProfile.create",
  "title": "确认新增客户档案",
  "summary": "将新增客户 张三，并创建首单信息",
  "permission": "customerProfile:add",
  "payload": {
    "customerName": "张三",
    "phone": "13800138000"
  }
}
```

---

## 四、实施阶段

### Phase 1：基础设施与最小可用链路（1.5天）

目标：打通“流式对话 + 只读工具 + 权限校验 + 审计”的最小闭环。

#### 4.1 依赖引入

在合适的 Maven 模块中引入：

- `langchain4j`
- `langchain4j-open-ai`
- 必要的 JSON / HTTP 支撑依赖

注意：

- `langchain4j-bom` 应放在父 `pom.xml` 的 `dependencyManagement`
- 不新增 `spring-boot-starter-webflux` 作为主方案
- 当前项目是 Spring Boot 2.7 的 MVC 栈，流式接口优先基于现有 Web 体系实现

#### 4.2 配置接入

在现有 `application.yml + application-dev.yml / application-prod.yml` 体系内补充 Agent 配置，例如：

```yaml
agent:
  enabled: true
  provider: minimax
  minimax:
    base-url: https://api.minimax.chat/v1
    api-key: ${MINIMAX_API_KEY:}
    model: abab6.5s-chat
    temperature: 0.3
    max-tokens: 2048
  memory:
    max-messages: 20
    ttl-minutes: 30
```

不建议单独增加一个未被 profile 激活的 `application-agent.yml` 作为首期落地方案。

#### 4.3 目录结构

```text
eladmin-system/src/main/java/me/zhengjie/modules/agent/
├── config/
│   └── AgentConfig.java
├── domain/
│   ├── dto/
│   │   ├── AgentChatRequest.java
│   │   ├── AgentStreamEvent.java
│   │   ├── PendingActionDto.java
│   │   └── AgentActionConfirmRequest.java
│   └── enums/
│       └── AgentEventType.java
├── rest/
│   └── AgentController.java
├── service/
│   ├── AgentOrchestratorService.java
│   ├── AgentSessionService.java
│   ├── AgentAuditService.java
│   └── impl/
├── support/
│   ├── AgentPermissionEvaluator.java
│   ├── AgentActionExecutor.java
│   ├── AgentEventWriter.java
│   └── AgentToolResultFormatter.java
├── tool/
│   ├── CustomerProfileTool.java
│   ├── CustomerOrderTool.java
│   ├── MealPlanTool.java
│   └── CustomerDietaryRestrictionTool.java
└── prompt/
    ├── system-prompt.md
    └── tool-policy.md
```

#### 4.4 首期交付范围

- 流式接口打通
- Redis 会话存储
- 只读客户档案查询工具
- 工具执行前权限校验
- Agent 审计日志

---

### Phase 2：客户档案读写闭环（1.5天）

目标：围绕现有 `CustomerProfileService` 建立完整的受控读写能力。

#### 4.5 工具设计

首期只围绕现有真实模型，不抽象“万能 Customer”：

- `search_customer_profiles`
  对应 `CustomerProfileService.queryAll(...)`
- `get_customer_profile_detail`
  对应 `CustomerProfileService.getDetail(...)`
- `prepare_create_customer_profile`
  收集并校验参数，生成待确认动作
- `prepare_update_customer_profile`
  读取原始数据、计算差异、生成待确认动作

#### 4.6 写操作协议

新增客户档案时：

1. 模型收集 `CustomerProfileSaveDto` 所需字段
2. 后端做参数完整性校验
3. 生成 `pending_action`
4. 用户确认
5. 执行 `CustomerProfileService.create(dto)`

修改客户档案时：

1. 先查原始详情
2. 计算字段差异
3. 把差异展示给前端
4. 用户确认
5. 执行 `CustomerProfileService.update(dto)`

#### 4.7 审计要求

每次动作至少记录：

- 会话 ID
- 用户 ID / 用户名
- 工具名称
- 所需权限码
- 参数摘要（敏感字段脱敏）
- 是否命中确认流程
- 执行结果
- 失败原因

---

### Phase 3：多业务域扩展（2天）

目标：把能力从客户档案扩展到订单、饮食限制、排餐计划。

#### 4.8 扩展顺序

1. 客户订单查询
2. 饮食限制查询与维护
3. 排餐计划查询
4. 排餐计划生成/调整（需确认）

#### 4.9 每个业务域统一交付物

每接入一个业务域，必须同时补齐：

- 工具定义
- 权限码映射
- DTO 转换
- 只读与写操作边界
- 审计字段
- 前端结果卡片

#### 4.10 典型工具分层

`CustomerOrderTool`

- `search_customer_orders`
- `get_customer_order_detail`
- `prepare_update_customer_order`

`MealPlanTool`

- `search_meal_plans`
- `get_meal_plan_detail`
- `prepare_generate_meal_plan`
- `prepare_adjust_meal_plan`

---

### Phase 4：前端 Chat 页面与交互完善（1.5天）

目标：完成 Vue2 场景下的业务可用交互，而不是只做一个演示聊天框。

#### 4.11 页面结构

```text
eladmin-web/src/views/agent/
├── index.vue
├── components/
│   ├── ChatMessage.vue
│   ├── ToolCallCard.vue
│   ├── ToolResultCard.vue
│   ├── PendingActionCard.vue
│   └── ConfirmDialog.vue
└── api/
    └── agent.js
```

#### 4.12 核心功能

- 基于 `fetch` 的流式消息接收
- 增量渲染模型文本
- 工具状态展示
- 待确认动作卡片
- 确认 / 取消按钮
- 历史会话恢复
- 错误态和中断态处理

#### 4.13 消息类型

- `assistant_text`
- `tool_call`
- `tool_result`
- `pending_action`
- `system_notice`
- `error`

#### 4.14 入口方式

推荐首期采用**独立菜单页**，不建议先做右下角悬浮入口。

原因：

- 更符合现有后台系统的菜单与权限体系
- 便于挂权限标识
- 便于后续扩展业务卡片与会话历史

---

### Phase 5：安全、稳定性与运营（1天）

#### 4.15 安全要求

- 所有工具执行前校验权限
- 敏感字段脱敏，例如手机号中间位
- 写操作强制确认
- 会话隔离，禁止跨用户读取上下文
- 限流与配额控制

#### 4.16 稳定性要求

- LLM 超时控制
- 工具调用超时控制
- 流式中断恢复策略
- Redis 会话过期清理
- 幂等控制，防止重复确认

#### 4.17 运营与排障

- 记录模型耗时
- 记录工具耗时
- 记录失败率
- 保留请求链路 ID，便于排查问题

---

## 五、接口设计

### 5.1 流式对话接口

`POST /api/agent/chat/stream`

请求体示例：

```json
{
  "sessionId": "sess_xxx",
  "message": "帮我查一下张三的客户档案",
  "scene": "customer_profile"
}
```

响应：

- `Content-Type: text/event-stream`
- 前端使用 `fetch` 读取流

### 5.2 待确认动作接口

`POST /api/agent/actions/{actionId}/confirm`

作用：

- 对待确认动作进行最终执行

`POST /api/agent/actions/{actionId}/cancel`

作用：

- 取消待确认动作

### 5.3 会话恢复接口

可选增加：

- `GET /api/agent/sessions/{sessionId}`
- `GET /api/agent/sessions`

用于恢复会话历史与前端页面状态。

---

## 六、文件清单

### 后端新增/修改

| 文件路径 | 描述 |
|----------|------|
| `eladmin/pom.xml` | 在 `dependencyManagement` 或适当模块补充 LangChain4j 版本管理 |
| `eladmin-system/src/main/resources/config/application*.yml` | 增加 Agent 配置 |
| `eladmin-system/src/main/java/me/zhengjie/modules/agent/config/AgentConfig.java` | LLM、Memory、Tool 配置 |
| `eladmin-system/src/main/java/me/zhengjie/modules/agent/rest/AgentController.java` | 流式对话与动作确认接口 |
| `eladmin-system/src/main/java/me/zhengjie/modules/agent/service/AgentOrchestratorService.java` | 核心编排服务 |
| `eladmin-system/src/main/java/me/zhengjie/modules/agent/support/AgentPermissionEvaluator.java` | 权限校验 |
| `eladmin-system/src/main/java/me/zhengjie/modules/agent/support/AgentActionExecutor.java` | 工具执行门面 |
| `eladmin-system/src/main/java/me/zhengjie/modules/agent/service/AgentAuditService.java` | 审计记录 |
| `eladmin-system/src/main/java/me/zhengjie/modules/agent/tool/CustomerProfileTool.java` | 客户档案工具 |
| `eladmin-system/src/main/java/me/zhengjie/modules/agent/tool/CustomerOrderTool.java` | 客户订单工具 |
| `eladmin-system/src/main/java/me/zhengjie/modules/agent/tool/MealPlanTool.java` | 排餐计划工具 |
| `eladmin-system/src/main/java/me/zhengjie/modules/agent/tool/CustomerDietaryRestrictionTool.java` | 饮食限制工具 |

### 前端新增/修改

| 文件路径 | 描述 |
|----------|------|
| `eladmin-web/src/views/agent/index.vue` | 智能助手主页面 |
| `eladmin-web/src/views/agent/components/ChatMessage.vue` | 聊天气泡 |
| `eladmin-web/src/views/agent/components/ToolCallCard.vue` | 工具调用卡片 |
| `eladmin-web/src/views/agent/components/ToolResultCard.vue` | 工具结果卡片 |
| `eladmin-web/src/views/agent/components/PendingActionCard.vue` | 待确认动作卡片 |
| `eladmin-web/src/views/agent/components/ConfirmDialog.vue` | 动作确认弹窗 |
| `eladmin-web/src/views/agent/api/agent.js` | Agent 前端请求封装 |
| `eladmin-web/src/router/routers.js` 或菜单配置来源 | 接入入口路由 |

---

## 七、首期范围与非目标

### 7.1 首期必须完成

- 客户档案场景可用
- 流式返回可用
- 严格权限校验可用
- 待确认动作闭环可用
- 审计日志可追踪

### 7.2 首期不做

- 自动执行高风险写操作
- 跨多个业务域的复杂事务编排
- 完整 BI 分析类智能问答
- 无确认的批量修改

---

## 八、待确认事项

1. **模型接入信息**
   MiniMax API Key 由谁提供，部署环境如何注入。

2. **权限码映射表**
   每个工具动作对应哪个权限标识，需要在开发前明确。

3. **审计落点**
   仅复用现有 `@Log` 体系，还是新增 Agent 专用审计表。

4. **首期工具范围**
   首期是否只开放客户档案查询 + 新增/修改，订单与排餐计划放到第二批。

5. **前端入口**
   是否按建议采用独立菜单“智能助手”。

6. **流式协议格式**
   最终采用严格 SSE 事件格式，还是采用 NDJSON 分块格式。

---

## 九、实施结论

本方案不再把 Agent 视为“聊天 UI + 几个工具”，而是视为：

**一个严格受权限体系约束、可流式交互、可审计、可扩展的业务操作编排层。**

按此方案实施，可以先在客户档案场景稳定落地，再逐步扩展到订单、饮食限制和排餐计划，而不需要重做认证、权限和写操作协议。
