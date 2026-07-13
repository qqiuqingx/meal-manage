package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** Agent 父子套餐与子套餐规格只读 DTO。 */
@Data
public class AgentPackageSpecDto {
    /** 套餐是否存在。 */ private boolean present;
    /** 父套餐 ID。 */ private Long parentPackageId;
    /** 父套餐编码。 */ private String parentPackageCode;
    /** 父套餐名称。 */ private String parentPackageName;
    /** 父套餐是否启用。 */ private Boolean enabled;
    /** 关联子套餐规格。 */ private List<AgentSubPackageSpecDto> subPackages = new ArrayList<>();
}
