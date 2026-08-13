package me.zhengjie.modules.agent.formdraft.service.impl;

import com.alibaba.fastjson2.JSONObject;
import me.zhengjie.modules.agent.formdraft.domain.AgentFormDraft;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftSaveRequest;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftSaveResult;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftWarning;
import me.zhengjie.modules.agent.formdraft.mapper.AgentFormDraftMapper;
import me.zhengjie.modules.agent.security.AgentAccessContext;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import me.zhengjie.modules.agent.security.AgentQueryPermissionService;
import me.zhengjie.modules.agent.session.domain.AgentChatMessage;
import me.zhengjie.modules.agent.session.domain.AgentChatSession;
import me.zhengjie.modules.agent.session.mapper.AgentChatMessageMapper;
import me.zhengjie.modules.agent.session.mapper.AgentChatSessionMapper;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageSubMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.meal.mapper.DishMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Agent 表单草稿生命周期核心单元测试。 */
class AgentFormDraftServiceImplTest {
    private AgentFormDraftMapper draftMapper;
    private AgentChatSessionMapper sessionMapper;
    private AgentChatMessageMapper messageMapper;
    private AgentQueryPermissionService permissionService;
    private CustomerProfileMapper customerProfileMapper;
    private CustomerOrderMapper customerOrderMapper;
    private AgentFormDraftServiceImpl service;

    /** 构建隔离的 Mapper 替身，不连接业务数据库。 */
    @BeforeEach
    void setUp() {
        draftMapper = mock(AgentFormDraftMapper.class);
        sessionMapper = mock(AgentChatSessionMapper.class);
        messageMapper = mock(AgentChatMessageMapper.class);
        permissionService = mock(AgentQueryPermissionService.class);
        customerProfileMapper = mock(CustomerProfileMapper.class);
        customerOrderMapper = mock(CustomerOrderMapper.class);
        service = new AgentFormDraftServiceImpl(draftMapper, sessionMapper, messageMapper, permissionService,
            customerProfileMapper, customerOrderMapper, mock(ParentPackageMapper.class),
            mock(SubPackageMapper.class), mock(ParentPackageSubMapper.class), mock(DishMapper.class));
        ReflectionTestUtils.setField(service, "expirationHours", 24L);
        AgentCustomerDataScopeContext.bind(null);
        AgentChatSession session = new AgentChatSession();
        session.setSessionId("session-1");
        session.setOperator("operator");
        when(sessionMapper.selectOne(any())).thenReturn(session);
        AgentChatMessage message = new AgentChatMessage();
        message.setId(9L);
        message.setRole("USER");
        when(messageMapper.selectOne(any())).thenReturn(message);
    }

    /** 清理线程数据范围，避免测试线程复用产生干扰。 */
    @AfterEach
    void tearDown() {
        AgentCustomerDataScopeContext.clear();
    }

    /** 无阻塞歧义时由主系统计算 READY，且摘要结果不返回 payload。 */
    @Test
    void shouldCreateReadyDraftWithoutReturningPayload() {
        AgentFormDraftSaveResult result = service.save(createRequest(), context());

        assertTrue(result.isSuccess());
        assertEquals("READY", result.getStatus());
        assertEquals(1, result.getRevision());
        ArgumentCaptor<AgentFormDraft> captor = ArgumentCaptor.forClass(AgentFormDraft.class);
        verify(draftMapper).insert(captor.capture());
        assertTrue(captor.getValue().getDraftId().startsWith("afd_"));
        assertTrue(captor.getValue().getPayload().contains("张三"));
    }

    /** 关闭草稿写入开关时，内部保存入口必须拒绝，避免绕过工具白名单。 */
    @Test
    void shouldRejectSaveWhenFeatureDisabled() {
        ReflectionTestUtils.setField(service, "formDraftEnabled", false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.save(createRequest(), context()));

        assertEquals("FORM_DRAFT_DISABLED", error.getReason());
        verify(draftMapper, never()).insert(any());
    }

    /** 关键关联歧义必须使草稿保持 EDITABLE，并使用主系统安全告警文本。 */
    @Test
    void shouldKeepDraftEditableForBlockingWarning() {
        AgentFormDraftSaveRequest request = createRequest();
        AgentFormDraftWarning warning = new AgentFormDraftWarning();
        warning.setCode("PACKAGE_AMBIGUOUS");
        warning.setMessage("可能含敏感原文");
        request.setWarnings(Collections.singletonList(warning));

        AgentFormDraftSaveResult result = service.save(request, context());

        assertEquals("EDITABLE", result.getStatus());
        assertEquals("父套餐匹配存在歧义，请先明确选择套餐", result.getWarnings().get(0).getMessage());
    }

