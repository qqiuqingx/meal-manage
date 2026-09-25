# Phase 01 Excel 聚合、转换、预览和逐行错误

## 执行前规则检查

- 重新读取当前生效的 AGENTS.md 规则链、overview 与客户/订单/套餐业务文档；确认第三版工作簿实际表头和哈希。
- 检查 Apache POI 现有依赖版本、文件大小限制和现有 MultipartFile/Excel 工具；仅不确定的库 API 再核对对应版本官方文档。

## 目标

不写数据库即可从第三版 Excel 形成客户+首单草稿和可定位到源行的错误报告。

## 前置依赖

- 无实施 Phase 依赖。输入为第三版源文件、当前套餐配置/编号池的只读快照与已确认映射规则。

## 输入与输出

- 输入：上传的工作簿；只使用“26年9月”工作表，忽略“客户禁忌”和 WPS 保留页。
- 输出：预览 DTO，包含文件哈希、源行范围、有效编号、套餐/规格、暂停判定、订单餐数、日期餐次数量、警告及跳过原因。

## 本阶段实施约束

- 预览接口仅做解析和只读校验；不落客户、订单、临时 Excel、排餐或核销数据，不在日志输出手机号、地址、健康资料。
- 文件列应按标题和 2/3 行日历表头联合定位，不把第三版 J/K 或未来列位置写成盲目固定常量。
- 新接口必须有 customerProfile:import 权限并记录 API 契约；单笔客户新增流程继续可用。

## 涉及文件

- 新增：客户导入专用 parser/草稿/预览 DTO、导入 Controller 或 profile 下明确职责的入口、解析业务测试。
- 修改：必要的客户/套餐查询服务与 eladmin/doc/apidoc/客户档案管理接口文档；不修改旧忌口模块。

## 实施步骤

1. 先审计 workbook 快照、合并单元格、A/B/C/D/E/F/G/H/I/J/K 与 9 月 25—30 日午晚餐列；仅标题、月份与日期餐次结构符合预期才继续。
2. 按 B 优先否则 A 得到有效编号，结合合并范围与电话归并续行；F1076 等相同身份合并为一位客户的一笔首单；不同电话/地址撞号报错。
3. 清洗电话和地址，按前缀只读映射启用父套餐，解析无副菜/含汤/送餐模式/餐次；处理纯早餐跳过、H 未知报错、E/F“等通知”暂停。
4. 读取公式计算值并交叉核对 I、J、CW 及日格；计算 J+未来待服务餐数，并构造逐格份数/含汤数。仅当 F 的“有效编号-2含汤”、G“不含汤”和日格数字 2 三者一致时认定第二份含汤；当前 B596 应得 12 个两份格、24 份，其中每格一份汤；异常数字和来源冲突报错。
5. 预览返回源行号、解析结果与跳过原因；用四条无效电话、合并续行、B370 新编号、B596 双份及暂停关键词场景写解析测试；记录当前文件哈希的审计结果。

## 验证方式

- 显式运行 parser 和预览服务测试；使用只读文件或匿名样例，不写真实客户数据库。
- 用当前文件快照比对源行数、未来份数、无效电话和暂停数量；若哈希变化，先更新审计基线再验证。
- git diff --check 与 API 文档检查。

## 完成标准

- 无数据库副作用的预览能准确给出逐位客户草稿、来源行与错误；对错列、坏公式、重复身份不作猜测。
- 下一阶段可直接读取规范化草稿契约，不需重写 Excel 解析。

## 状态

completed

## 阶段交付记录

### 交付文件

新增（**并入 `profile` 现有包，不新增子包**；下表路径相对 `eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/`）：

