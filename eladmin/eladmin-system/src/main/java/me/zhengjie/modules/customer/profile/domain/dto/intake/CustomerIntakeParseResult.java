package me.zhengjie.modules.customer.profile.domain.dto.intake;

import lombok.Data;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileSaveDto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 客户话术解析结果，包含可直接回填表单的客户新增草稿。
 */
@Data
public class CustomerIntakeParseResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否可以进入保存流程。存在 ERROR 时为 false。
     */
    private boolean valid;

    /**
     * 客户新增草稿，结构与现有新增客户接口保持一致。
     */
    private CustomerProfileSaveDto draft;

    /**
     * 解析问题列表。
     */
    private List<CustomerIntakeIssueDto> issues = new ArrayList<>();

    /**
     * 字段解析轨迹。
     */
    private List<CustomerIntakeParsedFieldDto> parsedFields = new ArrayList<>();

    /**
     * 原始键值对，便于排查未识别内容。
     */
    private Map<String, String> rawFields;
}
