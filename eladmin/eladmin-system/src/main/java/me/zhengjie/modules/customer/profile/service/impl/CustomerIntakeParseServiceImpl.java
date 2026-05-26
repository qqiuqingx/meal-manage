package me.zhengjie.modules.customer.profile.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.Setter;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileSaveDto;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerIntakeIssueDto;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerIntakeParseRequest;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerIntakeParseResult;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerIntakeParsedFieldDto;
import me.zhengjie.modules.customer.profile.service.CustomerIntakeParseService;
import me.zhengjie.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 客户话术解析服务实现。
 */
@Service
public class CustomerIntakeParseServiceImpl implements CustomerIntakeParseService {

    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^\\s*[【\\[]?([^：:\\]\\[]+?)[】\\]]?\\s*[：:]\\s*(.*)$");
    private static final Pattern DISH_CONFIG_PATTERN = Pattern.compile("(\\d+)\\s*主\\s*(\\d+)\\s*副\\s*(\\d+)\\s*素\\s*(\\d+)\\s*汤");
    private static final Pattern COUNT_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    @Autowired(required = false)
    private ParentPackageMapper parentPackageMapper;

    @Setter
    private Supplier<LocalDate> currentDateSupplier = LocalDate::now;

    /**
     * 解析客户话术文本，生成客户草稿、问题和字段映射。
     *
     * @param request 解析请求
     * @return 解析结果
     */
    @Override
    public CustomerIntakeParseResult parse(CustomerIntakeParseRequest request) {
        Map<String, String> rawFields = parseRawFields(request == null ? null : request.getText());
        CustomerProfileSaveDto draft = createDraft();
        List<CustomerIntakeIssueDto> issues = new ArrayList<>();
        List<CustomerIntakeParsedFieldDto> parsedFields = new ArrayList<>();

        fillBasicFields(rawFields, draft, parsedFields);
        fillMedicalAndSpecialFields(rawFields, draft, parsedFields);
        fillOrderInfo(rawFields, draft, issues, parsedFields);
        collectRequiredIssues(rawFields, draft, issues);

        CustomerIntakeParseResult result = new CustomerIntakeParseResult();
        result.setDraft(draft);
        result.setIssues(issues);
        result.setParsedFields(parsedFields);
        result.setRawFields(rawFields);
        result.setValid(issues.stream().noneMatch(issue -> "ERROR".equals(issue.getLevel())));
        return result;
    }

    /**
     * 创建解析结果草稿，并填充首单默认值。
     *
     * @return 与客户新增接口兼容的默认草稿
     */
    private CustomerProfileSaveDto createDraft() {
        CustomerProfileSaveDto draft = new CustomerProfileSaveDto();

        CustomerProfileSaveDto.AddressDto address = new CustomerProfileSaveDto.AddressDto();
        address.setAddressType("DEFAULT");
        draft.setAddresses(new ArrayList<>(Collections.singletonList(address)));

        CustomerProfileSaveDto.OrderInfoDto orderInfo = new CustomerProfileSaveDto.OrderInfoDto();
        orderInfo.setBreakfastCount(0);
        orderInfo.setLunchDinnerCount(0);
        orderInfo.setTotalCount(0);
        orderInfo.setScheduleMode("SCHEDULE");
        orderInfo.setStartDate(currentDateSupplier.get().toString());
        orderInfo.setStartMealType("LUNCH");
        orderInfo.setMealType("LUNCH_DINNER");
        orderInfo.setRiceCount(1);
        orderInfo.setRiceType("白米饭");
        orderInfo.setSoupCount(0);
        orderInfo.setMainDishCount(0);
        orderInfo.setSideDishCount(0);
        orderInfo.setVegCount(0);
        draft.setOrderInfo(orderInfo);
        return draft;
    }

    /**
     * 将原始文本按“标签: 值”结构解析为键值对。
     *
     * @param text 客服粘贴的原始文本
     * @return 保留原顺序的原始字段映射
     */
    private Map<String, String> parseRawFields(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (StringUtils.isBlank(text)) {
            return fields;
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (StringUtils.isBlank(line)) {
                continue;
            }
            String normalizedLine = normalizeLinePrefix(line);
            Matcher matcher = KEY_VALUE_PATTERN.matcher(normalizedLine);
            if (matcher.matches()) {
                String key = matcher.group(1).trim();
                String value = matcher.group(2) == null ? "" : matcher.group(2).trim();
                fields.put(key, value);
            }
        }
        return fields;
    }

