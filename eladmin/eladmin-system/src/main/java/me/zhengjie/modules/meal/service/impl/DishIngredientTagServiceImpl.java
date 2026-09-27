package me.zhengjie.modules.meal.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.meal.domain.DishIngredientTag;
import me.zhengjie.modules.meal.domain.DishIngredientTagRelation;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagDto;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagRelationDto;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagSaveDto;
import me.zhengjie.modules.meal.mapper.DishIngredientTagMapper;
import me.zhengjie.modules.meal.mapper.DishIngredientTagRelationMapper;
import me.zhengjie.modules.meal.service.DishIngredientTagService;
import me.zhengjie.utils.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 配料标签字典与配料关联服务实现。
 */
@Service
@RequiredArgsConstructor
public class DishIngredientTagServiceImpl implements DishIngredientTagService {

    private final DishIngredientTagMapper tagMapper;
    private final DishIngredientTagRelationMapper relationMapper;

    /**
     * 按标签名称分页查询标签。
     * @param criteria 名称和分页参数
     * @return 标签当前页及总记录数
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<DishIngredientTag> query(DishIngredientTagQueryCriteria criteria) {
        if (criteria == null) {
            criteria = new DishIngredientTagQueryCriteria();
        }
        int page = criteria.getPage() == null ? 0 : criteria.getPage();
        int size = criteria.getSize() == null ? 20 : criteria.getSize();
        if (page < 0 || size < 1) {
            throw new BadRequestException("分页参数不正确");
        }
        criteria.setPage(page);
        criteria.setSize(size);
        criteria.setName(StringUtils.trimWhitespace(criteria.getName()));
        IPage<DishIngredientTag> result = tagMapper.selectPageByCriteria(new Page<>(page + 1L, size), criteria);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    /**
     * 创建标签，数据库唯一索引负责并发重名兜底。
     * @param resources 标签名称
     * @return 包含数据库生成ID的标签
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishIngredientTag create(DishIngredientTagSaveDto resources) {
        DishIngredientTag tag = new DishIngredientTag();
        tag.setName(normalizeName(resources == null ? null : resources.getName()));
        tag.setCreateTime(new Timestamp(System.currentTimeMillis()));
        try {
            tagMapper.insert(tag);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("标签名称已存在");
        }
        return tag;
    }

    /**
     * 锁定标签后更新名称，避免与配料绑定或标签删除并发。
     * @param resources 标签ID和新名称
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DishIngredientTagSaveDto resources) {
        if (resources == null || resources.getId() == null) {
            throw new BadRequestException("标签ID不能为空");
        }
        DishIngredientTag tag = tagMapper.selectByIdForUpdate(resources.getId());
        if (tag == null) {
            throw new BadRequestException("标签不存在");
        }
        tag.setName(normalizeName(resources.getName()));
        tag.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        try {
            tagMapper.updateById(tag);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("标签名称已存在");
        }
    }

    /**
     * 仅允许删除没有配料引用的标签。
     * @param id 标签ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        if (id == null) {
            throw new BadRequestException("标签ID不能为空");
        }
        DishIngredientTag tag = tagMapper.selectByIdForUpdate(id);
        if (tag == null) {
            throw new BadRequestException("标签不存在");
        }
        if (tagMapper.countRelationsByTagId(id) > 0) {
            throw new BadRequestException("标签正在被配料使用，无法删除");
        }
        tagMapper.deleteById(id);
    }

    /**
     * 在当前事务中校验并整体替换指定配料的标签关系。
     * @param ingredientId 配料ID
     * @param tagIds 新的标签ID集合，空集合表示清空
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceIngredientTags(Integer ingredientId, List<Integer> tagIds) {
        if (ingredientId == null) {
            throw new BadRequestException("配料ID不能为空");
        }
        Set<Integer> orderedTagIds = new TreeSet<>();
        if (tagIds != null) {
            for (Integer tagId : tagIds) {
                if (tagId == null) {
                    throw new BadRequestException("标签ID不能为空");
                }
                orderedTagIds.add(tagId);
            }
        }
        for (Integer tagId : orderedTagIds) {
            if (tagMapper.selectByIdForUpdate(tagId) == null) {
                throw new BadRequestException("标签不存在：" + tagId);
            }
        }

        relationMapper.deleteByIngredientId(ingredientId);
        for (Integer tagId : orderedTagIds) {
            DishIngredientTagRelation relation = new DishIngredientTagRelation();
            relation.setIngredientId(ingredientId);
            relation.setTagId(tagId);
            relationMapper.insertRelation(relation);
        }
    }

    /**
     * 一次查询多个配料的标签，避免配料分页结果逐条访问数据库。
     * @param ingredientIds 配料ID集合
     * @return 每个有标签配料对应的标签列表
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Integer, List<DishIngredientTagDto>> findTagsByIngredientIds(List<Integer> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Integer> uniqueIds = new LinkedHashSet<>();
        for (Integer ingredientId : ingredientIds) {
            if (ingredientId != null) {
                uniqueIds.add(ingredientId);
            }
        }
        if (uniqueIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DishIngredientTagRelationDto> relations = relationMapper.selectTagsByIngredientIds(new ArrayList<>(uniqueIds));
        Map<Integer, List<DishIngredientTagDto>> tagsByIngredient = new LinkedHashMap<>();
        for (DishIngredientTagRelationDto relation : relations) {
            DishIngredientTagDto tag = new DishIngredientTagDto();
            tag.setId(relation.getTagId());
            tag.setName(relation.getName());
            tagsByIngredient.computeIfAbsent(relation.getIngredientId(), key -> new ArrayList<>()).add(tag);
        }
        return tagsByIngredient;
    }

    /**
     * 按明确的配料ID集合清理标签关系。
     * @param ingredientIds 配料ID集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteIngredientRelations(List<Integer> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return;
        }
        Set<Integer> uniqueIds = new TreeSet<>();
        for (Integer ingredientId : ingredientIds) {
            if (ingredientId != null) {
                uniqueIds.add(ingredientId);
            }
        }
        if (!uniqueIds.isEmpty()) {
            relationMapper.deleteByIngredientIds(new ArrayList<>(uniqueIds));
        }
    }

    /**
     * 去除首尾空白并校验标签名。
     * @param name 原始名称
     * @return 可保存的标签名称
     */
    private String normalizeName(String name) {
        String normalized = StringUtils.trimWhitespace(name);
        if (!StringUtils.hasText(normalized)) {
            throw new BadRequestException("标签名称不能为空");
        }
        if (normalized.codePointCount(0, normalized.length()) > 64) {
            throw new BadRequestException("标签名称不能超过64个字符");
        }
        return normalized;
    }
}
