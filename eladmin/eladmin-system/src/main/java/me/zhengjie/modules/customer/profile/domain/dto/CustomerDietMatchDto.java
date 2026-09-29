package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 第二工作表饮食原文词项及其字典候选。
 */
@Data
public class CustomerDietMatchDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 可在确认请求中回传的稳定来源键。 */
    private String sourceKey;

    private Integer sourceRow;

    /** Excel 列号，1 基。 */
    private Integer sourceColumn;

    /** 列方向：DISH_REQUIREMENTS 或 DIETARY_RESTRICTIONS。 */
    private String side;

    /** 待匹配词项原文，保留该词项中的表达前缀。 */
    private String rawText;

    /** 来源单元格完整原文，保留标点和换行。 */
    private String cellText;

    /** 用于字典精确匹配的词项，不覆盖完整原文。 */
    private String lookupText;

    /** UNIQUE / AMBIGUOUS / UNMATCHED / SKIPPED。 */
    private String status;

    private List<CustomerDietOptionDto> candidates = new ArrayList<>();

    /** 唯一命中或确认后选择的对象；未匹配、未选或显式跳过时为空。 */
    private CustomerDietItemDto selectedItem;

    private String message;
}
