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
import me.zhengjie.modules.customer.profile.domain.CustomerDietImportData;
import me.zhengjie.modules.customer.profile.domain.ImportCandidate;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportAddressDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportDraftDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportItemResultDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportMealCellDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 在单客户事务中创建客户档案与地址；有待导入餐数时创建首单和未来逐餐数量计划。
 */
@Service
@RequiredArgsConstructor
public class CustomerProfileImportWriter {

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
     * @param confirmedAt 确认请求开始时间，用于缺省成交时间
     * @return CREATED/UPDATED/ALREADY_EXISTS 结果；补录和无餐数建档的订单主键为空
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public CustomerImportItemResultDto write(ImportCandidate candidate, LocalDate importDate, LocalDateTime confirmedAt) {
        CustomerImportDraftDto draft = candidate.getDraft();
        if (candidate.isSupplemental()) {
            return updateExistingCustomer(candidate, true);
        }
        ParentPackage parent = lockAndValidatePackage(candidate);
        CustomerProfile existing = profileMapper.selectOne(
                new QueryWrapper<CustomerProfile>().eq("customer_code", draft.getCustomerCode()));
        if (existing != null) {
            if (draft.getCustomerCode().equals(existing.getCustomerCode())
                    && sameNormalizedPhone(existing.getPhone(), candidate.getParsed().getPhoneNormalized())) {
                CustomerProfile locked = profileMapper.selectByIdForImportUpdate(existing.getId());
                boolean updated = mergeSupplementalFields(locked, candidate);
                return result(candidate, updated ? "UPDATED" : "ALREADY_EXISTS",
                        updated ? "客户饮食信息已补录" : "客户已存在且无新增资料；未修改历史订单成交时间",
                        locked.getId(), null);
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
        CustomerDietImportData dietData = candidate.getParsed().getDietImportData();
        if (dietData != null) {
            profile.setMedicalRequirements(dietData.getMedicalRequirements());
            profile.setPostoperativeInfo(dietData.getPostoperativeInfo());
            profile.setDishRequirements(copyItems(dietData.getDishRequirements()));
            profile.setDietaryRestrictions(copyItems(dietData.getDietaryRestrictions()));
            profile.setDishRequirementsRaw(copyBlocks(dietData.getDishRequirementsRaw()));
            profile.setDietaryRestrictionsRaw(copyBlocks(dietData.getDietaryRestrictionsRaw()));
        }
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
        CustomerOrder order = buildFirstOrder(profile, parent, draft, importDate, confirmedAt,
                candidate.getParsed().getDietImportData());
        Long orderId = customerOrderService.createImportedFirstOrder(order);
        saveFutureMealCells(profile.getId(), orderId, draft.getMealCells());
        return result(candidate, "CREATED", "客户档案、首单和未来逐餐计划已创建", profile.getId(), orderId);
    }

    /**
     * 仅按第二工作表编号补录已存在客户的饮食资料。
     *
     * @param candidate 预览中已找到对应客户的第二工作表候选
     * @return 更新或无变化的结果；不会创建客户、地址和订单
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public CustomerImportItemResultDto writeDietOnly(ImportCandidate candidate) {
        if (!candidate.isSupplemental() || candidate.getExistingProfile() == null
                || candidate.getParsed().getDietImportData() == null) {
            throw new BadRequestException("第二工作表补录缺少已有客户或饮食资料，请重新预览");
        }
        return updateExistingCustomer(candidate, false);
    }

    /**
     * 锁定已存在客户并补录饮食共享字段，不触碰地址或任何历史订单。
     *
     * @param candidate 已通过预览核验的补录候选
     * @param verifyPhone 是否复核月份工作表提供的手机号
     * @return 更新或无变化的结果
     */
    private CustomerImportItemResultDto updateExistingCustomer(ImportCandidate candidate, boolean verifyPhone) {
        Long customerId = candidate.getExistingProfile() == null ? null : candidate.getExistingProfile().getId();
        CustomerProfile profile = customerId == null ? null : profileMapper.selectByIdForImportUpdate(customerId);
        if (profile == null
                || !candidate.getParsed().getEffectiveCode().equals(profile.getCustomerCode())
                || (verifyPhone && !sameNormalizedPhone(profile.getPhone(), candidate.getParsed().getPhoneNormalized()))) {
            throw new BadRequestException("已有客户身份资料已变化，请重新预览后补录");
        }
        boolean updated = mergeSupplementalFields(profile, candidate);
        return result(candidate, updated ? "UPDATED" : "ALREADY_EXISTS",
                updated ? "客户饮食信息已补录" : "客户已存在且无新增资料；未修改历史订单成交时间",
                profile.getId(), null);
    }

    /**
     * 仅在目标字段为空时补充医嘱和术后信息，并去重合并对象引用与来源原文。
     *
     * @param profile 已锁定的客户档案
     * @param candidate 已完成来源和身份核验的导入候选
     * @return 档案确有变更时为 true
     */
    private boolean mergeSupplementalFields(CustomerProfile profile, ImportCandidate candidate) {
        CustomerDietImportData data = candidate.getParsed().getDietImportData();
        if (data == null) {
            return false;
        }
        boolean changed = false;
        if (isBlank(profile.getMedicalRequirements()) && !isBlank(data.getMedicalRequirements())) {
            profile.setMedicalRequirements(data.getMedicalRequirements());
            changed = true;
        }
        if (isBlank(profile.getPostoperativeInfo()) && !isBlank(data.getPostoperativeInfo())) {
            profile.setPostoperativeInfo(data.getPostoperativeInfo());
            changed = true;
        }
        List<CustomerDietItemDto> mergedDish = mergeItems(profile.getDishRequirements(), data.getDishRequirements());
        if (!mergedDish.equals(nullToEmpty(profile.getDishRequirements()))) {
            profile.setDishRequirements(mergedDish);
            changed = true;
        }
        List<CustomerDietItemDto> mergedRestrictions = mergeItems(
                profile.getDietaryRestrictions(), data.getDietaryRestrictions());
        if (!mergedRestrictions.equals(nullToEmpty(profile.getDietaryRestrictions()))) {
            profile.setDietaryRestrictions(mergedRestrictions);
            changed = true;
        }
        List<String> mergedDishRaw = mergeBlocks(profile.getDishRequirementsRaw(), data.getDishRequirementsRaw());
        if (!mergedDishRaw.equals(nullToEmpty(profile.getDishRequirementsRaw()))) {
            profile.setDishRequirementsRaw(mergedDishRaw);
            changed = true;
        }
        List<String> mergedRestrictionRaw = mergeBlocks(
                profile.getDietaryRestrictionsRaw(), data.getDietaryRestrictionsRaw());
        if (!mergedRestrictionRaw.equals(nullToEmpty(profile.getDietaryRestrictionsRaw()))) {
            profile.setDietaryRestrictionsRaw(mergedRestrictionRaw);
            changed = true;
        }
        if (changed) {
            profile.setUpdateBy(currentUser());
            profile.setUpdateTime(LocalDateTime.now());
            profileMapper.updateById(profile);
        }
        return changed;
    }

    /**
     * 合并饮食对象列表，按类型+ID去重并保留已有名称快照。
     *
     * @param existing 当前客户对象列表
     * @param incoming 本次确认对象列表
     * @return 去重后的引用列表
     */
    private List<CustomerDietItemDto> mergeItems(List<CustomerDietItemDto> existing,
                                                  List<CustomerDietItemDto> incoming) {
        Map<String, CustomerDietItemDto> merged = new LinkedHashMap<>();
        for (CustomerDietItemDto item : nullToEmpty(existing)) {
            if (item != null && item.getType() != null && item.getId() != null) {
                merged.put(item.getType() + ":" + item.getId(), item);
            }
        }
        for (CustomerDietItemDto item : nullToEmpty(incoming)) {
            if (item == null || item.getType() == null || item.getId() == null) {
                continue;
            }
            String key = item.getType() + ":" + item.getId();
            if (!merged.containsKey(key)) {
                merged.put(key, copyItem(item));
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 合并完整原文块并按精确文本去重。
     *
     * @param existing 当前客户原文块
     * @param incoming 本次来源原文块
     * @return 保持原顺序的去重结果
     */
    private List<String> mergeBlocks(List<String> existing, List<String> incoming) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(nullToEmpty(existing));
        for (String block : nullToEmpty(incoming)) {
            if (block != null && !block.trim().isEmpty()) {
                merged.add(block);
            }
        }
        return new ArrayList<>(merged);
    }

    /** 复制并规范新建客户的饮食对象引用。 */
    private List<CustomerDietItemDto> copyItems(List<CustomerDietItemDto> items) {
        return new ArrayList<>(mergeItems(Collections.emptyList(), items));
    }

    /** 复制并去重新建客户的原文块。 */
    private List<String> copyBlocks(List<String> blocks) {
        return new ArrayList<>(mergeBlocks(Collections.emptyList(), blocks));
    }

    /** 复制饮食对象引用，避免保存导入预览上的临时属性。 */
    private CustomerDietItemDto copyItem(CustomerDietItemDto item) {
        CustomerDietItemDto copy = new CustomerDietItemDto();
        copy.setType(item.getType());
        copy.setId(item.getId());
        copy.setName(item.getName());
        return copy;
    }

    /** 将可空列表统一读取为空列表。 */
    private <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    /** 判断字符串是否为空或只含空白字符。 */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 锁定父套餐并确认它仍启用且编号位于当前编号池。
     *
     * <p>套餐名称不参与校验，编号归属以套餐管理中的编号池配置为准；
     * 防止预览到确认提交之间配置被修改导致编号错档。</p>
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
        if (code == null) {
            throw new BadRequestException("客户编号缺失，请重新预览");
        }
        if (parent.getPoolPrefix() == null || parent.getPoolStart() == null || parent.getPoolEnd() == null
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
     * @param confirmedAt 确认请求开始时间，成交时间为空时使用
     * @param dietData 第二工作表解析结果，用于设置成交时间
     * @return 可交由订单服务保存的首单
     */
    private CustomerOrder buildFirstOrder(CustomerProfile profile, ParentPackage parent,
                                          CustomerImportDraftDto draft, LocalDate importDate,
                                          LocalDateTime confirmedAt, CustomerDietImportData dietData) {
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
        order.setDealTime(dietData != null && dietData.getDealTime() != null
                ? dietData.getDealTime() : confirmedAt);
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
     * @param status CREATED / UPDATED / ALREADY_EXISTS
     * @param message 面向操作人的结果说明
     * @param customerId 新建或补录客户主键；未处理时为空
     * @param orderId 新建首单主键；补录或零餐数时为空
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
