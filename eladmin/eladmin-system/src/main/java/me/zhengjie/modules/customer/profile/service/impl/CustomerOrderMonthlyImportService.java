package me.zhengjie.modules.customer.profile.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealVerifiedCountDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.util.OrderStartMealTypeUtil;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import me.zhengjie.modules.customer.profile.domain.ImportCandidate;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportDraftDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueCategory;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportMealCellDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerScheduledMealDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerMealScheduleAdditionMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 月度续导的只读定位、数量保护及写入；写入由逐客户事务 Writer 调用。 */
@Service
@RequiredArgsConstructor
public class CustomerOrderMonthlyImportService {

    private static final LocalDate FIRST_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate LAST_DATE = LocalDate.of(9999, 12, 31);
    private final CustomerOrderMapper orderMapper;
    private final CustomerMealScheduleAdditionMapper additionMapper;
    private final MealPlanCustomerMapper planMapper;
    private final MealPlanService mealPlanService;

    /**
     * 定位唯一导入订单并计算来源月份、余额变更与月度数量差异，不写库。
     * @param candidate 已解析且完成饮食资料关联的客户候选
     * @param month 来源月份的月首日
     * @param importDate 历史/未来数量的日期边界
     */
    @Transactional(readOnly = true)
    public void prepare(ImportCandidate candidate, LocalDate month, LocalDate importDate) {
        candidate.setSourceMonth(month);
        candidate.setExistingOrder(null);
        candidate.setOrderRevision(null);
        CustomerImportDraftDto draft = candidate.getDraft();
        draft.setSourceMonth(month == null ? null : YearMonth.from(month).toString());
        draft.setImportAction(candidate.getExistingProfile() == null ? "CREATE" : "NEW_ORDER");
        try {
            if (!candidate.getParsed().isImportable() || candidate.isAlreadyExists()) {
                return;
            }
            if (month == null || importDate == null) {
                throw new BadRequestException("缺少来源月份或导入日期，请重新预览");
            }
            draft.setAfterMealCount(safe(draft.getLunchDinnerCount()));
            draft.setAfterRemainingCount(remaining(draft));
            draft.setAfterOrderStatus(safe(draft.getLunchDinnerCount()) == 0 ? null
                    : Boolean.TRUE.equals(draft.getPaused()) ? 4 : remaining(draft) == 0 ? 2 : 1);
            draft.setAddedMealCellCount(draft.getHistoricalMealCells().size() + draft.getMealCells().size());
            draft.setChangedMealCellCount(0);
            draft.setRemovedMealCellCount(0);
            if (candidate.getExistingProfile() == null) {
                if (safe(draft.getLunchDinnerCount()) == 0) {
                    draft.setImportAction("PROFILE_ONLY");
                }
                return;
            }
            List<CustomerOrder> orders = customerOrders(candidate);
            if (orders.isEmpty()) {
                candidate.setOrderRevision("NO_ORDER");
                if (safe(draft.getLunchDinnerCount()) == 0) {
                    draft.setImportAction("PROFILE_ONLY");
                }
                return;
            }
            LocalDate dealDate = candidate.getParsed().getDietImportData() == null
                    || candidate.getParsed().getDietImportData().getDealTime() == null ? null
                    : candidate.getParsed().getDietImportData().getDealTime().toLocalDate();
            List<Facts> matches = new ArrayList<>();
            for (CustomerOrder order : orders) {
                if (!Objects.equals(order.getParentPackageId(), draft.getParentPackageId())) {
                    continue;
                }
                if (dealDate != null && (order.getDealTime() == null
                        || !dealDate.equals(order.getDealTime().toLocalDate()))) {
                    continue;
                }
                Facts facts = facts(order);
                if (order.getImportMonth() != null || order.getImportDate() != null
                        || safe(order.getImportedVerifiedCount()) > 0
                        || facts.additions.stream().anyMatch(CustomerMealScheduleAddition::isImported)) {
                    matches.add(facts);
                }
            }
            if (matches.size() != 1) {
                throw new BadRequestException("无法唯一定位同套餐、成单日期的导入订单，请核对订单归属后重新预览");
            }
            Facts facts = matches.get(0);
            candidate.setExistingOrder(facts.order);
            candidate.setOrderRevision(revision(facts));
            describe(candidate, facts, importDate);
            check(candidate, facts, importDate);
        } catch (BadRequestException e) {
            if (candidate.getParsed().getIssues().stream().noneMatch(issue -> Objects.equals(issue.getMessage(), e.getMessage()))) {
                candidate.getParsed().addIssue(CustomerImportIssueCategory.PROFILE_ERROR, e.getMessage());
            }
        }
    }

