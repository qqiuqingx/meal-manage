package me.zhengjie.modules.customer.order.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.ResultSet;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
