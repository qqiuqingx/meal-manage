package me.zhengjie.modules.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.AgentActionAudit;
import me.zhengjie.modules.agent.domain.dto.AgentActionAuditQueryCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentActionConfirmRequest;
import me.zhengjie.modules.agent.domain.dto.AgentActionConfirmResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisActionDraftDto;
import me.zhengjie.modules.agent.mapper.AgentActionAuditMapper;
import me.zhengjie.modules.agent.service.AgentActionConfirmService;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderBalanceRecalculateResult;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderEffectiveDateAdjustResult;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleAdjustmentResult;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.PageUtil;
import me.zhengjie.utils.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 智能排查动作草稿人工确认服务实现。
 */
@Service
@RequiredArgsConstructor
public class AgentActionConfirmServiceImpl implements AgentActionConfirmService {

    private static final Set<String> HIGH_RISK_LEVELS = new HashSet<>(Arrays.asList("HIGH"));
    private static final Set<String> CONFIRM_ONLY_ACTIONS = new HashSet<>(Arrays.asList(
        "CREATE_MANUAL_RECHECK_TASK",
        "CREATE_CUSTOMER_PROFILE_DRAFT",
        "CREATE_CUSTOMER_ORDER_DRAFT",
        "SUPPLEMENT_DISH_CANDIDATES"
    ));

    private final AgentActionAuditMapper actionAuditMapper;
    private final MealPlanService mealPlanService;
    private final CustomerProfileService customerProfileService;
    private final CustomerOrderService customerOrderService;
    private final CustomerOrderMapper customerOrderMapper;
    private final CustomerProfileMapper customerProfileMapper;
    private final MealPlanMapper mealPlanMapper;

    /**
     * 校验并确认动作草稿，命中幂等键时直接返回历史审计结果。
     *
     * @param request 动作草稿确认请求
     * @return 确认结果，包含审计状态、失败原因和执行结果
     */
    @Override
    public AgentActionConfirmResponse confirm(AgentActionConfirmRequest request) {
        AgentActionAudit existing = findByIdempotencyKey(request.getIdempotencyKey());
        if (existing != null) {
            return toResponse(existing, true);
        }
        AgentDiagnosisActionDraftDto draft = request.getActionDraft();
        enrichDraftDigest(draft);
        AgentActionAudit audit = buildAudit(request, draft);
        validateDraft(audit, draft, request);
        actionAuditMapper.insert(audit);
        boolean needsUpdate = false;
        if ("PENDING".equals(audit.getStatus())) {
            checkStaleDraft(audit, draft);
            needsUpdate = !"PENDING".equals(audit.getStatus());
        }
        if ("PENDING".equals(audit.getStatus())) {
            executeDraft(audit, draft);
            needsUpdate = true;
        }
        if (needsUpdate) {
            audit.setUpdateTime(new Timestamp(System.currentTimeMillis()));
            actionAuditMapper.updateById(audit);
        }
        return toResponse(audit, false);
    }

    /**
     * 分页查询动作确认审计记录，用于按请求或会话追踪人工确认结果。
     *
     * @param criteria 查询条件
     * @return 动作确认审计分页结果
     */
    @Override
    public PageResult<AgentActionAudit> queryAudits(AgentActionAuditQueryCriteria criteria) {
        AgentActionAuditQueryCriteria safeCriteria = criteria == null ? new AgentActionAuditQueryCriteria() : criteria;
        Page<AgentActionAudit> page = new Page<>(normalizePage(safeCriteria.getPage()) + 1L, normalizeSize(safeCriteria.getSize()));
        return PageUtil.toPage(actionAuditMapper.selectPage(page, auditWrapper(safeCriteria)));
    }