    /**
     * 先锁定预览指定的订单；后续 Writer 再锁客户，避免与核销/日历保存锁序相反。
     * @param candidate 带目标订单与修订摘要的预览候选
     * @return 锁定订单；客户尚无订单时为空
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CustomerOrder lockOrder(ImportCandidate candidate) {
        if (candidate.getExistingOrder() == null) {
            return null;
        }
        CustomerOrder order = orderMapper.selectInlineUpdateByIdForUpdate(candidate.getExistingOrder().getId());
        if (order == null || !Objects.equals(order.getCustomerId(), candidate.getExistingProfile().getId())
                || !Objects.equals(order.getParentPackageId(), candidate.getDraft().getParentPackageId())) {
            throw new BadRequestException("续导目标订单已变化，请重新预览");
        }
        return order;
    }

    /**
     * 客户已加锁且尚无目标订单时复核未被并发创建首单。
     * @param candidate 首单候选
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkNoOrder(ImportCandidate candidate) {
        if (!customerOrders(candidate).isEmpty()) {
            throw new BadRequestException("客户订单已变化，请重新预览后确认，不能自动换单");
        }
    }

    /**
     * 在订单与客户锁内更新月度快照，保护实际核销与人工来源记录；不改变成单日期及金额。
     * @param candidate 预览候选
     * @param order 已锁定的订单
     * @param importDate 本次确认的历史/未来边界
     * @return 订单元数据、餐数或数量记录确有变化时为 true
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean apply(ImportCandidate candidate, CustomerOrder order, LocalDate importDate) {
        Facts facts = facts(order);
        if (!Objects.equals(candidate.getOrderRevision(), revision(facts))) {
            throw new BadRequestException("订单、数量计划或核销记录已变化，请重新预览");
        }
        check(candidate, facts, importDate);
        LocalDate currentMonth = currentMonth(facts);
        boolean countsApply = currentMonth == null || !candidate.getSourceMonth().isBefore(currentMonth);
        Map<String, CustomerImportMealCellDto> wanted = wanted(candidate);
        Map<String, CustomerMealScheduleAddition> saved = index(facts.additions);
        List<Long> removed = new ArrayList<>();
        boolean changed = false;
        for (CustomerMealScheduleAddition addition : facts.additions) {
            if (inMonth(addition.getRecordDate(), candidate.getSourceMonth()) && addition.isImported()
                    && !wanted.containsKey(key(addition.getRecordDate(), addition.getMealType()))) {
                removed.add(addition.getId());
            }
        }
        if (!removed.isEmpty()) {
            if (additionMapper.softDeleteImportedByIds(order.getId(), removed) != removed.size()) {
                throw new BadRequestException("月度数量记录已变化，请重新预览");
            }
            changed = true;
        }
        LocalDate nextImportDate = acceptedImportDate(candidate, facts, importDate);
        LocalDate boundary = nextImportDate == null || importDate.isAfter(nextImportDate) ? importDate : nextImportDate;
        for (CustomerImportMealCellDto cell : wanted.values()) {
            LocalDate date = LocalDate.parse(cell.getDate());
            String remark = sourceRemark(date, boundary);
            CustomerMealScheduleAddition old = saved.get(key(date, cell.getMealType()));
            if (old != null && same(old, cell, remark)) {
                continue;
            }
            if (old != null) {
                requireOne(additionMapper.updateOrderCalendarOverride(old.getId(), order.getId(),
                        cell.getQuantity(), cell.getSoupQuantity(), remark, operator()));
            } else {
                CustomerMealScheduleAddition deleted = additionMapper.selectAnyByOrderDateMeal(order.getId(), date, cell.getMealType());
                if (deleted != null && deleted.isImported()) {
                    requireOne(additionMapper.reviveOrderCalendarOverride(deleted.getId(), order.getId(), date,
                            cell.getMealType(), cell.getQuantity(), cell.getSoupQuantity(), remark, operator()));
                } else {
                    CustomerMealScheduleAddition addition = new CustomerMealScheduleAddition();
                    addition.setOrderId(order.getId());
                    addition.setCustomerId(order.getCustomerId());
                    addition.setRecordDate(date);
                    addition.setMealType(cell.getMealType());
                    addition.setQuantity(cell.getQuantity());
                    addition.setSoupQuantity(cell.getSoupQuantity());
                    addition.setRemark(remark);
                    addition.setDeleted(false);
                    addition.setCreateBy(operator());
                    requireOne(additionMapper.insert(addition));
                }
            }
            changed = true;
        }
        if (countsApply) {
            for (CustomerMealScheduleAddition addition : facts.additions) {
                if (!inMonth(addition.getRecordDate(), candidate.getSourceMonth()) && addition.isImported()
                        && !addition.isImportedHistory() && !addition.getRecordDate().isAfter(nextImportDate)) {
                    requireOne(additionMapper.updateOrderCalendarOverride(addition.getId(), order.getId(),
                            quantity(addition), addition.getSoupQuantity(), CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK, operator()));
                    changed = true;
                }
            }
        }
        LocalDate nextMonth = countsApply ? candidate.getSourceMonth() : currentMonth;
        int total = countsApply ? safe(candidate.getDraft().getLunchDinnerCount()) : safe(order.getLunchDinnerCount());
        int remaining = countsApply ? projectedRemaining(candidate, facts, importDate) : safe(order.getRemainingCount());
        int verified = countsApply ? total - remaining : safe(order.getVerifiedCount());
        int historical = countsApply ? total - remaining(candidate.getDraft()) - verifiedWithinSnapshot(candidate, facts, importDate)
                : safe(order.getImportedVerifiedCount());
        Integer status = Integer.valueOf(1).equals(order.getStatus()) && remaining == 0 ? 2 : order.getStatus();
        LocalDate nextDate = nextImportDate;
        if (!Objects.equals(order.getImportMonth(), nextMonth) || !Objects.equals(order.getImportDate(), nextDate)
                || safe(order.getLunchDinnerCount()) != total || safe(order.getRemainingCount()) != remaining
                || safe(order.getVerifiedCount()) != verified || safe(order.getImportedVerifiedCount()) != historical
                || !Objects.equals(order.getStatus(), status)) {
            order.setImportMonth(nextMonth);
            order.setImportDate(nextDate);
            order.setLunchDinnerCount(total);
            order.setRemainingCount(remaining);
            order.setVerifiedCount(verified);
            order.setImportedVerifiedCount(historical);
            order.setStatus(status);
            order.setUpdateBy(operator());
            requireOne(orderMapper.updateById(order));
            changed = true;
        }
        if (order.getImportDate() != null) {
            LocalDate coverage = OrderStartMealTypeUtil.resolveImportCoverageEnd(order);
            int before = facts.verifications.stream().filter(value -> !value.getRecordDate().isAfter(order.getImportDate()))
                    .mapToInt(value -> safe(value.getVerifiedCount())).sum();
            before += facts.progress.stream().filter(value -> ("LUNCH".equals(value.getMealType()) || "DINNER".equals(value.getMealType()))
                    && !value.getRecordDate().isAfter(order.getImportDate()) && value.getRecordDate().isAfter(coverage))
                    .mapToInt(value -> Math.max(safe(value.getGeneratedCount()) - safe(value.getVerifiedCount()), 0)).sum();
            order.setQuantityAllocatedBeforeImport(before);
        }
        for (CustomerScheduledMealDto progress : facts.progress) {
            if (!inMonth(progress.getRecordDate(), candidate.getSourceMonth())) {
                continue;
            }
            String key = key(progress.getRecordDate(), progress.getMealType());
            CustomerImportMealCellDto target = wanted.get(key);
            CustomerMealScheduleAddition old = saved.get(key);
            if (target == null && (old != null && !old.isImported() || old == null && !countsApply)) {
                continue;
            }
            int quantity = target == null ? 0 : safe(target.getQuantity());
            if (safe(progress.getGeneratedCount()) + safe(progress.getFailedCount()) > quantity) {
                mealPlanService.deleteExcessUnverifiedCustomerServingsForCalendarAdjustment(
                        order.getCustomerId(), order.getId(), progress.getRecordDate().toString(), progress.getMealType(), quantity);
                changed = true;
            }
        }
        return changed;
    }

    /** 查询客户全部订单；有其他套餐的订单时不能误当作尚无首单。 */
    private List<CustomerOrder> customerOrders(ImportCandidate candidate) {
        List<CustomerOrder> orders = orderMapper.selectList(new QueryWrapper<CustomerOrder>()
                .eq("customer_id", candidate.getExistingProfile().getId()).orderByAsc("id"));
        return orders == null ? Collections.emptyList() : orders;
    }

