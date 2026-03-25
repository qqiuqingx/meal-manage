package me.zhengjie.modules.customer.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerPackageCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 套餐分类 Mapper 接口
 */
@Mapper
public interface CustomerPackageCategoryMapper extends BaseMapper<CustomerPackageCategory> {

    /**
     * 查询所有分类(按sort排序)
     */
    List<CustomerPackageCategory> findAllOrderBySort();

    /**
     * 查询启用状态的父级分类
     */
    List<CustomerPackageCategory> findEnabledParents();

    /**
     * 检查是否存在指定前缀的启用父级
     * @param codePrefix 编号前缀
     * @param excludeId 排除的ID(用于更新时检查)
     * @return true if exists
     */
    boolean existsEnabledParentPrefix(@Param("codePrefix") String codePrefix, @Param("excludeId") Long excludeId);

    /**
     * 统计子级数量
     */
    int countChildren(@Param("parentId") Long parentId);

    /**
     * 统计客户引用数量
     */
    int countCustomerReferences(@Param("categoryId") Long categoryId);
}