package me.zhengjie.modules.meal.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.domain.dto.DishIngredientDto;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.service.DishTagService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 仅显式启用时使用私有环境配置验证生产保存及重读路径；不启动应用、Redis或后台任务。
 * 全部写入限于本次唯一测试菜品，始终回滚事务，并再次确认测试记录没有留存。
 */
@EnabledIfSystemProperty(named = "dishIngredient.mysql.integration", matches = "true")
class DishIngredientRecognitionMySqlTest {
    @Test
    void persistsNullQuantityAndRetainsOrClearsIngredientRelationsAndDisplayInRollbackTransaction() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + requiredEnv("DB_HOST") + ":" + requiredEnv("DB_PORT") + "/"
                + requiredEnv("DB_NAME") + "?serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false");
        dataSource.setUsername(requiredEnv("DB_USER"));
        dataSource.setPassword(requiredEnv("DB_PWD"));
        // 非事务表不能依赖回滚清理，任何写入前核验实际存储引擎。
        try (java.sql.Connection connection = dataSource.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
                             + "AND TABLE_NAME IN ('dish','dish_ingredient_relation')")) {
            try (java.sql.ResultSet result = statement.executeQuery()) {
                int count = 0;
                while (result.next()) {
                    assertEquals("InnoDB", result.getString(1));
                    count++;
                }
                assertEquals(2, count);
            }
        }
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTransactionFactory(new JdbcTransactionFactory());
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new Resource[]{new ClassPathResource("mapper/DishMapper.xml"),
                new ClassPathResource("mapper/DishIngredientMapper.xml")});
        SqlSessionFactory sessions = factory.getObject();
        assertNotNull(sessions);
        Integer dishId = null;
        try (SqlSession session = sessions.openSession(false)) {
            // 不受Spring管理的独立测试显式使用JDBC事务，写入前确认自动提交已关闭。
            assertFalse(session.getConnection().getAutoCommit());
            try {
                DishMapper dishes = session.getMapper(DishMapper.class);
                DishIngredientMapper ingredients = session.getMapper(DishIngredientMapper.class);
                DishIngredient ingredient = ingredients.selectList(new QueryWrapper<DishIngredient>()
                        .eq("enabled", true).orderByAsc("id").last("LIMIT 1")).get(0);
                DishTagService tags = mock(DishTagService.class);
                when(tags.findTagsByDishIds(anyList())).thenReturn(Collections.emptyMap());
                // 使用生产构造器，其他依赖在本次菜品保存/详情路径中无调用。
                DishServiceImpl service = new DishServiceImpl(dishes, null, null, null, null, ingredients,
                        null, null, null, null, null, null, null, tags, mock(ApplicationEventPublisher.class));
                ReflectionTestUtils.setField(service, "baseMapper", dishes);
                DishIngredientDto row = new DishIngredientDto();
                row.setIngredientId(ingredient.getId());
                row.setIngredientName(ingredient.getName());
                row.setUnit(ingredient.getUnit());
                row.setRemark("");
                Dish create = new Dish();
                create.setName("__dish_recognition_test_" + UUID.randomUUID());
                create.setDishType("MAIN");
                create.setCookingMethod(ingredient.getName() + "切丁");
                create.setEnabled(false);
                create.setIngredientList(Collections.singletonList(row));
                service.create(create);
                dishId = create.getId();
                assertNotNull(dishId);
                session.clearCache();
                Dish reloaded = service.getById(dishId);
                assertEquals(ingredient.getName(), reloaded.getIngredients());
                assertEquals(1, reloaded.getIngredientList().size());
                assertNull(reloaded.getIngredientList().get(0).getQuantity());
                assertEquals("", reloaded.getIngredientList().get(0).getRemark());

                row.setQuantity(35.0);
                row.setRemark("保留原备注");
                Dish replace = new Dish();
                replace.setId(dishId);
                replace.setIngredientList(Collections.singletonList(row));
                service.update(replace);
                Dish nullList = new Dish();
                nullList.setId(dishId);
                nullList.setName(create.getName() + "_renamed");
                service.update(nullList);
                session.clearCache();
                reloaded = service.getById(dishId);
                assertEquals(35.0, reloaded.getIngredientList().get(0).getQuantity());
                assertEquals("保留原备注", reloaded.getIngredientList().get(0).getRemark());

                Dish clear = new Dish();
                clear.setId(dishId);
                clear.setIngredientList(Collections.emptyList());
                service.update(clear);
                session.clearCache();
                reloaded = service.getById(dishId);
                assertEquals("", reloaded.getIngredients());
                assertTrue(reloaded.getIngredientList() == null || reloaded.getIngredientList().isEmpty());
                assertTrue(ingredients.findRelationsByDishId(dishId).isEmpty());
                assertEquals(create.getCookingMethod(), reloaded.getCookingMethod());
                // null列表仍保持清空结果，不在CRUD中重新识别流程。
                service.update(nullList);
                session.clearCache();
                assertEquals("", service.getById(dishId).getIngredients());
            } finally {
                session.rollback(true);
            }
        }
        try (SqlSession check = sessions.openSession()) {
            assertNull(check.getMapper(DishMapper.class).selectById(dishId));
            assertTrue(check.getMapper(DishIngredientMapper.class).findRelationsByDishId(dishId).isEmpty());
        }
    }

    private String requiredEnv(String key) {
        String value = System.getenv(key);
        assertNotNull(value, "集成测试缺少环境配置 " + key);
        return value;
    }
}
