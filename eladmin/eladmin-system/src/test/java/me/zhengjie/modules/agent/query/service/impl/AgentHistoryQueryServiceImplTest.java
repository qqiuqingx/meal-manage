package me.zhengjie.modules.agent.query.service.impl;

import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import me.zhengjie.modules.meal.mapper.MealRefundLogMapper;
import me.zhengjie.modules.meal.mapper.MealVerificationLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 核销与退餐 Agent 历史查询的数据范围和统计口径测试。 */
class AgentHistoryQueryServiceImplTest {

    @AfterEach
    void clearDataScope() {
        AgentCustomerDataScopeContext.clear();
    }

    /** 核销记录总数只统计当前数据范围内的未删除日志，并由数据库直接聚合。 */
    @Test
    void shouldCountVerificationRecordsWithinCurrentDataScope() {
        MealVerificationLogMapper verificationMapper = mock(MealVerificationLogMapper.class);
        when(verificationMapper.selectCount(any())).thenReturn(27L);
        AgentHistoryQueryServiceImpl service = new AgentHistoryQueryServiceImpl(
            verificationMapper, mock(MealRefundLogMapper.class));
        AgentCustomerDataScopeContext.bind(Set.of(10L, 11L));

        assertEquals(27L, service.countVerificationRecords());

        verify(verificationMapper).selectCount(any());
    }

    /** 未绑定访问范围时返回零且不访问核销表，避免绕过主系统授权上下文。 */
    @Test
    void shouldNotCountVerificationRecordsWithoutBoundDataScope() {
        MealVerificationLogMapper verificationMapper = mock(MealVerificationLogMapper.class);
        AgentHistoryQueryServiceImpl service = new AgentHistoryQueryServiceImpl(
            verificationMapper, mock(MealRefundLogMapper.class));

        assertEquals(0L, service.countVerificationRecords());

        verifyNoInteractions(verificationMapper);
    }
}
