package me.zhengjie.modules.customer.profile.rest;

import com.alibaba.fastjson2.JSON;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import me.zhengjie.annotation.Log;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportPreviewDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportResultDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietSelectionDto;
import me.zhengjie.modules.customer.profile.service.CustomerProfileImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(CustomerProfileImportController.class);

    private final CustomerProfileImportService importService;

    public CustomerProfileImportController(CustomerProfileImportService importService) {
        this.importService = importService;
    }

    /**
     * 上传客户用餐计划表并生成只读预览。
     *
     * @param file 第三版客户用餐计划表（xlsx）
     * @param importDate 计划导入日期，格式 yyyy-MM-dd；为空时取当天
     * @param dietOnly 是否仅预览「客户禁忌」工作表并补录已有客户
     * @return 逐位客户草稿、来源行与跳过原因
     */
    @Log("客户批量导入预览")
    @ApiOperation("客户批量导入预览")
    @PostMapping("/preview")
    @PreAuthorize("@el.check('customerProfile:import')")
    public ResponseEntity<CustomerImportPreviewDto> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "importDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate importDate,
            @RequestParam(value = "dietOnly", defaultValue = "false") boolean dietOnly) throws IOException {
        try {
            if (file == null || file.isEmpty()) {
                throw new BadRequestException("请上传客户用餐计划表文件");
            }
            validateXlsxFile(file);
            CustomerImportPreviewDto preview = dietOnly
                    ? importService.previewDietOnly(file.getBytes(), file.getOriginalFilename(), importDate)
                    : importService.preview(file.getBytes(), file.getOriginalFilename(), importDate);
            return ResponseEntity.ok(preview);
        } catch (BadRequestException e) {
            log.warn("客户批量导入预览校验失败: reason={}", e.getMessage());
            throw e;
        } catch (IOException | RuntimeException e) {
            log.error("客户批量导入预览异常: errorType={}", e.getClass().getSimpleName(), e);
            throw e;
        }
    }

    /**
     * 确认提交预览过的工作簿；服务端会重算摘要并按客户独立提交。
     *
     * @param file 确认提交的工作簿
     * @param fileHash 操作人预览确认的 SHA-256
     * @param dictionaryHash 预览饮食字典摘要
     * @param dietSelections 歧义词项的选择 JSON
     * @param importDate 预览时的计划导入日期
     * @param dietOnly 是否仅补录「客户禁忌」工作表到已有客户
     * @return 含创建、跳过、失败原因的逐位处理结果
     */
    @Log("客户批量导入提交")
    @ApiOperation("客户批量导入提交")
    @PostMapping("/confirm")
    @PreAuthorize("@el.check('customerProfile:import')")
    public ResponseEntity<CustomerImportResultDto> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileHash") String fileHash,
            @RequestParam(value = "dictionaryHash", required = false) String dictionaryHash,
            @RequestParam(value = "dietSelections", required = false) String dietSelections,
            @RequestParam(value = "importDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate importDate,
            @RequestParam(value = "dietOnly", defaultValue = "false") boolean dietOnly) throws IOException {
        try {
            if (file == null || file.isEmpty()) {
                throw new BadRequestException("请上传客户用餐计划表文件");
            }
            validateXlsxFile(file);
            java.util.List<CustomerDietSelectionDto> selections;
            try {
                selections = dietSelections == null || dietSelections.trim().isEmpty()
                        ? java.util.Collections.emptyList()
                        : JSON.parseArray(dietSelections, CustomerDietSelectionDto.class);
            } catch (RuntimeException e) {
                throw new BadRequestException("饮食匹配选择格式无效，请重新预览");
            }
            CustomerImportResultDto result = dietOnly
                    ? importService.importDietOnly(file.getBytes(), file.getOriginalFilename(), fileHash,
                    dictionaryHash, selections, importDate)
                    : importService.importCustomers(file.getBytes(), file.getOriginalFilename(), fileHash,
                    dictionaryHash, selections, importDate);
            return ResponseEntity.ok(result);
        } catch (BadRequestException e) {
            log.warn("客户批量导入提交校验失败: reason={}", e.getMessage());
            throw e;
        } catch (IOException | RuntimeException e) {
            log.error("客户批量导入提交异常: errorType={}", e.getClass().getSimpleName(), e);
            throw e;
        }
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
