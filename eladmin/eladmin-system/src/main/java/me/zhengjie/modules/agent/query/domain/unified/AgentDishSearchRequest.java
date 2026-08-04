package me.zhengjie.modules.agent.query.domain.unified;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 菜品名称、类型和启用状态受控搜索请求。 */
@Data
public class AgentDishSearchRequest {
    /** 菜品名称关键字。 */ @Size(max = 100) private String name;
    /** 菜品类型枚举。 */ @Pattern(regexp = "MAIN|SIDE|SOUP|VEGETABLE|RICE|RICE_TYPE") private String dishType;
    /** 是否启用。 */ private Boolean enabled;
    /** 页码。 */ @Min(1) private Integer page = 1;
    /** 单页数量，服务端上限 20。 */ @Min(1) @Max(20) private Integer size = 20;
}
