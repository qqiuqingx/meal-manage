package me.zhengjie.modules.customer.order.service.impl;

import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.domain.SubPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOrderServiceImplTest {

    @Mock
    private CustomerOrderMapper orderMapper;

    @Mock
    private CustomerProfileMapper profileMapper;

    @Mock
    private CustomerProfileService customerProfileService;

    @Mock
    private ParentPackageMapper parentPackageMapper;

    @Mock
    private SubPackageMapper subPackageMapper;

    @InjectMocks
    private CustomerOrderServiceImpl customerOrderService;

    @Test
    void getDetail_shouldFillPackageNamesAndCustomerInfo() {
        CustomerOrder order = new CustomerOrder();
        order.setId(1L);
        order.setCustomerId(10L);
        order.setParentPackageId(100L);
        order.setChildPackageId(200L);
        order.setOrderCode("ORD20260517001");
        order.setStatus(1);
        order.setMealType("LUNCH");
        order.setScheduleMode("DAILY");

        CustomerProfile profile = new CustomerProfile();
        profile.setId(10L);
        profile.setCustomerName("张三");
        profile.setPhone("13800138000");

        ParentPackage parentPackage = new ParentPackage();
        parentPackage.setId(100L);
        parentPackage.setPackageName("月子餐");

        SubPackage subPackage = new SubPackage();
        subPackage.setId(200L);
        subPackage.setSubPackageName("标准套餐");

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(profileMapper.selectById(10L)).thenReturn(profile);
        when(parentPackageMapper.selectById(100L)).thenReturn(parentPackage);
        when(subPackageMapper.selectById(200L)).thenReturn(subPackage);

        CustomerOrderDetailDto detail = customerOrderService.getDetail(1L);

        assertNotNull(detail);
        assertEquals(1L, detail.getId());
        assertEquals(10L, detail.getCustomerId());
        assertEquals("张三", detail.getCustomerName());
        assertEquals("13800138000", detail.getPhone());
        assertEquals("月子餐", detail.getParentPackageName());
        assertEquals("标准套餐", detail.getChildPackageName());
        assertEquals("进行中", detail.getStatusDesc());
        assertEquals("午餐", detail.getMealTypeDesc());
    }
}
