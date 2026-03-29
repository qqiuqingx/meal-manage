package me.zhengjie.modules.customer.pkg.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("parent_package_sub")
public class ParentPackageSub {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentPackageId;

    private Long subPackageId;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
