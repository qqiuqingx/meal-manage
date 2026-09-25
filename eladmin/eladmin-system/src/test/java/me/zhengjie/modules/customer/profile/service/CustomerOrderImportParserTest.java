package me.zhengjie.modules.customer.profile.service;

import me.zhengjie.modules.customer.profile.domain.ParsedCustomer;
import me.zhengjie.modules.customer.profile.domain.ParsedWorkbook;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportIssueCategory;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportMealCellDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户导入工作簿解析器单元测试。
 *
 * <p>全部使用匿名样例工作簿（运行时用 POI 构造），不引用真实客户数据文件。</p>
 */
class CustomerOrderImportParserTest {

    private static final int CALENDAR_START = 10;
    private static final int DAY_COUNT = 30;
    private static final int GRID_TOTAL_COLUMN = CALENDAR_START + 3 * DAY_COUNT;
    private static final LocalDate IMPORT_DATE = LocalDate.of(2026, 9, 24);

    private CustomerOrderImportParser parser;

    @BeforeEach
    void setUp() {
        parser = new CustomerOrderImportParser();
    }

    @Test
    void parseShouldRejectMisalignedHeader() throws IOException {
        byte[] content = buildWorkbook(sheet -> sheet.getRow(0).getCell(3).setCellValue("收货地址"));

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        assertFalse(workbook.isStructureValid());
        assertTrue(workbook.getIssues().stream()
                .anyMatch(issue -> CustomerImportIssueCategory.WORKBOOK_ERROR.name().equals(issue.getCategory())));
    }

