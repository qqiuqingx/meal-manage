package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能排查诊断结果客服反馈提交请求。
 */
@Data
public class AgentDiagnosisFeedbackRequest {

    private String requestId;

    private String sessionId;

    private Long customerId;

    private String customerName;

    private String recordDate;

    private String mealType;

    private List<String> predictedReasonCodes = new ArrayList<>();

    @NotBlank(message = "accepted不能为空")
    private String accepted;

    private String actualReasonCode;

    private String comment;
}
