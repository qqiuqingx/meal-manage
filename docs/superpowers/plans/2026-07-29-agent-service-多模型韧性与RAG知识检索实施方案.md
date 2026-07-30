# agent-service 多模型韧性与 RAG 知识检索实施方案

> 编制日期：2026-07-29  
> 适用项目：`/Users/qqx/job/code/eladmin-mp/agent-service`  
> 核心方向：多 LLM 提供商韧性、RAG 知识库检索、幻觉检测、客户健康度评分  
> 定位：内部智能客服系统（运营人员增强型），不涉及外部客户、人工转接、全渠道  
> 前置依赖：已完成的 agent-service 架构收口（2026-07-29）

## 实施进度

| 阶段 | 状态 | 最近更新 | 已完成范围 / 待办 |
|---|---|---|---|
| 阶段 1：多模型韧性 | 待实施 | — | 多 provider 配置、Fallback 链、健康检测、自动切换 |
| 阶段 2：RAG 知识检索 | 待实施 | — | 知识库构建、向量检索、意图扩展、路由集成 |
| 阶段 3：幻觉检测 | 待实施 | — | 数据断言校验、规则断言校验、建议标注 |
| 阶段 4：客户健康度评分 | 待实施 | — | 风险指标定义、评分计算、诊断/查询联动 |

---

## 1. 背景与总体结论

### 1.1 当前状态

agent-service 已具备以下能力：

- 混合意图分类（规则+LLM），16 种意图类型
- 结构化业务查询管道（NL → QueryPlan → API 调用 → 结果组合）
- 14 条诊断规则 + 5 个专项分析器 + LLM 兜底
- 会话管理、操作审计、安全权限模型
- 283 个测试通过，0 失败，1 跳过

### 1.2 与市面客服 Agent 的差距聚焦

经 2026-07-29 差距分析，结合内部客服系统定位，确认以下 **4 项能力**需要补齐：

| 能力 | 当前状态 | 目标状态 |
|------|---------|---------|
| **多模型韧性** | 仅 DeepSeek 单一 provider | 主备双模型 + 规则兜底，自动切换 |
| **RAG 知识检索** | 只能回答数据查询类问题 | 支持从业务文档检索知识问答 |
| **幻觉检测** | LLM 生成直接返回，无验证 | 数据断言校验 + 规则匹配校验 + 建议标注 |
| **客户健康度评分** | 无 | 多维度风险指标，诊断/查询联动 |

不纳入本次方案的能力及原因：

| 能力 | 排除原因 |
|------|---------|
| 人工转接 | 内部运营系统，操作员始终在回路 |
| 全渠道 | 仅 Web + 未来移动端 H5 |
| 多语言 | 仅中文 |
| 主动服务 | 后续独立规划 |
| 多 Agent 编排 | 当前单 Agent 够用，后续扩展时再拆分 |

### 1.3 核心判断

- **多模型韧性**实现成本最低、收益最直接——Spring AI 本身就是多模型抽象层，主要工作是配置和 fallback 链。
- **RAG 知识检索**是能力边界的关键扩展——让 agent 从"只能查数据"升级为"能回答问题"。
- **幻觉检测**在内部系统中比外部系统更容易做——数据源是结构化 API，断言可反向校验。
- **客户健康度评分**是"情感分析"在内部客服场景的正确形态——不是实时情绪检测，而是客户风险画像。
- 保留现有"模型只负责受控理解，服务端负责规划、授权和执行"的安全边界。
- 新增能力通过扩展意图 + 新增 Handler/管道实现，不修改中心聊天编排器。

---

## 2. 阶段 1：多模型韧性

### 2.1 目标

- 支持 DeepSeek（主）+ 至少一个备用 LLM provider（Claude 或 OpenAI 兼容）
- 主模型故障/超时时自动切换到备用模型
- 全部模型不可用时降级到规则兜底
- 切换对业务层透明——业务代码只选择 profile，不感知 provider

### 2.2 现状分析

当前架构：

```
AgentModelGateway (interface)
    └── SpringAiAgentModelGateway (唯一实现)
            └── ChatClient.Builder (Spring AI 自动配置)
                    └── 绑定到单一 spring.ai.deepseek 配置
```

**问题**：
1. Spring AI 的 `ChatClient.Builder` 在自动配置时绑定到**第一个**可用的 ChatModel bean，无法同时持有多个 provider 的 builder
2. `AgentProperties.Models.profiles` 只声明了 `default` 和 `diagnosis` 两个 profile，但都指向同一个 `deepseek-chat` 模型
3. 没有 fallback 链或熔断机制

### 2.3 技术方案

