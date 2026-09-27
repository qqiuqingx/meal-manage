package me.zhengjie.modules.meal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import me.zhengjie.modules.meal.domain.DishIngredientTag;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagQueryCriteria;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 配料标签字典 Mapper。
 */
@Mapper
public interface DishIngredientTagMapper extends BaseMapper<DishIngredientTag> {

    /**
     * 按标签名称分页查询。
     * @param page 分页参数
     * @param criteria 名称查询条件
     * @return 当前页标签及总数
     */
    IPage<DishIngredientTag> selectPageByCriteria(
        IPage<DishIngredientTag> page,
        @Param("criteria") DishIngredientTagQueryCriteria criteria
    );

    /**
     * 按标签ID加行锁，串行化标签改名、删除和配料绑定。
     * @param id 标签ID
     * @return 标签不存在时返回 null
     */
    DishIngredientTag selectByIdForUpdate(@Param("id") Integer id);

    /**
     * 统计指定标签当前被多少配料引用。
     * @param tagId 标签ID
     * @return 关联记录数
     */
    long countRelationsByTagId(@Param("tagId") Integer tagId);
}
