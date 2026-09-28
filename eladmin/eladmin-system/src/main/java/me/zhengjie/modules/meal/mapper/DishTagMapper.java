package me.zhengjie.modules.meal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import me.zhengjie.modules.meal.domain.DishTag;
import me.zhengjie.modules.meal.domain.dto.DishTagQueryCriteria;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 菜品标签字典 Mapper。
 */
@Mapper
public interface DishTagMapper extends BaseMapper<DishTag> {

    /**
     * 按标签名称分页查询。
     * @param page 分页参数
     * @param criteria 名称查询条件
     * @return 当前页标签及总数
     */
    IPage<DishTag> selectPageByCriteria(
        IPage<DishTag> page,
        @Param("criteria") DishTagQueryCriteria criteria
    );

    /**
     * 按标签ID加行锁，串行化标签字典变更与菜品绑定。
     * @param id 标签ID
     * @return 标签不存在时返回 null
     */
    DishTag selectByIdForUpdate(@Param("id") Integer id);
}
