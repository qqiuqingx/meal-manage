# Phase 08 文档、灰度与发布收口

## 目标

同步业务/API/架构文档，完成三端发布顺序、功能开关、人工验收和回滚演练，使草稿能力可以安全灰度。

## 依赖与输入

- Phase 07 全部验收通过。
- 已批准设计规格和最终实现契约。

## 输出

- 与实现一致的业务、API、架构和运维文档。
- 可执行的三端发布、白名单灰度、回滚演练和计划收口记录。

## 涉及文件

修改：

- `agent-service/README.md`
- `agent-service/docs/architecture-baseline.md`
- `agent-service/src/main/resources/openapi/agent-service-v2.yaml`
- `eladmin/doc/business/智能排查助手业务说明.md`
- `eladmin/doc/business/智能客服助手使用说明.md`
- `eladmin/doc/business/智能客服助手运维配置说明.md`
- `eladmin/doc/business/客户管理业务说明.md`
- `eladmin/doc/business/订单管理业务说明.md`
- `eladmin/doc/apidoc/智能排查助手接口文档.md`
- `eladmin/doc/apidoc/客户档案管理接口文档.md`
- `eladmin/doc/apidoc/客户订单管理接口文档.md`
- 新增草稿内部/客服接口文档（可独立成篇，避免现有文档过大）。
- `docs/plans/agent-form-draft-navigation/status.yaml`

## 实施步骤

### Step 1：同步业务边界

- 明确 Agent 只保存辅助草稿，客服在业务页面手动新增。
- 记录客户 + 首单、新增订单、多匹配、未找到转换和关键歧义规则。
- 记录首期不支持图片、不转交草稿、不自动提交、不建设独立审计。
- 明确完整手机号/地址会进入模型和会话存储的已确认产品边界。

### Step 2：同步接口契约

- 文档化 `saveFormDraft` 输入、输出、幂等、乐观锁和错误码。
- 文档化 claim/summary 和正式提交的可选草稿字段。
- 更新 Agent v2 响应 `formDraftSummary/uiActions` 及兼容策略。
- 列出固定动作枚举，明确禁止任意 URL。

### Step 3：更新运行基线

- README/架构基线改为“12 个只读工具 + 1 个辅助草稿写工具”。
- 说明副作用工具不使用同参缓存，主系统负责幂等。
- 补充功能开关、24 小时过期、敏感 payload 清理和排障错误码。
- 更新健康检查/启动校验，工具关闭时允许正常只读运行。

### Step 4：发布和灰度

1. 执行主系统 DDL并发布兼容接口，保持工具不可见。
2. 发布前端领取/预填代码，入口仍关闭。
3. 发布 Agent 第 13 个工具和响应字段。
4. 对白名单客服开启客户 + 首单，再开启新增订单。
5. 人工核对生成、领取、提交、失败和过期场景后扩大范围。

首期不强制建设独立运营指标或草稿操作审计。灰度使用不含个人信息的系统级计数、稳定错误日志和人工验收；正文日志默认关闭，不增加人员操作审计。

### Step 5：回滚演练

- 关闭主系统可用工具白名单后，模型立即退回只读能力。
- 回滚前端后新响应字段被忽略，业务页面恢复手工新增。
- 回滚 Agent 后主系统兼容字段和草稿表可保留。
- 已有未提交草稿自然过期并清空 payload；已提交业务数据不回滚。

### Step 6：计划收口

- 确认所有 Phase 完成标准和测试证据。
- 将 `status.yaml` 中全部状态改为 completed，`current_phase: complete`。
- 检查每份文档不存在旧“12 个只读工具且无写工具”的矛盾描述。

## 验证方式

- OpenAPI 校验和接口文档示例与实际 JSON 对照。
- 文档全文搜索旧“12 个只读工具且无写工具”、`不新增写工具`、`不会保存完整手机号` 等过时表述，并核对转换动作的 EDITABLE 例外。
- 受当前环境限制，本轮完成可执行发布/回滚清单和关闭开关自动化验证；真实测试环境部署、模型联调和人工八场景验收需在有 MySQL/Redis/Agent 依赖的环境执行，不把本地无依赖状态误报为已部署。
- 客服人工验收客户 + 首单与新增订单各至少一条，随后清理测试数据。

## 完成标准

- 业务、API、架构和运维文档与实现一致。
- 功能可以按白名单独立开启和关闭。
- 发布/回滚不影响原只读 Agent 和手工新增流程；目标模块构建、前端定向 Jest/ESLint、Agent 全量测试和主系统目标测试已有记录。
- `status.yaml` 正确标记全部阶段完成。

## 回滚

- 文档和代码按前端 -> Agent -> 主系统逆序回滚。
- DDL 默认保留；如确需删除，必须确认无未完成草稿并走数据库变更流程。

## 当前验证证据

- `agent-service`: `mvn -q -DargLine=-javaagent:/Users/qqx/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.8/byte-buddy-agent-1.17.8.jar test`：通过。
- `agent-service` 生产包：`mvn -q -DskipTests package`：通过。
- `agent-service` 健康检查登记字段（12 个只读工具 + 1 个草稿写工具）及契约测试：通过。
- `eladmin-system`: `mvn -q -pl eladmin-system -am -DfailIfNoTests=false -DskipTests=false -Dtest='AgentFormDraftServiceImplTest,DefaultAgentQueryPermissionServiceTest,AgentChatSessionServiceImplTest,CustomerProfileServiceImplTest,CustomerOrderServiceImplTest' test`：通过。
- `eladmin-system` 生产包：`mvn -q -pl eladmin-system -am -DskipTests package`：通过。
- 前端草稿/会话定向 Jest：5 suites、45 tests 通过；草稿相关 ESLint 通过。
- `git diff --check`：仅报告用户已有 `AGENTS.md` 尾随空格，草稿改动本身无新增格式错误。
