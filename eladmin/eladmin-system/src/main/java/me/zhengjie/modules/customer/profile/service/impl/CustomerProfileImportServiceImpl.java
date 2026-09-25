package me.zhengjie.modules.customer.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.ImportCandidate;
import me.zhengjie.modules.customer.profile.domain.ImportResolution;
import me.zhengjie.modules.customer.profile.domain.ParsedCustomer;
import me.zhengjie.modules.customer.profile.domain.ParsedWorkbook;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportAddressDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportDraftDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueCategory;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportMealCellDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportPreviewDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportItemResultDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportResultDto;
import me.zhengjie.modules.customer.profile.service.CustomerOrderImportParser;
import me.zhengjie.modules.customer.profile.service.CustomerProfileImportService;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 客户与首单批量导入服务实现。
 *
 * <p>预览阶段只做解析与只读校验，不写库、不落临时文件、不输出手机号与地址到日志。</p>
 *
 * @author qqx
 * @date 2026-09-24
 **/
@Service
@RequiredArgsConstructor
public class CustomerProfileImportServiceImpl implements CustomerProfileImportService {

    private static final Logger log = LoggerFactory.getLogger(CustomerProfileImportServiceImpl.class);

    private final CustomerOrderImportParser parser;
    private final CustomerProfileMapper profileMapper;
    private final ParentPackageMapper parentPackageMapper;
    private final CustomerProfileImportWriter importWriter;

    /**
     * 允许上传的最大字节数，第三版工作簿约 24MB
     */
    private static final long MAX_UPLOAD_BYTES = 40L * 1024 * 1024;

    private static final Pattern CODE_PATTERN = Pattern.compile("^([A-Za-z]+)(\\d+)$");

    @Override
    public CustomerImportPreviewDto preview(byte[] content, String fileName, LocalDate importDate) {
        ImportResolution resolution = resolve(content, fileName, importDate);
        return buildPreview(resolution);
    }

    /**
     * 复核文件摘要后按客户独立事务创建档案、首单与未来逐餐计划。
     *
     * @param content 上传的工作簿
     * @param fileName 上传文件名，仅用于处理上下文
     * @param expectedFileHash 操作人确认的预览 SHA-256
     * @param importDate 预览时使用的导入日期
     * @return 逐位导入结果及重新解析的预览
     */
    @Override
    public CustomerImportResultDto importCustomers(byte[] content, String fileName, String expectedFileHash,
                                                   LocalDate importDate) {
        validateUpload(content);
        if (isBlank(expectedFileHash)) {
            throw new BadRequestException("缺少预览文件摘要，请先重新预览");
        }
        LocalDate resolvedImportDate = importDate == null ? LocalDate.now() : importDate;
        ImportResolution resolution = resolve(content, fileName, resolvedImportDate);
        ParsedWorkbook workbook = resolution.getWorkbook();
        if (!expectedFileHash.equalsIgnoreCase(workbook.getFileHash())) {
            throw new BadRequestException("上传文件与预览文件不同，请重新预览后再确认");
        }
        if (!workbook.isStructureValid()) {
            throw new BadRequestException("工作簿结构校验失败，无法提交");
        }

        CustomerImportResultDto result = new CustomerImportResultDto();
        result.setFileHash(workbook.getFileHash());
        result.setImportDate(resolvedImportDate.toString());
        result.setPreview(buildPreview(resolution));
        for (ImportCandidate candidate : resolution.getCandidates()) {
            CustomerImportItemResultDto item;
            if (candidate.isAlreadyExists()) {
                item = buildItemResult(candidate, "ALREADY_EXISTS", firstIssue(candidate, "客户编号已存在，本次跳过"), null, null);
                result.setAlreadyExistsCount(result.getAlreadyExistsCount() + 1);
            } else if (!candidate.isImportable()) {
                item = buildItemResult(candidate, "SKIPPED", joinMessages(candidate.getParsed().getIssues()), null, null);
                result.setSkippedCount(result.getSkippedCount() + 1);
            } else {
                try {
                    item = importWriter.write(candidate, resolvedImportDate);
                    if ("CREATED".equals(item.getStatus())) {
                        result.setCreatedCount(result.getCreatedCount() + 1);
                        if (Integer.valueOf(0).equals(candidate.getDraft().getSheetRemainingCount())) {
                            log.info("客户批量导入来源剩余餐数为0: sourceRows={}, code={}, customerId={}, orderId={}",
                                    candidate.getParsed().getSourceRows(), candidate.getParsed().getEffectiveCode(),
                                    item.getCustomerId(), item.getOrderId());
                        }
                    } else if ("ALREADY_EXISTS".equals(item.getStatus())) {
                        result.setAlreadyExistsCount(result.getAlreadyExistsCount() + 1);
                    }
                } catch (BadRequestException e) {
                    item = buildItemResult(candidate, "FAILED", e.getMessage(), null, null);
                    result.setFailedCount(result.getFailedCount() + 1);
                } catch (Exception e) {
                    log.warn("客户批量导入单客户事务失败: sourceRow={}, code={}, errorType={}",
                            candidate.getParsed().getSourceRows().get(0), candidate.getParsed().getEffectiveCode(),
                            e.getClass().getSimpleName());
                    item = buildItemResult(candidate, "FAILED", "数据库写入失败，请检查配置后重试", null, null);
                    result.setFailedCount(result.getFailedCount() + 1);
                }
            }
            result.getResults().add(item);
        }
        return result;
    }

