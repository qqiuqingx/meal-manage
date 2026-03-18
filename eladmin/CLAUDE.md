# CLAUDE.md

This file provides guidance to Claude Code when working with the ELADMIN backend codebase.

## Project Structure

```
eladmin/                           # Backend root (Spring Boot)
├── eladmin-common/                 # Shared utilities and config
│   ├── annotation/                # Custom annotations (@Limit, @Anonymous)
│   ├── aspect/                     # AOP aspects
│   ├── base/                       # BaseEntity
│   ├── config/                     # Redis, Mybatis-Plus, Security, Web
│   ├── exception/                  # Exception classes + GlobalExceptionHandler
│   └── utils/                      # EncryptUtils, RedisUtils, StringUtils, etc.
├── eladmin-logging/                # Async logging (@AsyncLog)
├── eladmin-system/                 # Main application module
│   └── src/main/java/me/zhengjie/
│       ├── AppRun.java             # Entry point
│       ├── modules/                # Business modules
│       │   ├── system/             # User, Role, Menu, Dept, Job, Dict
│       │   ├── security/           # Authentication, JWT
│       │   ├── quartz/              # Scheduled tasks
│       │   ├── maint/               # System maintenance
│       │   └── meal/                # Custom: Dietary restrictions, dishes
│       └── config/                  # System-specific config
├── eladmin-tools/                  # Email, S3 storage, Alipay
└── eladmin-generator/              # Code generator
```

## Commands

### Build

```bash
# Build all modules
mvn clean install -DskipTests

# Build specific module
mvn clean package -DskipTests -pl eladmin-system

# Run (dev profile is default)
java -jar eladmin-system/target/eladmin-system-1.1.jar

# Run with custom profile
java -jar eladmin-system/target/eladmin-system-1.1.jar --spring.profiles.active=dev

# Run tests (disabled by default)
mvn test
mvn test -DskipTests=false
```

### Development

- Server runs on port 8000
- Swagger/Knife4j docs at `/doc.html`
- Default profile: `dev`

## Key Patterns

### JSON Serialization
Uses **fastjson2**, NOT Jackson. Custom serializers in `eladmin-common` config.

### Business Module Structure

```
modules/<module>/
├── domain/
│   ├── <Entity>.java              # Entity + Table annotations
│   ├── dto/                       # QueryCriteria, Result DTOs
│   └── enums/                     # Enums
├── mapper/
│   ├── <Entity>Mapper.java        # Mybatis-Plus Mapper interface
│   └── <Entity>Mapper.xml         # Custom SQL
├── rest/
│   └── <Entity>Controller.java   # REST endpoints
└── service/
    ├── <Entity>Service.java       # Service interface
    └── impl/
        └── <Entity>ServiceImpl.java
```

### Database Access
- MyBatis-Plus for ORM
- QueryCriteria classes for dynamic queries
- XML mappers in `eladmin-system/src/main/resources/mapper/`

### API Documentation
All new APIs must be documented in `doc/` as Markdown files.

### Security
- JWT + Spring Security
- Custom annotations: `@Anonymous` for public endpoints
- Rate limiting: `@Limit` annotation

## Common Utilities

| Utility | Purpose |
|---------|---------|
| `SecurityUtils` | Get current user ID/name |
| `RedisUtils` | Cache operations |
| `EncryptUtils` | AES encryption |
| `RequestHolder` | Access HTTP request/response |
| `PageUtil` / `PageResult` | Pagination |

## Configuration

- Config files: `eladmin-system/src/main/resources/config/application*.yml`
- Active profile set in `application.yml`: `spring.profiles.active: dev`
- RSA keys for password encryption in `application.yml`
- Redis via env vars: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PWD`, `REDIS_DB`

## Business Modules

- **system**: User, Role, Menu, Dept, Job, Dict, Logs
- **security**: Auth, JWT token handling
- **quartz**: Scheduled jobs
- **maint**: System maintenance (online users, SQL监控)
- **meal**: Custom dietary restrictions and dishes module