#### 2.3.0 实施前约束

- 备用 Claude 必须使用 **Anthropic 原生适配器**（`spring-ai-starter-model-anthropic`、`spring.ai.anthropic.*`），不能把 `https://api.anthropic.com` 直接配置为 `spring.ai.openai.base-url`。只有接入了已验收的 OpenAI 兼容代理时，才允许使用 OpenAI 适配器，并必须在配置中明确代理地址、责任方和协议验收用例。
- 现有 `AgentModelGateway.chatClient(profile)` 只在构造期返回一个固定 `ChatClient`。Fallback 不能仅在该方法中选一次 client；必须围绕一次完整的模型调用（含 prompt、结构化解析和工具调用循环）执行，异常才有机会切换 provider 后重放同一请求。
- DeepSeek 当前默认模型名 `deepseek-chat` 是历史兼容配置。实施时应以发布日官方模型列表为准，将生产模型通过环境变量显式指定，并增加启动期模型/能力探测；不在新方案中继续把历史模型名作为长期默认承诺。

#### 2.3.1 多 Provider 配置

Spring AI 的模型抽象可承载多个 provider，但本项目需显式创建并以 provider 名称注入各自的 `ChatModel`/`ChatClient.Builder`；不得依赖自动配置“选择第一个 bean”或隐式限定符：

```yaml
# application.yml
spring:
  ai:
    # 主 provider：DeepSeek（OpenAI 兼容协议）
    deepseek:
      api-key: ${AGENT_DEEPSEEK_API_KEY}
      base-url: ${AGENT_DEEPSEEK_BASE_URL:https://api.deepseek.com}
      chat:
        options:
          model: ${AGENT_DEEPSEEK_MODEL}
    # 备用 provider：Anthropic Claude 原生协议
    anthropic:
      api-key: ${AGENT_CLAUDE_API_KEY}
      base-url: ${AGENT_CLAUDE_BASE_URL:https://api.anthropic.com}
      chat:
        model: ${AGENT_CLAUDE_MODEL}
```

同时在 `pom.xml` 增加 `spring-ai-starter-model-anthropic`。如果选择代理方案，则该段改为 `spring.ai.openai`，并在部署文档中写明代理的 OpenAI 兼容性、鉴权头和可用区；两种方案不得混用。

#### 2.3.2 AgentProperties 扩展：Provider 绑定

在 `AgentProperties.Models` 中增加 `providers` 配置，将 profile 绑定到具体 provider：

```java
public static class Models {
    private Map<String, ModelProfile> profiles = new LinkedHashMap<>(Map.of("default", new ModelProfile()));
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();
    private List<String> fallbackOrder = List.of("deepseek", "claude");
    // ...
}

public static class ProviderConfig {
    private String type; // deepseek, anthropic, openai-compatible（仅代理）
    private String apiKey;
    private String baseUrl;
    private boolean enabled = true;
    // 本阶段不做负载均衡，避免“fallback 顺序”和权重路由并存。
}

public static class ModelProfile {
    // 现有字段...
    private String provider = "deepseek"; // 新增：绑定到哪个 provider
    private List<String> fallbackProviders = List.of(); // 新增：fallback 顺序
}
```

#### 2.3.3 Fallback 链实现

新增按**完整调用**执行的 `FallbackModelExecutor`，并由 provider 专属 gateway 负责构建 `ChatClient`：

```
FallbackModelExecutor
    ├── providerGateways: Map<String, ProviderModelGateway>
    │   ├── "deepseek" → SpringAiAgentModelGateway(deepseek ChatClient)
    │   └── "claude"  → SpringAiAgentModelGateway(anthropic ChatClient)
    ├── fallbackOrder: ["deepseek", "claude"]
    ├── healthChecker: ProviderHealthChecker
    └── execute(profile, request):
        1. 读取 profile 绑定的 provider
        2. 按 provider → fallbackProviders 顺序尝试
        3. 对同一受控 request 执行完整调用；仅网络超时、5xx、限流等可恢复错误进入 fallback
        4. 解析/Schema/工具执行错误不切换，保留稳定失败码和审计摘要
        5. 全部 provider 不可用 → 抛出 MODEL_UNAVAILABLE
```

业务分析、会话理解和诊断客户端应依赖这个执行端口，而非在 Bean 初始化时保存单一 `ChatClient`。工具调用场景的重放必须使用新的工具调用会话，禁止复用失败 provider 的中间会话或工具结果。

**熔断策略**：
- 连续失败 3 次 → 标记 provider 为 unhealthy，冷却 30 秒
- 冷却期后自动半开（允许 1 次探测请求）
- 探测成功 → 恢复 healthy；失败 → 重新冷却