    /**
     * 归一化行首符号，去除 emoji 和特殊前缀后再做键值识别。
     *
     * @param line 原始单行文本
     * @return 可参与键值解析的标准化文本
     */
    private String normalizeLinePrefix(String line) {
        String normalized = line == null ? "" : line.trim();
        while (!normalized.isEmpty()) {
            char first = normalized.charAt(0);
            if (Character.isLetterOrDigit(first) || first == '[' || first == '【') {
                break;
            }
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    /**
     * 提取客户基础字段，并同步默认地址联系人信息。
     *
     * @param rawFields 原始字段映射
     * @param draft 草稿对象
     * @param parsedFields 字段轨迹列表
     */
    private void fillBasicFields(Map<String, String> rawFields,
                                 CustomerProfileSaveDto draft,
                                 List<CustomerIntakeParsedFieldDto> parsedFields) {
        String customerName = firstNonBlank(rawFields, "联系人", "客户", "姓名");
        if (StringUtils.isNotBlank(customerName)) {
            draft.setCustomerName(customerName);
            draft.getAddresses().get(0).setContactName(customerName);
            addParsedField(parsedFields, "联系人", customerName, "customerName", customerName);
        }

        String phone = firstNonBlank(rawFields, "电话", "手机号", "手机");
        if (StringUtils.isNotBlank(phone)) {
            draft.setPhone(phone);
            draft.getAddresses().get(0).setContactPhone(phone);
            addParsedField(parsedFields, "电话", phone, "phone", phone);
        }

        String address = firstNonBlank(rawFields, "地址", "配送地址");
        if (StringUtils.isNotBlank(address)) {
            draft.getAddresses().get(0).setAddressDetail(address);
            addParsedField(parsedFields, "地址", address, "addresses[0].addressDetail", address);
        }

        String customerCode = firstNonBlank(rawFields, "客户编号");
        if (StringUtils.isNotBlank(customerCode)) {
            draft.setCustomerCode(customerCode);
            addParsedField(parsedFields, "客户编号", customerCode, "customerCode", customerCode);
        }
    }

    /**
     * 组装医嘱和特殊要求相关文本，并识别米饭类型。
     *
     * @param rawFields 原始字段映射
     * @param draft 草稿对象
     * @param parsedFields 字段轨迹列表
     */
    private void fillMedicalAndSpecialFields(Map<String, String> rawFields,
                                             CustomerProfileSaveDto draft,
                                             List<CustomerIntakeParsedFieldDto> parsedFields) {
        List<String> medicalSegments = new ArrayList<>();
        appendSegment(medicalSegments, "目前身体感受", rawFields.get("目前身体感受"));
        appendSegment(medicalSegments, "血糖血压情况", firstNonBlank(rawFields, "血糖血压情况", "血糖血压情况 "));
        appendSegment(medicalSegments, "缺铁、缺钙", firstNonBlank(rawFields, "缺铁、缺钙", "缺铁缺钙"));
        appendSegment(medicalSegments, "是否有贫血", rawFields.get("是否有贫血"));
        appendSegment(medicalSegments, "尊医嘱", rawFields.get("尊医嘱"));
        if (!medicalSegments.isEmpty()) {
            String medicalRequirements = String.join("；", medicalSegments);
            draft.setMedicalRequirements(medicalRequirements);
            addParsedField(parsedFields, "目前情况", medicalRequirements, "medicalRequirements", medicalRequirements);
        }

        List<String> specialSegments = new ArrayList<>();
        String cannotEat = firstNonBlank(rawFields, "不能吃的");
        String specialRequirement = firstNonBlank(rawFields, "特殊要求");
        String lunchTime = firstNonBlank(rawFields, "午餐开始配送时间");
        String dinnerTime = firstNonBlank(rawFields, "晚餐开始配送时间");
        String riceType = detectRiceType(cannotEat, specialRequirement);

        appendValueSegment(specialSegments, specialRequirement);
        appendValueSegment(specialSegments, cannotEat);
        appendSegment(specialSegments, "午餐开始配送时间", lunchTime);
        appendSegment(specialSegments, "晚餐开始配送时间", dinnerTime);
        if (!specialSegments.isEmpty()) {
            String specialRequirements = String.join("；", specialSegments);
            draft.setSpecialRequirements(specialRequirements);
            addParsedField(parsedFields, "特殊要求", specialRequirements, "specialRequirements", specialRequirements);
        }

        if (StringUtils.isNotBlank(riceType)) {
            draft.getOrderInfo().setRiceType(riceType);
            addParsedField(parsedFields, "米饭类型", riceType, "orderInfo.riceType", riceType);
        }
    }

    /**
     * 解析首单相关字段，包括套餐、餐数、排餐模式、菜品配置和过敏信息。
     *
     * @param rawFields 原始字段映射
     * @param draft 草稿对象
     * @param issues 解析问题列表
     * @param parsedFields 字段轨迹列表
     */
    private void fillOrderInfo(Map<String, String> rawFields,
                               CustomerProfileSaveDto draft,
                               List<CustomerIntakeIssueDto> issues,
                               List<CustomerIntakeParsedFieldDto> parsedFields) {
        CustomerProfileSaveDto.OrderInfoDto orderInfo = draft.getOrderInfo();

        String source = firstNonBlank(rawFields, "来源", "客户来源", "销售渠道");
        if (StringUtils.isNotBlank(source)) {
            orderInfo.setCustomerSource(source);
            addParsedField(parsedFields, "来源", source, "orderInfo.customerSource", source);
        }

        String packageText = firstNonBlank(rawFields, "套餐", "父套餐");
        if (StringUtils.isNotBlank(packageText)) {
            ParentPackage matched = matchParentPackage(packageText);
            if (matched != null) {
                orderInfo.setParentPackageId(matched.getId());
            }
            addParsedField(parsedFields, "套餐", packageText, "orderInfo.parentPackageId",
                    matched == null ? packageText : matched.getId());
        }

        String mealTypeText = firstNonBlank(rawFields, "餐次");
        applyMealType(orderInfo, mealTypeText);
        if (StringUtils.isNotBlank(mealTypeText)) {
            addParsedField(parsedFields, "餐次", mealTypeText, "orderInfo.mealType", orderInfo.getMealType());
        }

        String deliveryModeText = firstNonBlank(rawFields, "配送日期", "排餐模式");
        applyScheduleMode(orderInfo, deliveryModeText);
        if (StringUtils.isNotBlank(deliveryModeText)) {
            addParsedField(parsedFields, "配送日期", deliveryModeText, "orderInfo.scheduleMode", orderInfo.getScheduleMode());
        }

        String startDateText = firstNonBlank(rawFields, "开始日期", "订单开始日期", "开始配送日期");
        if (StringUtils.isNotBlank(startDateText) && startDateText.matches("\\d{4}-\\d{2}-\\d{2}")) {
            orderInfo.setStartDate(startDateText);
            addParsedField(parsedFields, "开始日期", startDateText, "orderInfo.startDate", startDateText);
        }

        Integer lunchDinnerCount = parseCount(firstNonBlank(rawFields, "餐数", "合计"));
        if (lunchDinnerCount != null) {
            orderInfo.setLunchDinnerCount(lunchDinnerCount);
            recalculateTotalCount(orderInfo);
            addParsedField(parsedFields, "餐数", String.valueOf(lunchDinnerCount), "orderInfo.lunchDinnerCount", lunchDinnerCount);
        }

        Integer breakfastCount = parseCount(firstNonBlank(rawFields, "早餐数"));
        if (breakfastCount != null) {
            orderInfo.setBreakfastCount(breakfastCount);
            recalculateTotalCount(orderInfo);
            addParsedField(parsedFields, "早餐数", String.valueOf(breakfastCount), "orderInfo.breakfastCount", breakfastCount);
        }

        Integer soupCount = parseCount(firstNonBlank(rawFields, "汤数", "是否含汤"));
        if (soupCount != null) {
            orderInfo.setSoupCount(soupCount);
            addParsedField(parsedFields, "汤数", String.valueOf(soupCount), "orderInfo.soupCount", soupCount);
        }

        String dishConfig = firstNonBlank(rawFields, "菜品配置");
        if (StringUtils.isNotBlank(dishConfig)) {
            Matcher matcher = DISH_CONFIG_PATTERN.matcher(dishConfig.replace("（", "(").replace("）", ")"));
            if (matcher.find()) {
                orderInfo.setMainDishCount(parseInteger(matcher.group(1), 0));
                orderInfo.setSideDishCount(parseInteger(matcher.group(2), 0));
                orderInfo.setVegCount(parseInteger(matcher.group(3), 0));
                orderInfo.setSoupCount(parseInteger(matcher.group(4), orderInfo.getSoupCount()));
                addParsedField(parsedFields, "菜品配置", dishConfig, "orderInfo.mainDishCount",
                        Arrays.asList(orderInfo.getMainDishCount(), orderInfo.getSideDishCount(),
                                orderInfo.getVegCount(), orderInfo.getSoupCount()));
            }
        }

        String allergyTags = firstNonBlank(rawFields, "过敏食物");
        if (StringUtils.isNotBlank(allergyTags)) {
            List<String> tagList = splitCsvValues(allergyTags);
            draft.setAllergyTags(tagList);
            addParsedField(parsedFields, "过敏食物", allergyTags, "allergyTags", tagList);
        }

        String excludedDishNames = firstNonBlank(rawFields, "排除菜品");
        if (StringUtils.isNotBlank(excludedDishNames)) {
            List<String> excludedDishes = splitCsvValues(excludedDishNames);
            addIssue(issues, "WARN", "excludedDishIds", "排除菜品需要客服确认并选择系统菜品", excludedDishNames);
            addParsedField(parsedFields, "排除菜品", excludedDishNames, "excludedDishIds", excludedDishes);
        }

        String mealCategory = firstNonBlank(rawFields, "餐别");
        if (StringUtils.isNotBlank(mealCategory) && StringUtils.isBlank(packageText)) {
            addIssue(issues, "ERROR", "orderInfo.parentPackageId", "套餐必须使用系统父套餐名称或编码，请填写“套餐”字段", mealCategory);
        }
    }

    /**
     * 汇总必填字段和阻塞保存的校验问题。
     *
     * @param rawFields 原始字段映射
     * @param draft 草稿对象
     * @param issues 解析问题列表
     */
    private void collectRequiredIssues(Map<String, String> rawFields,
                                       CustomerProfileSaveDto draft,
                                       List<CustomerIntakeIssueDto> issues) {
        if (StringUtils.isBlank(draft.getCustomerName())) {
            addIssue(issues, "ERROR", "customerName", "联系人不能为空", firstNonBlank(rawFields, "联系人", "客户", "姓名"));
        }
        if (StringUtils.isBlank(draft.getPhone())) {
            addIssue(issues, "ERROR", "phone", "电话不能为空", firstNonBlank(rawFields, "电话", "手机号", "手机"));
        } else if (!PHONE_PATTERN.matcher(draft.getPhone()).matches()) {
            addIssue(issues, "ERROR", "phone", "手机号格式不正确", draft.getPhone());
        }
        if (draft.getAddresses() == null
                || draft.getAddresses().isEmpty()
                || StringUtils.isBlank(draft.getAddresses().get(0).getAddressDetail())) {
            addIssue(issues, "ERROR", "addresses[0].addressDetail", "地址不能为空", firstNonBlank(rawFields, "地址", "配送地址"));
        }
        if (draft.getOrderInfo().getParentPackageId() == null) {
            addIssue(issues, "ERROR", "orderInfo.parentPackageId", "套餐必须使用系统父套餐名称或编码", firstNonBlank(rawFields, "套餐", "父套餐", "餐别"));
        }
        if (draft.getOrderInfo().getLunchDinnerCount() == null || draft.getOrderInfo().getLunchDinnerCount() <= 0) {
            addIssue(issues, "ERROR", "orderInfo.lunchDinnerCount", "餐数不能为空且必须大于0", firstNonBlank(rawFields, "餐数", "合计"));
        }
        if (isZero(draft.getOrderInfo().getMainDishCount())
                && isZero(draft.getOrderInfo().getSideDishCount())
                && isZero(draft.getOrderInfo().getVegCount())) {
            addIssue(issues, "ERROR", "orderInfo.mainDishCount", "菜品配置不能为空，请填写：菜品配置：2主1副1素0汤", firstNonBlank(rawFields, "菜品配置"));
        }
    }

    /**
     * 按父套餐名称或编码匹配已启用的系统父套餐。
     *
     * @param packageText 话术中的套餐文本
     * @return 匹配到的父套餐，未匹配到时返回 null
     */
    private ParentPackage matchParentPackage(String packageText) {
        if (StringUtils.isBlank(packageText) || parentPackageMapper == null) {
            return null;
        }
        QueryWrapper<ParentPackage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1)
                .and(wrapper -> wrapper.eq("package_name", packageText).or().eq("package_code", packageText))
                .last("limit 1");
        return parentPackageMapper.selectOne(queryWrapper);
    }

    /**
     * 根据餐次描述推导订单餐次类型和开始餐次。
     *
     * @param orderInfo 首单草稿
     * @param mealTypeText 话术中的餐次描述
     */
    private void applyMealType(CustomerProfileSaveDto.OrderInfoDto orderInfo, String mealTypeText) {
        if (StringUtils.isBlank(mealTypeText)) {
            return;
        }
        String normalized = mealTypeText.trim();
        if (normalized.contains("午餐+晚餐") || normalized.contains("午晚")) {
            orderInfo.setMealType("LUNCH_DINNER");
            orderInfo.setStartMealType("LUNCH");
            return;
        }
        if (normalized.contains("晚餐")) {
            orderInfo.setMealType("DINNER");
            orderInfo.setStartMealType("DINNER");
            return;
        }
        if (normalized.contains("午餐")) {
            orderInfo.setMealType("LUNCH");
            orderInfo.setStartMealType("LUNCH");
            return;
        }
        if (normalized.contains("早餐")) {
            orderInfo.setMealType("ALL");
            orderInfo.setStartMealType("BREAKFAST");
        }
    }

    /**
     * 根据配送描述推导排餐模式和送餐日期。
     *
     * @param orderInfo 首单草稿
     * @param scheduleModeText 话术中的配送模式描述
     */
    private void applyScheduleMode(CustomerProfileSaveDto.OrderInfoDto orderInfo, String scheduleModeText) {
        if (StringUtils.isBlank(scheduleModeText)) {
            return;
        }
        String normalized = scheduleModeText.trim();
        if ("默认等通知配送".equals(normalized) || normalized.contains("指定日期")) {
            orderInfo.setScheduleMode("SCHEDULE");
            orderInfo.setDeliveryDates(null);
            return;
        }
        if (normalized.contains("每天送")) {
            orderInfo.setScheduleMode("DAILY");
            orderInfo.setDeliveryDates(null);
            return;
        }
        if (normalized.contains("工作日")) {
            orderInfo.setScheduleMode("WEEKDAY");
            orderInfo.setDeliveryDates(null);
            return;
        }
        if (normalized.contains("周末")) {
            orderInfo.setScheduleMode("WEEKEND");
            orderInfo.setDeliveryDates(null);
            return;
        }
        if (normalized.matches("\\d{4}-\\d{2}-\\d{2}(\\s*,\\s*\\d{4}-\\d{2}-\\d{2})*")) {
            orderInfo.setScheduleMode("SCHEDULE");
            orderInfo.setDeliveryDates(JSON.toJSONString(splitCsvValues(normalized)));
        }
    }

    /**
     * 从“不能吃的/特殊要求”文本中识别米饭类型偏好。
     *
     * @param cannotEat 不能吃的原文
     * @param specialRequirement 特殊要求原文
     * @return 识别出的米饭类型，未识别时返回 null
     */
    private String detectRiceType(String cannotEat, String specialRequirement) {
        List<String> candidates = Arrays.asList(cannotEat, specialRequirement);
        for (String candidate : candidates) {
            if (StringUtils.isBlank(candidate)) {
                continue;
            }
            if (candidate.contains("糙米")) {
                return "糙米";
            }
            if (candidate.contains("黑米")) {
                return "黑米饭";
            }
        }
        return null;
    }

    /**
     * 从文本中提取整数餐数，兼容“不含汤/无汤”这类 0 值表达。
     *
     * @param text 原始文本
     * @return 解析结果，无法识别时返回 null
     */
    private Integer parseCount(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.contains("不含汤") || normalized.contains("无汤")) {
            return 0;
        }
        Matcher matcher = COUNT_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return parseInteger(matcher.group(1), null);
        }
        return null;
    }

    /**
     * 将字符串解析为整数，解析失败时回退到默认值。
     *
     * @param text 待解析文本
     * @param defaultValue 默认值
     * @return 解析后的整数
     */
    private Integer parseInteger(String text, Integer defaultValue) {
        if (StringUtils.isBlank(text)) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 根据早餐数和午晚餐数重算总餐数。
     *
     * @param orderInfo 首单草稿
     */
    private void recalculateTotalCount(CustomerProfileSaveDto.OrderInfoDto orderInfo) {
        orderInfo.setTotalCount(safeInt(orderInfo.getBreakfastCount()) + safeInt(orderInfo.getLunchDinnerCount()));
    }

    /**
     * 安全读取整数值，空值按 0 处理。
     *
     * @param value 原始整数
     * @return 非空整数结果
     */
    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 判断数量字段是否为空或为 0。
     *
     * @param value 数量值
     * @return true 表示为空或 0
     */
    private boolean isZero(Integer value) {
        return value == null || value == 0;
    }

    /**
     * 将逗号分隔文本拆分为字符串列表。
     *
     * @param text 原始文本
     * @return 去空白后的值列表
     */
    private List<String> splitCsvValues(String text) {
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }
        String[] parts = text.split("[,，]");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                result.add(part.trim());
            }
        }
        return result;
    }

    /**
     * 按候选标签顺序获取第一个非空字段值。
     *
     * @param fields 原始字段映射
     * @param keys 候选字段名
     * @return 第一个非空字段值
     */
    private String firstNonBlank(Map<String, String> fields, String... keys) {
        if (fields == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = fields.get(key);
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 追加一条解析问题。
     *
     * @param issues 问题列表
     * @param level 问题级别
     * @param field 目标字段
     * @param message 提示文案
     * @param sourceValue 原始值
     */
    private void addIssue(List<CustomerIntakeIssueDto> issues,
                          String level,
                          String field,
                          String message,
                          String sourceValue) {
        CustomerIntakeIssueDto issue = new CustomerIntakeIssueDto();
        issue.setLevel(level);
        issue.setField(field);
        issue.setMessage(message);
        issue.setSourceValue(sourceValue);
        issues.add(issue);
    }

    /**
     * 记录一条字段解析轨迹。
     *
     * @param parsedFields 轨迹列表
     * @param label 原始标签
     * @param rawValue 原始值
     * @param targetField 目标字段
     * @param normalizedValue 规范化结果
     */
    private void addParsedField(List<CustomerIntakeParsedFieldDto> parsedFields,
                                String label,
                                String rawValue,
                                String targetField,
                                Object normalizedValue) {
        CustomerIntakeParsedFieldDto fieldDto = new CustomerIntakeParsedFieldDto();
        fieldDto.setLabel(label);
        fieldDto.setRawValue(rawValue);
        fieldDto.setTargetField(targetField);
        fieldDto.setNormalizedValue(normalizedValue);
        parsedFields.add(fieldDto);
    }

    /**
     * 以“标签：值”的形式向文本片段列表追加内容。
     *
     * @param segments 片段列表
     * @param label 标签
     * @param value 值
     */
    private void appendSegment(List<String> segments, String label, String value) {
        if (StringUtils.isNotBlank(value)) {
            segments.add(label + "：" + value.trim());
        }
    }

    /**
     * 直接向文本片段列表追加非空原文值。
     *
     * @param segments 片段列表
     * @param value 原始值
     */
    private void appendValueSegment(List<String> segments, String value) {
        if (StringUtils.isNotBlank(value)) {
            segments.add(value.trim());
        }
    }
}
