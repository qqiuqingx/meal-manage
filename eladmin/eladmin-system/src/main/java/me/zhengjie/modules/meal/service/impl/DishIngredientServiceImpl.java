package me.zhengjie.modules.meal.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.domain.DishIngredientCategory;
import me.zhengjie.modules.meal.domain.dto.DishIngredientQueryCriteria;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.service.DishIngredientCategoryService;
import me.zhengjie.modules.meal.service.DishIngredientService;
import me.zhengjie.utils.FileUtil;
import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
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
    private final DishIngredientCategoryService categoryService;

    private static final Map<String, String> CATEGORY_LEGACY_MAP = new LinkedHashMap<>();
    static {
        CATEGORY_LEGACY_MAP.put("肉类", "MEAT");
        CATEGORY_LEGACY_MAP.put("蔬菜", "VEGETABLE");
        CATEGORY_LEGACY_MAP.put("水产", "SEAFOOD");
        CATEGORY_LEGACY_MAP.put("豆制品", "TOFU");
        CATEGORY_LEGACY_MAP.put("调料", "SPICE");
        CATEGORY_LEGACY_MAP.put("主食", "OTHER");
        CATEGORY_LEGACY_MAP.put("其他", "OTHER");
    }

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
        resolveCategory(resources);
        resources.setCreateTime(new Timestamp(System.currentTimeMillis()));
        save(resources);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DishIngredient resources) {
        DishIngredient existing = getById(resources.getId());
        existing.copy(resources);
        resolveCategory(existing);
        existing.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        updateById(existing);
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
            map.put("一级分类", dishIngredient.getParentCategoryName());
            map.put("二级分类", dishIngredient.getCategoryName());
            map.put("分类路径", dishIngredient.getCategoryPathName());
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

    /**
     * 解析分类信息：根据 parentCategoryName/categoryName 自动创建分类并设置 categoryId
     */
    private void resolveCategory(DishIngredient resources) {
        String parentCategoryName = resources.getParentCategoryName();
        String categoryName = resources.getCategoryName();

        if (resources.getCategoryId() != null && parentCategoryName == null && categoryName == null) {
            // 已有 categoryId，无需通过名称解析
            fillLegacyCategory(resources);
            return;
        }

        if (parentCategoryName == null && categoryName == null) {
            return;
        }

        // 1. 解析/创建一级分类
        DishIngredientCategory parentCategory = null;
        if (parentCategoryName != null) {
            parentCategory = categoryService.findByNameAndLevel(parentCategoryName, 1, null);
            if (parentCategory == null) {
                DishIngredientCategory newParent = new DishIngredientCategory();
                newParent.setName(parentCategoryName);
                newParent.setLevel(1);
                parentCategory = categoryService.create(newParent);
            }
        }

        // 2. 解析/创建二级分类
        if (categoryName != null) {
            Integer parentId = parentCategory != null ? parentCategory.getId() : null;
            DishIngredientCategory category = categoryService.findByNameAndLevel(categoryName, 2, parentId);
            if (category == null) {
                DishIngredientCategory newCategory = new DishIngredientCategory();
                newCategory.setName(categoryName);
                newCategory.setLevel(2);
                newCategory.setParentId(parentId);
                category = categoryService.create(newCategory);
            }
            resources.setCategoryId(category.getId());
        }

        fillLegacyCategory(resources);
    }

    /**
     * 根据一级分类名称反写旧 category 字段
     */
    private void fillLegacyCategory(DishIngredient resources) {
        if (resources.getCategoryId() != null && resources.getCategory() == null) {
            DishIngredientCategory cat = categoryService.getById(resources.getCategoryId());
            if (cat != null && cat.getParentId() != null) {
                DishIngredientCategory parent = categoryService.getById(cat.getParentId());
                if (parent != null) {
                    String legacy = CATEGORY_LEGACY_MAP.getOrDefault(parent.getName(), "OTHER");
                    resources.setCategory(legacy);
                }
            }
        }
    }
}
