package me.zhengjie.modules.customer.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileSaveDto;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerIntakeParseRequest;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerIntakeParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 客户话术解析服务单元测试。
 */
class CustomerIntakeParseServiceImplTest {

    private CustomerIntakeParseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerIntakeParseServiceImpl();
        service.setCurrentDateSupplier(() -> LocalDate.of(2026, 5, 25));
    }

    @Test
    void parseBasicCustomerFieldsShouldFillProfileAndAddress() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("联系人：测试客户甲\n电话：13800138001\n地址：成都市高新区示例路88号1栋1单元\n来源：小红书");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto draft = result.getDraft();

        assertEquals("测试客户甲", draft.getCustomerName());
        assertEquals("13800138001", draft.getPhone());
        assertEquals(1, draft.getAddresses().size());
        assertEquals("DEFAULT", draft.getAddresses().get(0).getAddressType());
        assertEquals("成都市高新区示例路88号1栋1单元", draft.getAddresses().get(0).getAddressDetail());
        assertEquals("小红书", draft.getOrderInfo().getCustomerSource());
    }

    @Test
    void parseUnknownCustomerSourceShouldFallbackToOther() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("来源：微信");

        CustomerIntakeParseResult result = service.parse(request);

        assertEquals("其他", result.getDraft().getOrderInfo().getCustomerSource());
    }

    @Test
    void parseCustomerSourceShouldIgnoreOptionTextAndUseActualSelection() {
        assertCustomerSource("来源：（小红书or抖音or推荐）抖音个人搜索", "抖音");
        assertCustomerSource("来源：抖音", "抖音");
        assertCustomerSource("来源：（小红书·抖音·推荐）", "其他");
        assertCustomerSource("来源：（小红书or抖音or推荐）抖音", "抖音");
    }

    @Test
    void parseDefaultsShouldUseTodayScheduleModeAndDefaultGrainRice() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("配送日期：默认等通知配送\n餐数：30\n汤数：0");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto.OrderInfoDto orderInfo = result.getDraft().getOrderInfo();

        assertEquals("2026-05-25", orderInfo.getStartDate());
        assertEquals("SCHEDULE", orderInfo.getScheduleMode());
        assertNull(orderInfo.getDeliveryDates());
        assertEquals("普通杂粮米饭", orderInfo.getRiceType());
        assertEquals(Integer.valueOf(0), orderInfo.getSoupCount());
    }

    private void assertCustomerSource(String text, String expectedSource) {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText(text);

        CustomerIntakeParseResult result = service.parse(request);

        assertEquals(expectedSource, result.getDraft().getOrderInfo().getCustomerSource());
    }

    @Test
    void parseDishQuantityShouldSupportCompactConfig() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("菜品配置：2主1副1素0汤");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto.OrderInfoDto orderInfo = result.getDraft().getOrderInfo();

        assertEquals(Integer.valueOf(2), orderInfo.getMainDishCount());
        assertEquals(Integer.valueOf(1), orderInfo.getSideDishCount());
        assertEquals(Integer.valueOf(1), orderInfo.getVegCount());
        assertEquals(Integer.valueOf(0), orderInfo.getSoupCount());
    }

    @Test
    void parseDishConfigShouldSupportMealCountPrefix() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("菜品配置：7 餐、1主0副1素1汤");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto.OrderInfoDto orderInfo = result.getDraft().getOrderInfo();

        assertEquals(Integer.valueOf(7), orderInfo.getLunchDinnerCount());
        assertEquals(Integer.valueOf(7), orderInfo.getTotalCount());
        assertEquals(Integer.valueOf(1), orderInfo.getMainDishCount());
        assertEquals(Integer.valueOf(0), orderInfo.getSideDishCount());
        assertEquals(Integer.valueOf(1), orderInfo.getVegCount());
        assertEquals(Integer.valueOf(1), orderInfo.getSoupCount());
        assertTrue(result.getIssues().stream().noneMatch(issue -> "orderInfo.lunchDinnerCount".equals(issue.getField())));
    }

    @Test
    void parseDeliveryTimesShouldNotAppendToSpecialRequirements() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("特殊要求：不能要任何冻货\n午餐开始配送时间：11点整\n晚餐开始配送时间：17点整");

        CustomerIntakeParseResult result = service.parse(request);

        assertEquals("不能要任何冻货", result.getDraft().getSpecialRequirements());
    }

    @Test
    void parseBrownRicePreferenceShouldMapToExistingRiceType() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("不能吃的：米换成糙米，不吃面食");

        CustomerIntakeParseResult result = service.parse(request);

        assertEquals("三色糙米", result.getDraft().getOrderInfo().getRiceType());
    }

    @Test
    void parseAllergyTagsShouldSplitCommaAndEnumerationComma() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("过敏食物：牛肉,猪肉，羊肉、葱花");

        CustomerIntakeParseResult result = service.parse(request);

        assertEquals(4, result.getDraft().getAllergyTags().size());
        assertEquals("牛肉", result.getDraft().getAllergyTags().get(0));
        assertEquals("猪肉", result.getDraft().getAllergyTags().get(1));
        assertEquals("羊肉", result.getDraft().getAllergyTags().get(2));
        assertEquals("葱花", result.getDraft().getAllergyTags().get(3));
    }

    @Test
    void parseNoneAllergyAndCannotEatShouldIgnoreEmptyMeaningText() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("过敏食物：无\n不能吃的:无");

        CustomerIntakeParseResult result = service.parse(request);

        assertTrue(result.getDraft().getAllergyTags().isEmpty());
        assertNull(result.getDraft().getSpecialRequirements());
    }

    @Test
    void parseOrderDescriptionShouldInferDailyLunchDinnerAndCount() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("订餐描述：14天每天午餐和晚餐");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto.OrderInfoDto orderInfo = result.getDraft().getOrderInfo();

        assertEquals("DAILY", orderInfo.getScheduleMode());
        assertEquals("LUNCH_DINNER", orderInfo.getMealType());
        assertEquals("LUNCH", orderInfo.getStartMealType());
        assertEquals(Integer.valueOf(28), orderInfo.getLunchDinnerCount());
    }

    @Test
    void parseOrderDescriptionShouldInferWeekdayLunchAndCount() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("订餐描述：11天工作日午餐");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto.OrderInfoDto orderInfo = result.getDraft().getOrderInfo();

        assertEquals("WEEKDAY", orderInfo.getScheduleMode());
        assertEquals("LUNCH", orderInfo.getMealType());
        assertEquals("LUNCH", orderInfo.getStartMealType());
        assertEquals(Integer.valueOf(11), orderInfo.getLunchDinnerCount());
    }

    @Test
    void parseOrderDescriptionShouldSupportShortBreakfastLunchDinnerAlias() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("订餐描述：14天每天早中晚");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto.OrderInfoDto orderInfo = result.getDraft().getOrderInfo();

        assertEquals("DAILY", orderInfo.getScheduleMode());
        assertEquals("ALL", orderInfo.getMealType());
        assertEquals("BREAKFAST", orderInfo.getStartMealType());
        assertEquals(Integer.valueOf(14), orderInfo.getBreakfastCount());
        assertEquals(Integer.valueOf(28), orderInfo.getLunchDinnerCount());
        assertEquals(Integer.valueOf(42), orderInfo.getTotalCount());
    }

    @Test
    void parseOrderDescriptionShouldSupportShortBreakfastNoonDinnerAlias() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("订餐描述：14天每天早午晚");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto.OrderInfoDto orderInfo = result.getDraft().getOrderInfo();

        assertEquals("ALL", orderInfo.getMealType());
        assertEquals("BREAKFAST", orderInfo.getStartMealType());
        assertEquals(Integer.valueOf(14), orderInfo.getBreakfastCount());
        assertEquals(Integer.valueOf(28), orderInfo.getLunchDinnerCount());
    }

    @Test
    void parseOrderDescriptionShouldSupportShortLunchDinnerAlias() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("订餐描述：14天每天中晚");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto.OrderInfoDto orderInfo = result.getDraft().getOrderInfo();

        assertEquals("DAILY", orderInfo.getScheduleMode());
        assertEquals("LUNCH_DINNER", orderInfo.getMealType());
        assertEquals("LUNCH", orderInfo.getStartMealType());
        assertEquals(Integer.valueOf(0), orderInfo.getBreakfastCount());
        assertEquals(Integer.valueOf(28), orderInfo.getLunchDinnerCount());
    }

    @Test
    void parseExplicitStartMealTypeShouldOverrideDefault() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("开始餐次：晚餐");

        CustomerIntakeParseResult result = service.parse(request);

        assertEquals("DINNER", result.getDraft().getOrderInfo().getStartMealType());
    }

    @Test
    void parseExplicitStartMealTypeShouldOverrideOrderDescriptionInference() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("订餐描述：14天每天午餐和晚餐\n开始餐次：晚餐");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto.OrderInfoDto orderInfo = result.getDraft().getOrderInfo();

        assertEquals("LUNCH_DINNER", orderInfo.getMealType());
        assertEquals(Integer.valueOf(28), orderInfo.getLunchDinnerCount());
        assertEquals("DINNER", orderInfo.getStartMealType());
    }

    @Test
    void parseProductionDateShouldSupportMultipleFormats() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("生产日期：5月2日");

        CustomerIntakeParseResult result = service.parse(request);

        assertEquals("2026-05-02", result.getDraft().getProductionDate());
    }

    @Test
    void parseDeliveryDateShouldOverrideOrderDescriptionScheduleMode() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("订餐描述：11天工作日午餐\n配送日期：默认等通知配送");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto.OrderInfoDto orderInfo = result.getDraft().getOrderInfo();

        assertEquals("SCHEDULE", orderInfo.getScheduleMode());
        assertEquals("LUNCH", orderInfo.getMealType());
        assertEquals(Integer.valueOf(11), orderInfo.getLunchDinnerCount());
    }

    @Test
    void parseDeliveryDateWaitingNoticeShouldUseScheduleMode() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("配送日期：等通知");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto.OrderInfoDto orderInfo = result.getDraft().getOrderInfo();

        assertEquals("SCHEDULE", orderInfo.getScheduleMode());
        assertNull(orderInfo.getDeliveryDates());
    }

    @Test
    void parseDeliveryDateShouldSupportOperationalDailyDinnerText() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("配送日期：6 月 1 日、晚餐开始、默认每天配送晚餐");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto.OrderInfoDto orderInfo = result.getDraft().getOrderInfo();

        assertEquals("2026-06-01", orderInfo.getStartDate());
        assertEquals("DAILY", orderInfo.getScheduleMode());
        assertNull(orderInfo.getDeliveryDates());
        assertEquals("DINNER", orderInfo.getMealType());
        assertEquals("DINNER", orderInfo.getStartMealType());
    }

    @Test
    void parseAmbiguousPackageTextShouldAddError() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("餐别：孕期营养餐不含汤（两荤一素）");

        CustomerIntakeParseResult result = service.parse(request);

        assertFalse(result.isValid());
        assertTrue(result.getIssues().stream().anyMatch(issue ->
                "orderInfo.parentPackageId".equals(issue.getField())
                        && issue.getMessage().contains("套餐必须使用系统父套餐名称")));
    }

    @Test
    void parseMealCategoryShouldFallbackToExactParentPackageSegment() {
        ParentPackage yueziPackage = new ParentPackage();
        yueziPackage.setId(6L);
        yueziPackage.setPackageName("月子餐");

        ParentPackage smallYueziPackage = new ParentPackage();
        smallYueziPackage.setId(7L);
        smallYueziPackage.setPackageName("小月子餐 ");
        smallYueziPackage.setPackageCode("XMONTH");

        ParentPackageMapper parentPackageMapper = mock(ParentPackageMapper.class);
        when(parentPackageMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(parentPackageMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(yueziPackage, smallYueziPackage));
        service.setParentPackageMapper(parentPackageMapper);

        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("餐别：小月子餐、两荤一素一汤");

        CustomerIntakeParseResult result = service.parse(request);

        assertEquals(7L, result.getDraft().getOrderInfo().getParentPackageId());
        assertTrue(result.getIssues().stream().noneMatch(issue -> "orderInfo.parentPackageId".equals(issue.getField())));
    }

    @Test
    void parseMealCategoryShouldNotMatchParentPackageInsideAnotherWord() {
        ParentPackage yueziPackage = new ParentPackage();
        yueziPackage.setId(6L);
        yueziPackage.setPackageName("月子餐");

        ParentPackageMapper parentPackageMapper = mock(ParentPackageMapper.class);
        when(parentPackageMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(parentPackageMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(yueziPackage));
        service.setParentPackageMapper(parentPackageMapper);

        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("餐别：小月子餐、两荤一素一汤");

        CustomerIntakeParseResult result = service.parse(request);

        assertNull(result.getDraft().getOrderInfo().getParentPackageId());
        assertTrue(result.getIssues().stream().anyMatch(issue -> "orderInfo.parentPackageId".equals(issue.getField())));
    }
}
