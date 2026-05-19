package me.zhengjie.agent.client;

import me.zhengjie.agent.domain.dto.DiagnosisToolCandidateDishStatsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerOrdersRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealPlanLookupRequest;

import java.util.List;
import java.util.Map;

public interface DiagnosisToolDataClient {

    Map<String, Object> getCustomerProfile(DiagnosisToolCustomerLookupRequest request);

    List<Map<String, Object>> listCustomerOrders(DiagnosisToolCustomerOrdersRequest request);

    Map<String, Object> getMealPlan(DiagnosisToolMealPlanLookupRequest request);

    List<Map<String, Object>> getCandidateDishStats(DiagnosisToolCandidateDishStatsRequest request);
}
