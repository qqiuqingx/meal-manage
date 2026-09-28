package me.zhengjie.modules.customer.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单行内修改的追加式审计记录。
 */
@Getter
@Setter
@TableName("customer_order_inline_audit")
public class CustomerOrderInlineAudit implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审计记录主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 被修改的订单主键。 */
    private Long orderId;

    /** 被修改订单所属的客户主键。 */
    private Long customerId;

    /** 本次请求修改的字段键。 */
    private String fieldKey;

    /** 执行修改的操作人。 */
    private String operator;

    /** 审计记录创建时间。 */
    private LocalDateTime createdAt;

    /** 按实体主键组织的修改前字段状态 JSON。 */
    private String beforeState;

    /** 按实体主键组织的修改后字段状态 JSON。 */
    private String afterState;
}