    /**
     * 构造动作确认审计查询条件，支持按会话、请求和动作状态追踪确认结果。
     */
    private LambdaQueryWrapper<AgentActionAudit> auditWrapper(AgentActionAuditQueryCriteria criteria) {
        return new LambdaQueryWrapper<AgentActionAudit>()
            .eq(!isBlank(criteria.getRequestId()), AgentActionAudit::getRequestId, criteria.getRequestId())
            .eq(!isBlank(criteria.getSessionId()), AgentActionAudit::getSessionId, criteria.getSessionId())
            .eq(!isBlank(criteria.getActionCode()), AgentActionAudit::getActionCode, normalize(criteria.getActionCode()))
            .eq(!isBlank(criteria.getStatus()), AgentActionAudit::getStatus, normalize(criteria.getStatus()))
            .eq(!isBlank(criteria.getOperator()), AgentActionAudit::getOperator, criteria.getOperator())
            .orderByDesc(AgentActionAudit::getUpdateTime)
            .orderByDesc(AgentActionAudit::getCreateTime);
    }

    /**
     * 按幂等键查询历史确认结果。
     */
    private AgentActionAudit findByIdempotencyKey(String idempotencyKey) {
        return actionAuditMapper.selectOne(new LambdaQueryWrapper<AgentActionAudit>()
            .eq(AgentActionAudit::getIdempotencyKey, idempotencyKey)
            .last("limit 1"));
    }

