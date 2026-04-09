# 智能助手 Agent 实施计划

## 一、技术方案概述

### 1.1 技术选型

| 组件 | 选型 | 说明 |
|------|------|------|
| AI 框架 | LangChain4j 0.35.0 | Java 版 LangChain |
| LLM | MiniMax API | abab6.5s-chat 模型 |
| 对话存储 | Redis | 多轮会话上下文 |
| 流式输出 | SSE | Server-Sent Events |

### 1.2 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    Vue Chat 前端                         │
│        (SSE 流式响应 + 工具执行结果卡片展示)            │
└─────────────────────────────────────────────────────────┘
                            ↓ HTTP POST /api/agent/chat
┌─────────────────────────────────────────────────────────┐
│              Spring Boot 后端                           │
│                                                         │
│  ┌─────────────────────────────────────────────────┐  │
│  │     AgentController                              │  │
│  │     - POST /api/agent/chat (单轮)               │  │
│  │     - GET  /api/agent/stream (SSE 流式)         │  │
│  └─────────────────────────────────────────────────┘  │
│                       ↓                               │
│  ┌─────────────────────────────────────────────────┐  │
│  │     AgentService (核心编排层)                    │  │
│  │     - ChatMemory (多轮对话上下文)                 │  │
│  │     - AiServices (LLM 调用封装)                  │  │
│  │     - ToolExecutorRegistry (工具注册表)           │  │
│  └─────────────────────────────────────────────────┘  │
│                       ↓                               │
│  ┌─────────────────────────────────────────────────┐  │
│  │     工具层 (LangChain4j @Tool)                   │  │
│  │     - CustomerTool (客户 CRUD)                   │  │
│  │     - DietaryTool (饮食限制)                     │  │
│  │     - MealPlanTool (排餐计划)                    │  │
│  └─────────────────────────────────────────────────┘  │
│                       ↓                               │
│  ┌─────────────────────────────────────────────────┐  │
│  │     MiniMax LLM (abab6.5s-chat)                │  │
│  └─────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## 二、实施阶段

### Phase 1：基础框架搭建（1天）

#### 1.1 依赖引入

在 `eladmin-common/pom.xml` 添加：

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-bom</artifactId>
    <version>0.35.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

#### 1.2 配置文件

`eladmin-system/src/main/resources/config/application-agent.yml`:

```yaml
langchain4j:
  minimax:
    base-url: https://api.minimax.chat/v1
    api-key: ${MINIMAX_API_KEY:}
    model: abab6.5s-chat
    temperature: 0.7
  chat-memory:
    max-messages: 20
```

#### 1.3 目录结构

```
modules/agent/
├── config/
│   └── AgentConfig.java              # LangChain4j + MiniMax 配置
├── domain/
│   ├── ChatSession.java              # 会话实体
│   └── dto/
│       ├── AgentChatRequest.java
│       └── AgentChatResponse.java
├── rest/
│   └── AgentController.java
├── service/
│   ├── AgentService.java
│   ├── tools/
│   │   ├── AbstractTool.java         # 工具基类
│   │   ├── CustomerTool.java         # 客户管理工具
│   │   ├── DietaryTool.java          # 饮食限制工具
│   │   └── ToolRegistry.java         # 工具注册表
│   └── memory/
│       └── RedisChatMemory.java      # Redis 会话存储
└── prompt/
    └── system-prompt.md              # 系统提示词
```

---

### Phase 2：核心服务实现（1.5天）

#### 2.1 MiniMax ChatModel 配置

```java
@Configuration
public class AgentConfig {

    @Value("${langchain4j.minimax.api-key}")
    private String apiKey;

    @Bean
    public ChatLanguageModel minimaxChatModel() {
        return OpenAiChatModel.builder()
            .baseUrl("https://api.minimax.chat/v1")
            .apiKey(apiKey)
            .modelName("abab6.5s-chat")
            .temperature(0.7)
            .maxTokens(2048)
            .build();
    }
}
```

#### 2.2 AI Service 定义（Tool Calling）