**健康检查**：
- 启动时仅校验配置、模型能力与客户端 Bean；不发送收费 prompt。深度连通性由受控运维探测接口或灰度任务显式触发。
- 运行时通过 `ChatClient` 调用异常自动触发熔断
- 不引入独立的 health check 定时任务（避免额外 API 费用）

#### 2.3.4 配置示例

```yaml
agent:
  models:
    providers:
      deepseek:
        type: deepseek
        enabled: true
      claude:
        type: anthropic
        enabled: ${AGENT_CLAUDE_ENABLED:false}
        base-url: ${AGENT_CLAUDE_BASE_URL:https://api.anthropic.com}
    profiles:
      default:
        model: ${AGENT_MODEL_DEFAULT:${AGENT_DEEPSEEK_MODEL}}
        provider: deepseek
        fallback-providers: [claude]
        timeout-ms: 3000
        structured-output: true
        tool-calling: true
      diagnosis:
        model: ${AGENT_MODEL_DIAGNOSIS:${AGENT_DEEPSEEK_MODEL}}
        provider: deepseek
        fallback-providers: [claude]
        timeout-ms: 8000
        structured-output: true
        tool-calling: true
```

#### 2.3.5 环境变量

| 变量 | 默认值 | 用途 |
|------|--------|------|
| `AGENT_DEEPSEEK_API_KEY` | 空 | DeepSeek API Key（主 provider） |
| `AGENT_DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek API 地址 |
| `AGENT_DEEPSEEK_MODEL` | 无固定长期默认值 | DeepSeek 模型名，生产环境必须显式设置并经能力评测 |
| `AGENT_CLAUDE_ENABLED` | `false` | 是否启用 Claude 备用 provider |
| `AGENT_CLAUDE_API_KEY` | 空 | Claude API Key |
| `AGENT_CLAUDE_BASE_URL` | `https://api.anthropic.com` | Claude API 地址 |
| `AGENT_CLAUDE_MODEL` | 空 | Claude 模型名，启用 Claude 时必填并经结构化输出/工具调用评测 |

### 2.4 新增/修改文件

```
agent-service/src/main/java/me/zhengjie/agent/
├── infrastructure/llm/
│   ├── AgentModelGateway.java              # [修改] 增加 provider 相关方法
│   ├── FallbackModelGateway.java           # [新增] Fallback 链实现
│   ├── ProviderHealthTracker.java         # [新增] 熔断/健康追踪
│   ├── SpringAiAgentModelGateway.java      # [修改] 支持多 provider
│   └── MultiProviderChatClientConfig.java  # [新增] 多 provider 自动配置
├── config/
│   └── AgentProperties.java               # [修改] 增加 provider 配置
└── src/main/resources/
    └── application.yml                     # [修改] 增加备用 provider 配置
```

### 2.5 测试策略

- `ProviderHealthTrackerTest`：熔断状态转换（healthy → unhealthy → half-open → healthy）
- `FallbackModelGatewayTest`：主 provider 不可用时自动切换到备用
- `FallbackModelGatewayTest`：全部不可用时抛出 MODEL_UNAVAILABLE
- `MultiProviderChatClientConfigTest`：配置校验（未配置 API Key 的 provider 启动警告）
- 现有 283 个测试行为不变

---

## 3. 阶段 2：RAG 知识检索

### 3.1 目标

- 支持"知识问答"类意图——运营人员可以问业务规则、操作流程、概念解释
- 从 `eladmin/doc/business/` 下的业务文档构建知识库
- 不影响现有"数据查询"管道（数据查询仍走结构化 API）

### 3.2 知识库内容

当前可用文档（`eladmin/doc/business/`）：

| 文档 | 大小 | 内容 |
|------|------|------|
| 配菜管理业务说明.md | ~15KB | 菜品主档、配料字典、排期生成、忌口过滤 |
| 套餐管理业务说明.md | ~12KB | 父子套餐结构、编号池、套餐与餐品线关系 |
| 客户管理业务说明.md | ~10KB | 客户档案、地址、套餐分类、签约记录、剩余餐数计算 |
| 订单管理业务说明.md | ~18KB | 订单生命周期、餐数体系、金额体系、排餐模式、核销联动 |
| 排餐管理业务说明.md | ~20KB | 三层表结构、scheduleKey、生效订单过滤、幂等生成、过敏过滤 |
| 核销管理业务说明.md | ~12KB | 核销链路校验、自动完单、金额扣减、日志快照、批量核销 |
| 智能客服助手使用说明.md | ~8KB | Agent 操作指南 |
| 智能客服助手运维配置说明.md | ~6KB | 运维配置 |
| 智能排查助手业务说明.md | ~10KB | 诊断助手业务逻辑 |

