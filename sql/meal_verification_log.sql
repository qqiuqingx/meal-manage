-- 核销日志表
create table meal_verification_log (
    id bigint auto_increment primary key,
    meal_plan_customer_id bigint not null comment '客户排餐ID',
    meal_plan_id bigint not null comment '排餐计划ID',
    customer_id bigint not null comment '客户ID',
    order_id bigint not null comment '关联订单ID',
    record_date date not null comment '排餐日期',
    meal_type varchar(20) not null comment '餐次（LUNCH/DINNER）',
    verification_count int not null default 1 comment '核销餐数',
    remaining_before int not null comment '核销前剩余餐数',
    remaining_after int not null comment '核销后剩余餐数',
    verified_total_before int not null comment '核销前已核销总数',
    verified_total_after int not null comment '核销后已核销总数',
    operator varchar(50) not null default '' comment '操作人',
    operate_time datetime not null comment '操作时间',
    remark varchar(255) default '' comment '备注',
    create_time datetime default current_timestamp,
    index idx_meal_plan_customer_id (meal_plan_customer_id),
    index idx_order_id (order_id),
    index idx_operate_time (operate_time),
    index idx_meal_plan_customer (meal_plan_id, customer_id)
) engine=innodb default charset=utf8mb4 comment='核销日志表';

-- 修改排餐客户表，添加核销相关字段
alter table meal_plan_customer
    add column is_verified tinyint(1) not null default 0 comment '是否已核销（0未核销，1已核销）' after status,
    add column verification_time datetime default null comment '核销时间' after is_verified,
    add column verification_operator varchar(50) default '' comment '核销操作人' after verification_time;

-- 添加索引
alter table meal_plan_customer
    add index idx_is_verified (is_verified, deleted);
