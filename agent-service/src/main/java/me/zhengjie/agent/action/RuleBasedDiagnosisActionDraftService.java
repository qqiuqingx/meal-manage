package me.zhengjie.agent.action;

import me.zhengjie.agent.domain.dto.DiagnosisActionDraftDto;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 使用固定模板把原因码转换为动作草稿，避免模型直接给出可执行写操作。
 */
@Service
public class RuleBasedDiagnosisActionDraftService implements DiagnosisActionDraftService {

    private static final String CONFIRM_API = "/api/agent/action-drafts/confirm";

    @Override
    public DiagnosisResponse applyActionDrafts(DiagnosisResponse response) {
        if (response == null) {
            return null;
        }
        response.setActionDrafts(buildDrafts(response));
        return response;
    }

    /**
     * 按原因码去重生成动作草稿，兜底结果统一生成人工复核任务草稿。
     */
    private List<DiagnosisActionDraftDto> buildDrafts(DiagnosisResponse response) {
        if (response.isFallback() || response.getReasons() == null || response.getReasons().isEmpty()) {
            return List.of(manualRecheckDraft(response, null));
        }
        List<DiagnosisActionDraftDto> drafts = new ArrayList<>();
        Set<String> actionCodes = new LinkedHashSet<>();
        for (DiagnosisReasonDto reason : response.getReasons()) {
            DiagnosisActionDraftDto draft = draftForReason(response, reason);
            if (draft != null && actionCodes.add(draft.getActionCode())) {
                drafts.add(draft);
            }
        }
        if (drafts.isEmpty()) {
            drafts.add(manualRecheckDraft(response, null));
        }
        return drafts;
    }

    /**
     * 将单个原因码映射为动作模板。
     */
    private DiagnosisActionDraftDto draftForReason(DiagnosisResponse response, DiagnosisReasonDto reason) {
        if (reason == null || isBlank(reason.getCode())) {
            return null;
        }
        return switch (reason.getCode()) {
            case "CUSTOMER_NOT_FOUND" -> customerProfileDraft(response, reason);
            case "ORDER_MISSING" -> customerOrderDraft(response, reason);
            case "CUSTOMER_EXCLUDE_DATE_HIT" -> customerDeliveryDraft(response, reason);
            case "ORDER_NOT_EFFECTIVE", "ORDER_EXPIRED" -> orderEffectiveDateDraft(response, reason);
            case "ORDER_REMAINING_COUNT_NOT_ENOUGH", "VERIFICATION_CONSUMED_COUNT" -> orderBalanceDraft(response, reason);
            case "CANDIDATE_DISH_EMPTY", "DISH_FILTERED_BY_ALLERGY", "PACKAGE_SPEC_MISSING" -> dishCandidateDraft(response, reason);
            case "MEAL_PLAN_GENERATION_FAILED", "MEAL_PLAN_ALREADY_EXISTS_BUT_CUSTOMER_MISSING" -> regenerateMealPlanDraft(response, reason);
            case "DATA_INCOMPLETE_NEED_RECHECK", "AI_RESULT_INVALID" -> manualRecheckDraft(response, reason);
            default -> null;
        };
    }

    /**
     * 生成客户档案创建草稿。
     */
    private DiagnosisActionDraftDto customerProfileDraft(DiagnosisResponse response, DiagnosisReasonDto reason) {
        Map<String, Object> afterPreview = basePreview(response);
        afterPreview.put("profileReview", "按客户编号、姓名和联系方式补建客户档案");
        return draft("CREATE_CUSTOMER_PROFILE_DRAFT", "创建客户档案草稿", "客户不存在或编号无法匹配时，建议先补建客户档案草稿并人工核对。",
            "HIGH", "CUSTOMER", targetCustomer(response), reason, afterPreview, "customerProfile:add");
    }

    /**
     * 生成客户订单创建草稿。
     */
    private DiagnosisActionDraftDto customerOrderDraft(DiagnosisResponse response, DiagnosisReasonDto reason) {
        Map<String, Object> afterPreview = basePreview(response);
        afterPreview.put("orderReview", "按合同、套餐和服务周期补建客户订单");
        return draft("CREATE_CUSTOMER_ORDER_DRAFT", "创建客户订单草稿", "客户无有效订单时，建议按合同和套餐信息补建订单草稿。",
            "HIGH", "ORDER", targetCustomer(response), reason, afterPreview, "customerOrder:add");
    }