    /**
     * 组装未写入成功客户的逐位结果。
     *
     * @param candidate 当前客户候选
     * @param status FAILED / SKIPPED / ALREADY_EXISTS
     * @param message 处理原因
     * @param customerId 客户主键；未创建时为空
     * @param orderId 订单主键；未创建时为空
     * @return 单客户导入结果
     */
    private CustomerImportItemResultDto buildItemResult(ImportCandidate candidate, String status, String message,
                                                        Long customerId, Long orderId) {
        CustomerImportItemResultDto item = new CustomerImportItemResultDto();
        item.setSourceRows(new ArrayList<>(candidate.getParsed().getSourceRows()));
        item.setCustomerCode(candidate.getParsed().getEffectiveCode());
        item.setStatus(status);
        item.setMessage(isBlank(message) ? "未能生成导入结果" : message);
        item.setCustomerId(customerId);
        item.setOrderId(orderId);
        return item;
    }

    /**
     * 取得候选上的第一条阻塞问题。
     *
     * @param candidate 当前客户候选
     * @param fallback 没有阻塞问题时的默认说明
     * @return 首条问题或默认说明
     */
    private String firstIssue(ImportCandidate candidate, String fallback) {
        return candidate.getParsed().getIssues().stream()
                .map(CustomerImportIssueDto::getMessage).filter(this::isNotBlank).findFirst().orElse(fallback);
    }

    /**
     * 合并候选上去重后的问题说明，供跳过结果展示。
     *
     * @param issues 客户阻塞问题列表
     * @return 问题说明；列表为空时返回默认说明
     */
    private String joinMessages(List<CustomerImportIssueDto> issues) {
        if (issues == null || issues.isEmpty()) {
            return "预览校验未通过，本次跳过";
        }
        return issues.stream().map(CustomerImportIssueDto::getMessage)
                .filter(this::isNotBlank).distinct().collect(Collectors.joining("；"));
    }

