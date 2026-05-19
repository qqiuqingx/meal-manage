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

## Git 提交规范

- Commit message 必须使用中文描述，包括普通提交和 amend 提交。
- 可以保留 `feat:`、`fix:`、`docs:` 等 conventional commit 类型前缀，但冒号后的描述必须为中文。

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
**IMPORTANT**: Any API change (add, update, delete) MUST be reflected in the corresponding Markdown doc in `doc/` directory immediately after the code change.

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

## Database

**MySQL** (127.0.0.1:13306/mydatabase) — 查询方式为 Python + pymysql：

```python
import pymysql

conn = pymysql.connect(
    host='127.0.0.1', port=13306,
    user='root', password='change-me',
    database='mydatabase'
)
cur = conn.cursor()

# 查询
cur.execute('SELECT id, name, dish_type FROM dish LIMIT 10')
for row in cur.fetchall(): print(row)

# 更新（需 commit）
cur.execute('UPDATE dish SET schedule = %s WHERE id = %s', (new_value, dish_id))
conn.commit()

cur.close()
conn.close()
```

> 连接凭据（`change-me`）可从 `~/.claude/mcp-database-config.yml` 中查看。

### 核心业务表

| 表名 | 说明 |
|------|------|
| `dish` | 菜品表（name, dish_type, meal_types, meal_packages, schedule 等 JSON 字段） |
| `dish_ingredient` | 菜品-食材关联表 |
| `dish_ingredient_relation` | 关联关系表 |
| `dish_schedule_record` | 排餐记录（record_date, meal_type, week_num, day_of_week） |
| `customer_dietary_restrictions` | 客户饮食限制（姓名、套餐、忌口、有效期、剩余餐数） |
| `customer_keywords` | 客户关键词 |
| `customer_menu_record` | 客户菜单记录（每餐选了哪些菜、是否被替换） |

### JSON 字段说明

`dish.schedule` 格式为 `["W-D", ...]`，W=周序号(1-4)，D=星期几(1-7)：
- `["1-1", "2-1", "3-1", "4-1"]` 表示每周一出现
- `["3-5"]` 表示第 3 周周四出现

`dish.meal_packages` / `customer.meal_package` 套餐代码：
- `yuezi` 月子餐 / `yunqi` 孕期餐 / `xiaoyuezi` 小月子 / `yingyang` 营养餐 / `fenmian` 分娩餐

## Code Style

### Java

**文件头注释（License）：**
```java
/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  ...
 */
```

**类注释格式（业务类）：**
```java
/**
 * 描述
 * @author Zheng Jie   // 系统原有模块
 * @author qqx        // 自定义新模块
 * @date 2026-03-14
 **/
```

**Entity 类：**
- 使用 Lombok `@Getter @Setter`（不用 `@Data`，避免 toString/equals/hashCode 冲突）
- 继承 `BaseEntity`
- 主键用 `@TableId(type = IdType.AUTO)`
- 非数据库字段用 `@TableField(exist = false)`
- 校验注解在字段上：`@NotBlank`、`@NotNull`、`@Email`

```java
@Getter @Setter
@TableName("dish")
public class Dish extends BaseEntity implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Integer id;

    @NotBlank(message = "菜品名称不能为空")
    @ApiModelProperty(value = "菜品名称")
    @TableField("name")
    private String name;

    @TableField(exist = false)
    @ApiModelProperty(value = "配料列表（新增/编辑时使用）")
    private List<DishIngredientDto> ingredientList;
}
```

**校验分组（BaseEntity 内）：**
```java
public @interface Create {}
public @interface Update {}
// 使用：@Validated(User.Update.class)
```

**Controller 注解顺序：**
```java
@Api(tags = "菜品管理")
@RestController
@RequestMapping("/api/dishes")
@RequiredArgsConstructor
public class DishController { ... }
```

**Controller 方法注解顺序：**
```java
@Log("新增菜品")        // 先 @Log（若适用）
@ApiOperation("新增菜品")
@GetMapping / @PostMapping / @PutMapping / @DeleteMapping
@PreAuthorize("@el.check('dish:add')")   // 权限检查
public ResponseEntity<Object> createDish(...) { ... }
```

**CRUD 方法命名（Controller）：**
| 操作 | 方法 | HTTP | 返回值 |
|------|------|------|--------|
| 分页查询 | `queryDish` | GET | `ResponseEntity<PageResult<T>>` |
| 详情查询 | `queryDishById` | GET `/{id}` | `ResponseEntity<T>` |
| 新增 | `createDish` | POST | `ResponseEntity<>(HttpStatus.CREATED)` |
| 修改 | `updateDish` | PUT | `ResponseEntity<>(HttpStatus.NO_CONTENT)` |
| 删除 | `deleteDish` | DELETE | `ResponseEntity<>(HttpStatus.OK)` |
| 导出 | `exportDish` | GET `/download` | `void` + `HttpServletResponse` |

**分页（Controller → Service）：**
```java
// Controller：前端 0 基，MyBatis-Plus 1 基，需 +1 转换
Page<Object> page = new Page<>(criteria.getPage(), criteria.getSize());
return new ResponseEntity<>(dishService.queryAll(criteria, page), HttpStatus.OK);
```

**Service 注解：**
```java
@Service
@RequiredArgsConstructor   // 构造器注入，不要 @Autowired 字段
@Slf4j
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {
```

**事务注解：**
```java
@Transactional(rollbackFor = Exception.class)
```

**异常处理：**
- 业务异常：`throw new BadRequestException("提示信息")`
- 重复数据：`throw new EntityExistException(Entity.class, "field", value)`

