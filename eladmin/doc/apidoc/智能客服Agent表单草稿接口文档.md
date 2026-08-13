# 智能客服 Agent 表单草稿接口文档

> 文档版本: 1.0
> 更新日期: 2026-08-12

## 1. 接口定位

表单草稿是 Agent 辅助能力，不是客户或订单写接口。Agent 服务只能通过内部保存接口创建或修订草稿；登录客服通过领取接口取得类型化 payload，复用现有客户/订单页面并手动提交。完整手机号、地址只在授权的模型输入、本人会话原文和未过期草稿 payload 中流转，不出现在摘要、卡片、工具追踪或默认日志。

草稿能力受主系统 `AGENT_FORM_DRAFT_ENABLED` 总开关和客服目标新增权限共同控制。开关关闭时 `saveFormDraft` 不会进入 Agent 工具白名单，12 个只读工具及原手工新增流程不受影响。

## 2. 草稿类型和状态

| 类型/动作 | 含义 | 固定页面或结果 |
| --- | --- | --- |
| `CREATE_CUSTOMER_WITH_ORDER` | 新增客户档案及首单 | `/customer/profile` |
| `CREATE_ORDER` | 为唯一匹配的已有客户新增订单 | `/customer/order` |
| `CONVERT_TO_CREATE_CUSTOMER_WITH_ORDER` | 未找到已有客户后的对话转换动作；持久化后仍是第一种草稿 | `/customer/profile` |

生命周期为 `EDITABLE → READY → CLAIMED → SUBMITTED`，任一活动状态可因超时变为 `EXPIRED`，也可被系统取消为 `CANCELLED`。普通 `EDITABLE` 草稿不能生成页面跳转；新增订单缺少唯一客户身份且没有多匹配歧义时，可生成固定转换对话动作，点击后仍需由 Agent 重新保存 `CREATE_CUSTOMER_WITH_ORDER` 草稿。只有 `READY` 才生成正式表单页面跳转动作。`SUBMITTED` 和 `EXPIRED` 的 payload 已清空，不能再次预填提交。

## 3. Agent 内部保存接口

### 3.1 请求

```
POST /api/internal/agent/form-drafts:save
Content-Type: application/json
X-Agent-Internal-Token: <服务间令牌>
X-Agent-Access-Context: <短期签名访问上下文>
X-Agent-Session-Id: <会话 ID>
X-Request-Id: <请求 ID>
```

此接口只接受 Agent 服务调用，不得暴露给浏览器。访问上下文必须绑定当前客服、会话、请求 ID、目标新增权限和客户数据范围。

```json
{
  "draftType": "CREATE_CUSTOMER_WITH_ORDER",
  "schemaVersion": "v1",
  "sourceSessionId": "session-001",
  "clientMessageId": "message-001",
  "payload": {
    "customer": {
      "customerName": "张三",
      "phone": "13800000000",
      "addresses": [{"addressType": "DEFAULT", "addressDetail": "某路 1 号"}]
    },
    "order": {"parentPackageId": 1, "breakfastCount": 0, "lunchDinnerCount": 1}
  },
  "recognizedFields": ["customer.customerName", "customer.phone"],
  "missingFields": ["customer.addresses"],
  "warnings": []
}
```

模型创建时不填写 `clientMessageId`、`draftId`、`expectedRevision`；Agent 服务从当前可信请求上下文强制注入本轮 `clientMessageId`。修订时必须传入活动草稿上下文中的同一 `draftId` 和当前 `expectedRevision`。`payload` 按 `draftType` 严格转换为 v1 DTO，未知字段、未知字段路径、任意 URL、权限、Token、状态或内部控制字段均拒绝。`recognizedFields`、`missingFields` 只能使用登记路径；告警码只能使用主系统白名单。

对话中的普通纠正和固定转换动作会先由主系统按当前客服、会话、权限及客户数据范围重新读取活动草稿，再把 `draftId + revision + 类型化 payload` 作为可信上下文下发给 Agent。`CREATE_ORDER` 转为 `CREATE_CUSTOMER_WITH_ORDER` 时必须沿用原草稿 ID 和版本并携带 `convertedFrom=CREATE_ORDER`，通过同一次乐观锁修订原子切换草稿类型和目标权限。

### 3.2 响应

```json
{
  "success": true,
  "operation": "CREATED",
  "draftId": "afd_01JEXAMPLE123456",
  "draftType": "CREATE_CUSTOMER_WITH_ORDER",
  "status": "READY",
  "revision": 1,
  "expiresAt": "2026-08-13T18:00:00+08:00",
  "recognizedFields": ["customer.customerName", "customer.phone"],
  "missingFields": [],
  "warnings": []
}
```

响应不包含完整 payload。`operation` 为 `CREATED`、`UPDATED` 或 `IDEMPOTENT_REPLAY`；创建幂等键为 `owner + sourceSessionId + clientMessageId`，相同键重试返回同一草稿；修订使用 `draftId + expectedRevision`，版本冲突返回 `DRAFT_VERSION_CONFLICT`。状态由主系统根据关键告警、客户身份和 payload 关联关系计算，模型不能提交 `status`。

## 4. 登录客服领取与摘要

### 4.1 领取完整草稿

```
POST /api/agent/form-drafts/{draftId}/claim
Authorization: Bearer <登录态>
```

