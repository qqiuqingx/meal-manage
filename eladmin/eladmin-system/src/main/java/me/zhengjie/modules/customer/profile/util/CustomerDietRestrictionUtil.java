package me.zhengjie.modules.customer.profile.util;

import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 人工禁忌移除、主动恢复与自动匹配共有的对象身份和集合合并规则。 */
public final class CustomerDietRestrictionUtil {
    private CustomerDietRestrictionUtil() { }

    /** 返回字典对象的稳定身份；同名不同类型或ID的对象不会合并。 */
    public static String key(CustomerDietItemDto item) {
        return item.getType() + ":" + item.getId();
    }

    /**
     * 按已规范化的人工选择记住移除并解除主动选回的排除，更新内存中的客户字段。
     * @param profile 已锁定的客户档案，调用方在同一事务中保存两个字段
     * @param selected 已通过字典校验的完整选择列表
     */
    public static void rememberManualSelection(CustomerProfile profile, List<CustomerDietItemDto> selected) {
        Set<String> excluded = exclusions(profile);
        Set<String> selectedKeys = new LinkedHashSet<>();
        for (CustomerDietItemDto item : selected) {
            selectedKeys.add(key(item));
        }
        for (CustomerDietItemDto item : items(profile.getDietaryRestrictions())) {
            if (!selectedKeys.contains(key(item))) {
                excluded.add(key(item));
            }
        }
        excluded.removeAll(selectedKeys);
        profile.setDietaryRestrictionExclusions(new ArrayList<>(excluded));
        profile.setDietaryRestrictions(selected);
    }

    /** 返回客户人工排除稳定键的独立集合；未记录时返回空集合。 */
    public static Set<String> exclusions(CustomerProfile profile) {
        return new LinkedHashSet<>(profile.getDietaryRestrictionExclusions() == null
                ? Collections.emptyList() : profile.getDietaryRestrictionExclusions());
    }

    /**
     * 保留已有对象，追加未被人工排除的自动命中对象，并按稳定键去重。
     * @param profile 最新锁定的客户，包含原有引用和人工排除
     * @param incoming 本次原文匹配结果
     * @return 保持原有引用顺序和名称快照的合并列表
     */
    public static List<CustomerDietItemDto> mergeAutomatic(CustomerProfile profile, List<CustomerDietItemDto> incoming) {
        Map<String, CustomerDietItemDto> merged = new LinkedHashMap<>();
        for (CustomerDietItemDto item : items(profile.getDietaryRestrictions())) {
            merged.putIfAbsent(key(item), item);
        }
        Set<String> excluded = exclusions(profile);
        for (CustomerDietItemDto item : items(incoming)) {
            if (!excluded.contains(key(item))) {
                merged.putIfAbsent(key(item), item);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** 将未初始化的引用列表视为空列表，避免修改客户原有集合。 */
    private static List<CustomerDietItemDto> items(List<CustomerDietItemDto> values) {
        return values == null ? Collections.emptyList() : values.stream()
                .filter(item -> item != null && item.getType() != null && item.getId() != null)
                .collect(Collectors.toList());
    }
}
