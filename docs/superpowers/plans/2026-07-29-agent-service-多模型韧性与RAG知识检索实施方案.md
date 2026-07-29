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

#### 2.3.1 多 Provider 配置

Spring AI 支持同时配置多个 provider，通过限定符区分：

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
          model: ${AGENT_DEEPSEEK_MODEL:deepseek-chat}
    # 备用 provider：Anthropic Claude（OpenAI 兼容协议，通过代理）
    openai:
      api-key: ${AGENT_CLAUDE_API_KEY}
      base-url: ${AGENT_CLAUDE_BASE_URL:https://api.anthropic.com}
      chat:
        options:
          model: ${AGENT_CLAUDE_MODEL:claude-sonnet-4-20250514}
```

#### 2.3.2 AgentProperties 扩展：Provider 绑定

在 `AgentProperties.Models` 中增加 `providers` 配置，将 profile 绑定到具体 provider：

```java
public static class Models {
    private Map<String, ModelProfile> profiles = new LinkedHashMap<>(Map.of("default", new ModelProfile()));
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();
    private List<String> fallbackOrder = List.of("deepseek", "openai");
    // ...
}

public static class ProviderConfig {
    private String type = "openai-compatible"; // openai-compatible, anthropic
    private String apiKeyEnv;
    private String baseUrl;
    private boolean enabled = true;
    private int weight = 100; // 负载权重，暂不启用
}

public static class ModelProfile {
    // 现有字段...
    private String provider = "deepseek"; // 新增：绑定到哪个 provider
    private List<String> fallbackProviders = List.of(); // 新增：fallback 顺序
}
```

#### 2.3.3 Fallback 链实现

新增 `FallbackModelGateway` 包装 `AgentModelGateway`：

```
FallbackModelGateway implements AgentModelGateway
    ├── providerGateways: Map<String, AgentModelGateway>
    │   ├── "deepseek" → SpringAiAgentModelGateway(deepseek ChatClient)
    │   └── "openai"  → SpringAiAgentModelGateway(openai ChatClient)
    ├── fallbackOrder: ["deepseek", "openai"]
    ├── healthChecker: ProviderHealthChecker
    └── chatClient(profile):
        1. 读取 profile 绑定的 provider
        2. 按 provider → fallbackProviders 顺序尝试
        3. 健康检查 + 超时熔断
        4. 全部不可用 → 抛出 MODEL_UNAVAILABLE
```

**熔断策略**：
- 连续失败 3 次 → 标记 provider 为 unhealthy，冷却 30 秒
- 冷却期后自动半开（允许 1 次探测请求）
- 探测成功 → 恢复 healthy；失败 → 重新冷却

**健康检查**：
- 启动时对所有 provider 做一次轻量探测（发送简单 prompt，验证响应格式）
- 运行时通过 `ChatClient` 调用异常自动触发熔断
- 不引入独立的 health check 定时任务（避免额外 API 费用）

#### 2.3.4 配置示例

```yaml
agent:
  models:
    providers:
      deepseek:
        type: openai-compatible
        enabled: true
      claude:
        type: openai-compatible
        enabled: ${AGENT_CLAUDE_ENABLED:false}
        base-url: ${AGENT_CLAUDE_BASE_URL:https://api.anthropic.com}
    profiles:
      default:
        model: ${AGENT_MODEL_DEFAULT:deepseek-chat}
        provider: deepseek
        fallback-providers: [claude]
        timeout-ms: 3000
        structured-output: true
        tool-calling: true
      diagnosis:
        model: ${AGENT_MODEL_DIAGNOSIS:deepseek-chat}
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
| `AGENT_DEEPSEEK_MODEL` | `deepseek-chat` | DeepSeek 模型名 |
| `AGENT_CLAUDE_ENABLED` | `false` | 是否启用 Claude 备用 provider |
| `AGENT_CLAUDE_API_KEY` | 空 | Claude API Key |
| `AGENT_CLAUDE_BASE_URL` | `https://api.anthropic.com` | Claude API 地址 |
| `AGENT_CLAUDE_MODEL` | `claude-sonnet-4-20250514` | Claude 模型名 |

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

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| **Redis Vector Search** | 项目已有 Redis，无需新依赖 | 需 Redis 7.2+，功能有限 | ✅ 推荐 |
| Chroma（内嵌） | 零运维，Java 有 SDK | 新增依赖，数据不持久化 | 备选 |
| PGVector | 功能完整 | 项目用 MySQL，无 PG | 不适用 |
| 内存向量（InMemory） | 零依赖，启动快 | 仅适合小规模 | 开发/测试用 |

**推荐方案**：**分层策略**
- 开发/测试环境：内存向量存储（InMemoryVectorStore）
- 生产环境：Redis Vector Search（利用现有 Redis，需确认版本 ≥ 7.2）

#### 3.3.3 Embedding 模型

使用 DeepSeek Embedding API（与现有 LLM provider 一致）：

```yaml
spring:
  ai:
    deepseek:
      embedding:
        api-key: ${AGENT_DEEPSEEK_API_KEY}
        base-url: ${AGENT_DEEPSEEK_BASE_URL:https://api.deepseek.com}
        options:
          model: ${AGENT_DEEPSEEK_EMBEDDING_MODEL:deepseek-embedding}
```

**备选**：如果 DeepSeek Embedding 不可用，使用 BGE-small-zh（本地 CPU 推理，无需额外服务）。

#### 3.3.4 文档处理管道

```
eladmin/doc/business/*.md
    ↓
DocumentLoader（Markdown 解析、分段）
    ↓
DocumentChunker（按 ## 标题分段，每段 500-1500 tokens）
    ↓
DocumentMetadataEnricher（添加标题、文件名、最后修改时间）
    ↓
EmbeddingGenerator（调用 DeepSeek Embedding API）
    ↓
VectorStore（Redis / InMemory）
```

**分段策略**：
- 以 Markdown `##` 标题为自然边界
- 每段 500-1500 tokens（中文约 750-2250 字）
- 相邻段落保留 100 字重叠（overlap），确保跨段信息不丢失
- 表格保留完整（不截断表格）

**文档更新机制**：
- 启动时全量加载一次
- 提供手动刷新端点：`POST /api/internal/agent/knowledge/refresh`
- 后续可扩展为文件监听自动刷新

#### 3.3.5 意图扩展

新增意图 `KNOWLEDGE_QUERY`：

```java
public enum ChatIntent {
    // ... 现有意图 ...
    /** 知识问答：从业务文档中检索答案 */
    KNOWLEDGE_QUERY
}
```

意图分类器扩展：在 `RuleBasedIntentClassifier` 中增加知识问答的关键词模式（"怎么"、"如何"、"什么是"、"规则"、"流程"），在 `LlmIntentClassifier` 的 prompt 中增加 `KNOWLEDGE_QUERY` 类型。

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
  "type": "KNOWLEDGE_ANSWER",
  "content": "订单的剩余餐数分为早餐餐数和午餐晚餐餐数，两者独立计算。\n\n剩余早餐数 = 订单早餐数 - 已核销早餐数\n剩余午餐晚餐数 = 订单午餐晚餐数 - 已核销午餐数 - 已核销晚餐数",
  "sources": [
    {
      "document": "订单管理业务说明.md",
      "section": "订单剩余餐数计算规则",
      "relevance": 0.92
    }
  ]
}
```

### 3.4 新增文件

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

### 3.5 测试策略

- `DocumentChunkerTest`：不同 Markdown 结构的分段正确性
- `VectorStoreServiceTest`：检索准确性（@SpringBootTest，需 Redis）
- `KnowledgeQueryPipelineTest`：端到端知识问答（Mock LLM）
- `ChatIntentClassifierTest`：知识问答意图识别准确率
- 评测集：10 个典型知识问答 case，评估答案准确率和来源正确率

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
LLM 生成输出
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

**实现方式**：在 `BusinessAnswerComposer` 之后增加 `DataAssertionValidator`：

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

**关键点**：这个校验只在**数据查询类意图**中启用——因为只有这类意图的 LLM 输出才包含可校验的数据断言。知识问答类意图不需要此校验（知识回答来自文档，数据准确性由文档保证）。

#### 4.2.3 第 2 层：规则断言校验

**实现方式**：在诊断建议输出中，校验 LLM 引用的规则是否在 YAML 规则库中真实存在：

```java
public class RuleAssertionValidator {
    /**
     * 校验 LLM 输出中引用的诊断规则是否在 RuleRegistry 中存在。
     */
    public ValidationResult validate(String diagnosisOutput, RuleRegistry registry) {
        // 1. 提取诊断输出中的 reasonCode 引用
        // 2. 在 RuleRegistry 中查找对应规则
        // 3. 校验规则描述是否匹配
    }
}
```

#### 4.2.4 前端呈现

检测结果通过现有 `AgentChatResponse` 的扩展字段返回：

```json
{
  "content": "该客户剩余早餐数为 3 餐...",
  "validation": {
    "dataAssertions": [
      { "claim": "剩余早餐数为 3 餐", "verified": true },
      { "claim": "最近一次核销为 7月28日", "verified": false, "actual": "7月29日" }
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
│   └── BusinessAnswerComposer.java            # [修改] 集成校验
├── summary/
│   └── TemplateDiagnosisSummaryService.java   # [修改] 集成校验
└── domain/dto/
    └── AgentChatResponse.java                 # [修改] 增加 validation 字段
```

---

## 5. 阶段 4：客户健康度评分

### 5.1 目标

为运营人员提供客户风险画像，辅助排查和决策。

### 5.2 评分维度

| 维度 | 指标 | 权重 | 数据来源 |
|------|------|------|---------|
| **排餐健康度** | 近 30 天排餐失败次数 | 30% | meal_plan_generation_snapshot |
| **核销异常度** | 近 30 天未核销天数 | 20% | meal_verification_log |
| **退款风险** | 近 90 天退款次数 | 25% | meal_refund |
| **餐数紧张度** | 剩余餐数 / 总餐数比例 | 15% | customer_order |
| **过敏复杂度** | 过敏食物种类数 | 10% | customer_dietary_restrictions |

### 5.3 计算方式

```java
public class CustomerHealthScorer {
    /**
     * 0-100 分，分数越低风险越高。
     */
    public HealthScore calculate(Long customerId) {
        // 各维度分别计算子分数（0-100）
        // 加权求和得到总分
        // 返回分数 + 各维度明细 + 风险标签
    }
}
```

### 5.4 触发时机

1. **诊断时**：排餐失败诊断结果中附带客户健康度摘要
2. **查询时**：客户查询结果中附带健康度标签
3. **主动查询**：运营人员可单独查询客户健康度

### 5.5 新增文件

```
agent-service/src/main/java/me/zhengjie/agent/
├── health/                                    # 新包
│   ├── CustomerHealthScorer.java              # 健康度评分计算
│   ├── HealthScoreService.java                # 服务层
│   └── domain/
│       ├── HealthScore.java                   # 评分结果
│       └── HealthDimension.java               # 评分维度定义
```

---

## 6. 实施顺序与依赖

```
阶段 1：多模型韧性（1 周）
    │  无外部依赖，纯基础设施改造
    │
    ├──→ 阶段 2：RAG 知识检索（2-3 周）
    │       依赖：Embedding API、Redis 版本确认
    │
    ├──→ 阶段 3：幻觉检测（2 周）
    │       依赖：阶段 1 多模型（需 LLM 做断言提取）
    │
    └──→ 阶段 4：客户健康度评分（2 周）
            依赖：阶段 2 上线后可联动
```

**并行空间**：阶段 2 和阶段 3 可部分并行（阶段 2 的 RAG 管道搭建和阶段 3 的校验框架不冲突）。

## 7. 风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| Redis 版本 < 7.2 不支持 Vector Search | RAG 无法使用 Redis 存储 | 降级为 Chroma 内嵌模式或 InMemory |
| DeepSeek Embedding API 不可用 | 文档无法向量化 | 使用 BGE-small-zh 本地推理 |
| Claude API 不在国内可用 | 备用 provider 不可用 | 使用国内可用的备用模型（如 Moonshot、Qwen） |
| 业务文档格式不统一 | 分段质量差 | 文档预处理脚本 + 人工审核分段结果 |
| 多 provider 增加 API 费用 | 成本上升 | 备用 provider 仅在故障时启用，健康检查用轻量探测 |

## 8. 验证清单

```bash
# 阶段 1：多模型韧性
# 模拟 DeepSeek 不可用，验证自动切换到 Claude
AGENT_DEEPSEEK_BASE_URL=http://localhost:9999  # 不可达地址
mvn -q test -Dtest='*Fallback*Test'

# 阶段 2：RAG 知识检索
# 运行知识问答评测集
mvn -q test -Dtest='*Knowledge*Test'
# 手动刷新知识库
curl -X POST http://localhost:18081/api/internal/agent/knowledge/refresh

# 阶段 3：幻觉检测
mvn -q test -Dtest='*Validation*Test'

# 阶段 4：客户健康度
mvn -q test -Dtest='*HealthScore*Test'

# 全量回归
mvn -q test  # 确保新增测试不破坏现有 283 个测试
```