package me.zhengjie.modules.customer.profile.domain.dto.intake;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户话术解析问题项，用于提示客服补齐或修正字段。
 */
@Data
public class CustomerIntakeIssueDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 问题级别：ERROR 阻塞保存，WARN 建议确认。
     */
    private String level;

    /**
     * 字段路径，如 orderInfo.parentPackageId。
     */
    private String field;

    /**
     * 面向客服展示的问题说明。
     */
    private String message;

    /**
     * 原始文本中识别到的值。
     */
    private String sourceValue;
}
