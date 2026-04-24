package me.zhengjie.modules.meal.service;

import me.zhengjie.modules.meal.domain.DishIngredientCategory;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配料分类服务接口
 * @author qqx
 * @date 2026-04-24
 **/
public interface DishIngredientCategoryService extends IService<DishIngredientCategory> {

    /**
     * 获取分类树结构
     * @return 分类树
     */
    List<DishIngredientCategory> tree();

    /**
     * 获取所有启用的分类列表
     * @return 分类列表
     */
    List<DishIngredientCategory> listEnabled();

    /**
     * 根据父分类ID获取子分类
     * @param parentId 父分类ID
     * @return 子分类列表
     */
    List<DishIngredientCategory> listByParentId(Integer parentId);

    /**
     * 根据名称和层级查找分类
     * @param name 分类名称
     * @param level 层级
     * @param parentId 父分类ID
     * @return 分类
     */
    DishIngredientCategory findByNameAndLevel(String name, int level, Integer parentId);

    /**
     * 创建分类（自动分配排序）
     * @param category 分类
     * @return 创建后的分类
     */
    DishIngredientCategory create(DishIngredientCategory category);

    /**
     * 删除分类（带校验）
     * @param id 分类ID
     */
    void delete(Integer id);

    /**
     * 获取分类名称→配料名称集合的映射（包含一级和二级分类）
     * 用于过敏词展开：输入分类名可查到该分类下所有配料名
     * @return Map: 分类名称 → 配料名称集合
     */
    Map<String, Set<String>> getCategoryIngredientMapping();
}
