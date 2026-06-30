package me.zhengjie.modules.customer.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.orderReplaceRule.domain.CustomerOrderReplaceRule;
import me.zhengjie.modules.customer.orderReplaceRule.domain.CustomerOrderReplaceRuleDto;
import me.zhengjie.modules.customer.orderReplaceRule.mapper.CustomerOrderReplaceRuleMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleAdditionDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleAdjustmentRequest;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleAdjustmentResult;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsRowDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileSaveDto;
import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerMealScheduleAdditionMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfilePackageMapper;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealVerificationLogMapper;
import me.zhengjie.modules.meal.service.DishService;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.modules.customer.numberpool.service.NumberPoolService;
import me.zhengjie.utils.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomerProfileServiceImpl 单元测试
 *
 * Wave 2 (TDD): Tests for excludedDates validation via validateExcludedDates method.
 *
 * @author qqx
 * @date 2026-04-14
 */
@ExtendWith(MockitoExtension.class)
class CustomerProfileServiceImplTest {

    @Mock
    private CustomerProfileAddressMapper addressMapper;

    @Mock
    private CustomerOrderMapper customerOrderMapper;

    @Mock
    private CustomerOrderReplaceRuleMapper replaceRuleMapper;

    @Mock
    private CustomerProfileMapper profileMapper;

    @Mock
    private ParentPackageMapper parentPackageMapper;

    @Mock
    private SubPackageMapper subPackageMapper;

    @Mock
    private CustomerProfilePackageMapper profilePackageMapper;

    @Mock
    private MealVerificationLogMapper verificationLogMapper;

    @Mock
    private MealPlanCustomerMapper mealPlanCustomerMapper;

    @Mock
    private CustomerMealScheduleAdditionMapper customerMealScheduleAdditionMapper;

    @Mock
    private MealPlanService mealPlanService;

    @Mock
    private NumberPoolService numberPoolService;

    @Mock
    private DishService dishService;

    @Mock
    private DishMapper dishMapper;

    @InjectMocks
    private CustomerProfileServiceImpl customerProfileService;

    private CustomerProfile profile;
    private CustomerProfileAddress address1;
    private CustomerProfileAddress address2;
    private CustomerProfileAddress address3;

    @BeforeEach
    void setUp() {
        profile = new CustomerProfile();
        profile.setId(1L);
        profile.setCustomerName("张三");
        profile.setPhone("13800138000");

        address1 = new CustomerProfileAddress();
        address1.setId(1L);
        address1.setCustomerId(1L);
        address1.setAddressType("DEFAULT");
        address1.setAddressDetail("北京市朝阳区xxx街道123号");
        address1.setContactName("张三");
        address1.setContactPhone("13800138000");

        address2 = new CustomerProfileAddress();
        address2.setId(2L);
        address2.setCustomerId(1L);
        address2.setAddressType("WORKDAY");
        address2.setAddressDetail("北京市海淀区xxx路456号");
        address2.setContactName("李四");
        address2.setContactPhone("13900139000");

        address3 = new CustomerProfileAddress();
        address3.setId(3L);
        address3.setCustomerId(1L);
        address3.setAddressType("WEEKEND");
        address3.setAddressDetail("北京市西城区xxx胡同789号");
        address3.setContactName("王五");
        address3.setContactPhone("13700137000");
    }

    private void invokeFillDefaultAddress(CustomerProfile profile) throws Exception {
        Method method = CustomerProfileServiceImpl.class.getDeclaredMethod("fillDefaultAddress", CustomerProfile.class);
        method.setAccessible(true);
        method.invoke(customerProfileService, profile);
    }

    private void invokeFillLatestOrderInfo(CustomerProfile profile) throws Exception {
        Method method = CustomerProfileServiceImpl.class.getDeclaredMethod("fillLatestOrderInfo", CustomerProfile.class);
        method.setAccessible(true);
        method.invoke(customerProfileService, profile);
    }

    private String invokeResolveCustomerCode(String manualCode, Long parentPackageId) throws Exception {
        Method method = CustomerProfileServiceImpl.class.getDeclaredMethod("resolveCustomerCode", String.class, Long.class);
        method.setAccessible(true);
        return (String) method.invoke(customerProfileService, manualCode, parentPackageId);
    }

    private CustomerProfileSaveDto.OrderInfoDto invokeNormalizeAndValidate(CustomerProfileSaveDto dto, boolean createMode) throws Exception {
        Method method = CustomerProfileServiceImpl.class.getDeclaredMethod("normalizeAndValidate", CustomerProfileSaveDto.class, boolean.class);
        method.setAccessible(true);
        return (CustomerProfileSaveDto.OrderInfoDto) method.invoke(customerProfileService, dto, createMode);
    }

    @Test
    void testFillDefaultAddress_WithMultipleAddresses() throws Exception {
        List<CustomerProfileAddress> addresses = Arrays.asList(address1, address2, address3);
        when(addressMapper.selectList(any(QueryWrapper.class))).thenReturn(addresses);

        invokeFillDefaultAddress(profile);

        assertNotNull(profile.getDefaultAddress());
        assertEquals("[默认] 北京市朝阳区xxx街道123号, [工作日] 北京市海淀区xxx路456号, [周末] 北京市西城区xxx胡同789号",
                     profile.getDefaultAddress());
    }

    @Test
    void testFillDefaultAddress_WithSingleAddress() throws Exception {
        List<CustomerProfileAddress> singleAddress = Arrays.asList(address1);
        when(addressMapper.selectList(any(QueryWrapper.class))).thenReturn(singleAddress);

        invokeFillDefaultAddress(profile);

        assertNotNull(profile.getDefaultAddress());
        assertEquals("[默认] 北京市朝阳区xxx街道123号", profile.getDefaultAddress());
    }

    @Test
    void testFillDefaultAddress_WithEmptyAddresses() throws Exception {
        List<CustomerProfileAddress> emptyAddresses = Arrays.asList(
            new CustomerProfileAddress(),
            new CustomerProfileAddress()
        );
        when(addressMapper.selectList(any(QueryWrapper.class))).thenReturn(emptyAddresses);

        invokeFillDefaultAddress(profile);

        assertNotNull(profile.getDefaultAddress());
        assertEquals("", profile.getDefaultAddress());
    }

