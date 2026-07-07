package me.zhengjie.modules.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.AgentDiagnosisFeedback;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisFeedbackQueryCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisFeedbackRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisFeedbackResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisFeedbackStatsDto;
import me.zhengjie.modules.agent.mapper.AgentDiagnosisFeedbackMapper;
import me.zhengjie.modules.agent.service.AgentDiagnosisFeedbackService;
import me.zhengjie.modules.agent.service.AgentRuleGapService;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.PageUtil;
import me.zhengjie.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能排查客服反馈服务实现。
 */
@Service
@RequiredArgsConstructor
public class AgentDiagnosisFeedbackServiceImpl implements AgentDiagnosisFeedbackService {

    private final AgentDiagnosisFeedbackMapper feedbackMapper;
    private final AgentRuleGapService ruleGapService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentDiagnosisFeedbackResponse submit(AgentDiagnosisFeedbackRequest request) {
        AgentDiagnosisFeedback feedback = buildFeedback(request);
        feedbackMapper.insert(feedback);
        ruleGapService.createFromFeedback(feedback);
        AgentDiagnosisFeedbackResponse response = new AgentDiagnosisFeedbackResponse();
        response.setId(feedback.getId());
        response.setStatus("SAVED");
        response.setMessage("诊断反馈已记录");
        return response;
    }

    @Override
    public PageResult<AgentDiagnosisFeedback> query(AgentDiagnosisFeedbackQueryCriteria criteria) {
        Page<AgentDiagnosisFeedback> page = new Page<>(normalizePage(criteria.getPage()) + 1L, normalizeSize(criteria.getSize()));
        Page<AgentDiagnosisFeedback> result = feedbackMapper.selectPage(page, wrapper(criteria));
        return PageUtil.toPage(result);
    }

    @Override
    public AgentDiagnosisFeedbackStatsDto stats(AgentDiagnosisFeedbackQueryCriteria criteria) {
        List<AgentDiagnosisFeedback> feedbacks = feedbackMapper.selectList(wrapper(criteria));
        AgentDiagnosisFeedbackStatsDto stats = new AgentDiagnosisFeedbackStatsDto();
        long total = feedbacks.size();
        long accepted = countAccepted(feedbacks, "ACCEPTED");
        long rejected = countAccepted(feedbacks, "REJECTED");
        long partial = countAccepted(feedbacks, "PARTIAL");
        stats.setTotalCount(total);
        stats.setAcceptedCount(accepted);
        stats.setRejectedCount(rejected);
        stats.setPartialCount(partial);
        stats.setAcceptedRate(total == 0 ? 0D : accepted * 1D / total);
        Map<String, Long> distribution = feedbacks.stream()
            .filter(item -> !isBlank(item.getActualReasonCode()))
            .collect(Collectors.groupingBy(AgentDiagnosisFeedback::getActualReasonCode, java.util.LinkedHashMap::new, Collectors.counting()));
        stats.setActualReasonDistribution(distribution);
        return stats;
    }

    /**
     * 构造可持久化的客服反馈记录。
     */
    private AgentDiagnosisFeedback buildFeedback(AgentDiagnosisFeedbackRequest request) {
        AgentDiagnosisFeedback feedback = new AgentDiagnosisFeedback();
        feedback.setRequestId(request.getRequestId());
        feedback.setSessionId(request.getSessionId());
        feedback.setCustomerId(request.getCustomerId());
        feedback.setCustomerName(request.getCustomerName());
        feedback.setRecordDate(request.getRecordDate());
        feedback.setMealType(request.getMealType());
        feedback.setPredictedReasonCodes(JSON.toJSONString(request.getPredictedReasonCodes()));
        feedback.setAccepted(normalizeAccepted(request.getAccepted()));
        feedback.setActualReasonCode(request.getActualReasonCode());
        feedback.setComment(request.getComment());
        feedback.setOperator(currentUsername());
        feedback.setCreateTime(new Timestamp(System.currentTimeMillis()));
        return feedback;
    }

    /**
     * 根据查询条件构造 MyBatis-Plus Wrapper。
     */
    private LambdaQueryWrapper<AgentDiagnosisFeedback> wrapper(AgentDiagnosisFeedbackQueryCriteria criteria) {
        AgentDiagnosisFeedbackQueryCriteria safeCriteria = criteria == null ? new AgentDiagnosisFeedbackQueryCriteria() : criteria;
        return new LambdaQueryWrapper<AgentDiagnosisFeedback>()
            .eq(!isBlank(safeCriteria.getRequestId()), AgentDiagnosisFeedback::getRequestId, safeCriteria.getRequestId())
            .eq(safeCriteria.getCustomerId() != null, AgentDiagnosisFeedback::getCustomerId, safeCriteria.getCustomerId())
            .eq(!isBlank(safeCriteria.getRecordDate()), AgentDiagnosisFeedback::getRecordDate, safeCriteria.getRecordDate())
            .eq(!isBlank(safeCriteria.getMealType()), AgentDiagnosisFeedback::getMealType, safeCriteria.getMealType())
            .eq(!isBlank(safeCriteria.getAccepted()), AgentDiagnosisFeedback::getAccepted, normalizeAccepted(safeCriteria.getAccepted()))
            .eq(!isBlank(safeCriteria.getActualReasonCode()), AgentDiagnosisFeedback::getActualReasonCode, safeCriteria.getActualReasonCode())
            .orderByDesc(AgentDiagnosisFeedback::getCreateTime);
    }

    private long countAccepted(List<AgentDiagnosisFeedback> feedbacks, String accepted) {
        return feedbacks.stream().filter(item -> accepted.equals(item.getAccepted())).count();
    }

    private String normalizeAccepted(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private int normalizePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return 10;
        }
        return Math.min(size, 100);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String currentUsername() {
        try {
            return SecurityUtils.getCurrentUsername();
        } catch (Exception ex) {
            return "system";
        }
    }
}
