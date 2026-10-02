package me.zhengjie.modules.customer.order.service.impl;

import cn.hutool.jwt.JWT;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.modules.customer.order.domain.CustomerOrderInlineAudit;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderInlineUpdateDto;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderQueryCriteria;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderSaveDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderInlineAuditMapper;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.orderReplaceRule.mapper.CustomerOrderReplaceRuleMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerDietDictionaryMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import me.zhengjie.modules.customer.profile.service.impl.CustomerDietDictionaryServiceImpl;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.SecurityUtils;
import me.zhengjie.utils.SpringBeanHolder;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOrderServiceImplTest {

    @Mock
    private CustomerOrderMapper orderMapper;

    @Mock
    private CustomerOrderInlineAuditMapper inlineAuditMapper;

    @Mock
    private MealPlanCustomerMapper mealPlanCustomerMapper;

    @Mock
    private CustomerProfileMapper profileMapper;

    @Mock
    private CustomerProfileAddressMapper profileAddressMapper;

    @Mock
    private ParentPackageMapper parentPackageMapper;

    @Mock
    private SubPackageMapper subPackageMapper;

    @Mock
    private CustomerProfileService customerProfileService;

    @Spy
    private CustomerDietDictionaryServiceImpl dietDictionaryService =
            new CustomerDietDictionaryServiceImpl(mock(CustomerDietDictionaryMapper.class));

    @Mock
    private CustomerOrderReplaceRuleMapper replaceRuleMapper;

    @Mock
    private DishMapper dishMapper;

    @InjectMocks
    private CustomerOrderServiceImpl orderService;

    @org.junit.jupiter.api.BeforeEach
    void prepareSecurityContext() {
        setTestSecurityContext();
    }

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        clearTestSecurityContext();
    }

    @Test
    void query_setsEstimatedRemainingCountFromCurrentRemainingMinusTodayUnverifiedPlans() {
        CustomerOrder order = new CustomerOrder();
        order.setId(10L);
        order.setBreakfastCount(3);
        order.setLunchDinnerCount(7);
        order.setRemainingCount(6);

        OrderScheduledCountDto totalScheduledCount = new OrderScheduledCountDto();
        totalScheduledCount.setOrderId(10L);
        totalScheduledCount.setScheduledCount(5);

        OrderScheduledCountDto scheduledCount = new OrderScheduledCountDto();
        scheduledCount.setOrderId(10L);
        scheduledCount.setScheduledCount(2);

        when(orderMapper.findAll(any(CustomerOrderQueryCriteria.class), any(Page.class)))
                .thenReturn(Collections.singletonList(order));
        when(mealPlanCustomerMapper.countAllScheduledByOrderIds(eq(Collections.singletonList(10L))))
                .thenReturn(Collections.singletonList(totalScheduledCount));
        when(mealPlanCustomerMapper.countTodayUnverifiedScheduledByOrderIds(eq(Collections.singletonList(10L)), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(scheduledCount));

        PageResult<?> result = orderService.query(new CustomerOrderQueryCriteria(), 1, 10);

        @SuppressWarnings("unchecked")
        List<CustomerOrder> orders = (List<CustomerOrder>) result.getContent();
        assertEquals(1, orders.size());
        assertEquals(10, orders.get(0).getTotalCount());
        assertEquals(5, orders.get(0).getScheduledCount());
        assertEquals(4, orders.get(0).getEstimatedRemainingCount());

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(mealPlanCustomerMapper)
                .countTodayUnverifiedScheduledByOrderIds(eq(Collections.singletonList(10L)), dateCaptor.capture());
        assertNotNull(dateCaptor.getValue());
    }

    @Test
    void query_displaysImportedSourceTotalWithHistoricalVerification() {
        CustomerOrder order = new CustomerOrder();
        order.setId(11L);
        order.setBreakfastCount(0);
        order.setLunchDinnerCount(7);
        order.setImportedVerifiedCount(3);
        order.setVerifiedCount(3);
        order.setRemainingCount(4);
        when(orderMapper.findAll(any(CustomerOrderQueryCriteria.class), any(Page.class)))
                .thenReturn(Collections.singletonList(order));

        setTestSecurityContext();
        try {
            PageResult<?> result = orderService.query(new CustomerOrderQueryCriteria(), 1, 10);

            @SuppressWarnings("unchecked")
            List<CustomerOrder> orders = (List<CustomerOrder>) result.getContent();
            assertEquals(Integer.valueOf(7), orders.get(0).getTotalCount());
            assertEquals(Integer.valueOf(3), orders.get(0).getVerifiedCount());
            assertEquals(Integer.valueOf(4), orders.get(0).getRemainingCount());
        } finally {
            clearTestSecurityContext();
        }
    }

    @Test
    void create_rejectsTrialConversionWhenLinkedOrderParentPackageIsNotTrialPackage() {
        CustomerProfile profile = new CustomerProfile();
        profile.setId(1L);
        profile.setCustomerCode("A1001");

        CustomerOrder linkedOrder = new CustomerOrder();
        linkedOrder.setId(20L);
        linkedOrder.setParentPackageId(30L);

        ParentPackage normalPackage = new ParentPackage();
        normalPackage.setId(30L);
        normalPackage.setPackageName("月子餐套餐");

        CustomerOrderSaveDto dto = new CustomerOrderSaveDto();
        dto.setCustomerId(1L);
        dto.setParentPackageId(40L);
        dto.setTotalAmount(BigDecimal.TEN);
        dto.setFinalAmount(BigDecimal.TEN);
        dto.setBreakfastCount(1);
        dto.setLunchDinnerCount(0);
        dto.setStartDate(LocalDate.of(2026, 5, 27));
        dto.setMealType("ALL");
        dto.setTrialConverted(true);
        dto.setTrialOrderId(20L);
        dto.setMainDishCount(1);
        dto.setSideDishCount(0);
        dto.setVegCount(1);
        dto.setSoupCount(0);

        when(profileMapper.selectById(1L)).thenReturn(profile);
        when(orderMapper.selectById(20L)).thenReturn(linkedOrder);
        when(parentPackageMapper.selectById(30L)).thenReturn(normalPackage);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.create(dto));

        assertEquals("关联订单必须是父套餐名称包含“试餐”的订单", ex.getMessage());
    }

    @Test
    void create_persistsCustomMenuImage() {
        CustomerProfile profile = buildProfile();
        CustomerOrderSaveDto dto = buildValidDto();
        dto.setCustomMenuImage("/file/avatar/menu-001.jpg");

        when(profileMapper.selectById(1L)).thenReturn(profile);

        orderService.create(dto);

        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertEquals("/file/avatar/menu-001.jpg", captor.getValue().getCustomMenuImage());
    }

    @Test
    void update_persistsCustomMenuImage() {
        CustomerOrder existing = new CustomerOrder();
        existing.setId(50L);
        existing.setCustomerId(1L);
        existing.setParentPackageId(40L);
        existing.setStatus(1);
        existing.setVerifiedCount(0);

        CustomerOrderSaveDto dto = buildValidDto();
        dto.setId(50L);
        dto.setCustomMenuImage("/file/avatar/menu-002.jpg");

        when(orderMapper.selectById(50L)).thenReturn(existing);
        when(profileMapper.selectById(1L)).thenReturn(buildProfile());

        orderService.update(dto);

        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderMapper).updateById(captor.capture());
        assertEquals("/file/avatar/menu-002.jpg", captor.getValue().getCustomMenuImage());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4})
    void updateInline_updatesOnlyRequestedMealCountAndAuditsDerivedRemainingCount(int status) {
        CustomerOrder existing = new CustomerOrder();
        existing.setId(70L);
        existing.setCustomerId(7L);
        existing.setStatus(status);
        existing.setBreakfastCount(2);
        existing.setLunchDinnerCount(3);
        existing.setVerifiedCount(2);
        existing.setImportedVerifiedCount(0);
        existing.setRemainingCount(3);
        existing.setUpdateBy("previous");

        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("breakfastCount");
        dto.setValue(4);
        dto.setExpectedValue(2);

        when(orderMapper.selectInlineUpdateByIdForUpdate(70L)).thenReturn(existing);
        when(orderMapper.updateInlineField(eq(70L), eq("breakfastCount"), eq(4),
                org.mockito.ArgumentMatchers.isNull(), eq(5), org.mockito.ArgumentMatchers.isNull(), eq("tester"),
                any(java.time.LocalDateTime.class)))
                .thenReturn(1);
        when(inlineAuditMapper.insert(any(CustomerOrderInlineAudit.class))).thenReturn(1);
        setTestSecurityContext();
        try {
            orderService.updateInline(70L, dto);
        } finally {
            clearTestSecurityContext();
        }

        ArgumentCaptor<CustomerOrderInlineAudit> auditCaptor = ArgumentCaptor.forClass(CustomerOrderInlineAudit.class);
        verify(inlineAuditMapper).insert(auditCaptor.capture());
        CustomerOrderInlineAudit audit = auditCaptor.getValue();
        assertEquals(70L, audit.getOrderId());
        assertEquals(7L, audit.getCustomerId());
        assertEquals("breakfastCount", audit.getFieldKey());
        assertEquals("tester", audit.getOperator());
        JSONObject before = JSON.parseObject(audit.getBeforeState()).getJSONObject("customer_order:70");
        JSONObject after = JSON.parseObject(audit.getAfterState()).getJSONObject("customer_order:70");
        assertEquals(2, before.getInteger("breakfastCount"));
        assertEquals(4, after.getInteger("breakfastCount"));
        assertEquals(3, before.getInteger("remainingCount"));
        assertEquals(5, after.getInteger("remainingCount"));
        assertEquals("previous", before.getString("updateBy"));
        assertEquals("tester", after.getString("updateBy"));
    }

    @Test
    void updateInline_rejectsStaleExpectedValueWithConflictAndDoesNotWriteAudit() {
        CustomerOrder existing = new CustomerOrder();
        existing.setId(71L);
        existing.setCustomerId(7L);
        existing.setStatus(1);
        existing.setMainDishCount(2);

        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("mainDishCount");
        dto.setValue(3);
        dto.setExpectedValue(1);
        when(orderMapper.selectInlineUpdateByIdForUpdate(71L)).thenReturn(existing);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.updateInline(71L, dto));

        assertEquals(409, ex.getStatus());
        verify(orderMapper, never()).updateInlineField(any(Long.class), any(String.class), any(Integer.class),
                any(String.class), any(Integer.class), any(LocalDate.class), any(String.class),
                any(java.time.LocalDateTime.class));
        verify(inlineAuditMapper, never()).insert(any(CustomerOrderInlineAudit.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4})
    void updateInline_rejectsMealCountBelowVerifiedCount(int status) {
        CustomerOrder existing = new CustomerOrder();
        existing.setId(72L);
        existing.setCustomerId(7L);
        existing.setStatus(status);
        existing.setBreakfastCount(5);
        existing.setLunchDinnerCount(2);
        existing.setVerifiedCount(4);
        existing.setRemainingCount(3);

        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("breakfastCount");
        dto.setValue(1);
        dto.setExpectedValue(5);
        when(orderMapper.selectInlineUpdateByIdForUpdate(72L)).thenReturn(existing);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.updateInline(72L, dto));

        assertEquals("订单餐数不能小于已核销餐数（当前已核销：4）", ex.getMessage());
        verify(orderMapper, never()).updateInlineField(any(Long.class), any(String.class), any(Integer.class),
                any(String.class), any(Integer.class), any(LocalDate.class), any(String.class),
                any(java.time.LocalDateTime.class));
        verify(inlineAuditMapper, never()).insert(any(CustomerOrderInlineAudit.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void updateInline_rejectsChangingCompletedOrderStatus(int requestedStatus) {
        CustomerOrder order = inlineOrder(172L, 7L);
        order.setStatus(2);
        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("status");
        dto.setExpectedValue(2);
        dto.setValue(requestedStatus);
        when(orderMapper.selectInlineUpdateByIdForUpdate(172L)).thenReturn(order);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.updateInline(172L, dto));

        assertEquals("已完成订单不能修改状态", ex.getMessage());
        verify(orderMapper, never()).updateInlineField(any(Long.class), any(String.class), any(Integer.class),
                any(String.class), any(Integer.class), any(LocalDate.class), any(String.class),
                any(java.time.LocalDateTime.class));
        verify(inlineAuditMapper, never()).insert(any(CustomerOrderInlineAudit.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void updateInline_rejectsCancelledAndRefundedOrders(int status) {
        CustomerOrder order = inlineOrder(173L, 7L);
        order.setStatus(status);
        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("mainDishCount");
        dto.setExpectedValue(1);
        dto.setValue(2);
        when(orderMapper.selectInlineUpdateByIdForUpdate(173L)).thenReturn(order);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.updateInline(173L, dto));

        assertEquals("只有进行中、暂停或已完成订单可以行内修改", ex.getMessage());
        verify(orderMapper, never()).updateInlineField(any(Long.class), any(String.class), any(Integer.class),
                any(String.class), any(Integer.class), any(LocalDate.class), any(String.class),
                any(java.time.LocalDateTime.class));
        verify(inlineAuditMapper, never()).insert(any(CustomerOrderInlineAudit.class));
    }

    @Test
    void updateInline_rejectsExternalCustomMenuUrls() {
        CustomerOrder existing = new CustomerOrder();
        existing.setId(75L);
        existing.setCustomerId(7L);
        existing.setStatus(1);
        existing.setCustomMenuImage(null);

        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("customMenuImage");
        dto.setValue("https://example.com/menu.jpg");
        dto.setExpectedValue(null);
        when(orderMapper.selectInlineUpdateByIdForUpdate(75L)).thenReturn(existing);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> orderService.updateInline(75L, dto));

        assertEquals(400, exception.getStatus());
        verify(orderMapper, never()).updateInlineField(any(Long.class), any(String.class), any(Integer.class),
                any(String.class), any(Integer.class), any(LocalDate.class), any(String.class),
                any(java.time.LocalDateTime.class));
        verify(inlineAuditMapper, never()).insert(any(CustomerOrderInlineAudit.class));
    }

    @Test
    void updateInline_acceptsTheExistingLocalStorageImagePath() {
        CustomerOrder existing = new CustomerOrder();
        existing.setId(76L);
        existing.setCustomerId(7L);
        existing.setStatus(1);
        existing.setCustomMenuImage(null);

        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("customMenuImage");
        dto.setValue("/file/image/menu-076.jpg");
        dto.setExpectedValue(null);
        when(orderMapper.selectInlineUpdateByIdForUpdate(76L)).thenReturn(existing);
        when(orderMapper.updateInlineField(eq(76L), eq("customMenuImage"),
                org.mockito.ArgumentMatchers.isNull(), eq("/file/image/menu-076.jpg"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), eq("tester"),
                any(java.time.LocalDateTime.class))).thenReturn(1);
        when(inlineAuditMapper.insert(any(CustomerOrderInlineAudit.class))).thenReturn(1);

        orderService.updateInline(76L, dto);

        verify(orderMapper).updateInlineField(eq(76L), eq("customMenuImage"),
                org.mockito.ArgumentMatchers.isNull(), eq("/file/image/menu-076.jpg"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), eq("tester"),
                any(java.time.LocalDateTime.class));
        verify(inlineAuditMapper).insert(any(CustomerOrderInlineAudit.class));
    }

    @Test
    void updateInline_normalizesAllergyTagsAndAuditsTheCustomerProfile() {
        CustomerOrder order = new CustomerOrder();
        order.setId(73L);
        order.setCustomerId(9L);
        order.setStatus(1);

        CustomerProfile profile = new CustomerProfile();
        profile.setId(9L);
        profile.setAllergyTags(Collections.singletonList("牛奶"));
        profile.setUpdateBy("previous");

        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("allergyTags");
        dto.setValue(Arrays.asList(" 花生 ", "花生", "鸡蛋"));
        dto.setExpectedValue(Collections.singletonList("牛奶"));

        when(orderMapper.selectInlineUpdateByIdForUpdate(73L)).thenReturn(order);
        when(profileMapper.selectByIdForInlineUpdate(9L)).thenReturn(profile);
        when(profileMapper.updateAllergyTagsInline(eq(9L),
                eq("[\"花生\",\"鸡蛋\"]"), eq("tester"), any(java.time.LocalDateTime.class))).thenReturn(1);
        when(inlineAuditMapper.insert(any(CustomerOrderInlineAudit.class))).thenReturn(1);

        orderService.updateInline(73L, dto);

        ArgumentCaptor<CustomerOrderInlineAudit> auditCaptor = ArgumentCaptor.forClass(CustomerOrderInlineAudit.class);
        verify(inlineAuditMapper).insert(auditCaptor.capture());
        JSONObject before = JSON.parseObject(auditCaptor.getValue().getBeforeState())
                .getJSONObject("customer_profile:9");
        JSONObject after = JSON.parseObject(auditCaptor.getValue().getAfterState())
                .getJSONObject("customer_profile:9");
        assertEquals(Collections.singletonList("牛奶"), before.getJSONArray("allergyTags").toJavaList(String.class));
        assertEquals(Arrays.asList("花生", "鸡蛋"), after.getJSONArray("allergyTags").toJavaList(String.class));
        assertEquals("previous", before.getString("updateBy"));
        assertEquals("tester", after.getString("updateBy"));
        verify(orderMapper, never()).updateInlineField(any(Long.class), any(String.class), any(Integer.class),
                any(String.class), any(Integer.class), any(LocalDate.class), any(String.class),
                any(java.time.LocalDateTime.class));
    }

    @Test
    void updateInline_normalizesDietReferencesAndKeepsHistoricalSnapshot() {
        CustomerOrder order = inlineOrder(91L, 9L);
        CustomerProfile profile = inlineProfile(9L);
        profile.setDishRequirements(Collections.singletonList(dietItem("INGREDIENT_TAG", 90L, "旧标签名")));
        CustomerOrderInlineUpdateDto dto = inlineDietUpdate("dishRequirements",
                Arrays.asList(dietItem("INGREDIENT_TAG", 90L, "客户端改名"), dietItem("DISH", 2L, "伪造名称")),
                Collections.singletonList(dietItem("INGREDIENT_TAG", 90L, "客户端快照")));
        doReturn(Collections.singletonList(dietOption("DISH", 2L, "服务端菜名")))
                .when(dietDictionaryService).listActiveOptions();
        when(orderMapper.selectInlineUpdateByIdForUpdate(91L)).thenReturn(order);
        when(profileMapper.selectByIdForInlineUpdate(9L)).thenReturn(profile);
        when(profileMapper.updateDishRequirementsInline(eq(9L), any(String.class), eq("tester"),
                any(java.time.LocalDateTime.class))).thenReturn(1);
        when(inlineAuditMapper.insert(any(CustomerOrderInlineAudit.class))).thenReturn(1);

        orderService.updateInline(91L, dto);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(profileMapper).updateDishRequirementsInline(eq(9L), jsonCaptor.capture(), eq("tester"),
                any(java.time.LocalDateTime.class));
        List<CustomerDietItemDto> saved = JSON.parseArray(jsonCaptor.getValue(), CustomerDietItemDto.class);
        assertEquals(Arrays.asList("旧标签名", "服务端菜名"),
                Arrays.asList(saved.get(0).getName(), saved.get(1).getName()));

        ArgumentCaptor<CustomerOrderInlineAudit> auditCaptor = ArgumentCaptor.forClass(CustomerOrderInlineAudit.class);
        verify(inlineAuditMapper).insert(auditCaptor.capture());
        JSONObject before = JSON.parseObject(auditCaptor.getValue().getBeforeState())
                .getJSONObject("customer_profile:9");
        JSONObject after = JSON.parseObject(auditCaptor.getValue().getAfterState())
                .getJSONObject("customer_profile:9");
        assertEquals("旧标签名", before.getJSONArray("dishRequirements").getJSONObject(0).getString("name"));
        assertEquals("服务端菜名", after.getJSONArray("dishRequirements").getJSONObject(1).getString("name"));
    }

    @Test
    void updateInline_rejectsUnsupportedDietType() {
        CustomerOrder order = inlineOrder(92L, 9L);
        CustomerProfile profile = inlineProfile(9L);
        CustomerOrderInlineUpdateDto dto = inlineDietUpdate("dishRequirements",
                Collections.singletonList(dietItem("UNKNOWN", 4L, "未知")), Collections.emptyList());
        when(orderMapper.selectInlineUpdateByIdForUpdate(92L)).thenReturn(order);
        when(profileMapper.selectByIdForInlineUpdate(9L)).thenReturn(profile);

        assertThrows(BadRequestException.class, () -> orderService.updateInline(92L, dto));

        verify(profileMapper, never()).updateDishRequirementsInline(any(Long.class), any(String.class),
                any(String.class), any(java.time.LocalDateTime.class));
        verify(inlineAuditMapper, never()).insert(any(CustomerOrderInlineAudit.class));
    }

    @Test
    void updateInline_clearsDietReferencesWhenValueIsNullOrEmpty() {
        CustomerOrder nullOrder = inlineOrder(93L, 9L);
        CustomerProfile nullProfile = inlineProfile(9L);
        nullProfile.setDietaryRestrictions(Collections.singletonList(dietItem("DISH", 1L, "菜")));
        CustomerOrderInlineUpdateDto nullDto = inlineDietUpdate("dietaryRestrictions", null,
                nullProfile.getDietaryRestrictions());
        when(orderMapper.selectInlineUpdateByIdForUpdate(93L)).thenReturn(nullOrder);
        when(profileMapper.selectByIdForInlineUpdate(9L)).thenReturn(nullProfile);
        when(profileMapper.updateDietaryRestrictionsInline(eq(9L), eq("[]"), eq("[\"DISH:1\"]"), eq("tester"),
                any(java.time.LocalDateTime.class))).thenReturn(1);
        when(inlineAuditMapper.insert(any(CustomerOrderInlineAudit.class))).thenReturn(1);

        orderService.updateInline(93L, nullDto);

        CustomerOrder emptyOrder = inlineOrder(94L, 10L);
        CustomerProfile emptyProfile = inlineProfile(10L);
        emptyProfile.setDishRequirements(Collections.singletonList(dietItem("DISH", 3L, "另一个菜")));
        CustomerOrderInlineUpdateDto emptyDto = inlineDietUpdate("dishRequirements", Collections.emptyList(),
                emptyProfile.getDishRequirements());
        when(orderMapper.selectInlineUpdateByIdForUpdate(94L)).thenReturn(emptyOrder);
        when(profileMapper.selectByIdForInlineUpdate(10L)).thenReturn(emptyProfile);
        when(profileMapper.updateDishRequirementsInline(eq(10L), eq("[]"), eq("tester"),
                any(java.time.LocalDateTime.class))).thenReturn(1);

        orderService.updateInline(94L, emptyDto);

        verify(profileMapper).updateDietaryRestrictionsInline(eq(9L), eq("[]"), eq("[\"DISH:1\"]"), eq("tester"),
                any(java.time.LocalDateTime.class));
        assertEquals(Collections.singletonList("DISH:1"), nullProfile.getDietaryRestrictionExclusions());
        verify(profileMapper).updateDishRequirementsInline(eq(10L), eq("[]"), eq("tester"),
                any(java.time.LocalDateTime.class));
    }

    @Test
    void updateInline_rejectsStaleDietReferencesByTypeAndId() {
        CustomerOrder order = inlineOrder(95L, 9L);
        CustomerProfile profile = inlineProfile(9L);
        profile.setDishRequirements(Collections.singletonList(dietItem("DISH", 1L, "当前菜名")));
        CustomerOrderInlineUpdateDto dto = inlineDietUpdate("dishRequirements", Collections.emptyList(),
                Collections.singletonList(dietItem("DISH", 2L, "预期菜名")));
        when(orderMapper.selectInlineUpdateByIdForUpdate(95L)).thenReturn(order);
        when(profileMapper.selectByIdForInlineUpdate(9L)).thenReturn(profile);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.updateInline(95L, dto));

        assertEquals(409, ex.getStatus());
        verify(dietDictionaryService, never()).listActiveOptions();
        verify(profileMapper, never()).updateDishRequirementsInline(any(Long.class), any(String.class),
                any(String.class), any(java.time.LocalDateTime.class));
    }

    @Test
    void updateInline_ignoresDietNameSnapshotChangesWhenComparingExpectedValue() {
        CustomerOrder order = inlineOrder(96L, 9L);
        CustomerProfile profile = inlineProfile(9L);
        profile.setDishRequirements(Collections.singletonList(dietItem("DISH", 1L, "旧菜名")));
        CustomerOrderInlineUpdateDto dto = inlineDietUpdate("dishRequirements",
                Collections.singletonList(dietItem("DISH", 1L, "新菜名")),
                Collections.singletonList(dietItem("DISH", 1L, "新菜名")));
        doReturn(Collections.singletonList(dietOption("DISH", 1L, "新菜名")))
                .when(dietDictionaryService).listActiveOptions();
        when(orderMapper.selectInlineUpdateByIdForUpdate(96L)).thenReturn(order);
        when(profileMapper.selectByIdForInlineUpdate(9L)).thenReturn(profile);

        orderService.updateInline(96L, dto);

        verify(profileMapper, never()).updateDishRequirementsInline(any(Long.class), any(String.class),
                any(String.class), any(java.time.LocalDateTime.class));
        verify(inlineAuditMapper, never()).insert(any(CustomerOrderInlineAudit.class));
    }

    @Test
    void updateInline_rejectsAddingHistoricalDietReferenceThatIsNotCurrentlySaved() {
        CustomerOrder order = inlineOrder(97L, 9L);
        CustomerProfile profile = inlineProfile(9L);
        CustomerOrderInlineUpdateDto dto = inlineDietUpdate("dietaryRestrictions",
                Collections.singletonList(dietItem("INGREDIENT", 99L, "已删除配料")), Collections.emptyList());
        doReturn(Collections.emptyList()).when(dietDictionaryService).listActiveOptions();
        when(orderMapper.selectInlineUpdateByIdForUpdate(97L)).thenReturn(order);
        when(profileMapper.selectByIdForInlineUpdate(9L)).thenReturn(profile);

        assertThrows(BadRequestException.class, () -> orderService.updateInline(97L, dto));

        verify(profileMapper, never()).updateDietaryRestrictionsInline(any(Long.class), any(String.class),
                any(String.class), any(String.class), any(java.time.LocalDateTime.class));
        verify(inlineAuditMapper, never()).insert(any(CustomerOrderInlineAudit.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4})
    void updateInline_updatesPhoneInCustomerProfileAndAuditsIt(int status) {
        CustomerOrder order = new CustomerOrder();
        order.setId(80L);
        order.setCustomerId(9L);
        order.setStatus(status);
        CustomerProfile profile = new CustomerProfile();
        profile.setId(9L);
        profile.setPhone("13800000000");
        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("phone");
        dto.setExpectedValue("13800000000");
        dto.setValue("13900000000");
        when(orderMapper.selectInlineUpdateByIdForUpdate(80L)).thenReturn(order);
        when(profileMapper.selectByIdForInlineUpdate(9L)).thenReturn(profile);
        when(profileMapper.updatePhoneInline(eq(9L), eq("13900000000"), eq("tester"),
                any(java.time.LocalDateTime.class))).thenReturn(1);
        when(inlineAuditMapper.insert(any(CustomerOrderInlineAudit.class))).thenReturn(1);

        orderService.updateInline(80L, dto);

        ArgumentCaptor<CustomerOrderInlineAudit> auditCaptor = ArgumentCaptor.forClass(CustomerOrderInlineAudit.class);
        verify(inlineAuditMapper).insert(auditCaptor.capture());
        JSONObject before = JSON.parseObject(auditCaptor.getValue().getBeforeState()).getJSONObject("customer_profile:9");
        JSONObject after = JSON.parseObject(auditCaptor.getValue().getAfterState()).getJSONObject("customer_profile:9");
        assertEquals("13800000000", before.getString("phone"));
        assertEquals("13900000000", after.getString("phone"));
    }

    @Test
    void updateInline_rejectsInvalidPhoneWithoutWriting() {
        CustomerOrder order = new CustomerOrder();
        order.setId(81L);
        order.setCustomerId(9L);
        order.setStatus(1);
        CustomerProfile profile = new CustomerProfile();
        profile.setId(9L);
        profile.setPhone("13800000000");
        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("phone");
        dto.setExpectedValue("13800000000");
        dto.setValue("123");
        when(orderMapper.selectInlineUpdateByIdForUpdate(81L)).thenReturn(order);
        when(profileMapper.selectByIdForInlineUpdate(9L)).thenReturn(profile);

        assertThrows(BadRequestException.class, () -> orderService.updateInline(81L, dto));

        verify(profileMapper, never()).updatePhoneInline(any(Long.class), any(String.class),
                any(String.class), any(java.time.LocalDateTime.class));
        verify(inlineAuditMapper, never()).insert(any(CustomerOrderInlineAudit.class));
    }

    @Test
    void updateInline_updatesOnlyTheChosenAddressSlotAndAuditsIt() {
        CustomerOrder order = new CustomerOrder();
        order.setId(82L);
        order.setCustomerId(9L);
        order.setStatus(1);
        CustomerProfile profile = new CustomerProfile();
        profile.setId(9L);
        CustomerProfileAddress address = new CustomerProfileAddress();
        address.setId(31L);
        address.setAddressType("WEEKEND");
        address.setAddressDetail("旧周末地址");
        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("addressDetail:WEEKEND");
        dto.setExpectedValue("旧周末地址");
        dto.setValue("新周末地址");
        when(orderMapper.selectInlineUpdateByIdForUpdate(82L)).thenReturn(order);
        when(profileMapper.selectByIdForInlineUpdate(9L)).thenReturn(profile);
        when(profileAddressMapper.selectForInlineUpdate(9L, "WEEKEND")).thenReturn(address);
        when(profileAddressMapper.updateAddressDetailInline(eq(31L), eq("新周末地址"),
                any(java.time.LocalDateTime.class))).thenReturn(1);
        when(inlineAuditMapper.insert(any(CustomerOrderInlineAudit.class))).thenReturn(1);

        orderService.updateInline(82L, dto);

        verify(profileAddressMapper).selectForInlineUpdate(9L, "WEEKEND");
        ArgumentCaptor<CustomerOrderInlineAudit> auditCaptor = ArgumentCaptor.forClass(CustomerOrderInlineAudit.class);
        verify(inlineAuditMapper).insert(auditCaptor.capture());
        JSONObject before = JSON.parseObject(auditCaptor.getValue().getBeforeState())
                .getJSONObject("customer_profile_address:31");
        JSONObject after = JSON.parseObject(auditCaptor.getValue().getAfterState())
                .getJSONObject("customer_profile_address:31");
        assertEquals("WEEKEND", before.getString("addressType"));
        assertEquals("旧周末地址", before.getString("addressDetail"));
        assertEquals("新周末地址", after.getString("addressDetail"));
    }

    @Test
    void updateInline_rejectsStaleAddressValueWithoutWriting() {
        CustomerOrder order = new CustomerOrder();
        order.setId(83L);
        order.setCustomerId(9L);
        order.setStatus(1);
        CustomerProfile profile = new CustomerProfile();
        profile.setId(9L);
        CustomerProfileAddress address = new CustomerProfileAddress();
        address.setId(31L);
        address.setAddressDetail("最新地址");
        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("addressDetail:DEFAULT");
        dto.setExpectedValue("旧地址");
        dto.setValue("新地址");
        when(orderMapper.selectInlineUpdateByIdForUpdate(83L)).thenReturn(order);
        when(profileMapper.selectByIdForInlineUpdate(9L)).thenReturn(profile);
        when(profileAddressMapper.selectForInlineUpdate(9L, "DEFAULT")).thenReturn(address);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.updateInline(83L, dto));

        assertEquals(409, ex.getStatus());
        verify(profileAddressMapper, never()).updateAddressDetailInline(any(Long.class), any(String.class),
                any(java.time.LocalDateTime.class));
        verify(inlineAuditMapper, never()).insert(any(CustomerOrderInlineAudit.class));
    }

    @Test
    void updateInline_doesNotCreateMissingAddressSlots() {
        CustomerOrder order = new CustomerOrder();
        order.setId(84L);
        order.setCustomerId(9L);
        order.setStatus(1);
        CustomerProfile profile = new CustomerProfile();
        profile.setId(9L);
        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("addressDetail:WEEKEND");
        dto.setExpectedValue(null);
        dto.setValue("新增周末地址");
        when(orderMapper.selectInlineUpdateByIdForUpdate(84L)).thenReturn(order);
        when(profileMapper.selectByIdForInlineUpdate(9L)).thenReturn(profile);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.updateInline(84L, dto));

        assertEquals("该类型地址不存在，请在客户档案中添加", ex.getMessage());
        verify(profileAddressMapper, never()).updateAddressDetailInline(any(Long.class), any(String.class),
                any(java.time.LocalDateTime.class));
        verify(inlineAuditMapper, never()).insert(any(CustomerOrderInlineAudit.class));
    }

    @Test
    void updateInline_syncsCustomerCodeUsingExistingVariableSuffixRuleAndAuditsBothEntities() {
        CustomerOrder order = new CustomerOrder();
        order.setId(74L);
        order.setCustomerId(9L);
        order.setParentPackageId(40L);
        order.setCustomerCode("A11001");
        order.setStatus(1);
        order.setUpdateBy("previous-order-operator");

        CustomerProfile profile = new CustomerProfile();
        profile.setId(9L);
        profile.setCustomerCode("A11001");
        profile.setUpdateBy("previous-profile-operator");

        ParentPackage parent = new ParentPackage();
        parent.setId(40L);
        parent.setPoolPrefix("A1");
        parent.setPoolStart(1001);
        parent.setPoolEnd(1199);

        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField("customerCode");
        dto.setValue("A100001002");
        dto.setExpectedValue("A11001");

        when(orderMapper.selectInlineUpdateByIdForUpdate(74L)).thenReturn(order);
        when(profileMapper.selectByIdForInlineUpdate(9L)).thenReturn(profile);
        when(parentPackageMapper.selectById(40L)).thenReturn(parent);
        when(profileMapper.countByCodeExcludeId("A100001002", 9L)).thenReturn(0);
        when(profileMapper.updateCustomerCodeInline(eq(9L), eq("A100001002"), eq("tester"),
                any(java.time.LocalDateTime.class))).thenReturn(1);
        when(orderMapper.updateCustomerCodeInline(eq(74L), eq("A100001002"), eq("tester"),
                any(java.time.LocalDateTime.class))).thenReturn(1);
        when(inlineAuditMapper.insert(any(CustomerOrderInlineAudit.class))).thenReturn(1);

        orderService.updateInline(74L, dto);

        verify(profileMapper).updateCustomerCodeInline(eq(9L), eq("A100001002"), eq("tester"),
                any(java.time.LocalDateTime.class));
        verify(orderMapper).updateCustomerCodeInline(eq(74L), eq("A100001002"), eq("tester"),
                any(java.time.LocalDateTime.class));
        ArgumentCaptor<CustomerOrderInlineAudit> auditCaptor = ArgumentCaptor.forClass(CustomerOrderInlineAudit.class);
        verify(inlineAuditMapper).insert(auditCaptor.capture());
        JSONObject before = JSON.parseObject(auditCaptor.getValue().getBeforeState());
        JSONObject after = JSON.parseObject(auditCaptor.getValue().getAfterState());
        assertEquals("A11001", before.getJSONObject("customer_order:74").getString("customerCode"));
        assertEquals("A11001", before.getJSONObject("customer_profile:9").getString("customerCode"));
        assertEquals("A100001002", after.getJSONObject("customer_order:74").getString("customerCode"));
        assertEquals("A100001002", after.getJSONObject("customer_profile:9").getString("customerCode"));
    }

    @Test
    void update_keepsImportedHistoricalVerificationWhenRequestOmitsVerifiedCount() {
        CustomerOrder existing = new CustomerOrder();
        existing.setId(51L);
        existing.setCustomerId(1L);
        existing.setParentPackageId(40L);
        existing.setStatus(1);
        existing.setBreakfastCount(0);
        existing.setLunchDinnerCount(7);
        existing.setImportedVerifiedCount(3);
        existing.setVerifiedCount(3);
        existing.setRemainingCount(4);
        CustomerOrderSaveDto dto = buildValidDto();
        dto.setId(51L);
        dto.setBreakfastCount(0);
        dto.setLunchDinnerCount(7);
        dto.setMealType("LUNCH_DINNER");
        dto.setStatus(1);
        when(orderMapper.selectById(51L)).thenReturn(existing);
        when(profileMapper.selectById(1L)).thenReturn(buildProfile());

        setTestSecurityContext();
        try {
            orderService.update(dto);

            assertEquals(Integer.valueOf(3), existing.getImportedVerifiedCount());
            assertEquals(Integer.valueOf(3), existing.getVerifiedCount());
            assertEquals(Integer.valueOf(4), existing.getRemainingCount());
        } finally {
            clearTestSecurityContext();
        }
    }

    @Test
    void update_recordsFirstPauseDateAndKeepsItUntilResume() {
        CustomerOrder existing = new CustomerOrder();
        existing.setId(50L);
        existing.setCustomerId(1L);
        existing.setParentPackageId(40L);
        existing.setStatus(1);

        CustomerOrderSaveDto dto = buildValidDto();
        dto.setId(50L);
        dto.setStatus(4);
        when(orderMapper.selectById(50L)).thenReturn(existing);
        when(profileMapper.selectById(1L)).thenReturn(buildProfile());

        ApplicationContext context = mock(ApplicationContext.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        lenient().when(context.getBean(UserDetailsService.class)).thenReturn(userDetailsService);
        lenient().when(userDetailsService.loadUserByUsername("tester"))
                .thenReturn(new User("tester", "", Collections.emptyList()));
        new SpringBeanHolder().setApplicationContext(context);
        SecurityUtils.header = "Authorization";
        SecurityUtils.tokenStartWith = "Bearer ";
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Authorization", "Bearer " + JWT.create()
                .setKey("test-key".getBytes(StandardCharsets.UTF_8)).setPayload("sub", "tester").sign());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        try {
            orderService.update(dto);
            assertEquals(LocalDate.now(), existing.getPauseEffectiveDate());

            LocalDate firstPauseDate = existing.getPauseEffectiveDate();
            orderService.update(dto);
            assertEquals(firstPauseDate, existing.getPauseEffectiveDate());

            dto.setStatus(1);
            orderService.update(dto);
            assertEquals(null, existing.getPauseEffectiveDate());
        } finally {
            RequestContextHolder.resetRequestAttributes();
            new SpringBeanHolder().destroy();
        }
    }

    @Test
    void getDetail_returnsCustomMenuImageAndTrialOrderCode() {
        CustomerOrder order = new CustomerOrder();
        order.setId(50L);
        order.setCustomerId(1L);
        order.setParentPackageId(40L);
        order.setOrderCode("ORD20260531001");
        order.setBreakfastCount(1);
        order.setLunchDinnerCount(2);
        order.setTrialConverted(true);
        order.setTrialOrderId(60L);
        order.setCustomMenuImage("/file/avatar/menu-003.jpg");

        CustomerOrder trialOrder = new CustomerOrder();
        trialOrder.setId(60L);
        trialOrder.setOrderCode("ORD20260523001");

        when(orderMapper.selectById(50L)).thenReturn(order);
        when(orderMapper.selectById(60L)).thenReturn(trialOrder);
        when(profileMapper.selectById(1L)).thenReturn(buildProfile());
        when(replaceRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertEquals("/file/avatar/menu-003.jpg", orderService.getDetail(50L).getCustomMenuImage());
        assertEquals("ORD20260523001", orderService.getDetail(50L).getTrialOrderCode());
    }

    @Test
    void create_allowsTrialConversionWhenLinkedOrderParentPackageIsTrialPackage() {
        CustomerOrderSaveDto dto = buildValidDto();
        dto.setTrialConverted(true);
        dto.setTrialOrderId(60L);

        CustomerOrder trialOrder = new CustomerOrder();
        trialOrder.setId(60L);
        trialOrder.setParentPackageId(70L);

        ParentPackage trialPackage = new ParentPackage();
        trialPackage.setId(70L);
        trialPackage.setPackageName("轻食试餐套餐");

        when(profileMapper.selectById(1L)).thenReturn(buildProfile());
        when(orderMapper.selectById(60L)).thenReturn(trialOrder);
        when(parentPackageMapper.selectById(70L)).thenReturn(trialPackage);

        orderService.create(dto);

        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().getTrialConverted());
        assertEquals(60L, captor.getValue().getTrialOrderId());
    }

    @Test
    void create_clearsTrialOrderWhenTrialConvertedIsFalse() {
        CustomerOrderSaveDto dto = buildValidDto();
        dto.setTrialConverted(false);
        dto.setTrialOrderId(60L);

        when(profileMapper.selectById(1L)).thenReturn(buildProfile());

        orderService.create(dto);

        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertFalse(captor.getValue().getTrialConverted());
        assertEquals(null, captor.getValue().getTrialOrderId());
        verify(orderMapper, never()).selectById(60L);
    }

    @Test
    void create_rejectsTrialConversionWithoutLinkedOrder() {
        CustomerOrderSaveDto dto = buildValidDto();
        dto.setTrialConverted(true);
        dto.setTrialOrderId(null);

        when(profileMapper.selectById(1L)).thenReturn(buildProfile());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.create(dto));

        assertEquals("请选择关联试餐订单", ex.getMessage());
    }

    @Test
    void update_rejectsTrialConversionLinkedToCurrentOrder() {
        CustomerOrder existing = new CustomerOrder();
        existing.setId(50L);
        existing.setCustomerId(1L);
        existing.setParentPackageId(40L);
        existing.setVerifiedCount(0);

        CustomerOrderSaveDto dto = buildValidDto();
        dto.setId(50L);
        dto.setTrialConverted(true);
        dto.setTrialOrderId(50L);

        when(orderMapper.selectById(50L)).thenReturn(existing);
        when(profileMapper.selectById(1L)).thenReturn(buildProfile());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.update(dto));

        assertEquals("关联试餐订单不能选择当前订单", ex.getMessage());
    }

    @Test
    void create_rejectsMissingLinkedTrialOrder() {
        CustomerOrderSaveDto dto = buildValidDto();
        dto.setTrialConverted(true);
        dto.setTrialOrderId(60L);

        when(profileMapper.selectById(1L)).thenReturn(buildProfile());
        when(orderMapper.selectById(60L)).thenReturn(null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.create(dto));

        assertEquals("关联试餐订单不存在", ex.getMessage());
    }

    private CustomerOrder inlineOrder(Long id, Long customerId) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setCustomerId(customerId);
        order.setStatus(1);
        return order;
    }

    private CustomerProfile inlineProfile(Long id) {
        CustomerProfile profile = new CustomerProfile();
        profile.setId(id);
        profile.setDishRequirements(Collections.emptyList());
        profile.setDietaryRestrictions(Collections.emptyList());
        return profile;
    }

    private CustomerOrderInlineUpdateDto inlineDietUpdate(String field, Object value, Object expectedValue) {
        CustomerOrderInlineUpdateDto dto = new CustomerOrderInlineUpdateDto();
        dto.setField(field);
        dto.setValue(value);
        dto.setExpectedValue(expectedValue);
        return dto;
    }

    private CustomerDietItemDto dietItem(String type, Long id, String name) {
        CustomerDietItemDto item = new CustomerDietItemDto();
        item.setType(type);
        item.setId(id);
        item.setName(name);
        return item;
    }

    private CustomerDietOptionDto dietOption(String type, Long id, String name) {
        CustomerDietOptionDto option = new CustomerDietOptionDto();
        option.setType(type);
        option.setId(id);
        option.setName(name);
        return option;
    }

    private CustomerProfile buildProfile() {
        CustomerProfile profile = new CustomerProfile();
        profile.setId(1L);
        profile.setCustomerCode("A1001");
        profile.setCustomerName("张三");
        return profile;
    }

    private CustomerOrderSaveDto buildValidDto() {
        CustomerOrderSaveDto dto = new CustomerOrderSaveDto();
        dto.setCustomerId(1L);
        dto.setParentPackageId(40L);
        dto.setTotalAmount(BigDecimal.TEN);
        dto.setFinalAmount(BigDecimal.TEN);
        dto.setBreakfastCount(1);
        dto.setLunchDinnerCount(2);
        dto.setStartDate(LocalDate.of(2026, 5, 27));
        dto.setMealType("ALL");
        dto.setTrialConverted(false);
        dto.setMainDishCount(1);
        dto.setSideDishCount(0);
        dto.setVegCount(1);
        dto.setSoupCount(0);
        return dto;
    }

    /**
     * 为真实订单查询与编辑路径准备登录上下文。
     */
    private void setTestSecurityContext() {
        ApplicationContext context = mock(ApplicationContext.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        lenient().when(context.getBean(UserDetailsService.class)).thenReturn(userDetailsService);
        lenient().when(userDetailsService.loadUserByUsername("tester"))
                .thenReturn(new User("tester", "", Collections.emptyList()));
        new SpringBeanHolder().setApplicationContext(context);
        SecurityUtils.header = "Authorization";
        SecurityUtils.tokenStartWith = "Bearer ";
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Authorization", "Bearer " + JWT.create()
                .setKey("test-key".getBytes(StandardCharsets.UTF_8)).setPayload("sub", "tester").sign());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
    }

    /**
     * 清理当前测试建立的登录上下文。
     */
    private void clearTestSecurityContext() {
        RequestContextHolder.resetRequestAttributes();
        new SpringBeanHolder().destroy();
    }
}
