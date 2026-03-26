package me.zhengjie.modules.customer.profile.service.impl;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.profile.domain.CustomerPackageCategory;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerPackageCategoryQueryCriteria;
import me.zhengjie.modules.customer.profile.mapper.CustomerPackageCategoryMapper;
import me.zhengjie.modules.customer.profile.service.CustomerPackageCategoryService;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 套餐分类服务实现
 */
@Service
public class CustomerPackageCategoryServiceImpl implements CustomerPackageCategoryService {

    @Autowired
    private CustomerPackageCategoryMapper categoryMapper;

    @Override
    public PageResult<CustomerPackageCategory> query(CustomerPackageCategoryQueryCriteria criteria, Integer current, Integer size) {
        List<CustomerPackageCategory> allCategories = categoryMapper.findAllOrderBySort();

        // 过滤查询条件
        if (criteria != null) {
            allCategories = allCategories.stream()
                .filter(c -> criteria.getCategoryName() == null || c.getCategoryName().contains(criteria.getCategoryName()))
                .filter(c -> criteria.getCategoryCode() == null || c.getCategoryCode().contains(criteria.getCategoryCode()))
                .filter(c -> criteria.getParentId() == null || Objects.equals(c.getParentId(), criteria.getParentId()))
                .filter(c -> criteria.getLevel() == null || c.getLevel().equals(criteria.getLevel()))
                .filter(c -> criteria.getEnabled() == null || c.getEnabled().equals(criteria.getEnabled()))
                .collect(Collectors.toList());
        }

        // 分页处理
        int total = allCategories.size();
        int fromIndex = (current - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);

        if (fromIndex >= total) {
            return new PageResult<>(new ArrayList<>(), total);
        }

        List<CustomerPackageCategory> pageList = allCategories.subList(fromIndex, toIndex);
        return new PageResult<>(pageList, total);
    }

    @Override
    public List<CustomerPackageCategory> getTree() {
        List<CustomerPackageCategory> allCategories = categoryMapper.findAllOrderBySort();
        return buildTree(allCategories);
    }

    @Override
    public List<CustomerPackageCategory> getParents() {
        return categoryMapper.findEnabledParents();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(CustomerPackageCategory category) {
        validateCategory(category);

        // 检查重复前缀
        if (category.getLevel() == 1 && StringUtils.isNotBlank(category.getCodePrefix())) {
            if (categoryMapper.existsEnabledParentPrefix(category.getCodePrefix(), null)) {
                throw new BadRequestException("编号前缀 " + category.getCodePrefix() + " 已存在");
            }
        }

        // 检查子级父级启用状态
        if (category.getLevel() == 2 && category.getParentId() != null) {
            CustomerPackageCategory parent = categoryMapper.selectById(category.getParentId());
            if (parent == null) {
                throw new BadRequestException("父级套餐不存在");
            }
            if (!parent.getEnabled()) {
                throw new BadRequestException("父级套餐已禁用，无法添加子级");
            }
        }

        categoryMapper.insert(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerPackageCategory category) {
        validateCategory(category);

        // 检查重复前缀(排除自身)
        if (category.getLevel() == 1 && StringUtils.isNotBlank(category.getCodePrefix())) {
            if (categoryMapper.existsEnabledParentPrefix(category.getCodePrefix(), category.getId())) {
                throw new BadRequestException("编号前缀 " + category.getCodePrefix() + " 已存在");
            }
        }

        categoryMapper.updateById(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Boolean enabled) {
        CustomerPackageCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BadRequestException("分类不存在");
        }

        // 停用父级前，需要先停用所有子级
        if (!enabled && category.getLevel() == 1) {
            int childCount = categoryMapper.countChildren(id);
            if (childCount > 0) {
                // 检查是否有启用的子级
                List<CustomerPackageCategory> children = categoryMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CustomerPackageCategory>()
                        .eq("parent_id", id)
                        .eq("enabled", true)
                );
                if (!children.isEmpty()) {
                    throw new BadRequestException("请先停用所有子级分类");
                }
            }
        }

        category.setEnabled(enabled);
        categoryMapper.updateById(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CustomerPackageCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BadRequestException("分类不存在");
        }

        // 检查是否有子级
        int childCount = categoryMapper.countChildren(id);
        if (childCount > 0) {
            throw new BadRequestException("该分类下存在子分类，无法删除");
        }

        // 检查是否被客户引用
        int refCount = categoryMapper.countCustomerReferences(id);
        if (refCount > 0) {
            throw new BadRequestException("该分类已被客户引用，无法删除");
        }

        categoryMapper.deleteById(id);
    }

    @Override
    public CustomerPackageCategory findById(Long id) {
        return categoryMapper.selectById(id);
    }

    private void validateCategory(CustomerPackageCategory category) {
        if (StringUtils.isBlank(category.getCategoryName())) {
            throw new BadRequestException("分类名称不能为空");
        }
        if (StringUtils.isBlank(category.getCategoryCode())) {
            throw new BadRequestException("分类编码不能为空");
        }

        // 父级必须有前缀
        if (category.getLevel() == 1) {
            if (StringUtils.isBlank(category.getCodePrefix())) {
                throw new BadRequestException("父级套餐必须设置编号前缀");
            }
            if (category.getCodePrefix().length() != 1 || !Character.isUpperCase(category.getCodePrefix().charAt(0))) {
                throw new BadRequestException("编号前缀必须为单个大写英文字母");
            }
        }

        // 子级不能有前缀
        if (category.getLevel() == 2 && StringUtils.isNotBlank(category.getCodePrefix())) {
            throw new BadRequestException("子级套餐不能设置编号前缀");
        }
    }

    /**
     * 构建树形结构
     */
    private List<CustomerPackageCategory> buildTree(List<CustomerPackageCategory> categories) {
        // 找出所有根节点
        List<CustomerPackageCategory> roots = categories.stream()
            .filter(c -> c.getParentId() == null)
            .collect(Collectors.toList());

        // 递归构建子树
        for (CustomerPackageCategory root : roots) {
            buildChildren(root, categories);
        }

        return roots;
    }

    private void buildChildren(CustomerPackageCategory parent, List<CustomerPackageCategory> allCategories) {
        List<CustomerPackageCategory> children = allCategories.stream()
            .filter(c -> parent.getId().equals(c.getParentId()))
            .collect(Collectors.toList());

        for (CustomerPackageCategory child : children) {
            buildChildren(child, allCategories);
        }

        parent.setChildren(children);
    }
}