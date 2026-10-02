# Phase 02：制作流程到配料表与保存闭环

## 执行前规则检查

读取 overview、当前规则链、`status.yaml` 和 Phase 01 交付记录。确认已有接口与计划一致、用量可空已核验；保留表单标签等现有能力及所有无关差异。

## 目标

新增和编辑共用表单支持自动追加配料，并在历史打开、人工删除、请求竞争和保存时保持已确认行为。

## 前置依赖

Phase 01 完成；需要只读接口、返回 DTO 和空用量存储核验结果。无需读取客户导入的无关实现。

## 输入与输出

输入：识别 API、现有表单、原有菜品保存接口。
输出：两处页面共用的自动关联能力，表单异步状态测试和菜品关联/展示文本一致性回归。

## 本阶段实施约束

- 使用输入事件触发，详情回填与历史保存不触发；不修改数据库结构，不做历史任务。
- 只追加，不用识别返回值整体覆盖配料列表；人工排除仅在当前编辑会话内保存。
- 新自动配料用量为空；手动添加已有的默认数量逻辑不顺带调整。
- 不从流程提取单位或备注；已有行各字段原样保留。
- 保存继续使用已有菜品 CRUD，不在后端保存时重新识别。取消编辑、识别失败或旧请求响应不产生持久化写入。
- 后端只修复此次闭环直接涉及的 `ingredientList` 空数组与 `ingredients` 文本一致性，未传/null 的保留语义不变。

## 涉及文件

修改：

- `eladmin-web/src/api/dish.js`：新增识别调用。
- `eladmin-web/src/views/meal/dish/dish.vue`：输入、防抖、排除、异步响应与保存接入。
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/service/impl/DishServiceImpl.java`：空数组清空展示文本与关联，null继续保留。

新增：

- `eladmin-web/tests/unit/views/meal/dishIngredientRecognition.spec.js`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/meal/service/impl/DishServiceImplIngredientUpdateTest.java`

已有 `dishFormTags.spec.js` 执行回归；只有为适配真实异步保存契约确有必要时调整测试，不更改断言掩盖失败。

## 实施步骤

1. 新增制作流程识别 API 函数，复用现有 request 实例与鉴权/错误处理。
2. 制作流程输入事件接入500ms防抖；建立本次编辑标识、递增请求序号、最新成功识别文本/待处理 Promise 和排除ID集合。初始化详情只设基线，不识别。
3. 接收结果时校验会话、请求序号与当前文本；按当前表单配料ID及排除ID过滤再追加，数量空、字典单位、备注空。保留既有配料行对象。
4. 删除配料时记录排除；手动重新添加时移除该ID排除。关闭、重置、切换菜品取消防抖计时并使旧响应失效。后续输入即使含相同配料，也不恢复本会话人工删除项。
5. 保存最新流程：若用户有实际输入变化，刷新防抖并等待最新识别；失败保留表单、提示重试并终止本次保存；成功后调用原 addDish/editDish。等待期间禁止重复提交，关闭弹窗使待保存动作失效。
6. 改为空流程则返回空候选、使旧识别失效但保留配料。输入最终文本等于历史初始文本且没有待生效识别时，保持历史“不因保存重识别”的契约。
7. 调整 `DishServiceImpl.update`：只有列表为 null 才从已有关系生成展示文本；空列表生成空展示文本并清空关联。新增/非空更新沿用现有事务流程，避免删除全部配料后重读显示旧配料文本。
8. 增加表单真实挂载/输入事件测试，使用假计时器和可控 Promise 覆盖异步行为；后端覆盖 null保留、空数组清空、非空替换，以及新增自动配料用量空的持久化参数。数据库保存/重读是否实测单独记录。

## 验证方式

在 `eladmin-web/` 执行：

```sh
./node_modules/.bin/eslint src/api/dish.js src/views/meal/dish/dish.vue tests/unit/views/meal/dishIngredientRecognition.spec.js
NODE_OPTIONS=--openssl-legacy-provider ./node_modules/.bin/vue-cli-service test:unit --no-cache --runInBand tests/unit/views/meal/dishIngredientRecognition.spec.js tests/unit/views/meal/dishFormTags.spec.js
```

在 `eladmin/` 使用 JDK 8/Maven 执行：

```sh
mvn -pl eladmin-system -am test \
  -Dtest=DishServiceImplIngredientUpdateTest,DishServiceImplDishTagTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

覆盖新增输入/粘贴、防抖、历史初始化、只改其他字段、重复配料、已有用量备注保留、删除后再输入、删除时请求在途、手动重新添加、响应乱序、流程清空、关闭重开、快速切换菜品、识别未完成立即保存、失败不提交、重复保存和全部配料清空。

## 完成标准

两处入口复用同一表单闭环；历史直接打开/保存零识别调用；快速保存等待最新结果；已有行和人工排除保持；清空配料时关联与展示文本一致。只报告实际通过的检查，不把 mock持久化参数当作数据库保存/重读证据。

## 状态

completed

## 阶段交付记录

- 新增前端 `recognizeIngredients` 调用；共用 `dish.vue` 使用真实输入事件、500ms防抖、编辑会话/递增序号/文本校验、当前会话人工排除集合以及待处理Promise；矩阵页和主档列表复用同一表单。
- 详情回填、只修改其他字段和直接保存历史菜品不识别；回到原始文本时不额外识别。只追加未存在且未排除的ID，已有行对象与用量/单位/备注保留。手动重新添加解除排除，仍使用现有100默认用量。
- 保存立即刷新防抖或复用最新在途请求；识别失败保留草稿、中止提交，下一次保存重试。等待期间再次输入会等待新文本；关闭、切换或取消使旧响应及待保存动作失效；保存期间禁用重复提交。
- 实际 Element UI 2.15 的数值输入框会把null转成0，故视图传undefined展示空值、输入事件将空值存回null。真实组件挂载验证空白显示、null提交、真实0与清空行为。
- `DishServiceImpl.update` 仅未传/null列表读取原关联；显式空数组删除关联并将展示文本写为 `""`，避免 MyBatis-Plus 的null字段更新策略跳过清空。既有CRUD不增加识别逻辑。
- 前端实际挂载测试19例及菜品标签回归4例共23例通过；后端保存语义4例及菜品标签回归3例共7例通过。使用计划中的vue-cli-service和Java 8/Maven 3.5.2命令，新增前端测试lint须加 `--env jest`；生产前端与新测试lint通过。
- 新增可显式启用的 `DishIngredientRecognitionMySqlTest`，使用生产Service/Mapper和真实数据库、不启动应用及后台任务。验证新增空用量、非空替换、null保留、空数组清空、重读及无CRUD重识别，1例通过；始终用JDBC事务回滚，写入前断言自动提交已关闭、表为InnoDB，结束后新连接确认测试菜品及关联不存在。
- 初次数据库测试使用Spring默认事务工厂导致独立连接自动提交；业务断言通过、清理断言捕获留存。已按本次测试ID及完整唯一名称核验并清理，改为JdbcTransactionFactory后重跑通过、无留存。只操作本次禁用测试菜品，未修改既有业务记录。
- 增补历史超10000字符但未修改流程的直接保存回归；只读识别长度约束不改变已有CRUD能力。
- 后续验收可复用上述结果；浏览器连接实际部署应用及完整JWT过滤链仍需在应用环境单独核验，本次不默认启动服务。