    /**
     * 解析工作簿并补齐父套餐映射、编号池校验与重复建档判定。
     *
     * @param content 工作簿字节内容
     * @param fileName 上传文件名，仅用于日志
     * @param importDate 计划导入日期；为空时取当天
     * @return 解析与只读校验结果
     */
    public ImportResolution resolve(byte[] content, String fileName, LocalDate importDate) {
        validateUpload(content);
        String fileHash = sha256(content);
        log.info("客户批量导入解析开始: fileName={}, size={}KB, 摘要前 8 位={}",
                fileName, content.length / 1024, fileHash.substring(0, 8));

        ParsedWorkbook workbook = parser.parse(content, fileHash, importDate);
        ImportResolution resolution = new ImportResolution();
        resolution.setWorkbook(workbook);
        if (!workbook.isStructureValid()) {
            log.warn("客户批量导入解析失败：工作簿结构不符合第三版模板");
            return resolution;
        }
        resolution.setCandidates(resolveCandidates(workbook));
        return resolution;
    }

    /**
     * 逐位补齐套餐与重复判定。
     *
     * @param workbook 工作簿解析结果
     * @return 客户候选列表
     */
    private List<ImportCandidate> resolveCandidates(ParsedWorkbook workbook) {
        List<ParentPackage> enabledPackages = parentPackageMapper.selectList(
                new QueryWrapper<ParentPackage>().eq("status", true));
        if (enabledPackages == null) {
            enabledPackages = Collections.emptyList();
        }

        List<String> codes = workbook.getCustomers().stream()
                .map(ParsedCustomer::getEffectiveCode)
                .filter(this::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> existingCodePhones = loadExistingCodePhones(codes);

        List<ImportCandidate> candidates = new ArrayList<>();
        for (ParsedCustomer parsed : workbook.getCustomers()) {
            ImportCandidate candidate = new ImportCandidate();
            candidate.setParsed(parsed);

            ParentPackage matched = matchParentPackage(parsed.getEffectiveCode(), enabledPackages);
            candidate.setParentPackage(matched);
            if (matched == null && parsed.getIssues().stream()
                    .noneMatch(issue -> CustomerImportIssueCategory.PACKAGE_CONFIG_ERROR.name().equals(issue.getCategory()))) {
                parsed.addIssue(CustomerImportIssueCategory.PACKAGE_CONFIG_ERROR,
                        describePackageMismatch(parsed.getEffectiveCode(), enabledPackages));
            }

            if (existingCodePhones.containsKey(parsed.getEffectiveCode())) {
                candidate.setAlreadyExists(true);
                String existingPhone = existingCodePhones.get(parsed.getEffectiveCode());
                String message = existingPhone != null && parsed.getPhoneNormalized() != null
                        && !existingPhone.equals(parsed.getPhoneNormalized())
                        ? "编号已存在且手机号与本次不同，按业务规则不合并，本次跳过"
                        : "客户编号已存在，本次跳过（幂等重传）";
                parsed.addIssue(CustomerImportIssueCategory.ALREADY_EXISTS, message);
            }

            CustomerImportDraftDto draft = buildDraft(parsed, matched);
            candidate.setDraft(draft);
            candidate.setImportable(parsed.isImportable());
            candidates.add(candidate);
        }
        candidates.sort(Comparator.comparingInt(candidate -> candidate.getParsed().getSourceRows().get(0)));
        return candidates;
    }

    /**
     * 把解析草稿转换为面向操作人的客户草稿，保留来源购买数并计算导入前历史核销基数。
     *
     * @param parsed 解析草稿
     * @param parentPackage 匹配到的父套餐
     * @return 客户草稿
     */
    private CustomerImportDraftDto buildDraft(ParsedCustomer parsed, ParentPackage parentPackage) {
        CustomerImportDraftDto draft = new CustomerImportDraftDto();
        draft.setSourceRows(new ArrayList<>(parsed.getSourceRows()));
        draft.setOriginalCodeA(parsed.getOriginalCodeA());
        draft.setOriginalCodeB(parsed.getOriginalCodeB());
        draft.setCustomerCode(parsed.getEffectiveCode());
        draft.setPhoneMasked(maskPhone(parsed.getPhoneNormalized()));
        if (parentPackage != null) {
            draft.setParentPackageId(parentPackage.getId());
            draft.setParentPackageName(trim(parentPackage.getPackageName()));
        }
        draft.setPaused(parsed.isPaused());
        draft.setScheduleMode(parsed.getScheduleMode());
        draft.setMealType(parsed.getMealType());
        draft.setBreakfastCount(0);
        draft.setMainDishCount(parsed.getMainDishCount());
        draft.setSideDishCount(parsed.getSideDishCount());
        draft.setVegCount(parsed.getVegCount());
        draft.setRiceCount(parsed.getRiceCount());
        draft.setRiceType(parsed.getRiceType());
        draft.setSoupCount(parsed.getSoupCount());
        draft.setAddresses(parsed.getAddresses().stream().map(address -> {
            CustomerImportAddressDto masked = new CustomerImportAddressDto();
            masked.setAddressType(address.getAddressType());
            masked.setAddressDetail(address.getAddressDetail());
            masked.setContactName(address.getContactName());
            masked.setContactPhone(maskPhone(address.getContactPhone()));
            return masked;
        }).collect(Collectors.toList()));
        draft.setRemark(parsed.getRemark());
        draft.setSpecialRequirements(parsed.getSpecialRequirements());
        draft.setMealCells(new ArrayList<>(parsed.getFutureMealCells()));
        draft.setWarnings(new ArrayList<>(parsed.getWarnings()));
        draft.setSheetRemainingCount(parsed.getSheetRemainingCount());

        int futureQuantity = 0;
        for (CustomerImportMealCellDto cell : parsed.getFutureMealCells()) {
            futureQuantity += cell.getQuantity() == null ? 0 : cell.getQuantity();
        }
        draft.setFutureMealCount(futureQuantity);
        int remaining = parsed.getSheetRemainingCount() == null ? 0 : parsed.getSheetRemainingCount();
        int lunchDinnerCount = parsed.getSheetMealCount() == null ? 0 : parsed.getSheetMealCount();
        if (lunchDinnerCount < remaining + futureQuantity) {
            parsed.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                    "午晚餐购买数小于来源剩余餐数与未来日格份数之和，请核对工作簿公式");
        }
        draft.setLunchDinnerCount(lunchDinnerCount);
        draft.setTotalCount(lunchDinnerCount);
        draft.setImportedVerifiedCount(Math.max(lunchDinnerCount - remaining - futureQuantity, 0));

        draft.setErrors(parsed.getIssues().stream()
                .map(CustomerImportIssueDto::getMessage)
                .collect(Collectors.toList()));
        draft.setImportable(parsed.isImportable());
        return draft;
    }

