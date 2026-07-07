package me.zhengjie.modules.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.agent.domain.AgentDiagnosisMetric;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能排查运营指标 Mapper。
 */
@Mapper
public interface AgentDiagnosisMetricMapper extends BaseMapper<AgentDiagnosisMetric> {
}
