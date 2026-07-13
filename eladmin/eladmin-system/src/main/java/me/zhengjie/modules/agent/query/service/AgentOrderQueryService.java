package me.zhengjie.modules.agent.query.service;

import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOrderSummaryDto;

/**
 * Agent 订单只读查询服务；禁止返回订单金额、单价和餐费余额。
 */
public interface AgentOrderQueryService {

    /**
     * 按客户查询有限分页的订单摘要。
     *
     * @param customerId 客户 ID
     * @param status 订单状态，可为空
     * @param page 从 1 开始的页码
     * @param size 单页数量，最大 20
     * @return 订单摘要分页结果
     */
    AgentListResultDto<AgentOrderSummaryDto> listByCustomer(Long customerId, Integer status, int page, int size);

    /**
     * 查询客户订单的受控汇总集合，供客户概览计算全量餐数余额；最多处理 200 笔。
     *
     * @param customerId 客户 ID
     * @return 受控订单摘要集合及截断标志
     */
    AgentListResultDto<AgentOrderSummaryDto> listForOverview(Long customerId);

    /**
     * 按订单 ID 或订单编号查询订单摘要。
     *
     * @param orderId 订单 ID，可为空
     * @param orderCode 订单编号，可为空
     * @param expectedCustomerId 当前上下文客户 ID，可为空；存在时必须匹配
     * @return 不存在或客户关系不匹配时返回 null
     */
    AgentOrderSummaryDto getDetail(Long orderId, String orderCode, Long expectedCustomerId);
}
