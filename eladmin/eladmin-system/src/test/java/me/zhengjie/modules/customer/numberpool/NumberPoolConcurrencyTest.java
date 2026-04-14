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
import me.zhengjie.modules.customer.numberpool.service.NumberPoolService;
import me.zhengjie.modules.customer.numberpool.service.impl.NumberPoolServiceImpl;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编号池并发分配测试
 *
 * CRITICAL design decisions (from PITFALLS.md Pitfall 6):
 * - Uses real MySQL via Spring context (not H2 mock)
 * - NO @Transactional on testConcurrentAllocation — each thread needs its own DB connection
 * - Uses ExecutorService + CountDownLatch to fire threads simultaneously
 * - assertEquals(threadCount, uniqueCodes.size()) verifies no duplicates
 *
 * @author qqx
 */
@SpringBootTest
public class NumberPoolConcurrencyTest {

    @MockBean(name = "serverEndpointExporter")
    private ServerEndpointExporter serverEndpointExporter;

    @Autowired
    private NumberPoolServiceImpl numberPoolService;

    @Autowired
    private ParentPackageMapper parentPackageMapper;

    @Autowired
    private CustomerProfileMapper profileMapper;

    @Autowired
    private CustomerOrderMapper customerOrderMapper;

    private Long testPackageId;

    @BeforeEach
    public void setup() {
        // Insert a test parent_package with small pool (10 slots: 1001-1010)
        ParentPackage pkg = new ParentPackage();
        pkg.setPackageCode("CONCURRENT-TEST-PKG");
        pkg.setPackageName("CONCURRENT-TEST-PKG");
        pkg.setPrefix("T");
        pkg.setPoolPrefix("T1");
        pkg.setPoolStart(1001);
        pkg.setPoolEnd(1010); // Pool size = 10
        pkg.setStatus(true);
        parentPackageMapper.insert(pkg);
        testPackageId = pkg.getId();

        // Clean up any previous test data for this package prefix
        profileMapper.delete(null);
        customerOrderMapper.delete(null);
    }

    /**
     * Test: 10 concurrent threads allocate from same pool simultaneously.
     *
     * Success criteria: All 10 allocated codes must be distinct.
     *
     * Why NO @Transactional here:
     * If the test method is @Transactional, Spring wraps ALL threads in the same
     * transaction. The lock is held until the outer transaction commits, serializing
     * what should be concurrent requests. Test passes in CI but fails in production.
     *
     * CountDownLatch ensures all threads begin allocating at the exact same moment.
     */
    @Test
    public void testConcurrentAllocation_10threads_allDistinct() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<String> codes = Collections.synchronizedList(new ArrayList<>());
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for start signal
                    NumberPoolConfig config = new NumberPoolConfig();
                    config.setPackageId(testPackageId);
                    config.setPoolPrefix("T1");
                    config.setPoolStart(1001);
                    config.setPoolEnd(1010);
                    String code = numberPoolService.allocate(config);
                    codes.add(code);
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all threads simultaneously
        boolean allDone = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(allDone, "Timed out waiting for allocations (>30s)");
        assertTrue(errors.isEmpty(), "Allocation errors: " + errors);
        assertEquals(threadCount, codes.size(), "Should have allocated 10 codes");

        // All codes must be distinct — the core assertion
        Set<String> uniqueCodes = new HashSet<>(codes);
        assertEquals(threadCount, uniqueCodes.size(),
            "All allocated codes must be distinct. Duplicate codes found: " + codes);
    }

    /**
     * Test: Pool overflow throws BadRequestException with correct message format.
     *
     * @Transactional is SAFE here — this test is purely sequential, no concurrency involved.
     */
    @Test
    public void testPoolOverflow_throwsCorrectException() {
        // Allocate all 10 codes sequentially
        for (int i = 1001; i <= 1010; i++) {
            NumberPoolConfig config = new NumberPoolConfig();
            config.setPackageId(testPackageId);
            config.setPoolPrefix("T1");
            config.setPoolStart(1001);
            config.setPoolEnd(1010);
            String code = numberPoolService.allocate(config);
            assertNotNull(code);
        }

        // 11th allocation — must throw
        NumberPoolConfig overflowConfig = new NumberPoolConfig();
        overflowConfig.setPackageId(testPackageId);
        overflowConfig.setPoolPrefix("T1");
        overflowConfig.setPoolStart(1001);
        overflowConfig.setPoolEnd(1010);

        BadRequestException ex = assertThrows(BadRequestException.class,
            () -> numberPoolService.allocate(overflowConfig));

        String msg = ex.getMessage();
        assertTrue(msg.contains("T1"), "Message must contain pool prefix 'T1': " + msg);
        assertTrue(msg.contains("已满"), "Message must contain '已满': " + msg);
        assertTrue(msg.contains("/"), "Message must contain used/total ratio: " + msg);
        assertTrue(msg.contains("请联系管理员扩容"), "Message must contain escalation text: " + msg);
        assertTrue(msg.contains("10/10"), "Message should contain exact count '10/10': " + msg);
    }
}
