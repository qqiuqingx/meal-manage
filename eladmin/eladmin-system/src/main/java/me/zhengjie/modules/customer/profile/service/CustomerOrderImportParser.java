package me.zhengjie.modules.customer.profile.service;

import me.zhengjie.modules.customer.profile.domain.ParsedCustomer;
import me.zhengjie.modules.customer.profile.domain.ParsedWorkbook;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportAddressDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueCategory;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportMealCellDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 客户与首单批量导入的工作簿解析器。
 *
 * <p>只做「读工作簿 → 聚合客户草稿 → 逐行校验」的纯解析工作，不访问数据库、不写任何数据，
 * 因此预览阶段可以安全地反复调用。父套餐映射、编号池校验与重复建档判定由
 * {@link CustomerProfileImportService} 在只读校验阶段补齐。</p>
 *
 * <p>列定位策略：按第 1 行标题联合第 2、3 行日历表头定位，兼容后续版本增删列，
 * 不把第三版的具体列字母写死；标题顺序或日历结构不符合预期时整体拒绝解析。</p>
 *
 * <p>聚合策略：A/B/C/D 列按合并单元格与编号继承归并续行，同一位客户可能由多行组成
 * （例如午餐一行、晚餐一行），送餐模式与餐次取各行的并集，餐数按行累加。</p>
 *
 * @author qqx
 * @date 2026-09-24
 **/
@Component
public class CustomerOrderImportParser {

    private static final Logger log = LoggerFactory.getLogger(CustomerOrderImportParser.class);

    /**
     * 业务工作表名称模式，第三版模板为「26年9月」
     */
    private static final Pattern SHEET_NAME_PATTERN = Pattern.compile("^(\\d{2,4})年(\\d{1,2})月$");

    private static final String HEADER_ORIGINAL_CODE = "编号";
    private static final String HEADER_NEW_CODE = "新编号";
    private static final String HEADER_PHONE = "电话";
    private static final String HEADER_ADDRESS = "地址";
    private static final String HEADER_REMARK = "备注信息";
    private static final String HEADER_SPECIAL = "特殊要求";
    private static final String HEADER_SOUP = "含汤";
    private static final String HEADER_DELIVERY = "每日/午餐/晚餐";
    private static final String HEADER_MEAL_COUNT = "餐数";
    private static final String HEADER_REMAINING = "剩余餐数";
    private static final String HEADER_CALENDAR = "消费记录";
    private static final String HEADER_GRID_TOTAL = "合计餐数";

    /**
     * 固定标题列，按第三版模板自左向右排列
     */
    private static final List<String> ORDERED_HEADERS = Collections.unmodifiableList(Arrays.asList(
            HEADER_ORIGINAL_CODE, HEADER_NEW_CODE, HEADER_PHONE, HEADER_ADDRESS, HEADER_REMARK,
            HEADER_SPECIAL, HEADER_SOUP, HEADER_DELIVERY, HEADER_MEAL_COUNT, HEADER_REMAINING));

    /**
     * 每日格数：早/中/晚
     */
    private static final int MEALS_PER_DAY = 3;

    /**
     * 早、中、晚在日格内的偏移
     */
    private static final int OFFSET_BREAKFAST = 0;
    private static final int OFFSET_LUNCH = 1;
    private static final int OFFSET_DINNER = 2;

    /**
     * 默认米饭类型
     */
    private static final String DEFAULT_RICE_TYPE = "白米饭";

    /**
     * 合法手机号
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * 从配送电话描述中选择一条可用于原有单值地址联系电话的手机号。
     */
    private static final Pattern PHONE_IN_TEXT_PATTERN =
            Pattern.compile("(?<!\\d)1[3-9](?:[\\s\\u3000\\-－—]*\\d){9}(?!\\d)");

    /**
     * 编号格式：字母前缀 + 数字
     */
    private static final Pattern CODE_PATTERN = Pattern.compile("^([A-Za-z]+)(\\d+)$");

    /**
     * 电话清洗时剔除的分隔符与空白
     */
    private static final Pattern PHONE_SEPARATOR_PATTERN =
            Pattern.compile("[\\s\\u3000\\-－—()（）\\[\\]【】/、,，.。．·]+");

    /**
     * 地址字段标签；长标签排在前面，避免「地址」抢先匹配「送餐地址」
     */
    private static final Pattern ADDRESS_LABEL_PATTERN = Pattern.compile(
            "(送餐联系人|工作日地址|周末地址|送餐地址|配送电话|送餐电话|备用电话|联系电话|联系人|收件人|客户|手机号|手机|电话\\d*|姓名|地址)\\s*[:：]"
                    + "|【\\s*(送餐地址|联系人|姓名|配送电话|送餐电话|备用电话|电话\\d*|地址)\\s*】");

    /**
     * 描述每餐多份配送但无法从描述确定份数构成的写法，例如「每餐两份一样的」「午餐送两份」
     */
    private static final Pattern MULTI_PORTION_PATTERN = Pattern.compile("[两二2三3]\\s*份");

