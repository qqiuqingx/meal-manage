package me.zhengjie.modules.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.AgentDiagnosisFeedback;
import me.zhengjie.modules.agent.domain.AgentRuleGap;
import me.zhengjie.modules.agent.domain.dto.AgentRuleGapQueryCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentRuleGapResponse;
import me.zhengjie.modules.agent.domain.dto.AgentRuleGapStatusRequest;
import me.zhengjie.modules.agent.mapper.AgentRuleGapMapper;
import me.zhengjie.modules.agent.service.AgentRuleGapService;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.PageUtil;
import me.zhengjie.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 智能排查规则缺口维护服务实现。
 */
@Service
@RequiredArgsConstructor
public class AgentRuleGapServiceImpl implements AgentRuleGapService {

    private static final Set<String> KNOWN_REASON_CODES = new HashSet<>(Arrays.asList(
        "CUSTOMER_NOT_FOUND",
        "CUSTOMER_EXCLUDE_DATE_HIT",
        "ORDER_MISSING",
        "ORDER_NOT_EFFECTIVE",
        "ORDER_EXPIRED",
        "ORDER_MEAL_TYPE_MISMATCH",
        "ORDER_REMAINING_COUNT_NOT_ENOUGH",
        "SCHEDULE_MODE_NOT_MATCH",
        "CANDIDATE_DISH_EMPTY",
        "DISH_FILTERED_BY_ALLERGY",
        "PACKAGE_SPEC_MISSING",
        "REFUND_OR_STOP_MEAL_HIT",
        "VERIFICATION_CONSUMED_COUNT",
        "MEAL_PLAN_GENERATION_FAILED",
        "MEAL_PLAN_ALREADY_EXISTS_BUT_CUSTOMER_MISSING",
        "DATA_INCOMPLETE_NEED_RECHECK",
        "AI_RESULT_INVALID"
    ));

    private static final Set<String> OPEN_STATUSES = new HashSet<>(Arrays.asList("OPEN", "IN_PROGRESS"));

    private static final Set<String> ALLOWED_STATUSES = new HashSet<>(Arrays.asList("OPEN", "IN_PROGRESS", "RESOLVED", "IGNORED"));

    private final AgentRuleGapMapper ruleGapMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createFromFeedback(AgentDiagnosisFeedback feedback) {
        if (feedback == null || !shouldCreateGap(feedback)) {
            return;
        }
        AgentRuleGap existing = findOpenGap(feedback);
        if (existing != null) {
            existing.setOccurrenceCount((existing.getOccurrenceCount() == null ? 0 : existing.getOccurrenceCount()) + 1);
            existing.setUpdateTime(new Timestamp(System.currentTimeMillis()));
            ruleGapMapper.updateById(existing);
            return;
        }
        ruleGapMapper.insert(buildGap(feedback));
    }

