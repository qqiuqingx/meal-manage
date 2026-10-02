package me.zhengjie.modules.customer.profile.mapper;

import me.zhengjie.modules.customer.profile.handler.CustomerDietExclusionsTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.StringTypeHandler;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CustomerDietPersistenceMappingTest {
    @Test
    void mapsInternalExclusionsAndBindsBothArraysInOneUpdate() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/CustomerProfileMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String namespace = "me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper.";
        ResultMapping mapping = configuration.getResultMap(namespace + "BaseResultMap")
                .getResultMappings().stream().filter(value -> "dietaryRestrictionExclusions".equals(value.getProperty()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(CustomerDietExclusionsTypeHandler.class, mapping.getTypeHandler().getClass());
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", 1L); parameters.put("json", "[]");
        parameters.put("exclusionsJson", "[\"INGREDIENT:501\"]");
        BoundSql update = configuration.getMappedStatement(namespace + "updateDietaryRestrictionsInline")
                .getBoundSql(parameters);
        assertTrue(update.getSql().contains("dietary_restriction_exclusions = ?"));
        assertEquals(StringTypeHandler.class, update.getParameterMappings().get(1).getTypeHandler().getClass());
        assertEquals("exclusionsJson", update.getParameterMappings().get(1).getProperty());
        parameters.put("afterId", 10L); parameters.put("limit", 100);
        BoundSql page = configuration.getMappedStatement(namespace + "findDietRematchIds").getBoundSql(parameters);
        assertTrue(page.getSql().contains("id > ?"));
        assertTrue(page.getSql().contains("ORDER BY id ASC"));
    }
}