    /** 新增订单缺少客户身份时，主系统补齐受控缺失字段供转换动作判断。 */
    @Test
    void shouldAddCustomerIdentityToOrderMissingFields() {
        AgentFormDraftSaveRequest request = new AgentFormDraftSaveRequest();
        request.setDraftType("CREATE_ORDER");
        request.setSchemaVersion("v1");
        request.setSourceSessionId("session-1");
        request.setClientMessageId("message-order-missing-customer");
        request.setPayload(JSONObject.parseObject("{}"));

        AgentFormDraftSaveResult result = service.save(request, context());

        assertEquals("EDITABLE", result.getStatus());
        assertTrue(result.getMissingFields().contains("customerId"));
        assertTrue(result.getMissingFields().contains("customerCode"));
    }

    /** 相同客服、会话和消息重试必须复用原草稿。 */
    @Test
    void shouldReplayIdempotentCreate() {
        AgentFormDraft existing = activeDraft("READY", 1);
        when(draftMapper.selectByCreateIdempotencyKey(7L, "session-1", "message-1")).thenReturn(existing);

        AgentFormDraftSaveResult result = service.save(createRequest(), context());

        assertEquals("IDEMPOTENT_REPLAY", result.getOperation());
        assertEquals("afd_existing", result.getDraftId());
    }

    /** 条件更新未命中时必须返回稳定版本冲突，不能覆盖新版本。 */
    @Test
    void shouldRejectStaleRevision() {
        AgentFormDraft existing = activeDraft("READY", 2);
        when(draftMapper.selectOwned("afd_existing", 7L)).thenReturn(existing);
        when(draftMapper.updateRevision(any(), anyInt())).thenReturn(0);
        AgentFormDraftSaveRequest request = createRequest();
        request.setDraftId("afd_existing");
        request.setExpectedRevision(2);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.save(request, context()));

