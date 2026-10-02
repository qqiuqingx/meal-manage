package me.zhengjie.modules.meal.service.impl;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import me.zhengjie.modules.meal.domain.event.DietDictionaryChangedEvent;

import com.baomidou.mybatisplus.core.metadata.IPage;
import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.domain.DishIngredientCategory;
import me.zhengjie.modules.meal.domain.dto.DishIngredientQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagDto;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.service.DishIngredientCategoryService;
import me.zhengjie.modules.meal.service.DishIngredientService;
import me.zhengjie.modules.meal.service.DishIngredientTagService;
import me.zhengjie.utils.FileUtil;
import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.zhengjie.exception.BadRequestException;
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
    private final DishIngredientTagService tagService;
    private final ApplicationEventPublisher events;

    /**
     * 分页查询配料并批量回填每条配料的标签。
     * @param criteria 配料筛选条件
     * @param page 分页参数
     * @return 当前页配料和总数
     */
    @Override
    public PageResult<DishIngredient> queryAll(DishIngredientQueryCriteria criteria, Page<Object> page){
        Page<DishIngredient> queryPage = new Page<>(page.getCurrent(), page.getSize());
        IPage<DishIngredient> result = dishIngredientMapper.selectPageByCriteria(criteria, queryPage);
        fillTags(result.getRecords());
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    /**
     * 查询符合条件的全部配料并批量回填标签。
     * @param criteria 配料筛选条件
     * @return 配料列表
     */
    @Override
    public List<DishIngredient> queryAll(DishIngredientQueryCriteria criteria){
        List<DishIngredient> ingredients = dishIngredientMapper.selectPageByCriteria(criteria);
        fillTags(ingredients);
        return ingredients;
    }

    /**
     * 查询配料详情并附加标签ID和标签名称。
     * @param id 配料ID
     * @return 配料详情；不存在时返回 null
     */
    @Override
    public DishIngredient findById(Integer id) {
        List<DishIngredient> list = dishIngredientMapper.findById(id);
        if (list.isEmpty()) {
            return null;
        }
        fillTags(list);
        return list.get(0);
    }

    /**
     * 按菜品查询配料并批量回填标签。
     * @param dishId 菜品ID
     * @return 菜品关联的配料列表
     */
    @Override
    public List<DishIngredient> findByDishId(Integer dishId) {
        List<DishIngredient> ingredients = dishIngredientMapper.findByDishId(dishId);
        fillTags(ingredients);
        return ingredients;
    }

    /**
     * 字典变化时发布事务内通知，提交成功后自动更新客户禁忌。
     * 新增配料，并在同一事务中保存传入的标签关系。
     * @param resources 新配料；tagIds 未传或为空时不绑定标签
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(DishIngredient resources) {
        resolveCategory(resources);
        resources.setCreateTime(new Timestamp(System.currentTimeMillis()));
        dishIngredientMapper.insert(resources);
        tagService.replaceIngredientTags(resources.getId(), resources.getTagIds());
        events.publishEvent(new DietDictionaryChangedEvent());
    }

    /**
     * 字典变化时发布事务内通知，提交成功后自动更新客户禁忌。
     * 锁定配料行后更新配料，并按 null 保持、空数组清空的契约替换标签；分类归属变化也触发禁忌重匹配。
     * @param resources 配料字段及可选标签ID集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DishIngredient resources) {
        if (resources.getId() == null) {
            throw new BadRequestException("配料ID不能为空");
        }
        DishIngredient existing = dishIngredientMapper.selectByIdForUpdate(resources.getId());
        if (existing == null) {
            throw new BadRequestException("配料不存在");
        }
        List<Integer> requestedTagIds = resources.getTagIds();
        String oldName = existing.getName();
        Boolean oldEnabled = existing.getEnabled();
        Integer oldCategoryId = existing.getCategoryId();
        existing.copy(resources);
        resolveCategory(existing);
        existing.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        dishIngredientMapper.updateById(existing);
        if (requestedTagIds != null) {
            tagService.replaceIngredientTags(existing.getId(), requestedTagIds);
        }
        if (!Objects.equals(oldName, existing.getName()) || !Objects.equals(oldEnabled, existing.getEnabled())
                || !Objects.equals(oldCategoryId, existing.getCategoryId())) {
            events.publishEvent(new DietDictionaryChangedEvent());
        }
    }

    /**
     * 字典变化时发布事务内通知，提交成功后自动更新客户禁忌。
     * 按ID锁定并删除配料，同时限定清理其标签关联。
     * @param ids 待删除配料ID集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Integer> ingredientIds = ids.stream()
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .collect(java.util.stream.Collectors.toList());
        if (ingredientIds.isEmpty()) {
            return;
        }
        for (Integer ingredientId : ingredientIds) {
            dishIngredientMapper.selectByIdForUpdate(ingredientId);
        }
        tagService.deleteIngredientRelations(ingredientIds);
        dishIngredientMapper.deleteBatchIds(ingredientIds);
        events.publishEvent(new DietDictionaryChangedEvent());
    }

    /**
     * 导出当前筛选结果，标签名称按标签ID顺序组成单列。
     * @param all 当前筛选出的配料列表
     * @param response Excel响应
     * @throws IOException 文件写入失败时抛出
     */
    @Override
    public void download(List<DishIngredient> all, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (DishIngredient dishIngredient : all) {
            Map<String,Object> map = new LinkedHashMap<>();
            map.put("配料名称", dishIngredient.getName());
            map.put("一级分类", dishIngredient.getParentCategoryName());
            map.put("二级分类", dishIngredient.getCategoryName());
            map.put("分类路径", dishIngredient.getCategoryPathName());
            String tagNames = dishIngredient.getTags() == null ? "" : dishIngredient.getTags().stream()
                .map(DishIngredientTagDto::getName)
                .collect(java.util.stream.Collectors.joining("、"));
            map.put("标签", tagNames);
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
     * 批量查询并设置配料的标签详情和标签ID，空页不访问关联表。
     * @param ingredients 待回填配料列表
     */
    private void fillTags(List<DishIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return;
        }
        List<Integer> ingredientIds = ingredients.stream()
            .map(DishIngredient::getId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());
        Map<Integer, List<DishIngredientTagDto>> tagsByIngredient = tagService.findTagsByIngredientIds(ingredientIds);
        for (DishIngredient ingredient : ingredients) {
            List<DishIngredientTagDto> tags = tagsByIngredient.get(ingredient.getId());
            if (tags == null) {
                tags = Collections.emptyList();
            }
            ingredient.setTags(tags);
            ingredient.setTagIds(tags.stream().map(DishIngredientTagDto::getId).collect(java.util.stream.Collectors.toList()));
        }
    }

    /**
     * 根据动态分类名称查找或创建分类，并设置配料的二级分类ID。
     * @param resources 配料及可选一级、二级分类名称；仅传分类ID时直接沿用该ID
     */
    private void resolveCategory(DishIngredient resources) {
        String parentCategoryName = resources.getParentCategoryName();
        String categoryName = resources.getCategoryName();

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
    }
}
