package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 第二工作表 F 列禁忌原文词项及其字典候选。
 */
@Data
public class CustomerDietMatchDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用于在预览中追踪来源行、列和词项的稳定标识。 */
    private String sourceKey;

    private Integer sourceRow;

    /** Excel 列号，1 基；禁忌来源为 F 列（6）。 */
    private Integer sourceColumn;

    /** 列方向：DIETARY_RESTRICTIONS（客户禁忌）。 */
    private String side;

    /** 待匹配词项原文，保留该词项中的表达前缀。 */
    private String rawText;

    /** 来源单元格完整原文，保留标点和换行。 */
    private String cellText;

    /** 用于字典精确匹配的词项，不覆盖完整原文。 */
    private String lookupText;

    /** UNIQUE / MULTI / UNMATCHED。 */
    private String status;

    private List<CustomerDietOptionDto> candidates = new ArrayList<>();

    /** 确认导入时将同时录入的全部对象。 */
    private List<CustomerDietItemDto> selectedItems = new ArrayList<>();

    private String message;
}
