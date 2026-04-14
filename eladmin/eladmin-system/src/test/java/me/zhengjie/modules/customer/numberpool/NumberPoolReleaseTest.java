/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.modules.customer.numberpool;

import me.zhengjie.modules.customer.numberpool.domain.NumberPoolConfig;
import me.zhengjie.modules.customer.numberpool.service.impl.NumberPoolServiceImpl;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderSaveDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编号池释放机制测试
 *
 * These tests prove POOL-09, POOL-10, POOL-11:
 * - POOL-09: Order status != 1 → code is automatically available for reuse
 * - POOL-10: Allocation and order-status-change happen in separate transactions
 * - POOL-11: Allocation query excludes codes with active orders (co.status = 1)
 *
 * Design: Release is PASSIVE — the co.status=1 filter in NumberPoolMapper.findUsedCodesInRange
 * automatically excludes cancelled/completed orders from the used-code set.
 * No explicit release() method exists or is called.
 *
 * @author qqx
 */
@SpringBootTest
public class NumberPoolReleaseTest {

    @MockBean(name = "serverEndpointExporter")
    private ServerEndpointExporter serverEndpointExporter;

    @Autowired
    private NumberPoolServiceImpl numberPoolService;

    @Autowired
    private ParentPackageMapper parentPackageMapper;

    @Autowired
    private CustomerProfileMapper profileMapper;

    @Autowired
    private CustomerOrderMapper orderMapper;

    @Autowired
    private CustomerOrderService orderService;

    private Long testPackageId;
    private static final String POOL_PREFIX = "R1";
    private static final int POOL_START = 2001;
    private static final int POOL_END = 2010;

    @BeforeEach
    public void cleanup() {
        // Clean all test data
        orderMapper.delete(null);
        profileMapper.delete(null);
        parentPackageMapper.delete(null);

        // Insert test package with small pool (10 slots: R12001-R12010)
        ParentPackage pkg = new ParentPackage();
        pkg.setPackageCode("RELEASE-TEST-PKG");
        pkg.setPackageName("RELEASE-TEST-PKG");
        pkg.setPrefix("R");
        pkg.setPoolPrefix(POOL_PREFIX);
        pkg.setPoolStart(POOL_START);
        pkg.setPoolEnd(POOL_END);
        pkg.setStatus(true);
        parentPackageMapper.insert(pkg);
        testPackageId = pkg.getId();
    }

