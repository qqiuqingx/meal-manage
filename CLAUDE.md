# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ELADMIN is a前后端分离的后台管理系统 (frontend-backend separated admin system) based on Spring Boot 2.7.18 + Mybatis-Plus + Vue.js.

## Directory Structure

```
eladmin-mp/
├── eladmin/           # Backend (Spring Boot)
│   ├── eladmin-common/      # Common utilities, annotations, config
│   │   ├── annotation/      # Custom annotations (@Limit, @Anonymous, etc.)
│   │   ├── aspect/          # AOP aspects for annotations
│   │   ├── base/            # Entity base class
│   │   ├── config/          # Redis, Mybatis-Plus, Security, CORS config
│   │   ├── exception/       # Global exception handling
│   │   └── utils/           # EncryptUtils, RedisUtils, StringUtils, etc.
│   ├── eladmin-logging/     # Logging module (@AsyncLog, LogService)
│   ├── eladmin-system/       # Core module
│   │   └── src/main/java/me/zhengjie/
│   │       ├── AppRun.java          # Application entry point
│   │       ├── config/               # System-specific config
│   │       └── modules/              # Business modules (system/, meal/, etc.)
│   ├── eladmin-tools/       # Third-party integrations (email, S3, Alipay)
│   └── eladmin-generator/   # Code generator (backend + frontend)
├── eladmin-web/       # Frontend (Vue 2.7 + element-ui)
└── sql/               # Database scripts
```

## Commands

### Backend (Maven)

```bash
# Build all modules (from eladmin-mp root)
mvn clean install -DskipTests

# Build only backend
cd eladmin && mvn clean install -DskipTests

# Run development (activates dev profile)
java -jar eladmin-system/target/eladmin-system-1.1.jar

# Run with specific profile
java -jar eladmin-system/target/eladmin-system-1.1.jar --spring.profiles.active=dev

# Run specific module
mvn clean package -DskipTests -pl eladmin-system

# Run tests (disabled by default in pom.xml)
mvn test
mvn test -DskipTests=false
```

**Important**: Tests are skipped by default in `pom.xml` (`maven-surefire-plugin` with `<skip>true</skip>`).

### Frontend (Node.js)

```bash
cd eladmin-web

# Install dependencies
npm install

# Development
npm run dev

# Build production
npm run build:prod

# Build staging
npm run build:stage

# Lint
npm run lint
```

**Note**: Use `NODE_OPTIONS=--openssl-legacy-provider` for Node.js 17+ compatibility.

## Key Technologies