    /**
     * 在启用的父套餐中匹配编号所属套餐。
     *
     * <p>匹配规则：编号以套餐编号池前缀开头、数字部分落在池范围内，且结果唯一。
     * 套餐名称不参与匹配，编号归属完全以套餐管理中的编号池配置为准。</p>
     *
     * @param code 有效编号
     * @param enabledPackages 启用父套餐列表
     * @return 唯一匹配的父套餐；无匹配或不唯一时返回 null
     */
    private ParentPackage matchParentPackage(String code, List<ParentPackage> enabledPackages) {
        if (isBlank(code)) {
            return null;
        }
        List<ParentPackage> hits = findPoolHits(code, enabledPackages);
        return hits.size() == 1 ? hits.get(0) : null;
    }

    /**
     * 收集编号池覆盖该编号的启用父套餐：前缀命中、剩余部分为纯数字且落在池区间内。
     *
     * @param code 有效编号
     * @param enabledPackages 启用父套餐列表
     * @return 编号池覆盖该编号的父套餐列表
     */
    private List<ParentPackage> findPoolHits(String code, List<ParentPackage> enabledPackages) {
        List<ParentPackage> hits = new ArrayList<>();
        for (ParentPackage parent : enabledPackages) {
            if (isBlank(parent.getPoolPrefix()) || parent.getPoolStart() == null || parent.getPoolEnd() == null) {
                continue;
            }
            if (!code.startsWith(parent.getPoolPrefix())) {
                continue;
            }
            String numberPart = code.substring(parent.getPoolPrefix().length());
            if (!numberPart.matches("\\d+")) {
                continue;
            }
            int number;
            try {
                number = Integer.parseInt(numberPart);
            } catch (NumberFormatException e) {
                // 超长数字不可能落在 Integer 编号池范围内，保留可读的不匹配原因
                continue;
            }
            if (number >= parent.getPoolStart() && number <= parent.getPoolEnd()) {
                hits.add(parent);
            }
        }
        return hits;
    }