| 文件 | 职责 |
|---|---|
| `domain/dto/CustomerImportIssueCategory.java` | 问题分类枚举（WORKBOOK_ERROR / PROFILE_ERROR / PACKAGE_CONFIG_ERROR / ALREADY_EXISTS / SKIPPED / DATABASE_ERROR / WARNING）|
| `domain/dto/CustomerImportIssueDto.java` | 单条问题：分类、源行号、客户编号、原因 |
| `domain/dto/CustomerImportAddressDto.java` | 地址槽位：DEFAULT / WORKDAY / WEEKEND |
| `domain/dto/CustomerImportMealCellDto.java` | 未来逐餐计划：源行、日期、餐次、目标份数、含汤份数 |
| `domain/ParsedCustomer.java` | 聚合后的客户草稿（未做数据库校验）|
| `domain/ParsedWorkbook.java` | 一次解析的完整结果 |
| `domain/ImportCandidate.java` | 客户候选：解析草稿 + 父套餐 + 重复标记 |
| `domain/ImportResolution.java` | 工作簿 + 候选列表 |
| `domain/dto/CustomerImportDraftDto.java` | 面向操作人的逐位客户草稿（预览与提交共用）|
| `domain/dto/CustomerImportPreviewDto.java` | 预览汇总与逐位草稿、问题列表 |
| `service/CustomerOrderImportParser.java` | POI 解析器：纯解析、无数据库依赖 |
| `service/CustomerProfileImportService.java` | 预览服务接口 |
| `service/impl/CustomerProfileImportServiceImpl.java` | 只读校验：父套餐编号池匹配、重复建档判定、草稿组装 |
| `rest/CustomerProfileImportController.java` | `POST /api/customerProfile/import/preview` |

新增测试：`eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/CustomerOrderImportParserTest.java`（16 个用例，全部使用运行时构造的匿名工作簿，不引用真实客户数据）。

其中 `domain/ParsedCustomer`、`domain/ParsedWorkbook`、`domain/ImportCandidate`、`domain/ImportResolution` 是解析期领域对象（不入库），与持久化实体并列放在 `domain/` 下；6 个导入 DTO 与问题分类枚举放在 `domain/dto/`，与既有 DTO 同级。

修改：`eladmin-system/src/main/resources/config/application.yml` 增加 multipart 上限（`max-file-size: 40MB`、`max-request-size: 48MB`），第三版工作簿约 22MB，默认 1MB 会直接拒绝上传。

文档：`eladmin/doc/apidoc/客户档案管理接口文档.md`（升至 v2.5，新增 3.8 与 `customerProfile:import` 权限，并修正原有重复的 3.3 小节编号）、`eladmin/doc/business/客户管理业务说明.md`（新增「8. 客户与首单 Excel 批量导入（预览阶段）」）。

### 第三版工作簿表头与哈希

- 源文件：`/Users/qqx/job/code/erp/2025客户用餐计划表3.xlsx`（仓库外，只读，未复制进 Git）。
- 快照 SHA-256：`429f68f40e65fe88b72c8fb1add699feb7054a1e9a5ef409ee97d41416fa09cf`（复算一致）。
- 工作表：`26年9月`；第 1 行表头为 编号 / 新编号 / 电话 / 地址 / 备注信息 / 特殊要求 / 含汤 / 每日·午餐·晚餐 / 餐数 / 剩余餐数 / 消费记录；第 2 行日序（跨 3 列合并）、第 3 行「早/中/晚」；数据自第 4 行起；`CW` 列为「合计餐数」公式、`CX` 列为「剩余餐数」。
- 解析器按标题 + 2/3 行日历表头联合定位列，只读取 `NN年N月` 命名的工作表，跳过 `客户禁忌` 与 WPS 保留页；表头错位、日序不连续、日格数不完整、末列不是「合计餐数」时置 `structureValid=false` 并整体拒绝。

### API 契约

`POST /api/customerProfile/import/preview`，`multipart/form-data`，参数 `file` 与可选 `importDate`（ISO 日期，缺省取当天）。响应为 `CustomerImportPreviewDto`：`fileHash`、`sheetName`、`calendarMonth`、`importDate`、`dataRowCount`、`customerCount`、`importableCount`、`alreadyExistsCount`、`errorCount`、`futureMealQuantity`、`structureValid`、`drafts`、`issues`。权限 `customerProfile:import`。字段级契约见 API 文档 3.8。下一阶段（Phase 03）可直接复用 `CustomerImportDraftDto` 与 `CustomerImportMealCellDto`，不需要重做 Excel 解析。

### 真实文件审计数（2026-09-24 快照）

| 指标 | 计划基线 | 实测 | 结论 |
|---|---|---|---|
| 有业务内容的数据行 | 412 | 412 | 一致 |
| 聚合客户数 | — | 399 | 412 行 − 13 组续行 |
| 手机号不合规 | 4 条（行 147/178/358/412）| 4 条，行号完全一致 | 一致 |
| E/F 含「等通知」 | 153 行 | 153 位客户 | 一致 |
| 同编号多行业务行 | 13 组 | 13 组（A003、B529、B572、F803、F1009、F1076、F1177、F1209、F1250、F1330、F1350、F1572、F1573）| 一致 |
| 9 月 25—30 日非零午晚餐 | 91 行 / 435 份 | 91 位客户 / 438 格 / 450 份 | 以实测为准 |
| B596 同餐次两份 | 12 格 / 24 份 | 12 格 / 24 份，每格含汤 1 份 | 一致 |
| 未来逐餐份数合计 | — | 450 | 与服务层 `futureMealQuantity` 一致 |

