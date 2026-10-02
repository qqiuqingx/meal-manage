package me.zhengjie.modules.customer.profile.domain.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;

/**
 * 客户饮食对象选择项，只返回当前可选字典记录。
 */
@Data
public class CustomerDietOptionDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;

    private Long id;

    private String name;

    /** 配料二级分类的父级/子级路径，其他对象为空。 */
    private String categoryPath;

    /** 内部自动匹配标记：配料所属分类或分类自身及父分类名为“调料”时忽略，不影响人工选择。 */
    @JSONField(serialize = false, deserialize = false)
    private boolean ignoreDietMatch;
}
