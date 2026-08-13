package me.zhengjie.modules.agent.formdraft.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.formdraft.domain.AgentFormDraft;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftClaimResult;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftSaveRequest;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftSaveResult;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftSummary;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftWarning;
import me.zhengjie.modules.agent.formdraft.domain.dto.CustomerOrderDraftPayload;
import me.zhengjie.modules.agent.formdraft.domain.dto.CustomerWithOrderDraftPayload;
import me.zhengjie.modules.agent.formdraft.domain.enums.AgentFormDraftStatus;
import me.zhengjie.modules.agent.formdraft.domain.enums.AgentFormDraftType;
import me.zhengjie.modules.agent.formdraft.mapper.AgentFormDraftMapper;
import me.zhengjie.modules.agent.formdraft.service.AgentFormDraftService;
import me.zhengjie.modules.agent.security.AgentAccessContext;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import me.zhengjie.modules.agent.security.AgentQueryPermissionService;
import me.zhengjie.modules.agent.session.domain.AgentChatMessage;
import me.zhengjie.modules.agent.session.domain.AgentChatSession;
import me.zhengjie.modules.agent.session.mapper.AgentChatMessageMapper;
import me.zhengjie.modules.agent.session.mapper.AgentChatSessionMapper;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.domain.ParentPackageSub;
import me.zhengjie.modules.customer.pkg.domain.SubPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageSubMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.mapper.DishMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Agent 表单草稿生命周期服务实现。 */
@Service
@RequiredArgsConstructor
public class AgentFormDraftServiceImpl implements AgentFormDraftService {
    private static final String SCHEMA_VERSION = "v1";
    private static final int MAX_FIELDS = 128;
    private static final int MAX_WARNINGS = 32;
    private static final Set<String> WARNING_CODES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "CUSTOMER_AMBIGUOUS", "PACKAGE_AMBIGUOUS", "SUB_PACKAGE_AMBIGUOUS", "REPLACE_DISH_AMBIGUOUS",
        "TRIAL_ORDER_AMBIGUOUS", "CONFLICTING_INPUT", "ASSOCIATION_REVIEW_REQUIRED", "FIELD_REVIEW_REQUIRED"
    )));
    private static final Set<String> BLOCKING_WARNING_CODES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "CUSTOMER_AMBIGUOUS", "PACKAGE_AMBIGUOUS", "SUB_PACKAGE_AMBIGUOUS", "REPLACE_DISH_AMBIGUOUS",
        "TRIAL_ORDER_AMBIGUOUS", "CONFLICTING_INPUT"
    )));
    private static final Set<String> CUSTOMER_PATHS = paths(
        "customer.customerCode", "customer.customerName", "customer.phone",
        "customer.gestationalWeek", "customer.allergyTags", "customer.excludedDishIds", "customer.excludedDates",
        "customer.medicalRequirements", "customer.specialRequirements", "customer.productionDate", "customer.remark",
        "customer.addresses", "order.parentPackageId", "order.childPackageId", "order.breakfastCount",
        "order.lunchDinnerCount", "order.breakfastPrice", "order.lunchDinnerPrice", "order.totalAmount",
        "order.depositAmount", "order.finalAmount", "order.dealTime", "order.firstDeliveryTime", "order.startDate",
        "order.startMealType", "order.endDate", "order.mealType", "order.scheduleMode", "order.deliveryDates",
        "order.customerSource", "order.trialConverted", "order.trialOrderId", "order.mainDishCount",
        "order.sideDishCount", "order.vegCount", "order.riceCount", "order.riceType", "order.soupCount",
        "customer.addresses.addressType", "customer.addresses.addressDetail", "customer.addresses.contactName",
        "customer.addresses.contactPhone", "customer.excludedDates.date", "customer.excludedDates.mealTypes",
        "order.deliveryDates.date", "order.deliveryDates.mealTypes", "order.replaceRules.sourceDishId",
        "order.replaceRules.targetDishId", "order.replaceRules.enabled", "order.replaceRules.remark",
        "order.remark", "order.replaceRules"
    );
    private static final Set<String> ORDER_PATHS = paths(
        "customerId", "customerCode", "parentPackageId", "childPackageId", "breakfastCount", "lunchDinnerCount",
        "breakfastPrice", "lunchDinnerPrice", "totalAmount", "depositAmount", "finalAmount", "dealTime",
        "firstDeliveryTime", "startDate", "startMealType", "endDate", "mealType", "scheduleMode", "deliveryDates",
        "customerSource", "trialConverted", "trialOrderId", "mainDishCount", "sideDishCount", "vegCount",
        "riceCount", "riceType", "soupCount", "remark", "replaceRules", "deliveryDates.date",
        "deliveryDates.mealTypes", "replaceRules.sourceDishId", "replaceRules.targetDishId",
        "replaceRules.enabled", "replaceRules.remark"
    );

    private final AgentFormDraftMapper draftMapper;
    private final AgentChatSessionMapper sessionMapper;
    private final AgentChatMessageMapper messageMapper;
    private final AgentQueryPermissionService permissionService;
    private final CustomerProfileMapper customerProfileMapper;
    private final CustomerOrderMapper customerOrderMapper;
    private final ParentPackageMapper parentPackageMapper;
    private final SubPackageMapper subPackageMapper;
    private final ParentPackageSubMapper parentPackageSubMapper;
    private final DishMapper dishMapper;

    @Value("${agent.form-draft.expiration-hours:24}")
    private long expirationHours;

    /** 草稿写入总开关；测试替身默认开启，运行环境由配置覆盖为安全默认关闭。 */
    @Value("${agent.form-draft.enabled:false}")
    private boolean formDraftEnabled = true;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentFormDraftSaveResult save(AgentFormDraftSaveRequest request, AgentAccessContext context) {
        if (!formDraftEnabled) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORM_DRAFT_DISABLED");
        requireRequestContext(request, context);
        AgentFormDraftType type = parseType(request.getDraftType());
        permissionService.require(context, type.getTargetPermission());
        Object payload = parseAndValidatePayload(type, request.getPayload());
        List<String> recognized = validateFieldPaths(type, request.getRecognizedFields(), "recognizedFields");
        List<String> missing = ensureOrderIdentityMissing(type,
            validateFieldPaths(type, request.getMissingFields(), "missingFields"), payload);
        List<AgentFormDraftWarning> warnings = validateWarnings(request.getWarnings());
        validateAssociations(type, payload);
        AgentChatMessage source = requireSourceMessage(request, context);

        if (StringUtils.isBlank(request.getDraftId())) {
            return create(request, context, type, payload, recognized, missing, warnings, source);
        }
        return revise(request, context, type, payload, recognized, missing, warnings);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentFormDraftClaimResult claim(String draftId, Long operatorId) {
        AgentFormDraft draft = draftMapper.selectOwnedForUpdate(cleanId(draftId), operatorId);
        if (draft == null) throw notFound();
        permissionService.requireCurrent(draft.getTargetPermission());
        draft = expireIfNecessary(draft);
        AgentFormDraftStatus status = AgentFormDraftStatus.valueOf(draft.getStatus());
        if (status != AgentFormDraftStatus.READY && status != AgentFormDraftStatus.CLAIMED) {
            throw conflict("DRAFT_NOT_CLAIMABLE");
        }
        validateAssociations(parseType(draft.getDraftType()), parseStoredPayload(draft));
        if (status == AgentFormDraftStatus.READY) {
            draft.setStatus(AgentFormDraftStatus.CLAIMED.name());
            draft.setClaimedAt(now());
            draft.setUpdateTime(now());
            if (draftMapper.updateById(draft) != 1) throw conflict("DRAFT_CLAIM_CONFLICT");
        }
        return toClaimResult(draft);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentFormDraftSummary summary(String draftId, Long operatorId) {
        AgentFormDraft draft = requireOwned(draftId, operatorId);
        permissionService.requireCurrent(draft.getTargetPermission());
        draft = expireIfNecessary(draft);
        return toSummary(draft);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> conversationContext(String draftId, String sessionId, Long operatorId) {
        AgentFormDraft draft = StringUtils.isBlank(draftId)
            ? draftMapper.selectLatestRevisable(operatorId, cleanSessionId(sessionId))
            : requireOwned(draftId, operatorId);
        if (draft == null || !StringUtils.equals(draft.getSourceSessionId(), cleanSessionId(sessionId))) {
            return Collections.emptyMap();
        }
        draft = expireIfNecessary(draft);
        if (!isRevisable(draft)) return Collections.emptyMap();
        permissionService.requireCurrent(draft.getTargetPermission());
        validateAssociations(parseType(draft.getDraftType()), parseStoredPayload(draft));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("draftId", draft.getDraftId());
        result.put("draftType", draft.getDraftType());
        result.put("schemaVersion", draft.getSchemaVersion());
        result.put("revision", draft.getRevision());
        result.put("payload", parseStoredPayload(draft));
        result.put("recognizedFields", readStrings(draft.getRecognizedFields()));
        result.put("missingFields", readStrings(draft.getMissingFields()));
        result.put("warnings", readWarnings(draft.getWarnings()));
        return result;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentFormDraft lockForSubmission(String draftId, Long operatorId, Integer expectedRevision, String expectedType) {
        AgentFormDraft draft = draftMapper.selectOwnedForUpdate(cleanId(draftId), operatorId);
        if (draft == null) throw notFound();
        permissionService.requireCurrent(draft.getTargetPermission());
        draft = expireIfNecessary(draft);
        if (!StringUtils.equals(draft.getDraftType(), expectedType)) throw conflict("DRAFT_TYPE_MISMATCH");
        if (expectedRevision == null || !expectedRevision.equals(draft.getRevision())) throw conflict("DRAFT_VERSION_CONFLICT");
        if (!AgentFormDraftStatus.CLAIMED.name().equals(draft.getStatus())) throw conflict("DRAFT_NOT_SUBMITTABLE");
        validateAssociations(parseType(draft.getDraftType()), parseStoredPayload(draft));
        return draft;
    }

    /** {@inheritDoc} */
    @Override
    public void markSubmitted(AgentFormDraft lockedDraft, Long targetBusinessId) {
        if (lockedDraft == null || lockedDraft.getId() == null || targetBusinessId == null) {
            throw badRequest("DRAFT_SUBMISSION_ARGUMENT_INVALID");
        }
        if (!AgentFormDraftStatus.CLAIMED.name().equals(lockedDraft.getStatus())) throw conflict("DRAFT_NOT_SUBMITTABLE");
        lockedDraft.setStatus(AgentFormDraftStatus.SUBMITTED.name());
        lockedDraft.setSubmittedAt(now());
        lockedDraft.setTargetBusinessId(targetBusinessId);
        lockedDraft.setPayload(null);
        lockedDraft.setUpdateTime(now());
        if (draftMapper.updateById(lockedDraft) != 1) throw conflict("DRAFT_SUBMISSION_CONFLICT");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireBatch(int batchSize) {
        int limit = Math.max(1, Math.min(batchSize, 500));
        List<AgentFormDraft> drafts = draftMapper.selectExpiredBatch(now(), limit);
        int count = 0;
        for (AgentFormDraft draft : drafts) {
            Timestamp updateTime = now();
            int updated = draftMapper.expireIfActive(draft.getId(), draft.getStatus(), updateTime, updateTime);
            if (updated == 1) {
                draft.setStatus(AgentFormDraftStatus.EXPIRED.name());
                draft.setPayload(null);
                draft.setUpdateTime(updateTime);
                count++;
            }
        }
        return count;
    }

    /** 创建草稿；并发命中唯一键时返回同一幂等结果。 */
    private AgentFormDraftSaveResult create(AgentFormDraftSaveRequest request, AgentAccessContext context,
                                            AgentFormDraftType type, Object payload, List<String> recognized,
                                            List<String> missing, List<AgentFormDraftWarning> warnings,
                                            AgentChatMessage source) {
        AgentFormDraft existing = draftMapper.selectByCreateIdempotencyKey(context.getOperatorId(),
            request.getSourceSessionId(), request.getClientMessageId());
        if (existing != null) return toSaveResult(existing, "IDEMPOTENT_REPLAY");
        AgentFormDraft draft = new AgentFormDraft();
        draft.setDraftId("afd_" + UUID.randomUUID().toString().replace("-", ""));
        draft.setDraftType(type.name());
        draft.setSchemaVersion(SCHEMA_VERSION);
        draft.setStatus(resolveStatus(type, payload, warnings).name());
        draft.setRevision(1);
        draft.setPayload(JSON.toJSONString(payload));
        draft.setRecognizedFields(JSON.toJSONString(recognized));
        draft.setMissingFields(JSON.toJSONString(missing));
        draft.setWarnings(JSON.toJSONString(warnings));
        draft.setSourceSessionId(request.getSourceSessionId());
        draft.setSourceMessageId(String.valueOf(source.getId()));
        draft.setSourceClientMessageId(request.getClientMessageId());
        draft.setOwnerUserId(context.getOperatorId());
        draft.setTargetPermission(type.getTargetPermission());
        draft.setConvertedFrom(trim(request.getConvertedFrom()));
        draft.setExpiresAt(expiresAt());
        draft.setCreateBy(context.getOperatorName());
        draft.setUpdateBy(context.getOperatorName());
        draft.setCreateTime(now());
        draft.setUpdateTime(now());
        draft.setIsDel(Boolean.FALSE);
        try {
            draftMapper.insert(draft);
            return toSaveResult(draft, "CREATED");
        } catch (DuplicateKeyException ex) {
            AgentFormDraft replay = draftMapper.selectByCreateIdempotencyKey(context.getOperatorId(),
                request.getSourceSessionId(), request.getClientMessageId());
            if (replay == null) throw ex;
            return toSaveResult(replay, "IDEMPOTENT_REPLAY");
        }
    }

    /** 使用条件更新修订草稿并返回递增后的版本。 */
    private AgentFormDraftSaveResult revise(AgentFormDraftSaveRequest request, AgentAccessContext context,
                                            AgentFormDraftType type, Object payload, List<String> recognized,
                                            List<String> missing, List<AgentFormDraftWarning> warnings) {
        if (request.getExpectedRevision() == null || request.getExpectedRevision() < 1) {
            throw badRequest("DRAFT_EXPECTED_REVISION_REQUIRED");
        }
        AgentFormDraft existing = requireOwned(request.getDraftId(), context.getOperatorId());
        boolean converting = AgentFormDraftType.CREATE_ORDER.name().equals(existing.getDraftType())
            && type == AgentFormDraftType.CREATE_CUSTOMER_WITH_ORDER
            && AgentFormDraftType.CREATE_ORDER.name().equals(trim(request.getConvertedFrom()));
        if (!type.name().equals(existing.getDraftType()) && !converting) throw conflict("DRAFT_TYPE_MISMATCH");
        if (isExpired(existing)) {
            existing = expireIfNecessary(existing);
            throw conflict("DRAFT_EXPIRED");
        }
        if (!isRevisable(existing)) throw conflict("DRAFT_NOT_EDITABLE");
        existing.setPayload(JSON.toJSONString(payload));
        existing.setDraftType(type.name());
        existing.setTargetPermission(type.getTargetPermission());
        existing.setRecognizedFields(JSON.toJSONString(recognized));
        existing.setMissingFields(JSON.toJSONString(missing));
        existing.setWarnings(JSON.toJSONString(warnings));
        existing.setStatus(resolveStatus(type, payload, warnings).name());
        existing.setExpiresAt(expiresAt());
        existing.setConvertedFrom(trim(request.getConvertedFrom()));
        existing.setUpdateBy(context.getOperatorName());
        existing.setUpdateTime(now());
        int updated = draftMapper.updateRevision(existing, request.getExpectedRevision());
        if (updated != 1) throw conflict("DRAFT_VERSION_CONFLICT");
        existing.setRevision(request.getExpectedRevision() + 1);
        return toSaveResult(existing, "UPDATED");
    }

    /** 校验签名上下文、会话和请求协议的一致性。 */
    private void requireRequestContext(AgentFormDraftSaveRequest request, AgentAccessContext context) {
        if (request == null || context == null || context.getOperatorId() == null) throw badRequest("DRAFT_CONTEXT_INVALID");
        if (!SCHEMA_VERSION.equals(request.getSchemaVersion())) throw badRequest("DRAFT_SCHEMA_UNSUPPORTED");
        if (!StringUtils.equals(request.getSourceSessionId(), context.getSessionId())) throw badRequest("DRAFT_SESSION_MISMATCH");
        if (StringUtils.length(request.getSourceSessionId()) > 80 || StringUtils.length(request.getClientMessageId()) > 80) {
            throw badRequest("DRAFT_SOURCE_TOO_LONG");
        }
        AgentChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<AgentChatSession>()
            .eq(AgentChatSession::getSessionId, request.getSourceSessionId()).last("LIMIT 1"));
        if (session == null || !StringUtils.equals(session.getOperator(), context.getOperatorName())) throw notFound();
    }

    /** 校验创建幂等键对应的本轮用户消息确实属于该会话。 */
    private AgentChatMessage requireSourceMessage(AgentFormDraftSaveRequest request, AgentAccessContext context) {
        AgentChatMessage message = messageMapper.selectOne(new LambdaQueryWrapper<AgentChatMessage>()
            .eq(AgentChatMessage::getSessionId, request.getSourceSessionId())
            .eq(AgentChatMessage::getClientMessageId, request.getClientMessageId())
            .eq(AgentChatMessage::getRole, "USER").last("LIMIT 1"));
        if (message == null) throw notFound();
        return message;
    }

    /** 按草稿类型严格转换 payload，并拒绝所有未登记字段。 */
    private Object parseAndValidatePayload(AgentFormDraftType type, JSONObject payload) {
        if (payload == null) throw badRequest("DRAFT_PAYLOAD_REQUIRED");
        Set<String> allowed = type == AgentFormDraftType.CREATE_ORDER ? ORDER_PATHS : CUSTOMER_PATHS;
        validateJsonKeys(payload, "", allowed);
        try {
            if (type == AgentFormDraftType.CREATE_ORDER) return payload.toJavaObject(CustomerOrderDraftPayload.class);
            return payload.toJavaObject(CustomerWithOrderDraftPayload.class);
        } catch (Exception ex) {
            throw badRequest("DRAFT_PAYLOAD_INVALID");
        }
    }

    /** 递归校验对象字段，数组元素沿用父字段类型约束。 */
    private void validateJsonKeys(Object node, String prefix, Set<String> allowed) {
        if (node instanceof java.util.Map) {
            java.util.Map<?, ?> object = (java.util.Map<?, ?>) node;
            for (Object rawKey : object.keySet()) {
                String key = String.valueOf(rawKey);
                String path = StringUtils.isBlank(prefix) ? key : prefix + "." + key;
                boolean accepted = allowed.contains(path) || allowed.stream().anyMatch(item -> item.startsWith(path + "."));
                if (!accepted) throw badRequest("DRAFT_UNKNOWN_FIELD");
                validateJsonKeys(object.get(rawKey), path, allowed);
            }
        } else if (node instanceof JSONArray) {
            for (Object item : (JSONArray) node) validateJsonKeys(item, prefix, allowed);
        }
    }

    /** 校验模型给出的字段路径只引用协议登记字段。 */
    private List<String> validateFieldPaths(AgentFormDraftType type, List<String> values, String label) {
        if (values == null) return new ArrayList<>();
        if (values.size() > MAX_FIELDS) throw badRequest("DRAFT_TOO_MANY_FIELDS");
        Set<String> allowed = type == AgentFormDraftType.CREATE_ORDER ? ORDER_PATHS : CUSTOMER_PATHS;
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String path = trim(value);
            if (!allowed.contains(path)) throw badRequest("DRAFT_FIELD_PATH_INVALID");
            result.add(path);
        }
        return new ArrayList<>(result);
    }

    /** 为缺少唯一客户身份的新增订单补充受控缺失字段，供转换动作确定性判断。 */
    private List<String> ensureOrderIdentityMissing(AgentFormDraftType type, List<String> values, Object payload) {
        if (type != AgentFormDraftType.CREATE_ORDER || !(payload instanceof CustomerOrderDraftPayload)) return values;
        CustomerOrderDraftPayload order = (CustomerOrderDraftPayload) payload;
        LinkedHashSet<String> result = new LinkedHashSet<>(values == null ? Collections.emptyList() : values);
        if (order.getCustomerId() == null) result.add("customerId");
        if (StringUtils.isBlank(order.getCustomerCode())) result.add("customerCode");
        return new ArrayList<>(result);
    }

    /** 校验告警码白名单及安全文本长度。 */
    private List<AgentFormDraftWarning> validateWarnings(List<AgentFormDraftWarning> values) {
        if (values == null) return new ArrayList<>();
        if (values.size() > MAX_WARNINGS) throw badRequest("DRAFT_TOO_MANY_WARNINGS");
        List<AgentFormDraftWarning> result = new ArrayList<>();
        for (AgentFormDraftWarning warning : values) {
            String code = warning == null ? null : StringUtils.upperCase(trim(warning.getCode()), Locale.ROOT);
            if (!WARNING_CODES.contains(code)) throw badRequest("DRAFT_WARNING_CODE_INVALID");
            if (StringUtils.length(warning.getMessage()) > 200) throw badRequest("DRAFT_WARNING_MESSAGE_TOO_LONG");
            warning.setCode(code);
            warning.setMessage(safeWarningMessage(code));
            result.add(warning);
        }
        return result;
    }

    /** 校验客户、套餐、子套餐、菜品和试餐订单关联仍有效且可见。 */
    private void validateAssociations(AgentFormDraftType type, Object payload) {
        CustomerOrderDraftPayload order;
        if (type == AgentFormDraftType.CREATE_ORDER) {
            order = (CustomerOrderDraftPayload) payload;
            if (order.getCustomerId() != null) {
                CustomerProfile customer = customerProfileMapper.selectById(order.getCustomerId());
                if (customer == null || !AgentCustomerDataScopeContext.allows(customer.getId())) throw notFound();
                if (StringUtils.isBlank(order.getCustomerCode())
                    || !StringUtils.equals(customer.getCustomerCode(), order.getCustomerCode())) {
                    throw badRequest("DRAFT_CUSTOMER_IDENTITY_INVALID");
                }
            }
        } else {
            CustomerWithOrderDraftPayload customerPayload = (CustomerWithOrderDraftPayload) payload;
            order = customerPayload == null ? null : customerPayload.getOrder();
        }
        if (order == null) return;
        if (order.getParentPackageId() != null) {
            ParentPackage parent = parentPackageMapper.selectById(order.getParentPackageId());
            if (parent == null || !Boolean.TRUE.equals(parent.getStatus())) throw badRequest("DRAFT_PARENT_PACKAGE_INVALID");
        }
        if (order.getChildPackageId() != null) {
            SubPackage child = subPackageMapper.selectById(order.getChildPackageId());
            if (child == null || !Boolean.TRUE.equals(child.getStatus())) throw badRequest("DRAFT_SUB_PACKAGE_INVALID");
            if (order.getParentPackageId() == null || parentPackageSubMapper.selectCount(
                new LambdaQueryWrapper<ParentPackageSub>().eq(ParentPackageSub::getParentPackageId, order.getParentPackageId())
                    .eq(ParentPackageSub::getSubPackageId, order.getChildPackageId()).eq(ParentPackageSub::getStatus, 1)) == 0) {
                throw badRequest("DRAFT_PACKAGE_RELATION_INVALID");
            }
        }
        if (Boolean.TRUE.equals(order.getTrialConverted())) {
            CustomerOrder trial = order.getTrialOrderId() == null ? null : customerOrderMapper.selectById(order.getTrialOrderId());
            if (trial == null || !AgentCustomerDataScopeContext.allows(trial.getCustomerId())) throw badRequest("DRAFT_TRIAL_ORDER_INVALID");
        }
        if (order.getReplaceRules() != null) {
            for (CustomerOrderDraftPayload.ReplaceRuleDraft rule : order.getReplaceRules()) {
                requireEnabledDish(rule == null ? null : rule.getSourceDishId());
                requireEnabledDish(rule == null ? null : rule.getTargetDishId());
            }
        }
    }

    /** 校验换菜规则引用的菜品存在且启用。 */
    private void requireEnabledDish(Long dishId) {
        if (dishId == null) throw badRequest("DRAFT_DISH_INVALID");
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null || !Boolean.TRUE.equals(dish.getEnabled())) throw badRequest("DRAFT_DISH_INVALID");
    }

    /** 从阻塞告警确定 EDITABLE 或 READY，模型不能直接指定状态。 */
    private AgentFormDraftStatus resolveStatus(AgentFormDraftType type, Object payload,
                                               List<AgentFormDraftWarning> warnings) {
        if (type == AgentFormDraftType.CREATE_ORDER
            && (payload == null || ((CustomerOrderDraftPayload) payload).getCustomerId() == null
            || StringUtils.isBlank(((CustomerOrderDraftPayload) payload).getCustomerCode()))) {
            return AgentFormDraftStatus.EDITABLE;
        }
        for (AgentFormDraftWarning warning : warnings) {
            if (BLOCKING_WARNING_CODES.contains(warning.getCode())) return AgentFormDraftStatus.EDITABLE;
        }
        return AgentFormDraftStatus.READY;
    }

    /** 将模型告警文本替换为主系统固定安全文案，避免敏感数据进入摘要。 */
    private String safeWarningMessage(String code) {
        if ("CUSTOMER_AMBIGUOUS".equals(code)) return "客户匹配存在歧义，请先明确选择客户";
        if ("PACKAGE_AMBIGUOUS".equals(code)) return "父套餐匹配存在歧义，请先明确选择套餐";
        if ("SUB_PACKAGE_AMBIGUOUS".equals(code)) return "子套餐匹配存在歧义，请先明确选择规格";
        if ("REPLACE_DISH_AMBIGUOUS".equals(code)) return "换菜菜品匹配存在歧义，请先明确选择菜品";
        if ("TRIAL_ORDER_AMBIGUOUS".equals(code)) return "试餐订单匹配存在歧义，请先明确选择订单";
        if ("CONFLICTING_INPUT".equals(code)) return "输入资料存在冲突，请先确认";
        if ("ASSOCIATION_REVIEW_REQUIRED".equals(code)) return "关联对象需要人工复核";
        return "部分字段需要人工复核";
    }

    /** 将持久化记录转换为不含 payload 的保存结果。 */
    private AgentFormDraftSaveResult toSaveResult(AgentFormDraft draft, String operation) {
        AgentFormDraftSaveResult result = new AgentFormDraftSaveResult();
        result.setSuccess(true);
        result.setOperation(operation);
        result.setDraftId(draft.getDraftId());
        result.setDraftType(draft.getDraftType());
        result.setStatus(draft.getStatus());
        result.setRevision(draft.getRevision());
        result.setExpiresAt(draft.getExpiresAt());
        result.setRecognizedFields(readStrings(draft.getRecognizedFields()));
        result.setMissingFields(readStrings(draft.getMissingFields()));
        result.setWarnings(readWarnings(draft.getWarnings()));
        return result;
    }

    /** 将可领取记录转换为唯一匹配草稿类型的 payload。 */
    private AgentFormDraftClaimResult toClaimResult(AgentFormDraft draft) {
        AgentFormDraftClaimResult result = new AgentFormDraftClaimResult();
        result.setDraftId(draft.getDraftId());
        result.setDraftType(draft.getDraftType());
        result.setSchemaVersion(draft.getSchemaVersion());
        result.setStatus(draft.getStatus());
        result.setRevision(draft.getRevision());
        if (AgentFormDraftType.CREATE_ORDER.name().equals(draft.getDraftType())) {
            result.setOrderPayload(JSON.parseObject(draft.getPayload(), CustomerOrderDraftPayload.class));
        } else {
            result.setCustomerWithOrderPayload(JSON.parseObject(draft.getPayload(), CustomerWithOrderDraftPayload.class));
        }
        result.setRecognizedFields(readStrings(draft.getRecognizedFields()));
        result.setMissingFields(readStrings(draft.getMissingFields()));
        result.setWarnings(readWarnings(draft.getWarnings()));
        result.setSourceSessionId(draft.getSourceSessionId());
        result.setExpiresAt(draft.getExpiresAt());
        return result;
    }

    /** 将记录转换为不含敏感数据的摘要。 */
    private AgentFormDraftSummary toSummary(AgentFormDraft draft) {
        AgentFormDraftSummary result = new AgentFormDraftSummary();
        result.setDraftId(draft.getDraftId());
        result.setDraftType(draft.getDraftType());
        result.setStatus(draft.getStatus());
        result.setRevision(draft.getRevision());
        result.setRecognizedFieldCount(readStrings(draft.getRecognizedFields()).size());
        result.setMissingFields(readStrings(draft.getMissingFields()));
        result.setWarnings(readWarnings(draft.getWarnings()));
        result.setSourceSessionId(draft.getSourceSessionId());
        result.setExpiresAt(draft.getExpiresAt());
        result.setTargetBusinessId(draft.getTargetBusinessId());
        return result;
    }

    /** 按所有者读取草稿，未知与越权统一返回 404。 */
    private AgentFormDraft requireOwned(String draftId, Long ownerUserId) {
        AgentFormDraft draft = draftMapper.selectOwned(cleanId(draftId), ownerUserId);
        if (draft == null) throw notFound();
        return draft;
    }

    /** 到期时使用状态条件更新清空 payload；并发状态变化时重新读取最新记录。 */
    private AgentFormDraft expireIfNecessary(AgentFormDraft draft) {
        if (!isExpired(draft) || !isActive(draft)) return draft;
        Timestamp updateTime = now();
        int updated = draftMapper.expireIfActive(draft.getId(), draft.getStatus(), updateTime, updateTime);
        if (updated == 1) {
            draft.setStatus(AgentFormDraftStatus.EXPIRED.name());
            draft.setPayload(null);
            draft.setUpdateTime(updateTime);
            return draft;
        }
        return requireOwned(draft.getDraftId(), draft.getOwnerUserId());
    }

    /** 判断状态是否仍参与自然过期。 */
    private boolean isActive(AgentFormDraft draft) {
        return AgentFormDraftStatus.EDITABLE.name().equals(draft.getStatus())
            || AgentFormDraftStatus.READY.name().equals(draft.getStatus())
            || AgentFormDraftStatus.CLAIMED.name().equals(draft.getStatus());
    }

    /** 判断当前记录是否超过服务端有效期。 */
    private boolean isExpired(AgentFormDraft draft) {
        return draft.getExpiresAt() != null && !draft.getExpiresAt().after(now());
    }

    /** 判断状态是否允许对话修订。 */
    private boolean isRevisable(AgentFormDraft draft) {
        return AgentFormDraftStatus.EDITABLE.name().equals(draft.getStatus())
            || AgentFormDraftStatus.READY.name().equals(draft.getStatus());
    }

    /** 按类型反序列化已保存 payload。 */
    private Object parseStoredPayload(AgentFormDraft draft) {
        if (StringUtils.isBlank(draft.getPayload())) throw conflict("DRAFT_PAYLOAD_UNAVAILABLE");
        if (AgentFormDraftType.CREATE_ORDER.name().equals(draft.getDraftType())) {
            return JSON.parseObject(draft.getPayload(), CustomerOrderDraftPayload.class);
        }
        return JSON.parseObject(draft.getPayload(), CustomerWithOrderDraftPayload.class);
    }

    /** 解析固定草稿类型。 */
    private AgentFormDraftType parseType(String value) {
        try {
            return AgentFormDraftType.valueOf(StringUtils.upperCase(trim(value), Locale.ROOT));
        } catch (Exception ex) {
            throw badRequest("DRAFT_TYPE_INVALID");
        }
    }

    /** 解析持久化字符串数组。 */
    private List<String> readStrings(String json) {
        if (StringUtils.isBlank(json)) return new ArrayList<>();
        List<String> values = JSON.parseArray(json, String.class);
        return values == null ? new ArrayList<>() : values;
    }

    /** 解析持久化稳定告警。 */
    private List<AgentFormDraftWarning> readWarnings(String json) {
        if (StringUtils.isBlank(json)) return new ArrayList<>();
        List<AgentFormDraftWarning> values = JSON.parseArray(json, AgentFormDraftWarning.class);
        return values == null ? new ArrayList<>() : values;
    }

    /** 返回配置时长后的过期时间，非法配置回退 24 小时。 */
    private Timestamp expiresAt() {
        long hours = expirationHours > 0 && expirationHours <= 168 ? expirationHours : 24;
        return new Timestamp(System.currentTimeMillis() + Duration.ofHours(hours).toMillis());
    }

    /** 创建不可变字段路径集合。 */
    private static Set<String> paths(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
    }

    /** 去除外部标识两端空白并限制长度。 */
    private String cleanId(String value) {
        String result = trim(value);
        if (StringUtils.isBlank(result) || result.length() > 80) throw notFound();
        return result;
    }

    /** 校验会话标识并限制数据库查询长度。 */
    private String cleanSessionId(String value) {
        String result = trim(value);
        if (StringUtils.isBlank(result) || result.length() > 80) throw notFound();
        return result;
    }

    /** 去除可选文本两端空白。 */
    private String trim(String value) {
        return StringUtils.trimToNull(value);
    }

    /** 返回当前数据库时间。 */
    private Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    /** 构造不暴露对象是否存在的 404。 */
    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "FORM_DRAFT_NOT_FOUND");
    }

    /** 构造稳定的请求参数错误。 */
    private ResponseStatusException badRequest(String code) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
    }

    /** 构造稳定的草稿状态冲突。 */
    private ResponseStatusException conflict(String code) {
        return new ResponseStatusException(HttpStatus.CONFLICT, code);
    }
}
