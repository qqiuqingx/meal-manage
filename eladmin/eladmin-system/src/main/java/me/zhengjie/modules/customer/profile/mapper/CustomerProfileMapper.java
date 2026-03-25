package me.zhengjie.modules.customer.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileQueryCriteria;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户档案 Mapper 接口
 */
@Mapper
public interface CustomerProfileMapper extends BaseMapper<CustomerProfile> {

    /**
     * 条件查询客户档案列表
     */
    List<CustomerProfile> findAll(@Param("criteria") CustomerProfileQueryCriteria criteria);

    /**
     * 根据客户编号查询(排除指定ID)
     */
    int countByCodeExcludeId(@Param("customerCode") String customerCode, @Param("excludeId") Long excludeId);
}