    @Test
    void parseShouldMergeContinuationRowsAndSkipBreakfastLine() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row breakfast = dataRow(sheet, 3, "A90", "A001", "18223380737",
                    "联系人：A90\n电话：18223380737\n地址：成都市示例路1号", "等通知", "12点送到", "含汤",
                    "早餐", 1, 1);
            setGridTotal(breakfast, 0);
            Row meals = dataRow(sheet, 4, null, null, null, null, null, null, "含汤",
                    "每日/午餐/晚餐", 10, 6);
            setGridCell(meals, 25, "LUNCH", 1);
            setGridCell(meals, 26, "LUNCH", 1);
            setGridCell(meals, 27, "LUNCH", 1);
            setGridCell(meals, 28, "LUNCH", 1);
            setGridTotal(meals, 4);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        assertTrue(workbook.isStructureValid());
        assertEquals(2, workbook.getDataRowCount());
        assertEquals(1, workbook.getCustomerCount());
        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertEquals("A001", customer.getEffectiveCode());
        // dataRow 传入的是 0-based 索引，解析器对外暴露 Excel 1-based 行号，故为 4、5
        assertEquals(Arrays.asList(4, 5), customer.getSourceRows());
        assertTrue(customer.isImportable());
        assertTrue(customer.isPaused());
        assertEquals(Integer.valueOf(6), customer.getSheetRemainingCount());
        assertEquals(Integer.valueOf(10), customer.getSheetMealCount());
        assertEquals(Integer.valueOf(1), customer.getSoupCount());
        assertEquals("DAILY", customer.getScheduleMode());
        assertEquals("LUNCH_DINNER", customer.getMealType());
        assertEquals(4, customer.getFutureMealCells().size());
    }

    @Test
    void parseShouldPreferNewCodeAndKeepOriginalCode() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, "B378", "B370", "13628005058",
                    "联系人：柴女士\n电话：13628005058\n地址：红牌楼示例路115号", null, "加一份主菜", "含汤",
                    "每日/午餐/晚餐", 7, 4);
            setGridCell(row, 25, "DINNER", 1);
            setGridCell(row, 26, "DINNER", 1);
            setGridCell(row, 27, "DINNER", 1);
            setGridTotal(row, 3);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertEquals("B370", customer.getEffectiveCode());
        assertEquals("B378", customer.getOriginalCodeA());
        assertEquals("B370", customer.getOriginalCodeB());
        assertEquals("B370", customer.getCustomerName());
    }

    @Test
    void parseShouldRejectInvalidPhoneWithMaskedMessage() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "B606", "1380182477",
                    "联系人：B606\n电话：1380182477\n地址：示例路1号", null, null, "含汤",
                    "每日/晚餐", 5, 5);
            setGridTotal(row, 0);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertFalse(customer.isImportable());
        String messages = customer.getIssues().stream()
                .map(issue -> issue.getMessage()).collect(Collectors.joining("|"));
        assertTrue(messages.contains("138****2477"));
        assertFalse(messages.contains("1380182477"));
    }

    @Test
    void parseShouldReportConflictingPhonesForSameCode() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row first = dataRow(sheet, 3, null, "B522", "13800138000",
                    "联系人：B522\n电话：13800138000\n地址：示例路1号", null, null, "含汤",
                    "每日/午餐", 3, 3);
            setGridTotal(first, 0);
            Row second = dataRow(sheet, 4, null, "B522", "13800138001",
                    "联系人：B522\n电话：13800138001\n地址：示例路2号", null, null, "含汤",
                    "每日/午餐", 3, 3);
            setGridTotal(second, 0);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        assertEquals(1, workbook.getCustomerCount());
        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertEquals(2, customer.getSourceRows().size());
        assertTrue(customer.getIssues().stream()
                .anyMatch(issue -> issue.getMessage().contains("不同手机号")));
    }

    @Test
    void parseShouldSkipPureBreakfastCustomer() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, "A201", "A003", "18782418444",
                    "联系人：A201\n电话：18782418444\n地址：示例路1号", null, "等通知、早餐3个鸡蛋", null,
                    "早餐", 1, 1);
            setGridTotal(row, 0);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertFalse(customer.isImportable());
        assertTrue(customer.getIssues().stream()
                .anyMatch(issue -> CustomerImportIssueCategory.SKIPPED.name().equals(issue.getCategory())));
    }

    @Test
    void parseShouldKeepZeroRemainingLunchCustomer() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "A004", "13800138000",
                    "联系人：A004\n电话：13800138000\n地址：示例路1号", null, null, "含汤",
                    "每日/午餐", 2, 0);
            setGridCell(row, 1, "LUNCH", 1);
            setGridCell(row, 2, "LUNCH", 1);
            setGridTotal(row, 2);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertTrue(customer.isImportable(), customer.getIssues().toString());
        assertEquals(Integer.valueOf(0), customer.getSheetRemainingCount());
        assertTrue(customer.getFutureMealCells().isEmpty());
    }

    @Test
    void parseShouldKeepMealTypeEmptyForWaitNoticeDelivery() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "A068", "18380166535",
                    "联系人：A068\n电话：18380166535\n地址：示例路1号", null, "川味副菜", "含汤",
                    "等通知送餐", 6, 6);
            setGridTotal(row, 0);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertTrue(customer.isImportable());
        assertEquals("SCHEDULE", customer.getScheduleMode());
        assertNull(customer.getMealType());
        assertTrue(customer.isPaused());
        assertTrue(customer.getWarnings().stream()
                .anyMatch(warning -> warning.contains("餐次留空")));
    }

    @Test
    void parseShouldPauseAndClearMealTypeWhenDeliveryIncludesWaitNotice() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "A069", "13800138069",
                    "联系人：A069\n电话：13800138069\n地址：示例路69号", null, null, "含汤",
                    "每日/午餐/等通知", 3, 3);
            setGridTotal(row, 0);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertTrue(customer.isImportable(), customer.getIssues().toString());
        assertTrue(customer.isPaused());
        assertEquals("SCHEDULE", customer.getScheduleMode());
        assertNull(customer.getMealType());
    }

    @Test
    void parseShouldKeepWaitNoticeAcrossContinuationRows() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row first = dataRow(sheet, 3, null, "A070", "13800138070",
                    "联系人：A070\n电话：13800138070\n地址：示例路70号", null, null, "含汤",
                    "每日/午餐", 3, 3);
            setGridTotal(first, 0);
            Row continuation = dataRow(sheet, 4, null, null, null, null, null, null, "含汤",
                    "等通知送餐", 0, 0);
            setGridTotal(continuation, 0);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertTrue(customer.isImportable(), customer.getIssues().toString());
        assertTrue(customer.isPaused());
        assertEquals("SCHEDULE", customer.getScheduleMode());
        assertNull(customer.getMealType());
    }

    @Test
    void parseShouldMarkSecondPortionSoupForMatchingMarker() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "B596", "13120338787",
                    "联系人：B596\n电话：13120338787\n送餐地址：示例小区9栋801", null, "B596-2含汤", "不含汤",
                    "每日/午餐/晚餐", 20, 16);
            setGridCell(row, 25, "LUNCH", 2);
            setGridCell(row, 25, "DINNER", 2);
            setGridTotal(row, 4);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertTrue(customer.isImportable());
        assertEquals(Integer.valueOf(0), customer.getSoupCount());
        List<CustomerImportMealCellDto> cells = customer.getFutureMealCells();
        assertEquals(2, cells.size());
        for (CustomerImportMealCellDto cell : cells) {
            assertEquals(Integer.valueOf(2), cell.getQuantity());
            assertEquals(Integer.valueOf(1), cell.getSoupQuantity());
        }
    }

    @Test
    void parseShouldRejectTwoPortionCellWithoutMarker() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "B600", "13120338700",
                    "联系人：B600\n电话：13120338700\n送餐地址：示例小区9栋802", null, null, "含汤",
                    "每日/午餐/晚餐", 20, 18);
            setGridCell(row, 25, "LUNCH", 2);
            setGridTotal(row, 2);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertFalse(customer.isImportable());
        assertTrue(customer.getIssues().stream()
                .anyMatch(issue -> issue.getMessage().contains("第二份含汤")));
    }

    @Test
    void parseShouldApplyNoSideDishSpec() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "F1076", "18257181111",
                    "联系人：F1076\n电话：18257181111\n地址：示例广场3号楼", "国庆后等通知", "无副菜", "不含汤",
                    "工作日/午餐", 5, 5);
            setGridTotal(row, 0);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertEquals(Integer.valueOf(1), customer.getMainDishCount());
        assertEquals(Integer.valueOf(0), customer.getSideDishCount());
        assertEquals(Integer.valueOf(1), customer.getVegCount());
        assertEquals(Integer.valueOf(0), customer.getSoupCount());
        assertEquals("WEEKDAY", customer.getScheduleMode());
        assertEquals("LUNCH", customer.getMealType());
        assertTrue(customer.isPaused());
    }

    @Test
    void parseShouldParseWorkdayAndWeekendAddressSlots() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "F900", "13880057445",
                    "联系人：F900\n电话：13880057445\n工作日地址：高新示例广场D座902\n周末地址：示例街78号2栋1单元",
                    null, null, "含汤", "每日/午餐", 2, 2);
            setGridTotal(row, 0);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertEquals(2, customer.getAddresses().size());
        assertEquals("WORKDAY", customer.getAddresses().get(0).getAddressType());
        assertEquals("高新示例广场D座902", customer.getAddresses().get(0).getAddressDetail());
        assertEquals("WEEKEND", customer.getAddresses().get(1).getAddressType());
        assertEquals("F900", customer.getAddresses().get(0).getContactName());
    }

    @Test
    void parseShouldParseBracketedAddressLabels() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "B56", "13810144847",
                    "【姓名】B56\n【电话】13810144847\n【送餐地址】示例小区3-2-601", "等通知", null, "含汤",
                    "每日/午餐", 2, 2);
            setGridTotal(row, 0);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertEquals(1, customer.getAddresses().size());
        assertEquals("示例小区3-2-601", customer.getAddresses().get(0).getAddressDetail());
        assertEquals("B56", customer.getAddresses().get(0).getContactName());
    }

    @Test
    void parseShouldKeepCustomerPhoneAndAllDeliveryPhones() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "A018", "13800138018",
                    "联系人：A018\n配送电话：13900139018、13700137018\n备用电话：13600136018\n送餐地址：示例路18号",
                    null, null, "含汤", "每日/午餐", 2, 2);
            setGridTotal(row, 0);
        });

        ParsedCustomer customer = parser.parse(content, "hash", IMPORT_DATE).getCustomers().get(0);

        assertTrue(customer.isImportable(), customer.getIssues().toString());
        assertEquals("13800138018", customer.getPhoneNormalized());
        assertEquals("13900139018、13700137018\n13600136018", customer.getDeliveryPhoneInfo());
        assertEquals("13900139018", customer.getAddresses().get(0).getContactPhone());
        assertTrue(customer.getWarnings().stream().noneMatch(warning -> warning.contains("电话与客户手机号不一致")));
    }

    @Test
    void parseShouldKeepUnlabeledDeliveryPhoneContinuation() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "A019", "13800138019",
                    "联系人：A019\n13900139019\n电话：13700137019\n13600136019\n地址：示例路19号",
                    null, null, "含汤", "每日/午餐", 2, 2);
            setGridTotal(row, 0);
        });

        ParsedCustomer customer = parser.parse(content, "hash", IMPORT_DATE).getCustomers().get(0);

        assertEquals("13900139019\n13700137019\n13600136019", customer.getDeliveryPhoneInfo());
        assertEquals("示例路19号", customer.getAddresses().get(0).getAddressDetail());
    }

    @Test
    void parseShouldRejectUntrustedGridTotal() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "A001", "13800138000",
                    "联系人：A001\n电话：13800138000\n地址：示例路1号", null, null, "含汤",
                    "每日/午餐", 5, 0);
            setGridCell(row, 25, "LUNCH", 1);
            setGridTotal(row, 5);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertFalse(customer.isImportable());
        assertTrue(customer.getIssues().stream()
                .anyMatch(issue -> issue.getMessage().contains("重算")));
    }

    @Test
    void parseShouldTreatBlankGridTotalAsZeroWithoutIssue() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            dataRow(sheet, 3, null, "A004", "13800138004",
                    "联系人：A004\n电话：13800138004\n地址：示例路4号", null, null, "含汤",
                    "每日/午餐", 0, 0);
            Row historicalMeals = dataRow(sheet, 4, null, "A005", "13800138005",
                    "联系人：A005\n电话：13800138005\n地址：示例路5号", null, null, "含汤",
                    "每日/午餐", 2, 0);
            setGridCell(historicalMeals, 1, "LUNCH", 1);
            setGridCell(historicalMeals, 2, "LUNCH", 1);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        assertEquals(2, workbook.getCustomerCount());
        for (ParsedCustomer customer : workbook.getCustomers()) {
            assertTrue(customer.isImportable(), customer.getIssues().toString());
            assertEquals(Integer.valueOf(0), customer.getSheetGridTotal());
            assertTrue(customer.getIssues().isEmpty());
        }
    }

    @Test
    void parseShouldRejectMultiPortionDescriptionWithoutCells() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "A002", "13800138002",
                    "联系人：A002\n电话：13800138002\n地址：示例路1号", null, "每餐两份一样的", "含汤",
                    "每日/午餐", 4, 4);
            setGridTotal(row, 0);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertFalse(customer.isImportable());
        assertTrue(customer.getIssues().stream()
                .anyMatch(issue -> issue.getMessage().contains("多份配送描述")));
    }

    @Test
    void parseShouldOnlyRegisterFutureLunchAndDinnerCells() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "A003", "13800138003",
                    "联系人：A003\n电话：13800138003\n地址：示例路1号", null, null, "含汤",
                    "每日/午餐/晚餐", 20, 11);
            setGridCell(row, 24, "LUNCH", 1);
            setGridCell(row, 24, "DINNER", 1);
            setGridCell(row, 25, "BREAKFAST", 5);
            setGridCell(row, 25, "LUNCH", 1);
            setGridCell(row, 30, "DINNER", 1);
            setGridTotal(row, 9);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        List<String> cells = customer.getFutureMealCells().stream()
                .map(cell -> cell.getDate() + "#" + cell.getMealType())
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("2026-09-25#LUNCH", "2026-09-30#DINNER"), cells);
    }

    @Test
    void parseShouldRejectMissingDeliveryDescription() throws IOException {
        byte[] content = buildWorkbook(sheet -> {
            Row row = dataRow(sheet, 3, null, "B17", "13800138017",
                    "联系人：B17\n电话：13800138017\n地址：示例路1号", null, null, "含汤",
                    null, 3, 3);
            setGridTotal(row, 0);
        });

        ParsedWorkbook workbook = parser.parse(content, "hash", IMPORT_DATE);

        ParsedCustomer customer = workbook.getCustomers().get(0);
        assertFalse(customer.isImportable());
        assertTrue(customer.getIssues().stream()
                .anyMatch(issue -> issue.getMessage().contains("送餐描述缺失")));
    }

    /**
     * 构造符合第三版模板结构的匿名工作簿。
     *
     * @param customizer 数据行定制逻辑
     * @return 工作簿字节内容
     */
    private static byte[] buildWorkbook(Consumer<Sheet> customizer) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("26年9月");
            Row header = sheet.createRow(0);
            String[] titles = {"编号", "新编号", "电话", "地址", "备注信息", "特殊要求", "含汤",
                    "每日/午餐/晚餐", "餐数", "剩余餐数", "消费记录"};
            for (int i = 0; i < titles.length; i++) {
                header.createCell(i).setCellValue(titles[i]);
            }
            Row dayRow = sheet.createRow(1);
            Row mealRow = sheet.createRow(2);
            String[] meals = {"早", "中", "晚"};
            for (int day = 1; day <= DAY_COUNT; day++) {
                int base = CALENDAR_START + 3 * (day - 1);
                dayRow.createCell(base).setCellValue(day);
                for (int meal = 0; meal < 3; meal++) {
                    mealRow.createCell(base + meal).setCellValue(meals[meal]);
                }
            }
            header.createCell(GRID_TOTAL_COLUMN).setCellValue("合计餐数");
            header.createCell(GRID_TOTAL_COLUMN + 1).setCellValue("剩余餐数");
            customizer.accept(sheet);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 写入一行固定列数据。
     */
    private static Row dataRow(Sheet sheet, int rowIndex, String codeA, String codeB, String phone, String address,
                               String remark, String special, String soup, String delivery,
                               Integer mealCount, Integer remaining) {
        Row row = sheet.createRow(rowIndex);
        setValue(row, 0, codeA);
        setValue(row, 1, codeB);
        setValue(row, 2, phone);
        setValue(row, 3, address);
        setValue(row, 4, remark);
        setValue(row, 5, special);
        setValue(row, 6, soup);
        setValue(row, 7, delivery);
        if (mealCount != null) {
            row.createCell(8).setCellValue(mealCount);
        }
        if (remaining != null) {
            row.createCell(9).setCellValue(remaining);
        }
        return row;
    }

    private static void setValue(Row row, int column, String value) {
        if (value == null) {
            return;
        }
        row.createCell(column).setCellValue(value);
    }

    /**
     * 设置某天某餐次的份数。
     */
    private static void setGridCell(Row row, int day, String mealType, int value) {
        int offset = "BREAKFAST".equals(mealType) ? 0 : ("LUNCH".equals(mealType) ? 1 : 2);
        int column = CALENDAR_START + 3 * (day - 1) + offset;
        Cell cell = row.getCell(column);
        if (cell == null) {
            cell = row.createCell(column);
        }
        cell.setCellValue(value);
    }

    /**
     * 设置「合计餐数」列取值。
     */
    private static void setGridTotal(Row row, int value) {
        Cell cell = row.getCell(GRID_TOTAL_COLUMN);
        if (cell == null) {
            cell = row.createCell(GRID_TOTAL_COLUMN);
        }
        cell.setCellValue(value);
    }
}
