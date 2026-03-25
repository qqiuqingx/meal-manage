package me.zhengjie.modules.customer.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.profile.domain.*;
import me.zhengjie.modules.customer.profile.domain.dto.*;
import me.zhengjie.modules.customer.profile.mapper.*;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.customer.profile.service.CustomerPackageCategoryService;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.SecurityUtils;
import me.zhengjie.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 客户档案服务实现
 */
@Service
public class CustomerProfileServiceImpl implements CustomerProfileService {

    @Autowired
    private CustomerProfileMapper profileMapper;

    @Autowired
    private CustomerProfileAddressMapper addressMapper;

    @Autowired
    private CustomerProfilePackageMapper packageMapper;

    @Autowired
    private CustomerPackageCategoryMapper categoryMapper;

    @Autowired
    private CustomerPackageCategoryService categoryService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public PageResult<CustomerProfile> query(CustomerProfileQueryCriteria criteria, Integer current, Integer size) {
        IPage<CustomerProfile> page = new Page<>(current, size);
        List<CustomerProfile> list = profileMapper.findAll(criteria);

        // 分页处理
        int total = list.size();
        int fromIndex = (current - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);

        if (fromIndex >= total) {
            return new PageResult<>(new ArrayList<>(), total);
        }

        List<CustomerProfile> pageList = list.subList(fromIndex, toIndex);

        // 填充套餐名称
        for (CustomerProfile profile : pageList) {
            fillPackageNames(profile);
        }

        return new PageResult<>(pageList, total);
    }