「435 份」与实测的差异源自计划撰写时的粗算：450 = 438 个非零格 + B596 的 12 个额外份，与独立用 Python 复核的结果一致，故以实测为准，并已把该口径写入文档。

### 测试命令与结果

构建环境（`~/.m2` 中的 `eladmin-common` 等 4 个模块 jar 是 JDK 17 产物，类文件版本 61.0，JDK 8 下无法使用）：

```
cd eladmin
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home
mvn -o test -pl eladmin-system -am -DskipTests=false -DfailIfNoTests=false -Dtest=CustomerOrderImportParserTest
```

结果：`Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。用 `-am` 让上游模块在 reactor 内从源码构建，避免覆盖本地仓库中的 JDK 17 产物。

服务层只读校验（临时类，已删除）：用 dev 库 `parent_package` / `customer_profile` 只读快照做桩，跑真实文件得到 `structureValid=true`、`dataRowCount=412`、`customerCount=399`、`futureMealQuantity=450`，且幂等分支与套餐不匹配分支均按预期触发。

### 发现的问题与偏差

1. **dev 库的 `parent_package` 是测试夹具，不能代表真实编号体系。** 该库只有 5 条父套餐，`package_code` 为 `RELEASE-TEST-PKG` / `SHORT-RANGE-PKG`，编号池为 `A(1～169)`、`A(170～199)`、`B3(301～599)`、`B5(600～699)`、`B2(200～300)`。系统内部生成与校验编号的口径是 `pool_prefix + 补零序号`（见 `CustomerProfileServiceImpl` 手工编号校验与 `NumberPoolServiceImpl#buildCode`，库中已有 `B3301`、`B5600`、`B2200` 佐证），而工作簿的编号分布为 `A 1～711`、`B 8～610`、`C 65～775`、`D 2～5`、`F 800～1615`。按当前配置只读预览，399 位客户中仅 6 位可导入，387 位报套餐不匹配（孕期餐 132、营养餐 244、小月子餐 9、非标准编号 1、超出月子餐池 1）。**这是配置缺口而非解析缺陷**：解析、聚合、餐数、暂停、未来逐餐全部正确，报错原因也可读。真实导入前必须由业务提供与工作簿一致的编号池配置（含缺失的小月子餐父套餐），并用生产配置重新预览。
2. 修正了套餐不匹配原因的一处口径错误：原「同时落在多个父套餐编号池内」只做前缀匹配、漏算数值区间，导致 `A711` 被误报为池冲突；现与 `matchParentPackage` 使用同一区间口径，`A711` 正确报为「不在父套餐「月子餐」的编号池 A001～A169 内」。
3. `CustomerImportDraftDto.parentPackageName` 的字段注释误写为「父套餐ID」，已改为「父套餐名称」。
4. API 文档原有两节同为 3.3（「解析客户建档话术」与「新增客户档案」），已把后者及其后小节顺延为 3.4～3.7，新增导入预览为 3.8。

### 2026-09-25 审查修正

- 同编号续行现在同时核验手机号和地址；同编号出现不同身份资料时整位拒绝，避免将不同地址客户拼成一个档案。
- 父套餐匹配执行编号前缀到套餐名称映射并验证编号池范围；超长数字会返回可读的不匹配错误。
- 子套餐按父子关联、荤菜总数、素菜数、含汤和米饭配置尝试唯一匹配；无法唯一确认时子套餐留空并给出提示。
- 地址联系人统一使用有效编号；地址解析前保留原始换行，未标记片段仍留在默认地址并提示。
- 日格文本、公式错误、负数、小数和超出整数范围的取值按源行报错，不再当作零忽略。
- 同一客户重复提供同一未来日期餐次时报告错误，避免写入重复的订单日历唯一键。
- 明确订单餐次与未来日格冲突时拒绝导入；`mealType=null` 的来源计划可保存展示，但不会进入人工排餐生成。
- 预览响应中的客户电话和地址联系人电话脱敏；单客户写入使用解析期原值。
