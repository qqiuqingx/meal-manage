package me.zhengjie.modules.meal.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.domain.dto.DishIngredientQueryCriteria;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.service.DishIngredientService;
import me.zhengjie.utils.FileUtil;
import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import me.zhengjie.utils.PageResult;
import java.sql.Timestamp;

/**
 * 配料服务实现
 * @author qqx
 * @date 2026-03-15
 **/
@Service
@RequiredArgsConstructor
public class DishIngredientServiceImpl extends ServiceImpl<DishIngredientMapper, DishIngredient> implements DishIngredientService {

    private final DishIngredientMapper dishIngredientMapper;

    @Override
    public PageResult<DishIngredient> queryAll(DishIngredientQueryCriteria criteria, Page<Object> page){
        Page<DishIngredient> queryPage = new Page<>(page.getCurrent(), page.getSize());
        IPage<DishIngredient> result = dishIngredientMapper.selectPageByCriteria(criteria, queryPage);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public List<DishIngredient> queryAll(DishIngredientQueryCriteria criteria){
        return dishIngredientMapper.selectPageByCriteria(criteria);
    }

    @Override
    public DishIngredient findById(Integer id) {
        List<DishIngredient> list = dishIngredientMapper.findById(id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<DishIngredient> findByDishId(Integer dishId) {
        return dishIngredientMapper.findByDishId(dishId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(DishIngredient resources) {
        resources.setCreateTime(new Timestamp(System.currentTimeMillis()));
        save(resources);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DishIngredient resources) {
        DishIngredient dishIngredient = getById(resources.getId());
        dishIngredient.copy(resources);
        dishIngredient.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        updateById(dishIngredient);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Integer> ids) {
        removeByIds(ids);
    }

    @Override
    public void download(List<DishIngredient> all, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (DishIngredient dishIngredient : all) {
            Map<String,Object> map = new LinkedHashMap<>();
            map.put("配料名称", dishIngredient.getName());
            map.put("分类", dishIngredient.getCategory());
            map.put("单位", dishIngredient.getUnit());
            map.put("热量", dishIngredient.getCalories());
            map.put("备注", dishIngredient.getRemark());
            map.put("是否启用", dishIngredient.getEnabled());
            map.put("创建时间", dishIngredient.getCreateTime());
            map.put("更新时间", dishIngredient.getUpdateTime());
            list.add(map);
        }
        FileUtil.downloadExcel(list, response);
    }
}
