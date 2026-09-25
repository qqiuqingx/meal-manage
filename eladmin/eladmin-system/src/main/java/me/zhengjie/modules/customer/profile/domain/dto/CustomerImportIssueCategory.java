package me.zhengjie.modules.customer.profile.domain.dto;

/**
 * 客户批量导入的问题分类。
 *
 * <p>分类决定导入结果报告的分组方式，也决定该行是否会被写入数据库：
 * 除 {@link #WARNING} 外，其余分类都表示该客户本次不导入。</p>
 *
 * @author qqx
 * @date 2026-09-24
 **/
public enum CustomerImportIssueCategory {

    /**
     * 工作簿级或整表结构问题（表头错位、工作表缺失、公式缓存不可信等），此时整份文件不可导入
     */
    WORKBOOK_ERROR,

    /**
     * 客户资料错误（编号格式、手机号、地址、送餐描述等）
     */
    PROFILE_ERROR,

    /**
     * 套餐配置错误（编号池前缀、范围不匹配或父套餐不唯一）
     */
    PACKAGE_CONFIG_ERROR,

    /**
     * 客户已存在，本次跳过（幂等重传）
     */
    ALREADY_EXISTS,

    /**
     * 纯早餐、等通知送餐等按业务规则整体跳过的行
     */
    SKIPPED,

    /**
     * 提交阶段单个客户事务失败，其他客户不受影响
     */
    DATABASE_ERROR,

    /**
     * 不影响导入、但操作人需要知晓的提示
     */
    WARNING
}
