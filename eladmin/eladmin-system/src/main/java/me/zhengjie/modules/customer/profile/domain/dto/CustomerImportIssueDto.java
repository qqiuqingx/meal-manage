package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户批量导入的一条问题记录。
 *
 * <p>用于预览和提交阶段向操作人定位「哪一源行的哪一位客户因为什么原因被跳过」，
 * 只包含可定位信息，不承载手机号、地址等个人敏感内容的完整值。</p>
 *
 * @author qqx
 * @date 2026-09-24
 **/
@Data
public class CustomerImportIssueDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 问题分类，取值见 {@link CustomerImportIssueCategory}
     */
    private String category;

    /**
     * 源工作表行号（1 基，与 Excel 界面显示一致）
     */
    private Integer sourceRow;

    /**
     * 客户编号；未解析到编号时为空
     */
    private String customerCode;

    /**
     * 面向操作人的原因描述
     */
    private String message;

    /**
     * 构造一条问题记录。
     *
     * @param category 问题分类
     * @param sourceRow 源行号，可为空
     * @param customerCode 客户编号，可为空
     * @param message 原因描述
     * @return 问题记录
     */
    public static CustomerImportIssueDto of(CustomerImportIssueCategory category, Integer sourceRow,
                                            String customerCode, String message) {
        CustomerImportIssueDto issue = new CustomerImportIssueDto();
        issue.setCategory(category.name());
        issue.setSourceRow(sourceRow);
        issue.setCustomerCode(customerCode);
        issue.setMessage(message);
        return issue;
    }
}