**总计约 110KB Markdown 文档**，是高质量的领域知识语料。

### 3.3 技术方案

#### 3.3.1 整体架构

```
用户问题
    ↓
意图分类（现有 ChatIntentClassifier）
    ↓
┌─ BUSINESS_QUERY 等数据类意图 → 现有 BusinessQueryPipeline（不变）
│
└─ KNOWLEDGE_QUERY（新增）→ RAG 管道
       ├── 1. 查询精炼（QueryRefiner）
       ├── 2. 向量检索（VectorStoreRetriever）
       ├── 3. 重排序（可选，Reranker）
       ├── 4. 上下文组装（ContextAssembler）
       └── 5. LLM 生成 + 来源标注（KnowledgeAnswerGenerator）
```

#### 3.3.2 向量数据库选型

本项目的首批语料约 110KB，预计只有数百个 chunk；选型重点不是极限吞吐，而是中文召回、可审计的版本切换、数据不出域和运维复杂度。不得把普通 Redis 或 `SimpleVectorStore` 当作生产向量库：Spring AI 明确将后者定位为测试/演示用途。

| 方案 | 与当前项目的匹配度 | 优点 | 主要成本/否决条件 | 结论 |
|------|------|------|------|------|
| **Redis Stack / Redis Query Engine + RedisVectorStore** | 高（条件成立） | 复用 Redis 运维体系；支持向量检索、metadata filter 与全文 BM25；Spring AI 1.1.6 有对应 starter | 现有 Redis 必须实际具备 RedisJSON/Query Engine、持久化、备份与独立 key namespace；不能只看 Redis 版本 | **首选，须通过 POC** |
| **Qdrant + QdrantVectorStore** | 中 | 向量库职责清晰、collection 版本切换直接，不污染缓存 Redis | 新增服务、监控、备份和高可用成本 | Redis POC 不通过时的**生产备选** |
| Elasticsearch/OpenSearch | 低 | 可做成熟的全文/向量混合检索 | 项目未部署该搜索集群，新增运维成本对数百 chunk 不划算 | 仅在已有集群时复用 |
| Chroma | 低 | 原型快速 | 是独立服务而非“内嵌零运维”；生产备份、鉴权、升级策略仍需维护 | 不作为本项目生产方案 |
| MySQL / PGVector | 低 | 可与关系数据邻近部署 | 当前 MySQL 架构没有 Spring AI 1.1.6 的标准 MySQL VectorStore；项目也没有 PostgreSQL | 不引入 |
| Simple/InMemory | 仅测试 | 零外部依赖 | 进程级数据、全量扫描，Spring AI 不建议生产使用 | 仅单元测试 |

**阶段 2 暂定决策**：以 **Redis Stack + 本地 embedding + 稠密向量/BM25 混合召回** 为首选；若 POC 未满足准入指标，则改为 **Qdrant + 同一 embedding 模型**，而不是生产环境回退 InMemory。语料规模小，首期使用精确 `FLAT` 检索即可；仅在 chunk 数量和压测显示需要时再切换 HNSW。暂不引入 reranker。

**Redis 准入 POC（必须在编码前完成）**：确认运行实例具备 Redis Query Engine/RedisJSON，使用独立 `agent:knowledge:{version}:` namespace，验证向量 schema 显式创建、metadata filter、BM25、删除、备份恢复、ACL 隔离和 20 并发检索。Spring AI 的 RedisVectorStore 需要 Redis Stack/Query Engine 和单独的 starter，且 schema 初始化默认需要显式开启；依赖版本必须锁定到现有 Spring AI `1.1.6` BOM 后验证。

#### 3.3.3 Embedding 模型

Embedding 必须作为独立依赖决策，不能假定聊天 provider 同时提供 embedding，也不能使用未验证的 `deepseek-embedding` 默认值。

**首选**：在独立的本地 Ollama 服务运行 `bge-m3`，通过 Spring AI 1.1.6 的 Ollama model starter 接入。该模型支持中文/多语言，Ollama 发布物约 1.2GB、向量维度为 1024；镜像构建期预拉取固定 digest，生产环境禁止首启自动下载。Embedding 服务与 agent-service 分开部署，限制网络访问与资源配额。

选择本地 embedding 只解决“入库向量化不出域”，不自动解决 RAG 上下文出域：检索到的原文仍会发给答案生成 LLM。知识清单必须先经数据安全评审，敏感字段/内部地址/凭证不得入库；如果聊天模型部署在外部 provider，发送前还要执行上下文最小化、敏感内容扫描和出域审批。

