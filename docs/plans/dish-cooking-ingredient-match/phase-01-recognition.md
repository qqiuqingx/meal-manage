# Phase 01：名称识别接口闭环

## 执行前规则检查

读取 `status.yaml`、`00-overview.md`、当前生效的用户及仓库 `AGENTS.md`；核对目标目录是否新增规则。检查工作区差异，保留客户匹配测试中已有的修改。规则变化先同步当前计划。

## 目标

提供仅返回启用配料名称候选的只读接口，复用现有领域分词器，保持客户禁忌匹配行为。

## 前置依赖

无。输入为 overview 已确定的名称识别范围和接口契约。

## 输入与输出

输入：制作流程文本、当前启用配料库、现有 HanLP 1.8.6 创建逻辑。
输出：可调用的只读识别接口、共享分词器工厂、定向测试结果与空用量存储约束核验结果。

## 本阶段实施约束

- 不新增依赖、不配置外部分词服务、不改全局词典、不引入字典缓存或刷新任务。
- 客户禁忌规则留在客户服务中；制作流程识别包括已有调料，不读取客户候选字典。
- 新 Service 使用构造器注入；方法有用途、参数与返回含义注释；DTO 沿用 fastjson2 和项目校验约定。
- 接口受菜品新增/编辑权限约束，不要求额外配料维护权限，不增加匿名入口。
- 不改变原 CRUD 契约，不在识别接口中保存任何业务数据。实施前核对静态路由与 `/{id}` 无冲突。

## 涉及文件

新增：

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/util/DietDictionarySegmenter.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/domain/dto/DishIngredientRecognizeRequest.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/service/impl/DishIngredientRecognitionService.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/meal/service/impl/DishIngredientRecognitionServiceTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/meal/rest/DishIngredientRecognitionControllerTest.java`

修改：

- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/CustomerDietMatchService.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/rest/DishController.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/CustomerDietMatchServiceTest.java`：仅按实际复用影响补必要回归，不覆盖已有差异。

现有 `DishIngredientDto`、`DishIngredientService.queryAll` 和 `DishIngredientMapper` 优先复用，不为识别另建配料查询。

## 实施步骤

1. 从已有 DDL或实施环境只读元数据核验关联表用量可空；记录证据。未核验时明确记录，不把 Java 字段可空当作数据库证据；若 NOT NULL，停止涉及保存的阶段并报告范围变化。
2. 把名称集合创建独立 HanLP 分词器的实现提取到共享工具；客户服务改调用工具，其他匹配规则不动。空名称集合关闭自定义词典。
3. 创建识别 Service：查询所有启用配料，构建规整名称索引及领域词典；空文本/空配料库直接返回空列表；对全文分词，仅接受精确命中配料名的词项。
4. 按首次出现顺序返回去重 DTO，使用字典ID/原名/单位，数量为空。完整名称优先，不用短词 substring 补齐，不新增未知词或别名记录。
5. 新增请求 DTO与控制器方法，实现10000字符长度校验、菜品任一维护权限、标准参数错误与 HTTP 200 响应。
6. 增加生产路径测试：mock 字典数据访问，使用真实分词工具；控制器测试覆盖参数、返回结构、权限和无写入行为。

## 验证方式

按 Maven 技能和本地开发指南选择 JDK 8/Maven；在 `eladmin/` 执行受影响模块及依赖的编译和测试，例如：

```sh
mvn -pl eladmin-system -am test \
  -Dtest=DishIngredientRecognitionServiceTest,DishIngredientRecognitionControllerTest,CustomerDietMatchServiceTest,CustomerProfileImportDietOnlyTest,CustomerProfileImportDietFlowTest,CustomerDietRematchServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

必要用例：

- “胡萝卜切丁，加入生姜，胡萝卜炒熟” → 胡萝卜、生姜，两条且保序。
- 同时有“胡萝卜”“萝卜”时，前者不产生后者；另外出现完整“萝卜”时才加入。
- “葱姜蒜”且三者各有启用配料 → 三条；长名称保护与连写识别均使用真实 HanLP 验证。
- 制作流程含启用调料 → 正常识别，客户调料过滤仍保持现有结果。
- “姜”不猜测为“生姜”；配料标签、分类名和未知词不产生关联。
- 重复名称、空流程、纯空白、字典为空、全半角/零宽字符、停用配料和超长输入。
- 识别 DTO 不产生数量；多次/并发调用不污染其他请求或客户匹配的词典。
- 新增/编辑任一权限及管理员可用，无相关权限拒绝。核对项目实际权限评估路径，不能只断言注解字符串。

## 完成标准

接口与 overview 契约一致，生产分词路径通过定向验证；客户匹配回归通过；无持久化副作用或仅为测试的生产分支。记录用量可空核验的实际结果。

## 状态

completed

## 阶段交付记录

- 新增 `DietDictionarySegmenter`、`DishIngredientRecognizeRequest`、`DishIngredientRecognitionService` 及服务/控制器测试；`CustomerDietMatchService` 仅替换分词器工厂调用，原有客户测试差异保留。
- `POST /api/dishes/recognize-ingredients` 接收 `{ "cookingMethod": "胡萝卜切丁，加入生姜" }`，返回配料关联 DTO 数组。用量为 null（现有 fastjson2 配置可省略 null 属性），备注为空，名称和单位取字典原值。
- 新增/编辑任一权限及 `admin` 通过生产 `AuthorityConfig`、JWT 用户读取和 Spring 方法安全代理验证；无相应权限拒绝。10000字符边界、空/null文本和标准400错误通过 MockMvc 验证。
- 完整名称保护、连写调料、精确名称、全半角/零宽字符、ID去重和并发隔离通过真实 HanLP 测试；基础词库、禁用命名实体、偏移、动态领域词典和强制匹配配置保持不变。
- 使用本地 pymysql 与私有配置只读查询 `information_schema.COLUMNS`：`dish_ingredient_relation.quantity = decimal(10,2), IS_NULLABLE=YES, DEFAULT=NULL`；`remark` 同样可空。未写入数据库。
- Java 8/Maven 3.5.2：overview 中 Phase 01 的6类定向测试共39例。服务和客户相关36例首次通过；控制器测试补齐测试鉴权上下文、JSON媒体类型和标准错误消息断言后，单独重跑3例全部通过。最终失败0、错误0、跳过0。
- Phase 02 可按既有 CRUD 保存空用量；识别接口不写入、不发布字典事件。无需变更数据库。
