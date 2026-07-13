package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/** Agent 子套餐餐品规格。 */
@Data
public class AgentSubPackageSpecDto {
    /** 子套餐 ID。 */ private Long subPackageId;
    /** 子套餐编码。 */ private String subPackageCode;
    /** 子套餐名称。 */ private String subPackageName;
    /** 每餐荤菜数量。 */ private Integer meatCount;
    /** 每餐素菜数量。 */ private Integer vegCount;
    /** 是否含汤。 */ private Boolean includeSoup;
    /** 是否含米饭。 */ private Boolean includeRice;
    /** 是否启用。 */ private Boolean enabled;
}
