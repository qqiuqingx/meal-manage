package me.zhengjie.modules.customer.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户地址 Mapper 接口
 */
@Mapper
public interface CustomerProfileAddressMapper extends BaseMapper<CustomerProfileAddress> {
}