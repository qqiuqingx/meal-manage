package me.zhengjie.modules.customer.order.service.impl;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderQueryCriteria;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderSaveDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.domain.SubPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.SecurityUtils;
import me.zhengjie.utils.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 客户订单服务实现
 */
@Service
public class CustomerOrderServiceImpl implements CustomerOrderService {

    @Autowired
    private CustomerOrderMapper orderMapper;

    @Autowired
    private CustomerProfileMapper profileMapper;

    @Autowired
    private ParentPackageMapper parentPackageMapper;

    @Autowired
    private SubPackageMapper subPackageMapper;

    private static final DateTimeFormatter ORDER_CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public PageResult<?> query(CustomerOrderQueryCriteria criteria, Integer current, Integer size) {
        Page<CustomerOrder> page = new Page<>(current, size);
        List<CustomerOrder> list = orderMapper.findAll(criteria, page);

        // 计算合计餐数字段
        for (CustomerOrder order : list) {
            int breakfast = order.getBreakfastCount() != null ? order.getBreakfastCount() : 0;
            int lunchDinner = order.getLunchDinnerCount() != null ? order.getLunchDinnerCount() : 0;
            order.setTotalCount(breakfast + lunchDinner);
        }

        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public CustomerOrderDetailDto getDetail(Long id) {
        CustomerOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }

        CustomerProfile profile = profileMapper.selectById(order.getCustomerId());
        CustomerOrderDetailDto dto = buildDetailDto(order, profile);
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(CustomerOrderSaveDto dto) {
        // 先校验订单冲突
        validateOrderConflict(dto, null);
        validateAndNormalize(dto, null);

        // 获取客户信息，用于设置客户编号
        CustomerProfile profile = profileMapper.selectById(dto.getCustomerId());
        if (profile == null) {
            throw new BadRequestException("客户不存在");
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            CustomerOrder order = new CustomerOrder();
            buildOrderEntity(order, dto);
            order.setCustomerCode(profile.getCustomerCode());
            order.setOrderCode(generateOrderCode());
            order.setCreateBy(getCurrentUsername());
            try {
                orderMapper.insert(order);
                return;
            } catch (DuplicateKeyException ex) {
                // Concurrent inserts can race on the daily order code; retry with a fresh code.
            }
        }
        throw new BadRequestException("订单编号生成失败，请重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerOrderSaveDto dto) {
        if (dto.getId() == null) {
            throw new BadRequestException("订单ID不能为空");
        }

        CustomerOrder order = orderMapper.selectById(dto.getId());
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }

        // 先校验订单冲突
        validateOrderConflict(dto, dto.getId());
        validateAndNormalize(dto, dto.getId());

        buildOrderEntity(order, dto);
        order.setUpdateBy(getCurrentUsername());

        orderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("请选择要删除的订单");
        }
        orderMapper.deleteBatchIds(ids);
    }

    @Override
    public PageResult<?> getOrdersByCustomerId(Long customerId, Integer current, Integer size) {
        if (customerId == null) {
            throw new BadRequestException("客户ID不能为空");
        }
        CustomerOrderQueryCriteria criteria = new CustomerOrderQueryCriteria();
        criteria.setCustomerId(customerId);
        return query(criteria, current, size);
    }

    /**
     * 生成订单编号: ORD + yyyyMMdd + 3位序号
     */
    private String generateOrderCode() {
        String datePrefix = "ORD" + LocalDate.now().format(ORDER_CODE_DATE);

        String maxCode = orderMapper.findTodayMaxOrderCode(datePrefix);
        int nextNum = 1;
        if (StringUtils.isNotBlank(maxCode) && maxCode.length() > datePrefix.length()) {
            String numPart = maxCode.substring(datePrefix.length());
            try {
                nextNum = Integer.parseInt(numPart) + 1;
            } catch (NumberFormatException e) {
                // 解析失败，从1开始
            }
        }

        return datePrefix + String.format("%03d", nextNum);
    }