    /**
     * 解析工作簿字节内容并聚合客户草稿。
     *
     * @param content 工作簿字节内容
     * @param fileHash 工作簿 SHA-256 摘要，由调用方计算，用于预览与提交之间的一致性校验
     * @param importDate 计划导入日期，晚于该日期的非零午晚餐格视为未来计划；为空时取当天
     * @return 解析结果；结构校验失败时 {@code structureValid=false} 且客户列表为空
     */
    public ParsedWorkbook parse(byte[] content, String fileHash, LocalDate importDate) {
        ParsedWorkbook result = new ParsedWorkbook();
        result.setFileHash(fileHash);
        result.setImportDate(importDate != null ? importDate : LocalDate.now());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = resolveBusinessSheet(workbook);
            if (sheet == null) {
                result.getIssues().add(CustomerImportIssueDto.of(CustomerImportIssueCategory.WORKBOOK_ERROR, null, null,
                        "未找到形如「26年9月」的月份工作表，请确认上传的是第三版客户用餐计划表"));
                return result;
            }
            result.setSheetName(sheet.getSheetName());

            YearMonth calendarMonth = parseCalendarMonth(sheet.getSheetName());
            if (calendarMonth == null) {
                result.getIssues().add(CustomerImportIssueDto.of(CustomerImportIssueCategory.WORKBOOK_ERROR, null, null,
                        "工作表名称「" + sheet.getSheetName() + "」无法解析出年月，无法还原日格对应日期"));
                return result;
            }
            result.setCalendarMonthStart(calendarMonth.atDay(1));

            CalendarLayout layout = resolveLayout(sheet, result);
            if (layout == null) {
                return result;
            }
            if (layout.dayCount > calendarMonth.lengthOfMonth()) {
                result.getIssues().add(CustomerImportIssueDto.of(CustomerImportIssueCategory.WORKBOOK_ERROR, 2, null,
                        "日历表头天数 " + layout.dayCount + " 超出 " + sheet.getSheetName() + " 的实际天数"));
                return result;
            }

            result.setStructureValid(true);
            List<ParsedCustomer> customers = aggregateCustomers(sheet, layout, calendarMonth, result);
            result.setCustomers(customers);
            result.setCustomerCount(customers.size());
            log.info("客户导入工作簿解析完成: sheet={}, 数据行={}, 客户草稿={}",
                    sheet.getSheetName(), result.getDataRowCount(), customers.size());
            return result;
        } catch (Exception e) {
            log.warn("客户导入工作簿解析失败，异常类型: {}", e.getClass().getSimpleName());
            result.setStructureValid(false);
            result.getIssues().add(CustomerImportIssueDto.of(CustomerImportIssueCategory.WORKBOOK_ERROR, null, null,
                    "工作簿无法解析：" + e.getMessage()));
            return result;
        }
    }

    /**
     * 选择业务工作表：忽略「客户禁忌」与 WPS 保留页，取名称形如「26年9月」的工作表。
     *
     * @param workbook 工作簿
     * @return 业务工作表；不存在时返回 null
     */
    private Sheet resolveBusinessSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String name = sheet.getSheetName();
            if (name == null || name.contains("客户禁忌") || name.startsWith("WpsReserved")) {
                continue;
            }
            if (SHEET_NAME_PATTERN.matcher(name.trim()).matches()) {
                return sheet;
            }
        }
        return null;
    }

    /**
     * 解析工作表名称中的年月。
     *
     * @param sheetName 工作表名称，如「26年9月」
     * @return 年月；无法解析时返回 null
     */
    private YearMonth parseCalendarMonth(String sheetName) {
        if (sheetName == null) {
            return null;
        }
        Matcher matcher = SHEET_NAME_PATTERN.matcher(sheetName.trim());
        if (!matcher.matches()) {
            return null;
        }
        int year = Integer.parseInt(matcher.group(1));
        if (year < 100) {
            year += 2000;
        }
        int month = Integer.parseInt(matcher.group(2));
        if (month < 1 || month > 12) {
            return null;
        }
        return YearMonth.of(year, month);
    }

    /**
     * 定位表头列与日历列。
     *
     * @param sheet 业务工作表
     * @param result 解析结果，用于登记结构错误
     * @return 列布局；结构不符合第三版模板时返回 null
     */
    private CalendarLayout resolveLayout(Sheet sheet, ParsedWorkbook result) {
        int firstRow = sheet.getFirstRowNum();
        Row headerRow = sheet.getRow(firstRow);
        if (headerRow == null) {
            result.getIssues().add(CustomerImportIssueDto.of(CustomerImportIssueCategory.WORKBOOK_ERROR, 1, null,
                    "工作表第 1 行为空，缺少表头"));
            return null;
        }

        Map<String, Integer> titleColumns = new LinkedHashMap<>();
        int lastColumn = Math.max(headerRow.getLastCellNum(), 1);
        for (int c = 0; c < lastColumn; c++) {
            String title = text(headerRow.getCell(c));
            if (!title.isEmpty()) {
                titleColumns.putIfAbsent(title, c);
            }
        }

        CalendarLayout layout = new CalendarLayout();
        int previous = -1;
        for (String header : ORDERED_HEADERS) {
            Integer column = titleColumns.get(header);
            if (column == null) {
                result.getIssues().add(CustomerImportIssueDto.of(CustomerImportIssueCategory.WORKBOOK_ERROR, 1, null,
                        "第 1 行缺少表头「" + header + "」，与第三版模板不一致"));
                return null;
            }
            if (column <= previous) {
                result.getIssues().add(CustomerImportIssueDto.of(CustomerImportIssueCategory.WORKBOOK_ERROR, 1, null,
                        "表头顺序错位：「" + header + "」应排在上一列之后"));
                return null;
            }
            previous = column;
            layout.columnIndex.put(header, column);
        }

        Integer calendarStart = titleColumns.get(HEADER_CALENDAR);
        if (calendarStart == null || calendarStart <= previous) {
            result.getIssues().add(CustomerImportIssueDto.of(CustomerImportIssueCategory.WORKBOOK_ERROR, 1, null,
                    "第 1 行缺少「" + HEADER_CALENDAR + "」表头，无法定位日格区"));
            return null;
        }
        layout.calendarStart = calendarStart;

        Row dayRow = sheet.getRow(firstRow + 1);
        Row mealRow = sheet.getRow(firstRow + 2);
        if (dayRow == null || mealRow == null) {
            result.getIssues().add(CustomerImportIssueDto.of(CustomerImportIssueCategory.WORKBOOK_ERROR, 2, null,
                    "缺少第 2、3 行日历表头"));
            return null;
        }

        int dayCount = 0;
        while (dayCount < 31) {
            int dayColumn = calendarStart + MEALS_PER_DAY * dayCount;
            Integer expectedDay = integer(dayRow.getCell(dayColumn));
            if (expectedDay == null || expectedDay != dayCount + 1) {
                break;
            }
            if (!"早".equals(text(mealRow.getCell(dayColumn)))
                    || !"中".equals(text(mealRow.getCell(dayColumn + OFFSET_LUNCH)))
                    || !"晚".equals(text(mealRow.getCell(dayColumn + OFFSET_DINNER)))) {
                result.getIssues().add(CustomerImportIssueDto.of(CustomerImportIssueCategory.WORKBOOK_ERROR, 3, null,
                        "第 " + (dayCount + 1) + " 天的早/中/晚表头不完整"));
                return null;
            }
            dayCount++;
        }
        if (dayCount == 0) {
            result.getIssues().add(CustomerImportIssueDto.of(CustomerImportIssueCategory.WORKBOOK_ERROR, 2, null,
                    "未识别到任何日格日序，请确认使用第三版模板"));
            return null;
        }
        layout.dayCount = dayCount;

        int gridTotalColumn = calendarStart + MEALS_PER_DAY * dayCount;
        if (!HEADER_GRID_TOTAL.equals(text(headerRow.getCell(gridTotalColumn)))) {
            result.getIssues().add(CustomerImportIssueDto.of(CustomerImportIssueCategory.WORKBOOK_ERROR, 1, null,
                    "日格区右侧缺少「" + HEADER_GRID_TOTAL + "」列，无法交叉核对公式缓存"));
            return null;
        }
        layout.gridTotalColumn = gridTotalColumn;
        return layout;
    }

    /**
     * 按编号聚合客户草稿。
     *
     * <p>合并单元格的续行没有自己的编号，沿用上方最近一次出现的有效编号；
     * 同一编号的多个业务行视为同一位客户的不同餐次计划（例如午餐一行、晚餐一行）。</p>
     *
     * @param sheet 业务工作表
     * @param layout 列布局
     * @param calendarMonth 工作日历所属年月
     * @param result 解析结果
     * @return 客户草稿列表
     */
    private List<ParsedCustomer> aggregateCustomers(Sheet sheet, CalendarLayout layout, YearMonth calendarMonth,
                                                    ParsedWorkbook result) {
        Map<String, List<RawRow>> grouped = new LinkedHashMap<>();
        int dataRowCount = 0;
        String inheritedCode = null;

        for (int rowIndex = sheet.getFirstRowNum() + 3; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            RawRow raw = readRow(row, layout, calendarMonth);
            if (!raw.hasBusinessContent()) {
                continue;
            }
            dataRowCount++;
            if (isBlank(raw.codeB)) {
                raw.effectiveCode = !isBlank(raw.codeA) ? raw.codeA : inheritedCode;
            } else {
                raw.effectiveCode = raw.codeB;
            }
            if (!isBlank(raw.effectiveCode)) {
                inheritedCode = raw.effectiveCode;
            }
            if (isBlank(raw.effectiveCode)) {
                result.getIssues().add(CustomerImportIssueDto.of(CustomerImportIssueCategory.PROFILE_ERROR,
                        raw.sourceRow, null, "无法解析有效编号，且上方没有可沿用的编号"));
                continue;
            }
            grouped.computeIfAbsent(raw.effectiveCode, key -> new ArrayList<>()).add(raw);
        }
        result.setDataRowCount(dataRowCount);

        List<ParsedCustomer> customers = new ArrayList<>();
        for (Map.Entry<String, List<RawRow>> entry : grouped.entrySet()) {
            customers.add(buildCustomer(entry.getKey(), entry.getValue(), result));
        }
        return customers;
    }

    /**
     * 把同一编号下的多行业务明细合并为一位客户草稿并完成字段级校验。
     *
     * @param code 有效编号
     * @param rows 该编号下的明细行，按源行号升序
     * @param result 解析结果
     * @return 客户草稿
     */
    private ParsedCustomer buildCustomer(String code, List<RawRow> rows, ParsedWorkbook result) {
        ParsedCustomer customer = new ParsedCustomer();
        customer.setEffectiveCode(code);
        customer.setCustomerName(code);
        RawRow first = rows.get(0);
        customer.setOriginalCodeA(first.codeA);
        customer.setOriginalCodeB(first.codeB);
        for (RawRow row : rows) {
            customer.getSourceRows().add(row.sourceRow);
        }

        validateCodeAndPhone(customer, code, rows);
        mergeProfileText(customer, rows);
        resolveDelivery(customer, rows);
        String soupText = resolveSoup(customer, rows);
        resolveDishSpec(customer, rows);
        parseAddresses(customer, joinAddresses(rows));
        resolveMealCounts(customer, rows, result, code, soupText);
        return customer;
    }

    /**
     * 校验编号格式与手机号唯一性。
     *
     * @param customer 客户草稿
     * @param code 有效编号
     * @param rows 明细行
     */
    private void validateCodeAndPhone(ParsedCustomer customer, String code, List<RawRow> rows) {
        Set<String> phones = new LinkedHashSet<>();
        Set<String> addresses = new LinkedHashSet<>();
        for (RawRow row : rows) {
            String normalized = normalizePhone(row.phone);
            if (!normalized.isEmpty()) {
                phones.add(normalized);
            }
            if (!isBlank(row.address)) {
                addresses.add(collapseWhitespace(row.address));
            }
        }
        if (phones.size() > 1) {
            customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                    "同一编号出现 " + phones.size() + " 个不同手机号，无法确认是否同一客户，请业务改为唯一编号");
        } else if (phones.isEmpty()) {
            customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR, "客户手机号缺失");
        } else {
            String phone = phones.iterator().next();
            customer.setPhoneNormalized(phone);
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                        "手机号「" + maskPhone(phone) + "」不是有效的 11 位手机号");
            }
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR, "编号「" + code + "」不是「字母前缀+数字」格式");
        }
        if (addresses.size() > 1) {
            customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                    "同一编号出现多个不同地址，无法确认是否同一客户，请业务核对编号与地址");
        }
    }

    /**
     * 合并备注与特殊要求，并根据备注、特殊要求或送餐描述判定暂停。
     *
     * @param customer 客户草稿
     * @param rows 明细行
     */
    private void mergeProfileText(ParsedCustomer customer, List<RawRow> rows) {
        customer.setRemark(joinDistinct(rows, row -> row.remark));
        customer.setSpecialRequirements(joinDistinct(rows, row -> row.special));
        customer.setPaused(customer.getRemark().contains("等通知")
                || customer.getSpecialRequirements().contains("等通知")
                || rows.stream().anyMatch(row -> row.delivery != null && row.delivery.contains("等通知")));
    }

    /**
     * 合并各行的送餐描述，得到送餐模式与餐次类型。
     *
     * <p>同一客户的多行可能分别描述午餐与晚餐，餐次取并集；任一送餐描述含「等通知」时，
     * 订单采用待通知模式且餐次留空；送餐描述缺失的行默认按「午餐+晚餐」处理。</p>
     *
     * @param customer 客户草稿
     * @param rows 明细行
     */
    private void resolveDelivery(ParsedCustomer customer, List<RawRow> rows) {
        Set<String> scheduleModes = new LinkedHashSet<>();
        boolean lunch = false;
        boolean dinner = false;
        boolean hasMealLine = false;
        boolean hasProblem = false;
        boolean waitNotice = false;
        List<Integer> breakfastRows = new ArrayList<>();
        List<Integer> missingDeliveryRows = new ArrayList<>();

        for (RawRow row : rows) {
            String deliveryText = row.delivery;
            if (isBlank(deliveryText)) {
                missingDeliveryRows.add(row.sourceRow);
                lunch = true;
                dinner = true;
                row.mealLine = true;
                hasMealLine = true;
                continue;
            }
            boolean rowBreakfast = deliveryText.contains("早餐");
            boolean rowLunch = deliveryText.contains("午餐");
            boolean rowDinner = deliveryText.contains("晚餐");
            boolean rowWaitNotice = deliveryText.contains("等通知");
            waitNotice |= rowWaitNotice;
            if (rowBreakfast && !rowLunch && !rowDinner) {
                breakfastRows.add(row.sourceRow);
                continue;
            }
            if (rowWaitNotice) {
                scheduleModes.add("SCHEDULE");
                row.mealLine = true;
                hasMealLine = true;
                continue;
            }
            if (!rowLunch && !rowDinner) {
                customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                        "第 " + row.sourceRow + " 行「" + HEADER_DELIVERY + "」列送餐描述「" + deliveryText + "」无法识别");
                hasProblem = true;
                continue;
            }
            String mode = resolveScheduleMode(deliveryText);
            if (mode == null) {
                customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                        "第 " + row.sourceRow + " 行送餐描述「" + deliveryText + "」缺少送餐模式");
                hasProblem = true;
                continue;
            }
            scheduleModes.add(mode);
            lunch |= rowLunch;
            dinner |= rowDinner;
            row.mealLine = true;
            hasMealLine = true;
        }

        if (!hasMealLine && !hasProblem) {
            customer.addIssue(CustomerImportIssueCategory.SKIPPED, "纯早餐客户本批次不导入，已跳过");
            return;
        }
        if (!breakfastRows.isEmpty()) {
            customer.addWarning("第 " + joinRows(breakfastRows) + " 行为早餐计划，本批次不导入早餐");
        }
        if (!missingDeliveryRows.isEmpty()) {
            customer.addWarning("第 " + joinRows(missingDeliveryRows) + " 行「" + HEADER_DELIVERY
                    + "」列送餐描述缺失，默认按午餐+晚餐处理");
        }
        if (scheduleModes.size() > 1 && !waitNotice) {
            customer.addWarning("同编号多行送餐模式不一致（" + String.join("/", scheduleModes) + "），已采用首行取值，请业务确认");
        }
        if (!scheduleModes.isEmpty()) {
            customer.setScheduleMode(waitNotice ? "SCHEDULE" : scheduleModes.iterator().next());
        }
        if (waitNotice) {
            customer.setMealType(null);
            customer.addWarning("等通知送餐，订单餐次留空，需人工确认后再排餐");
        } else if (lunch && dinner) {
            customer.setMealType("LUNCH_DINNER");
        } else if (lunch) {
            customer.setMealType("LUNCH");
        } else if (dinner) {
            customer.setMealType("DINNER");
        } else {
            customer.setMealType(null);
            customer.addWarning("送餐描述未指定午晚餐餐次，订单餐次留空，需人工确认后再排餐");
        }
    }

    /**
     * 由送餐描述解析送餐模式。
     *
     * @param deliveryText 送餐描述
     * @return DAILY / WEEKDAY / WEEKEND / SCHEDULE；无法识别时返回 null
     */
    private String resolveScheduleMode(String deliveryText) {
        if (deliveryText.contains("每日")) {
            return "DAILY";
        }
        if (deliveryText.contains("工作日")) {
            return "WEEKDAY";
        }
        if (deliveryText.contains("周末")) {
            return "WEEKEND";
        }
        if (deliveryText.contains("等通知")) {
            return "SCHEDULE";
        }
        return null;
    }

    /**
     * 解析订单默认汤数。
     *
     * @param customer 客户草稿
     * @param rows 明细行
     * @return 首行非空的「含汤」列原文
     */
    private String resolveSoup(ParsedCustomer customer, List<RawRow> rows) {
        Set<String> values = new LinkedHashSet<>();
        for (RawRow row : rows) {
            if (!isBlank(row.soup)) {
                values.add(row.soup);
            }
        }
        if (values.isEmpty()) {
            customer.setSoupCount(0);
            customer.addWarning("「" + HEADER_SOUP + "」列缺失，订单默认汤数按 0 处理");
            return "";
        }
        if (values.size() > 1) {
            customer.addWarning("同编号多行「" + HEADER_SOUP + "」描述不一致（" + String.join("/", values)
                    + "），已采用首行取值，请业务确认");
        }
        String soupText = values.iterator().next();
        if ("含汤".equals(soupText)) {
            customer.setSoupCount(1);
        } else if ("不含汤".equals(soupText)) {
            customer.setSoupCount(0);
        } else {
            customer.setSoupCount(0);
            customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                    "「" + HEADER_SOUP + "」列取值「" + soupText + "」无法识别");
        }
        return soupText;
    }

    /**
     * 解析菜品规格：无副菜时副菜为 0，其余为主菜 1、副菜 1、素菜 1，米饭沿用默认一份白米饭。
     *
     * @param customer 客户草稿
     * @param rows 明细行
     */
    private void resolveDishSpec(ParsedCustomer customer, List<RawRow> rows) {
        Set<Boolean> noSideDishValues = new LinkedHashSet<>();
        for (RawRow row : rows) {
            if (!isBlank(row.special)) {
                noSideDishValues.add(row.special.contains("无副菜"));
            }
        }
        boolean noSideDish = customer.getSpecialRequirements().contains("无副菜");
        customer.setMainDishCount(1);
        customer.setSideDishCount(noSideDish ? 0 : 1);
        customer.setVegCount(1);
        customer.setRiceCount(1);
        customer.setRiceType(DEFAULT_RICE_TYPE);
        if (noSideDish) {
            customer.addWarning("特殊要求包含「无副菜」，订单规格按主菜 1、副菜 0、素菜 1 生成");
        }
        if (noSideDishValues.size() > 1) {
            customer.addWarning("同编号多行「无副菜」描述不一致，已按整条特殊要求判定，请业务确认");
        }
    }

    /**
     * 核对公式缓存并累计待导入餐数与未来逐餐计划。
     *
     * <p>只有含午晚餐的明细行才贡献餐数，早餐行即使有剩余餐数也不导入。</p>
     *
     * @param customer 客户草稿
     * @param rows 明细行
     * @param result 解析结果，提供导入日期
     * @param code 有效编号
     * @param soupText 「含汤」列原文
     */
    private void resolveMealCounts(ParsedCustomer customer, List<RawRow> rows, ParsedWorkbook result,
                                   String code, String soupText) {
        String twoPortionMarker = code + "-2含汤";
        boolean hasTwoPortionMarker = stripSpaces(customer.getSpecialRequirements()).contains(twoPortionMarker);
        boolean soupAbsent = "不含汤".equals(soupText);
        boolean sawTwoPortion = false;
        int remainingSum = 0;
        Set<String> futureCellKeys = new LinkedHashSet<>();

        for (RawRow row : rows) {
            for (String gridIssue : row.gridIssues) {
                customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR, gridIssue);
            }
            long recomputedGrid = 0L;
            for (Integer value : row.gridValues) {
                if (value != null && value > 0) {
                    recomputedGrid += value;
                }
            }
            if (row.gridTotal == null) {
                customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                        "第 " + row.sourceRow + " 行「" + HEADER_GRID_TOTAL + "」列不是有效整数，公式缓存不可用");
            } else if (!row.gridTotalBlank && row.gridTotal != recomputedGrid) {
                customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                        "第 " + row.sourceRow + " 行「" + HEADER_GRID_TOTAL + "」公式缓存为 " + row.gridTotal
                                + "，日格重算为 " + recomputedGrid + "，请在 Excel 中重算公式后重传");
            }
            if (row.mealCount == null) {
                customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                        "第 " + row.sourceRow + " 行「" + HEADER_MEAL_COUNT + "」列取值为空");
            } else if (row.mealCount < 0) {
                customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                        "第 " + row.sourceRow + " 行「" + HEADER_MEAL_COUNT + "」不能为负数");
            }
            if (row.remainingCount == null) {
                customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                        "第 " + row.sourceRow + " 行「" + HEADER_REMAINING + "」列取值为空");
            } else if (row.remainingCount < 0) {
                customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                        "第 " + row.sourceRow + " 行「" + HEADER_REMAINING + "」不能为负数");
            } else if (row.mealCount != null && row.gridTotal != null && !row.gridTotalBlank
                    && row.remainingCount != row.mealCount - row.gridTotal) {
                customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                        "第 " + row.sourceRow + " 行「" + HEADER_MEAL_COUNT + "」「" + HEADER_REMAINING + "」「"
                                + HEADER_GRID_TOTAL + "」三者关系不成立：剩余餐数应为餐数减日格合计");
            }

            if (!row.mealLine) {
                continue;
            }
            if (row.remainingCount != null && row.remainingCount > 0) {
                remainingSum += row.remainingCount;
            }
            for (Map.Entry<LocalDate, int[]> entry : row.dayCells.entrySet()) {
                LocalDate date = entry.getKey();
                if (!date.isAfter(result.getImportDate())) {
                    continue;
                }
                int[] quantities = entry.getValue();
                boolean lunchTwo = appendUniqueMealCell(customer, futureCellKeys, row.sourceRow, date, "LUNCH",
                        quantities[OFFSET_LUNCH], hasTwoPortionMarker, soupAbsent);
                boolean dinnerTwo = appendUniqueMealCell(customer, futureCellKeys, row.sourceRow, date, "DINNER",
                        quantities[OFFSET_DINNER], hasTwoPortionMarker, soupAbsent);
                sawTwoPortion |= lunchTwo || dinnerTwo;
            }
        }

        customer.setSheetMealCount(sumMealCount(rows));
        customer.setSheetRemainingCount(remainingSum);
        customer.setSheetGridTotal(sumGridTotal(rows));
        customer.setHasFutureMealCell(!customer.getFutureMealCells().isEmpty());
        validateFutureMealTypes(customer);
        if (hasTwoPortionMarker && !sawTwoPortion) {
            customer.addWarning("特殊要求描述了「" + twoPortionMarker + "」，但没有份数为 2 的未来日格");
        }
        boolean multiPortionText = MULTI_PORTION_PATTERN.matcher(customer.getSpecialRequirements()).find();
        if (multiPortionText && !sawTwoPortion) {
            customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                    "特殊要求包含多份配送描述，但无法确定每餐份数与含汤份数，请业务拆分为明确的日格份数");
        }
    }

    /**
     * 累计午晚餐明细行的「餐数」列取值，排除本批次不导入的早餐行。
     *
     * @param rows 明细行
     * @return 合计餐数；全部为空时返回 null
     */
    private Integer sumMealCount(List<RawRow> rows) {
        int sum = 0;
        boolean any = false;
        for (RawRow row : rows) {
            if (row.mealLine && row.mealCount != null) {
                sum += row.mealCount;
                any = true;
            }
        }
        return any ? sum : null;
    }

    /**
     * 累计各行「合计餐数」列取值。
     *
     * @param rows 明细行
     * @return 合计值；空白单元格按 0 累计
     */
    private Integer sumGridTotal(List<RawRow> rows) {
        int sum = 0;
        boolean any = false;
        for (RawRow row : rows) {
            if (row.gridTotal != null) {
                sum += row.gridTotal;
                any = true;
            }
        }
        return any ? sum : null;
    }

    /**
     * 追加一格未来排餐数量，并按规则判定含汤份数。
     *
     * <p>「编号-2含汤」描述只有在日格份数为 2、「含汤」列为不含汤时才被解读为第二份含汤；
     * 其他多份情形无法确定含汤构成，直接报错而不猜测。</p>
     *
     * @param customer 客户草稿
     * @param sourceRow 源行号
     * @param date 日期
     * @param mealType 餐次
     * @param quantity 份数
     * @param hasTwoPortionMarker 特殊要求中是否出现「编号-2含汤」
     * @param soupAbsent 「含汤」列是否为不含汤
     * @return 是否登记了份数为 2 的格子
     */
    private boolean appendMealCell(ParsedCustomer customer, int sourceRow, LocalDate date, String mealType,
                                   int quantity, boolean hasTwoPortionMarker, boolean soupAbsent) {
        if (quantity <= 0) {
            return false;
        }
        CustomerImportMealCellDto cell = new CustomerImportMealCellDto();
        cell.setSourceRow(sourceRow);
        cell.setDate(date.toString());
        cell.setMealType(mealType);
        cell.setQuantity(quantity);
        if (quantity == 1) {
            customer.getFutureMealCells().add(cell);
            return false;
        }
        if (quantity == 2 && hasTwoPortionMarker && soupAbsent) {
            cell.setSoupQuantity(1);
            customer.getFutureMealCells().add(cell);
            return true;
        }
        customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                "第 " + sourceRow + " 行 " + date + " " + mealTypeName(mealType) + " 份数为 " + quantity
                        + "，特殊要求未给出可确定的第二份含汤描述，请业务确认");
        return false;
    }

    /**
     * 登记唯一的日期餐次计划，拒绝跨续行重复提供同一未来单元格。
     *
     * @param customer 聚合客户草稿
     * @param existingKeys 已出现的日期餐次键集合
     * @param sourceRow 当前源行
     * @param date 日期
     * @param mealType 餐次
     * @param quantity 份数
     * @param hasTwoPortionMarker 是否有指定的第二份含汤说明
     * @param soupAbsent 「含汤」列是否为不含汤
     * @return 是否成功登记的是一格两份含汤计划
     */
    private boolean appendUniqueMealCell(ParsedCustomer customer, Set<String> existingKeys, int sourceRow,
                                         LocalDate date, String mealType, int quantity,
                                         boolean hasTwoPortionMarker, boolean soupAbsent) {
        if (quantity <= 0) {
            return false;
        }
        String key = date + "#" + mealType;
        if (!existingKeys.add(key)) {
            customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                    "第 " + sourceRow + " 行重复提供 " + date + " " + mealTypeName(mealType)
                            + " 日格，无法确认是否应累计份数");
            return false;
        }
        return appendMealCell(customer, sourceRow, date, mealType, quantity, hasTwoPortionMarker, soupAbsent);
    }

    /**
     * 核对未来午晚餐格是否包含在订单明确指定的餐次中。
     *
     * @param customer 聚合客户草稿
     */
    private void validateFutureMealTypes(ParsedCustomer customer) {
        String orderMealType = customer.getMealType();
        if (orderMealType == null || "ALL".equals(orderMealType) || "LUNCH_DINNER".equals(orderMealType)) {
            return;
        }
        for (CustomerImportMealCellDto cell : customer.getFutureMealCells()) {
            if (!orderMealType.equals(cell.getMealType())) {
                customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR,
                        "第 " + cell.getSourceRow() + " 行未来日格包含"
                                + mealTypeName(cell.getMealType()) + "，与订单餐次「" + orderMealType
                                + "」不一致，请业务确认");
            }
        }
    }

    /**
     * 解析地址列，得到默认/工作日/周末槽位。
     *
     * <p>标签无法覆盖的地址片段留在默认地址槽位；只有「已出现标签但仍存在游离片段」这种
     * 真正无法确认归属的情形才给出提示，避免正常单地址客户产生大量噪音。</p>
     *
     * @param customer 客户草稿
     * @param addressText 地址列原文
     */
    private void parseAddresses(ParsedCustomer customer, String addressText) {
        String contactName = null;
        List<String> deliveryPhones = new ArrayList<>();
        StringBuilder defaultAddress = new StringBuilder();
        StringBuilder workdayAddress = new StringBuilder();
        StringBuilder weekendAddress = new StringBuilder();
        boolean hasLabel = false;
        boolean hasUnlabeledFragmentBesideLabel = false;

        if (!isBlank(addressText)) {
            for (String line : addressText.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                Matcher matcher = ADDRESS_LABEL_PATTERN.matcher(trimmed);
                List<int[]> labelRanges = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                while (matcher.find()) {
                    labels.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
                    labelRanges.add(new int[]{matcher.start(), matcher.end()});
                }
                if (labels.isEmpty()) {
                    if (PHONE_IN_TEXT_PATTERN.matcher(trimmed).find()
                            && trimmed.matches("[\\d\\s\\u3000\\-－—()（）/、,，;；.。．·]+")) {
                        deliveryPhones.add(trimmed);
                        continue;
                    }
                    appendSegments(defaultAddress, trimmed);
                    if (hasLabel) {
                        hasUnlabeledFragmentBesideLabel = true;
                    }
                    continue;
                }
                hasLabel = true;
                String leading = trimmed.substring(0, labelRanges.get(0)[0]).trim();
                if (!leading.isEmpty()) {
                    appendSegments(defaultAddress, leading);
                    hasUnlabeledFragmentBesideLabel = true;
                }
                for (int i = 0; i < labels.size(); i++) {
                    int valueStart = labelRanges.get(i)[1];
                    int valueEnd = i + 1 < labels.size() ? labelRanges.get(i + 1)[0] : trimmed.length();
                    String value = trimmed.substring(valueStart, valueEnd).trim();
                    if (value.isEmpty()) {
                        continue;
                    }
                    String label = labels.get(i);
                    if (label.contains("联系人") || label.contains("姓名")
                            || label.contains("收件人") || label.contains("客户")) {
                        if (contactName == null) {
                            contactName = value;
                        }
                    } else if (label.contains("电话") || label.contains("手机")) {
                        deliveryPhones.add(value);
                    } else if (label.contains("工作日")) {
                        appendSegments(workdayAddress, value);
                    } else if (label.contains("周末")) {
                        appendSegments(weekendAddress, value);
                    } else {
                        appendSegments(defaultAddress, value);
                    }
                }
            }
        }

        // 导入契约规定有效编号同时作为地址联系人名，避免保留旧编号或不一致姓名。
        String resolvedContact = customer.getEffectiveCode();
        customer.setDeliveryPhoneInfo(deliveryPhones.isEmpty() ? null : String.join("\n", deliveryPhones));
        String resolvedPhone = firstDeliveryContactPhone(deliveryPhones, customer.getPhoneNormalized());

        boolean hasAddress = false;
        hasAddress |= addAddress(customer, "DEFAULT", defaultAddress.toString(), resolvedContact, resolvedPhone);
        hasAddress |= addAddress(customer, "WORKDAY", workdayAddress.toString(), resolvedContact, resolvedPhone);
        hasAddress |= addAddress(customer, "WEEKEND", weekendAddress.toString(), resolvedContact, resolvedPhone);
        if (!hasAddress) {
            customer.addIssue(CustomerImportIssueCategory.PROFILE_ERROR, "缺少可用的送餐地址");
        }
        if (hasUnlabeledFragmentBesideLabel) {
            customer.addWarning("地址中存在无法确认槽位的片段，已保留在默认地址，请业务确认");
        }
    }

    /**
     * 从配送信息中选择单值地址联系电话；完整原文另存于客户主档。
     *
     * @param deliveryPhones 地址列解析出的电话文本，按出现顺序排列
     * @param fallback 客户手机号；配送信息没有可用单值电话时使用
     * @return 首个有效手机号或单值数字电话，均不可用时返回客户手机号
     */
    private String firstDeliveryContactPhone(List<String> deliveryPhones, String fallback) {
        for (String value : deliveryPhones) {
            Matcher mobile = PHONE_IN_TEXT_PATTERN.matcher(value);
            if (mobile.find()) {
                return normalizePhone(mobile.group());
            }
            String normalized = normalizePhone(value);
            if (normalized.matches("\\d{5,20}")) {
                return normalized;
            }
        }
        return fallback;
    }

    /**
     * 追加一个地址槽位。
     *
     * @param customer 客户草稿
     * @param addressType 地址类型
     * @param detail 地址原文
     * @param contactName 联系人
     * @param contactPhone 联系人电话
     * @return 是否登记了有效地址
     */
    private boolean addAddress(ParsedCustomer customer, String addressType, String detail,
                               String contactName, String contactPhone) {
        String normalized = collapseWhitespace(detail);
        if (normalized.isEmpty()) {
            return false;
        }
        CustomerImportAddressDto address = new CustomerImportAddressDto();
        address.setAddressType(addressType);
        address.setAddressDetail(normalized);
        address.setContactName(contactName);
        address.setContactPhone(contactPhone);
        customer.getAddresses().add(address);
        return true;
    }

    /**
     * 读取一行原始数据。
     *
     * @param row 工作簿行
     * @param layout 列布局
     * @param calendarMonth 工作日历所属年月
     * @return 原始行数据
     */
    private RawRow readRow(Row row, CalendarLayout layout, YearMonth calendarMonth) {
        RawRow raw = new RawRow();
        raw.sourceRow = row.getRowNum() + 1;
        raw.codeA = text(row.getCell(layout.columnIndex.get(HEADER_ORIGINAL_CODE)));
        raw.codeB = text(row.getCell(layout.columnIndex.get(HEADER_NEW_CODE)));
        raw.phone = text(row.getCell(layout.columnIndex.get(HEADER_PHONE)));
        raw.address = text(row.getCell(layout.columnIndex.get(HEADER_ADDRESS)));
        raw.remark = text(row.getCell(layout.columnIndex.get(HEADER_REMARK)));
        raw.special = text(row.getCell(layout.columnIndex.get(HEADER_SPECIAL)));
        raw.soup = text(row.getCell(layout.columnIndex.get(HEADER_SOUP)));
        raw.delivery = text(row.getCell(layout.columnIndex.get(HEADER_DELIVERY)));
        raw.mealCount = integer(row.getCell(layout.columnIndex.get(HEADER_MEAL_COUNT)));
        raw.remainingCount = integer(row.getCell(layout.columnIndex.get(HEADER_REMAINING)));
        Cell gridTotalCell = row.getCell(layout.gridTotalColumn);
        raw.gridTotalBlank = isBlankCell(gridTotalCell);
        raw.gridTotal = raw.gridTotalBlank ? 0 : integer(gridTotalCell);
        raw.dayCells = readDayCells(row, layout, calendarMonth);
        raw.gridValues = readGridValues(row, layout, raw.sourceRow, calendarMonth, raw.gridIssues);
        return raw;
    }

    /**
     * 读取一行的全部日格，按日期分组为「早/中/晚」份数。
     *
     * @param row 工作簿行
     * @param layout 列布局
     * @param calendarMonth 工作日历所属年月
     * @return 日期到早中晚份数的有序映射
     */
    private Map<LocalDate, int[]> readDayCells(Row row, CalendarLayout layout, YearMonth calendarMonth) {
        Map<LocalDate, int[]> cells = new TreeMap<>();
        for (int day = 1; day <= layout.dayCount; day++) {
            int base = layout.calendarStart + MEALS_PER_DAY * (day - 1);
            int[] quantities = new int[MEALS_PER_DAY];
            boolean nonZero = false;
            for (int meal = 0; meal < MEALS_PER_DAY; meal++) {
                Integer value = integer(row.getCell(base + meal));
                quantities[meal] = value == null || value < 0 ? 0 : value;
                nonZero |= quantities[meal] > 0;
            }
            if (nonZero) {
                cells.put(calendarMonth.atDay(day), quantities);
            }
        }
        return cells;
    }

    /**
     * 读取一行的全部日格份数，用于重算合计餐数并核对公式缓存。
     *
     * @param row 工作簿行
     * @param layout 列布局
     * @return 日格份数列表
     */
    private List<Integer> readGridValues(Row row, CalendarLayout layout, int sourceRow, YearMonth calendarMonth,
                                         List<String> issues) {
        List<Integer> values = new ArrayList<>();
        for (int day = 1; day <= layout.dayCount; day++) {
            int base = layout.calendarStart + MEALS_PER_DAY * (day - 1);
            for (int meal = 0; meal < MEALS_PER_DAY; meal++) {
                Cell cell = row.getCell(base + meal);
                Integer value = integer(cell);
                if (hasCellContent(cell) && value == null) {
                    issues.add("第 " + sourceRow + " 行 " + calendarMonth.atDay(day) + " "
                            + mealTypeNameByOffset(meal) + "日格不是有效整数");
                } else if (value != null && value < 0) {
                    issues.add("第 " + sourceRow + " 行 " + calendarMonth.atDay(day) + " "
                            + mealTypeNameByOffset(meal) + "日格份数不能为负数");
                }
                values.add(value);
            }
        }
        return values;
    }

    /**
     * 判断单元格是否包含需要校验的原始内容。
     *
     * @param cell 工作簿单元格
     * @return 空白单元格返回 false，其余内容返回 true
     */
    private boolean hasCellContent(Cell cell) {
        if (cell == null) {
            return false;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        if (type == CellType.STRING) {
            return !cell.getStringCellValue().trim().isEmpty();
        }
        return type != CellType.BLANK;
    }

    /**
     * 把日历表头中的餐次偏移转换为可读名称。
     *
     * @param offset 早/中/晚偏移
     * @return 餐次名称
     */
    private String mealTypeNameByOffset(int offset) {
        if (offset == OFFSET_BREAKFAST) {
            return "早餐";
        }
        if (offset == OFFSET_LUNCH) {
            return "午餐";
        }
        return "晚餐";
    }

    /**
     * 合并续行地址时保留换行，避免把独立的地址片段并入联系人或电话字段。
     *
     * @param rows 同一编号的业务行
     * @return 去重后的地址原文
     */
    private String joinAddresses(List<RawRow> rows) {
        Set<String> values = new LinkedHashSet<>();
        for (RawRow row : rows) {
            if (!isBlank(row.address)) {
                values.add(row.address.replace("\r\n", "\n").replace('\r', '\n').trim());
            }
        }
        return String.join("\n", values);
    }

    /**
     * 读取单元格文本；公式单元格取缓存结果值。
     *
     * @param cell 单元格
     * @return 去首尾空白后的文本
     */
    private String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                return plainNumber(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    /**
     * 读取单元格整数；非整数值或空值返回 null。
     *
     * @param cell 单元格
     * @return 整数值
     */
    private Integer integer(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        if (type == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                return null;
            }
            return (int) value;
        }
        if (type == CellType.STRING) {
            String value = cell.getStringCellValue().trim();
            if (value.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(value).intValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 判断单元格及其公式缓存是否为空，用于将空白「合计餐数」按零处理。
     *
     * @param cell 待检查单元格
     * @return 单元格不存在、为空类型或仅含空白字符时返回 true
     */
    private boolean isBlankCell(Cell cell) {
        if (cell == null) {
            return true;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return type == CellType.BLANK
                || type == CellType.STRING && cell.getStringCellValue().trim().isEmpty();
    }

    /**
     * 把 double 转成不带科学计数法的十进制字符串，保留手机号等长数字的原始形态。
     *
     * @param value 数值
     * @return 十进制字符串
     */
    private String plainNumber(double value) {
        if (Math.abs(value) < 1e15 && value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /**
     * 规范化手机号：去除分隔符与空白，保持数字序列。
     *
     * @param raw 原始取值
     * @return 规范化手机号
     */
    private String normalizePhone(String raw) {
        if (isBlank(raw)) {
            return "";
        }
        return PHONE_SEPARATOR_PATTERN.matcher(raw).replaceAll("").trim();
    }

    /**
     * 手机号脱敏，仅保留前 3 位与后 4 位。
     *
     * @param phone 规范化手机号
     * @return 脱敏结果
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 合并多行同一列的文本，去重后以「；」连接。
     *
     * @param rows 明细行
     * @param getter 字段读取器
     * @return 合并结果
     */
    private String joinDistinct(List<RawRow> rows, Function<RawRow, String> getter) {
        Set<String> values = new LinkedHashSet<>();
        for (RawRow row : rows) {
            String value = getter.apply(row);
            if (!isBlank(value)) {
                values.add(collapseWhitespace(value));
            }
        }
        return String.join("；", values);
    }

    private String stripSpaces(String text) {
        return text == null ? "" : text.replace(" ", "").replace("\u3000", "");
    }

    /**
     * 把源行号列表拼成便于阅读的文本。
     *
     * @param rows 源行号列表
     * @return 形如「5、6」的文本
     */
    private String joinRows(List<Integer> rows) {
        StringBuilder builder = new StringBuilder();
        for (Integer row : rows) {
            if (builder.length() > 0) {
                builder.append('、');
            }
            builder.append(row);
        }
        return builder.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String collapseWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\s\u3000]+", " ").trim();
    }

    private void appendSegments(StringBuilder builder, String value) {
        String normalized = collapseWhitespace(value);
        if (normalized.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(normalized);
    }

    private String mealTypeName(String mealType) {
        return "LUNCH".equals(mealType) ? "午餐" : "晚餐";
    }

    /**
     * 工作簿列布局。
     */
    private static class CalendarLayout {
        private final Map<String, Integer> columnIndex = new LinkedHashMap<>();
        private int calendarStart;
        private int dayCount;
        private int gridTotalColumn;
    }

    /**
     * 一行原始数据。
     */
    private static class RawRow {
        private int sourceRow;
        private String codeA;
        private String codeB;
        private String effectiveCode;
        private String phone;
        private String address;
        private String remark;
        private String special;
        private String soup;
        private String delivery;
        private Integer mealCount;
        private Integer remainingCount;
        private Integer gridTotal;
        private boolean gridTotalBlank;
        private Map<LocalDate, int[]> dayCells = Collections.emptyMap();
        private List<Integer> gridValues = Collections.emptyList();
        private List<String> gridIssues = new ArrayList<>();
        /**
         * 该行是否包含午晚餐计划，用于决定是否累计餐数
         */
        private boolean mealLine;

        /**
         * 判断该行是否有业务内容：固定列有值，或日格区有非零份数。
         *
         * <p>不计入「合计餐数」列，因为空行的公式同样会返回 0，会导致空白行被误判为业务行。</p>
         *
         * @return true 表示需要纳入解析
         */
        private boolean hasBusinessContent() {
            if (!isBlank(codeA) || !isBlank(codeB) || !isBlank(phone) || !isBlank(address)
                    || !isBlank(remark) || !isBlank(special) || !isBlank(soup) || !isBlank(delivery)
                    || mealCount != null || remainingCount != null) {
                return true;
            }
            for (Integer value : gridValues) {
                if (value != null && value != 0) {
                    return true;
                }
            }
            return false;
        }

        private boolean isBlank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }
}
