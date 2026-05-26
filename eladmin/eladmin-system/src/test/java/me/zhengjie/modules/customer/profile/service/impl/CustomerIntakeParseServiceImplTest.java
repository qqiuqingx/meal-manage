package me.zhengjie.modules.customer.profile.service.impl;

import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileSaveDto;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerIntakeParseRequest;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerIntakeParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void parseDefaultsShouldUseTodayScheduleModeAndWhiteRice() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("配送日期：默认等通知配送\n餐数：30\n汤数：0");

        CustomerIntakeParseResult result = service.parse(request);
        CustomerProfileSaveDto.OrderInfoDto orderInfo = result.getDraft().getOrderInfo();

        assertEquals("2026-05-25", orderInfo.getStartDate());
        assertEquals("SCHEDULE", orderInfo.getScheduleMode());
        assertNull(orderInfo.getDeliveryDates());
        assertEquals("白米饭", orderInfo.getRiceType());
        assertEquals(Integer.valueOf(0), orderInfo.getSoupCount());
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
    void parseDeliveryTimesShouldNotAppendToSpecialRequirements() {
        CustomerIntakeParseRequest request = new CustomerIntakeParseRequest();
        request.setText("特殊要求：不能要任何冻货\n午餐开始配送时间：11点整\n晚餐开始配送时间：17点整");

        CustomerIntakeParseResult result = service.parse(request);

        assertEquals("不能要任何冻货", result.getDraft().getSpecialRequirements());
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
}
