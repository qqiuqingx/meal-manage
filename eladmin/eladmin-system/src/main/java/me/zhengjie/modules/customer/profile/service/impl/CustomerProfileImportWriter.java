package me.zhengjie.modules.customer.profile.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.CustomerOrderStatus;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import me.zhengjie.modules.customer.order.util.OrderStartMealTypeUtil;
import me.zhengjie.modules.customer.numberpool.mapper.NumberPoolMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress;
import me.zhengjie.modules.customer.profile.domain.ImportCandidate;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportAddressDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportDraftDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportItemResultDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportMealCellDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerMealScheduleAdditionMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 在单客户事务中创建客户档案与地址；有待导入餐数时创建首单和未来逐餐数量计划。
 */
@Service
@RequiredArgsConstructor
public class CustomerProfileImportWriter {

    private static final Pattern CODE_PATTERN = Pattern.compile("^([A-Za-z]+)(\\d+)$");
    private static final Map<String, String> CODE_PREFIX_PACKAGE_NAME = new LinkedHashMap<>();

    static {
        CODE_PREFIX_PACKAGE_NAME.put("A", "月子餐");
        CODE_PREFIX_PACKAGE_NAME.put("B", "孕期餐");
        CODE_PREFIX_PACKAGE_NAME.put("C", "小月子餐");
        CODE_PREFIX_PACKAGE_NAME.put("D", "营养餐");
        CODE_PREFIX_PACKAGE_NAME.put("F", "营养餐");
    }

    private final CustomerProfileMapper profileMapper;
    private final CustomerProfileAddressMapper addressMapper;
    private final CustomerMealScheduleAdditionMapper scheduleAdditionMapper;
    private final NumberPoolMapper numberPoolMapper;
    private final CustomerOrderService customerOrderService;

    /**
     * 原子写入一位客户；有待导入餐数时一并创建首单和未来日期餐次。
     *
     * @param candidate 已重新解析并完成只读校验的客户候选
     * @param importDate 实际订单开始日期
     * @return 创建结果，包含客户主键；无待导入餐数时首单主键为空
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public CustomerImportItemResultDto write(ImportCandidate candidate, LocalDate importDate) {
        CustomerImportDraftDto draft = candidate.getDraft();
        ParentPackage parent = lockAndValidatePackage(candidate);
        CustomerProfile existing = profileMapper.selectOne(
                new QueryWrapper<CustomerProfile>().eq("customer_code", draft.getCustomerCode()));
        if (existing != null) {
            if (draft.getCustomerCode().equals(existing.getCustomerCode())
                    && draft.getPhoneMasked() != null
                    && sameNormalizedPhone(existing.getPhone(), candidate.getParsed().getPhoneNormalized())) {
                return result(candidate, "ALREADY_EXISTS", "客户编号已存在，本次跳过（幂等重传）", null, null);
            }
            throw new BadRequestException("客户编号已存在且身份信息不一致，本次跳过");
        }

        CustomerProfile profile = new CustomerProfile();
        profile.setCustomerCode(draft.getCustomerCode());
        profile.setCustomerName(draft.getCustomerCode());
        profile.setPhone(candidate.getParsed().getPhoneNormalized());
        profile.setDeliveryPhoneInfo(candidate.getParsed().getDeliveryPhoneInfo());
        profile.setRemark(draft.getRemark());
        profile.setSpecialRequirements(draft.getSpecialRequirements());
        profile.setCreateBy(currentUser());
        profileMapper.insert(profile);

        for (CustomerImportAddressDto address : candidate.getParsed().getAddresses()) {
            CustomerProfileAddress entity = new CustomerProfileAddress();
            entity.setCustomerId(profile.getId());
            entity.setAddressType(address.getAddressType());
            entity.setAddressDetail(address.getAddressDetail());
            entity.setContactName(draft.getCustomerCode());
            entity.setContactPhone(address.getContactPhone() == null
                    ? candidate.getParsed().getPhoneNormalized() : address.getContactPhone());
            addressMapper.insert(entity);
        }

        if (draft.getLunchDinnerCount() == 0) {
            return result(candidate, "CREATED", "客户档案已创建", profile.getId(), null);
        }
        CustomerOrder order = buildFirstOrder(profile, parent, draft, importDate);
        Long orderId = customerOrderService.createImportedFirstOrder(order);
        saveFutureMealCells(profile.getId(), orderId, draft.getMealCells());
        return result(candidate, "CREATED", "客户档案、首单和未来逐餐计划已创建", profile.getId(), orderId);
    }

    /**
     * 锁定父套餐并确认它仍启用且编号位于当前编号池。
     *
     * @param candidate 当前客户导入候选
     * @return 已锁定并通过校验的父套餐
     */
    private ParentPackage lockAndValidatePackage(ImportCandidate candidate) {
        Long packageId = candidate.getDraft().getParentPackageId();
        ParentPackage parent = numberPoolMapper.selectForUpdate(packageId);
        if (parent == null || !Boolean.TRUE.equals(parent.getStatus())) {
            throw new BadRequestException("父套餐已不存在或已停用，请重新预览");
        }
        String code = candidate.getDraft().getCustomerCode();
        Matcher matcher = CODE_PATTERN.matcher(code == null ? "" : code);
        if (!matcher.matches()) {
            throw new BadRequestException("客户编号格式已变化，请重新预览");
        }
        String expectedName = CODE_PREFIX_PACKAGE_NAME.get(matcher.group(1).toUpperCase(Locale.ROOT));
        if (expectedName == null || !expectedName.equals(parent.getPackageName())
                || parent.getPoolPrefix() == null || parent.getPoolStart() == null || parent.getPoolEnd() == null
                || !code.startsWith(parent.getPoolPrefix())) {
            throw new BadRequestException("父套餐编号池配置已变化，请重新预览");
        }
        int number;
        try {
            number = Integer.parseInt(code.substring(parent.getPoolPrefix().length()));
        } catch (NumberFormatException e) {
            throw new BadRequestException("客户编号超出父套餐编号池格式范围，请重新预览");
        }
        if (number < parent.getPoolStart() || number > parent.getPoolEnd()) {
            throw new BadRequestException("客户编号已不在父套餐编号池范围内，请重新预览");
        }
        return parent;
    }

