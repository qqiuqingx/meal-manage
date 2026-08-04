package me.zhengjie.modules.agent.query.service;

import me.zhengjie.modules.agent.query.domain.dto.AgentDishCandidateRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentHistoryQueryRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentMealPlanQueryRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentPackageDetailRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentRuleExplainRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentScheduledMenuQueryRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentDishSearchRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentMetricQueryRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentProfileSearchRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentServiceCustomerDetailRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentServiceCustomerSearchRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentUnifiedQueryDto;
import me.zhengjie.modules.agent.query.domain.unified.AgentUnifiedQueryResponse;

import java.util.Map;

/**
 * Agent 统一只读领域查询编排服务。
 *
 * <p>该服务只组合主系统已有的受控查询服务；权限和数据范围由 Controller 在进入这里之前完成，
 * 本服务负责把旧领域 DTO 收敛成 12 个稳定工具契约。</p>
 */
public interface AgentUnifiedQueryService {
    /** 查询客户档案列表。 */
    AgentUnifiedQueryResponse<AgentUnifiedQueryDto.ProfileItem> searchCustomerProfiles(AgentProfileSearchRequest request);

    /** 查询以订单为根的服务客户列表。 */
    AgentUnifiedQueryResponse<AgentUnifiedQueryDto.ServiceCustomerItem> searchServiceCustomers(AgentServiceCustomerSearchRequest request);

    /** 查询单客户或单订单综合快照。 */
    AgentUnifiedQueryResponse<AgentUnifiedQueryDto.ServiceCustomerDetailItem> getServiceCustomerDetail(AgentServiceCustomerDetailRequest request);

    /** 查询排餐及菜品摘要。 */
    AgentUnifiedQueryResponse<AgentUnifiedQueryDto.MealPlanItem> listMealPlans(AgentMealPlanQueryRequest request);

    /** 查询核销摘要。 */
    AgentUnifiedQueryResponse<AgentUnifiedQueryDto.VerificationItem> listVerifications(AgentHistoryQueryRequest request);

    /** 查询退餐摘要。 */
    AgentUnifiedQueryResponse<AgentUnifiedQueryDto.RefundItem> listRefunds(AgentHistoryQueryRequest request);

    /** 预览候选菜及过滤原因。 */
    AgentUnifiedQueryResponse<Map<String, Object>> previewDishCandidates(AgentDishCandidateRequest request);

    /** 查询公共排期菜单。 */
    AgentUnifiedQueryResponse<Map<String, Object>> listScheduledDishes(AgentScheduledMenuQueryRequest request);

    /** 搜索菜品及限量配料摘要。 */
    AgentUnifiedQueryResponse<AgentUnifiedQueryDto.DishItem> searchDishes(AgentDishSearchRequest request);

    /** 查询父子套餐详情。 */
    AgentUnifiedQueryResponse<AgentUnifiedQueryDto.PackageDetailItem> getPackageDetail(AgentPackageDetailRequest request);

    /** 查询登记运营指标。 */
    AgentUnifiedQueryResponse<AgentUnifiedQueryDto.MetricItem> queryBusinessMetrics(AgentMetricQueryRequest request);

    /** 查询版本化业务规则。 */
    AgentUnifiedQueryResponse<AgentUnifiedQueryDto.RuleItem> explainBusinessRule(AgentRuleExplainRequest request);
}