    /** 读取当前订单所有数量和真实核销事实，预览/锁内确认复用同一检查路径。 */
    private Facts facts(CustomerOrder order) {
        Facts facts = new Facts();
        facts.order = order;
        List<CustomerMealScheduleAddition> additions = additionMapper.selectActiveByOrderIdAndDateRange(order.getId(), FIRST_DATE, LAST_DATE);
        facts.additions = additions == null ? Collections.emptyList() : additions.stream()
                .sorted(Comparator.comparing(CustomerMealScheduleAddition::getRecordDate).thenComparing(CustomerMealScheduleAddition::getMealType))
                .collect(Collectors.toList());
        List<CustomerScheduledMealDto> progress = planMapper.selectScheduledMealsByOrderIdAndDateRange(order.getId(), FIRST_DATE, LAST_DATE);
        facts.progress = progress == null ? Collections.emptyList() : progress.stream()
                .sorted(Comparator.comparing(CustomerScheduledMealDto::getRecordDate).thenComparing(CustomerScheduledMealDto::getMealType))
                .collect(Collectors.toList());
        List<OrderMealVerifiedCountDto> verified = orderMapper.sumVerifiedCountByOrderDate(order.getId());
        facts.verifications = verified == null ? Collections.emptyList() : verified;
        if (facts.verifications.stream().anyMatch(value -> value.getRecordDate() == null)) {
            throw new BadRequestException("真实核销日志缺少用餐日期，请核对数据");
        }
        facts.verifications = facts.verifications.stream().sorted(Comparator.comparing(OrderMealVerifiedCountDto::getRecordDate)
                .thenComparing(OrderMealVerifiedCountDto::getMealType)).collect(Collectors.toList());
        facts.actualVerified = facts.verifications.stream().mapToInt(count -> safe(count.getVerifiedCount())).sum();
        return facts;
    }

