package me.zhengjie.modules.customer.profile.service;

import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleAdjustmentRequest;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleAdjustmentResult;
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
     * 保存客户排餐日历调整。
     *
     * @param request 调整请求，包含排除日期和人工新增餐次
     * @return 调整结果
     */
    CustomerMealScheduleAdjustmentResult saveMealScheduleAdjustments(CustomerMealScheduleAdjustmentRequest request);

    /**
     * 恢复客户指定日期餐次配送，仅移除客户排除日期中的对应餐次，不影响同月人工新增记录。
     *
     * @param customerId 客户ID
     * @param recordDate 配送日期，格式 yyyy-MM-dd
     * @param mealType 餐次，支持 BREAKFAST / LUNCH / DINNER
     * @return 调整结果
     */
    CustomerMealScheduleAdjustmentResult resumeCustomerDelivery(Long customerId, String recordDate, String mealType);

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
