package me.zhengjie.modules.customer.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CustomerProfileServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class CustomerProfileServiceImplTest {

    @Mock
    private CustomerProfileAddressMapper addressMapper;

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

    @Test
    void testFillDefaultAddress_WithMultipleAddresses() throws Exception {
        // 准备测试数据
        List<CustomerProfileAddress> addresses = Arrays.asList(address1, address2, address3);
        when(addressMapper.selectList(any(QueryWrapper.class))).thenReturn(addresses);

        // 执行测试
        invokeFillDefaultAddress(profile);

        // 验证结果
        assertNotNull(profile.getDefaultAddress());
        assertEquals("[默认] 北京市朝阳区xxx街道123号, [工作日] 北京市海淀区xxx路456号, [周末] 北京市西城区xxx胡同789号",
                     profile.getDefaultAddress());
    }

    @Test
    void testFillDefaultAddress_WithSingleAddress() throws Exception {
        // 准备测试数据 - 只有默认地址
        List<CustomerProfileAddress> singleAddress = Arrays.asList(address1);
        when(addressMapper.selectList(any(QueryWrapper.class))).thenReturn(singleAddress);

        // 执行测试
        invokeFillDefaultAddress(profile);

        // 验证结果
        assertNotNull(profile.getDefaultAddress());
        assertEquals("[默认] 北京市朝阳区xxx街道123号", profile.getDefaultAddress());
    }

    @Test
    void testFillDefaultAddress_WithEmptyAddresses() throws Exception {
        // 准备测试数据 - 空地址列表
        List<CustomerProfileAddress> emptyAddresses = Arrays.asList(
            new CustomerProfileAddress(),
            new CustomerProfileAddress()
        );
        when(addressMapper.selectList(any(QueryWrapper.class))).thenReturn(emptyAddresses);

        // 执行测试
        invokeFillDefaultAddress(profile);

        // 验证结果
        assertNotNull(profile.getDefaultAddress());
        assertEquals("", profile.getDefaultAddress());
    }

    @Test
    void testFillDefaultAddress_WithSomeEmptyAddresses() throws Exception {
        // 准备测试数据 - 混合空和非空地址
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

        // 执行测试
        invokeFillDefaultAddress(profile);

        // 验证结果
        assertNotNull(profile.getDefaultAddress());
        assertEquals("[工作日] 北京市海淀区xxx路456号", profile.getDefaultAddress());
    }

    @Test
    void testFillDefaultAddress_WithNullAddresses() throws Exception {
        // 准备测试数据 - null地址列表
        when(addressMapper.selectList(any(QueryWrapper.class))).thenReturn(null);

        // 执行测试
        invokeFillDefaultAddress(profile);

        // 验证结果
        assertNotNull(profile.getDefaultAddress());
        assertEquals("", profile.getDefaultAddress());
    }
}