package me.zhengjie.modules.customer.profile.service;

import me.zhengjie.modules.customer.profile.domain.CustomerPackageCategory;

import java.util.List;

/**
 * 套餐分类服务接口
 */
public interface CustomerPackageCategoryService {

    /**
     * 获取分类树形结构
     */
    List<CustomerPackageCategory> getTree();

    /**
     * 获取父级分类列表(仅启用状态)
     */
    List<CustomerPackageCategory> getParents();

    /**
     * 创建分类
     */
    void create(CustomerPackageCategory category);

    /**
     * 更新分类
     */
    void update(CustomerPackageCategory category);

    /**
     * 更新分类状态
     */
    void updateStatus(Long id, Boolean enabled);

    /**
     * 删除分类
     */
    void delete(Long id);

    /**
     * 根据ID查询分类
     */
    CustomerPackageCategory findById(Long id);
}