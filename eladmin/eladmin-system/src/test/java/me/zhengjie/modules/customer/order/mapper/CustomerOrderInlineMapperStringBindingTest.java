package me.zhengjie.modules.customer.order.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.StringTypeHandler;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
