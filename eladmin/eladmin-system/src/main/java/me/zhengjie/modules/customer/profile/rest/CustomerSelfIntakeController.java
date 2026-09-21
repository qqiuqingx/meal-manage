package me.zhengjie.modules.customer.profile.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.annotation.rest.AnonymousPostMapping;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerSelfIntakeRequest;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerSelfIntakeResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 客户自助资料登记测试接口。
 *
 * 该接口只记录应用日志，不调用客户建档、订单或任何持久化组件；
 * 只有 customer.self-intake.enabled=true 时才会注册。
 *
 * @author qqx
 * @date 2026-09-21
 */
@Slf4j
@RestController
@RequestMapping("/api/public/customer-intake")
@ConditionalOnProperty(prefix = "customer.self-intake", name = "enabled", havingValue = "true")
@Api(tags = "客户自助资料登记")
public class CustomerSelfIntakeController {

    private static final String SUCCESS_MESSAGE = "测试资料已接收，请联系您的客服确认";

    /**
     * 接收客户自助资料测试提交，生成追踪号并将规范化后的字段写入应用日志。
     *
     * @param request 客户姓名、手机号、地址和备注
     * @return 包含请求追踪号和固定提示的成功响应
     */
    @AnonymousPostMapping
    @ApiOperation("提交客户自助资料测试信息")
    public ResponseEntity<CustomerSelfIntakeResponse> submit(@Validated @RequestBody CustomerSelfIntakeRequest request) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        log.info("客户自助资料测试提交 requestId={}, customerName={}, phone={}, address={}, remark={}",
                requestId,
                sanitizeForLog(request.getCustomerName()),
                sanitizeForLog(request.getPhone()),
                sanitizeForLog(request.getAddress()),
                sanitizeForLog(request.getRemark()));
        return ResponseEntity.ok(new CustomerSelfIntakeResponse(requestId, SUCCESS_MESSAGE));
    }

    /**
     * 将日志字段中的换行、回车及其他控制字符替换为空格，避免日志跨行污染。
     *
     * @param value 待写入日志的字段
     * @return 可安全写入单条日志的文本，输入为 null 时返回空字符串
     */
    private String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isISOControl(current) || current == '\u2028' || current == '\u2029') {
                sanitized.append(' ');
            } else {
                sanitized.append(current);
            }
        }
        return sanitized.toString();
    }
}
