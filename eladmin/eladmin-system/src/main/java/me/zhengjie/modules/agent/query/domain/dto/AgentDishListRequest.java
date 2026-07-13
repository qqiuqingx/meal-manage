package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/** Agent 菜品受控查询请求。 */
@Data
public class AgentDishListRequest {
    /** 要查询的菜品 ID，最多 20 个。 */
    @NotEmpty
    @Size(max = 20)
    private List<Integer> dishIds = new ArrayList<>();
}