    @Test
    void testFillDefaultAddress_WithSomeEmptyAddresses() throws Exception {
        CustomerProfileAddress emptyAddress = new CustomerProfileAddress();
        emptyAddress.setId(4L);
        emptyAddress.setCustomerId(1L);
        emptyAddress.setAddressType("DEFAULT");
        emptyAddress.setAddressDetail("");

        CustomerProfileAddress emptyAddress2 = new CustomerProfileAddress();
        emptyAddress2.setId(5L);
        emptyAddress2.setCustomerId(1L);
        emptyAddress2.setAddressType("WEEKEND");
        emptyAddress2.setAddressDetail("");

        List<CustomerProfileAddress> mixedAddresses = Arrays.asList(emptyAddress, address2, emptyAddress2);
        when(addressMapper.selectList(any(QueryWrapper.class))).thenReturn(mixedAddresses);

        invokeFillDefaultAddress(profile);

        assertNotNull(profile.getDefaultAddress());
        assertEquals("[工作日] 北京市海淀区xxx路456号", profile.getDefaultAddress());
    }

    @Test
    void testFillDefaultAddress_WithNullAddresses() throws Exception {
        when(addressMapper.selectList(any(QueryWrapper.class))).thenReturn(null);

        invokeFillDefaultAddress(profile);

        assertNotNull(profile.getDefaultAddress());
        assertEquals("", profile.getDefaultAddress());
    }

    @Test
    void shouldSaveMealScheduleAdjustmentsAndDeleteNewExcludedGeneratedMeals() {
        ExcludedDateDto excludedDate = new ExcludedDateDto();
        excludedDate.setDate("2026-05-24");
        excludedDate.setMealTypes(Arrays.asList("LUNCH"));
        CustomerMealScheduleAdjustmentRequest request = new CustomerMealScheduleAdjustmentRequest();
        request.setCustomerId(1L);
        request.setStatsMonth("2026-05");
        request.setExcludedDates(Arrays.asList(excludedDate));
        request.setAdditions(Collections.emptyList());

        when(profileMapper.selectById(1L)).thenReturn(profile);
        when(mealPlanService.deleteUnverifiedCustomerMealForCalendarAdjustment(1L, "2026-05-24", "LUNCH"))
                .thenReturn(1);

        CustomerMealScheduleAdjustmentResult result = customerProfileService.saveMealScheduleAdjustments(request);

        assertEquals(1L, result.getCustomerId());
        assertEquals(1, result.getExcludedMealCount());
        assertEquals(0, result.getAdditionMealCount());
        assertEquals(1, result.getDeletedUnverifiedPlanCount());
        verify(profileMapper).updateById(profile);
        verify(customerMealScheduleAdditionMapper).softDeleteMissingByCustomerIdAndDateRange(
                eq(1L),
                eq(LocalDate.of(2026, 5, 1)),
                eq(LocalDate.of(2026, 5, 31)),
                eq(Collections.emptyList())
        );
    }

    @Test
    void shouldRestoreSoftDeletedManualAdditionInsteadOfInsertingDuplicate() {
        CustomerMealScheduleAdditionDto additionDto = new CustomerMealScheduleAdditionDto();
        additionDto.setOrderId(10L);
        additionDto.setDate("2026-05-25");
        additionDto.setMealType("LUNCH");
        additionDto.setRemark("恢复新增");

        CustomerMealScheduleAdjustmentRequest request = new CustomerMealScheduleAdjustmentRequest();
        request.setCustomerId(1L);
        request.setStatsMonth("2026-05");
        request.setExcludedDates(Collections.emptyList());
        request.setAdditions(Collections.singletonList(additionDto));

        CustomerOrder order = new CustomerOrder();
        order.setId(10L);
        order.setCustomerId(1L);
        order.setStatus(1);
        order.setMealType("LUNCH_DINNER");
        order.setLunchDinnerCount(10);
        order.setStartDate(LocalDate.of(2026, 5, 1));
        order.setEndDate(LocalDate.of(2026, 5, 31));

        CustomerMealScheduleAddition deletedAddition = new CustomerMealScheduleAddition();
        deletedAddition.setId(99L);
        deletedAddition.setCustomerId(1L);
        deletedAddition.setOrderId(10L);
        deletedAddition.setRecordDate(LocalDate.of(2026, 5, 25));
        deletedAddition.setMealType("LUNCH");
        deletedAddition.setDeleted(true);

        when(profileMapper.selectById(1L)).thenReturn(profile);
        when(customerOrderMapper.selectById(10L)).thenReturn(order);
        when(customerMealScheduleAdditionMapper.selectActiveByOrderDateMeal(10L, LocalDate.of(2026, 5, 25), "LUNCH"))
                .thenReturn(null);
        when(customerMealScheduleAdditionMapper.selectAnyByOrderDateMeal(10L, LocalDate.of(2026, 5, 25), "LUNCH"))
                .thenReturn(deletedAddition);

        CustomerMealScheduleAdjustmentResult result = customerProfileService.saveMealScheduleAdjustments(request);

        assertEquals(1, result.getAdditionMealCount());
        assertFalse(deletedAddition.getDeleted());
        assertEquals("恢复新增", deletedAddition.getRemark());
        verify(customerMealScheduleAdditionMapper).updateById(deletedAddition);
        verify(customerMealScheduleAdditionMapper).softDeleteMissingByCustomerIdAndDateRange(
                eq(1L),
                eq(LocalDate.of(2026, 5, 1)),
                eq(LocalDate.of(2026, 5, 31)),
                eq(Collections.singletonList(99L))
        );
    }

