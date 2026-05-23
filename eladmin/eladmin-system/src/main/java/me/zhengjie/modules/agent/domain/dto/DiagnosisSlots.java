package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * 聊天诊断会话槽位。
 */
@Data
public class DiagnosisSlots {

    private Long customerId;
    private String customerCode;
    private String recordDate;
    private String mealType;
}
