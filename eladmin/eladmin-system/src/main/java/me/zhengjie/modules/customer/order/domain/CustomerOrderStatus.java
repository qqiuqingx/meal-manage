package me.zhengjie.modules.customer.order.domain;

/**
 * 客户订单状态及其展示名称。
 */
public enum CustomerOrderStatus {

    CANCELED(0, "已取消"),
    ACTIVE(1, "进行中"),
    COMPLETED(2, "已完成"),
    REFUNDED(3, "已退餐"),
    PAUSED(4, "暂停");

    private final int code;
    private final String description;

    CustomerOrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * @return 数据库存储状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * @return 面向操作人的状态名称
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param code 数据库存储状态码
     * @return 匹配的状态；未识别时返回 null
     */
    public static CustomerOrderStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CustomerOrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
