package me.zhengjie.modules.customer.profile.domain.dto.intake;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 客户自助资料登记测试响应。
 *
 * 不返回客户 ID、订单 ID 或任何持久化状态。
 *
 * @author qqx
 * @date 2026-09-21
 */
@Data
@AllArgsConstructor
public class CustomerSelfIntakeResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 本次测试请求的追踪号，用于关联前端结果和服务端日志。
     */
    private String requestId;

    /**
     * 前端展示的测试接收提示。
     */
    private String message;
}