    // -------------------------------------------------------------------------
    // Test: Order cancelled (status 1→0) releases code for reuse
    // Proves: POOL-09 (automatic release on cancellation)
    // -------------------------------------------------------------------------
    @Test
    public void testRelease_onCancel_reusesSmallestCode() {
        // Step 1: Create first profile + allocate code
        CustomerProfile c1 = new CustomerProfile();
        c1.setCustomerName("Cancel-Test-Customer-1");
        c1.setPhone("13800001111");
        c1.setGestationalWeek(20);
        profileMapper.insert(c1);

        // Allocate code to first customer
        NumberPoolConfig config = new NumberPoolConfig();
        config.setPackageId(testPackageId);
        config.setPoolPrefix(POOL_PREFIX);
        config.setPoolStart(POOL_START);
        config.setPoolEnd(POOL_END);
        String code1 = numberPoolService.allocate(config);

        // Set the code on the profile
        c1.setCustomerCode(code1);
        profileMapper.updateById(c1);

        assertNotNull(code1);
        assertTrue(code1.startsWith(POOL_PREFIX), "First code should use pool prefix: " + code1);

        // Verify code1 = R12001 (smallest available)
        assertEquals(POOL_PREFIX + "2001", code1);

        // Step 2: Create an ACTIVE order for customer 1 (status=1 holds the code)
        CustomerOrderSaveDto activeOrder = new CustomerOrderSaveDto();
        activeOrder.setCustomerId(c1.getId());
        activeOrder.setParentPackageId(testPackageId);
        activeOrder.setBreakfastCount(10);
        activeOrder.setLunchDinnerCount(10);
        activeOrder.setTotalAmount(new BigDecimal("1000"));
        activeOrder.setFinalAmount(new BigDecimal("900"));
        activeOrder.setStatus(1); // 进行中
        activeOrder.setMealType("ALL");
        activeOrder.setStartDate(LocalDate.now());
        activeOrder.setEndDate(LocalDate.now().plusDays(30));
        orderService.create(activeOrder);

        // Step 3: Cancel the order (status 1→0) — THIS IS THE RELEASE TRIGGER
        CustomerOrder activeOrderEntity = orderMapper.selectList(null).stream()
            .filter(o -> o.getCustomerId().equals(c1.getId()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Expected value not found"));
        CustomerOrderSaveDto cancelDto = buildDtoFromOrder(activeOrderEntity);
        cancelDto.setId(activeOrderEntity.getId());
        cancelDto.setStatus(0); // 已取消
        orderService.update(cancelDto);

        // Verify status changed
        CustomerOrder updated = orderMapper.selectById(activeOrderEntity.getId());
        assertEquals(Integer.valueOf(0), updated.getStatus(), "Order should be cancelled");

        // Step 4: Create second profile and allocate
        CustomerProfile c2 = new CustomerProfile();
        c2.setCustomerName("Cancel-Test-Customer-2");
        c2.setPhone("13800002222");
        c2.setGestationalWeek(20);
        profileMapper.insert(c2);

        // Step 5: Allocate code to second customer
        String code2 = numberPoolService.allocate(config);

        // CRITICAL ASSERTION: code2 must equal code1 (released code is reused)
        // If this fails (code2 = R12002), the release mechanism is broken
        assertEquals(code1, code2,
            "Released code should be reused by next customer. " +
            "Expected " + code1 + " but got " + code2 + ". " +
            "This means the co.status=1 filter is not working correctly.");
    }

    // -------------------------------------------------------------------------
    // Test: Order completed (status 1→2) releases code for reuse
    // Proves: POOL-09 (automatic release on completion)
    // -------------------------------------------------------------------------
    @Test
    public void testRelease_onComplete_reusesSmallestCode() {
        // Setup: customer + profile + active order
        CustomerProfile c1 = new CustomerProfile();
        c1.setCustomerName("Complete-Test-Customer-1");
        c1.setPhone("13800003333");
        c1.setGestationalWeek(20);
        profileMapper.insert(c1);

        NumberPoolConfig config = new NumberPoolConfig();
        config.setPackageId(testPackageId);
        config.setPoolPrefix(POOL_PREFIX);
        config.setPoolStart(POOL_START);
        config.setPoolEnd(POOL_END);
        String code1 = numberPoolService.allocate(config);
        c1.setCustomerCode(code1);
        profileMapper.updateById(c1);

        assertEquals(POOL_PREFIX + "2001", code1);

        CustomerOrderSaveDto activeOrder = new CustomerOrderSaveDto();
        activeOrder.setCustomerId(c1.getId());
        activeOrder.setParentPackageId(testPackageId);
        activeOrder.setBreakfastCount(10);
        activeOrder.setLunchDinnerCount(10);
        activeOrder.setTotalAmount(new BigDecimal("1000"));
        activeOrder.setFinalAmount(new BigDecimal("1000"));
        activeOrder.setVerifiedCount(20); // all consumed
        activeOrder.setStatus(1);
        activeOrder.setMealType("ALL");
        activeOrder.setStartDate(LocalDate.now());
        activeOrder.setEndDate(LocalDate.now().plusDays(30));
        orderService.create(activeOrder);

        // Complete the order (status 1→2) — THIS IS THE RELEASE TRIGGER
        CustomerOrder activeOrderEntity = orderMapper.selectList(null).stream()
            .filter(o -> o.getCustomerId().equals(c1.getId()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Expected value not found"));
        CustomerOrderSaveDto completeDto = buildDtoFromOrder(activeOrderEntity);
        completeDto.setId(activeOrderEntity.getId());
        completeDto.setStatus(2); // 已完成
        orderService.update(completeDto);

        CustomerOrder updated = orderMapper.selectById(activeOrderEntity.getId());
        assertEquals(Integer.valueOf(2), updated.getStatus(), "Order should be completed");

        // Next customer should get the released code
        CustomerProfile c2 = new CustomerProfile();
        c2.setCustomerName("Complete-Test-Customer-2");
        c2.setPhone("13800004444");
        c2.setGestationalWeek(20);
        profileMapper.insert(c2);

        String code2 = numberPoolService.allocate(config);

        assertEquals(code1, code2,
            "Completed-order code should be reused by next customer. " +
            "Expected " + code1 + " but got " + code2);
    }

    // -------------------------------------------------------------------------
    // Test: Multiple orders cancelled, verify code reuse follows smallest-first
    // Proves: POOL-11 (smallest available is always picked)
    // -------------------------------------------------------------------------
    @Test
    public void testRelease_multipleCancelled_smallestFirstReuse() {
        // Allocate 3 codes for 3 customers with active orders
        String[] codes = new String[3];
        Long[] customerIds = new Long[3];

        NumberPoolConfig config = new NumberPoolConfig();
        config.setPackageId(testPackageId);
        config.setPoolPrefix(POOL_PREFIX);
        config.setPoolStart(POOL_START);
        config.setPoolEnd(POOL_END);

        for (int i = 0; i < 3; i++) {
            CustomerProfile c = new CustomerProfile();
            c.setCustomerName("Multi-Cancel-Customer-" + i);
            c.setPhone("1380000" + String.format("%04d", i));
            c.setGestationalWeek(20);
            profileMapper.insert(c);
            customerIds[i] = c.getId();
            codes[i] = numberPoolService.allocate(config);
            c.setCustomerCode(codes[i]);
            profileMapper.updateById(c);

            CustomerOrderSaveDto order = new CustomerOrderSaveDto();
            order.setCustomerId(c.getId());
            order.setParentPackageId(testPackageId);
            order.setBreakfastCount(10);
            order.setLunchDinnerCount(10);
            order.setTotalAmount(new BigDecimal("1000"));
            order.setFinalAmount(new BigDecimal("900"));
            order.setStatus(1);
            order.setMealType("ALL");
            order.setStartDate(LocalDate.now());
            order.setEndDate(LocalDate.now().plusDays(30));
            orderService.create(order);
        }

        // codes should be R12001, R12002, R12003
        assertEquals(POOL_PREFIX + "2001", codes[0]);
        assertEquals(POOL_PREFIX + "2002", codes[1]);
        assertEquals(POOL_PREFIX + "2003", codes[2]);

        // Cancel customer 2's order only (middle code)
        CustomerOrder customer2Order = orderMapper.selectList(null).stream()
            .filter(o -> o.getCustomerId().equals(customerIds[1]))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Expected value not found"));
        CustomerOrderSaveDto cancelDto = buildDtoFromOrder(customer2Order);
        cancelDto.setId(customer2Order.getId());
        cancelDto.setStatus(0);
        orderService.update(cancelDto);

        // New customer should get R12002 (smallest available), not R12004
        CustomerProfile c4 = new CustomerProfile();
        c4.setCustomerName("Multi-Cancel-Customer-4");
        c4.setPhone("13800005555");
        c4.setGestationalWeek(20);
        profileMapper.insert(c4);

        String code4 = numberPoolService.allocate(config);

        assertEquals(POOL_PREFIX + "2002", code4,
            "Released code (R12002) should be reused before moving to next pool number. " +
            "Expected R12002 but got " + code4);
    }

    @Test
    public void testAllocate_withShortRange_usesSameZeroPaddingAsRangeQuery() {
        ParentPackage shortRangePkg = new ParentPackage();
        shortRangePkg.setPackageCode("SHORT-RANGE-PKG");
        shortRangePkg.setPackageName("SHORT-RANGE-PKG");
        shortRangePkg.setPrefix("A");
        shortRangePkg.setPoolPrefix("A1");
        shortRangePkg.setPoolStart(1);
        shortRangePkg.setPoolEnd(199);
        shortRangePkg.setStatus(true);
        parentPackageMapper.insert(shortRangePkg);

        CustomerProfile c1 = new CustomerProfile();
        c1.setCustomerName("Short-Range-Customer-1");
        c1.setPhone("13800006666");
        c1.setGestationalWeek(20);
        c1.setCustomerCode("A1001");
        profileMapper.insert(c1);

        CustomerOrderSaveDto activeOrder = new CustomerOrderSaveDto();
        activeOrder.setCustomerId(c1.getId());
        activeOrder.setParentPackageId(shortRangePkg.getId());
        activeOrder.setBreakfastCount(10);
        activeOrder.setLunchDinnerCount(10);
        activeOrder.setTotalAmount(new BigDecimal("1000"));
        activeOrder.setFinalAmount(new BigDecimal("900"));
        activeOrder.setStatus(1);
        activeOrder.setMealType("ALL");
        activeOrder.setStartDate(LocalDate.now());
        activeOrder.setEndDate(LocalDate.now().plusDays(30));
        orderService.create(activeOrder);

        NumberPoolConfig config = new NumberPoolConfig();
        config.setPackageId(shortRangePkg.getId());
        config.setPoolPrefix("A1");
        config.setPoolStart(1);
        config.setPoolEnd(199);

        String code = numberPoolService.allocate(config);

        assertEquals("A1002", code,
            "Range query must recognize A1001 as occupied when pool is configured as 1..199.");
    }

    // -------------------------------------------------------------------------
    // Helper: build CustomerOrderSaveDto from existing CustomerOrder
    // -------------------------------------------------------------------------
    private CustomerOrderSaveDto buildDtoFromOrder(CustomerOrder order) {
        CustomerOrderSaveDto dto = new CustomerOrderSaveDto();
        dto.setCustomerId(order.getCustomerId());
        dto.setParentPackageId(order.getParentPackageId());
        dto.setChildPackageId(order.getChildPackageId());
        dto.setBreakfastCount(order.getBreakfastCount());
        dto.setLunchDinnerCount(order.getLunchDinnerCount());
        dto.setBreakfastPrice(order.getBreakfastPrice());
        dto.setLunchDinnerPrice(order.getLunchDinnerPrice());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setFinalAmount(order.getFinalAmount());
        dto.setVerifiedCount(order.getVerifiedCount());
        dto.setVerifiedAmount(order.getVerifiedAmount());
        dto.setMealBalance(order.getMealBalance());
        dto.setRemainingCount(order.getRemainingCount());
        dto.setStartDate(order.getStartDate());
        dto.setEndDate(order.getEndDate());
        dto.setMealType(order.getMealType());
        dto.setScheduleMode(order.getScheduleMode());
        dto.setDeliveryDates(order.getDeliveryDates());
        dto.setRemark(order.getRemark());
        dto.setCustomerSource(order.getCustomerSource());
        dto.setStatus(order.getStatus()); // current status, will be changed by caller
        return dto;
    }
}
