package me.zhengjie.modules.customer.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.OrderVerifiedCountDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.meal.mapper.MealVerificationLogMapper;
import me.zhengjie.modules.meal.service.DishService;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.domain.SubPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileSaveDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfilePackageMapper;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.customer.numberpool.domain.NumberPoolConfig;
import me.zhengjie.modules.customer.numberpool.service.NumberPoolService;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.PageUtil;
import me.zhengjie.utils.SecurityUtils;
import me.zhengjie.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户档案服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerProfileServiceImpl implements CustomerProfileService {

    private final CustomerProfileMapper profileMapper;
    private final CustomerProfileAddressMapper addressMapper;
    private final ParentPackageMapper parentPackageMapper;
    private final SubPackageMapper subPackageMapper;
    private final CustomerOrderMapper customerOrderMapper;
    private final CustomerProfilePackageMapper profilePackageMapper;
    private final MealVerificationLogMapper verificationLogMapper;
    private final NumberPoolService numberPoolService;
    private final DishService dishService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ORDER_CODE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public PageResult<CustomerProfile> queryAll(CustomerProfileQueryCriteria criteria, Page<Object> page) {
        page.setCountId("findAll_COUNT");
        IPage<CustomerProfile> pageResult = profileMapper.findAll(criteria, page);


        for (CustomerProfile profile : pageResult.getRecords()) {
            fillDefaultAddress(profile);
            fillLatestOrderInfo(profile);
        }
        PageResult<CustomerProfile> result = PageUtil.toPage(pageResult);
        return result;
    }

    @Override
    public CustomerProfileDetailDto getDetail(Long id) {
        CustomerProfile profile = profileMapper.selectByIdWithJson(id);
        if (profile == null) {
            throw new BadRequestException("客户档案不存在");
        }

        List<CustomerProfileAddress> addresses = addressMapper.selectList(
            new QueryWrapper<CustomerProfileAddress>().eq("customer_id", id)
        );

        CustomerProfileDetailDto detail = new CustomerProfileDetailDto();
        detail.setId(profile.getId());
        detail.setCustomerCode(profile.getCustomerCode());
        detail.setCustomerName(profile.getCustomerName());
        detail.setPhone(profile.getPhone());
        detail.setGestationalWeek(profile.getGestationalWeek());
        detail.setAllergyTags(profile.getAllergyTags());
        detail.setExcludedDishIds(profile.getExcludedDishIds());
        detail.setExcludedDishNames(convertDishIdsToNames(profile.getExcludedDishIds()));
        detail.setExcludedDates(profile.getExcludedDates());
        detail.setMedicalRequirements(profile.getMedicalRequirements());
        detail.setSpecialRequirements(profile.getSpecialRequirements());
        detail.setProductionDate(profile.getProductionDate());
        //
        detail.setCreateTime(profile.getCreateTime() != null ? profile.getCreateTime().toLocalDate() : null);
        detail.setUpdateTime(profile.getUpdateTime() != null ? profile.getUpdateTime().toLocalDate() : null);

        List<CustomerProfileDetailDto.AddressDto> addressDtos = addresses.stream()
            .map(addr -> {
                CustomerProfileDetailDto.AddressDto dto = new CustomerProfileDetailDto.AddressDto();
                dto.setAddressType(addr.getAddressType());
                dto.setAddressDetail(addr.getAddressDetail());
                dto.setContactName(addr.getContactName());
                dto.setContactPhone(addr.getContactPhone());
                return dto;
            })
            .collect(Collectors.toList());
        detail.setAddresses(addressDtos);

        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(CustomerProfileSaveDto dto) {
        CustomerProfileSaveDto.OrderInfoDto orderInfo = normalizeAndValidate(dto, true);
        String customerCode = resolveCustomerCode(dto.getCustomerCode(), orderInfo.getParentPackageId());

        CustomerProfile profile = new CustomerProfile();
        profile.setCustomerCode(customerCode);
        profile.setCustomerName(dto.getCustomerName());
        profile.setPhone(dto.getPhone());
        profile.setGestationalWeek(dto.getGestationalWeek());
        profile.setAllergyTags(dto.getAllergyTags());
        profile.setExcludedDishIds(dto.getExcludedDishIds());
        profile.setExcludedDates(dto.getExcludedDates());
        profile.setMedicalRequirements(dto.getMedicalRequirements());
        profile.setSpecialRequirements(dto.getSpecialRequirements());
        if (StringUtils.isNotBlank(dto.getProductionDate())) {
            profile.setProductionDate(LocalDate.parse(dto.getProductionDate().substring(0, 10), DATE_FORMATTER));
        }
        //
        profile.setCreateBy(getCurrentUsername());
        profileMapper.insert(profile);

        saveAddresses(profile.getId(), dto.getAddresses());
        saveFirstOrder(profile, orderInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerProfileSaveDto dto) {
        if (dto.getId() == null) {
            throw new BadRequestException("客户ID不能为空");
        }

        normalizeAndValidate(dto, false);

        CustomerProfile profile = profileMapper.selectById(dto.getId());
        if (profile == null) {
            throw new BadRequestException("客户档案不存在");
        }

        profile.setCustomerName(dto.getCustomerName());
        profile.setPhone(dto.getPhone());
        profile.setGestationalWeek(dto.getGestationalWeek());
        profile.setAllergyTags(dto.getAllergyTags());
        profile.setExcludedDishIds(dto.getExcludedDishIds());
        profile.setExcludedDates(dto.getExcludedDates());
        profile.setMedicalRequirements(dto.getMedicalRequirements());
        profile.setSpecialRequirements(dto.getSpecialRequirements());
        if (StringUtils.isNotBlank(dto.getProductionDate())) {
            profile.setProductionDate(LocalDate.parse(dto.getProductionDate().substring(0, 10), DATE_FORMATTER));
        }
        profile.setRemark(dto.getRemark());
        profile.setUpdateBy(getCurrentUsername());
        profileMapper.updateById(profile);

        updateAddresses(profile.getId(), dto.getAddresses());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("请选择要删除的客户");
        }

        // Step 1: 校验有效订单
        List<Map<String, Object>> activeOrderCounts = customerOrderMapper.countActiveOrdersByCustomerIds(ids);
        Map<Long, Integer> activeOrderMap = new HashMap<>();
        for (Map<String, Object> row : activeOrderCounts) {
            Object customerIdObj = row.get("customerId");
            Object countObj = row.get("orderCount");
            if (customerIdObj != null && countObj != null) {
                activeOrderMap.put(((Number) customerIdObj).longValue(), ((Number) countObj).intValue());
            }
        }

        List<String> blockedCustomers = new ArrayList<>();
        for (Long customerId : ids) {
            Integer count = activeOrderMap.getOrDefault(customerId, 0);
            if (count > 0) {
                CustomerProfile profile = profileMapper.selectById(customerId);
                if (profile != null) {
                    blockedCustomers.add(profile.getCustomerName());
                }
            }
        }

        if (!blockedCustomers.isEmpty()) {
            throw new BadRequestException("以下客户存在进行中的订单，无法删除：" + String.join("、", blockedCustomers));
        }

        // Step 2: 级联删除
        // 2a: 删除地址
        addressMapper.delete(new QueryWrapper<me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress>().in("customer_id", ids));
        // 2b: 删除签约记录
        profilePackageMapper.delete(new QueryWrapper<me.zhengjie.modules.customer.profile.domain.CustomerProfilePackage>().in("customer_id", ids));
        // 2c: 删除客户档案
        profileMapper.deleteBatchIds(ids);
    }

    //

    @Override
    public String generateCode(Long parentPackageId) {
        if (parentPackageId == null) {
            throw new BadRequestException("父套餐ID不能为空");
        }

        ParentPackage parent = parentPackageMapper.selectById(parentPackageId);
        if (parent == null) {
            throw new BadRequestException("父套餐不存在");
        }
        if (!Boolean.TRUE.equals(parent.getStatus())) {
            throw new BadRequestException("父套餐已禁用");
        }
//        if (StringUtils.isBlank(parent.getPrefix())) {
//            throw new BadRequestException("父套餐未配置编号前缀");
//        }

        // --- Backward compatibility: pool fields NULL → fall back to sequential allocation ---
        // Phase 5 migration will populate pool fields for all packages.
        // Before migration, packages with NULL pool fields use the original sequential logic.
        if (StringUtils.isBlank(parent.getPoolPrefix())
                || parent.getPoolStart() == null
                || parent.getPoolEnd() == null) {
            return generateCodeSequential(parent);
        }

        // --- Pool-based allocation (per POOL-05, POOL-06, POOL-07, POOL-08) ---
        // Delegates to NumberPoolService which holds SELECT FOR UPDATE on the package row.
        NumberPoolConfig config = new NumberPoolConfig();
        config.setPackageId(parent.getId());
        config.setPoolPrefix(parent.getPoolPrefix());
        config.setPoolStart(parent.getPoolStart());
        config.setPoolEnd(parent.getPoolEnd());

        return numberPoolService.allocate(config);
    }

    /**
     * 解析客户编号：优先使用前端传入值，否则自动生成。
     *
     * 前端传入时校验：
     * 1. 必须以父套餐 poolPrefix 开头
     * 2. 前缀后的数字必须在 [poolStart, poolEnd] 范围内
     * 3. 零填充宽度必须与 resolveCodeWidth() 一致（自动生成时保持一致）
     * 4. 编号必须在数据库中唯一
     *
     * @param manualCode 前端传入手动编号（可能为 null 或 blank）
     * @param parentPackageId 父套餐ID
     * @return 合规的客户编号
     */
    private String resolveCustomerCode(String manualCode, Long parentPackageId) {
        if (StringUtils.isNotBlank(manualCode)) {
            // --- 手动编号校验 ---
            ParentPackage parent = parentPackageMapper.selectById(parentPackageId);
            if (parent == null) {
                throw new BadRequestException("父套餐不存在");
            }
            String poolPrefix = parent.getPoolPrefix();
            if (StringUtils.isBlank(poolPrefix)) {
                throw new BadRequestException("该套餐未配置编号池，无法使用手动编号");
            }
            int poolStart = parent.getPoolStart();
            int poolEnd = parent.getPoolEnd();
            int codeWidth = resolveCodeWidth(poolStart, poolEnd);

            // 校验 1：必须以 poolPrefix 开头
            if (!manualCode.startsWith(poolPrefix)) {
                throw new BadRequestException("客户编号必须以「" + poolPrefix + "」开头，当前编号格式不符合");
            }

            // 校验 2：截取数字部分，校验范围
            String numPart = manualCode.substring(poolPrefix.length());
            if (numPart.length() != codeWidth) {
                throw new BadRequestException("客户编号数字部分必须为" + codeWidth + "位，如「" + poolPrefix + String.format("%0" + codeWidth + "d", poolStart) + "」");
            }
            int seq;
            try {
                seq = Integer.parseInt(numPart);
            } catch (NumberFormatException e) {
                throw new BadRequestException("客户编号数字部分必须为纯数字，如「" + poolPrefix + String.format("%0" + codeWidth + "d", poolStart) + "」");
            }
            if (seq < poolStart || seq > poolEnd) {
                throw new BadRequestException("客户编号必须在「" + poolPrefix + String.format("%0" + codeWidth + "d", poolStart) + "」～「" + poolPrefix + String.format("%0" + codeWidth + "d", poolEnd) + "」范围内");
            }

            // 校验 3：唯一性
            Long existingCount = profileMapper.selectCount(
                new QueryWrapper<CustomerProfile>().eq("customer_code", manualCode)
            );
            if (existingCount > 0) {
                throw new BadRequestException("客户编号「" + manualCode + "」已被占用，请换一个");
            }

            return manualCode;
        }

        // --- 自动生成 ---
        return generateCode(parentPackageId);
    }

    /**
     * 计算编号数字部分的固定宽度，保持与 NumberPoolServiceImpl.buildCode() 一致。
     * 宽度 = max(3, max(len(poolStart), len(poolEnd)))
     */
    private int resolveCodeWidth(int poolStart, int poolEnd) {
        int startWidth = String.valueOf(poolStart).length();
        int endWidth = String.valueOf(poolEnd).length();
        return Math.max(3, Math.max(startWidth, endWidth));
    }

    /**
     * Fallback: sequential allocation without pool (for packages with NULL pool fields).
     * Preserves existing behavior for packages migrated in Phase 5.
     * No locking — only used for packages without pool configuration.
     */
    private String generateCodeSequential(ParentPackage parent) {
        QueryWrapper<CustomerProfile> wrapper = new QueryWrapper<>();
        wrapper.likeRight("customer_code", parent.getPrefix())
            .orderByDesc("customer_code")
            .last("LIMIT 1");

        CustomerProfile lastProfile = profileMapper.selectOne(wrapper);
        int nextNum = 1;
        if (lastProfile != null) {
            String code = lastProfile.getCustomerCode();
            String numPart = code.substring(parent.getPrefix().length());
            try {
                nextNum = Integer.parseInt(numPart) + 1;
            } catch (NumberFormatException e) {
                nextNum = 1;
            }
        }
        return parent.getPrefix() + String.format("%03d", nextNum);
    }

    private CustomerProfileSaveDto.OrderInfoDto normalizeAndValidate(CustomerProfileSaveDto dto, boolean createMode) {
        if (StringUtils.isBlank(dto.getCustomerName())) {
            throw new BadRequestException("客户姓名不能为空");
        }
        if (StringUtils.isBlank(dto.getPhone())) {
            throw new BadRequestException("手机号不能为空");
        }
        if (!dto.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new BadRequestException("手机号格式不正确");
        }
        if (dto.getGestationalWeek() != null && dto.getGestationalWeek() <= 0) {
            throw new BadRequestException("孕周必须为正整数");
        }
        if (dto.getAddresses() == null || dto.getAddresses().isEmpty()) {
            throw new BadRequestException("地址信息不能为空");
        }

        boolean hasValidAddress = false;
        Set<String> addressTypes = new HashSet<>();
        for (CustomerProfileSaveDto.AddressDto addr : dto.getAddresses()) {
            if (StringUtils.isNotBlank(addr.getAddressDetail())) {
                hasValidAddress = true;
            }
            if (StringUtils.isNotBlank(addr.getAddressType())) {
                if (addressTypes.contains(addr.getAddressType())) {
                    throw new BadRequestException("地址类型不能重复");
                }
                addressTypes.add(addr.getAddressType());
            }
        }
        if (!hasValidAddress) {
            throw new BadRequestException("至少需要一个有效地址");
        }
        for (CustomerProfileSaveDto.AddressDto addr : dto.getAddresses()) {
            if (StringUtils.isNotBlank(addr.getAddressType())) {
                if (!"DEFAULT".equals(addr.getAddressType())
                    && !"WORKDAY".equals(addr.getAddressType())
                    && !"WEEKEND".equals(addr.getAddressType())) {
                    throw new BadRequestException("地址类型必须是 DEFAULT、WORKDAY 或 WEEKEND");
                }
            }
        }

        CustomerProfileSaveDto.OrderInfoDto validatedOrderInfo = null;
        if (createMode) {
            validatedOrderInfo = normalizeAndValidateOrderInfo(dto.getOrderInfo());
        }

        if (dto.getAllergyTags() != null && !dto.getAllergyTags().isEmpty()) {
            List<String> normalizedTags = dto.getAllergyTags().stream()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
            dto.setAllergyTags(normalizedTags);
        }

        // 验证排除菜品ID列表
        validateExcludedDishes(dto.getExcludedDishIds());

        // 验证排除日期格式
        validateExcludedDates(dto.getExcludedDates());

        return validatedOrderInfo;
    }

    private CustomerProfileSaveDto.OrderInfoDto normalizeAndValidateOrderInfo(CustomerProfileSaveDto.OrderInfoDto orderInfo) {
        if (orderInfo == null) {
            throw new BadRequestException("首单信息不能为空");
        }
        if (orderInfo.getParentPackageId() == null) {
            throw new BadRequestException("首单父套餐不能为空");
        }

        ParentPackage parent = parentPackageMapper.selectById(orderInfo.getParentPackageId());
        if (parent == null) {
            throw new BadRequestException("父套餐不存在");
        }
        if (!Boolean.TRUE.equals(parent.getStatus())) {
            throw new BadRequestException("父套餐已禁用");
        }
//        if (StringUtils.isBlank(parent.getPrefix())) {
//            throw new BadRequestException("父套餐未配置编号前缀");
//        }

        if (orderInfo.getChildPackageId() != null) {
            SubPackage child = subPackageMapper.selectById(orderInfo.getChildPackageId());
            if (child == null) {
                throw new BadRequestException("子套餐不存在");
            }
        }

        if (orderInfo.getBreakfastCount() == null && orderInfo.getLunchDinnerCount() == null) {
            throw new BadRequestException("早餐数与午餐+晚餐数至少填写一个");
        }
        if (StringUtils.isBlank(orderInfo.getStartDate()) ) {
            throw new BadRequestException("首单开始日期不能为空");
        }

        normalizeDishCount(orderInfo.getMainDishCount(), "主菜", false);
        normalizeDishCount(orderInfo.getSideDishCount(), "副菜", false);
        normalizeDishCount(orderInfo.getVegCount(), "素菜", false);
        normalizeDishCount(orderInfo.getRiceCount(), "米饭", true);
        normalizeDishCount(orderInfo.getSoupCount(), "汤", false);



        int breakfastCount = orderInfo.getBreakfastCount() != null ? orderInfo.getBreakfastCount() : 0;
        int lunchDinnerCount = orderInfo.getLunchDinnerCount() != null ? orderInfo.getLunchDinnerCount() : 0;
        if (breakfastCount < 0 || lunchDinnerCount < 0) {
            throw new BadRequestException("首单份数不能为负数");
        }
        int totalCount = breakfastCount + lunchDinnerCount;
        if (totalCount <= 0) {
            throw new BadRequestException("首单份数必须大于0");
        }
        orderInfo.setMainDishCount(orderInfo.getMainDishCount() != null ? orderInfo.getMainDishCount() : 0);
        orderInfo.setSideDishCount(orderInfo.getSideDishCount() != null ? orderInfo.getSideDishCount() : 0);
        orderInfo.setVegCount(orderInfo.getVegCount() != null ? orderInfo.getVegCount() : 0);
        orderInfo.setRiceCount(orderInfo.getRiceCount() != null ? orderInfo.getRiceCount() : 1);
        orderInfo.setSoupCount(orderInfo.getSoupCount() != null ? orderInfo.getSoupCount() : 0);
        orderInfo.setTotalCount(totalCount);
        return orderInfo;
    }

    private void normalizeDishCount(Integer count, String label, boolean enforceMaxTen) {
        if (count == null) {
            return;
        }
        if (count < 0) {
            throw new BadRequestException(label + "数量不能为负数");
        }
        if (enforceMaxTen && count > 10) {
            throw new BadRequestException(label + "数量不能超过10份");
        }
    }

    private void saveAddresses(Long customerId, List<CustomerProfileSaveDto.AddressDto> addresses) {
        if (addresses == null) {
            return;
        }

        for (CustomerProfileSaveDto.AddressDto addr : addresses) {
            if (StringUtils.isBlank(addr.getAddressDetail())) {
                continue;
            }
            CustomerProfileAddress entity = new CustomerProfileAddress();
            entity.setCustomerId(customerId);
            entity.setAddressType(addr.getAddressType());
            entity.setAddressDetail(addr.getAddressDetail());
            entity.setContactName(addr.getContactName());
            entity.setContactPhone(addr.getContactPhone());
            addressMapper.insert(entity);
        }
    }

    private void updateAddresses(Long customerId, List<CustomerProfileSaveDto.AddressDto> addresses) {
        addressMapper.delete(new QueryWrapper<CustomerProfileAddress>().eq("customer_id", customerId));
        saveAddresses(customerId, addresses);
    }

    private void saveFirstOrder(CustomerProfile profile, CustomerProfileSaveDto.OrderInfoDto orderInfo) {
        CustomerOrder order = new CustomerOrder();
        order.setCustomerId(profile.getId());
        order.setCustomerCode(profile.getCustomerCode());
        order.setOrderCode(generateFirstOrderCode());
        order.setParentPackageId(orderInfo.getParentPackageId());
        order.setChildPackageId(orderInfo.getChildPackageId());
        order.setDepositAmount(orderInfo.getDepositAmount() != null ? orderInfo.getDepositAmount() : BigDecimal.ZERO);
        order.setTotalAmount(orderInfo.getTotalAmount() != null ? orderInfo.getTotalAmount() : BigDecimal.ZERO);
        order.setFinalAmount(orderInfo.getFinalAmount() != null ? orderInfo.getFinalAmount() : BigDecimal.ZERO);
        order.setBreakfastCount(orderInfo.getBreakfastCount() != null ? orderInfo.getBreakfastCount() : 0);
        order.setLunchDinnerCount(orderInfo.getLunchDinnerCount() != null ? orderInfo.getLunchDinnerCount() : 0);
        order.setTotalCount(orderInfo.getTotalCount());
        order.setBreakfastPrice(orderInfo.getBreakfastPrice() != null ? orderInfo.getBreakfastPrice() : BigDecimal.ZERO);
        order.setLunchDinnerPrice(orderInfo.getLunchDinnerPrice() != null ? orderInfo.getLunchDinnerPrice() : BigDecimal.ZERO);
        order.setVerifiedCount(0);
        order.setVerifiedAmount(BigDecimal.ZERO);
        order.setMealBalance(orderInfo.getFinalAmount() != null ? orderInfo.getFinalAmount() : BigDecimal.ZERO);
        order.setRemainingCount(orderInfo.getTotalCount());
        order.setStartDate(LocalDate.parse(orderInfo.getStartDate(), DATE_FORMATTER));
        if (!StringUtils.isBlank(orderInfo.getEndDate())){
            order.setEndDate(LocalDate.parse(orderInfo.getEndDate(), DATE_FORMATTER));
        }

        order.setFirstDeliveryTime(null);
        order.setDealTime(java.time.LocalDateTime.now());
        order.setStatus(1);
        order.setScheduleMode(orderInfo.getScheduleMode() != null ? orderInfo.getScheduleMode() : "SCHEDULE");
        order.setMealType(orderInfo.getMealType() != null ? orderInfo.getMealType() : "ALL");
        order.setCustomerSource(orderInfo.getCustomerSource());
        order.setDeliveryDates(orderInfo.getDeliveryDates());
        order.setMainDishCount(orderInfo.getMainDishCount() != null ? orderInfo.getMainDishCount() : 0);
        order.setSideDishCount(orderInfo.getSideDishCount() != null ? orderInfo.getSideDishCount() : 0);
        order.setVegCount(orderInfo.getVegCount() != null ? orderInfo.getVegCount() : 0);
        order.setRiceCount(orderInfo.getRiceCount() != null ? orderInfo.getRiceCount() : 1);
        order.setRiceType(orderInfo.getRiceType() != null ? orderInfo.getRiceType() : "白米饭");
        order.setSoupCount(orderInfo.getSoupCount() != null ? orderInfo.getSoupCount() : 0);
        order.setRemark(profile.getRemark());
        order.setCreateBy(getCurrentUsername());
        customerOrderMapper.insert(order);
    }

    private String generateFirstOrderCode() {
        String datePrefix = "ORD" + LocalDate.now().format(ORDER_CODE_DATE_FORMATTER);
        String maxCode = customerOrderMapper.findTodayMaxOrderCode(datePrefix);
        int nextNum = 1;
        if (StringUtils.isNotBlank(maxCode) && maxCode.length() > datePrefix.length()) {
            String numPart = maxCode.substring(datePrefix.length());
            try {
                nextNum = Integer.parseInt(numPart) + 1;
            } catch (NumberFormatException e) {
                nextNum = 1;
            }
        }
        return datePrefix + String.format("%03d", nextNum);
    }

    private void fillDefaultAddress(CustomerProfile profile) {
        List<CustomerProfileAddress> addresses = addressMapper.selectList(
            new QueryWrapper<CustomerProfileAddress>()
                .eq("customer_id", profile.getId())
                .orderByAsc("address_type")
        );

        if (addresses == null) {
            addresses = Collections.emptyList();
        }

        StringBuilder allAddresses = new StringBuilder();
        for (CustomerProfileAddress addr : addresses) {
            if (StringUtils.isNotBlank(addr.getAddressDetail())) {
                if (allAddresses.length() > 0) {
                    allAddresses.append(", ");
                }
                allAddresses.append("[").append(getAddressTypeName(addr.getAddressType())).append("] ")
                          .append(addr.getAddressDetail());
            }
        }
        profile.setDefaultAddress(allAddresses.toString());
    }

    private String getAddressTypeName(String addressType) {
        if ("DEFAULT".equals(addressType)) {
            return "默认";
        } else if ("WORKDAY".equals(addressType)) {
            return "工作日";
        } else if ("WEEKEND".equals(addressType)) {
            return "周末";
        }
        return addressType;
    }

    /**
     * 验证排除日期格式
     */
    private void validateExcludedDates(List<me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto> excludedDates) {
        if (excludedDates == null || excludedDates.isEmpty()) {
            return;  // 字段可选，null或空列表合法
        }

        Set<String> validMealTypes = new HashSet<>(Arrays.asList("BREAKFAST", "LUNCH", "DINNER"));

        for (me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto dto : excludedDates) {
            if (dto == null) {
                throw new BadRequestException("排除日期列表不能包含空项");
            }

            // 校验日期格式
            try {
                LocalDate.parse(dto.getDate());  // 必须为 yyyy-MM-dd
            } catch (Exception e) {
                throw new BadRequestException("排除日期格式错误: " + dto.getDate());
            }

            // 校验餐次列表
            if (dto.getMealTypes() == null || dto.getMealTypes().isEmpty()) {
                throw new BadRequestException("排除日期必须指定至少一个餐次");
            }

            // 校验餐次值
            for (String mealType : dto.getMealTypes()) {
                if (!validMealTypes.contains(mealType)) {
                    throw new BadRequestException("无效的餐次类型: " + mealType);
                }
            }
        }
    }

    /**
     * 验证排除菜品ID列表
     */
    private void validateExcludedDishes(List<Integer> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return; // 空列表是允许的
        }

        // 去重检查
        Set<Integer> uniqueIds = new HashSet<>(dishIds);
        if (uniqueIds.size() != dishIds.size()) {
            throw new BadRequestException("排除菜品ID列表包含重复值");
        }

        // 检查菜品ID是否有效（正数）
        for (Integer dishId : dishIds) {
            if (dishId == null || dishId <= 0) {
                throw new BadRequestException("排除菜品ID必须为正整数");
            }
        }
    }

    private void fillLatestOrderInfo(CustomerProfile profile) {
        List<CustomerOrder> activeOrders = customerOrderMapper.findActiveOrdersByCustomerId(profile.getId());
        if (activeOrders != null && !activeOrders.isEmpty()) {
            // 汇总所有有效订单的总餐数
            int totalBreakfast = 0;
            int totalLunchDinner = 0;
            for (CustomerOrder order : activeOrders) {
                int breakfastCount = order.getBreakfastCount() != null ? order.getBreakfastCount() : 0;
                int lunchDinnerCount = order.getLunchDinnerCount() != null ? order.getLunchDinnerCount() : 0;
                totalBreakfast += breakfastCount;
                totalLunchDinner += lunchDinnerCount;
            }
            profile.setBreakfastCount(totalBreakfast);
            profile.setLunchDinnerCount(totalLunchDinner);

            // 汇总所有有效订单的核销数据
            int breakfastVerified = 0;
            int lunchDinnerVerified = 0;
            for (CustomerOrder order : activeOrders) {
                List<OrderVerifiedCountDto> verifiedList = customerOrderMapper.sumVerifiedCountByOrderId(order.getId());
                if (verifiedList != null) {
                    for (OrderVerifiedCountDto item : verifiedList) {
                        String mealType = item.getMealType();
                        int verifiedCount = item.getVerifiedCount() != null ? item.getVerifiedCount() : 0;
                        if ("BREAKFAST".equals(mealType)) {
                            breakfastVerified += verifiedCount;
                        } else if ("LUNCH".equals(mealType) || "DINNER".equals(mealType)) {
                            lunchDinnerVerified += verifiedCount;
                        }
                    }
                }
            }
            profile.setRemainingBreakfastCount(Math.max(totalBreakfast - breakfastVerified, 0));
            profile.setRemainingLunchDinnerCount(Math.max(totalLunchDinner - lunchDinnerVerified, 0));

            // 填充送餐模式（从最新订单获取）
            CustomerOrder latestOrder = customerOrderMapper.findLatestByCustomerId(profile.getId());
            if (latestOrder != null && latestOrder.getScheduleMode() != null) {
                profile.setScheduleMode(mapScheduleModeToChinese(latestOrder.getScheduleMode()));
            } else {
                profile.setScheduleMode("-");
            }
        } else {
            profile.setScheduleMode("-");
        }
    }

    /**
     * 映射排餐模式为中文标签
     * @param scheduleMode 排餐模式代码（SCHEDULE/DAILY/WEEKEND/WEEKDAY）
     * @return 中文标签
     */
    private String mapScheduleModeToChinese(String scheduleMode) {
        if (scheduleMode == null) {
            return "-";
        }
        switch (scheduleMode) {
            case "SCHEDULE": return "指定日期";
            case "DAILY": return "每天";
            case "WEEKEND": return "周末";
            case "WEEKDAY": return "工作日";
            default: return "-";
        }
    }

    private String getCurrentUsername() {
        try {
            return SecurityUtils.getCurrentUsername();
        } catch (Exception e) {
            return "system";
        }
    }

    /**
     * 将菜品ID列表转换为菜品名称列表
     * @param dishIds 排除菜品ID列表
     * @return 排除菜品名称列表
     */
    private List<String> convertDishIdsToNames(List<Integer> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return null;
        }
        List<me.zhengjie.modules.meal.domain.Dish> dishes = dishService.listByIds(dishIds);
        if (dishes == null || dishes.isEmpty()) {
            return null;
        }
        Map<Integer, String> dishNameMap = dishes.stream()
            .collect(Collectors.toMap(
                me.zhengjie.modules.meal.domain.Dish::getId,
                me.zhengjie.modules.meal.domain.Dish::getName,
                (existing, replacement) -> existing
            ));
        return dishIds.stream()
            .map(dishNameMap::get)
            .collect(Collectors.toList());
    }
}
