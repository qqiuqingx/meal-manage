package me.zhengjie.modules.customer.pkg.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class ParentPackageDto {

    private Long id;
    private String packageCode;
    private String prefix;
    private String packageName;
    private Integer status;
    private String remark;

    // 关联的子套餐列表（用于展开展示）
    private List<SubPackageDto> children;
}
