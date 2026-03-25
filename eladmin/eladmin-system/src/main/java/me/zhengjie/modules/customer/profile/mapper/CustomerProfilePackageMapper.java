package me.zhengjie.modules.customer.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfilePackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 客户签约 Mapper 接口
 */
@Mapper
public interface CustomerProfilePackageMapper extends BaseMapper<CustomerProfilePackage> {

    /**
     * 查询客户的当前生效签约
     */
    CustomerProfilePackage findActiveByCustomerId(@Param("customerId") Long customerId);
}