    /**
     * 校验并规范化 DTO
     */
    private void validateAndNormalize(CustomerOrderSaveDto dto, Long excludeId) {
        // 客户校验
        if (dto.getCustomerId() == null) {
            throw new BadRequestException("客户不能为空");
        }

        CustomerProfile profile = profileMapper.selectById(dto.getCustomerId());
        if (profile == null) {
            throw new BadRequestException("客户不存在");
        }

        // 金额校验
        if (dto.getTotalAmount() == null) {
            throw new BadRequestException("总金额不能为空");
        }
        if (dto.getFinalAmount() == null) {
            throw new BadRequestException("成交金额不能为空");
        }
        if (dto.getFinalAmount().compareTo(dto.getTotalAmount()) > 0) {
            throw new BadRequestException("成交金额不能超过总金额");
        }

        // 日期校验
        if (dto.getStartDate() != null && dto.getEndDate() != null) {
            if (dto.getEndDate().isBefore(dto.getStartDate())) {
                throw new BadRequestException("订单结束日期不能早于开始日期");
            }
        }

        // 核销数校验
        int totalCount = getTotalCount(dto);
        int verifiedCount = dto.getVerifiedCount() != null ? dto.getVerifiedCount() : 0;
        if (verifiedCount > totalCount) {
            throw new BadRequestException("核销餐数不能超过合计餐数");
        }

        // 核销金额校验
        BigDecimal verifiedAmount = dto.getVerifiedAmount() != null ? dto.getVerifiedAmount() : BigDecimal.ZERO;
        if (verifiedAmount.compareTo(dto.getFinalAmount()) > 0) {
            throw new BadRequestException("核销金额不能超过成交金额");
        }

        // 自动计算餐费余额和剩余餐数
        BigDecimal mealBalance = dto.getFinalAmount().subtract(verifiedAmount);
        dto.setMealBalance(mealBalance);

        int remainingCount = totalCount - verifiedCount;
        dto.setRemainingCount(remainingCount);

        // 餐数默认值
        if (dto.getBreakfastCount() == null) dto.setBreakfastCount(0);
        if (dto.getLunchDinnerCount() == null) dto.setLunchDinnerCount(0);
        if (dto.getDepositAmount() == null) dto.setDepositAmount(BigDecimal.ZERO);
        if (dto.getBreakfastPrice() == null) dto.setBreakfastPrice(BigDecimal.ZERO);
        if (dto.getLunchDinnerPrice() == null) dto.setLunchDinnerPrice(BigDecimal.ZERO);
        if (dto.getVerifiedAmount() == null) dto.setVerifiedAmount(BigDecimal.ZERO);
        if (dto.getVerifiedCount() == null) dto.setVerifiedCount(0);

        // 状态默认值
        if (dto.getStatus() == null) {
            dto.setStatus(1);
        }

        // 餐次类型默认值（兼容老数据）
        if (dto.getMealType() == null) {
            dto.setMealType("ALL");
        }
    }

    /**
     * 构建 Order 实体
     */
    private void buildOrderEntity(CustomerOrder order, CustomerOrderSaveDto dto) {
        order.setCustomerId(dto.getCustomerId());
        order.setParentPackageId(dto.getParentPackageId());
        order.setChildPackageId(dto.getChildPackageId());
        order.setDepositAmount(dto.getDepositAmount());
        order.setTotalAmount(dto.getTotalAmount());
        order.setFinalAmount(dto.getFinalAmount());
        order.setBreakfastCount(dto.getBreakfastCount());
        order.setLunchDinnerCount(dto.getLunchDinnerCount());
        order.setBreakfastPrice(dto.getBreakfastPrice());
        order.setLunchDinnerPrice(dto.getLunchDinnerPrice());
        order.setVerifiedCount(dto.getVerifiedCount());
        order.setVerifiedAmount(dto.getVerifiedAmount());
        order.setMealBalance(dto.getMealBalance());
        order.setRemainingCount(dto.getRemainingCount());
        order.setDealTime(dto.getDealTime());
        order.setFirstDeliveryTime(dto.getFirstDeliveryTime());
        order.setStartDate(dto.getStartDate());
        order.setEndDate(dto.getEndDate());
        order.setStatus(dto.getStatus());
        order.setMealType(dto.getMealType());
        order.setScheduleMode(dto.getScheduleMode());
        order.setDeliveryDates(StringUtils.isNotBlank(dto.getDeliveryDatesWithMealTypes())
                ? dto.getDeliveryDatesWithMealTypes()
                : dto.getDeliveryDates());
        order.setRemark(dto.getRemark());
        order.setCustomerSource(dto.getCustomerSource());
    }

