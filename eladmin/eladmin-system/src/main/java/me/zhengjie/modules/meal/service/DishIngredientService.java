package me.zhengjie.modules.meal.service;

import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.domain.dto.DishIngredientQueryCriteria;
import java.util.List;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import me.zhengjie.utils.PageResult;

/**
 * 配料服务接口
 * @author qqx
 * @date 2026-03-15
 **/
public interface DishIngredientService extends IService<DishIngredient> {

    /**
     * 查询数据分页
     * @param criteria 条件
     * @param page 分页参数
     * @return PageResult
     */
    PageResult<DishIngredient> queryAll(DishIngredientQueryCriteria criteria, Page<Object> page);

    /**
     * 查询所有数据不分页
     * @param criteria 条件参数
     * @return List<DishIngredient>
     */
    List<DishIngredient> queryAll(DishIngredientQueryCriteria criteria);

    /**
     * 根据ID查询
     * @param id /
     * @return DishIngredient
     */
    DishIngredient findById(Integer id);

    /**
     * 根据菜品ID查询配料列表
     * @param dishId 菜品ID
     * @return List<DishIngredient>
     */
    List<DishIngredient> findByDishId(Integer dishId);

    /**
     * 创建
     * @param resources /
     */
    void create(DishIngredient resources);

    /**
     * 编辑
     * @param resources /
     */
    void update(DishIngredient resources);

    /**
     * 删除
     * @param ids /
     */
    void delete(List<Integer> ids);

    /**
     * 导出数据
     * @param all 待导出的数据
     * @param response /
     * @throws IOException /
     */
    void download(List<DishIngredient> all, HttpServletResponse response) throws IOException;
}
