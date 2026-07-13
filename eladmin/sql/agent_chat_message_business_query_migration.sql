-- 已部署 agent_chat_message 表的业务查询卡片快照升级脚本。
-- 回滚说明：确认历史会话不再需要恢复业务查询结构化卡片后，可执行
-- ALTER TABLE `agent_chat_message` DROP COLUMN `business_result_json`;
ALTER TABLE `agent_chat_message`
    ADD COLUMN `business_result_json` mediumtext COMMENT '受控业务查询卡片快照JSON（不含金额和原始工具响应）' AFTER `tool_summary_json`;
