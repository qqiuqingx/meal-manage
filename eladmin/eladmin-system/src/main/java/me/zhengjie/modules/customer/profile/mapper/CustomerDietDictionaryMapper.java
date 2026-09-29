package me.zhengjie.modules.customer.profile.mapper;

import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 只读聚合查询客户饮食引用可用的五类字典。
 */
@Mapper
public interface CustomerDietDictionaryMapper {

    /**
     * 查询当前可选的菜品、配料、标签和配料二级分类。
     *
     * @return 按类型、名称和ID排序的选项
     */
    List<CustomerDietOptionDto> selectActiveOptions();
}