    /**
     * 生成恢复客户配送的草稿。
     */
    private DiagnosisActionDraftDto customerDeliveryDraft(DiagnosisResponse response, DiagnosisReasonDto reason) {
        Map<String, Object> afterPreview = basePreview(response);
        afterPreview.put("deliveryAvailable", true);
        afterPreview.put("excludeDateReview", "移除或调整命中日期/餐次的停送配置");
        return draft("RESUME_CUSTOMER_DELIVERY", "恢复客户配送", "复核客户停送或排除日期后恢复指定日期餐次配送。",
            "MEDIUM", "CUSTOMER", targetCustomer(response), reason, afterPreview, "customerProfile:edit");
    }

    /**
     * 生成订单有效期调整的草稿。
     */
    private DiagnosisActionDraftDto orderEffectiveDateDraft(DiagnosisResponse response, DiagnosisReasonDto reason) {
        Map<String, Object> afterPreview = basePreview(response);
        String orderId = targetOrder(reason);
        String startDate = evidenceValue(reason, "orders.startDate", "order.startDate");
        String endDate = evidenceValue(reason, "orders.endDate", "order.endDate");
        afterPreview.put("orderId", orderId);
        afterPreview.put("currentStartDate", startDate);
        afterPreview.put("currentEndDate", endDate);
        if ("ORDER_EXPIRED".equals(reason.getCode())) {
            afterPreview.put("newEndDate", response.getRecordDate());
        } else {
            afterPreview.put("newStartDate", response.getRecordDate());
        }
        afterPreview.put("effectiveDateReview", "调整订单起止日期覆盖诊断日期");
        return draft("ADJUST_ORDER_EFFECTIVE_DATE", "调整订单有效期", "复核订单合同和服务周期后调整订单起止日期。",
            "HIGH", "ORDER", orderId, reason, afterPreview, "customerOrder:edit");
    }

    /**
     * 生成订单餐数重算的草稿。
     */
    private DiagnosisActionDraftDto orderBalanceDraft(DiagnosisResponse response, DiagnosisReasonDto reason) {
        Map<String, Object> afterPreview = basePreview(response);
        String orderId = targetOrder(reason);
        afterPreview.put("orderId", orderId);
        afterPreview.put("balanceReview", "按订单餐数和核销记录重新计算剩余餐数");
        return draft("RECALCULATE_ORDER_BALANCE", "重算订单剩余餐数", "复核核销记录、退餐记录和订单餐数后重新计算餐数余额。",
            "HIGH", "ORDER", orderId, reason, afterPreview, "customerOrder:edit");
    }

    /**
     * 生成候选菜或套餐规格补充的草稿。
     */
    private DiagnosisActionDraftDto dishCandidateDraft(DiagnosisResponse response, DiagnosisReasonDto reason) {
        Map<String, Object> afterPreview = basePreview(response);
        afterPreview.put("candidateReview", "补充套餐规格、候选菜池或过敏过滤替代菜");
        return draft("SUPPLEMENT_DISH_CANDIDATES", "补充候选菜配置", "补齐套餐规格和候选菜配置，必要时配置过敏忌口替代菜。",
            "MEDIUM", "DISH_CONFIG", targetDateMeal(response), reason, afterPreview, "dish:edit");
    }

    /**
     * 生成重新排餐的草稿。
     */
    private DiagnosisActionDraftDto regenerateMealPlanDraft(DiagnosisResponse response, DiagnosisReasonDto reason) {
        Map<String, Object> afterPreview = basePreview(response);
        afterPreview.put("generationReview", "在配置修复后重新生成当前日期餐次排餐");
        return draft("REGENERATE_MEAL_PLAN", "重新生成排餐", "确认客户、订单和菜单配置均已修复后重新生成排餐。",
            "MEDIUM", "MEAL_PLAN", targetDateMeal(response), reason, afterPreview, "mealPlan:generate");
    }

    /**
     * 生成创建人工复核任务的兜底草稿。
     */
    private DiagnosisActionDraftDto manualRecheckDraft(DiagnosisResponse response, DiagnosisReasonDto reason) {
        Map<String, Object> afterPreview = basePreview(response);
        afterPreview.put("taskType", "MEAL_PLAN_DIAGNOSIS_RECHECK");
        afterPreview.put("fallbackReason", response.getFallbackReason());
        return draft("CREATE_MANUAL_RECHECK_TASK", "创建人工复核任务", "诊断证据不足或服务兜底时，创建人工复核任务并附带当前上下文。",
            "LOW", "RECHECK_TASK", targetDateMeal(response), reason, afterPreview, "agentDiagnosis:confirm");
    }

