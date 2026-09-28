package me.zhengjie.modules.customer.order.domain.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 订单行内单字段修改请求。
 */
@Getter
@Setter
public class CustomerOrderInlineUpdateDto {

    /** 白名单中的订单字段键。 */
    private String field;

    /** 请求写入的新值；图片字段允许传 null 清空。 */
    private Object value;

    /** 页面读取到的旧值，用于拒绝覆盖并发修改。 */
    private Object expectedValue;
}
