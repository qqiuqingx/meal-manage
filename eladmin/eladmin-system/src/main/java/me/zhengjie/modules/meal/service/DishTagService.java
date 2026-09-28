package me.zhengjie.modules.meal.service;

import me.zhengjie.modules.meal.domain.DishTag;
import me.zhengjie.modules.meal.domain.dto.DishTagDto;
import me.zhengjie.modules.meal.domain.dto.DishTagQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishTagSaveDto;
import me.zhengjie.utils.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 菜品标签查询与菜品关联服务。
 */
public interface DishTagService {

    /**
     * 按名称分页查询菜品标签。
     * @param criteria 名称和分页条件
     * @return 标签分页结果
     */
    PageResult<DishTag> query(DishTagQueryCriteria criteria);

    /**
     * 创建标签并返回数据库生成的ID。
     * @param resources 标签名称
     * @return 已创建标签
     */
    DishTag create(DishTagSaveDto resources);

    /**
     * 锁定标签后更新名称，保留现有菜品关联。
     * @param resources 标签ID和新名称
     */
    void update(DishTagSaveDto resources);

    /**
     * 仅删除没有菜品引用的标签。
     * @param id 标签ID
     */
    void delete(Integer id);

    /**
     * 在当前事务内校验并整体替换指定菜品的标签关系。
     * @param dishId 菜品ID
     * @param tagIds 新标签ID集合，空集合表示清空
     */
    void replaceDishTags(Integer dishId, List<Integer> tagIds);

    /**
     * 按菜品ID批量查询标签，避免菜品分页逐条访问数据库。
     * @param dishIds 菜品ID集合
     * @return 每个有标签菜品对应的标签列表
     */
    Map<Integer, List<DishTagDto>> findTagsByDishIds(List<Integer> dishIds);

    /**
     * 按给定菜品ID集合清理标签关系。
     * @param dishIds 菜品ID集合
     */
    void deleteDishRelations(List<Integer> dishIds);
}