**枚举类：**
```java
@Getter
@AllArgsConstructor
public enum DishTypeEnum {
    MAIN("MAIN", "主菜"),
    SIDE("SIDE", "副菜");

    private final String code;
    private final String desc;

    @JsonValue
    public String getDesc() { return desc; }

    public static DishTypeEnum fromCode(String code) {
        for (DishTypeEnum e : values()) {
            if (e.getCode().equals(code)) return e;
        }
        return null;
    }
}
```

**常量定义（ServiceImpl 内部）：**
```java
private static final String[] DISH_TYPES = {"MAIN", "SIDE", "SOUP"};
private static final Map<String, String> MEAL_TYPE_CN = new LinkedHashMap<>();
static {
    MEAL_TYPE_CN.put("LUNCH", "午餐");
}
```

**日志：**
- 使用 `@Slf4j` 生成 `log`，不在类中手动创建 Logger
- 通过 `@Log` 注解记录操作日志，不在方法内手动写 `log.info`

---

### XML Mapper

**文件位置：** `eladmin-system/src/main/resources/mapper/`

**SQL 关键字：** 小写

**条件判断：**
```xml
<where>
    <if test="criteria.name != null">
        and name like concat('%', #{criteria.name}, '%')
    </if>
    <if test="criteria.mealType != null">
        and JSON_CONTAINS(meal_types, CONCAT('"', #{criteria.mealType}, '"')) = 1
    </if>
</where>
order by sort asc, id desc
```

**字段映射（JSON List）：**
```xml
<result column="meal_types" property="mealTypes" typeHandler="com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler"/>
```

---

### Vue

**目录结构：**
```
src/
├── api/
│   ├── dish.js               # 一个实体一个文件
│   └── customer/
│       └── order.js
└── views/
    ├── meal/
    │   ├── dish/
    │   │   ├── index.vue    # 列表页
    │   │   └── dish.vue      # 表单弹窗组件（子文件）
    │   └── dishIngredient/
    └── customer/
        └── profile/
            └── index.vue
```

**API 文件（`src/api/*.js`）：**
```javascript
import request from '@/utils/request'

export function queryDishes(params) {
  return request({
    url: 'api/dishes',
    method: 'get',
    params
  })
}

export function addDish(data) {
  return request({
    url: 'api/dishes',
    method: 'post',
    data
  })
}

export function editDish(data) {
  return request({
    url: 'api/dishes',
    method: 'put',
    data
  })
}

export default { queryDishes, addDish, editDish, delDish }
```

**Vue 组件（`<script>` 部分）：**
```javascript
export default {
  name: 'Dish',
  components: { DishForm, Pagination },
  data() {
    return {
      loading: true,
      dishList: [],
      queryParams: {
        page: 0,        // 前端 0 基，后端 0 基
        size: 10,
        name: null,
        dishType: null
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      queryDishes(this.queryParams).then(response => {
        this.dishList = response.content || []
        this.total = response.totalElements || response.total || 0
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    handlePagination({ page, limit }) {
      this.queryParams.page = page - 1   // el-pagination 从 1 开始
      this.queryParams.size = limit
      this.getList()
    },
    handleDelete(row) {
      const ids = row.id ? [row.id] : this.ids
      this.$confirm('是否确认删除...', '警告', { type: 'warning' })
        .then(() => delDish(ids))
        .then(() => {
          this.getList()
          this.$message.success('删除成功')
        }).catch(() => {})
    }
  }
}
```

**el-switch 布尔值绑定：**
```html
<el-switch v-model="scope.row.enabled" :active-value="true" :inactive-value="false" />
```

**el-form 校验规则：**
```javascript
rules: {
  name: [{ required: true, message: '菜品名称不能为空', trigger: 'blur' }],
  dishType: [{ required: true, message: '菜品类型不能为空', trigger: 'change' }]
}
```

**确认删除写法：**
```javascript
this.$confirm('提示信息', '标题', { type: 'warning' })
  .then(() => apiCall())
  .then(() => { /* success */ })
  .catch(() => {})   // 用户取消不报错
```

**状态切换后回滚（失败时）：**
```javascript
handleStatusChange(row) {
  editDish(row).then(() => {
    this.$message.success('更新成功')
  }).catch(() => {
    row.enabled = !row.enabled  // 回滚
  })
}
```

**命名规范：**
| 类型 | 风格 | 示例 |
|------|------|------|
| 组件 name | PascalCase | `name: 'DishForm'` |
| 变量 / 方法 | camelCase | `dishList`, `getList()` |
| 模板属性 | kebab-case | `class="filter-item"`, `@click="handleAdd"` |
| API 文件名 | kebab-case | `dish.js`, `customer-order.js` |

---

### 通用

**前后端约定：**
- 分页：前端 0 基，后端 0 基；前端传 `page`（0开始）、`size`，后端 `Page<>(page + 1, size)`
- 删除：批量传 `List<Integer> ids`，HTTP DELETE BODY
- 新增成功：`HttpStatus.CREATED`，前端无需特殊处理
- 修改成功：`HttpStatus.NO_CONTENT`，前端无需特殊处理
- API 文档：每次改动 API（增/删/改）必须同步更新 `doc/*.md`

## Business Modules

- **system**: User, Role, Menu, Dept, Job, Dict, Logs
- **security**: Auth, JWT token handling
- **quartz**: Scheduled jobs
- **maint**: System maintenance (online users, SQL监控)
- **meal**: Custom dietary restrictions and dishes module
