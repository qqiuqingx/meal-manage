package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次客户与首单批量导入的逐位结果。
 */
@Data
public class CustomerImportResultDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本次处理的工作簿 SHA-256。 */
    private String fileHash;

    /** 实际使用的导入日期，格式 yyyy-MM-dd。 */
    private String importDate;

    /** 本次重新校验后的预览，便于确认提交结果与原文件一致。 */
    private CustomerImportPreviewDto preview;

    /** 按源行顺序排列的逐位处理结果。 */
    private List<CustomerImportItemResultDto> results = new ArrayList<>();

    /** 成功新建客户数。 */
    private int createdCount;

    /** 已存在而跳过的客户数。 */
    private int alreadyExistsCount;

    /** 预览不合规而跳过的客户数。 */
    private int skippedCount;

    /** 事务失败的客户数。 */
    private int failedCount;
}