    @Override
    public PageResult<AgentRuleGap> query(AgentRuleGapQueryCriteria criteria) {
        AgentRuleGapQueryCriteria safeCriteria = criteria == null ? new AgentRuleGapQueryCriteria() : criteria;
        Page<AgentRuleGap> page = new Page<>(normalizePage(safeCriteria.getPage()) + 1L, normalizeSize(safeCriteria.getSize()));
        return PageUtil.toPage(ruleGapMapper.selectPage(page, wrapper(safeCriteria)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentRuleGapResponse updateStatus(Long id, AgentRuleGapStatusRequest request) {
        if (request == null) {
            return response(id, "VALIDATION_FAILED", "规则缺口状态更新请求不能为空");
        }
        String normalizedStatus = normalize(request.getStatus());
        AgentRuleGapResponse validationResponse = validateStatusChange(id, normalizedStatus, request);
        if (validationResponse != null) {
            return validationResponse;
        }
        AgentRuleGap gap = ruleGapMapper.selectById(id);
        if (gap == null) {
            AgentRuleGapResponse response = new AgentRuleGapResponse();
            response.setStatus("NOT_FOUND");
            response.setMessage("规则缺口不存在");
            return response;
        }
        gap.setStatus(normalizedStatus);
        gap.setOwner(request.getOwner());
        gap.setOperator(currentUsername());
        gap.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        if (!isBlank(request.getComment())) {
            String description = isBlank(gap.getGapDescription()) ? "" : gap.getGapDescription();
            gap.setGapDescription(description + "；处理备注：" + request.getComment());
        }
        ruleGapMapper.updateById(gap);
        AgentRuleGapResponse response = new AgentRuleGapResponse();
        response.setId(gap.getId());
        response.setStatus(gap.getStatus());
        response.setMessage("规则缺口状态已更新");
        return response;
    }

    /**
     * 校验规则缺口状态流转所需的维护信息，确保关闭缺口前留下规则维护证据。
     */
    private AgentRuleGapResponse validateStatusChange(Long id, String normalizedStatus, AgentRuleGapStatusRequest request) {
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            return response(id, "VALIDATION_FAILED", "规则缺口状态不合法");
        }
        if ("IN_PROGRESS".equals(normalizedStatus) && isBlank(request.getOwner())) {
            return response(id, "VALIDATION_FAILED", "规则缺口进入处理中必须指定处理人");
        }
        if ("RESOLVED".equals(normalizedStatus) && isBlank(request.getComment())) {
            return response(id, "VALIDATION_FAILED", "规则缺口标记已解决必须填写规则或评测维护证据");
        }
        return null;
    }

    private AgentRuleGapResponse response(Long id, String status, String message) {
        AgentRuleGapResponse response = new AgentRuleGapResponse();
        response.setId(id);
        response.setStatus(status);
        response.setMessage(message);
        return response;
    }

    /**
     * 判断反馈是否需要进入规则缺口维护池。
     */
    private boolean shouldCreateGap(AgentDiagnosisFeedback feedback) {
        String accepted = normalize(feedback.getAccepted());
        String actualReasonCode = normalize(feedback.getActualReasonCode());
        return "REJECTED".equals(accepted)
            || "PARTIAL".equals(accepted)
            || (!isBlank(actualReasonCode) && !KNOWN_REASON_CODES.contains(actualReasonCode));
    }

    /**
     * 查询同一真实原因码下尚未关闭的缺口，避免重复创建。
     */
    private AgentRuleGap findOpenGap(AgentDiagnosisFeedback feedback) {
        String actualReasonCode = normalize(feedback.getActualReasonCode());
        return ruleGapMapper.selectOne(new LambdaQueryWrapper<AgentRuleGap>()
            .eq(AgentRuleGap::getActualReasonCode, isBlank(actualReasonCode) ? "UNKNOWN" : actualReasonCode)
            .in(AgentRuleGap::getStatus, OPEN_STATUSES)
            .last("limit 1"));
    }

    /**
     * 从反馈记录构造规则缺口记录。
     */
    private AgentRuleGap buildGap(AgentDiagnosisFeedback feedback) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String actualReasonCode = normalize(feedback.getActualReasonCode());
        AgentRuleGap gap = new AgentRuleGap();
        gap.setGapType(resolveGapType(feedback));
        gap.setSourceType("FEEDBACK");
        gap.setSourceId(feedback.getId());
        gap.setRequestId(feedback.getRequestId());
        gap.setCustomerId(feedback.getCustomerId());
        gap.setRecordDate(feedback.getRecordDate());
        gap.setMealType(feedback.getMealType());
        gap.setPredictedReasonCodes(feedback.getPredictedReasonCodes());
        gap.setActualReasonCode(isBlank(actualReasonCode) ? "UNKNOWN" : actualReasonCode);
        gap.setAccepted(normalize(feedback.getAccepted()));
        gap.setGapDescription(description(feedback));
        gap.setOccurrenceCount(1);
        gap.setStatus("OPEN");
        gap.setOperator(currentUsername());
        gap.setCreateTime(now);
        gap.setUpdateTime(now);
        return gap;
    }

    private String resolveGapType(AgentDiagnosisFeedback feedback) {
        String actualReasonCode = normalize(feedback.getActualReasonCode());
        if (!isBlank(actualReasonCode) && !KNOWN_REASON_CODES.contains(actualReasonCode)) {
            return "UNKNOWN_REASON";
        }
        if ("REJECTED".equals(normalize(feedback.getAccepted()))) {
            return "WRONG_REASON";
        }
        return "PARTIAL_REASON";
    }

    private String description(AgentDiagnosisFeedback feedback) {
        return "客服反馈=" + normalize(feedback.getAccepted())
            + "，真实原因=" + (isBlank(feedback.getActualReasonCode()) ? "UNKNOWN" : feedback.getActualReasonCode())
            + "，备注=" + (isBlank(feedback.getComment()) ? "-" : feedback.getComment());
    }

    private LambdaQueryWrapper<AgentRuleGap> wrapper(AgentRuleGapQueryCriteria criteria) {
        return new LambdaQueryWrapper<AgentRuleGap>()
            .eq(!isBlank(criteria.getStatus()), AgentRuleGap::getStatus, normalize(criteria.getStatus()))
            .eq(!isBlank(criteria.getGapType()), AgentRuleGap::getGapType, normalize(criteria.getGapType()))
            .eq(!isBlank(criteria.getActualReasonCode()), AgentRuleGap::getActualReasonCode, normalize(criteria.getActualReasonCode()))
            .eq(!isBlank(criteria.getRecordDate()), AgentRuleGap::getRecordDate, criteria.getRecordDate())
            .orderByDesc(AgentRuleGap::getOccurrenceCount)
            .orderByDesc(AgentRuleGap::getUpdateTime);
    }

    private String normalize(String value) {
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
