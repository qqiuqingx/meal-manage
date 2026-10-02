package me.zhengjie.modules.meal.service.impl;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import me.zhengjie.modules.meal.domain.event.DietDictionaryChangedEvent;

import me.zhengjie.modules.meal.domain.DishIngredientCategory;
import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.domain.dto.CategoryIngredientMappingRow;
import me.zhengjie.modules.meal.domain.dto.DishIngredientCategoryCreateDto;
import me.zhengjie.modules.meal.domain.dto.DishIngredientCategoryUpdateDto;
import me.zhengjie.modules.meal.mapper.DishIngredientCategoryMapper;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.service.DishIngredientCategoryService;
import me.zhengjie.exception.BadRequestException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
    private final ApplicationEventPublisher events;

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

    /** 保存字典变更并发布事务内通知，提交成功后自动更新客户禁忌。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishIngredientCategory create(DishIngredientCategory category) {
        category.setSort(getNextSort(category.getLevel(), category.getParentId()));
        category.setEnabled(true);
        category.setCreateTime(new Timestamp(System.currentTimeMillis()));
        categoryMapper.insert(category);
        events.publishEvent(new DietDictionaryChangedEvent());
        return category;
    }

    /**
     * 字典变化时发布事务内通知，提交成功后自动更新客户禁忌。
     * 校验并新增分类；排序未指定时追加到同级分类末尾。
     * @param request 分类名称、层级、父分类和可选排序
     * @return 新建分类
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishIngredientCategory createCategory(DishIngredientCategoryCreateDto request) {
        if (request == null) {
            throw new BadRequestException("分类信息不能为空");
        }
        String name = normalizeCategoryName(request.getName());
        Integer level = request.getLevel();
        Integer parentId = request.getParentId();
        if (level == null || (level != 1 && level != 2)) {
            throw new BadRequestException("分类层级只能为1或2");
        }
        if (level == 1 && parentId != null) {
            throw new BadRequestException("一级分类不能设置父分类");
        }
        if (level == 2) {
            if (parentId == null) {
                throw new BadRequestException("二级分类必须选择一级分类");
            }
            DishIngredientCategory parent = categoryMapper.selectById(parentId);
            if (parent == null || parent.getLevel() != 1) {
                throw new BadRequestException("父分类不存在或不是一级分类");
            }
        }
        if (countSameName(level, parentId, name, null) > 0) {
            throw new BadRequestException("同级分类名称已存在");
        }

        DishIngredientCategory category = new DishIngredientCategory();
        category.setName(name);
        category.setLevel(level);
        category.setParentId(parentId);
        category.setSort(request.getSort() == null ? getNextSort(level, parentId) : request.getSort());
        category.setEnabled(true);
        category.setCreateTime(new Timestamp(System.currentTimeMillis()));
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("同级分类名称已存在");
        }
        events.publishEvent(new DietDictionaryChangedEvent());
        return category;
    }

    /**
     * 字典变化时发布事务内通知，提交成功后自动更新客户禁忌。
     * 更新分类名称和排序；层级和父分类由现有记录确定且不会改变。
     * @param id 分类ID
     * @param request 新名称和可选排序
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(Integer id, DishIngredientCategoryUpdateDto request) {
        if (id == null) {
            throw new BadRequestException("分类ID不能为空");
        }
        if (request == null) {
            throw new BadRequestException("分类信息不能为空");
        }
        DishIngredientCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BadRequestException("分类不存在");
        }

        String name = normalizeCategoryName(request.getName());
        if (countSameName(category.getLevel(), category.getParentId(), name, id) > 0) {
            throw new BadRequestException("同级分类名称已存在");
        }
        String oldName = category.getName();
        category.setName(name);
        if (request.getSort() != null) {
            category.setSort(request.getSort());
        }
        category.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        try {
            if (categoryMapper.updateById(category) == 0) {
                throw new BadRequestException("分类不存在");
            }
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("同级分类名称已存在");
        }
        if (!Objects.equals(oldName, category.getName())) {
            events.publishEvent(new DietDictionaryChangedEvent());
        }
    }

    /** 保存字典变更并发布事务内通知，提交成功后自动更新客户禁忌。 */
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
        events.publishEvent(new DietDictionaryChangedEvent());
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

    /**
     * 查询同层级、同父级下的同名分类数量，可按需排除正在编辑的分类。
     * @param level 分类层级
     * @param parentId 父分类ID，一级分类传null
     * @param name 已规范化的分类名称
     * @param excludeId 编辑时排除的分类ID
     * @return 匹配数量
     */
    private Long countSameName(Integer level, Integer parentId, String name, Integer excludeId) {
        QueryWrapper<DishIngredientCategory> query = new QueryWrapper<DishIngredientCategory>()
            .eq("level", level)
            .eq("name", name);
        if (parentId == null) {
            query.isNull("parent_id");
        } else {
            query.eq("parent_id", parentId);
        }
        if (excludeId != null) {
            query.ne("id", excludeId);
        }
        return categoryMapper.selectCount(query);
    }

    /**
     * 去除分类名称首尾空白并校验非空和长度。
     * @param name 原始分类名称
     * @return 可保存的分类名称
     */
    private String normalizeCategoryName(String name) {
        String normalized = StringUtils.trimWhitespace(name);
        if (!StringUtils.hasText(normalized)) {
            throw new BadRequestException("分类名称不能为空");
        }
        if (normalized.codePointCount(0, normalized.length()) > 64) {
            throw new BadRequestException("分类名称不能超过64个字符");
        }
        return normalized;
    }
}
