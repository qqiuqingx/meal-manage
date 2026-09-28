package me.zhengjie.modules.customer.order.mapper;

import com.alibaba.fastjson2.JSON;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.StringTypeHandler;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 订单行内更新的普通字符串参数绑定验证。 */
class CustomerOrderInlineMapperStringBindingTest {

    /** 验证排餐模式、菜单路径及客户档案文本不会被 JSON 类型处理器额外加引号。 */
    @Test
    void bindsInlineTextAsPlainVarchar() throws Exception {
        String[][] cases = {
                {"mapper/CustomerOrderMapper.xml", "me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper.updateInlineField", "scheduleMode", "stringValue"},
                {"mapper/CustomerOrderMapper.xml", "me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper.updateInlineField", "customMenuImage", "stringValue"},
                {"mapper/CustomerOrderMapper.xml", "me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper.updateCustomerCodeInline", "", "customerCode"},
                {"mapper/CustomerProfileMapper.xml", "me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper.updateAllergyTagsInline", "", "allergyTagsJson"},
                {"mapper/CustomerProfileMapper.xml", "me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper.updateSpecialRequirementsInline", "", "specialRequirements"},
                {"mapper/CustomerProfileMapper.xml", "me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper.updatePhoneInline", "", "phone"},
                {"mapper/CustomerProfileMapper.xml", "me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper.updateCustomerCodeInline", "", "customerCode"}
        };
        for (String[] entry : cases) {
            Configuration configuration = new Configuration();
            configuration.getTypeHandlerRegistry().register("com.baomidou.mybatisplus.extension.handlers");
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(entry[0])) {
                new XMLMapperBuilder(input, configuration, entry[0], configuration.getSqlFragments()).parse();
            }
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("field", entry[2]);
            ParameterMapping mapping = configuration.getMappedStatement(entry[1]).getBoundSql(parameters)
                    .getParameterMappings().stream()
                    .filter(item -> entry[3].equals(item.getProperty()))
                    .findFirst().orElseThrow(AssertionError::new);
            assertEquals(StringTypeHandler.class, mapping.getTypeHandler().getClass(), entry[1]);
        }
    }

    /** 验证订单列表地址保留供行内定位的槽位代码与中文展示名。 */
    @Test
    void mapsAddressTypeForInlineEditing() throws Exception {
        String resource = "mapper/CustomerOrderMapper.xml";
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register("com.baomidou.mybatisplus.extension.handlers");
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        ResultMapping mapping = configuration.getResultMap(
                "me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper.JoinResultMap")
                .getResultMappings().stream()
                .filter(item -> "addresses".equals(item.getProperty()))
                .findFirst().orElseThrow(AssertionError::new);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("addresses"))
                .thenReturn("[{\"addressType\":\"WORKDAY\",\"type\":\"工作日\",\"detail\":\"新地址\"}]");

        List<?> addresses = (List<?>) mapping.getTypeHandler().getResult(resultSet, "addresses");
        com.alibaba.fastjson2.JSONObject address = JSON.parseObject(JSON.toJSONString(addresses.get(0)));
        assertEquals("WORKDAY", address.getString("addressType"));
        assertEquals("工作日", address.getString("type"));
        assertEquals("新地址", address.getString("detail"));
    }
}
