package me.zhengjie.modules.customer.profile.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次导入解析与只读校验的完整结果。
 *
 * @author qqx
 * @date 2026-09-24
 **/
@Data
public class ImportResolution implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工作簿解析结果
     */
    private ParsedWorkbook workbook;

    /**
     * 逐位客户候选，按首个源行号升序
     */
    private List<ImportCandidate> candidates = new ArrayList<>();
}
