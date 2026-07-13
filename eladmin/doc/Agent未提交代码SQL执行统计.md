# Agent 未提交代码 SQL 执行统计

> 统计日期：2026-07-13
>
> 统计范围：当前 Git 工作区内与智能客服 Agent 相关的已修改、未跟踪代码及 SQL 文件。
>
> 说明：本文只做执行清单统计，未连接数据库，也未实际执行任何 SQL。

## 一、统计结论

当前 Agent 未提交代码涉及以下数据库结构变更：

| 类型 | 数量 | 对象 |
| --- | ---: | --- |
| 新增表 | 1 张 | `agent_business_query_audit` |
| 既有表新增字段 | 6 个 | `agent_chat_session` 5 个、`agent_chat_message` 1 个 |
| 数据补录/数据迁移 | 0 项 | 本次 Agent 改动不要求业务数据补录 |

生产或已有数据库升级时，需要根据数据库当前结构执行 **5 个基础脚本**；如果 `agent_business_query_audit` 已经以早期结构创建，还需要改用/补执行 **2 个兼容迁移脚本**。SQL 文件本身大多不是幂等的，执行前必须先检查字段是否存在。

## 二、已有数据库升级：基础必执行清单

以下清单适用于数据库中已经存在 `agent_chat_session`、`agent_chat_message`，但尚未包含本次新增字段的情况。

| 顺序 | SQL 文件 | 作用 | 结构变化 |
| ---: | --- | --- | --- |
| 1 | `eladmin/sql/agent_chat_session_order_context_migration.sql` | 持久化会话订单上下文 | 新增 `order_id`、`order_code` |
| 2 | `eladmin/sql/agent_chat_session_meal_plan_context_migration.sql` | 持久化排餐客户记录上下文 | 新增 `meal_plan_record_id` |
| 3 | `eladmin/sql/agent_chat_session_query_range_migration.sql` | 持久化受控查询日期范围 | 新增 `query_start_date`、`query_end_date` |
| 4 | `eladmin/sql/agent_chat_message_business_query_migration.sql` | 保存业务查询卡片快照 | 新增 `business_result_json` |
| 5 | `eladmin/sql/agent_business_query_audit.sql` | 保存 Agent 只读业务查询审计记录 | 新建 `agent_business_query_audit`（已包含当前全部字段及索引） |

推荐执行顺序：先升级会话和消息表，再创建审计表，最后发布后端与 `agent-service`。

### 2.1 字段明细

| 表 | 字段 | 类型 | 是否允许空 | 默认值 | 用途 |
| --- | --- | --- | --- | --- | --- |
| `agent_chat_session` | `order_id` | `bigint` | 是 | `NULL` | 当前会话订单 ID |
| `agent_chat_session` | `order_code` | `varchar(64)` | 是 | `NULL` | 当前会话订单编号 |
| `agent_chat_session` | `meal_plan_record_id` | `bigint` | 是 | `NULL` | 当前会话排餐客户记录 ID |
| `agent_chat_session` | `query_start_date` | `varchar(20)` | 是 | `NULL` | 受控查询起始日期 |
| `agent_chat_session` | `query_end_date` | `varchar(20)` | 是 | `NULL` | 受控查询结束日期 |
| `agent_chat_message` | `business_result_json` | `mediumtext` | 是 | `NULL` | 脱敏后的业务查询卡片快照 |

## 三、审计表已存在时的兼容迁移

`agent_business_query_audit.sql` 使用 `CREATE TABLE IF NOT EXISTS`。如果表已经存在，该脚本不会补齐字段，因此不能只重复执行建表脚本。

### 场景 A：表不存在

只执行：

```text
eladmin/sql/agent_business_query_audit.sql
```

不要再执行下面两个审计表迁移脚本，因为当前建表脚本已经包含这些字段。

### 场景 B：早期审计表已存在，缺少 `cached`

执行：

```text
eladmin/sql/agent_business_query_audit_cached_migration.sql
```