    /**
     * 生成编号与父套餐编号池不匹配的可读原因。
     *
     * <p>口径与 {@link #matchParentPackage} 一致：只看启用父套餐的编号池前缀与区间，
     * 套餐名称仅用于提示文案，不参与匹配。</p>
     *
     * @param code 有效编号
     * @param enabledPackages 启用父套餐列表
     * @return 原因描述
     */
    private String describePackageMismatch(String code, List<ParentPackage> enabledPackages) {
        if (isBlank(code)) {
            return "编号缺失，无法匹配父套餐编号池";
        }
        Matcher matcher = CODE_PATTERN.matcher(code);
        if (!matcher.matches()) {
            return "编号「" + code + "」不是「字母前缀+数字」格式，无法匹配父套餐编号池";
        }
        List<ParentPackage> hits = findPoolHits(code, enabledPackages);
        if (hits.size() > 1) {
            return "编号「" + code + "」同时落在多个父套餐编号池内（"
                    + hits.stream().map(this::describePool).collect(Collectors.joining("、"))
                    + "），无法唯一确认套餐，请业务确认";
        }
        List<ParentPackage> prefixMatched = enabledPackages.stream()
                .filter(parent -> !isBlank(parent.getPoolPrefix())
                        && code.startsWith(parent.getPoolPrefix())
                        && code.substring(parent.getPoolPrefix().length()).matches("\\d+"))
                .collect(Collectors.toList());
        if (!prefixMatched.isEmpty()) {
            return "编号「" + code + "」不在父套餐编号池 "
                    + prefixMatched.stream().map(this::describePoolWithName).collect(Collectors.joining("、"))
                    + " 内";
        }
        return "编号「" + code + "」不落在任何启用父套餐的编号池内，请先在套餐管理中配置对应编号池";
    }

    /**
     * 描述一个父套餐的编号池区间。
     *
     * @param parent 父套餐
     * @return 形如「A1～A169」的描述
     */
    private String describePool(ParentPackage parent) {
        int width = resolveCodeWidth(parent.getPoolStart(), parent.getPoolEnd());
        String start = parent.getPoolPrefix() + String.format("%0" + width + "d", parent.getPoolStart());
        String end = parent.getPoolPrefix() + String.format("%0" + width + "d", parent.getPoolEnd());
        return start + "～" + end;
    }

    /**
     * 描述编号池区间并附上套餐名称，用于面向操作人的提示文案。
     *
     * @param parent 父套餐
     * @return 形如「C001～C1000（小月子）」的描述
     */
    private String describePoolWithName(ParentPackage parent) {
        String name = trim(parent.getPackageName());
        return describePool(parent) + (isBlank(name) ? "" : "（" + name + "）");
    }

    /**
     * 计算编号数字段的固定宽度，与编号池分配逻辑保持一致。
     *
     * @param poolStart 池起始号
     * @param poolEnd 池结束号
     * @return 固定宽度
     */
    private int resolveCodeWidth(int poolStart, int poolEnd) {
        int startWidth = String.valueOf(poolStart).length();
        int endWidth = String.valueOf(poolEnd).length();
        return Math.max(3, Math.max(startWidth, endWidth));
    }

