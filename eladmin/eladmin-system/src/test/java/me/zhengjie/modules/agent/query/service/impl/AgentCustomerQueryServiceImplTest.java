package me.zhengjie.modules.agent.query.service.impl;

import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerCandidateDto;
import me.zhengjie.modules.agent.query.service.AgentOrderQueryService;
import me.zhengjie.modules.agent.query.service.AgentHistoryQueryService;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerOverviewDto;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Agent 客户解析使用有限候选和脱敏字段，不会自动选择同名客户。 */
@ExtendWith(MockitoExtension.class)
class AgentCustomerQueryServiceImplTest {
    @Mock private CustomerProfileMapper customerProfileMapper;
    @Mock private CustomerProfileAddressMapper customerProfileAddressMapper;
    @Mock private CustomerOrderMapper customerOrderMapper;
    @Mock private AgentOrderQueryService agentOrderQueryService;
    @Mock private AgentHistoryQueryService agentHistoryQueryService;
    @InjectMocks private AgentCustomerQueryServiceImpl service;

    @Test
    void shouldLimitNameCandidatesAndMaskPhone() {
        List<CustomerProfile> profiles = new ArrayList<>();
        for (int index = 1; index <= 11; index++) profiles.add(profile((long) index, "B" + index, "张三", "13800138000"));
        when(customerProfileMapper.selectList(any())).thenReturn(profiles);

        AgentListResultDto<AgentCustomerCandidateDto> result = service.resolve(null, null, "张三");

        assertEquals(11, result.getTotal());
        assertEquals(10, result.getItems().size());
        assertTrue(result.isTruncated());
        assertEquals("138****8000", result.getItems().get(0).getMaskedPhone());
    }

    @Test
    void shouldReturnCandidatesWithoutLoadingCustomerOverviewWhenNameIsAmbiguous() {
        when(customerProfileMapper.selectList(any())).thenReturn(List.of(profile(1L, "B1", "张三", "13800138000"), profile(2L, "B2", "张三", "13900139000")));

        AgentListResultDto<AgentCustomerCandidateDto> result = service.resolve(null, null, "张三");

        assertEquals(2, result.getTotal());
        assertFalse(result.isTruncated());
        verify(customerProfileMapper, never()).selectByIdWithJson(any());
    }

    /** 客户概览应返回档案创建时间，并以首单成交时间作为首次购买时间。 */
    @Test
    void shouldReturnCustomerCreationAndFirstPurchaseTime() {
        CustomerProfile profile = profile(68L, "B2200", "新客户", "13800138000");
        profile.setCreateTime(LocalDateTime.of(2026, 7, 1, 9, 0));
        CustomerOrder firstOrder = new CustomerOrder();
        firstOrder.setCreateTime(LocalDateTime.of(2026, 7, 2, 9, 30));
        firstOrder.setDealTime(LocalDateTime.of(2026, 7, 2, 10, 0));
        when(customerProfileMapper.selectList(any())).thenReturn(List.of(profile));
        when(customerProfileMapper.selectByIdWithJson(68L)).thenReturn(profile);
        when(customerOrderMapper.selectOne(any())).thenReturn(firstOrder);
        when(customerProfileAddressMapper.selectList(any())).thenReturn(List.of());
        when(agentOrderQueryService.listForOverview(68L)).thenReturn(new me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto<>());
        when(agentHistoryQueryService.listVerifications(any())).thenReturn(new me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto<>());
        when(agentHistoryQueryService.listRefunds(any())).thenReturn(new me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto<>());

        AgentCustomerOverviewDto result = service.getOverview(null, "B2200");

        assertTrue(result.isPresent());
        assertEquals(profile.getCreateTime(), result.getCreateTime());
        assertEquals(firstOrder.getDealTime(), result.getFirstPurchaseTime());
    }

    /** 构造最小客户档案。 */
    private CustomerProfile profile(Long id, String code, String name, String phone) {
        CustomerProfile profile = new CustomerProfile(); profile.setId(id); profile.setCustomerCode(code); profile.setCustomerName(name); profile.setPhone(phone); return profile;
    }
}
