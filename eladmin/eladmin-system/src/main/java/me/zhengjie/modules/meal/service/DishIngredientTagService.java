package me.zhengjie.modules.meal.service;

import me.zhengjie.modules.meal.domain.DishIngredientTag;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagDto;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagSaveDto;
import me.zhengjie.utils.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 配料标签字典及配料标签关联服务。
 */
public interface DishIngredientTagService {

    /**
     * 按名称分页查询标签。
     * @param criteria 查询条件和分页参数
     * @return 标签列表及总数
     */
    PageResult<DishIngredientTag> query(DishIngredientTagQueryCriteria criteria);

    /**
     * 创建标签并返回数据库生成的标签ID。
     * @param resources 新标签名称
     * @return 已保存标签
     */
    DishIngredientTag create(DishIngredientTagSaveDto resources);

    /**
     * 修改标签名称。
     * @param resources 标签ID和新名称
     */
    void update(DishIngredientTagSaveDto resources);

    /**
     * 删除未被任何配料使用的标签。
     * @param id 标签ID
     */
    void delete(Integer id);

    /**
     * 替换指定配料的全部标签关系；标签按ID升序加锁后校验并写入。
     * @param ingredientId 配料ID
     * @param tagIds 目标标签ID集合，空集合表示清空
     */
    void replaceIngredientTags(Integer ingredientId, List<Integer> tagIds);

    /**
     * 按配料ID批量读取标签，供配料列表、详情和菜品配料查询回填。
     * @param ingredientIds 配料ID集合
     * @return 配料ID到标签详情列表的映射，无标签配料不出现在映射中
     */
    Map<Integer, List<DishIngredientTagDto>> findTagsByIngredientIds(List<Integer> ingredientIds);

    /**
     * 清理指定配料的标签关系；仅删除传入配料ID对应的记录。
     * @param ingredientIds 配料ID集合
     */
    void deleteIngredientRelations(List<Integer> ingredientIds);
}