    @Override
    public CustomerProfileDetailDto getDetail(Long id) {
        CustomerProfile profile = profileMapper.selectById(id);
        if (profile == null) {
            throw new BadRequestException("客户档案不存在");
        }

        // 查询地址列表
        List<CustomerProfileAddress> addresses = addressMapper.selectList(
            new QueryWrapper<CustomerProfileAddress>().eq("customer_id", id)
        );

        // 查询当前签约
        CustomerProfilePackage pkg = packageMapper.findActiveByCustomerId(id);

        // 构建详情 DTO
        CustomerProfileDetailDto detail = new CustomerProfileDetailDto();
        detail.setId(profile.getId());
        detail.setCustomerCode(profile.getCustomerCode());
        detail.setCustomerName(profile.getCustomerName());
        detail.setPhone(profile.getPhone());
        detail.setGestationalWeek(profile.getGestationalWeek());
        detail.setAllergyTags(profile.getAllergyTags());
        detail.setMedicalRequirements(profile.getMedicalRequirements());
        detail.setStatus(profile.getStatus());
        detail.setRemark(profile.getRemark());
        detail.setCreateTime(profile.getCreateTime() != null ? profile.getCreateTime().toLocalDate() : null);
        detail.setUpdateTime(profile.getUpdateTime() != null ? profile.getUpdateTime().toLocalDate() : null);

        // 转换地址
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

        // 转换套餐信息
        if (pkg != null) {
            CustomerProfileDetailDto.PackageInfoDto packageDto = new CustomerProfileDetailDto.PackageInfoDto();
            packageDto.setParentPackageId(pkg.getParentPackageId());
            packageDto.setChildPackageId(pkg.getChildPackageId());
            packageDto.setBreakfastCount(pkg.getBreakfastCount());
            packageDto.setLunchDinnerCount(pkg.getLunchDinnerCount());
            packageDto.setTotalCount(pkg.getTotalCount());
            packageDto.setStartDate(pkg.getStartDate() != null ? pkg.getStartDate().format(DATE_FORMATTER) : null);
            packageDto.setEndDate(pkg.getEndDate() != null ? pkg.getEndDate().format(DATE_FORMATTER) : null);
            packageDto.setActiveFlag(pkg.getActiveFlag());

            // 填充套餐名称
            CustomerPackageCategory parent = categoryMapper.selectById(pkg.getParentPackageId());
            CustomerPackageCategory child = categoryMapper.selectById(pkg.getChildPackageId());
            if (parent != null) {
                packageDto.setParentPackageName(parent.getCategoryName());
            }
            if (child != null) {
                packageDto.setChildPackageName(child.getCategoryName());
            }

            detail.setPackageInfo(packageDto);
        }

        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(CustomerProfileSaveDto dto) {
        normalizeAndValidate(dto, null);

        // 创建主档
        CustomerProfile profile = new CustomerProfile();
        profile.setCustomerCode(dto.getCustomerCode());
        profile.setCustomerName(dto.getCustomerName());
        profile.setPhone(dto.getPhone());
        profile.setGestationalWeek(dto.getGestationalWeek());
        profile.setAllergyTags(dto.getAllergyTags());
        profile.setMedicalRequirements(dto.getMedicalRequirements());
        profile.setStatus(dto.getStatus() != null ? dto.getStatus() : true);
        profile.setRemark(dto.getRemark());
        profile.setCreateBy(getCurrentUsername());

        profileMapper.insert(profile);

        // 保存地址
        saveAddresses(profile.getId(), dto.getAddresses(), dto.getStatus());

        // 保存套餐
        savePackage(profile.getId(), dto.getPackageInfo(), dto.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerProfileSaveDto dto) {
        if (dto.getId() == null) {
            throw new BadRequestException("客户ID不能为空");
        }

        normalizeAndValidate(dto, dto.getId());

        // 更新主档
        CustomerProfile profile = profileMapper.selectById(dto.getId());
        if (profile == null) {
            throw new BadRequestException("客户档案不存在");
        }

        profile.setCustomerCode(dto.getCustomerCode());
        profile.setCustomerName(dto.getCustomerName());
        profile.setPhone(dto.getPhone());
        profile.setGestationalWeek(dto.getGestationalWeek());
        profile.setAllergyTags(dto.getAllergyTags());
        profile.setMedicalRequirements(dto.getMedicalRequirements());
        profile.setStatus(dto.getStatus());
        profile.setRemark(dto.getRemark());
        profile.setUpdateBy(getCurrentUsername());

        profileMapper.updateById(profile);

        // 全量覆盖地址
        updateAddresses(profile.getId(), dto.getAddresses(), dto.getStatus());

        // 全量覆盖套餐
        updatePackage(profile.getId(), dto.getPackageInfo(), dto.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, CustomerProfileStatusRequestDto dto) {
        CustomerProfile profile = profileMapper.selectById(id);
        if (profile == null) {
            throw new BadRequestException("客户档案不存在");
        }

        if (dto.getStatus()) {
            // 启用客户，必须同时提供有效的套餐信息
            if (dto.getPackageInfo() == null) {
                throw new BadRequestException("启用客户时必须提供套餐信息");
            }
            validatePackageInfo(dto.getPackageInfo());

            // 更新主档状态
            profile.setStatus(true);
            profile.setUpdateBy(getCurrentUsername());
            profileMapper.updateById(profile);

            // 更新或创建套餐记录
            updatePackage(id, dto.getPackageInfo(), true);
        } else {
            // 停用客户，同步失效套餐
            profile.setStatus(false);
            profile.setUpdateBy(getCurrentUsername());
            profileMapper.updateById(profile);

            // 失效当前套餐
            CustomerProfilePackage pkg = packageMapper.findActiveByCustomerId(id);
            if (pkg != null) {
                pkg.setActiveFlag(false);
                packageMapper.updateById(pkg);
            }
        }
    }

    @Override
    public String generateCode(Long parentPackageId) {
        if (parentPackageId == null) {
            throw new BadRequestException("父套餐ID不能为空");
        }

        CustomerPackageCategory parent = categoryMapper.selectById(parentPackageId);
        if (parent == null) {
            throw new BadRequestException("父套餐不存在");
        }

        if (!parent.getEnabled()) {
            throw new BadRequestException("父套餐已禁用");
        }

        if (StringUtils.isBlank(parent.getCodePrefix())) {
            throw new BadRequestException("父套餐未配置编号前缀");
        }

        // 查询当前最大编号
        QueryWrapper<CustomerProfile> wrapper = new QueryWrapper<>();
        wrapper.likeRight("customer_code", parent.getCodePrefix())
            .orderByDesc("customer_code")
            .last("LIMIT 1");

        CustomerProfile lastProfile = profileMapper.selectOne(wrapper);

        int nextNum = 1;
        if (lastProfile != null) {
            String code = lastProfile.getCustomerCode();
            // 提取数字部分
            String numPart = code.substring(parent.getCodePrefix().length());
            try {
                nextNum = Integer.parseInt(numPart) + 1;
            } catch (NumberFormatException e) {
                // 如果解析失败，从1开始
            }
        }

        return parent.getCodePrefix() + String.format("%03d", nextNum);
    }

    /**
     * 规范化并校验数据
     */
    private void normalizeAndValidate(CustomerProfileSaveDto dto, Long excludeId) {
        // 编号校验
        if (StringUtils.isBlank(dto.getCustomerCode())) {
            throw new BadRequestException("客户编号不能为空");
        }

        // 编号格式: 父套餐前缀+三位数字
        if (!dto.getCustomerCode().matches("^[A-Z]\\d{3}$")) {
            throw new BadRequestException("客户编号格式错误，应为字母+3位数字(如A001)");
        }

        // 校验编号唯一性
        int count = profileMapper.countByCodeExcludeId(dto.getCustomerCode(), excludeId);
        if (count > 0) {
            throw new BadRequestException("客户编号已存在");
        }

        // 姓名校验
        if (StringUtils.isBlank(dto.getCustomerName())) {
            throw new BadRequestException("客户姓名不能为空");
        }

        // 手机号校验
        if (StringUtils.isBlank(dto.getPhone())) {
            throw new BadRequestException("手机号不能为空");
        }

        // 简单的手机号校验
        if (!dto.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new BadRequestException("手机号格式不正确");
        }

        // 孕周校验
        if (dto.getGestationalWeek() != null && dto.getGestationalWeek() <= 0) {
            throw new BadRequestException("孕周必须为正整数");
        }

        // 地址校验
        if (dto.getAddresses() == null || dto.getAddresses().isEmpty()) {
            throw new BadRequestException("地址信息不能为空");
        }

        // 检查至少有一个非空地址
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

        // 校验地址类型
        for (CustomerProfileSaveDto.AddressDto addr : dto.getAddresses()) {
            if (StringUtils.isNotBlank(addr.getAddressType())) {
                if (!addr.getAddressType().equals("DEFAULT") &&
                    !addr.getAddressType().equals("WORKDAY") &&
                    !addr.getAddressType().equals("WEEKEND")) {
                    throw new BadRequestException("地址类型必须是 DEFAULT、WORKDAY 或 WEEKEND");
                }
            }
        }

        // 套餐信息校验
        if (dto.getPackageInfo() == null) {
            throw new BadRequestException("套餐信息不能为空");
        }

        validatePackageInfo(dto.getPackageInfo());

        // 过敏标签规范化
        if (dto.getAllergyTags() != null && !dto.getAllergyTags().isEmpty()) {
            // 去重、trim
            List<String> normalizedTags = dto.getAllergyTags().stream()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
            dto.setAllergyTags(normalizedTags);
        }
    }

    private void validatePackageInfo(CustomerProfileSaveDto.PackageInfoDto pkg) {
        if (pkg.getParentPackageId() == null) {
            throw new BadRequestException("父套餐不能为空");
        }

        if (pkg.getChildPackageId() == null) {
            throw new BadRequestException("子套餐不能为空");
        }

        // 校验子套餐属于父套餐
        CustomerPackageCategory child = categoryMapper.selectById(pkg.getChildPackageId());
        if (child == null) {
            throw new BadRequestException("子套餐不存在");
        }
        if (!pkg.getParentPackageId().equals(child.getParentId())) {
            throw new BadRequestException("子套餐必须属于所选父套餐");
        }

        // 餐数校验
        if (pkg.getBreakfastCount() == null && pkg.getLunchDinnerCount() == null) {
            throw new BadRequestException("早餐数与午餐+晚餐数至少填写一个");
        }

        // 日期校验
        if (StringUtils.isBlank(pkg.getStartDate()) || StringUtils.isBlank(pkg.getEndDate())) {
            throw new BadRequestException("签约开始日期和结束日期不能为空");
        }

        LocalDate startDate = LocalDate.parse(pkg.getStartDate(), DATE_FORMATTER);
        LocalDate endDate = LocalDate.parse(pkg.getEndDate(), DATE_FORMATTER);

        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("签约结束日期不能早于开始日期");
        }

        // 计算 totalCount
        int totalCount = (pkg.getBreakfastCount() != null ? pkg.getBreakfastCount() : 0)
            + (pkg.getLunchDinnerCount() != null ? pkg.getLunchDinnerCount() : 0);
        pkg.setTotalCount(totalCount);
    }

    private void saveAddresses(Long customerId, List<CustomerProfileSaveDto.AddressDto> addresses, Boolean status) {
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

    private void updateAddresses(Long customerId, List<CustomerProfileSaveDto.AddressDto> addresses, Boolean status) {
        // 删除旧地址
        addressMapper.delete(new QueryWrapper<CustomerProfileAddress>().eq("customer_id", customerId));

        // 保存新地址
        saveAddresses(customerId, addresses, status);
    }

    private void savePackage(Long customerId, CustomerProfileSaveDto.PackageInfoDto pkgInfo, Boolean status) {
        if (pkgInfo == null) {
            return;
        }

        CustomerProfilePackage pkg = new CustomerProfilePackage();
        pkg.setCustomerId(customerId);
        pkg.setParentPackageId(pkgInfo.getParentPackageId());
        pkg.setChildPackageId(pkgInfo.getChildPackageId());
        pkg.setBreakfastCount(pkgInfo.getBreakfastCount());
        pkg.setLunchDinnerCount(pkgInfo.getLunchDinnerCount());
        pkg.setTotalCount(pkgInfo.getTotalCount());
        pkg.setStartDate(LocalDate.parse(pkgInfo.getStartDate(), DATE_FORMATTER));
        pkg.setEndDate(LocalDate.parse(pkgInfo.getEndDate(), DATE_FORMATTER));
        pkg.setActiveFlag(status != null && status);

        packageMapper.insert(pkg);
    }

    private void updatePackage(Long customerId, CustomerProfileSaveDto.PackageInfoDto pkgInfo, Boolean status) {
        // 先失效旧套餐
        CustomerProfilePackage oldPkg = packageMapper.findActiveByCustomerId(customerId);
        if (oldPkg != null) {
            oldPkg.setActiveFlag(false);
            packageMapper.updateById(oldPkg);
        }

        // 创建新套餐
        if (pkgInfo != null) {
            savePackage(customerId, pkgInfo, status);
        }
    }

    private void fillPackageNames(CustomerProfile profile) {
        // 从 package 表获取套餐信息
        CustomerProfilePackage pkg = packageMapper.findActiveByCustomerId(profile.getId());
        if (pkg != null) {
            CustomerPackageCategory parent = categoryMapper.selectById(pkg.getParentPackageId());
            CustomerPackageCategory child = categoryMapper.selectById(pkg.getChildPackageId());

            if (parent != null) {
                profile.setParentPackageName(parent.getCategoryName());
            }
            if (child != null) {
                profile.setChildPackageName(child.getCategoryName());
            }

            profile.setBreakfastCount(pkg.getBreakfastCount());
            profile.setLunchDinnerCount(pkg.getLunchDinnerCount());
            profile.setTotalCount(pkg.getTotalCount());
            profile.setStartDate(pkg.getStartDate() != null ? pkg.getStartDate().format(DATE_FORMATTER) : null);
            profile.setEndDate(pkg.getEndDate() != null ? pkg.getEndDate().format(DATE_FORMATTER) : null);
        }

        // 从 address 表获取默认地址
        List<CustomerProfileAddress> addresses = addressMapper.selectList(
            new QueryWrapper<CustomerProfileAddress>()
                .eq("customer_id", profile.getId())
                .orderByAsc("address_type")
        );

        for (CustomerProfileAddress addr : addresses) {
            if ("DEFAULT".equals(addr.getAddressType())) {
                profile.setDefaultAddress(addr.getAddressDetail());
                break;
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