package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.modules.meal.domain.DishIngredientCategory;
import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.domain.dto.CategoryIngredientMappingRow;
import me.zhengjie.modules.meal.mapper.DishIngredientCategoryMapper;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.service.DishIngredientCategoryService;
import me.zhengjie.exception.BadRequestException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 配料分类服务实现
 * @author qqx
 * @date 2026-04-24
 **/
@Service
@RequiredArgsConstructor
public class DishIngredientCategoryServiceImpl
    extends ServiceImpl<DishIngredientCategoryMapper, DishIngredientCategory>
    implements DishIngredientCategoryService {

    private final DishIngredientCategoryMapper categoryMapper;
    private final DishIngredientMapper dishIngredientMapper;

    @Override
    public List<DishIngredientCategory> tree() {
        List<DishIngredientCategory> all = categoryMapper.selectList(
            new QueryWrapper<DishIngredientCategory>().eq("enabled", true)
        );
        return buildTree(all);
    }

    @Override
    public List<DishIngredientCategory> listEnabled() {
        return categoryMapper.selectList(
            new QueryWrapper<DishIngredientCategory>()
                .eq("enabled", true)
                .orderByAsc("level", "sort")
        );
    }

    @Override
    public List<DishIngredientCategory> listByParentId(Integer parentId) {
        return categoryMapper.selectByParentId(parentId);
    }

    @Override
    public DishIngredientCategory findByNameAndLevel(String name, int level, Integer parentId) {
        return categoryMapper.selectByNameAndLevel(name, level, parentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishIngredientCategory create(DishIngredientCategory category) {
        category.setSort(getNextSort(category.getLevel(), category.getParentId()));
        category.setEnabled(true);
        category.setCreateTime(new Timestamp(System.currentTimeMillis()));
        categoryMapper.insert(category);
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        DishIngredientCategory category = getById(id);
        if (category == null) {
            throw new BadRequestException("分类不存在");
        }

        if (category.getLevel() == 1) {
            Long childrenCount = categoryMapper.selectCount(
                new QueryWrapper<DishIngredientCategory>().eq("parent_id", id)
            );
            if (childrenCount > 0) {
                throw new BadRequestException("一级分类下存在二级分类，无法删除");
            }
        } else {
            Long ingredientCount = dishIngredientMapper.selectCount(
                new QueryWrapper<DishIngredient>().eq("category_id", id)
            );
            if (ingredientCount > 0) {
                throw new BadRequestException("该二级分类下存在配料，无法删除");
            }
        }
        removeById(id);
    }

    @Override
    public Map<String, Set<String>> getCategoryIngredientMapping() {
        Map<String, Set<String>> mapping = new HashMap<>();

        // 一级分类 → 配料名
        for (CategoryIngredientMappingRow row : categoryMapper.selectLevel1IngredientMapping()) {
            mapping.computeIfAbsent(row.getCategoryName(), k -> new HashSet<>())
                .add(row.getIngredientName());
        }

        // 二级分类 → 配料名
        for (CategoryIngredientMappingRow row : categoryMapper.selectLevel2IngredientMapping()) {
            mapping.computeIfAbsent(row.getCategoryName(), k -> new HashSet<>())
                .add(row.getIngredientName());
        }

        return mapping;
    }

private List<DishIngredientCategory> buildTree(List<DishIngredientCategory> all) {
        List<DishIngredientCategory> tree = new ArrayList<>();
        List<DishIngredientCategory> level1List = all.stream()
            .filter(c -> c.getLevel() == 1)
            .sorted(Comparator.comparingInt(DishIngredientCategory::getSort))
            .collect(Collectors.toList());

        for (DishIngredientCategory level1 : level1List) {
            List<DishIngredientCategory> children = all.stream()
                .filter(c -> level1.getId().equals(c.getParentId()))
                .sorted(Comparator.comparingInt(DishIngredientCategory::getSort))
                .collect(Collectors.toList());
            level1.setChildren(children);
            tree.add(level1);
        }
        return tree;
    }

    private int getNextSort(int level, Integer parentId) {
        Integer maxSort = categoryMapper.selectMaxSort(level, parentId);
        return (maxSort == null ? 0 : maxSort) + 10;
    }
}