    /**
     * 读取既有来源月份，缺少元数据时从导入日格推断，不使用订单创建日期。
     * @param facts 已定位导入订单及其有效数量记录
     * @return 已采用的来源月；旧导入订单尚无月份基线时返回空，由首次通过校验的文件建立
     */
    private LocalDate currentMonth(Facts facts) {
        if (facts.order.getImportMonth() != null) {
            return facts.order.getImportMonth();
        }
        return facts.additions.stream().filter(CustomerMealScheduleAddition::isImported)
                .map(addition -> addition.getRecordDate().withDayOfMonth(1)).max(LocalDate::compareTo)
                .orElse(null);
    }

    /** 校验月份、累计真实核销及本月已核销份数下限，不通过时禁止该客户续导。 */
    private void check(ImportCandidate candidate, Facts facts, LocalDate importDate) {
        LocalDate month = currentMonth(facts);
        boolean latest = month == null || !candidate.getSourceMonth().isBefore(month);
        CustomerOrder order = facts.order;
        if (latest) {
            if (order.getImportDate() != null && importDate.isBefore(order.getImportDate())) {
                throw new BadRequestException("导入日期不能早于订单已承接的日期边界，请重新预览");
            }
            int real = safe(order.getVerifiedCount()) - safe(order.getImportedVerifiedCount());
            int remaining = projectedRemaining(candidate, facts, importDate);
            if (safe(order.getBreakfastCount()) != 0 || real < 0 || real != facts.actualVerified) {
                throw new BadRequestException("订单餐池或系统核销事实与累计核销数不一致，请核对后续导");
            }
            if (remaining < 0 || safe(candidate.getDraft().getLunchDinnerCount()) - remaining(candidate.getDraft())
                    < verifiedWithinSnapshot(candidate, facts, importDate)) {
                throw new BadRequestException("来源餐数或剩余数与已有真实核销冲突，不能生成负余额或负的历史核销基数");
            }
            if (Integer.valueOf(2).equals(order.getStatus()) && remaining > 0) {
                throw new BadRequestException("已完成订单出现正余额，请先确认订单归属与状态，不自动恢复");
            }
        }
        Map<String, CustomerImportMealCellDto> desired = wanted(candidate);
        Map<String, CustomerMealScheduleAddition> saved = index(facts.additions);
        LocalDate boundary = order.getImportDate() != null && !latest && order.getImportDate().isAfter(importDate)
                ? order.getImportDate() : importDate;
        String mealType = OrderStartMealTypeUtil.normalizeOrderMealType(order.getMealType());
        for (Map.Entry<String, CustomerImportMealCellDto> entry : desired.entrySet()) {
            CustomerImportMealCellDto cell = entry.getValue();
            if (LocalDate.parse(cell.getDate()).isAfter(boundary)
                    && ("LUNCH".equals(mealType) || "DINNER".equals(mealType))
                    && !mealType.equals(cell.getMealType())) {
                throw new BadRequestException(cell.getDate() + " 未来日格与原订单餐次不一致，请先核对用餐配置");
            }
            if (LocalDate.parse(cell.getDate()).isAfter(boundary)
                    && (order.getStartDate() == null || !OrderStartMealTypeUtil.hasStartedForMeal(order.getStartDate(),
                    OrderStartMealTypeUtil.normalizeStartMealType(order.getMealType(), order.getStartMealType()),
                    LocalDate.parse(cell.getDate()), cell.getMealType()))) {
                throw new BadRequestException(cell.getDate() + " 未来日格早于原订单开始日期或开始餐次");
            }
            CustomerMealScheduleAddition old = saved.get(entry.getKey());
            if (old != null && !old.isImported()) {
                throw new BadRequestException(entry.getValue().getDate() + " " + entry.getValue().getMealType() + " 已有人工来源数量，不能覆盖");
            }
        }
        if (latest) {
            LocalDate covered = OrderStartMealTypeUtil.importCoverageEnd(candidate.getSourceMonth(), importDate);
            for (CustomerMealScheduleAddition addition : facts.additions) {
                if (inMonth(addition.getRecordDate(), candidate.getSourceMonth()) && !addition.isImported()
                        && !addition.getRecordDate().isAfter(covered) && quantity(addition) > 0) {
                    throw new BadRequestException(addition.getRecordDate() + " 历史人工数量与本月文件冲突，请先核对日历");
                }
            }
        }
        for (CustomerScheduledMealDto progress : facts.progress) {
            if (!inMonth(progress.getRecordDate(), candidate.getSourceMonth())) {
                continue;
            }
            String key = key(progress.getRecordDate(), progress.getMealType());
            CustomerImportMealCellDto cell = desired.get(key);
            CustomerMealScheduleAddition old = saved.get(key);
            if (cell == null && old != null && !old.isImported()) {
                continue;
            }
            if (safe(progress.getVerifiedCount()) > (cell == null ? 0 : safe(cell.getQuantity()))) {
                throw new BadRequestException(progress.getRecordDate() + " " + progress.getMealType() + " 已核销份数不能减少或移除");
            }
            if (safe(progress.getVerifiedCount()) > 0 && old != null && cell != null
                    && !Objects.equals(old.getSoupQuantity(), cell.getSoupQuantity())) {
                throw new BadRequestException(progress.getRecordDate() + " 已核销格含汤配置不能改写");
            }
        }
    }