    @Test
    void shouldUseOrderCoveringManualAdditionDateWhenDefaultOrderIsOutsideRange() {
        CustomerMealScheduleAdditionDto additionDto = new CustomerMealScheduleAdditionDto();
        additionDto.setOrderId(10L);
        additionDto.setDate("2026-05-25");
        additionDto.setMealType("DINNER");

        CustomerMealScheduleAdjustmentRequest request = new CustomerMealScheduleAdjustmentRequest();
        request.setCustomerId(1L);
        request.setStatsMonth("2026-05");
        request.setExcludedDates(Collections.emptyList());
        request.setAdditions(Collections.singletonList(additionDto));

        CustomerOrder expiredOrder = new CustomerOrder();
        expiredOrder.setId(10L);
        expiredOrder.setCustomerId(1L);
        expiredOrder.setStatus(1);
        expiredOrder.setMealType("LUNCH_DINNER");
        expiredOrder.setLunchDinnerCount(10);
        expiredOrder.setStartDate(LocalDate.of(2026, 4, 1));
        expiredOrder.setEndDate(LocalDate.of(2026, 4, 30));

        CustomerOrder coveringOrder = new CustomerOrder();
        coveringOrder.setId(11L);
        coveringOrder.setCustomerId(1L);
        coveringOrder.setStatus(1);
        coveringOrder.setMealType("LUNCH_DINNER");
        coveringOrder.setLunchDinnerCount(10);
        coveringOrder.setStartDate(LocalDate.of(2026, 5, 1));
        coveringOrder.setEndDate(LocalDate.of(2026, 5, 31));

        when(profileMapper.selectById(1L)).thenReturn(profile);
        when(customerOrderMapper.selectById(10L)).thenReturn(expiredOrder);
        when(customerOrderMapper.findActiveOrdersByCustomerId(1L)).thenReturn(Arrays.asList(expiredOrder, coveringOrder));
        when(customerMealScheduleAdditionMapper.selectActiveByOrderDateMeal(11L, LocalDate.of(2026, 5, 25), "DINNER"))
                .thenReturn(null);
        when(customerMealScheduleAdditionMapper.selectAnyByOrderDateMeal(11L, LocalDate.of(2026, 5, 25), "DINNER"))
                .thenReturn(null);

        CustomerMealScheduleAdjustmentResult result = customerProfileService.saveMealScheduleAdjustments(request);

        assertEquals(1, result.getAdditionMealCount());
        ArgumentCaptor<CustomerMealScheduleAddition> captor = ArgumentCaptor.forClass(CustomerMealScheduleAddition.class);
        verify(customerMealScheduleAdditionMapper).insert(captor.capture());
        assertEquals(11L, captor.getValue().getOrderId());
    }