备选是已完成安全评审的远程 embedding 服务；它必须独立配置 endpoint、模型、维度、密钥和数据保留条款。远程 embedding 不能与聊天 provider 的 API key 或模型名混用。

配置必须独立于聊天模型，例如：

```yaml
agent:
  knowledge:
    embedding:
      provider: ${AGENT_KNOWLEDGE_EMBEDDING_PROVIDER:}
      model: ${AGENT_KNOWLEDGE_EMBEDDING_MODEL:bge-m3}
      dimensions: ${AGENT_KNOWLEDGE_EMBEDDING_DIMENSIONS:1024}
```

`provider`、`model` 或 `dimensions` 未通过启动校验时，知识库能力保持关闭并报告稳定的配置错误；不得回退到不兼容维度的旧索引。embedding 模型、模型 digest、维度、chunker 版本和检索策略都是 collection version 的组成部分。上线前以脱敏中文评测集对比 **BM25-only、dense-only、RRF 混合召回**，以 Recall@3、来源命中率、P95 延迟和拒答率决定是否启用混合检索；首期不接 reranker。

#### 3.3.4 文档处理管道

```
受控知识清单（manifest）中的 Markdown 文档
    ↓
DocumentLoader（Markdown 解析、分段）
    ↓
DocumentChunker（按 ## 标题分段，每段 500-1500 tokens）
    ↓
DocumentMetadataEnricher（添加文档 ID、Git revision/内容 hash、标题、文件名、段落锚点）
    ↓
EmbeddingGenerator（调用独立本地/已审批远程 EmbeddingModel）
    ↓
HybridRetriever（Redis Stack 的 dense + BM25；测试替身仅用于单测）
```

**分段策略**：
- 以 Markdown `##` 标题为自然边界；超长标题段继续按段落边界拆分
- 每段 500-1500 tokens（中文约 750-2250 字）
- 相邻段落保留 100 字重叠（overlap），确保跨段信息不丢失
- 表格保留完整（不截断表格）

**文档来源与更新机制**：
- 不扫描目录中所有新增 `.md`。维护受代码评审的 knowledge manifest，只纳入业务规则类文档；草稿、实施计划、运维密钥说明、用户可写内容和未审核文件默认排除，避免提示注入和过期流程进入知识库。
- `AGENT_KNOWLEDGE_DOCS_PATH` 必须是部署时挂载的绝对路径；启动时校验 manifest 文件存在、可读且内容 hash 与索引版本一致。不得依赖 JAR 的当前工作目录或仓库相对路径。
- 刷新采用“构建新版本 → 检索/引用验收 → 原子切换 active collection”的两阶段流程。每个 chunk ID 由文档 ID、段落锚点、内容 hash 和 embedding 版本组成；切换后清理旧版本，确保修改和删除的文档不会残留旧向量。
- `POST /api/internal/agent/knowledge/refresh` 仅作为异步任务提交入口，返回 jobId 和目标版本。接口须由 agent-service 入站管理员鉴权、网络隔离、幂等锁、限流和审计保护；不能把 agent 调用主系统所用的内部 token 直接当成缺少校验的入站鉴权。

#### 3.3.5 意图扩展

新增意图 `KNOWLEDGE_QUERY`：

```java
public enum ChatIntent {
    // ... 现有意图 ...
    /** 知识问答：从业务文档中检索答案 */
    KNOWLEDGE_QUERY
}
```

路由优先级必须固定：已有 `BUSINESS_RULE_QUERY`、数据查询和诊断意图优先；仅当问题不要求实时数据、命中 knowledge manifest 且没有受控规则工具答案时才进入 `KNOWLEDGE_QUERY`。不能仅以“怎么”“规则”“流程”等通用词触发，否则会把“剩余餐数怎么算”等已有强类型规则查询错误送入 RAG。

#### 3.3.6 路由集成

在 `DefaultConversationHandler`（或 `ConversationCoordinator`）中：

```java
// 伪代码
if (intent == ChatIntent.KNOWLEDGE_QUERY) {
    return knowledgeQueryPipeline.execute(command, context);
} else if (intent.isBusinessQuery()) {
    return existingBusinessQueryPipeline.execute(command, context);
}
```

#### 3.3.7 回答格式

知识问答的响应包含**来源标注**：

```json
{
  "responseType": "KNOWLEDGE_ANSWER",
  "assistantMessage": "订单的剩余餐数分为早餐餐数和午餐晚餐餐数，两者独立计算。",
  "sources": [
    {
      "document": "订单管理业务说明.md",
      "section": "订单剩余餐数计算规则",
      "anchor": "#订单剩余餐数计算规则",
      "documentVersion": "sha256:..."
    }
  ]
}
```

