package me.zhengjie.modules.customer.profile.service;

import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportPreviewDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportResultDto;

import java.time.LocalDate;

/**
 * 客户与首单批量导入服务。
 *
 * @author qqx
 * @date 2026-09-24
 **/
public interface CustomerProfileImportService {

    /**
     * 解析上传的工作簿并生成只读预览。
     *
     * <p>不写库、不落临时文件、不在日志输出手机号与地址；用于操作人在正式导入前核对
     * 逐位客户的来源行、编号、套餐规格、暂停判定、订单餐数与未来逐餐数量。</p>
     *
     * @param content 工作簿字节内容
     * @param fileName 上传文件名，仅用于日志中的非敏感标识
     * @param importDate 计划导入日期；为空时取当天
     * @return 预览结果
     */
    CustomerImportPreviewDto preview(byte[] content, String fileName, LocalDate importDate);

    /**
     * 重新解析并逐客户提交同一份预览过的工作簿，客户之间使用独立事务。
     *
     * @param content 工作簿字节内容
     * @param fileName 上传文件名，仅供处理上下文使用
     * @param expectedFileHash 预览返回的 SHA-256
     * @param importDate 预览使用的导入日期
     * @return 逐位导入结果
     */
    CustomerImportResultDto importCustomers(byte[] content, String fileName, String expectedFileHash,
                                            LocalDate importDate);
}
