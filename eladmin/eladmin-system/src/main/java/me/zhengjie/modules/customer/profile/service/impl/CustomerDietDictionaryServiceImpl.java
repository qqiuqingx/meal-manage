package me.zhengjie.modules.customer.profile.service.impl;

import lombok.RequiredArgsConstructor;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerDietDictionaryMapper;
import me.zhengjie.modules.customer.profile.service.CustomerDietDictionaryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户饮食对象字典服务。
 */
@Service
@RequiredArgsConstructor
public class CustomerDietDictionaryServiceImpl implements CustomerDietDictionaryService {

    private final CustomerDietDictionaryMapper dictionaryMapper;

    @Override
    public List<CustomerDietOptionDto> listActiveOptions() {
        return dictionaryMapper.selectActiveOptions();
    }

    @Override
    public List<CustomerDietItemDto> normalizeSelections(List<CustomerDietItemDto> requested,
                                                         List<CustomerDietItemDto> existing,
                                                         List<CustomerDietOptionDto> activeOptions) {
        if (requested == null) {
            return null;
        }
        Map<String, CustomerDietItemDto> existingByKey = indexExisting(existing);
        Map<String, CustomerDietOptionDto> activeByKey = indexActiveOptions(activeOptions);
        Map<String, CustomerDietItemDto> normalized = new LinkedHashMap<>();
        for (CustomerDietItemDto item : requested) {
            if (item == null || item.getType() == null || item.getId() == null || item.getId() <= 0) {
                throw new BadRequestException("饮食对象引用缺少合法的类型或ID");
            }
            if (!isSupportedType(item.getType())) {
                throw new BadRequestException("饮食对象类型不支持：" + item.getType());
            }
            String key = key(item.getType(), item.getId());
            if (normalized.containsKey(key)) {
                continue;
            }
            CustomerDietItemDto saved = existingByKey.get(key);
            if (saved != null) {
                normalized.put(key, copyReference(saved));
                continue;
            }
            CustomerDietOptionDto option = activeByKey.get(key);
            if (option == null) {
                throw new BadRequestException("饮食对象不存在或已停用：" + item.getType() + "#" + item.getId());
            }
            CustomerDietItemDto resolved = new CustomerDietItemDto();
            resolved.setType(option.getType());
            resolved.setId(option.getId());
            resolved.setName(option.getName());
            normalized.put(key, resolved);
        }
        return new ArrayList<>(normalized.values());
    }

    /**
     * 按类型和ID建立当前客户已保存引用索引。
     *
     * @param existing 已保存饮食对象列表
     * @return 可用于保留历史名称快照的索引
     */
    private Map<String, CustomerDietItemDto> indexExisting(List<CustomerDietItemDto> existing) {
        if (existing == null || existing.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, CustomerDietItemDto> indexed = new HashMap<>();
        for (CustomerDietItemDto item : existing) {
            if (item != null && item.getType() != null && item.getId() != null) {
                indexed.put(key(item.getType(), item.getId()), item);
            }
        }
        return indexed;
    }

    /**
     * 按类型和ID建立当前有效选项索引。
     *
     * @param options 当前有效字典选项
     * @return 当前有效选项索引
     */
    private Map<String, CustomerDietOptionDto> indexActiveOptions(List<CustomerDietOptionDto> options) {
        Map<String, CustomerDietOptionDto> indexed = new HashMap<>();
        if (options != null) {
            for (CustomerDietOptionDto option : options) {
                indexed.put(key(option.getType(), option.getId()), option);
            }
        }
        return indexed;
    }

    /**
     * 校验结构化引用是否使用受支持的字典类型。
     *
     * @param type 客户提交的类型
     * @return 类型受支持时为 true
     */
    private boolean isSupportedType(String type) {
        return "DISH".equals(type)
                || "INGREDIENT".equals(type)
                || "INGREDIENT_TAG".equals(type)
                || "INGREDIENT_CATEGORY".equals(type)
                || "DISH_TAG".equals(type);
    }

    /**
     * 构造饮食对象稳定键。
     *
     * @param type 字典类型
     * @param id 字典主键
     * @return 类型与ID组成的键
     */
    private String key(String type, Long id) {
        return type + ":" + id;
    }

    /**
     * 复制已保存引用，保留历史名称快照且不带客户端额外字段。
     *
     * @param source 已保存饮食引用
     * @return 规范引用副本
     */
    private CustomerDietItemDto copyReference(CustomerDietItemDto source) {
        CustomerDietItemDto copied = new CustomerDietItemDto();
        copied.setType(source.getType());
        copied.setId(source.getId());
        copied.setName(source.getName());
        return copied;
    }
}