### 3.4 新增/修改文件与契约

```
agent-service/src/main/java/me/zhengjie/agent/
├── knowledge/                                  # 新包
│   ├── KnowledgeQueryPipeline.java            # RAG 管道入口
│   ├── KnowledgeQueryHandler.java             # ConversationHandler 实现
│   ├── retrieval/
│   │   ├── DocumentLoader.java                # Markdown 文档加载
│   │   ├── DocumentChunker.java               # 文档分段
│   │   ├── EmbeddingService.java              # Embedding 生成
│   │   └── VectorStoreService.java            # 向量存储抽象
│   ├── generation/
│   │   ├── KnowledgeAnswerGenerator.java      # LLM 答案生成
│   │   └── SourceCitationFormatter.java       # 来源标注格式化
│   └── domain/
│       ├── KnowledgeDocument.java             # 知识文档实体
│       ├── KnowledgeChunk.java                # 文档片段
│       └── KnowledgeQueryResult.java          # 查询结果
├── config/
│   └── AgentProperties.java                   # [修改] 增加 knowledge 配置
└── src/main/resources/
    └── application.yml                        # [修改] 增加 embedding 配置
```

以下文件同样是阶段 2 的必改范围，不能只改 agent-service 内部包：

- `agent-service/pom.xml`：增加已选 embedding/vector store 的依赖；现有 `spring.ai.model.embedding: none` 必须按知识库开关做条件化配置。
- `agent-service/src/main/resources/openapi/agent-service-v2.yaml`：增加 `KnowledgeSource`、`knowledgeSources`、刷新任务的请求/响应和 `KNOWLEDGE_UNAVAILABLE` 错误码；维持 `additionalProperties: false`。
- `eladmin-system` 的 agent-service client、DTO、会话快照持久化/恢复：透传并保存来源信息和知识库版本。
- `eladmin-web`：按来源安全地渲染文档名和锚点；链接只能指向受控文档查看页，不得让模型返回任意 URL。

### 3.5 测试策略

- `DocumentChunkerTest`：不同 Markdown 结构的分段正确性
- `VectorStoreServiceTest`：检索准确性、索引版本原子切换、删除文档后不再命中（测试容器或独立测试 Redis）
- `KnowledgeQueryPipelineTest`：端到端知识问答（Mock LLM）
- `ChatIntentClassifierTest`：知识问答意图识别准确率
- 评测集：至少覆盖既有强类型规则查询、纯知识问答、冲突文档、删除文档和提示注入样本；评估召回率、答案准确率、来源正确率与拒答率

### 3.6 关键设计决策

1. **知识库和数据查询不混合**：`KNOWLEDGE_QUERY` 走 RAG，`BUSINESS_QUERY` 走 API——两者意图不同、数据源不同、验证方式不同，混合会增加复杂度
2. **文档为权威来源**：LLM 生成的知识回答必须基于检索到的文档片段，不允许超出文档范围发挥
3. **来源可追溯**：每个回答必须标注引用来源，让运营人员可以点击查看原文验证
4. **不引入重排序模型**：当前文档量（~110KB）不需要重排序层，向量检索 + LLM 组合已足够；后续文档量增长后再评估

---

## 4. 阶段 3：幻觉检测

### 4.1 目标

- 检测 LLM 生成内容中的事实性错误
- 数据类断言可反向校验
- 规则类断言可匹配 YAML 规则库
- 建议类输出明确标注"AI 建议"

### 4.2 技术方案

#### 4.2.1 分层检测策略

```
模型参与生成的诊断/知识回答
    ↓
[第 1 层] 结构化数据校验
    - 提取输出中的数字/日期/状态等数据断言
    - 反向调用对应 API 校验
    - 不匹配 → 标记为"数据可能不准确"
    ↓
[第 2 层] 业务规则校验
    - 提取输出中的规则引用
    - 匹配 YAML 规则库
    - 不匹配 → 标记为"规则描述可能不准确"
    ↓
[第 3 层] 建议标注
    - 识别建议类语句（"建议"、"可以尝试"、"推荐"）
    - 自动添加"AI 建议，请核实"标注
    ↓
输出：带标注的答案
```

#### 4.2.2 第 1 层：数据断言校验

**实现方式**：在模型输出进入展示层之前增加 `DataAssertionValidator`。强类型业务查询不走自由生成，`BusinessAnswerComposer` 只基于主系统受控 DTO 生成确定性话术，继续复用现有 `BusinessAnswerValidator`、facts 和 `BusinessResultValidator`，不再额外反向调用 API。

