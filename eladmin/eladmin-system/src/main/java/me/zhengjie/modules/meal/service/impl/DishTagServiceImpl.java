package me.zhengjie.modules.meal.service.impl;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import me.zhengjie.modules.meal.domain.event.DietDictionaryChangedEvent;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.meal.domain.DishTag;
import me.zhengjie.modules.meal.domain.DishTagRelation;
import me.zhengjie.modules.meal.domain.dto.DishTagDto;
import me.zhengjie.modules.meal.domain.dto.DishTagQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishTagRelationDto;
import me.zhengjie.modules.meal.domain.dto.DishTagSaveDto;
import me.zhengjie.modules.meal.mapper.DishTagMapper;
import me.zhengjie.modules.meal.mapper.DishTagRelationMapper;
import me.zhengjie.modules.meal.service.DishTagService;
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
 * 菜品标签查询与菜品关联服务实现。
 */
@Service
@RequiredArgsConstructor
public class DishTagServiceImpl implements DishTagService {

    private final DishTagMapper tagMapper;
    private final DishTagRelationMapper relationMapper;
    private final ApplicationEventPublisher events;

    /**
     * 按标签名称分页查询标签。
     * @param criteria 名称与分页参数
     * @return 标签当前页及总记录数
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<DishTag> query(DishTagQueryCriteria criteria) {
        if (criteria == null) {
            criteria = new DishTagQueryCriteria();
        }
        int page = criteria.getPage() == null ? 0 : criteria.getPage();
        int size = criteria.getSize() == null ? 20 : criteria.getSize();
        if (page < 0 || size < 1) {
            throw new BadRequestException("分页参数不正确");
        }
        criteria.setPage(page);
        criteria.setSize(size);
        criteria.setName(StringUtils.trimWhitespace(criteria.getName()));
        IPage<DishTag> result = tagMapper.selectPageByCriteria(new Page<>(page + 1L, size), criteria);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    /**
     * 字典变化时发布事务内通知，提交成功后自动更新客户禁忌。
     * 创建标签，数据库唯一索引负责并发重名兜底。
     * @param resources 标签名称
     * @return 包含数据库生成ID的标签
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishTag create(DishTagSaveDto resources) {
        DishTag tag = new DishTag();
        tag.setName(normalizeName(resources == null ? null : resources.getName()));
        tag.setCreateTime(new Timestamp(System.currentTimeMillis()));
        try {
            tagMapper.insert(tag);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("标签名称已存在");
        }
        events.publishEvent(new DietDictionaryChangedEvent());
        return tag;
    }

    /**
     * 字典变化时发布事务内通知，提交成功后自动更新客户禁忌。
     * 锁定标签后更新名称，避免与菜品绑定或标签删除并发。
     * @param resources 标签ID和新名称
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DishTagSaveDto resources) {
        if (resources == null || resources.getId() == null || resources.getId() <= 0) {
            throw new BadRequestException("标签ID不能为空");
        }
        DishTag tag = tagMapper.selectByIdForUpdate(resources.getId());
        if (tag == null) {
            throw new BadRequestException("标签不存在");
        }
        String oldName = tag.getName();
        tag.setName(normalizeName(resources.getName()));
        tag.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        try {
            tagMapper.updateById(tag);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("标签名称已存在");
        }
        if (!Objects.equals(oldName, tag.getName())) {
            events.publishEvent(new DietDictionaryChangedEvent());
        }
    }

    /**
     * 字典变化时发布事务内通知，提交成功后自动更新客户禁忌。
     * 锁定标签并检查菜品引用后删除字典项。
     * @param id 标签ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("标签ID不能为空");
        }
        DishTag tag = tagMapper.selectByIdForUpdate(id);
        if (tag == null) {
            throw new BadRequestException("标签不存在");
        }
        if (relationMapper.countRelationsByTagId(id) > 0) {
            throw new BadRequestException("标签正在被菜品使用，请先从菜品中移除并保存关联后再删除");
        }
        tagMapper.deleteById(id);
        events.publishEvent(new DietDictionaryChangedEvent());
    }

    /**
     * 在锁定标签行后整体替换菜品标签关系，防止并发删除标签留下孤立关系。
     * @param dishId 菜品ID
     * @param tagIds 新标签ID集合，空集合表示清空
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceDishTags(Integer dishId, List<Integer> tagIds) {
        if (dishId == null) {
            throw new BadRequestException("菜品ID不能为空");
        }
        Set<Integer> orderedTagIds = new TreeSet<>();
        if (tagIds != null) {
            for (Integer tagId : tagIds) {
                if (tagId == null || tagId <= 0) {
                    throw new BadRequestException("标签ID必须为正整数");
                }
                orderedTagIds.add(tagId);
            }
        }
        for (Integer tagId : orderedTagIds) {
            if (tagMapper.selectByIdForUpdate(tagId) == null) {
                throw new BadRequestException("标签不存在：" + tagId);
            }
        }

        relationMapper.deleteByDishIds(Collections.singletonList(dishId));
        for (Integer tagId : orderedTagIds) {
            DishTagRelation relation = new DishTagRelation();
            relation.setDishId(dishId);
            relation.setTagId(tagId);
            relationMapper.insertRelation(relation);
        }
    }

    /**
     * 一次查询多个菜品的标签并按菜品分组。
     * @param dishIds 菜品ID集合
     * @return 每个有标签菜品对应的标签列表
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Integer, List<DishTagDto>> findTagsByDishIds(List<Integer> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Integer> uniqueIds = new LinkedHashSet<>();
        for (Integer dishId : dishIds) {
            if (dishId != null) {
                uniqueIds.add(dishId);
            }
        }
        if (uniqueIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DishTagRelationDto> relations = relationMapper.selectTagsByDishIds(new ArrayList<>(uniqueIds));
        Map<Integer, List<DishTagDto>> tagsByDish = new LinkedHashMap<>();
        for (DishTagRelationDto relation : relations) {
            DishTagDto tag = new DishTagDto();
            tag.setId(relation.getTagId());
            tag.setName(relation.getName());
            tagsByDish.computeIfAbsent(relation.getDishId(), key -> new ArrayList<>()).add(tag);
        }
        return tagsByDish;
    }

    /**
     * 按明确的菜品ID集合清理标签关系。
     * @param dishIds 菜品ID集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDishRelations(List<Integer> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return;
        }
        Set<Integer> uniqueIds = new TreeSet<>();
        for (Integer dishId : dishIds) {
            if (dishId != null) {
                uniqueIds.add(dishId);
            }
        }
        if (!uniqueIds.isEmpty()) {
            relationMapper.deleteByDishIds(new ArrayList<>(uniqueIds));
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
