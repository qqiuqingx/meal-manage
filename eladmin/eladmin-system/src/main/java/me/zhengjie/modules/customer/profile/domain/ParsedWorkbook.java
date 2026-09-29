package me.zhengjie.modules.customer.profile.domain;

import lombok.Data;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueDto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次工作簿解析的完整结果。
 *
 * <p>结构校验失败时 {@code structureValid=false}，不允许进入导入流程；已能解析的月份草稿
 * 仍可保留供诊断，操作人需要先修正工作簿结构。</p>
 *
 * @author qqx
 * @date 2026-09-24
 **/
@Data
public class ParsedWorkbook implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 上传文件的 SHA-256 十六进制摘要，用于预览与提交之间的一致性校验
     */
    private String fileHash;

    /**
     * 实际解析的工作表名称
     */
    private String sheetName;

    /**
     * 工作日历所属年月对应的每月 1 日，用于把日格列号还原成真实日期
     */
    private LocalDate calendarMonthStart;

    /**
     * 导入日期；晚于该日期的非零午晚餐格视为未来计划
     */
    private LocalDate importDate;

    /**
     * 有业务内容的数据行数（不含表头与日历表头两行）
     */
    private int dataRowCount;

    /**
     * 聚合出的客户草稿数
     */
    private int customerCount;

    /**
     * 结构是否通过校验
     */
    private boolean structureValid;

    /**
     * 客户草稿列表
     */
    private List<ParsedCustomer> customers = new ArrayList<>();

    /** 第二工作表中的饮食信息来源行；未提供该工作表时为空。 */
    private List<CustomerDietSourceRow> dietRows = new ArrayList<>();

    /** 工作簿是否包含客户禁忌工作表。 */
    private boolean dietSheetPresent;

    /** 饮食字典快照摘要；未提供第二工作表时为空。 */
    private String dictionaryHash;

    /**
     * 工作簿级与行级问题列表
     */
    private List<CustomerImportIssueDto> issues = new ArrayList<>();
}
