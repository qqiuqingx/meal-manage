package me.zhengjie.modules.agent.query.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
        if (AgentCustomerDataScopeContext.status() == AgentCustomerDataScopeContext.ScopeStatus.UNBOUND) return new AgentListResultDto<>();
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
        int page = safePage(safe);
        int size = safeSize(safe);
        Page<MealVerificationLog> logPage = verificationLogMapper.selectPage(new Page<>(page, size), wrapper);
        List<MealVerificationLog> rows = logPage == null || logPage.getRecords() == null
            ? java.util.Collections.emptyList() : logPage.getRecords();
        AgentListResultDto<AgentVerificationLogDto> result = new AgentListResultDto<>();
        result.setTotal(logPage == null ? 0L : logPage.getTotal());
        result.setPage(page); result.setSize(size);
        result.setTruncated((long) page * size < result.getTotal());
        result.setQueriedAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.ofHours(8)).toString());
        result.setItems(rows.stream().map(this::verification).collect(Collectors.toList()));
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentListResultDto<AgentRefundLogDto> listRefunds(AgentHistoryQueryRequest request) {
        AgentHistoryQueryRequest safe = request == null ? new AgentHistoryQueryRequest() : request;
        if (AgentCustomerDataScopeContext.status() == AgentCustomerDataScopeContext.ScopeStatus.UNBOUND) return new AgentListResultDto<>();
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
        int page = safePage(safe);
        int size = safeSize(safe);
        Page<MealRefundLog> logPage = refundLogMapper.selectPage(new Page<>(page, size), wrapper);
        List<MealRefundLog> rows = logPage == null || logPage.getRecords() == null
            ? java.util.Collections.emptyList() : logPage.getRecords();
        AgentListResultDto<AgentRefundLogDto> result = new AgentListResultDto<>();
        result.setTotal(logPage == null ? 0L : logPage.getTotal());
        result.setPage(page); result.setSize(size);
        result.setTruncated((long) page * size < result.getTotal());
        result.setQueriedAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.ofHours(8)).toString());
        result.setItems(rows.stream().map(this::refund).collect(Collectors.toList()));
        return result;
    }

    /** 将核销实体转换为不含金额的 Agent 核销摘要。 */
    private AgentVerificationLogDto verification(MealVerificationLog source) {
        AgentVerificationLogDto dto = new AgentVerificationLogDto();
        dto.setVerificationId(source.getId()); dto.setCustomerId(source.getCustomerId()); dto.setOrderId(source.getOrderId());
        dto.setMealPlanCustomerId(source.getMealPlanCustomerId()); dto.setRecordDate(source.getRecordDate());
        dto.setMealTypeCode(source.getMealType()); dto.setVerificationCount(source.getVerificationCount());
        dto.setRefunded(Integer.valueOf(1).equals(source.getIsRefunded())); dto.setOperateTime(source.getOperateTime());
        return dto;
    }

    /** 将退餐实体转换为限长原因且不含退款金额的 Agent 摘要。 */
    private AgentRefundLogDto refund(MealRefundLog source) {
        AgentRefundLogDto dto = new AgentRefundLogDto();
        dto.setRefundId(source.getId()); dto.setCustomerId(source.getCustomerId()); dto.setOrderId(source.getOrderId());
        dto.setRefundBreakfastCount(source.getRefundBreakfastCount()); dto.setRefundLunchDinnerCount(source.getRefundLunchDinnerCount());
        dto.setVerifiedBreakfastCount(source.getVerifiedBreakfastCount()); dto.setVerifiedLunchDinnerCount(source.getVerifiedLunchDinnerCount());
        dto.setRefundReason(truncate(source.getRefundReason())); dto.setOperateTime(source.getOperateTime());
        return dto;
    }
    /** 兼容旧 recentLimit 参数并将历史查询条数限制在 1 到 50。 */
    private int limit(Integer value) { return Math.min(Math.max(value == null ? 10 : value, 1), 50); }

    /** 兼容旧 recentLimit 请求，同时为统一接口提供真实 SQL 分页参数。 */
    private int safePage(AgentHistoryQueryRequest request) {
        return Math.max(request.getPage() == null ? 1 : request.getPage(), 1);
    }

    /** 统一历史查询单页上限，防止通过分页参数扩大模型上下文。 */
    private int safeSize(AgentHistoryQueryRequest request) {
        return Math.min(Math.max(request.getSize() == null ? limit(request.getRecentLimit()) : request.getSize(), 1), 50);
    }
    /** 防止请求在没有业务对象约束下扫描无界历史记录。 */
    private void requireScope(AgentHistoryQueryRequest request) {
        if (request.getCustomerId() == null && request.getOrderId() == null
            && !(hasText(request.getStartDate()) && hasText(request.getEndDate()))) {
            throw new IllegalArgumentException("核销或退餐查询必须指定客户或订单");
        }
        if (request.getCustomerId() == null && request.getOrderId() == null) {
            LocalDate start = parseDate(request.getStartDate());
            LocalDate end = parseDate(request.getEndDate());
            if (end.isBefore(start) || end.isAfter(start.plusDays(30))) {
                throw new IllegalArgumentException("广域核销或退餐查询日期范围不能超过 31 天");
            }
        }
    }
    /** 返回当前请求的受限客户集合；空集合由调用方直接返回空结果。 */
    private Set<Long> scopedCustomerIds() { return AgentCustomerDataScopeContext.customerIds(); }
    private Date startOfDay(String value) { return parse(value, false); }
    private Date endExclusive(String value) { return parse(value, true); }
    /** 将日期文本转换为上海时区的起始时间或次日开区间。 */
    private Date parse(String value, boolean nextDay) {
        if (!hasText(value)) return null;
        try {
            LocalDate date = LocalDate.parse(value.trim());
            if (nextDay) date = date.plusDays(1);
            return Date.from(date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("日期必须使用 yyyy-MM-dd 格式", exception);
        }
    }
    /** 解析广域历史查询的日期范围，不读取数据库。 */
    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("日期必须使用 yyyy-MM-dd 格式", exception);
        }
    }
    /** 判断日期或查询参数是否包含非空文本。 */
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    /** 限制退餐原因长度，防止自由文本扩大模型上下文。 */
    private String truncate(String value) { return value == null ? null : value.length() <= 200 ? value : value.substring(0, 200) + "…"; }
}
