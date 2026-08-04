package me.zhengjie.modules.agent.query.domain.unified;

import lombok.Data;
import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 主系统统一只读查询响应信封。
 *
 * <p>列表和单对象接口共用同一结构，避免 Agent 适配层维护多套分页和告警解析逻辑。</p>
 *
 * @param <T> 受控结果项类型
 */
@Data
public class AgentUnifiedQueryResponse<T> {
    /** 统一响应版本。 */
    private String schemaVersion = "v1";
    /** 当前页结果。 */
    private List<T> items = new ArrayList<>();
    /** 查询命中总量。 */
    private long total;
    /** 当前页码。 */
    private int page = 1;
    /** 当前页大小。 */
    private int size;
    /** 是否还有未返回结果。 */
    private boolean truncated;
    /** 主系统生成时间。 */
    private String queriedAt;
    /** 单对象结果或聚合结果。 */
    private T data;
    /** 稳定告警码或数据缺失说明。 */
    private List<String> warnings = new ArrayList<>();

    /** 将既有受控列表 DTO 转换为统一响应，保持真实 SQL 分页结果。 */
    public static <T> AgentUnifiedQueryResponse<T> fromList(AgentListResultDto<T> source) {
        AgentUnifiedQueryResponse<T> result = new AgentUnifiedQueryResponse<>();
        if (source == null) {
            result.setQueriedAt(now());
            return result;
        }
        result.setItems(source.getItems() == null ? new ArrayList<>() : source.getItems());
        result.setTotal(source.getTotal());
        result.setPage(source.getPage());
        result.setSize(source.getSize());
        result.setTruncated(source.isTruncated());
        result.setQueriedAt(source.getQueriedAt() == null ? now() : source.getQueriedAt());
        return result;
    }

    /** 构造单对象响应。 */
    public static <T> AgentUnifiedQueryResponse<T> single(T data) {
        AgentUnifiedQueryResponse<T> result = new AgentUnifiedQueryResponse<>();
        result.setData(data);
        result.setTotal(data == null ? 0 : 1);
        result.setSize(data == null ? 0 : 1);
        result.setQueriedAt(now());
        return result;
    }

    /** 为结果补充稳定告警。 */
    public AgentUnifiedQueryResponse<T> warning(String warning) {
        if (warning != null && !warning.trim().isEmpty()) warnings.add(warning.trim());
        return this;
    }

    /** 返回主系统统一响应使用的业务时区时间。 */
    private static String now() {
        return OffsetDateTime.now(ZoneOffset.ofHours(8)).toString();
    }
}