```java
public class DataAssertionValidator {
    /**
     * 从 LLM 输出中提取数据断言（数字、日期、状态），并反向校验。
     */
    public ValidationResult validate(String llmOutput, AgentQueryPlan plan) {
        // 1. 正则提取数据断言模式：
        //    - "XX 餐数为 5" → 校验餐数
        //    - "订单状态为已取消" → 校验状态
        //    - "2026-07-29 核销了 3 餐" → 校验核销记录
        // 2. 调用对应 API 获取真实数据
        // 3. 对比断言与真实数据
        // 4. 返回不匹配的断言列表
    }
}
```

**关键点**：仅对模型生成且包含当前受控 QueryPlan 的数据断言启用。知识问答也不能因“来自文档”而跳过校验：它至少要校验引用的文档版本、段落锚点和答案中的引用覆盖率；文档与实时业务状态冲突时必须提示以业务系统实时数据为准。

#### 4.2.3 第 2 层：规则断言校验

**实现方式**：扩展既有 `DiagnosisResultValidator` 对 ruleId、reasonCode 和 evidence 字段的校验，不新增只靠正则提取文本的平行校验链：

```java
public class RuleAssertionValidator {
    /**
     * 校验 LLM 输出中引用的诊断规则是否在 RuleRegistry 中存在。
     */
    public ValidationResult validate(String diagnosisOutput, RuleRegistry registry) {
        // 1. 校验结构化 ruleId、reasonCode 与 evidence
        // 2. 在 RuleRegistry 中查找对应规则和版本
        // 3. 不匹配时返回稳定错误或受控兜底，不展示原始模型断言
    }
}
```

#### 4.2.4 前端呈现

检测结果通过版本化的 `AgentChatResponse` 扩展字段返回，并同步更新 OpenAPI、主系统 DTO/快照和前端。对外仅返回事实引用 ID、规则 ID、版本和稳定校验状态；不要把可能包含敏感数据的原始断言或 `actual` 值作为通用诊断字段回传：

```json
{
  "content": "该客户剩余早餐数为 3 餐...",
  "validation": {
    "dataAssertions": [
      { "factId": "F1", "verified": true },
      { "factId": "F2", "verified": false, "reason": "VALUE_MISMATCH" }
    ],
    "confidence": "HIGH"
  }
}
```

前端可据此展示：
- ✅ 绿色：已验证的数据
- ⚠️ 黄色：无法验证的断言
- ❌ 红色：验证失败的断言

### 4.3 新增/修改文件

```
agent-service/src/main/java/me/zhengjie/agent/
├── validation/                                # 新包
│   ├── DataAssertionValidator.java            # 数据断言校验
│   ├── RuleAssertionValidator.java            # 规则断言校验
│   ├── SuggestionMarker.java                  # 建议标注
│   └── domain/
│       ├── ValidationResult.java              # 校验结果
│       └── DataAssertion.java                 # 数据断言
├── query/
│   └── BusinessAnswerValidator.java           # [修改] 复用强类型 facts 校验
├── summary/
│   └── TemplateDiagnosisSummaryService.java   # [修改] 仅展示已通过结构化校验的诊断摘要
└── domain/dto/
    └── AgentChatResponse.java                 # [修改] 增加 validation 字段
```

并同步修改 `agent-service-v2.yaml`、主系统 agent client/会话快照和前端展示契约。灰度期可只标记不阻断，但“校验失败”的原模型内容不得继续当作已核实事实展示。

---

## 5. 阶段 4：客户健康度评分

### 5.1 目标

为运营人员提供客户风险画像，辅助排查和决策。

### 5.2 评分维度

| 维度 | 指标 | 权重 | 数据来源 |
|------|------|------|---------|
| **排餐健康度** | 近 30 天应排餐次中的失败次数/失败率 | 30% | 主系统新增的客户维度历史排餐聚合接口 |
| **核销异常度** | 近 30 天应服务且已排餐次中的超期未核销率 | 20% | 主系统新增的客户维度核销聚合接口 |
| **退款风险** | 近 90 天退餐次数/有效订单数 | 25% | `meal_refund_log` 的主系统只读聚合接口 |
| **餐数紧张度** | 剩余餐数 / 总餐数比例 | 15% | customer_order |
| **过敏复杂度** | 过敏食物种类数 | 10% | customer_dietary_restrictions |

### 5.3 数据边界与计算方式

agent-service 不直连数据库，也不应从单日 `meal-plan-generation-snapshot` 推算 30 天客户历史。阶段 4 必须先由主系统提供受权限、部门数据范围和时间范围约束的**强类型聚合 API/tool**，返回每个分量的分子、分母、缺失原因、数据截至时间和口径版本；Agent 仅负责组合和展示。

