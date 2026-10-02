-- 部署客户禁忌自动匹配前执行；只记录上线后人工移除的对象，不推断历史删除。
ALTER TABLE customer_profile
    ADD COLUMN dietary_restriction_exclusions JSON NULL
        COMMENT '人工移除的禁忌对象稳定键(type:id)，自动匹配和补录跳过' AFTER dietary_restrictions;
