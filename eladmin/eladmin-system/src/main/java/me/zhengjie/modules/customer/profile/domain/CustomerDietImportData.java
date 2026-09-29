package me.zhengjie.modules.customer.profile.domain;

import lombok.Data;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietMatchDto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 合并到月份工作表客户后的第二工作表信息与匹配结果。
 */
@Data
public class CustomerDietImportData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String medicalRequirements;
    private String medicalConflict;
    private String postoperativeInfo;
    private String postoperativeConflict;
    private String dealTimeSource;
    private LocalDateTime dealTime;
    private String dealTimeConflict;
    private List<String> dishRequirementsRaw = new ArrayList<>();
    private List<String> dietaryRestrictionsRaw = new ArrayList<>();
    private List<CustomerDietMatchDto> matches = new ArrayList<>();
    private List<CustomerDietItemDto> dishRequirements = new ArrayList<>();
    private List<CustomerDietItemDto> dietaryRestrictions = new ArrayList<>();
    private List<String> issues = new ArrayList<>();
}
