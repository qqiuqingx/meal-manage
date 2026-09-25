# Phase 02 导入解析与写入

## 执行前规则检查

重新读取当前生效的 `AGENTS.md` 规则链与客户业务文档。

## 目标

同时保留电话列客户手机号和地址列全部配送电话信息。

## 前置依赖

依赖 Phase 01 的 `delivery_phone_info` 字段契约；需读取其交付记录。

## 本阶段实施约束

复用现有地址标签解析。多条电话原文完整保存；地址单值联系电话只取一条，避免把多个号码拼接进 VARCHAR(20)。日志与预览不得输出完整号码。

## 涉及文件与步骤

- 更新 `ParsedCustomer` 承载配送电话原文。
- 更新 `CustomerOrderImportParser` 收集地址列所有电话标签，去除号码不一致的阻塞或提示，仅让 `contactPhone` 使用首个可用号码。
- 更新 `CustomerProfileImportWriter` 同时写入客户手机号和配送电话信息。
- 增补 `CustomerOrderImportParserTest`，覆盖重复电话标签、同标签多个号码与电话列不同值。

## 验证方式与完成标准

解析测试证明多条电话完整保留、客户手机号不变、地址联系电话不超出单值字段；记录实际变更与测试结果。

## 状态

complete

## 阶段交付记录

- `ParsedCustomer.deliveryPhoneInfo` 接收地址列全部电话标签值及独立号码行，以换行分隔保留同标签内的多号码文本与重复标签。
- 地址 `contactPhone` 从这些值选首个可用手机号或单值数字电话；客户主档 `phone` 仍取电话列。号码不同不再产生复核提示。
- 导入写入器将配送电话信息写入新增字段；预览不暴露原文。
- `CustomerOrderImportParserTest` 与 `CustomerProfileImportFlowTest` 针对性 Maven 测试通过。
