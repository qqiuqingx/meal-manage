package me.zhengjie.modules.customer.profile.service;

import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerIntakeParseRequest;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerIntakeParseResult;

/**
 * 客户话术解析服务。
 */
public interface CustomerIntakeParseService {

    /**
     * 将客服话术解析为客户档案和首单草稿。
     *
     * @param request 解析请求，包含原始文本
     * @return 解析结果，包含草稿、问题和字段轨迹
     */
    CustomerIntakeParseResult parse(CustomerIntakeParseRequest request);
}