```java
public interface CustomerAssistant {

    @UserSystemPrompt("""
        你是一个专业的客户档案管理助手。
        用户的任务是帮助客户新增、查询、修改客户档案。

        可用工具：
        - create_customer: 创建新客户，需要姓名、手机号
        - search_customer: 按条件搜索客户
        - get_customer_detail: 查看客户详情
        - update_customer: 修改客户信息

        规则：
        1. 新增客户前必须确认所有必填信息
        2. 修改前展示差异，让用户确认
        3. 返回结果要清晰明确
        """)
    String chat(@UserMessage String userMessage);
}
```

#### 2.3 工具实现

```java
public class CustomerTool {

    @Tool("创建新客户档案")
    public String createCustomer(
        @ToolParam("客户姓名") String name,
        @ToolParam("手机号码") String phone,
        @ToolParam("性别") String gender,
        @ToolParam("年龄") Integer age,
        @ToolParam("地址") String address) {

        // 调用现有 CustomerService
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setGender(gender);
        customer.setAge(age);
        customer.setAddress(address);

        Customer created = customerService.create(customer);
        return "客户创建成功，ID: " + created.getId();
    }

    @Tool("搜索客户列表")
    public String searchCustomer(
        @ToolParam("客户姓名") String name,
        @ToolParam("手机号") String phone,
        @ToolParam("页码") Integer page,
        @ToolParam("每页条数") Integer size) {

        // 调用现有 CustomerService.query()
        return result;
    }
}
```

---

### Phase 3：前端 Chat 组件（1.5天）

#### 3.1 页面结构

```
eladmin-web/src/views/agent/
├── index.vue              # 主对话页面
├── components/
│   ├── ChatMessage.vue    # 消息气泡
│   ├── ToolResult.vue     # 工具执行结果卡片
│   └── ConfirmDialog.vue  # 确认弹窗
```

#### 3.2 核心功能

- SSE 流式输出（`EventSource`）
- 消息类型：text / tool_result / confirm_request
- 快捷指令按钮："新增客户" / "查询客户" / "我的客户"
- 操作确认弹窗（新增/修改前）

#### 3.3 交互示例

```
用户：帮我新增客户
AI：好的，请提供客户信息：
    - 姓名：？
    - 手机号：？

用户：张三，13800138000
AI：[调用 create_customer 工具]
    ✅ 客户张三创建成功！
    客户编号：C-2026-001
```

---

### Phase 4：安全与优化（0.5天）

- 操作日志记录（`@AsyncLog`）
- 敏感信息脱敏
- Redis 会话超时（默认 30 分钟）
- API 限流

---

## 三、文件清单

### 后端新增

| 文件路径 | 描述 |
|----------|------|
| `eladmin-common/pom.xml` | 添加 langchain4j 依赖 |
| `eladmin-system/.../config/application-agent.yml` | Agent 配置 |
| `eladmin-system/.../modules/agent/config/AgentConfig.java` | LangChain4j + MiniMax 配置 |
| `eladmin-system/.../modules/agent/rest/AgentController.java` | REST 接口 |
| `eladmin-system/.../modules/agent/service/AgentService.java` | 核心服务 |
| `eladmin-system/.../modules/agent/service/tools/CustomerTool.java` | 客户管理工具 |
| `eladmin-system/.../modules/agent/service/tools/DietaryTool.java` | 饮食限制工具 |
| `eladmin-system/.../modules/agent/prompt/system-prompt.md` | 系统提示词 |

### 前端新增

| 文件路径 | 描述 |
|----------|------|
| `eladmin-web/src/views/agent/index.vue` | 对话页面 |
| `eladmin-web/src/views/agent/components/ChatMessage.vue` | 消息组件 |
| `eladmin-web/src/views/agent/components/ToolResult.vue` | 工具结果卡片 |
| `eladmin-web/src/router/agent.js` | 路由配置 |

---

## 四、待确认事项

1. **API Key**：MiniMax API Key 由你提供，配置到环境变量？
2. **现有 CustomerService**：确认接口方法名（create/query/update/findById）
3. **入口**：独立菜单"智能助手"还是右下角悬浮？