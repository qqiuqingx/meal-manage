package me.zhengjie.modules.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.agent.domain.AgentDiagnosisFeedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能排查客服反馈 Mapper。
 */
@Mapper
public interface AgentDiagnosisFeedbackMapper extends BaseMapper<AgentDiagnosisFeedback> {
}
