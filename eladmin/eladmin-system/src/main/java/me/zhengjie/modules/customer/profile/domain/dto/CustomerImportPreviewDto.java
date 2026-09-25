package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户批量导入的预览结果。
 *
 * <p>预览只做解析与只读校验，不写入任何客户、订单、排餐或核销数据。</p>
 *
 * @author qqx
 * @date 2026-09-24
 **/
@Data
public class CustomerImportPreviewDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 上传文件的 SHA-256 摘要，提交时需原样回传以确认同一份文件
     */
    private String fileHash;

    /**
     * 解析的工作表名称
     */
    private String sheetName;

    /**
     * 工作日历所属月份，格式 yyyy-MM
     */
    private String calendarMonth;

    /**
     * 计划导入日期，格式 yyyy-MM-dd
     */
    private String importDate;

    /**
     * 有业务内容的数据行数
     */
    private int dataRowCount;

    /**
     * 聚合出的客户数
     */
    private int customerCount;

    /**
     * 可导入客户数
     */
    private int importableCount;

    /**
     * 已存在客户数（幂等跳过）
     */
    private int alreadyExistsCount;

    /**
     * 存在阻塞问题的客户数
     */
    private int errorCount;

    /**
     * 未来日格派餐总份数
     */
    private int futureMealQuantity;

    /**
     * 结构是否通过校验；为 false 时不允许提交
     */
    private boolean structureValid;

    /**
     * 逐位客户草稿
     */
    private List<CustomerImportDraftDto> drafts = new ArrayList<>();

    /**
     * 全部问题记录，包含工作簿级与客户级
     */
    private List<CustomerImportIssueDto> issues = new ArrayList<>();
}
