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

- **Backend**: Spring Boot 2.7.18, Java 17, Mybatis-Plus 3.5.3.1, Spring Security, JWT, Redis (Lettuce), Redisson
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

## Important Patterns

- **JSON Serialization**: Uses fastjson2, NOT Jackson. Custom serializers go in `eladmin-common` config.
- **Security**: JWT + Spring Security. Token-based authentication.
- **API Documentation**:
  - Knife4j (Swagger) available at `/doc.html` (runtime)
  - **All new/modified APIs must be documented in `doc/` directory as Markdown files**
  - Example: `eladmin/doc/客户饮食限制接口文档.md`
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
  - API Doc: `eladmin/doc/客户饮食限制接口文档.md`