        assertEquals("DRAFT_VERSION_CONFLICT", error.getReason());
    }

    /** 过期任务必须清空敏感 payload 并转为 EXPIRED。 */
    @Test
    void shouldClearPayloadWhenExpiring() {
        AgentFormDraft draft = activeDraft("CLAIMED", 1);
        draft.setPayload("{\"phone\":\"13800000000\"}");
        when(draftMapper.selectExpiredBatch(any(), anyInt())).thenReturn(Collections.singletonList(draft));
        when(draftMapper.expireIfActive(any(), any(), any(), any())).thenReturn(1);

        assertEquals(1, service.expireBatch(10));
        assertEquals("EXPIRED", draft.getStatus());
        assertNull(draft.getPayload());
    }

    /** 过期扫描命中旧快照但条件更新失败时，不得把并发提交误计为过期。 */
    @Test
    void shouldIgnoreDraftChangedDuringExpiration() {
        AgentFormDraft draft = activeDraft("CLAIMED", 1);
        draft.setPayload("{\"phone\":\"13800000000\"}");
        when(draftMapper.selectExpiredBatch(any(), anyInt())).thenReturn(Collections.singletonList(draft));
        when(draftMapper.expireIfActive(any(), any(), any(), any())).thenReturn(0);

        assertEquals(0, service.expireBatch(10));
        assertEquals("CLAIMED", draft.getStatus());
        assertTrue(draft.getPayload().contains("13800000000"));
    }

    /** 领取必须使用行锁读取，避免并发修订被旧实体覆盖。 */
    @Test
    void shouldLockDraftBeforeClaiming() {
        AgentFormDraft draft = activeDraft("READY", 3);
        draft.setPayload("{\"customer\":{},\"order\":{}}");
        when(draftMapper.selectOwnedForUpdate("afd_existing", 7L)).thenReturn(draft);
        when(draftMapper.updateById(any())).thenReturn(1);

        assertEquals("CLAIMED", service.claim("afd_existing", 7L).getStatus());
        verify(draftMapper).selectOwnedForUpdate("afd_existing", 7L);
        verify(draftMapper, never()).selectOwned("afd_existing", 7L);
    }

    /** 固定转换动作应获得当前草稿完整可信上下文，并允许原子转换草稿类型。 */
    @Test
    void shouldProvideContextAndConvertOrderDraft() {
        AgentFormDraft draft = activeDraft("EDITABLE", 2);
        draft.setDraftType("CREATE_ORDER");
        draft.setTargetPermission("customerOrder:add");
        draft.setSourceSessionId("session-1");
        draft.setPayload("{}");
        when(draftMapper.selectOwned("afd_existing", 7L)).thenReturn(draft);
        when(draftMapper.updateRevision(any(), anyInt())).thenReturn(1);

        Map<String, Object> context = service.conversationContext("afd_existing", "session-1", 7L);
        assertEquals("CREATE_ORDER", context.get("draftType"));
        assertEquals(2, context.get("revision"));

        AgentFormDraftSaveRequest request = createRequest();
        request.setDraftId("afd_existing");
        request.setExpectedRevision(2);
        request.setConvertedFrom("CREATE_ORDER");
        AgentFormDraftSaveResult result = service.save(request, context());

        assertEquals("CREATE_CUSTOMER_WITH_ORDER", draft.getDraftType());
        assertEquals("customerProfile:add", draft.getTargetPermission());
        assertEquals(3, result.getRevision());
    }

    /** 未知草稿与跨客服猜测草稿必须都返回相同 404，避免泄露草稿是否存在。 */
    @Test
    void shouldHideDraftExistenceFromAnotherOwner() {
        when(draftMapper.selectOwned("afd_existing", 8L)).thenReturn(null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.summary("afd_existing", 8L));

        assertEquals(404, error.getStatus().value());
        verify(permissionService, never()).requireCurrent(any());
    }

    /** 已提交草稿不能再次锁定，保证双击和多标签只会有一个业务事务成功。 */
    @Test
    void shouldRejectRepeatedSubmission() {
        AgentFormDraft submitted = activeDraft("SUBMITTED", 1);
        submitted.setPayload(null);
        when(draftMapper.selectOwnedForUpdate("afd_existing", 7L)).thenReturn(submitted);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.lockForSubmission("afd_existing", 7L, 1, "CREATE_CUSTOMER_WITH_ORDER"));

        assertEquals("DRAFT_NOT_SUBMITTABLE", error.getReason());
    }

    /** 提交订单时必须重新校验当前数据范围，权限变化后不可沿用领取结果。 */
    @Test
    void shouldRejectOrderSubmissionAfterCustomerScopeIsRevoked() {
        AgentFormDraft draft = activeDraft("CLAIMED", 2);
        draft.setDraftType("CREATE_ORDER");
        draft.setTargetPermission("customerOrder:add");
        draft.setPayload("{\"customerId\":99,\"customerCode\":\"A0099\"}");
        when(draftMapper.selectOwnedForUpdate("afd_existing", 7L)).thenReturn(draft);
        CustomerProfile profile = new CustomerProfile();
        profile.setId(99L);
        profile.setCustomerCode("A0099");
        when(customerProfileMapper.selectById(99L)).thenReturn(profile);
        AgentCustomerDataScopeContext.bind(Collections.singleton(100L));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.lockForSubmission("afd_existing", 7L, 2, "CREATE_ORDER"));

        assertEquals(404, error.getStatus().value());
    }

    /** 构造合法的新建客户及首单草稿请求。 */
    private AgentFormDraftSaveRequest createRequest() {
        AgentFormDraftSaveRequest request = new AgentFormDraftSaveRequest();
        request.setDraftType("CREATE_CUSTOMER_WITH_ORDER");
        request.setSchemaVersion("v1");
        request.setSourceSessionId("session-1");
        request.setClientMessageId("message-1");
        request.setPayload(JSONObject.parseObject("{\"customer\":{\"customerName\":\"张三\"},\"order\":{}}"));
        request.setRecognizedFields(Arrays.asList("customer.customerName"));
        request.setMissingFields(Arrays.asList("customer.phone"));
        return request;
    }

    /** 构造已验签访问上下文。 */
    private AgentAccessContext context() {
        AgentAccessContext context = new AgentAccessContext();
        context.setOperatorId(7L);
        context.setOperatorName("operator");
        context.setSessionId("session-1");
        context.setPermissions(Arrays.asList("agentDiagnosis:list", "customerProfile:add"));
        return context;
    }

    /** 构造可复用的活动草稿记录。 */
    private AgentFormDraft activeDraft(String status, int revision) {
        AgentFormDraft draft = new AgentFormDraft();
        draft.setId(1L);
        draft.setDraftId("afd_existing");
        draft.setDraftType("CREATE_CUSTOMER_WITH_ORDER");
        draft.setSchemaVersion("v1");
        draft.setStatus(status);
        draft.setRevision(revision);
        draft.setRecognizedFields("[]");
        draft.setMissingFields("[]");
        draft.setWarnings("[]");
        draft.setExpiresAt(new Timestamp(System.currentTimeMillis() + 60000));
        draft.setOwnerUserId(7L);
        return draft;
    }
}
