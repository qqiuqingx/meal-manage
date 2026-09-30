package me.zhengjie.modules.customer.profile.service;

import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;

import java.util.List;

/**
 * 客户饮食对象字典查询与已确认引用校验。
 */
public interface CustomerDietDictionaryService {

    /**
     * 查询供客户维护、订单行内编辑和导入预览使用的当前有效选项。
     *
     * @return 五类字典的可选项
     */
    List<CustomerDietOptionDto> listActiveOptions();

    /**
     * 校验并规范化客户提交的饮食对象引用。
     *
     * @param requested 客户提交的对象引用；null 表示调用方未提交此字段
     * @param existing 客户当前已保存的引用，用于原样保留历史或停用对象
     * @return 去重后的权威引用；requested 为 null 时返回 null
     */
    List<CustomerDietItemDto> normalizeSelections(List<CustomerDietItemDto> requested,
                                                   List<CustomerDietItemDto> existing,
                                                   List<CustomerDietOptionDto> activeOptions);
}