    /**
     * 装配动作草稿字段和证据快照。
     */
    private DiagnosisActionDraftDto draft(String actionCode,
                                          String title,
                                          String description,
                                          String riskLevel,
                                          String targetType,
                                          String targetId,
                                          DiagnosisReasonDto reason,
                                          Map<String, Object> afterPreview,
                                          String requiredPermission) {
        DiagnosisActionDraftDto draft = new DiagnosisActionDraftDto();
        draft.setActionCode(actionCode);
        draft.setTitle(title);
        draft.setDescription(description);
        draft.setRiskLevel(riskLevel);
        draft.setTargetType(targetType);
        draft.setTargetId(targetId);
        draft.setBeforeSnapshot(evidenceSnapshot(reason));
        draft.setAfterPreview(afterPreview);
        enrichDraftDigest(draft);
        draft.setRequiredPermission(requiredPermission);
        draft.setConfirmApi(CONFIRM_API);
        return draft;
    }

    /**
     * 生成动作草稿和快照摘要，便于主系统幂等和过期校验复用同一指纹。
     */
    private void enrichDraftDigest(DiagnosisActionDraftDto draft) {
        draft.setDraftDigest(digest((draft.getActionCode() == null ? "" : draft.getActionCode()) + "|"
            + (draft.getTargetType() == null ? "" : draft.getTargetType()) + "|"
            + (draft.getTargetId() == null ? "" : draft.getTargetId()) + "|"
            + toJson(draft.getAfterPreview())));
        draft.setSnapshotDigest(digest(toJson(draft.getBeforeSnapshot())));
        draft.setSnapshotTime(System.currentTimeMillis());
    }

    /**
     * 将诊断证据转换为动作草稿的变更前快照。
     */
    private Map<String, Object> evidenceSnapshot(DiagnosisReasonDto reason) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (reason == null) {
            snapshot.put("source", "fallback");
            return snapshot;
        }
        snapshot.put("reasonCode", reason.getCode());
        snapshot.put("reasonTitle", reason.getTitle());
        if (reason.getRuleIds() != null && !reason.getRuleIds().isEmpty()) {
            snapshot.put("ruleIds", reason.getRuleIds());
        }
        if (reason.getEvidence() != null) {
            for (DiagnosisEvidenceDto evidence : reason.getEvidence()) {
                if (evidence != null && !isBlank(evidence.getLabel())) {
                    snapshot.put(evidence.getLabel(), evidence.getValue());
                }
            }
        }
        return snapshot;
    }

    /**
     * 生成动作草稿共用的变更后预览字段。
     */
    private Map<String, Object> basePreview(DiagnosisResponse response) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("customerId", response.getCustomerId());
        preview.put("customerName", response.getCustomerName());
        preview.put("recordDate", response.getRecordDate());
        preview.put("mealType", response.getMealType());
        preview.put("executeMode", "MANUAL_CONFIRM_REQUIRED");
        return preview;
    }

    private String targetCustomer(DiagnosisResponse response) {
        return response.getCustomerId() == null ? null : String.valueOf(response.getCustomerId());
    }

    private String targetDateMeal(DiagnosisResponse response) {
        return (response.getRecordDate() == null ? "" : response.getRecordDate())
            + "|" + (response.getMealType() == null ? "" : response.getMealType());
    }

    private String targetOrder(DiagnosisReasonDto reason) {
        return evidenceValue(reason, "orders.orderId", "order.orderId", "orderId");
    }

    private String evidenceValue(DiagnosisReasonDto reason, String... labels) {
        if (reason == null || reason.getEvidence() == null || labels == null) {
            return null;
        }
        for (String label : labels) {
            for (DiagnosisEvidenceDto evidence : reason.getEvidence()) {
                if (evidence != null && label.equals(evidence.getLabel()) && evidence.getValue() != null) {
                    return String.valueOf(evidence.getValue());
                }
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String toJson(Object value) {
        return value == null ? "" : String.valueOf(value);
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
}
