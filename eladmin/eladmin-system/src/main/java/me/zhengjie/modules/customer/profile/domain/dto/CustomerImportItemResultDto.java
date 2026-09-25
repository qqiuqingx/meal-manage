package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 单位客户的批量导入结果。
 */
@Data
public class CustomerImportItemResultDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源行号（1 基）。 */
    private List<Integer> sourceRows = new ArrayList<>();

    /** 客户编号。 */
    private String customerCode;

    /** CREATED / ALREADY_EXISTS / SKIPPED / FAILED。 */
    private String status;

    /** 面向操作人的处理结果。 */
    private String message;

    /** 新建客户主键；跳过或失败时为空。 */
    private Long customerId;

    /** 新建首单主键；零餐数仅建档、跳过或失败时为空。 */
    private Long orderId;
}
