package me.zhengjie.agent.client;

import me.zhengjie.agent.domain.dto.DiagnosisToolCandidateDishStatsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightMealRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightOrderRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightVerificationRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerOrdersRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealRefundsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealPlanLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolPackageSpecRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolVerificationLogsRequest;

import java.util.List;
import java.util.Map;

public interface DiagnosisToolDataClient {

    Map<String, Object> getCustomerProfile(DiagnosisToolCustomerLookupRequest request);

    List<Map<String, Object>> listCustomerOrders(DiagnosisToolCustomerOrdersRequest request);

    Map<String, Object> getMealPlan(DiagnosisToolMealPlanLookupRequest request);

    List<Map<String, Object>> getCandidateDishStats(DiagnosisToolCandidateDishStatsRequest request);

    Map<String, Object> getCustomerExcludeDates(DiagnosisToolCustomerLookupRequest request);

    Map<String, Object> getOrderMealBalance(DiagnosisToolCustomerOrdersRequest request);

    Map<String, Object> getPackageSpec(DiagnosisToolPackageSpecRequest request);

    List<Map<String, Object>> getDishCandidateDetail(DiagnosisToolCandidateDishStatsRequest request);

    List<Map<String, Object>> listVerificationLogs(DiagnosisToolVerificationLogsRequest request);

    List<Map<String, Object>> listMealRefunds(DiagnosisToolMealRefundsRequest request);

    Map<String, Object> getMealPlanGenerationSnapshot(DiagnosisToolMealPlanLookupRequest request);

    // ==================== 客户信息查询 ====================

    /**
     * 获取客户餐数汇总
     */
    Map<String, Object> getCustomerMealSummary(DiagnosisToolCustomerInsightMealRequest request);

    /**
     * 获取客户核销统计
     */
    Map<String, Object> getCustomerVerificationSummary(DiagnosisToolCustomerInsightVerificationRequest request);

    /**
     * 获取客户订单列表
     */
    Map<String, Object> getCustomerOrderSummary(DiagnosisToolCustomerInsightOrderRequest request);
}