    /**
     * 构建详情 DTO
     */
    private CustomerOrderDetailDto buildDetailDto(CustomerOrder order, CustomerProfile profile) {
        CustomerOrderDetailDto dto = new CustomerOrderDetailDto();
        dto.setId(order.getId());
        dto.setCustomerId(order.getCustomerId());
        if (profile != null) {
            dto.setCustomerName(profile.getCustomerName());
            dto.setPhone(profile.getPhone());
        }

        // 填充套餐名称
        if (order.getParentPackageId() != null) {
            ParentPackage parentPackage = parentPackageMapper.selectById(order.getParentPackageId());
            if (parentPackage != null) {
                dto.setParentPackageName(parentPackage.getPackageName());
            }
        }
        if (order.getChildPackageId() != null) {
            SubPackage childPackage = subPackageMapper.selectById(order.getChildPackageId());
            if (childPackage != null) {
                dto.setChildPackageName(childPackage.getSubPackageName());
            }
        }

        dto.setOrderCode(order.getOrderCode());
        dto.setDepositAmount(order.getDepositAmount());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setFinalAmount(order.getFinalAmount());
        dto.setBreakfastCount(order.getBreakfastCount());
        dto.setLunchDinnerCount(order.getLunchDinnerCount());
        dto.setTotalCount(getTotalCountFromOrder(order));
        dto.setBreakfastPrice(order.getBreakfastPrice());
        dto.setLunchDinnerPrice(order.getLunchDinnerPrice());
        dto.setVerifiedCount(order.getVerifiedCount());
        dto.setVerifiedAmount(order.getVerifiedAmount());
        dto.setMealBalance(order.getMealBalance());
        dto.setRemainingCount(order.getRemainingCount());
        dto.setDealTime(order.getDealTime());
        dto.setFirstDeliveryTime(order.getFirstDeliveryTime());
        dto.setStartDate(order.getStartDate());
        dto.setEndDate(order.getEndDate());
        dto.setStatus(order.getStatus());
        dto.setStatusDesc(getStatusDesc(order.getStatus()));
        dto.setMealType(order.getMealType());
        dto.setMealTypeDesc(getMealTypeDesc(order.getMealType()));
        dto.setScheduleMode(order.getScheduleMode());
        dto.setDeliveryDates(order.getDeliveryDates());
        dto.setRemark(order.getRemark());
        dto.setCustomerSource(order.getCustomerSource());
        dto.setCreateTime(order.getCreateTime());
        dto.setUpdateTime(order.getUpdateTime());
        return dto;
    }

    private int getTotalCount(CustomerOrderSaveDto dto) {
        return (dto.getBreakfastCount() != null ? dto.getBreakfastCount() : 0)
            + (dto.getLunchDinnerCount() != null ? dto.getLunchDinnerCount() : 0);
    }

    private int getTotalCountFromOrder(CustomerOrder order) {
        return (order.getBreakfastCount() != null ? order.getBreakfastCount() : 0)
            + (order.getLunchDinnerCount() != null ? order.getLunchDinnerCount() : 0);
    }

    private String getStatusDesc(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "已取消";
            case 1: return "进行中";
            case 2: return "已完成";
            default: return "未知";
        }
    }

    private String getMealTypeDesc(String mealType) {
        if (mealType == null) return "全餐次";
        switch (mealType) {
            case "LUNCH": return "午餐";
            case "DINNER": return "晚餐";
            case "ALL": return "全餐次";
            default: return "未知";
        }
    }

    @Override
    public void validateOrderConflict(CustomerOrderSaveDto dto, Long excludeId) {
        // 如果没有日期范围，不进行校验
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            return;
        }

        LocalDate startDate = dto.getStartDate();
        LocalDate endDate = dto.getEndDate();

        // 校验规则：
        // 1. 如果是全餐次订单，同一时间段只能有1个订单
        if ("ALL".equals(dto.getMealType())) {
            int count = orderMapper.countAllMealTypeOrders(dto.getCustomerId(), startDate, endDate, excludeId);
            if (count > 0) {
                throw new BadRequestException("同一时间段已存在全餐次订单，不能重复创建");
            }
            return;
        }

        // 2. 如果是午餐或晚餐订单，同一时间段最多2个不同餐次的订单
        if ("LUNCH".equals(dto.getMealType()) || "DINNER".equals(dto.getMealType())) {
            // 先检查同一时间段的总订单数
            int totalCount = orderMapper.countOverlappingOrders(dto.getCustomerId(), startDate, endDate, excludeId);
            if (totalCount >= 2) {
                throw new BadRequestException("同一时间段最多只能有两个不同餐次的订单");
            }

            // 检查是否已存在相同餐次的订单
            int sameTypeCount = orderMapper.countMealTypeOrders(dto.getCustomerId(), startDate, endDate, dto.getMealType(), excludeId);
            if (sameTypeCount > 0) {
                throw new BadRequestException("同一时间段已存在相同餐次的订单");
            }
        }

        // 3. 检查剩余餐数（仅编辑时）
        if (excludeId != null) {
            CustomerOrder existingOrder = orderMapper.selectById(excludeId);
            if (existingOrder != null && existingOrder.getVerifiedCount() != null) {
                int newTotalCount = getTotalCount(dto);
                int existingVerified = existingOrder.getVerifiedCount();
                if (newTotalCount < existingVerified) {
                    throw new BadRequestException("订单餐数不能小于已核销餐数（当前已核销：" + existingVerified + "）");
                }
            }
        }
    }

    private String getCurrentUsername() {
        try {
            return SecurityUtils.getCurrentUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}