    /** 填充操作、目标月份、数量前后值及新增/修改/移除格数，用于操作人确认。 */
    private void describe(ImportCandidate candidate, Facts facts, LocalDate importDate) {
        CustomerImportDraftDto draft = candidate.getDraft();
        LocalDate current = currentMonth(facts);
        boolean oldMonth = current != null && candidate.getSourceMonth().isBefore(current);
        draft.setImportAction(oldMonth ? "BACKFILL_MONTH" : candidate.getSourceMonth().equals(current) ? "UPDATE_SAME_MONTH" : "UPDATE_MONTH");
        draft.setTargetOrderId(facts.order.getId());
        draft.setTargetOrderCode(facts.order.getOrderCode());
        draft.setCurrentImportMonth(current == null ? null : YearMonth.from(current).toString());
        draft.setCurrentMealCount(safe(facts.order.getLunchDinnerCount()));
        draft.setCurrentRemainingCount(safe(facts.order.getRemainingCount()));
        draft.setAfterMealCount(oldMonth ? safe(facts.order.getLunchDinnerCount()) : safe(draft.getLunchDinnerCount()));
        draft.setAfterRemainingCount(oldMonth ? safe(facts.order.getRemainingCount()) : projectedRemaining(candidate, facts, importDate));
        draft.setImportedVerifiedCount(oldMonth ? safe(facts.order.getImportedVerifiedCount())
                : safe(draft.getLunchDinnerCount()) - remaining(draft) - verifiedWithinSnapshot(candidate, facts, importDate));
        draft.setPostSnapshotVerifiedCount(oldMonth ? 0 : facts.actualVerified - verifiedWithinSnapshot(candidate, facts, importDate));
        draft.setCurrentOrderStatus(facts.order.getStatus());
        draft.setAfterOrderStatus(!oldMonth && Integer.valueOf(1).equals(facts.order.getStatus()) && projectedRemaining(candidate, facts, importDate) == 0
                ? 2 : facts.order.getStatus());
        Map<String, CustomerMealScheduleAddition> saved = index(facts.additions);
        Map<String, CustomerImportMealCellDto> desired = wanted(candidate);
        int added = 0, changed = 0, removed = 0;
        LocalDate boundary = facts.order.getImportDate() != null && oldMonth && facts.order.getImportDate().isAfter(importDate)
                ? facts.order.getImportDate() : importDate;
        for (Map.Entry<String, CustomerImportMealCellDto> entry : desired.entrySet()) {
            CustomerMealScheduleAddition old = saved.get(entry.getKey());
            if (old == null) {
                added++;
            } else if (!same(old, entry.getValue(), sourceRemark(LocalDate.parse(entry.getValue().getDate()), boundary))) {
                changed++;
            }
        }
        for (CustomerMealScheduleAddition addition : facts.additions) {
            if (addition.isImported() && inMonth(addition.getRecordDate(), candidate.getSourceMonth())
                    && !desired.containsKey(key(addition.getRecordDate(), addition.getMealType()))) {
                removed++;
            }
        }
        draft.setAddedMealCellCount(added);
        draft.setChangedMealCellCount(changed);
        draft.setRemovedMealCellCount(removed);
    }