要求当前客服是草稿所有者、具有草稿的目标新增权限，并且客户数据范围仍允许关联对象。`READY` 首次领取后使用数据库行锁原子变为 `CLAIMED`，避免与对话修订互相覆盖；再次刷新可领取同一 `CLAIMED` 草稿。成功响应按类型只返回一个 payload：

```json
{
  "draftId": "afd_01JEXAMPLE123456",
  "draftType": "CREATE_ORDER",
  "schemaVersion": "v1",
  "status": "CLAIMED",
  "revision": 2,
  "orderPayload": {
    "customerId": 100,
    "customerCode": "B3303",
    "parentPackageId": 1,
    "lunchDinnerCount": 20
  },
  "recognizedFields": ["customerId", "customerCode"],
  "missingFields": [],
  "warnings": [],
  "sourceSessionId": "session-001",
  "expiresAt": "2026-08-13T18:00:00+08:00"
}
```

### 4.2 恢复脱敏摘要

```
GET /api/agent/form-drafts/{draftId}/summary
Authorization: Bearer <登录态>
```

摘要只返回 `draftId`、类型、状态、版本、已识别字段数量、缺失字段路径、稳定告警、来源会话、过期时间和（已提交时）目标业务 ID，不返回手机号、地址或 payload。会话恢复只对草稿摘要刷新状态；普通业务卡片不重新查询实时业务数据。

## 5. 正式提交关联

### 5.1 新增客户 + 首单

`POST /api/customerProfile` 可选携带：

```json
{"agentDraftId":"afd_01JEXAMPLE123456","agentDraftRevision":1}
```

后端在当前客服权限、草稿所有权、类型、状态、版本和关联对象校验通过后，复用现有客户/地址/首单校验。客户、首单和草稿 `SUBMITTED` 标记在同一事务完成，响应体为新建客户 ID（Long）。

### 5.2 已有客户新增订单

`POST /api/customer/order` 同样可选携带 `agentDraftId` 和 `agentDraftRevision`，并要求 `customerId + customerCode` 与实际客户档案一致。订单新增与草稿 `SUBMITTED` 标记在同一事务完成，响应体为新建订单 ID（Long）。

不携带草稿字段时两条接口保持原手工新增兼容行为。旧 revision、已提交、已过期、类型不匹配、重复提交或权限撤销均不得创建第二条业务记录。

## 6. 固定前端动作

Agent v2 `uiActions` 只允许：

- `OPEN_CREATE_CUSTOMER_WITH_ORDER_FORM`
- `OPEN_CREATE_ORDER_FORM`
- `CONVERT_TO_CREATE_CUSTOMER_WITH_ORDER`

动作 payload 只允许 `draftId` 和来源会话标识。前端按本地白名单映射到固定路由，忽略服务端提供的 `url`、`path`、组件名和任意 query；URL 不携带手机号、地址、金额或完整 JSON。`READY` 以外状态不生成可用页面跳转动作；转换动作只向聊天接口提交固定业务语句和 `formDraftId` 引用，不携带草稿 payload。主系统验证该引用确实属于当前客服和当前会话后，才读取完整草稿供 Agent 修订。

过期任务使用“记录仍处于查询到的活动状态且已到期”的条件更新清空 payload，不会把并发完成的 `SUBMITTED` 状态覆盖为 `EXPIRED`。

## 7. 稳定错误码

| 错误码 | 含义 |
| --- | --- |
| `DRAFT_CONTEXT_INVALID` / `DRAFT_SESSION_MISMATCH` | 访问上下文、会话或消息不一致 |
| `DRAFT_SCHEMA_UNSUPPORTED` / `DRAFT_TYPE_INVALID` / `DRAFT_TYPE_MISMATCH` | 协议版本或草稿类型错误 |
| `DRAFT_PAYLOAD_REQUIRED` / `DRAFT_PAYLOAD_INVALID` / `DRAFT_UNKNOWN_FIELD` | payload 缺失、类型错误或包含未登记字段 |
| `DRAFT_FIELD_PATH_INVALID` / `DRAFT_WARNING_CODE_INVALID` | 字段路径或告警码不在白名单 |
| `DRAFT_PARENT_PACKAGE_INVALID` / `DRAFT_SUB_PACKAGE_INVALID` / `DRAFT_PACKAGE_RELATION_INVALID` | 套餐关联无效、停用或越权 |
| `DRAFT_CUSTOMER_IDENTITY_INVALID` | `customerId` 与 `customerCode` 不一致 |
| `DRAFT_DISH_INVALID` / `DRAFT_TRIAL_ORDER_INVALID` | 菜品或试餐订单关联无效 |
| `DRAFT_VERSION_CONFLICT` / `DRAFT_NOT_EDITABLE` | 修订版本冲突或草稿不再可修订 |
| `DRAFT_NOT_CLAIMABLE` / `DRAFT_NOT_SUBMITTABLE` | 生命周期不允许领取或提交 |
| `DRAFT_EXPIRED` / `FORM_DRAFT_NOT_FOUND` | 草稿已过期或当前客服不可见 |
| `DRAFT_SUBMISSION_CONFLICT` | 提交状态条件更新失败，业务事务回滚 |
| `SENSITIVE_DATA_REJECTED` / `FORM_DRAFT_TEXT_TOO_LONG` | 敏感输入路径或文本长度不符合护栏 |

错误响应不得返回 SQL、堆栈、内部 URL、访问上下文、完整 payload、手机号或地址。