- **Backend**: Spring Boot 2.7.18, Java 8, Mybatis-Plus 3.5.3.1, Spring Security, JWT, Redis (Lettuce), Redisson
- **Database**: MySQL 9.2.0, Druid 1.2.19
- **Serialization**: fastjson2 (NOT Jackson - it's excluded in pom.xml)
- **Frontend**: Vue 2.7.16, element-ui 2.15.14, Vuex 3.1, vue-router 3.0

## Configuration

- Backend config: `eladmin-system/src/main/resources/config/application*.yml`
- Active profile: `application.yml` sets `spring.profiles.active: dev`
- Frontend config: Environment variables in `.env.*` files
- Server: Port 8000, HTTP/2 enabled, GZIP compression enabled
- Frontend: Port 8013 (see `eladmin-web/vue.config.js`)
- Default login: admin / REDACTED_PASSWORD
- Dev验证码: 输入 `REDACTED_CODE` 可跳过验证码校验 (see `AuthController.java`)

### Environment Variables

Backend reads from:
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PWD`, `REDIS_DB`

## Git Commit

- **提交描述使用中文**，描述本次改动的内容和原因
- type 使用英文：`feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci`
- 示例：`feat: 新增套餐编号池功能`

## Important Patterns

- **JSON Serialization**: Uses fastjson2, NOT Jackson. Custom serializers go in `eladmin-common` config.
- **Security**: JWT + Spring Security. Token-based authentication.
- **API Documentation**:
  - Knife4j (Swagger) available at `/doc.html` (runtime)
  - **All new/modified APIs must be documented in `doc/apidoc/` directory as Markdown files**
  - Example: `eladmin/doc/apidoc/客户饮食限制接口文档.md`
  - Current API docs:
    - `eladmin/doc/apidoc/菜品管理接口文档.md`
    - `eladmin/doc/apidoc/客户档案管理接口文档.md`
    - `eladmin/doc/apidoc/客户订单管理接口文档.md`
    - `eladmin/doc/apidoc/客户统计接口文档.md`
    - `eladmin/doc/apidoc/客户饮食限制接口文档.md`
    - `eladmin/doc/apidoc/核销管理接口文档.md`
    - `eladmin/doc/apidoc/排餐计划接口文档.md`
    - `eladmin/doc/apidoc/排餐计划生成接口.md`
    - `eladmin/doc/apidoc/订单排餐日历接口文档.md`
    - `eladmin/doc/apidoc/父套餐餐数统计接口.md`
- **Business Documentation**:
  - Business docs are in `doc/business/` directory
  - Read these before modifying business logic — they explain concepts, table relationships, and cross-module dependencies
  - Current business docs:
    - `eladmin/doc/business/配菜管理业务说明.md` — 菜品主档、配料字典、排期生成、忌口过滤
    - `eladmin/doc/business/套餐管理业务说明.md` — 父子套餐结构、编号池、套餐与餐品线的关系
    - `eladmin/doc/business/客户管理业务说明.md` — 客户档案、地址、套餐分类、签约记录、剩余餐数计算
    - `eladmin/doc/business/订单管理业务说明.md` — 订单生命周期、餐数体系、金额体系、排餐模式、核销联动
    - `eladmin/doc/business/排餐管理业务说明.md` — 三层表结构、scheduleKey、生效订单过滤、幂等生成、过敏过滤、子套餐规格选菜
    - `eladmin/doc/business/核销管理业务说明.md` — 核销链路校验、自动完单、金额扣减、日志快照、批量核销
- **Code Generation**: Both backend and frontend CRUD code can be generated via the generator module

## Adding New Business Modules

Typical structure for a new module (e.g., `meal`):
```
eladmin-system/src/main/java/me/zhengjie/modules/meal/
├── domain/
│   ├── Dish.java              # Entity
│   ├── dto/                   # DTOs
│   └── enums/                 # Enums
├── mapper/                    # Mybatis-Plus Mapper
│   ├── DishMapper.java
│   └── DishMapper.xml
├── rest/                     # Controllers
│   └── DishController.java
└── service/                  # Service layer
    ├── DishService.java
    └── impl/
        └── DishServiceImpl.java
```

### Database Access
- MyBatis-Plus is used for database operations
- XML mappers go in `eladmin-system/src/main/resources/mapper/`
- Use `QueryCriteria` classes for dynamic query building
- **禁止使用 Docker 查询数据库**，直接用 Python pymysql 或其他本地工具

## Business Modules

- **meal**: 饮食限制模块 (customer_dietary_restrictions 表)
  - Code: `eladmin-system/src/main/java/me/zhengjie/modules/meal/`
  - API Doc: `eladmin/doc/apidoc/客户饮食限制接口文档.md`

## Business Rules

### 订单剩余餐数计算规则

**餐数分类**：早餐餐数和午餐+晚餐餐数是区分开的，独立计算。

**剩余餐数计算公式：**
```
剩余早餐数 = 订单早餐数 - 已核销早餐数
剩余午餐晚餐数 = 订单午餐晚餐数 - 已核销午餐数 - 已核销晚餐数
```

**数据来源（订单维度）：**
- 订单表字段：`CustomerOrder.breakfastCount`, `CustomerOrder.lunchDinnerCount`
- 核销统计：通过 `MealVerificationLog` 表按 `mealType` + `orderID` 分组统计已核销数量
- 计算位置：`CustomerProfileServiceImpl.fillLatestOrderInfo()` 方法

**使用场景（客户维度）：**
- 客户档案视图中显示该客户最新订单的剩余餐数
- 通过 `customerOrderMapper.findLatestByCustomerId(customerId)` 获取最新订单
- 使用该订单的 ID 统计核销数量并计算剩余餐数

**核销类型映射：**
- `BREAKFAST` → 计入早餐核销数
- `LUNCH` → 计入午餐晚餐核销数
- `DINNER` → 计入午餐晚餐核销数

**业务规则说明：**
- 剩余餐数基于订单表字段和核销记录实时计算，非订单表存储字段
- 午餐和晚餐共享一个餐数池（lunchDinnerCount），核销时累加计算
- 不依赖排餐记录的创建或删除
- 删除排餐记录时不会影响剩余餐数（因为排餐不消耗餐数，仅核销消耗）
- 剩余餐数最小值为 0（使用 `Math.max(..., 0)` 保证）