    /**
     * 批量查询已存在的客户编号及其手机号。
     *
     * @param codes 待查编号
     * @return 编号到手机号的映射；手机号为空时值为 null
     */
    private Map<String, String> loadExistingCodePhones(List<String> codes) {
        Map<String, String> existing = new HashMap<>();
        if (codes.isEmpty()) {
            return existing;
        }
        int batchSize = 500;
        for (int start = 0; start < codes.size(); start += batchSize) {
            List<String> batch = codes.subList(start, Math.min(start + batchSize, codes.size()));
            List<CustomerProfile> profiles = profileMapper.selectList(
                    new QueryWrapper<CustomerProfile>().in("customer_code", batch));
            if (profiles == null) {
                continue;
            }
            for (CustomerProfile profile : profiles) {
                existing.put(profile.getCustomerCode(), profile.getPhone());
            }
        }
        return existing;
    }

    /**
     * 组装预览结果。
     *
     * @param resolution 解析与只读校验结果
     * @return 预览结果
     */
    private CustomerImportPreviewDto buildPreview(ImportResolution resolution) {
        ParsedWorkbook workbook = resolution.getWorkbook();
        CustomerImportPreviewDto preview = new CustomerImportPreviewDto();
        preview.setFileHash(workbook.getFileHash());
        preview.setSheetName(workbook.getSheetName());
        preview.setCalendarMonth(workbook.getCalendarMonthStart() == null
                ? null : YearMonth.from(workbook.getCalendarMonthStart()).toString());
        preview.setImportDate(workbook.getImportDate() == null ? null : workbook.getImportDate().toString());
        preview.setDataRowCount(workbook.getDataRowCount());
        preview.setCustomerCount(workbook.getCustomerCount());
        preview.setStructureValid(workbook.isStructureValid());

        List<CustomerImportIssueDto> issues = new ArrayList<>(workbook.getIssues());
        int importable = 0;
        int alreadyExists = 0;
        int errors = 0;
        int futureQuantity = 0;
        for (ImportCandidate candidate : resolution.getCandidates()) {
            preview.getDrafts().add(candidate.getDraft());
            futureQuantity += candidate.getDraft().getFutureMealCount() == null
                    ? 0 : candidate.getDraft().getFutureMealCount();
            issues.addAll(candidate.getParsed().getIssues());
            if (candidate.isAlreadyExists()) {
                alreadyExists++;
            } else if (candidate.isImportable()) {
                importable++;
            } else {
                errors++;
            }
        }
        preview.setImportableCount(importable);
        preview.setAlreadyExistsCount(alreadyExists);
        preview.setErrorCount(errors);
        preview.setFutureMealQuantity(futureQuantity);

        issues.sort(Comparator
                .comparing((CustomerImportIssueDto issue) -> issue.getSourceRow() == null ? Integer.MAX_VALUE : issue.getSourceRow())
                .thenComparing(CustomerImportIssueDto::getCategory));
        preview.setIssues(issues);
        return preview;
    }

    /**
     * 校验上传内容。
     *
     * @param content 工作簿字节内容
     */
    private void validateUpload(byte[] content) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("请上传客户用餐计划表文件");
        }
        if (content.length > MAX_UPLOAD_BYTES) {
            throw new BadRequestException("文件过大，请上传 40MB 以内的 xlsx 文件");
        }
    }

    /**
     * 计算字节内容的 SHA-256 摘要。
     *
     * @param content 字节内容
     * @return 十六进制摘要
     */
    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content);
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BadRequestException("无法计算文件摘要：" + e.getMessage());
        }
    }

    /**
     * 手机号脱敏，仅保留前 3 位与后 4 位。
     *
     * @param phone 规范化手机号
     * @return 脱敏结果
     */
    private String maskPhone(String phone) {
        if (isBlank(phone) || phone.length() <= 7) {
            return null;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
