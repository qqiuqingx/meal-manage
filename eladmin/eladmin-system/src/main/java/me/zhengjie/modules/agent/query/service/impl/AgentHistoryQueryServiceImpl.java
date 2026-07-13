package me.zhengjie.modules.agent.query.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.query.domain.dto.AgentHistoryQueryRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentRefundLogDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentVerificationLogDto;
import me.zhengjie.modules.agent.query.service.AgentHistoryQueryService;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import me.zhengjie.modules.meal.domain.MealRefundLog;
import me.zhengjie.modules.meal.domain.MealVerificationLog;
import me.zhengjie.modules.meal.mapper.MealRefundLogMapper;
import me.zhengjie.modules.meal.mapper.MealVerificationLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 核销与退餐受控只读查询实现。 */
@Service
@RequiredArgsConstructor
public class AgentHistoryQueryServiceImpl implements AgentHistoryQueryService {

    private final MealVerificationLogMapper verificationLogMapper;
    private final MealRefundLogMapper refundLogMapper;

    /** {@inheritDoc} */
    @Override
    public AgentListResultDto<AgentVerificationLogDto> listVerifications(AgentHistoryQueryRequest request) {
        AgentHistoryQueryRequest safe = request == null ? new AgentHistoryQueryRequest() : request;
        requireScope(safe);
        Set<Long> scopedCustomerIds = scopedCustomerIds();
        if (scopedCustomerIds != null && scopedCustomerIds.isEmpty()) return new AgentListResultDto<>();
        if (safe.getCustomerId() != null && !AgentCustomerDataScopeContext.allows(safe.getCustomerId())) return new AgentListResultDto<>();
        Date start = startOfDay(safe.getStartDate());
        Date endExclusive = endExclusive(safe.getEndDate());
        LambdaQueryWrapper<MealVerificationLog> wrapper = new LambdaQueryWrapper<MealVerificationLog>()
                .eq(MealVerificationLog::getDeleted, 0)
                .eq(safe.getCustomerId() != null, MealVerificationLog::getCustomerId, safe.getCustomerId())
                .eq(safe.getOrderId() != null, MealVerificationLog::getOrderId, safe.getOrderId())
                .in(scopedCustomerIds != null, MealVerificationLog::getCustomerId, scopedCustomerIds)
                .eq(hasText(safe.getMealType()), MealVerificationLog::getMealType, safe.getMealType())
                .ge(start != null, MealVerificationLog::getRecordDate, start)
                .lt(endExclusive != null, MealVerificationLog::getRecordDate, endExclusive)
                .orderByDesc(MealVerificationLog::getOperateTime).orderByDesc(MealVerificationLog::getId);
        List<MealVerificationLog> rows = verificationLogMapper.selectList(wrapper);
        AgentListResultDto<AgentVerificationLogDto> result = new AgentListResultDto<>();
        result.setTotal(rows.size());
        int limit = limit(safe.getRecentLimit());
        result.setTruncated(rows.size() > limit);
        result.setItems(rows.stream().limit(limit).map(this::verification).collect(Collectors.toList()));
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentListResultDto<AgentRefundLogDto> listRefunds(AgentHistoryQueryRequest request) {
        AgentHistoryQueryRequest safe = request == null ? new AgentHistoryQueryRequest() : request;
        requireScope(safe);
        Set<Long> scopedCustomerIds = scopedCustomerIds();
        if (scopedCustomerIds != null && scopedCustomerIds.isEmpty()) return new AgentListResultDto<>();
        if (safe.getCustomerId() != null && !AgentCustomerDataScopeContext.allows(safe.getCustomerId())) return new AgentListResultDto<>();
        Date start = startOfDay(safe.getStartDate());
        Date endExclusive = endExclusive(safe.getEndDate());
        LambdaQueryWrapper<MealRefundLog> wrapper = new LambdaQueryWrapper<MealRefundLog>()
                .eq(safe.getCustomerId() != null, MealRefundLog::getCustomerId, safe.getCustomerId())
                .eq(safe.getOrderId() != null, MealRefundLog::getOrderId, safe.getOrderId())
                .in(scopedCustomerIds != null, MealRefundLog::getCustomerId, scopedCustomerIds)
                .ge(start != null, MealRefundLog::getOperateTime, start)
                .lt(endExclusive != null, MealRefundLog::getOperateTime, endExclusive)
                .orderByDesc(MealRefundLog::getOperateTime).orderByDesc(MealRefundLog::getId);
        List<MealRefundLog> rows = refundLogMapper.selectList(wrapper);
        AgentListResultDto<AgentRefundLogDto> result = new AgentListResultDto<>();
        result.setTotal(rows.size());
        int limit = limit(safe.getRecentLimit());
        result.setTruncated(rows.size() > limit);
        result.setItems(rows.stream().limit(limit).map(this::refund).collect(Collectors.toList()));
        return result;
    }

    private AgentVerificationLogDto verification(MealVerificationLog source) {
        AgentVerificationLogDto dto = new AgentVerificationLogDto();
        dto.setVerificationId(source.getId()); dto.setCustomerId(source.getCustomerId()); dto.setOrderId(source.getOrderId());
        dto.setMealPlanCustomerId(source.getMealPlanCustomerId()); dto.setRecordDate(source.getRecordDate());
        dto.setMealTypeCode(source.getMealType()); dto.setVerificationCount(source.getVerificationCount());
        dto.setRefunded(Integer.valueOf(1).equals(source.getIsRefunded())); dto.setOperateTime(source.getOperateTime());
        return dto;
    }

    private AgentRefundLogDto refund(MealRefundLog source) {
        AgentRefundLogDto dto = new AgentRefundLogDto();
        dto.setRefundId(source.getId()); dto.setCustomerId(source.getCustomerId()); dto.setOrderId(source.getOrderId());
        dto.setRefundBreakfastCount(source.getRefundBreakfastCount()); dto.setRefundLunchDinnerCount(source.getRefundLunchDinnerCount());
        dto.setVerifiedBreakfastCount(source.getVerifiedBreakfastCount()); dto.setVerifiedLunchDinnerCount(source.getVerifiedLunchDinnerCount());
        dto.setRefundReason(truncate(source.getRefundReason())); dto.setOperateTime(source.getOperateTime());
        return dto;
    }
    private int limit(Integer value) { return Math.min(Math.max(value == null ? 10 : value, 1), 50); }
    /** 防止请求在没有业务对象约束下扫描无界历史记录。 */
    private void requireScope(AgentHistoryQueryRequest request) {
        if (request.getCustomerId() == null && request.getOrderId() == null) {
            throw new IllegalArgumentException("核销或退餐查询必须指定客户或订单");
        }
    }
    /** 返回当前请求的受限客户集合；空集合由调用方直接返回空结果。 */
    private Set<Long> scopedCustomerIds() { return AgentCustomerDataScopeContext.customerIds(); }
    private Date startOfDay(String value) { return parse(value, false); }
    private Date endExclusive(String value) { return parse(value, true); }
    private Date parse(String value, boolean nextDay) {
        if (!hasText(value)) return null;
        try {
            LocalDate date = LocalDate.parse(value.trim());
            if (nextDay) date = date.plusDays(1);
            return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("日期必须使用 yyyy-MM-dd 格式", exception);
        }
    }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private String truncate(String value) { return value == null ? null : value.length() <= 200 ? value : value.substring(0, 200) + "…"; }
}
