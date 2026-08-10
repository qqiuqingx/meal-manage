# Phase 01：版本基线与依赖对齐

## 目标

先把 `agent-service` 的运行基线切换到 Spring Boot 4.0.7 + Spring AI 2.0.0，确保 Maven 依赖解析清晰、版本唯一，为后续源码迁移提供稳定输入。

## 输入

- 当前 `agent-service/pom.xml`。
- JDK 17。
- Maven 3.9.9。
- 当前工作区状态快照。

## 涉及文件

修改：

- `agent-service/pom.xml`

本阶段不修改 Java 源码和运行配置。由于 API 尚未迁移，本阶段允许编译失败，但依赖解析必须成功，且失败原因只能是已知的 Spring AI 2 API 变化。

## 实施步骤

### Step 1：记录升级前基线

在 `agent-service/` 下记录：

```bash
git status --short
mvn -version
mvn clean test
mvn dependency:tree -Dincludes=org.springframework.ai,org.springframework.boot,org.springframework,com.fasterxml.jackson
```

要求：

- 确认 Java 为 17，Maven 为 3.9.9。
- 保存当前测试数量和失败情况；若升级前已有失败，先区分既有问题，不把它误判为升级回归。
- 记录工作区已有修改，尤其是 `BusinessAgentRunner`、Presentation 和 Tool 相关文件，后续不得覆盖。

### Step 2：更新版本属性

在 `agent-service/pom.xml` 中：

- `spring.boot.version` 从 `3.5.14` 改为 `4.0.7`。
- `spring.ai.version` 从 `1.1.6` 改为 `2.0.0`。
- 保持 `java.version` 为 `17`。
- 保持 Spring Boot Maven Plugin 与 `${spring.boot.version}` 一致。

不得在子依赖上散落 Spring AI 或 Spring Boot 版本，继续由两个 BOM 统一管理。

### Step 3：迁移 Boot 4 Web Starter

将：

```xml
spring-boot-starter-web
```

替换为：

```xml
spring-boot-starter-webmvc
```

原因：Boot 4 将 WebMVC Starter 拆分得更明确，旧 Starter 已不适合作为新基线。

### Step 4：加入 Jackson 2 过渡依赖

增加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-jackson2</artifactId>
</dependency>
```

保留现有 `jackson-dataformat-yaml`，因为规则加载仍依赖 Jackson 2 的 YAMLMapper。不要在本阶段做 Jackson 包名的批量替换。

### Step 5：保留模型依赖边界

继续保留：

- `spring-ai-starter-model-deepseek`：主 Provider 自动配置。
- `spring-ai-openai`：仅供手工构造 OpenAI-compatible 备用 Provider。

不得改成 OpenAI Starter，否则可能创建第二套自动配置的 ChatModel，与 DeepSeek 主模型冲突。

### Step 6：检查依赖树

执行：

```bash
mvn -DskipTests dependency:tree \
  -Dincludes=org.springframework.ai,org.springframework.boot,org.springframework,com.fasterxml.jackson
```

检查：

- Spring AI 统一为 2.0.0。
- Spring Boot 统一为 4.0.7。
- Spring Framework 主版本为 7.x。
- 不存在运行时 Spring AI 1.x、Boot 3.x、Framework 6.x。
- Jackson 2 依赖来自明确的兼容模块，而不是偶然传递依赖。
- 没有手工引入旧版 `spring-retry` 来掩盖 API 迁移问题。

### Step 7：记录预期编译差异

执行一次：

```bash
mvn -DskipTests compile
```

本阶段预期可能出现以下编译错误：

- `ChatOptions` 改为接收 Builder。
- `OpenAiApi` 类型不存在。
- `RetryTemplate` 不再适用于当前模型 Builder。
- OpenAI ChatModel 的旧 `defaultOptions`、`openAiApi` 构造方式失效。

若出现上述列表之外的大范围错误，停止进入 Phase 02，先更新本计划的风险和影响范围。

## 验证方式

- Maven 能成功解析 POM 和依赖树。
- 版本检查结果符合目标矩阵。
- 编译失败仅来自 Phase 02 已列明的模型 API 迁移点。
- `git diff -- agent-service/pom.xml` 只包含本阶段依赖调整。
- `git diff --check` 通过。

## 完成标准

- Boot 4.0.7 和 Spring AI 2.0.0 已由 BOM 统一管理。
- Web Starter 与 Jackson 2 过渡依赖已正确配置。
- 依赖树不存在旧代框架混用。
- 已保存升级前测试基线和当前工作区快照。
- 未修改 `eladmin/`、`eladmin-web/`、SQL 或 Agent 业务逻辑。

## 回滚点

只回退 `agent-service/pom.xml` 即可恢复 Phase 01 前状态。若已进入 Phase 02，则不得单独回退 POM，必须按总回滚策略整体回退。

## 状态

completed
