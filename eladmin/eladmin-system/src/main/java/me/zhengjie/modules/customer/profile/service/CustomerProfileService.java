package me.zhengjie.modules.customer.profile.service;

import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileSaveDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileStatusRequestDto;
import me.zhengjie.utils.PageResult;

/**
 * 客户档案服务接口
 */
public interface CustomerProfileService {

    /**
     * 分页查询客户档案
     */
    PageResult<CustomerProfile> query(CustomerProfileQueryCriteria criteria, Integer current, Integer size);

    /**
     * 获取客户详情
     */
    CustomerProfileDetailDto getDetail(Long id);

    /**
     * 创建客户档案
     */
    void create(CustomerProfileSaveDto dto);

    /**
     * 更新客户档案
     */
    void update(CustomerProfileSaveDto dto);

    /**
     * 更新客户状态
     */
    void updateStatus(Long id, CustomerProfileStatusRequestDto dto);

    /**
     * 生成客户编号
     */
    String generateCode(Long parentPackageId);
}