    /**
     * 构造动作确认审计记录。
     */
    private AgentActionAudit buildAudit(AgentActionConfirmRequest request, AgentDiagnosisActionDraftDto draft) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        AgentActionAudit audit = new AgentActionAudit();
        audit.setRequestId(request.getRequestId());
        audit.setSessionId(request.getSessionId());
        audit.setIdempotencyKey(request.getIdempotencyKey());
        audit.setDraftDigest(draft.getDraftDigest());
        audit.setActionCode(draft.getActionCode());
        audit.setActionTitle(draft.getTitle());
        audit.setRiskLevel(draft.getRiskLevel());
        audit.setTargetType(draft.getTargetType());
        audit.setTargetId(draft.getTargetId());
        audit.setBeforeSnapshot(JSON.toJSONString(draft.getBeforeSnapshot()));
        audit.setAfterPreview(JSON.toJSONString(draft.getAfterPreview()));
        audit.setRequiredPermission(draft.getRequiredPermission());
        audit.setSecondConfirmed(Boolean.TRUE.equals(request.getSecondConfirmed()));
        audit.setStatus("PENDING");
        audit.setSuccess(false);
        audit.setOperator(currentUsername());
        audit.setComment(request.getComment());
        audit.setCreateTime(now);
        audit.setUpdateTime(now);
        return audit;
    }

    /**
     * 补齐动作草稿摘要，保证审计和前端重复提交命中相同的草稿指纹。
     */
    private void enrichDraftDigest(AgentDiagnosisActionDraftDto draft) {
        if (draft == null) {
            return;
        }
        if (isBlank(draft.getDraftDigest())) {
            draft.setDraftDigest(digest(normalizeDraftDigestSource(draft)));
        }
        if (isBlank(draft.getSnapshotDigest())) {
            draft.setSnapshotDigest(digest(JSON.toJSONString(draft.getBeforeSnapshot())));
        }
        if (draft.getSnapshotTime() == null) {
            draft.setSnapshotTime(System.currentTimeMillis());
        }
    }

    /**
     * 校验动作草稿的必要字段和高风险二次确认状态。
     */
    private void validateDraft(AgentActionAudit audit, AgentDiagnosisActionDraftDto draft, AgentActionConfirmRequest request) {
        if (isBlank(draft.getActionCode())) {
            markFailed(audit, "VALIDATION_FAILED", "actionCode不能为空");
            return;
        }
        if (isBlank(draft.getTargetType())) {
            markFailed(audit, "VALIDATION_FAILED", "targetType不能为空");
            return;
        }
        if (isBlank(draft.getRequiredPermission())) {
            markFailed(audit, "VALIDATION_FAILED", "requiredPermission不能为空");
            return;
        }
        if (!hasRequiredPermission(draft.getRequiredPermission())) {
            markFailed(audit, "PERMISSION_DENIED", "当前用户缺少动作所需权限：" + draft.getRequiredPermission());
            return;
        }
        if (HIGH_RISK_LEVELS.contains(normalize(draft.getRiskLevel())) && !Boolean.TRUE.equals(request.getSecondConfirmed())) {
            markFailed(audit, "NEED_SECOND_CONFIRM", "高风险动作必须完成二次确认");
        }
    }

    /**
     * 确认前重新读取业务数据，阻止基于过期诊断结果继续执行写操作。
     */
    private void checkStaleDraft(AgentActionAudit audit, AgentDiagnosisActionDraftDto draft) {
        if (draft == null || isBlank(draft.getActionCode())) {
            return;
        }
        if ("RESUME_CUSTOMER_DELIVERY".equals(draft.getActionCode())) {
            staleCheckResumeCustomerDelivery(audit, draft);
            return;
        }
        if ("ADJUST_ORDER_EFFECTIVE_DATE".equals(draft.getActionCode())) {
            staleCheckOrderEffectiveDate(audit, draft);
            return;
        }
        if ("RECALCULATE_ORDER_BALANCE".equals(draft.getActionCode())) {
            staleCheckOrderBalance(audit, draft);
            return;
        }
        if ("REGENERATE_MEAL_PLAN".equals(draft.getActionCode())) {
            staleCheckRegenerateMealPlan(audit, draft);
            return;
        }
        audit.setStaleCheckResult("SKIPPED");
        audit.setStaleCheckDetail(null);
    }

    /**
     * 恢复配送前确认目标日期餐次仍在客户排除日期中，否则说明草稿已过期。
     */
    private void staleCheckResumeCustomerDelivery(AgentActionAudit audit, AgentDiagnosisActionDraftDto draft) {
        Long customerId = readLong(draft.getAfterPreview(), "customerId");
        if (customerId == null) {
            customerId = readLong(draft.getTargetId());
        }
        String recordDate = readString(draft.getAfterPreview(), "recordDate");
        String mealType = readString(draft.getAfterPreview(), "mealType");
        if (customerId == null || isBlank(recordDate) || isBlank(mealType)) {
            audit.setStaleCheckResult("SKIPPED");
            return;
        }
        CustomerProfile profile = customerProfileMapper.selectByIdWithJson(customerId);
        if (profile == null) {
            markStale(audit, "CUSTOMER_PROFILE_CHANGED", "业务数据已变化，请重新排查后再确认动作。", "诊断草稿生成后客户档案已不存在");
            return;
        }
        List<ExcludedDateDto> excludedDates = readExcludedDates(profile);
        if (!containsExcludedMeal(excludedDates, recordDate, mealType)) {
            markStale(audit, "CUSTOMER_DELIVERY_ALREADY_RESUMED", "业务数据已变化，请重新排查后再确认动作。", "诊断草稿生成后该客户日期餐次的排除配置已被移除");
            return;
        }
        audit.setStaleCheckResult("PASSED");
        audit.setStaleCheckDetail(null);
    }

    /**
     * 调整订单有效期前确认订单当前起止日期和状态仍与草稿生成时一致。
     */
    private void staleCheckOrderEffectiveDate(AgentActionAudit audit, AgentDiagnosisActionDraftDto draft) {
        Long orderId = readLong(draft.getAfterPreview(), "orderId");
        if (orderId == null) {
            orderId = readLong(draft.getTargetId());
        }
        if (orderId == null) {
            audit.setStaleCheckResult("SKIPPED");
            return;
        }
        CustomerOrder order = customerOrderMapper.selectById(orderId);
        if (order == null) {
            markStale(audit, "ORDER_MISSING", "业务数据已变化，请重新排查后再确认动作。", "诊断草稿生成后订单已不存在");
            return;
        }
        String currentStartDate = readString(draft.getAfterPreview(), "currentStartDate");
        String currentEndDate = readString(draft.getAfterPreview(), "currentEndDate");
        String actualStartDate = localDateToString(readField(order, "startDate", LocalDate.class));
        String actualEndDate = localDateToString(readField(order, "endDate", LocalDate.class));
        Integer status = readField(order, "status", Integer.class);
        if (String.valueOf(1).equals(readString(draft.getAfterPreview(), "currentStatus")) && status != null && status != 1) {
            markStale(audit, "ORDER_STATUS_CHANGED", "业务数据已变化，请重新排查后再确认动作。", "诊断草稿生成后订单状态已变化");
            return;
        }
        if (!sameNullableText(currentStartDate, actualStartDate) || !sameNullableText(currentEndDate, actualEndDate)) {
            markStale(audit, "ORDER_EFFECTIVE_DATE_CHANGED", "业务数据已变化，请重新排查后再确认动作。", "诊断草稿生成后订单有效期已变化");
            return;
        }
        if (status != null && status != 1) {
            markStale(audit, "ORDER_STATUS_CHANGED", "业务数据已变化，请重新排查后再确认动作。", "诊断草稿生成后订单状态已变化");
            return;
        }
        audit.setStaleCheckResult("PASSED");
        audit.setStaleCheckDetail(null);
    }

    /**
     * 重算订单余额前确认订单核销和余额关键字段仍与草稿一致。
     */
    private void staleCheckOrderBalance(AgentActionAudit audit, AgentDiagnosisActionDraftDto draft) {
        Long orderId = readLong(draft.getAfterPreview(), "orderId");
        if (orderId == null) {
            orderId = readLong(draft.getTargetId());
        }
        if (orderId == null) {
            audit.setStaleCheckResult("SKIPPED");
            return;
        }
        CustomerOrder order = customerOrderMapper.selectById(orderId);
        if (order == null) {
            markStale(audit, "ORDER_MISSING", "业务数据已变化，请重新排查后再确认动作。", "诊断草稿生成后订单已不存在");
            return;
        }
        boolean changed = false;
        changed = compareIntegerSnapshot(draft, "currentVerifiedCount", readField(order, "verifiedCount", Integer.class)) || changed;
        changed = compareIntegerSnapshot(draft, "currentRemainingCount", readField(order, "remainingCount", Integer.class)) || changed;
        changed = compareTextSnapshot(draft, "currentMealBalance", decimalToString(readField(order, "mealBalance", java.math.BigDecimal.class))) || changed;
        changed = compareIntegerSnapshot(draft, "currentStatus", readField(order, "status", Integer.class)) || changed;
        if (changed) {
            markStale(audit, "ORDER_BALANCE_CHANGED", "业务数据已变化，请重新排查后再确认动作。", "诊断草稿生成后订单核销或余额数据已变化");
            return;
        }
        audit.setStaleCheckResult("PASSED");
        audit.setStaleCheckDetail(null);
    }

    /**
     * 重新排餐前确认目标日期餐次当前没有已生成的有效排餐记录。
     */
    private void staleCheckRegenerateMealPlan(AgentActionAudit audit, AgentDiagnosisActionDraftDto draft) {
        String recordDate = readString(draft.getAfterPreview(), "recordDate");
        String mealType = readString(draft.getAfterPreview(), "mealType");
        if (isBlank(recordDate) || isBlank(mealType)) {
            String[] targetParts = splitTargetId(draft.getTargetId());
            recordDate = isBlank(recordDate) ? targetParts[0] : recordDate;
            mealType = isBlank(mealType) ? targetParts[1] : mealType;
        }
        if (isBlank(recordDate) || isBlank(mealType)) {
            audit.setStaleCheckResult("SKIPPED");
            return;
        }
        if (mealPlanMapper.findActiveByDateAndMealType(LocalDate.parse(recordDate), mealType) != null) {
            markStale(audit, "MEAL_PLAN_ALREADY_GENERATED", "业务数据已变化，请重新排查后再确认动作。", "诊断草稿生成后该日期餐次的排餐已生成");
            return;
        }
        audit.setStaleCheckResult("PASSED");
        audit.setStaleCheckDetail(null);
    }

    /**
     * 执行动作草稿；草稿类和配置补齐类动作仅确认登记，业务写操作由对应模块人工处理。
     */
    private void executeDraft(AgentActionAudit audit, AgentDiagnosisActionDraftDto draft) {
        if (CONFIRM_ONLY_ACTIONS.contains(draft.getActionCode())) {
            audit.setStatus("CONFIRMED");
            audit.setSuccess(true);
            audit.setFailureReason(null);
            return;
        }
        if ("REGENERATE_MEAL_PLAN".equals(draft.getActionCode())) {
            executeRegenerateMealPlan(audit, draft);
            return;
        }
        if ("RESUME_CUSTOMER_DELIVERY".equals(draft.getActionCode())) {
            executeResumeCustomerDelivery(audit, draft);
            return;
        }
        if ("ADJUST_ORDER_EFFECTIVE_DATE".equals(draft.getActionCode())) {
            executeAdjustOrderEffectiveDate(audit, draft);
            return;
        }
        if ("RECALCULATE_ORDER_BALANCE".equals(draft.getActionCode())) {
            executeRecalculateOrderBalance(audit, draft);
            return;
        }
        markFailed(audit, "UNSUPPORTED_ACTION", "该动作的主系统正式执行接口尚未接入，已记录审计但未写入业务数据");
    }

    /**
     * 正式执行订单餐数余额重算动作，按核销日志回写订单核销与余额字段。
     */
    private void executeRecalculateOrderBalance(AgentActionAudit audit, AgentDiagnosisActionDraftDto draft) {
        try {
            Long orderId = readLong(draft.getAfterPreview(), "orderId");
            if (orderId == null) {
                orderId = readLong(draft.getTargetId());
            }
            if (orderId == null) {
                markFailed(audit, "VALIDATION_FAILED", "重算订单剩余餐数缺少orderId");
                return;
            }
            CustomerOrderBalanceRecalculateResult result = customerOrderService.recalculateBalance(orderId);
            audit.setStatus("EXECUTED");
            audit.setSuccess(true);
            audit.setFailureReason(null);
            audit.setExecutionResult(JSON.toJSONString(result));
        } catch (Exception ex) {
            markFailed(audit, "EXECUTION_FAILED", "重算订单剩余餐数执行失败：" + ex.getMessage());
        }
    }

    /**
     * 正式执行订单有效期调整动作，只更新订单开始日期和结束日期。
     */
    private void executeAdjustOrderEffectiveDate(AgentActionAudit audit, AgentDiagnosisActionDraftDto draft) {
        try {
            Long orderId = readLong(draft.getAfterPreview(), "orderId");
            if (orderId == null) {
                orderId = readLong(draft.getTargetId());
            }
            String newStartDate = readString(draft.getAfterPreview(), "newStartDate");
            String newEndDate = readString(draft.getAfterPreview(), "newEndDate");
            if (orderId == null || (isBlank(newStartDate) && isBlank(newEndDate))) {
                markFailed(audit, "VALIDATION_FAILED", "调整订单有效期缺少orderId或新起止日期");
                return;
            }
            CustomerOrderEffectiveDateAdjustResult result = customerOrderService.adjustEffectiveDate(orderId, newStartDate, newEndDate);
            audit.setStatus("EXECUTED");
            audit.setSuccess(true);
            audit.setFailureReason(null);
            audit.setExecutionResult(JSON.toJSONString(result));
        } catch (Exception ex) {
            markFailed(audit, "EXECUTION_FAILED", "调整订单有效期执行失败：" + ex.getMessage());
        }
    }

    /**
     * 正式执行恢复客户配送动作，仅移除客户指定日期餐次的排除配置。
     */
    private void executeResumeCustomerDelivery(AgentActionAudit audit, AgentDiagnosisActionDraftDto draft) {
        try {
            String recordDate = readString(draft.getAfterPreview(), "recordDate");
            String mealType = readString(draft.getAfterPreview(), "mealType");
            Long customerId = readLong(draft.getAfterPreview(), "customerId");
            if (customerId == null) {
                customerId = readLong(draft.getTargetId());
            }
            if (customerId == null || isBlank(recordDate) || isBlank(mealType)) {
                markFailed(audit, "VALIDATION_FAILED", "恢复配送缺少customerId、recordDate或mealType");
                return;
            }
            CustomerMealScheduleAdjustmentResult result = customerProfileService.resumeCustomerDelivery(customerId, recordDate, mealType);
            audit.setStatus("EXECUTED");
            audit.setSuccess(true);
            audit.setFailureReason(null);
            audit.setExecutionResult(JSON.toJSONString(result));
        } catch (Exception ex) {
            markFailed(audit, "EXECUTION_FAILED", "恢复配送执行失败：" + ex.getMessage());
        }
    }

    /**
     * 正式执行重新排餐动作，复用排餐业务 Service 的校验、事务和生成逻辑。
     */
    private void executeRegenerateMealPlan(AgentActionAudit audit, AgentDiagnosisActionDraftDto draft) {
        try {
            String recordDate = readString(draft.getAfterPreview(), "recordDate");
            String mealType = readString(draft.getAfterPreview(), "mealType");
            Long customerId = readLong(draft.getAfterPreview(), "customerId");
            if (isBlank(recordDate) || isBlank(mealType)) {
                String[] targetParts = splitTargetId(draft.getTargetId());
                recordDate = isBlank(recordDate) ? targetParts[0] : recordDate;
                mealType = isBlank(mealType) ? targetParts[1] : mealType;
            }
            if (isBlank(recordDate) || isBlank(mealType)) {
                markFailed(audit, "VALIDATION_FAILED", "重新排餐缺少recordDate或mealType");
                return;
            }
            MealPlanGenerateResult result = mealPlanService.generateMealPlan(recordDate, mealType, customerId);
            audit.setStatus("EXECUTED");
            audit.setSuccess(true);
            audit.setFailureReason(null);
            audit.setExecutionResult(JSON.toJSONString(result));
        } catch (Exception ex) {
            markFailed(audit, "EXECUTION_FAILED", "重新排餐执行失败：" + ex.getMessage());
        }
    }

    /**
     * 标记审计记录为失败并写入明确原因。
     */
    private void markFailed(AgentActionAudit audit, String status, String reason) {
        audit.setStatus(status);
        audit.setSuccess(false);
        audit.setFailureReason(reason);
    }

    /**
     * 标记草稿已过期，并将原因写入审计，要求客服重新排查。
     */
    private void markStale(AgentActionAudit audit, String staleCheckResult, String message, String detail) {
        audit.setStatus("STALE_DRAFT");
        audit.setSuccess(false);
        audit.setFailureReason(detail);
        audit.setStaleCheckResult(staleCheckResult);
        audit.setStaleCheckDetail(detail);
        if (isBlank(message)) {
            return;
        }
    }

    /**
     * 转换审计记录为接口响应。
     */
    private AgentActionConfirmResponse toResponse(AgentActionAudit audit, boolean idempotentHit) {
        AgentActionConfirmResponse response = new AgentActionConfirmResponse();
        response.setAuditId(audit.getId());
        response.setActionCode(audit.getActionCode());
        response.setStatus(audit.getStatus());
        response.setSuccess(Boolean.TRUE.equals(audit.getSuccess()));
        response.setIdempotentHit(idempotentHit);
        response.setFailureReason(audit.getFailureReason());
        response.setExecutionResult(parseExecutionResult(audit.getExecutionResult()));
        response.setMessage(responseMessage(audit));
        return response;
    }

    private String successMessage(AgentActionAudit audit) {
        if ("EXECUTED".equals(audit.getStatus())) {
            return "动作确认并执行成功";
        }
        return "动作确认已记录";
    }

    private Object parseExecutionResult(String executionResult) {
        if (isBlank(executionResult)) {
            return null;
        }
        try {
            return JSON.parse(executionResult);
        } catch (Exception ex) {
            return executionResult;
        }
    }

    private String readString(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String responseMessage(AgentActionAudit audit) {
        if (Boolean.TRUE.equals(audit.getSuccess())) {
            return successMessage(audit);
        }
        if ("STALE_DRAFT".equals(audit.getStatus())) {
            return "业务数据已变化，请重新排查后再确认动作。";
        }
        return audit.getFailureReason();
    }

    private Long readLong(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return readLong(value);
    }

    private Long readLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String[] splitTargetId(String targetId) {
        String[] result = new String[] {null, null};
        if (isBlank(targetId)) {
            return result;
        }
        String[] parts = targetId.split("\\|", -1);
        result[0] = parts.length > 0 ? parts[0] : null;
        result[1] = parts.length > 1 ? parts[1] : null;
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean compareIntegerSnapshot(AgentDiagnosisActionDraftDto draft, String key, Integer currentValue) {
        String expected = firstNonBlank(readString(draft.getAfterPreview(), key), readString(draft.getBeforeSnapshot(), key));
        if (isBlank(expected)) {
            return false;
        }
        Integer expectedValue = readInteger(expected);
        return expectedValue != null && !expectedValue.equals(currentValue);
    }

    private boolean compareTextSnapshot(AgentDiagnosisActionDraftDto draft, String key, String currentValue) {
        String expected = firstNonBlank(readString(draft.getAfterPreview(), key), readString(draft.getBeforeSnapshot(), key));
        return !isBlank(expected) && !sameNullableText(expected, currentValue);
    }

    private Integer readInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private boolean sameNullableText(String expected, String actual) {
        return normalizeNullable(expected).equals(normalizeNullable(actual));
    }

    private String normalizeNullable(String value) {
        return value == null ? "" : value.trim();
    }

    private String decimalToString(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String localDateToString(LocalDate value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<ExcludedDateDto> readExcludedDates(CustomerProfile profile) {
        Object value = readRawField(profile, "excludedDates");
        return value instanceof List ? (List<ExcludedDateDto>) value : null;
    }

    private boolean containsExcludedMeal(List<ExcludedDateDto> excludedDates, String recordDate, String mealType) {
        if (excludedDates == null || excludedDates.isEmpty()) {
            return false;
        }
        for (ExcludedDateDto excludedDate : excludedDates) {
            if (excludedDate == null) {
                continue;
            }
            if (!sameNullableText(recordDate, excludedDate.getDate())) {
                continue;
            }
            if (excludedDate.getMealTypes() != null && excludedDate.getMealTypes().contains(mealType)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeDraftDigestSource(AgentDiagnosisActionDraftDto draft) {
        return (draft.getActionCode() == null ? "" : draft.getActionCode()) + "|"
            + (draft.getTargetType() == null ? "" : draft.getTargetType()) + "|"
            + (draft.getTargetId() == null ? "" : draft.getTargetId()) + "|"
            + JSON.toJSONString(draft.getAfterPreview());
    }

    private String digest(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = messageDigest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            return Integer.toHexString(value == null ? 0 : value.hashCode());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T readField(Object target, String fieldName, Class<T> expectedType) {
        Object value = readRawField(target, fieldName);
        if (value == null) {
            return null;
        }
        return expectedType.isInstance(value) ? (T) value : null;
    }

    private Object readRawField(Object target, String fieldName) {
        if (target == null || isBlank(fieldName)) {
            return null;
        }
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException ex) {
            return null;
        }
    }

    private Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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

    /**
     * 校验当前登录用户是否具备动作草稿声明的业务权限，管理员权限可执行全部动作。
     */
    private boolean hasRequiredPermission(String requiredPermission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        Set<String> required = parsePermissions(requiredPermission);
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        for (GrantedAuthority authority : authorities) {
            if (authority == null || authority.getAuthority() == null) {
                continue;
            }
            String current = authority.getAuthority();
            if ("admin".equals(current) || required.contains(current)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析动作草稿权限字段，兼容单权限和逗号分隔的多权限。
     */
    private Set<String> parsePermissions(String requiredPermission) {
        Set<String> permissions = new HashSet<>();
        if (isBlank(requiredPermission)) {
            return permissions;
        }
        String[] parts = requiredPermission.split(",");
        for (String part : parts) {
            if (!isBlank(part)) {
                permissions.add(part.trim());
            }
        }
        return permissions;
    }

    private String currentUsername() {
        try {
            return SecurityUtils.getCurrentUsername();
        } catch (Exception ex) {
            return "system";
        }
    }
}
