# Phase 05 会话列表与生命周期

## 目标

让客服能够找到超过首屏的历史会话、查看最近摘要、浏览并恢复归档会话，同时避免点击“新建会话”立即产生空数据库记录。

## 依赖

- Phase 04 已稳定消息发送、重试和消息对象。
- 沿用当前会话归属、归档字段和分页 API，不新增 DDL。

## 输入

- 现有 `/api/agent/chat-sessions` 查询、创建、详情、标题和 archive 接口。
- 会话摘要中的 title、customerCode、lastSummary、lastMessageTime、archived。
- 主系统 `resolveWritableSession()` 的空 sessionId 自动创建能力。

## 输出

- 服务端关键字搜索和分页加载。
- 进行中/已归档视图及恢复操作。
- 会话摘要信息层级。
- 懒创建新会话和空会话控制。

## 涉及文件

修改：

- `eladmin-web/src/api/agentDiagnosis.js`
- `eladmin-web/src/views/agent/diagnosis/index.vue`
- `eladmin-web/tests/unit/api/agentDiagnosis.spec.js`
- `eladmin-web/tests/unit/views/agent/diagnosis/index.spec.js`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/session/rest/AgentChatSessionController.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/session/domain/dto/AgentChatSessionQueryCriteria.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/session/domain/dto/AgentChatSessionSummaryDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImpl.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/session/rest/AgentChatSessionControllerTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/agent/session/service/impl/AgentChatSessionServiceImplTest.java`

可能新增的前端 sidebar 组件放在 Phase 06；本 Phase 先在现有页面中完成行为，避免行为修改和结构重构同时发生。

## 会话体验规则

### 列表与搜索

- 默认查询未归档会话，page=0、size=20。
- 关键字为空时按分页加载；关键字非空时传给后端 `keyword`，不再只过滤当前 20 条。
- 搜索关键字最少 1 个非空字符，输入后 300ms 防抖；也支持 Enter 立即查询。
- 新搜索重置 page 和列表，加载更多追加并按 sessionId 去重。
- 使用后端返回 total/totalElements 判断是否还有更多，不无限请求空页。

### 列表信息

每个会话项显示：

- 标题，最多一行。
- 客户编号或订单编号，可用时显示。
- 最近摘要，最多两行。
- 最近消息时间，使用统一日期时间格式。

不在列表显示客户姓名、手机号、地址、金额、内部 ID 或工具信息。

### 新建会话

- 点击“新建会话”只重置本地工作区，`activeSessionId=null`，显示欢迎消息。
- 首次发送时直接调用现有聊天接口并允许 sessionId 为空；主系统在事务内创建会话后返回真实 sessionId。
- 收到响应后将新会话插入列表顶部。
- 保留显式 create session API，供未来从客户页携带预设上下文打开时使用；本页面不再空创建。

### 归档与恢复

- 侧栏提供“进行中/已归档”切换。
- 已归档列表使用同一查询 API 的 `archived=true`。
- 已归档会话可查看历史消息，但输入框禁用并提示“恢复后可继续”。
- 点击“恢复会话”调用现有 archive 接口传 `false`，成功后移动到进行中列表。
- 归档当前会话后切换到下一条进行中会话；没有时显示本地新会话状态。
- 归档可恢复，因此不增加多步确认弹窗；操作失败时保留当前状态并提示。

## 实施步骤

### Step 1：确认服务端分页契约

- Controller 继续接收 page/size/archived/keyword。
- page、size 使用项目统一分页约束；size 固定前端 20，不增加超大页。
- 查询始终附加当前客服 operator 条件。
- keyword 复用 title、customerCode、lastSummary 的现有 like 条件。
- 返回标准分页对象，不新增第二个搜索接口。

### Step 2：前端会话查询状态

新增最小状态：

- `sessionArchivedView`
- `sessionPage`
- `sessionTotal`
- `sessionHasMore`
- `sessionSearchTimer`

- `loadSessions({ reset, selectCurrent })` 统一首次加载、搜索、刷新和加载更多。
- 切换归档视图或关键字时 reset；滚动到底或点击“加载更多”时追加。
- 请求期间保留已有列表，失败时提示一次，不清空已加载数据。

### Step 3：服务端搜索接线

- 删除 `filteredSessions` 对当前数组的业务搜索职责；可保留纯显示去重计算。
- query 参数传 `keyword: trimmedKeyword || undefined`。
- 对快速连续请求使用请求序号忽略旧响应，不引入取消令牌封装或状态管理库。
- 搜索框 placeholder 改为“搜索标题、客户编号或最近内容”。

### Step 4：完善会话项展示

- 标题仍优先 session.title。
- 标题为空时使用客户编号/订单编号/日期/餐次组合，最后才显示“新会话”。
- 不直接展示 UUID sessionId。
- 最近时间为空时不显示占位符。
- active、hover、archived 状态使用现有 Element UI 色彩，不增加新主题依赖。

### Step 5：改为懒创建

- `clearSession()` 改为本地 reset，不请求 create API。
- `sendMessage()` 在 activeSessionId 为空时直接发送 null sessionId；后端返回 sessionId 后更新当前状态。
- 主系统确认 `resolveWritableSession(null)` 创建会话、下游 v2 信封获得非空 sessionId。
- 如果聊天失败但主系统已经创建会话，下一次刷新应能看到已包含用户消息的会话；不在前端尝试删除。

### Step 6：归档查看与恢复

- 添加进行中/已归档 tab。
- 归档详情页禁用 composer、快捷回复和重试。
- 恢复成功后切回进行中并选中该会话。
- 会话重命名在两个视图均可使用。

### Step 7：测试会话边界

- 服务端 keyword 查询始终限制当前客服。
- 第 21 条之后的会话可以通过服务端搜索找到。
- 加载更多不重复 sessionId。
- 新建会话不调用 create API，首次发送后获得 sessionId。
- 归档详情只读，恢复后可发送。
- 搜索旧响应晚于新响应返回时不会覆盖新结果。

## 验证方式

- 运行 session Controller、Service 和权限归属测试。
- 运行前端 agent API 与 index 测试。
- 使用至少 25 条 mock 会话验证分页、搜索和追加。
- 手工验证进行中、已归档、恢复、改名和空列表状态。

## 完成标准

- 搜索不再局限于首屏 20 条。
- 会话列表能展示最近摘要和时间，但不泄露敏感信息。
- 点击新建不会立即产生空会话。
- 已归档会话可查看、恢复，归档状态下不能发送消息。
- 不新增数据库字段、搜索服务、全局状态库或复杂请求取消框架。

## 回滚

- 前端可回滚到首屏列表，后端 keyword 分页兼容不影响旧页面。
- 懒创建回滚后仍可使用现有 create session API。
- 归档恢复只复用现有 archived 字段，无数据迁移。

## 状态

completed
