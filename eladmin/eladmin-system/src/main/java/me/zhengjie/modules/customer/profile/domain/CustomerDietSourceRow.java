package me.zhengjie.modules.customer.profile.domain;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 第二工作表中的一行客户医嘱、饮食与术后信息来源。
 */
@Data
public class CustomerDietSourceRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private int sourceRow;
    private String originalCodeA;
    private String originalCodeB;
    private String effectiveCode;
    private String medicalRequirements;
    private String dishRequirementsRaw;
    private String dietaryRestrictionsRaw;
    private String dealTimeSource;
    private LocalDateTime dealTime;
    private String postoperativeInfo;
    private List<String> issues = new ArrayList<>();
}
