package me.zhengjie.modules.customer.profile.service;

import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsRowDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileSaveDto;
import me.zhengjie.utils.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Set;

/**
 * 客户档案服务接口
 */
public interface CustomerProfileService {

    /**
     * 分页查询客户档案
     */
    PageResult<CustomerProfile> queryAll(CustomerProfileQueryCriteria criteria, Page<Object> page);

    /**
     * 分页查询客户用餐统计
     */
    PageResult<CustomerMealStatsRowDto> queryMealStats(CustomerMealStatsQueryCriteria criteria, Integer page, Integer size);

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
     * 批量删除客户档案
     * @param ids 客户ID集合
     */
    void delete(Set<Long> ids);

    /**
     * 生成客户编号
     */
    String generateCode(Long parentPackageId);
}
