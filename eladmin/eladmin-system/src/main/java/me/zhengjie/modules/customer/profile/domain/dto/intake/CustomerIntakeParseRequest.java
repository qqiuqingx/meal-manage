package me.zhengjie.modules.customer.profile.domain.dto.intake;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户话术解析请求，承载客服粘贴的原始文本。
 */
@Data
public class CustomerIntakeParseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 原始客户信息文本。
     */
    private String text;
}