    /** 表内已经涵盖的真实消费不重复扣减，表期以后的真实消费仍保留已扣余额。 */
    private int verifiedWithinSnapshot(ImportCandidate candidate, Facts facts, LocalDate importDate) {
        LocalDate covered = OrderStartMealTypeUtil.importCoverageEnd(candidate.getSourceMonth(), importDate);
        return facts.verifications.stream().filter(value -> !value.getRecordDate().isAfter(covered))
                .mapToInt(value -> safe(value.getVerifiedCount())).sum();
    }

    /** 最大月份表作为余额基数，其覆盖期外的真实核销不能因重新上传被返还。 */
    private int projectedRemaining(ImportCandidate candidate, Facts facts, LocalDate importDate) {
        return remaining(candidate.getDraft()) - (facts.actualVerified - verifiedWithinSnapshot(candidate, facts, importDate));
    }

    /** 月末之后重复修订同月，不把已有后续月份履约区间重新推成导入历史。 */
    private LocalDate acceptedImportDate(ImportCandidate candidate, Facts facts, LocalDate importDate) {
        LocalDate current = currentMonth(facts);
        if (current != null && candidate.getSourceMonth().isBefore(current)) {
            return facts.order.getImportDate();
        }
        LocalDate end = YearMonth.from(candidate.getSourceMonth()).atEndOfMonth();
        if (candidate.getSourceMonth().equals(current) && facts.order.getImportDate() != null
                && !facts.order.getImportDate().isBefore(end) && !importDate.isBefore(end)) {
            return facts.order.getImportDate();
        }
        return importDate;
    }