    @Test
    void shouldAllowManualCustomerCodeWithVariableDigitLengthWhenSequenceInPoolRange() throws Exception {
        ParentPackage parentPackage = new ParentPackage();
        parentPackage.setId(1L);
        parentPackage.setPoolPrefix("F");
        parentPackage.setPoolStart(800);
        parentPackage.setPoolEnd(999);

        when(parentPackageMapper.selectById(1L)).thenReturn(parentPackage);
        when(profileMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        String customerCode = invokeResolveCustomerCode("F0800", 1L);

        assertEquals("F0800", customerCode);
    }

    @Test
    void shouldApplyTrialDefaultsWhenCreatingTrialCustomer() throws Exception {
        ParentPackage parentPackage = new ParentPackage();
        parentPackage.setId(1L);
        parentPackage.setStatus(true);
        parentPackage.setPackageName("午晚试餐套餐");

        when(parentPackageMapper.selectById(1L)).thenReturn(parentPackage);

        CustomerProfileSaveDto dto = new CustomerProfileSaveDto();
        dto.setPhone("13800001234");
        dto.setAddresses(Collections.singletonList(buildAddressDto("DEFAULT", "成都市高新区天府大道1号")));
        CustomerProfileSaveDto.OrderInfoDto orderInfo = new CustomerProfileSaveDto.OrderInfoDto();
        orderInfo.setParentPackageId(1L);
        dto.setOrderInfo(orderInfo);

        CustomerProfileSaveDto.OrderInfoDto validated = invokeNormalizeAndValidate(dto, true);

        assertEquals("试餐客户1234", dto.getCustomerName());
        assertEquals(0, validated.getBreakfastCount());
        assertEquals(1, validated.getLunchDinnerCount());
        assertEquals(1, validated.getTotalCount());
        assertEquals("SCHEDULE", validated.getScheduleMode());
        assertEquals(LocalDate.now().toString(), validated.getStartDate());
        assertEquals("LUNCH", validated.getStartMealType());
        assertEquals("LUNCH_DINNER", validated.getMealType());
        assertEquals(0, validated.getMainDishCount());
        assertEquals(0, validated.getSideDishCount());
        assertEquals(0, validated.getVegCount());
        assertEquals(1, validated.getRiceCount());
        assertEquals("白米饭", validated.getRiceType());
        assertEquals(0, validated.getSoupCount());
        assertEquals(BigDecimal.ZERO, validated.getBreakfastPrice());
        assertEquals(BigDecimal.ZERO, validated.getLunchDinnerPrice());
        assertEquals(BigDecimal.ZERO, validated.getTotalAmount());
        assertEquals(BigDecimal.ZERO, validated.getDepositAmount());
        assertEquals(BigDecimal.ZERO, validated.getFinalAmount());
        assertFalse(validated.getTrialConverted());
    }

    @Test
    void shouldRejectNormalCustomerWithoutNameOrAddress() {
        ParentPackage parentPackage = new ParentPackage();
        parentPackage.setId(1L);
        parentPackage.setStatus(true);
        parentPackage.setPackageName("标准套餐");

        when(parentPackageMapper.selectById(1L)).thenReturn(parentPackage);

        CustomerProfileSaveDto dto = new CustomerProfileSaveDto();
        dto.setPhone("13800001234");
        dto.setAddresses(Collections.emptyList());
        CustomerProfileSaveDto.OrderInfoDto orderInfo = new CustomerProfileSaveDto.OrderInfoDto();
        orderInfo.setParentPackageId(1L);
        dto.setOrderInfo(orderInfo);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            try {
                invokeNormalizeAndValidate(dto, true);
            } catch (InvocationTargetException e) {
                throw (RuntimeException) e.getCause();
            }
        });
        assertEquals("客户姓名不能为空", exception.getMessage());
    }

    @Test
    void shouldCreateTrialCustomerAndFirstOrderWithMinimalPayload() {
        ParentPackage parentPackage = new ParentPackage();
        parentPackage.setId(1L);
        parentPackage.setStatus(true);
        parentPackage.setPackageName("试餐午晚套餐");
        parentPackage.setPoolPrefix("T");
        parentPackage.setPoolStart(100);
        parentPackage.setPoolEnd(199);

        when(parentPackageMapper.selectById(1L)).thenReturn(parentPackage);
        when(numberPoolService.allocate(any())).thenReturn("T100");
        doAnswer(invocation -> {
            CustomerProfile profileArg = invocation.getArgument(0);
            profileArg.setId(88L);
            return 1;
        }).when(profileMapper).insert(any(CustomerProfile.class));

        CustomerProfileSaveDto dto = new CustomerProfileSaveDto();
        dto.setPhone("13800001234");
        dto.setAddresses(Collections.singletonList(buildAddressDto("DEFAULT", "成都市高新区天府大道1号")));
        CustomerProfileSaveDto.OrderInfoDto orderInfo = new CustomerProfileSaveDto.OrderInfoDto();
        orderInfo.setParentPackageId(1L);
        dto.setOrderInfo(orderInfo);

        customerProfileService.create(dto);

        ArgumentCaptor<CustomerProfile> profileCaptor = ArgumentCaptor.forClass(CustomerProfile.class);
        verify(profileMapper).insert(profileCaptor.capture());
        assertEquals("T100", profileCaptor.getValue().getCustomerCode());
        assertEquals("试餐客户1234", profileCaptor.getValue().getCustomerName());
        assertEquals("13800001234", profileCaptor.getValue().getPhone());

        ArgumentCaptor<CustomerOrder> orderCaptor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(customerOrderMapper).insert(orderCaptor.capture());
        CustomerOrder createdOrder = orderCaptor.getValue();
        assertEquals(88L, createdOrder.getCustomerId());
        assertEquals("T100", createdOrder.getCustomerCode());
        assertEquals(1L, createdOrder.getParentPackageId());
        assertEquals(0, createdOrder.getBreakfastCount());
        assertEquals(1, createdOrder.getLunchDinnerCount());
        assertEquals(1, createdOrder.getTotalCount());
        assertEquals("LUNCH_DINNER", createdOrder.getMealType());
        assertEquals("LUNCH", createdOrder.getStartMealType());
        assertEquals("SCHEDULE", createdOrder.getScheduleMode());
        assertEquals(LocalDate.now(), createdOrder.getStartDate());
        assertEquals("白米饭", createdOrder.getRiceType());
        assertEquals(1, createdOrder.getRiceCount());
        assertEquals(BigDecimal.ZERO, createdOrder.getDepositAmount());
        assertEquals(BigDecimal.ZERO, createdOrder.getTotalAmount());
        assertEquals(BigDecimal.ZERO, createdOrder.getFinalAmount());
        verify(addressMapper).insert(any(CustomerProfileAddress.class));
    }

    @Test
    void shouldRejectTrialCustomerWithoutAddress() {
        ParentPackage parentPackage = new ParentPackage();
        parentPackage.setId(1L);
        parentPackage.setStatus(true);
        parentPackage.setPackageName("试餐午晚套餐");

        when(parentPackageMapper.selectById(1L)).thenReturn(parentPackage);

        CustomerProfileSaveDto dto = new CustomerProfileSaveDto();
        dto.setPhone("13800001234");
        dto.setAddresses(Collections.emptyList());
        CustomerProfileSaveDto.OrderInfoDto orderInfo = new CustomerProfileSaveDto.OrderInfoDto();
        orderInfo.setParentPackageId(1L);
        dto.setOrderInfo(orderInfo);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            try {
                invokeNormalizeAndValidate(dto, true);
            } catch (InvocationTargetException e) {
                throw (RuntimeException) e.getCause();
            }
        });
        assertEquals("地址信息不能为空", exception.getMessage());
    }

    private CustomerProfileSaveDto.AddressDto buildAddressDto(String addressType, String addressDetail) {
        CustomerProfileSaveDto.AddressDto addressDto = new CustomerProfileSaveDto.AddressDto();
        addressDto.setAddressType(addressType);
        addressDto.setAddressDetail(addressDetail);
        return addressDto;
    }

    // ========== excludedDates 校验测试 ==========

    private void invokeValidateExcludedDates(List<ExcludedDateDto> excludedDates) throws Exception {
        Method method = CustomerProfileServiceImpl.class.getDeclaredMethod(
            "validateExcludedDates", List.class);
        method.setAccessible(true);
        method.invoke(customerProfileService, excludedDates);
    }

    /**
     * Test: testValidateExcludedDatesFormat
     * Valid date format (yyyy-MM-dd) with valid mealTypes should pass.
     */
    @Test
    void testValidateExcludedDatesFormat() throws Exception {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("2026-04-15");
        dto.setMealTypes(Arrays.asList("BREAKFAST"));

        List<ExcludedDateDto> excludedDates = Arrays.asList(dto);

        // No exception thrown = validation passed
        invokeValidateExcludedDates(excludedDates);
    }

    /**
     * Test: testValidateExcludedDatesMultipleMealTypes
     * Multiple mealTypes in one entry should pass.
     */
    @Test
    void testValidateExcludedDatesMultipleMealTypes() throws Exception {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("2026-04-16");
        dto.setMealTypes(Arrays.asList("LUNCH", "DINNER"));

        List<ExcludedDateDto> excludedDates = Arrays.asList(dto);

        invokeValidateExcludedDates(excludedDates);
    }

    /**
     * Test: testValidateInvalidDateFormat
     * Invalid date format should throw BadRequestException.
     */
    @Test
    void testValidateInvalidDateFormat() throws Exception {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("2026/04/15");  // Wrong format
        dto.setMealTypes(Arrays.asList("BREAKFAST"));

        List<ExcludedDateDto> excludedDates = Arrays.asList(dto);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            try {
                invokeValidateExcludedDates(excludedDates);
            } catch (InvocationTargetException e) {
                throw (RuntimeException) e.getCause();
            }
        });
        assertTrue(exception.getMessage().contains("排除日期格式错误"));
    }

    /**
     * Test: testValidateInvalidDateFormat_SlashDate
     * Another invalid format variation should throw BadRequestException.
     */
    @Test
    void testValidateInvalidDateFormat_SlashDate() throws Exception {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("15/04/2026");
        dto.setMealTypes(Arrays.asList("BREAKFAST"));

        List<ExcludedDateDto> excludedDates = Arrays.asList(dto);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            try {
                invokeValidateExcludedDates(excludedDates);
            } catch (InvocationTargetException e) {
                throw (RuntimeException) e.getCause();
            }
        });
        assertTrue(exception.getMessage().contains("排除日期格式错误"));
    }

    /**
     * Test: testValidateEmptyMealTypes
     * Empty mealTypes list should throw BadRequestException.
     */
    @Test
    void testValidateEmptyMealTypes() throws Exception {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("2026-04-15");
        dto.setMealTypes(Arrays.asList());  // Empty list

        List<ExcludedDateDto> excludedDates = Arrays.asList(dto);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            try {
                invokeValidateExcludedDates(excludedDates);
            } catch (InvocationTargetException e) {
                throw (RuntimeException) e.getCause();
            }
        });
        assertTrue(exception.getMessage().contains("排除日期必须指定至少一个餐次"));
    }

    /**
     * Test: testValidateNullMealTypes
     * Null mealTypes should throw BadRequestException.
     */
    @Test
    void testValidateNullMealTypes() throws Exception {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("2026-04-15");
        dto.setMealTypes(null);

        List<ExcludedDateDto> excludedDates = Arrays.asList(dto);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            try {
                invokeValidateExcludedDates(excludedDates);
            } catch (InvocationTargetException e) {
                throw (RuntimeException) e.getCause();
            }
        });
        assertTrue(exception.getMessage().contains("排除日期必须指定至少一个餐次"));
    }

    /**
     * Test: testValidateInvalidMealType
     * Invalid mealType value should throw BadRequestException.
     */
    @Test
    void testValidateInvalidMealType() throws Exception {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("2026-04-15");
        dto.setMealTypes(Arrays.asList("SNACK"));  // Invalid mealType

        List<ExcludedDateDto> excludedDates = Arrays.asList(dto);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            try {
                invokeValidateExcludedDates(excludedDates);
            } catch (InvocationTargetException e) {
                throw (RuntimeException) e.getCause();
            }
        });
        assertTrue(exception.getMessage().contains("无效的餐次类型"));
    }

    /**
     * Test: testValidateInvalidMealType_MixedValidInvalid
     * Mixed valid and invalid mealTypes should fail on invalid one.
     */
    @Test
    void testValidateInvalidMealType_MixedValidInvalid() throws Exception {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("2026-04-15");
        dto.setMealTypes(Arrays.asList("BREAKFAST", "INVALID"));

        List<ExcludedDateDto> excludedDates = Arrays.asList(dto);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            try {
                invokeValidateExcludedDates(excludedDates);
            } catch (InvocationTargetException e) {
                throw (RuntimeException) e.getCause();
            }
        });
        assertTrue(exception.getMessage().contains("无效的餐次类型"));
    }

    /**
     * Test: testValidateNullExcludedDates
     * null excludedDates should pass (optional field).
     */
    @Test
    void testValidateNullExcludedDates() throws Exception {
        invokeValidateExcludedDates(null);  // Should not throw
    }

    /**
     * Test: testValidateEmptyExcludedDates
     * Empty list excludedDates should pass (optional field).
     */
    @Test
    void testValidateEmptyExcludedDates() throws Exception {
        invokeValidateExcludedDates(Collections.emptyList());  // Should not throw
    }

    /**
     * Test: testValidateMultipleDates
     * Multiple valid date entries should pass.
     */
    @Test
    void testValidateMultipleDates() throws Exception {
        ExcludedDateDto dto1 = new ExcludedDateDto();
        dto1.setDate("2026-04-15");
        dto1.setMealTypes(Arrays.asList("BREAKFAST"));

        ExcludedDateDto dto2 = new ExcludedDateDto();
        dto2.setDate("2026-04-16");
        dto2.setMealTypes(Arrays.asList("LUNCH", "DINNER"));

        ExcludedDateDto dto3 = new ExcludedDateDto();
        dto3.setDate("2026-04-17");
        dto3.setMealTypes(Arrays.asList("BREAKFAST", "LUNCH", "DINNER"));

        List<ExcludedDateDto> excludedDates = Arrays.asList(dto1, dto2, dto3);

        invokeValidateExcludedDates(excludedDates);
    }

    // ========== 客户用餐统计查询测试 ==========

    @Test
    void testQueryMealStats_WithStatsMonthFiltersRemainingOrdersStartedBeforeNextMonth() {
        CustomerProfile customer = new CustomerProfile();
        customer.setId(1L);
        customer.setCustomerCode("A1001");
        customer.setCustomerName("张三");
        customer.setPhone("13800138000");

        CustomerOrder order = new CustomerOrder();
        order.setId(10L);
        order.setCustomerId(1L);
        order.setBreakfastCount(5);
        order.setLunchDinnerCount(0);
        order.setRemainingCount(3);
        order.setStartDate(LocalDate.of(2026, 4, 1));
        order.setStartMealType("BREAKFAST");
        order.setMealType("ALL");
        order.setScheduleMode("DAILY");

        CustomerMealStatsQueryCriteria criteria = new CustomerMealStatsQueryCriteria();
        criteria.setStatsMonth("2026-04");

        when(profileMapper.findAll(any())).thenReturn(Collections.singletonList(customer));
        when(addressMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(address1));
        when(customerOrderMapper.findActiveOrdersByCustomerIds(Collections.singletonList(1L), LocalDate.of(2026, 5, 1)))
            .thenReturn(Collections.singletonList(order));
        when(customerOrderMapper.sumVerifiedCountByOrderIds(Collections.singletonList(10L)))
            .thenReturn(Collections.emptyList());

        PageResult<CustomerMealStatsRowDto> result = customerProfileService.queryMealStats(criteria, 1, 20);

        assertEquals(1L, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals(Integer.valueOf(5), result.getContent().get(0).getRemainingMealCount());
        assertEquals(30, result.getContent().get(0).getScheduleDays().size());
        verify(customerOrderMapper).findActiveOrdersByCustomerIds(Collections.singletonList(1L), LocalDate.of(2026, 5, 1));
    }

    @Test
    void testQueryMealStats_WithMergedCustomerScheduleDaysOnGroupedRows() {
        CustomerProfile customer = new CustomerProfile();
        customer.setId(1L);
        customer.setCustomerCode("A1001");
        customer.setCustomerName("张三");
        customer.setPhone("13800138000");

        ExcludedDateDto excluded = new ExcludedDateDto();
        excluded.setDate("2026-04-02");
        excluded.setMealTypes(Collections.singletonList("DINNER"));
        customer.setExcludedDates(Collections.singletonList(excluded));

        CustomerOrder order = new CustomerOrder();
        order.setId(10L);
        order.setCustomerId(1L);
        order.setBreakfastCount(5);
        order.setLunchDinnerCount(5);
        order.setRemainingCount(10);
        order.setStartDate(LocalDate.of(2026, 4, 1));
        order.setEndDate(LocalDate.of(2026, 4, 2));
        order.setStartMealType("BREAKFAST");
        order.setMealType("ALL");
        order.setScheduleMode("DAILY");

        CustomerMealStatsQueryCriteria criteria = new CustomerMealStatsQueryCriteria();
        criteria.setStatsMonth("2026-04");

        when(profileMapper.findAll(any())).thenReturn(Collections.singletonList(customer));
        when(addressMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(address1));
        when(customerOrderMapper.findActiveOrdersByCustomerIds(Collections.singletonList(1L), LocalDate.of(2026, 5, 1)))
            .thenReturn(Collections.singletonList(order));
        when(customerOrderMapper.sumVerifiedCountByOrderIds(Collections.singletonList(10L)))
            .thenReturn(Collections.emptyList());

        PageResult<CustomerMealStatsRowDto> result = customerProfileService.queryMealStats(criteria, 1, 20);

        assertEquals(2, result.getContent().size());
        CustomerMealStatsRowDto breakfastRow = result.getContent().get(0);
        CustomerMealStatsRowDto lunchDinnerRow = result.getContent().get(1);
        assertEquals(2, breakfastRow.getCustomerScheduleDays().size());
        assertEquals(Arrays.asList("BREAKFAST", "LUNCH", "DINNER"), breakfastRow.getCustomerScheduleDays().get(0).getMealTypes());
        assertEquals(Arrays.asList("BREAKFAST", "LUNCH"), breakfastRow.getCustomerScheduleDays().get(1).getMealTypes());
        assertEquals(breakfastRow.getCustomerScheduleDays(), lunchDinnerRow.getCustomerScheduleDays());
    }

    @Test
    void testQueryMealStats_WithScheduledMealTypesMarkedInCustomerScheduleDays() {
        CustomerProfile customer = new CustomerProfile();
        customer.setId(1L);
        customer.setCustomerCode("A1001");
        customer.setCustomerName("张三");
        customer.setPhone("13800138000");

        CustomerOrder order = new CustomerOrder();
        order.setId(10L);
        order.setCustomerId(1L);
        order.setBreakfastCount(5);
        order.setLunchDinnerCount(5);
        order.setRemainingCount(10);
        order.setStartDate(LocalDate.of(2026, 4, 1));
        order.setEndDate(LocalDate.of(2026, 4, 2));
        order.setStartMealType("BREAKFAST");
        order.setMealType("ALL");
        order.setScheduleMode("DAILY");

        me.zhengjie.modules.customer.profile.domain.dto.CustomerScheduledMealDto scheduled =
                new me.zhengjie.modules.customer.profile.domain.dto.CustomerScheduledMealDto();
        scheduled.setCustomerId(1L);
        scheduled.setRecordDate(LocalDate.of(2026, 4, 1));
        scheduled.setMealType("LUNCH");

        CustomerMealStatsQueryCriteria criteria = new CustomerMealStatsQueryCriteria();
        criteria.setStatsMonth("2026-04");

        when(profileMapper.findAll(any())).thenReturn(Collections.singletonList(customer));
        when(addressMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(address1));
        when(customerOrderMapper.findActiveOrdersByCustomerIds(Collections.singletonList(1L), LocalDate.of(2026, 5, 1)))
            .thenReturn(Collections.singletonList(order));
        when(customerOrderMapper.sumVerifiedCountByOrderIds(Collections.singletonList(10L)))
            .thenReturn(Collections.emptyList());
        when(mealPlanCustomerMapper.selectScheduledMealsByCustomerIdsAndDateRange(
                Collections.singletonList(1L), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
            .thenReturn(Collections.singletonList(scheduled));

        PageResult<CustomerMealStatsRowDto> result = customerProfileService.queryMealStats(criteria, 1, 20);

        CustomerMealStatsRowDto firstRow = result.getContent().get(0);
        assertEquals(Collections.singletonList("LUNCH"), firstRow.getCustomerScheduleDays().get(0).getScheduledMealTypes());
        assertEquals(Collections.emptyList(), firstRow.getCustomerScheduleDays().get(1).getScheduledMealTypes());
    }

    // ========== scheduleMode 填充测试 ==========

    @Test
    void testFillLatestOrderInfo_WithScheduleMode() throws Exception {
        // Setup: 创建订单对象，设置 scheduleMode 为 "DAILY"
        CustomerOrder latestOrder = new CustomerOrder();
        latestOrder.setId(1L);
        latestOrder.setCustomerId(1L);
        latestOrder.setScheduleMode("DAILY");
        latestOrder.setBreakfastCount(10);
        latestOrder.setLunchDinnerCount(20);

        List<CustomerOrder> activeOrders = Arrays.asList(latestOrder);

        // Mock: 设置 mapper 返回值
        when(customerOrderMapper.findActiveOrdersByCustomerId(1L)).thenReturn(activeOrders);
        when(customerOrderMapper.findLatestByCustomerId(1L)).thenReturn(latestOrder);
        when(customerOrderMapper.sumVerifiedCountByOrderId(1L)).thenReturn(Arrays.asList());

        // Execute: 调用 fillLatestOrderInfo()
        invokeFillLatestOrderInfo(profile);

        // Verify: 验证 scheduleMode 填充为中文标签
        assertNotNull(profile.getScheduleMode());
        assertEquals("每天", profile.getScheduleMode());
    }

    @Test
    void testFillLatestOrderInfo_NoOrder() throws Exception {
        // Mock: 设置 mapper 返回空列表（无订单）
        when(customerOrderMapper.findActiveOrdersByCustomerId(1L)).thenReturn(Collections.emptyList());

        // Execute: 调用 fillLatestOrderInfo()
        invokeFillLatestOrderInfo(profile);

        // Verify: 验证 scheduleMode 显示 "-"
        assertNotNull(profile.getScheduleMode());
        assertEquals("-", profile.getScheduleMode());
    }

    @Test
    void testFillLatestOrderInfo_MultipleOrders() throws Exception {
        // Setup: 创建两个订单，order2 为最新订单（deal_time DESC 排序）
        CustomerOrder order1 = new CustomerOrder();
        order1.setId(1L);
        order1.setCustomerId(1L);
        order1.setScheduleMode("WEEKEND");
        order1.setBreakfastCount(5);
        order1.setLunchDinnerCount(10);

        CustomerOrder order2 = new CustomerOrder();
        order2.setId(2L);
        order2.setCustomerId(1L);
        order2.setScheduleMode("WEEKDAY");  // 最新订单的模式
        order2.setBreakfastCount(10);
        order2.setLunchDinnerCount(20);

        List<CustomerOrder> activeOrders = Arrays.asList(order2, order1);  // order2 是最新

        // Mock: findLatestByCustomerId 返回最新订单 order2
        when(customerOrderMapper.findActiveOrdersByCustomerId(1L)).thenReturn(activeOrders);
        when(customerOrderMapper.findLatestByCustomerId(1L)).thenReturn(order2);  // 返回最新
        when(customerOrderMapper.sumVerifiedCountByOrderId(any())).thenReturn(Arrays.asList());

        // Execute: 调用 fillLatestOrderInfo()
        invokeFillLatestOrderInfo(profile);

        // Verify: 验证 scheduleMode 取最新订单的模式
        assertNotNull(profile.getScheduleMode());
        assertEquals("工作日", profile.getScheduleMode());  // 取最新订单的模式（WEEKDAY → 工作日）
    }

    @Test
    void testFillLatestOrderInfo_InvalidScheduleMode() throws Exception {
        // Setup: 创建订单，设置 scheduleMode 为非法值 "INVALID"
        CustomerOrder latestOrder = new CustomerOrder();
        latestOrder.setId(1L);
        latestOrder.setCustomerId(1L);
        latestOrder.setScheduleMode("INVALID");  // 非法值
        latestOrder.setBreakfastCount(10);
        latestOrder.setLunchDinnerCount(20);

        List<CustomerOrder> activeOrders = Arrays.asList(latestOrder);

        // Mock: 设置 mapper 返回值
        when(customerOrderMapper.findActiveOrdersByCustomerId(1L)).thenReturn(activeOrders);
        when(customerOrderMapper.findLatestByCustomerId(1L)).thenReturn(latestOrder);
        when(customerOrderMapper.sumVerifiedCountByOrderId(1L)).thenReturn(Arrays.asList());

        // Execute: 调用 fillLatestOrderInfo()
        invokeFillLatestOrderInfo(profile);

        // Verify: 验证非法值显示 "-"
        assertNotNull(profile.getScheduleMode());
        assertEquals("-", profile.getScheduleMode());  // 非法值显示 "-"
    }

    @Test
    void testSaveFirstOrder_PersistsDishCounts() throws Exception {
        Method method = CustomerProfileServiceImpl.class.getDeclaredMethod(
            "saveFirstOrder", CustomerProfile.class, CustomerProfileSaveDto.OrderInfoDto.class);
        method.setAccessible(true);

        CustomerProfileSaveDto.OrderInfoDto orderInfo = new CustomerProfileSaveDto.OrderInfoDto();
        orderInfo.setParentPackageId(10L);
        orderInfo.setBreakfastCount(5);
        orderInfo.setLunchDinnerCount(10);
        orderInfo.setTotalCount(15);
        orderInfo.setBreakfastPrice(new BigDecimal("12"));
        orderInfo.setLunchDinnerPrice(new BigDecimal("28"));
        orderInfo.setTotalAmount(new BigDecimal("340"));
        orderInfo.setDepositAmount(new BigDecimal("50"));
        orderInfo.setFinalAmount(new BigDecimal("290"));
        orderInfo.setScheduleMode("SCHEDULE");
        orderInfo.setStartDate("2026-04-18");
        orderInfo.setEndDate("2026-04-28");
        orderInfo.setMealType("ALL");
        orderInfo.setCustomerSource("ONLINE");
        orderInfo.setDeliveryDates("[\"2026-04-18\"]");
        orderInfo.setMainDishCount(2);
        orderInfo.setSideDishCount(1);
        orderInfo.setVegCount(3);
        orderInfo.setRiceCount(1);
        orderInfo.setSoupCount(2);

        method.invoke(customerProfileService, profile, orderInfo);

        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(customerOrderMapper).insert(captor.capture());
        CustomerOrder order = captor.getValue();
        assertEquals(Integer.valueOf(2), order.getMainDishCount());
        assertEquals(Integer.valueOf(1), order.getSideDishCount());
        assertEquals(Integer.valueOf(3), order.getVegCount());
        assertEquals(Integer.valueOf(1), order.getRiceCount());
        assertEquals(Integer.valueOf(2), order.getSoupCount());
        assertEquals(Long.valueOf(10L), order.getParentPackageId());
    }

    @Test
    void testSaveFirstOrder_PersistsReplaceRules() throws Exception {
        Method method = CustomerProfileServiceImpl.class.getDeclaredMethod(
            "saveFirstOrder", CustomerProfile.class, CustomerProfileSaveDto.OrderInfoDto.class);
        method.setAccessible(true);

        Dish sourceDish = new Dish();
        sourceDish.setId(101);
        sourceDish.setName("原主菜");
        sourceDish.setDishType("MAIN");
        sourceDish.setEnabled(true);

        Dish targetDish = new Dish();
        targetDish.setId(202);
        targetDish.setName("目标主菜");
        targetDish.setDishType("MAIN");
        targetDish.setEnabled(true);

        when(dishMapper.selectById(101)).thenReturn(sourceDish);
        when(dishMapper.selectById(202)).thenReturn(targetDish);
        doAnswer(invocation -> {
            CustomerOrder order = invocation.getArgument(0);
            order.setId(99L);
            return 1;
        }).when(customerOrderMapper).insert(any(CustomerOrder.class));

        CustomerOrderReplaceRuleDto replaceRule = new CustomerOrderReplaceRuleDto();
        replaceRule.setSourceDishId(101L);
        replaceRule.setTargetDishId(202L);
        replaceRule.setRemark("首单换菜");

        CustomerProfileSaveDto.OrderInfoDto orderInfo = new CustomerProfileSaveDto.OrderInfoDto();
        orderInfo.setParentPackageId(10L);
        orderInfo.setBreakfastCount(5);
        orderInfo.setLunchDinnerCount(10);
        orderInfo.setTotalCount(15);
        orderInfo.setBreakfastPrice(new BigDecimal("12"));
        orderInfo.setLunchDinnerPrice(new BigDecimal("28"));
        orderInfo.setTotalAmount(new BigDecimal("340"));
        orderInfo.setDepositAmount(new BigDecimal("50"));
        orderInfo.setFinalAmount(new BigDecimal("290"));
        orderInfo.setScheduleMode("SCHEDULE");
        orderInfo.setStartDate("2026-04-18");
        orderInfo.setMealType("ALL");
        orderInfo.setDeliveryDates("[\"2026-04-18\"]");
        orderInfo.setMainDishCount(2);
        orderInfo.setSideDishCount(1);
        orderInfo.setVegCount(3);
        orderInfo.setRiceCount(1);
        orderInfo.setSoupCount(2);
        orderInfo.setReplaceRules(Collections.singletonList(replaceRule));

        method.invoke(customerProfileService, profile, orderInfo);

        ArgumentCaptor<CustomerOrderReplaceRule> captor = ArgumentCaptor.forClass(CustomerOrderReplaceRule.class);
        verify(replaceRuleMapper).insert(captor.capture());
        CustomerOrderReplaceRule savedRule = captor.getValue();
        assertEquals(Long.valueOf(99L), savedRule.getOrderId());
        assertEquals(Long.valueOf(101L), savedRule.getSourceDishId());
        assertEquals("原主菜", savedRule.getSourceDishName());
        assertEquals(Long.valueOf(202L), savedRule.getTargetDishId());
        assertEquals("目标主菜", savedRule.getTargetDishName());
        assertEquals(Boolean.TRUE, savedRule.getEnabled());
        assertEquals("首单换菜", savedRule.getRemark());
    }

    @Test
    void testNormalizeAndValidate_DefaultsRiceCountToOneForFirstOrder() throws Exception {
        Method method = CustomerProfileServiceImpl.class.getDeclaredMethod(
            "normalizeAndValidate", CustomerProfileSaveDto.class, boolean.class);
        method.setAccessible(true);

        ParentPackage parentPackage = new ParentPackage();
        parentPackage.setId(10L);
        parentPackage.setStatus(true);
        when(parentPackageMapper.selectById(10L)).thenReturn(parentPackage);

        CustomerProfileSaveDto dto = new CustomerProfileSaveDto();
        dto.setCustomerName("张三");
        dto.setPhone("13800138000");

        CustomerProfileSaveDto.AddressDto address = new CustomerProfileSaveDto.AddressDto();
        address.setAddressType("DEFAULT");
        address.setAddressDetail("北京市朝阳区测试地址");
        dto.setAddresses(Collections.singletonList(address));

        CustomerProfileSaveDto.OrderInfoDto orderInfo = new CustomerProfileSaveDto.OrderInfoDto();
        orderInfo.setParentPackageId(10L);
        orderInfo.setBreakfastCount(5);
        orderInfo.setStartDate("2026-04-18");
        orderInfo.setMainDishCount(1);
        orderInfo.setSideDishCount(1);
        orderInfo.setVegCount(1);
        orderInfo.setSoupCount(1);
        dto.setOrderInfo(orderInfo);

        CustomerProfileSaveDto.OrderInfoDto normalizedOrderInfo =
            (CustomerProfileSaveDto.OrderInfoDto) method.invoke(customerProfileService, dto, true);

        assertNotNull(normalizedOrderInfo);
        assertEquals(Integer.valueOf(1), normalizedOrderInfo.getRiceCount());
    }

    @Test
    void testNormalizeAndValidate_AllowsLargeDishCountsForFirstOrder() throws Exception {
        Method method = CustomerProfileServiceImpl.class.getDeclaredMethod(
            "normalizeAndValidate", CustomerProfileSaveDto.class, boolean.class);
        method.setAccessible(true);

        ParentPackage parentPackage = new ParentPackage();
        parentPackage.setId(10L);
        parentPackage.setStatus(true);
        when(parentPackageMapper.selectById(10L)).thenReturn(parentPackage);

        CustomerProfileSaveDto dto = new CustomerProfileSaveDto();
        dto.setCustomerName("张三");
        dto.setPhone("13800138000");

        CustomerProfileSaveDto.AddressDto address = new CustomerProfileSaveDto.AddressDto();
        address.setAddressType("DEFAULT");
        address.setAddressDetail("北京市朝阳区测试地址");
        dto.setAddresses(Collections.singletonList(address));

        CustomerProfileSaveDto.OrderInfoDto orderInfo = new CustomerProfileSaveDto.OrderInfoDto();
        orderInfo.setParentPackageId(10L);
        orderInfo.setBreakfastCount(5);
        orderInfo.setStartDate("2026-04-18");
        orderInfo.setMainDishCount(12);
        orderInfo.setSideDishCount(15);
        orderInfo.setVegCount(18);
        orderInfo.setRiceCount(1);
        orderInfo.setSoupCount(25);
        dto.setOrderInfo(orderInfo);

        CustomerProfileSaveDto.OrderInfoDto normalizedOrderInfo =
            (CustomerProfileSaveDto.OrderInfoDto) method.invoke(customerProfileService, dto, true);

        assertNotNull(normalizedOrderInfo);
        assertEquals(Integer.valueOf(12), normalizedOrderInfo.getMainDishCount());
        assertEquals(Integer.valueOf(15), normalizedOrderInfo.getSideDishCount());
        assertEquals(Integer.valueOf(18), normalizedOrderInfo.getVegCount());
        assertEquals(Integer.valueOf(1), normalizedOrderInfo.getRiceCount());
        assertEquals(Integer.valueOf(25), normalizedOrderInfo.getSoupCount());
    }
}