    /**
     * 将已确认的导入草稿转换为零金额首单，历史餐数从已核销基数起算。
     *
     * @param profile 新建客户
     * @param parent 唯一匹配父套餐
     * @param draft 已通过预览的客户草稿
     * @param importDate 实际导入日期；订单从次日开始承接未来计划
     * @return 可交由订单服务保存的首单
     */
    private CustomerOrder buildFirstOrder(CustomerProfile profile, ParentPackage parent,
                                          CustomerImportDraftDto draft, LocalDate importDate) {
        int importedVerified = draft.getImportedVerifiedCount() == null ? 0 : draft.getImportedVerifiedCount();
        CustomerOrder order = new CustomerOrder();
        order.setCustomerId(profile.getId());
        order.setCustomerCode(profile.getCustomerCode());
        order.setParentPackageId(parent.getId());
        order.setDepositAmount(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setFinalAmount(BigDecimal.ZERO);
        order.setBreakfastCount(0);
        order.setLunchDinnerCount(draft.getLunchDinnerCount());
        order.setBreakfastPrice(BigDecimal.ZERO);
        order.setLunchDinnerPrice(BigDecimal.ZERO);
        order.setImportedVerifiedCount(importedVerified);
        order.setVerifiedCount(importedVerified);
        order.setVerifiedAmount(BigDecimal.ZERO);
        order.setMealBalance(BigDecimal.ZERO);
        order.setRemainingCount(draft.getLunchDinnerCount() - importedVerified);
        order.setDealTime(LocalDateTime.now());
        order.setStartDate(importDate.plusDays(1));
        order.setStartMealType(draft.getMealType() == null ? null
                : OrderStartMealTypeUtil.normalizeStartMealType(draft.getMealType(), null));
        order.setPauseEffectiveDate(Boolean.TRUE.equals(draft.getPaused()) ? importDate : null);
        order.setStatus(Boolean.TRUE.equals(draft.getPaused())
                ? CustomerOrderStatus.PAUSED.getCode()
                : order.getRemainingCount() == 0 ? CustomerOrderStatus.COMPLETED.getCode()
                : CustomerOrderStatus.ACTIVE.getCode());
        order.setMealType(draft.getMealType());
        order.setScheduleMode(draft.getScheduleMode());
        order.setDeliveryDates(buildDeliveryDates(draft.getMealCells()));
        order.setRemark(draft.getRemark());
        order.setMainDishCount(draft.getMainDishCount());
        order.setSideDishCount(draft.getSideDishCount());
        order.setVegCount(draft.getVegCount());
        order.setRiceCount(draft.getRiceCount());
        order.setRiceType(draft.getRiceType());
        order.setSoupCount(draft.getSoupCount());
        order.setCreateBy(currentUser());
        return order;
    }

    /**
     * 将工作簿中的未来午晚餐格写入数量日历人工覆盖表。
     *
     * @param customerId 新建客户主键
     * @param orderId 新建首单主键
     * @param cells 已解析未来逐餐计划
     */
    private void saveFutureMealCells(Long customerId, Long orderId, List<CustomerImportMealCellDto> cells) {
        if (cells == null) {
            return;
        }
        for (CustomerImportMealCellDto cell : cells) {
            CustomerMealScheduleAddition addition = new CustomerMealScheduleAddition();
            addition.setCustomerId(customerId);
            addition.setOrderId(orderId);
            addition.setRecordDate(LocalDate.parse(cell.getDate()));
            addition.setMealType(cell.getMealType());
            addition.setQuantity(cell.getQuantity());
            addition.setSoupQuantity(cell.getSoupQuantity());
            addition.setRemark("客户用餐计划表导入");
            addition.setDeleted(false);
            addition.setCreateBy(currentUser());
            scheduleAdditionMapper.insert(addition);
        }
    }

    /**
     * 把未来逐餐计划整理为排餐模式使用的日期和餐次 JSON。
     *
     * @param cells 未来逐餐计划
     * @return 日期餐次 JSON；没有明确未来计划时返回 null
     */
    private String buildDeliveryDates(List<CustomerImportMealCellDto> cells) {
        if (cells == null || cells.isEmpty()) {
            return null;
        }
        Map<String, Set<String>> byDate = new TreeMap<>();
        for (CustomerImportMealCellDto cell : cells) {
            byDate.computeIfAbsent(cell.getDate(), key -> new java.util.LinkedHashSet<>()).add(cell.getMealType());
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : byDate.entrySet()) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", entry.getKey());
            day.put("mealTypes", entry.getValue());
            values.add(day);
        }
        return JSON.toJSONString(values);
    }

