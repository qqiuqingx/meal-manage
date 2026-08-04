package me.zhengjie.agent.client;

import me.zhengjie.agent.tool.input.ExplainBusinessRuleInput;
import me.zhengjie.agent.tool.input.GetPackageDetailInput;
import me.zhengjie.agent.tool.input.GetServiceCustomerDetailInput;
import me.zhengjie.agent.tool.input.ListMealPlansInput;
import me.zhengjie.agent.tool.input.ListRefundsInput;
import me.zhengjie.agent.tool.input.ListScheduledDishesInput;
import me.zhengjie.agent.tool.input.ListVerificationsInput;
import me.zhengjie.agent.tool.input.PreviewDishCandidatesInput;
import me.zhengjie.agent.tool.input.QueryBusinessMetricsInput;
import me.zhengjie.agent.tool.input.SearchCustomerProfilesInput;
import me.zhengjie.agent.tool.input.SearchDishesInput;
import me.zhengjie.agent.tool.input.SearchServiceCustomersInput;
import me.zhengjie.agent.tool.output.ToolOutputs;

/** Agent 调用主系统统一只读查询 API 的端口；不允许自由 URL、SQL 或 Mapper。 */
public interface MainSystemQueryClient {
    /** 查询客户档案列表。 */
    ToolOutputs.ToolResult<ToolOutputs.CustomerProfile> searchCustomerProfiles(SearchCustomerProfilesInput input);
    /** 查询以订单为根的服务客户列表。 */
    ToolOutputs.ToolResult<ToolOutputs.ServiceCustomer> searchServiceCustomers(SearchServiceCustomersInput input);
    /** 查询客户或订单的综合业务快照。 */
    ToolOutputs.ToolResult<ToolOutputs.ServiceCustomerDetail> getServiceCustomerDetail(GetServiceCustomerDetailInput input);
    /** 查询已生成排餐及菜品明细。 */
    ToolOutputs.ToolResult<ToolOutputs.MealPlan> listMealPlans(ListMealPlansInput input);
    /** 查询未删除的核销记录。 */
    ToolOutputs.ToolResult<ToolOutputs.Verification> listVerifications(ListVerificationsInput input);
    /** 查询退餐记录。 */
    ToolOutputs.ToolResult<ToolOutputs.Refund> listRefunds(ListRefundsInput input);
    /** 查询指定客户指定日期的候选菜。 */
    ToolOutputs.ToolResult<Object> previewDishCandidates(PreviewDishCandidatesInput input);
    /** 查询指定日期的公共排期菜单。 */
    ToolOutputs.ToolResult<Object> listScheduledDishes(ListScheduledDishesInput input);
    /** 分页查询菜品和配料摘要。 */
    ToolOutputs.ToolResult<ToolOutputs.Dish> searchDishes(SearchDishesInput input);
    /** 查询父套餐及子套餐规格。 */
    ToolOutputs.ToolResult<ToolOutputs.PackageDetail> getPackageDetail(GetPackageDetailInput input);
    /** 查询登记的运营指标。 */
    ToolOutputs.ToolResult<ToolOutputs.Metric> queryBusinessMetrics(QueryBusinessMetricsInput input);
    /** 查询版本化业务规则说明。 */
    ToolOutputs.ToolResult<ToolOutputs.BusinessRule> explainBusinessRule(ExplainBusinessRuleInput input);
}