### 场景 C：早期审计表已存在，缺少分析与回答校验字段

执行：

```text
eladmin/sql/agent_business_query_audit_analysis_migration.sql
```

该脚本一次新增 7 个字段：`analysis_source`、`analysis_confidence`、`clarification_required`、`metric_codes`、`dimension_codes`、`unsupported_reason`、`answer_validation_result`。

## 四、全新数据库初始化

全新数据库不需要执行会话/消息迁移脚本，可直接使用包含最终字段的完整建表脚本：

```text
eladmin/sql/agent_chat_session.sql
eladmin/sql/agent_chat_message.sql
eladmin/sql/agent_business_query_audit.sql
```

注意：这里只列出本次未提交 Agent 改动对应的表，不代表 Agent 模块完整初始化所需的全部历史表（例如反馈表、动作审计表、规则缺口表等）。

## 五、执行前检查 SQL

```sql
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND (
    (TABLE_NAME = 'agent_chat_session' AND COLUMN_NAME IN (
      'order_id', 'order_code', 'meal_plan_record_id',
      'query_start_date', 'query_end_date'
    ))
    OR (TABLE_NAME = 'agent_chat_message' AND COLUMN_NAME = 'business_result_json')
    OR (TABLE_NAME = 'agent_business_query_audit')
  )
ORDER BY TABLE_NAME, ORDINAL_POSITION;
```

检查审计表是否存在：

```sql
SELECT COUNT(*) AS table_exists
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'agent_business_query_audit';
```

## 六、执行后验证 SQL

```sql
SHOW COLUMNS FROM agent_chat_session;
SHOW COLUMNS FROM agent_chat_message;
SHOW CREATE TABLE agent_business_query_audit;
```

预期结果：

- `agent_chat_session` 存在本次新增的 5 个字段；
- `agent_chat_message` 存在 `business_result_json`；
- `agent_business_query_audit` 存在当前建表脚本中的全部字段；
- 审计表至少存在请求、会话、客服时间、客户四组普通索引。

## 七、风险和执行注意事项

1. 现有 `ALTER TABLE ... ADD COLUMN` 脚本未使用 `IF NOT EXISTS`，重复执行会报 `Duplicate column name`。
2. `agent_business_query_audit.sql` 的 `CREATE TABLE IF NOT EXISTS` 只能创建缺失表，不能升级早期表结构。
3. 先备份表结构；大表执行 `ALTER TABLE` 前应在测试环境评估锁表时间。
4. 本次新增字段均不要求历史数据回填；会话历史记录允许保持 `NULL`。
5. 不要同时对全新审计表执行完整建表脚本和两个增量迁移脚本。

## 八、未纳入 Agent 必执行清单的未提交 SQL

当前工作区还存在下列 SQL，但其用途属于菜品、配料、排餐、订单或数据导入，不是本次 Agent 代码运行所依赖的数据库结构，因此未计入上述 1 张表和 6 个字段：

- `eladmin/sql/assign_ingredient_categories.sql`
- `eladmin/sql/dish_ingredient_category.sql`
- `eladmin/sql/insert_dish_ingredient_relations_from_excel.sql`
- `eladmin/sql/insert_dish_ingredients_from_excel.sql`
- `eladmin/sql/insert_dishes_dedup.sql`
- `eladmin/sql/insert_dishes_dedup_with_schedule.sql`
- `eladmin/sql/insert_meal_schedule_plan_from_excel.sql`
- `eladmin/sql/insert_missing_dish_ingredient_relations.sql`
- `eladmin/sql/meal_plan_customer_supplementary.sql`
- `eladmin/sql/meal_plan_customer_supplementary_ext.sql`
- `eladmin/sql/update_meal_type_comment.sql`
- `sql/add_cutting_info_field.sql`

这些脚本是否执行，应分别按对应业务功能和目标数据库现状评估，不能因为它们处于未提交状态就随 Agent 升级一并执行。
