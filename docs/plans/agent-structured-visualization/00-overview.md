# Agent 结构化表格与图表展示实施计划

## 背景

当前智能客服链路已经返回自然语言 `assistantMessage` 和由成功工具事实生成的 `cards`，但前端仍把卡片数据格式化为原始 JSON；Markdown 表格也只是普通文本。客户相关统一查询还输出 `maskedName`，无法满足“客户编号为主标识、完整姓名辅助确认”的内部客服场景。

本计划以 [2026-08-05-agent-structured-visualization-design.md](../../superpowers/specs/2026-08-05-agent-structured-visualization-design.md) 为唯一设计输入，只拆解实施工作，不修改业务指标口径，不授权 LLM 生成业务数据或前端代码。

## 目标

- 主系统只在已认证且有业务权限的内部 Agent 查询链路返回 `customerCode + customerName`，保留手机号、地址、金额、内部 ID 等现有保护。
- Agent 服务从成功工具卡片生成独立的 `presentations`，已知卡片使用系统规则，未知卡片才调用受控 LLM 展示规划器。
- 前端只用固定 Vue、Element UI 和 ECharts 组件解释展示描述，不解析 `assistantMessage` 中的业务明细，不执行任何 LLM 代码或表达式。
- 新会话持久化展示决策；旧会话只读兼容，不重新查询、不重新调用 LLM、不补全旧姓名。
- 展示失败不覆盖卡片事实、工具告警或自然语言答案。

## 非目标

- 不增加写工具、数据库业务表或消息队列。
- 不把 Markdown、HTML、ECharts option 或前端组件名开放给 LLM。
- 不从分页、截断或不完整明细中聚合图表。
- 不改变订单下单时间、运营指标、餐数、核销和退餐的业务口径。
- 不改普通客户管理页面和非 Agent 对外接口的姓名策略。

## 实施约定

- `presentations` 与 `cards` 分离，通过 `sourceToolCallId` 一对一关联；每张卡片最多一个展示描述。
- v1 顶层继续使用设计中的 `summary`、`table`、`chart` 语义。为承载 `SERVICE_CUSTOMER_DETAIL` 的多个子表，`table` 允许受控 `sections`；单表仍使用设计样例中的 `dataPath + columns`，不改变样例兼容性。
- `summary` 可以和表格同时显示，不单独制造空页签；只有可切换的表格/图表或文本视图参与 `availableViews`。
- 指标卡保留原 `dimensions`，同时由主系统确定性生成只含 `label/value` 的 `breakdown`，供表格和图表引用；这是展示形态补充，不改变指标总数与分组口径。
- 当前已登记的 11 种卡片全部必须命中系统规则；12 个工具中的两个菜品工具共享 `DISH_LIST` 规则。
- `business_result_json` 已能保存扩展 JSON，本需求不新增数据库字段，不执行 DDL。

## 影响范围

### 主系统 `eladmin-system`

- 内部 Agent 客户/订单事实契约：姓名、统一下单时间、图表可用指标分组。
- Agent v2 客户端响应映射。
- 聊天消息业务快照保存、恢复和旧快照兼容。
- 安全、DTO、HTTP 客户端和会话测试。

### 独立 `agent-service`

- 展示描述模型、字段目录、系统规则注册表、启动校验和健康告警。
- 已知卡片确定性展示生成。
- 未知卡片 LLM 规划、校验、通用降级和稳定告警。
- Agent v2 OpenAPI 契约、模型 profile 和契约测试。

### 前端 `eladmin-web`

- 固定表格、摘要、图表和容器组件。
- 日期、状态、餐次、空值等集中格式化。
- 新响应渲染、历史只读兼容、图表异常回退和组件测试。

### 文档

- 智能排查助手、客户管理、订单管理业务说明。
- 智能排查助手接口、Agent 内部业务查询接口和 Agent v2 OpenAPI。

## Phase 列表

| Phase | 名称 | 主要输出 | 状态 |
| --- | --- | --- | --- |
| 01 | 内部 Agent 事实与身份契约 | `customerName`、`orderTime`、指标 `breakdown` 及安全测试 | completed |
| 02 | 系统展示契约与已知卡片规则 | v1 展示 DTO、11 类系统规则、启动校验、Agent 响应 | completed |
| 03 | 未知卡片 LLM 规划与安全降级 | 最小 Schema 输入、候选校验、通用 fallback、稳定告警 | completed |
| 04 | 主系统透传与会话持久化 | 聊天响应、消息快照、新旧恢复兼容 | completed |
| 05 | 新消息固定组件渲染 | 摘要、表格、图表、页签和集中格式化组件 | completed |
| 06 | 历史兼容与前端异常回退 | 旧卡映射、非法描述/图表失败降级、恢复测试 | completed |
| 07 | 跨模块验收、文档与发布准备 | 核心场景验收、完整回归、业务/API 文档、回滚清单 | completed |

## 阶段依赖

```text
Phase 01 事实契约
  -> Phase 02 系统展示规则
      -> Phase 03 LLM 兜底
      -> Phase 04 主系统持久化
          -> Phase 05 新消息渲染
              -> Phase 06 历史与异常兼容
                  -> Phase 07 联调与文档
```

Phase 03 与 Phase 04 在 Phase 02 完成后可以分别实施，但合并联调必须按 `status.yaml` 的顺序执行。

## 核心风险

- **完整姓名扩散**：只修改内部 Agent DTO；日志、审计、手机号、地址和金额护栏必须通过回归测试。
- **规则与卡片字段漂移**：系统规则启动时校验重复规则、未知路径、非法视图和字段；当前卡片缺规则由契约测试阻断发布。
- **LLM 兜底越权**：只发送字段名、类型、路径和完整性标记，禁止发送业务值；候选必须经后端白名单和敏感字段校验。
- **图表误导**：只有完整聚合数据可生成图表；`truncated=true` 或完整性告警时移除图表视图。
- **历史消息不兼容**：旧快照缺 `presentations` 时仅由前端本地已知映射转换；未知旧卡片只显示安全摘要。
- **复杂卡片表达过大**：`SERVICE_CUSTOMER_DETAIL` 使用受控 summary 和 table sections，不允许无限嵌套或动态组件。
- **双端规则短期重复**：Agent 注册表是新消息真相源，前端兼容映射只服务历史数据，并由固定历史样例测试防止漂移。

## 回滚策略

- 主系统响应新增字段采用向后兼容的可选字段；回滚 Agent 或前端时旧客户端可忽略 `presentations`。
- 姓名契约回滚时恢复 `maskedName`，不需要数据库回滚；旧消息保持原快照，不重写历史数据。
- 前端展示异常可回滚到兼容安全摘要，但不得恢复已知卡片的原始敏感 JSON 展示。
- LLM 展示规划可通过禁用 `presentation` 模型 profile 或服务开关退化为通用表格/摘要；已知系统规则不受影响。
- 会话快照仍写入同一 `business_result_json`，回滚无需 DDL，只需让旧代码忽略新增键。

## 执行规则

1. 每次只读取 `status.yaml` 和 `current_phase` 对应的 phase 文件。
2. 当前 Phase 的测试与完成标准全部满足后，才将其标记为 `completed`。
3. 更新 `current_phase` 后再开始下一阶段；不得跨 Phase 一次性修改所有模块。
4. 新增或修改方法必须同步维护清晰的方法注释。
5. 所有接口、业务规则和安全边界变更必须在 Phase 07 合并前同步文档。
