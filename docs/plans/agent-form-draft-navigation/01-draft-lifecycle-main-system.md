# Phase 01 草稿生命周期与主系统接口

## 目标

在主系统建立可独立测试的服务端草稿能力：类型化保存/修订、领取、脱敏摘要、过期、所有权和提交锁定，不依赖 LLM 或前端即可验证。

## 依赖与输入

- 设计规格第 4、8、9 节。
- 现有 HMAC `AgentAccessContextService`、`DefaultAgentQueryPermissionService` 和当前客服归属规则。
- 客户/订单业务文档只用于字段与权限校验，本阶段不改正式新增流程。

## 输出

- 可独立调用的草稿创建、修订、领取、摘要、过期和提交锁定服务。
- `agent_form_draft` DDL、强类型 DTO、权限矩阵和自动化测试。

## 涉及文件

新增建议：

- `eladmin/sql/agent_form_draft.sql`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/domain/AgentFormDraft.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/domain/enums/AgentFormDraftType.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/domain/enums/AgentFormDraftStatus.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/domain/dto/AgentFormDraftSaveRequest.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/domain/dto/AgentFormDraftSaveResult.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/domain/dto/AgentFormDraftClaimResult.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/domain/dto/AgentFormDraftSummary.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/domain/dto/CustomerWithOrderDraftPayload.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/domain/dto/CustomerOrderDraftPayload.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/mapper/AgentFormDraftMapper.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/service/AgentFormDraftService.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/service/impl/AgentFormDraftServiceImpl.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/rest/InternalAgentFormDraftController.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/formdraft/rest/AgentFormDraftController.java`
- 对应主系统单元测试和 Controller 测试。

修改：

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/security/AgentQueryPermissionService.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/security/impl/DefaultAgentQueryPermissionService.java`
- `eladmin-system/src/main/resources/config/application*.yml`（仅在需要功能开关/过期配置时）。

## 实施步骤

### Step 1：设计 DDL 和实体

- 建立 `draft_id` 唯一键，以及 `owner_user_id + source_session_id + source_client_message_id` 创建幂等约束。
- 保存 `draft_type/schema_version/status/revision/payload/recognized_fields/missing_fields/warnings`。
- 保存来源会话、消息、目标权限、过期/领取/提交时间和目标业务 ID。
- `payload` 使用 JSON/TEXT 字段并按客户数据等级保护；默认 24 小时过期。
- 不新增独立审计表。

### Step 2：建立强类型协议

- 为客户 + 首单和新增订单分别建 payload DTO；字段覆盖现有保存 DTO，但排除图片和服务端计算字段。
- 登记字段路径和稳定告警码，拒绝未知字段。
- DTO 只描述草稿，不继承 `CustomerProfileSaveDto`、`CustomerOrderSaveDto` 或数据库实体。

### Step 3：实现保存与修订

- 创建时校验内部令牌、短期上下文、会话/消息归属、目标新增权限和幂等键。
- 修订时校验 `draftId + expectedRevision + owner`，使用条件更新防止丢失更新。
- 主系统根据关键关联与缺失项计算 `EDITABLE/READY`，不接受模型指定 status。
- 幂等重试返回同一 `draftId/revision/status`。

### Step 4：实现领取与摘要

- `POST /api/agent/form-drafts/{draftId}/claim` 返回完整类型化 payload，仅限所属客服和目标权限。
- `GET /api/agent/form-drafts/{draftId}/summary` 只返回脱敏摘要和状态。
- `READY/CLAIMED` 可重复领取用于刷新；`SUBMITTED/EXPIRED/CANCELLED` 不返回可提交 payload。
- 领取时重新校验关联对象的可见性，详细业务有效性在后续表单阶段补齐。

### Step 5：预留一次提交接口

- 在 Service 中提供 `lockForSubmission()`、`markSubmitted()` 协议，使用当前事务和行锁。
- 本阶段用测试替身验证状态转换；Phase 05/06 才接入正式客户/订单创建。
- 过期清理至少清空敏感 payload；如采用定时任务，使用可配置批次和明确方法注释。

## 验证方式

- Mapper/Service 测试：幂等创建、乐观锁、状态机、过期和清空 payload。
- Controller 测试：内部身份、短期上下文、登录客服所有权和权限变化。
- 跨客服猜测 `draftId` 返回无权或空结果，不暴露草稿是否存在。
- `git diff --check`。
- 主系统测试命令：`mvn399 -q -DskipTests=false -Dtest='*AgentFormDraft*Test,*DefaultAgentQueryPermissionServiceTest' test`。

## 完成标准

- 不依赖 Agent 即可创建、修订、领取、查询摘要和过期草稿。
- `EDITABLE/READY` 由主系统确定，幂等与乐观锁测试通过。
- 完整 payload 只从领取接口返回给所属客服。
- 没有新增审计表或正式业务写接口。

## 回滚

- 关闭草稿功能开关并停止暴露相关接口。
- DDL 保留不影响现有业务；确认无草稿数据后再按数据库流程移除。

## 状态

completed
