package me.zhengjie.modules.customer.profile.domain;

import lombok.Data;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportDraftDto;

import java.io.Serializable;

/**
 * 一位客户在完成套餐映射与重复判定之后的导入候选。
 *
 * <p>预览与提交共用该结构：预览只读取 {@code draft}，提交在独立事务中按
 * {@code parsed} 的原始字段写入客户档案、地址；有待导入餐数时一并写入首单与未来逐餐计划。</p>
 *
 * @author qqx
 * @date 2026-09-24
 **/
@Data
public class ImportCandidate implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工作簿解析结果中的客户草稿
     */
    private ParsedCustomer parsed;

    /**
     * 面向操作人的客户草稿说明
     */
    private CustomerImportDraftDto draft;

    /**
     * 匹配到的父套餐；未匹配成功时为空
     */
    private ParentPackage parentPackage;

    /**
     * 该编号是否已存在于客户档案（幂等跳过）
     */
    private boolean alreadyExists;

    /**
     * 是否可以写入数据库
     */
    private boolean importable;
}
