package me.zhengjie.modules.customer.order.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsQueryCriteria;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import me.zhengjie.modules.customer.profile.handler.CustomerDietItemsTypeHandler;
import me.zhengjie.modules.customer.profile.handler.CustomerDietRawBlocksTypeHandler;

import java.io.InputStream;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 订单列表结果映射测试。
 */
class CustomerOrderMapperMappingTest {

    /**
     * 验证订单、客户档案和排餐地址查询均把两种 JSON 形态解析为标签列表。
     */
    @Test
    void mapsAllergyTagsAsList() throws Exception {
        String[][] mappings = {
                {"mapper/CustomerOrderMapper.xml", "me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper.JoinResultMap"},
                {"mapper/CustomerProfileMapper.xml", "me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper.BaseResultMap"},
                {"mapper/MealPlanCustomerMapper.xml", "me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper.MealPlanCustomerAddressVOMap"}
        };
        for (String[] entry : mappings) {
            Configuration configuration = new Configuration();
            configuration.getTypeHandlerRegistry().register("com.baomidou.mybatisplus.extension.handlers");
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(entry[0])) {
                new XMLMapperBuilder(input, configuration, entry[0], configuration.getSqlFragments()).parse();
            }

            ResultMap resultMap = configuration.getResultMap(entry[1]);
            ResultMapping mapping = resultMap.getResultMappings().stream()
                    .filter(item -> "allergyTags".equals(item.getProperty()))
                    .findFirst().orElseThrow(AssertionError::new);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getString("allergy_tags"))
                    .thenReturn("[\"ces\"]", "\"[\\\"ces\\\"]\"");

            assertEquals(Collections.singletonList("ces"), mapping.getTypeHandler().getResult(resultSet, "allergy_tags"));
            assertEquals(Collections.singletonList("ces"), mapping.getTypeHandler().getResult(resultSet, "allergy_tags"));
        }
    }

    @Test
    void mapsDietItemsAndRawBlocksAcrossCustomerOrderAndMealPlanQueries() throws Exception {
        String[][] maps = {
                {"mapper/CustomerProfileMapper.xml", "me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper.BaseResultMap"},
                {"mapper/CustomerOrderMapper.xml", "me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper.JoinResultMap"},
                {"mapper/MealPlanCustomerMapper.xml", "me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper.BaseResultMap"},
                {"mapper/MealPlanCustomerMapper.xml", "me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper.MealPlanCustomerAddressVOMap"}
        };
        String[] itemProperties = {"dishRequirements", "dietaryRestrictions"};
        String[] rawProperties = {"dishRequirementsRaw", "dietaryRestrictionsRaw"};

        for (String[] entry : maps) {
            Configuration configuration = new Configuration();
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(entry[0])) {
                new XMLMapperBuilder(input, configuration, entry[0], configuration.getSqlFragments()).parse();
            }
            ResultMap resultMap = configuration.getResultMap(entry[1]);
            for (String property : itemProperties) {
                ResultMapping mapping = resultMap.getResultMappings().stream()
                        .filter(item -> property.equals(item.getProperty()))
                        .findFirst().orElseThrow(AssertionError::new);
                assertEquals(CustomerDietItemsTypeHandler.class, mapping.getTypeHandler().getClass());
            }
            for (String property : rawProperties) {
                ResultMapping mapping = resultMap.getResultMappings().stream()
                        .filter(item -> property.equals(item.getProperty()))
                        .findFirst().orElseThrow(AssertionError::new);
                assertEquals(CustomerDietRawBlocksTypeHandler.class, mapping.getTypeHandler().getClass());
            }
        }
    }

    /**
     * 验证历史月范围与剩余订单范围独立绑定，客户条件同时限制两种查询路径。
     */
    @Test
    void bindsHistoryMonthBoundsAlongsideCustomerFilters() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/CustomerOrderMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        CustomerMealStatsQueryCriteria criteria = new CustomerMealStatsQueryCriteria();
        criteria.setStatsMonth("2026-09");
        criteria.setCustomerCode("TEST");
        criteria.setCustomerName("匿名客户");
        criteria.setPhone("000");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("criteria", criteria);
        parameters.put("monthStartDate", LocalDate.of(2026, 9, 1));
        parameters.put("startedBeforeDate", LocalDate.of(2026, 10, 1));
        parameters.put("historyRemark", CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK);
        BoundSql sql = configuration.getMappedStatement(
                "me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper.findMealStatsOrders").getBoundSql(parameters);

        assertEquals(Arrays.asList("startedBeforeDate", "historyRemark", "monthStartDate", "startedBeforeDate",
                        "criteria.customerCode", "criteria.customerName", "criteria.phone"),
                sql.getParameterMappings().stream().map(ParameterMapping::getProperty).collect(Collectors.toList()));
        assertTrue(sql.getSql().contains("OR EXISTS"));
        assertTrue(sql.getSql().contains("history.order_id = o.id"));

        parameters.put("monthStartDate", null);
        parameters.put("startedBeforeDate", null);
        BoundSql noMonthSql = configuration.getMappedStatement(
                "me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper.findMealStatsOrders").getBoundSql(parameters);
        assertEquals(Arrays.asList("historyRemark", "criteria.customerCode", "criteria.customerName", "criteria.phone"),
                noMonthSql.getParameterMappings().stream().map(ParameterMapping::getProperty).collect(Collectors.toList()));
    }

    /** 验证独立导入日期随订单读取，排餐边界不再借用业务开始日。 */
    @Test
    void mapsImportDateAsLocalDate() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/CustomerOrderMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        ResultMapping mapping = configuration.getResultMap(
                "me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper.BaseResultMap")
                .getResultMappings().stream().filter(item -> "importDate".equals(item.getProperty()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("import_date", mapping.getColumn());
        assertEquals(LocalDate.class, mapping.getJavaType());
    }

    @Test
    void bindsDateBasedVerificationAndAllocationQueries() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/CustomerOrderMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("orderId", 20L);
        parameters.put("orderIds", Arrays.asList(20L, 21L));
        assertTrue(configuration.getMappedStatement("me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper.sumVerifiedCountByOrderDate")
                .getBoundSql(parameters).getSql().contains("record_date"));
        BoundSql occupied = configuration.getMappedStatement("me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper.countAllocatedBeforeImport")
                .getBoundSql(parameters);
        assertEquals(4, occupied.getParameterMappings().size());
        assertTrue(occupied.getSql().contains("UNION ALL"));
        ResultMapping mapping = configuration.getResultMap("me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper.BaseResultMap")
                .getResultMappings().stream().filter(value -> "importMonth".equals(value.getProperty()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("import_month", mapping.getColumn());
    }

    @Test
    void parsesReadOnlyDietDictionaryMapperQuery() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/CustomerDietDictionaryMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        assertTrue(configuration.hasStatement(
                "me.zhengjie.modules.customer.profile.mapper.CustomerDietDictionaryMapper.selectActiveOptions"));
    }
}
