-- 上线前按 customer_profile.id 导出 production_date 旧值至受控备份。
-- 变更保留原值的 yyyy-MM-dd 文本，不推算术后天数；历史日期应由业务人员核对。
ALTER TABLE customer_profile
    CHANGE COLUMN production_date postoperative_info VARCHAR(255) NULL
        COMMENT '术后情况原文；历史生产日期按原值保留供人工核对',
    ADD COLUMN dish_requirements JSON NULL COMMENT '客户希望食用的结构化对象引用(JSON数组)',
    ADD COLUMN dietary_restrictions JSON NULL COMMENT '客户禁忌的结构化对象引用(JSON数组)',
    ADD COLUMN dish_requirements_raw JSON NULL COMMENT '导入来源中的想吃原文块(JSON数组)',
    ADD COLUMN dietary_restrictions_raw JSON NULL COMMENT '导入来源中的禁忌原文块(JSON数组)';