    /**
     * 组装当前客户的提交结果。
     *
     * @param candidate 当前客户导入候选
     * @param status CREATED / ALREADY_EXISTS
     * @param message 面向操作人的结果说明
     * @param customerId 新建客户主键；跳过时为空
     * @param orderId 新建首单主键；跳过时为空
     * @return 单客户处理结果
     */
    private CustomerImportItemResultDto result(ImportCandidate candidate, String status, String message,
                                               Long customerId, Long orderId) {
        CustomerImportItemResultDto result = new CustomerImportItemResultDto();
        result.setSourceRows(new ArrayList<>(candidate.getParsed().getSourceRows()));
        result.setCustomerCode(candidate.getDraft().getCustomerCode());
        result.setStatus(status);
        result.setMessage(message);
        result.setCustomerId(customerId);
        result.setOrderId(orderId);
        return result;
    }

    /**
     * 比较已存在客户和来源电话的数字序列，兼容数据库中的分隔符格式。
     *
     * @param existingPhone 数据库手机号
     * @param sourcePhone 解析后的来源手机号
     * @return 数字序列一致时为 true
     */
    private boolean sameNormalizedPhone(String existingPhone, String sourcePhone) {
        if (existingPhone == null || sourcePhone == null) {
            return false;
        }
        return existingPhone.replaceAll("[^0-9]", "").equals(sourcePhone);
    }

    /**
     * 获取当前导入操作人；无安全上下文的内部调用按 system 记录。
     *
     * @return 当前用户名或 system
     */
    private String currentUser() {
        try {
            return SecurityUtils.getCurrentUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}