    /** 合并本表历史与未来非零格，防止日期跨月、餐次重复或非法份数进入写入阶段。 */
    private Map<String, CustomerImportMealCellDto> wanted(ImportCandidate candidate) {
        Map<String, CustomerImportMealCellDto> result = new LinkedHashMap<>();
        List<CustomerImportMealCellDto> cells = new ArrayList<>(candidate.getDraft().getHistoricalMealCells());
        cells.addAll(candidate.getDraft().getMealCells());
        for (CustomerImportMealCellDto cell : cells) {
            LocalDate date = LocalDate.parse(cell.getDate());
            if (!inMonth(date, candidate.getSourceMonth()) || safe(cell.getQuantity()) <= 0
                    || !("LUNCH".equals(cell.getMealType()) || "DINNER".equals(cell.getMealType()))
                    || result.putIfAbsent(key(date, cell.getMealType()), cell) != null) {
                throw new BadRequestException("月度导入日格日期、餐次或份数不合法");
            }
        }
        return result;
    }

    /** 以订单、有效数量及真实进度计算摘要，不在日志或响应中暴露原始状态数据。 */
    private String revision(Facts facts) {
        try {
            CustomerOrder order = facts.order;
            // BaseMapper 与 SELECT * 锁定查询可能填充不同的非数据库展示字段，只比较持久化业务状态。
            List<Object> orderState = Arrays.asList(order.getId(), order.getCustomerId(), order.getCustomerCode(),
                    order.getParentPackageId(), order.getChildPackageId(), order.getOrderCode(),
                    order.getDealTime(), order.getStartDate(), order.getStartMealType(), order.getEndDate(),
                    order.getImportMonth(), order.getImportDate(), order.getPauseEffectiveDate(),
                    order.getBreakfastCount(), order.getLunchDinnerCount(), order.getVerifiedCount(),
                    order.getImportedVerifiedCount(), order.getRemainingCount(), order.getStatus(),
                    order.getMealType(), order.getScheduleMode(), order.getDeliveryDates(),
                    order.getBreakfastPrice(), order.getLunchDinnerPrice(), order.getDepositAmount(),
                    order.getTotalAmount(), order.getFinalAmount(), order.getVerifiedAmount(), order.getMealBalance(),
                    order.getMainDishCount(), order.getSideDishCount(), order.getVegCount(),
                    order.getRiceCount(), order.getRiceType(), order.getSoupCount(),
                    order.getCreateTime(), order.getUpdateTime());
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(JSON.toJSONString(
                    Arrays.asList(orderState, facts.additions, facts.progress, facts.verifications)).getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            for (byte b : hash) {
                text.append(String.format("%02x", b & 255));
            }
            return text.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 将已有有效日格按日期餐次索引，用于差异和核销下限检查。 */
    private Map<String, CustomerMealScheduleAddition> index(List<CustomerMealScheduleAddition> additions) {
        Map<String, CustomerMealScheduleAddition> result = new LinkedHashMap<>();
        for (CustomerMealScheduleAddition addition : additions) {
            if (result.putIfAbsent(key(addition.getRecordDate(), addition.getMealType()), addition) != null) {
                throw new BadRequestException("订单存在重复有效数量格，请核对数据后续导");
            }
        }
        return result;
    }

    /** 比较数量、显式含汤数和来源，完全一致时不写库。 */
    private boolean same(CustomerMealScheduleAddition old, CustomerImportMealCellDto cell, String remark) {
        return quantity(old) == safe(cell.getQuantity()) && Objects.equals(old.getSoupQuantity(), cell.getSoupQuantity())
                && Objects.equals(old.getRemark(), remark);
    }

    /** 判断来源年月，不受实际确认日期或订单开始日期影响。 */
    private boolean inMonth(LocalDate date, LocalDate month) {
        return date != null && month != null && date.withDayOfMonth(1).equals(month);
    }

    /** 历史及未来记录使用已有来源标识，日期边界包括导入当天。 */
    private String sourceRemark(LocalDate date, LocalDate boundary) {
        return date.isAfter(boundary) ? CustomerMealScheduleAddition.IMPORTED_PLAN_REMARK : CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK;
    }

    /** 日期餐次业务键。 */
    private String key(LocalDate date, String mealType) { return date + "#" + mealType; }
    /** 空数量按0参与总量计算。 */
    private int safe(Integer value) { return value == null ? 0 : value; }
    /** 旧数量覆盖没有份数字段时沿用已有的一份语义。 */
    private int quantity(CustomerMealScheduleAddition addition) { return addition.getQuantity() == null ? 1 : addition.getQuantity(); }
    /** 可用餐数为J列加未来份数，不能再次扣除实际核销。 */
    private int remaining(CustomerImportDraftDto draft) {
        long count = (long) safe(draft.getSheetRemainingCount()) + safe(draft.getFutureMealCount());
        if (count < 0 || count > Integer.MAX_VALUE) {
            throw new BadRequestException("来源剩余餐数与未来份数合计超出有效范围");
        }
        return (int) count;
    }
    /** 所有范围写入都要求恰好影响预期行，防止并发覆盖。 */
    private void requireOne(int affected) {
        if (affected != 1) { throw new BadRequestException("订单或数量记录已变化，请重新预览"); }
    }
    /** 无安全上下文的内部业务调用按system记录，与导入Writer一致。 */
    private String operator() {
        try { return SecurityUtils.getCurrentUsername(); } catch (Exception e) { return "system"; }
    }

    /** 同一个生产事实集合供只读预览和锁内确认复用。 */
    private static class Facts {
        private CustomerOrder order;
        private List<CustomerMealScheduleAddition> additions;
        private List<CustomerScheduledMealDto> progress;
        private int actualVerified;
        private List<OrderMealVerifiedCountDto> verifications;
    }
}