“未核销”分母只包括实际应服务、已生成排餐且未被排除日期、停餐或退餐覆盖的餐次；不能把没有有效订单、未排餐或无需服务的自然日计为异常。过敏复杂度使用已启用的过敏/忌口条目并区分过敏与主动排除菜。

```java
public class CustomerHealthScorer {
    /**
     * 0-100 分，分数越低风险越高。
     */
    public HealthScore calculate(Long customerId) {
        // 根据主系统返回的版本化分子/分母计算各维度子分数（0-100）
        // 仅对数据完整的维度加权；缺失维度明确标记 UNKNOWN，不伪造总分
        // 返回分数、各维度明细、口径版本、数据截至时间和风险标签
    }
}
```

### 5.4 触发时机

1. **诊断时**：排餐失败诊断结果中附带客户健康度摘要
2. **查询时**：客户查询结果中附带健康度标签
3. **主动查询**：运营人员可单独查询客户健康度

### 5.5 新增/修改文件

```
agent-service/src/main/java/me/zhengjie/agent/
├── health/                                    # 新包
│   ├── CustomerHealthScorer.java              # 健康度评分计算
│   ├── HealthScoreService.java                # 服务层
│   └── domain/
│       ├── HealthScore.java                   # 评分结果
│       └── HealthDimension.java               # 评分维度定义
```

还需新增或修改：

- `eladmin-system`：健康度聚合查询 controller/service/DTO、权限与部门数据范围校验、对应 API 文档；不得暴露自由 SQL 或原始金额。
- `agent-service`：受控 tool descriptor、typed client、QueryPlan/capability、OpenAPI 和会话快照字段。
- 配置与评测：权重、阈值和口径版本必须可追溯；先用脱敏历史样本校准，并覆盖暂停、退餐、排除日期、无排餐、数据缺失等边界测试。

---

## 6. 实施顺序与依赖

```
阶段 1：多模型韧性（1 周）
    │  依赖：Anthropic/代理协议验收、双 provider 能力评测
    │
    ├──→ 阶段 2：RAG 知识检索（2-3 周）
    │       依赖：Redis Stack/Qdrant POC、本地 embedding 部署与数据安全评审
    │
    ├──→ 阶段 3：幻觉检测（2 周）
    │       依赖：阶段 1 多模型（需 LLM 做断言提取）
    │
    └──→ 阶段 4：客户健康度评分（2 周）
            依赖：主系统历史聚合 API、口径评审与脱敏样本校准；不依赖阶段 2
```

**并行空间**：阶段 2 和阶段 3 可部分并行（阶段 2 的 RAG 管道搭建和阶段 3 的校验框架不冲突）。

## 7. 风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| Redis 未部署可持久化的 Vector Search | RAG 索引不可用或重启丢失 | 阶段 2 POC 后选择已验收的独立向量存储；生产环境不静默降级为 InMemory |
| 选定 embedding provider 不可用或向量维度变更 | 文档无法向量化或旧索引不可用 | 以 provider/model/dimensions 版本构建新 collection，验收后原子切换 |
| Claude API 不在国内可用 | 备用 provider 不可用 | 使用国内可用的备用模型（如 Moonshot、Qwen） |
| 未审核文档或提示注入进入知识库 | 错误规则、敏感内容或恶意指令被检索 | 使用受评审 manifest、内容 hash、来源白名单和刷新前验收 |
| 多 provider 增加 API 费用 | 成本上升 | 备用 provider 仅在故障时启用；深度连通性仅由受控运维任务按需探测 |

## 8. 验证清单

```bash
# 阶段 1：多模型韧性
# 模拟 DeepSeek 不可用，验证自动切换到 Claude
AGENT_DEEPSEEK_BASE_URL=http://localhost:9999  # 不可达地址
mvn -q test -Dtest='*Fallback*Test'

# 阶段 2：RAG 知识检索
# 运行知识问答评测集
mvn -q test -Dtest='*Knowledge*Test'
# 提交知识库刷新（接口实施后；需要独立的入站管理员鉴权）
curl -X POST http://localhost:18081/api/internal/agent/knowledge/refresh \
  -H "X-Agent-Knowledge-Admin: ${AGENT_KNOWLEDGE_ADMIN_TOKEN}"

# 阶段 3：幻觉检测
mvn -q test -Dtest='*Validation*Test'

# 阶段 4：客户健康度
mvn -q test -Dtest='*HealthScore*Test'

# 全量回归
mvn -q test  # 确保新增测试不破坏现有 283 个测试
```
