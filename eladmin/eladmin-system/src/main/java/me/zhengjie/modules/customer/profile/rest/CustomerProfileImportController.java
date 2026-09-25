package me.zhengjie.modules.customer.profile.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import me.zhengjie.annotation.Log;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportPreviewDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportResultDto;
import me.zhengjie.modules.customer.profile.service.CustomerProfileImportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;

/**
 * 客户与首单批量导入 REST 控制器。
 *
 * <p>导入流程与单笔客户新增完全分开：需要 {@code customerProfile:import} 权限，
 * 上传后先预览再确认提交，提交时需回传同一份文件的摘要。</p>
 *
 * @author qqx
 * @date 2026-09-24
 **/
@Api(tags = "客户批量导入")
@RestController
@RequestMapping("/api/customerProfile/import")
public class CustomerProfileImportController {

    private final CustomerProfileImportService importService;

    public CustomerProfileImportController(CustomerProfileImportService importService) {
        this.importService = importService;
    }

    /**
     * 上传客户用餐计划表并生成只读预览。
     *
     * @param file 第三版客户用餐计划表（xlsx）
     * @param importDate 计划导入日期，格式 yyyy-MM-dd；为空时取当天
     * @return 逐位客户草稿、来源行与跳过原因
     */
    @Log("客户批量导入预览")
    @ApiOperation("客户批量导入预览")
    @PostMapping("/preview")
    @PreAuthorize("@el.check('customerProfile:import')")
    public ResponseEntity<CustomerImportPreviewDto> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "importDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate importDate) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请上传客户用餐计划表文件");
        }
        validateXlsxFile(file);
        CustomerImportPreviewDto preview = importService.preview(file.getBytes(), file.getOriginalFilename(), importDate);
        return ResponseEntity.ok(preview);
    }

    /**
     * 确认提交预览过的工作簿；服务端会重算摘要并按客户独立提交。
     *
     * @param file 确认提交的工作簿
     * @param fileHash 操作人预览确认的 SHA-256
     * @param importDate 预览时的计划导入日期
     * @return 含创建、跳过、失败原因的逐位处理结果
     */
    @Log("客户批量导入提交")
    @ApiOperation("客户批量导入提交")
    @PostMapping("/confirm")
    @PreAuthorize("@el.check('customerProfile:import')")
    public ResponseEntity<CustomerImportResultDto> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileHash") String fileHash,
            @RequestParam(value = "importDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate importDate) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请上传客户用餐计划表文件");
        }
        validateXlsxFile(file);
        CustomerImportResultDto result = importService.importCustomers(
                file.getBytes(), file.getOriginalFilename(), fileHash, importDate);
        return ResponseEntity.ok(result);
    }

    /**
     * 确保导入文件采用受支持的 xlsx 格式。
     *
     * @param file 上传文件
     */
    private void validateXlsxFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BadRequestException("仅支持 xlsx 格式的客户用餐计划表");
        }
    }
}
