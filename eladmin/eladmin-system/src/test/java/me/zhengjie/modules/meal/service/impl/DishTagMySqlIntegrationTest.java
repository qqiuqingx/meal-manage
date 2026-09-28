package me.zhengjie.modules.meal.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.config.AuthorityConfig;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.DishTag;
import me.zhengjie.modules.meal.domain.dto.DishQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishTagSaveDto;
import me.zhengjie.modules.meal.mapper.DishTagMapper;
import me.zhengjie.modules.meal.service.DishService;
import me.zhengjie.modules.meal.service.DishTagService;
import me.zhengjie.utils.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 MySQL 验证菜品标签持久化、空值语义及标签/菜品行锁竞争。
 * 仅在显式指定集成测试和隔离测试库属性时运行；测试数据按本次生成的主键清理。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "dishTag.mysql.integration", matches = "true")
@EnabledIfSystemProperty(named = "dishTag.mysql.testDatabase", matches = "true")
class DishTagMySqlIntegrationTest {

    @MockBean(name = "serverEndpointExporter")
    private ServerEndpointExporter serverEndpointExporter;

    @MockBean(name = "el")
    private AuthorityConfig authorityConfig;

    @Autowired
    private DishService dishService;

    @Autowired
    private DishTagService tagService;

    @Autowired
    private DishTagMapper tagMapper;

    private final List<Integer> dishIds = new ArrayList<>();
    private final List<Integer> tagIds = new ArrayList<>();
    private String uniqueSuffix;

    @BeforeEach
    void initializeTestIdentifier() {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @AfterEach
    void cleanOnlyThisTestData() {
        if (!dishIds.isEmpty()) {
            dishService.deleteAll(new ArrayList<>(dishIds));
        }
        for (Integer tagId : tagIds) {
            if (tagMapper.selectById(tagId) != null) {
                tagService.delete(tagId);
            }
        }
        SecurityContextHolder.clearContext();
    }

    @Test
    void persistsTagsAndVerifiesNullEmptyInvalidAndDishDeleteSemantics() {
        DishTag firstTag = createTag("_A");
        DishTag secondTag = createTag("_B");
        DishTagSaveDto duplicateName = new DishTagSaveDto();
        duplicateName.setName(firstTag.getName().toUpperCase());
        assertThrows(BadRequestException.class, () -> tagService.create(duplicateName));

        Dish firstDish = createDish("_1", Arrays.asList(secondTag.getId(), firstTag.getId(), firstTag.getId()));
        Dish secondDish = createDish("_2", Collections.singletonList(firstTag.getId()));
        assertEquals(Arrays.asList(firstTag.getId(), secondTag.getId()), dishService.getById(firstDish.getId()).getTagIds());
        assertEquals(firstTag.getName(), dishService.getById(firstDish.getId()).getTags().get(0).getName());

        DishQueryCriteria criteria = new DishQueryCriteria();
        criteria.setName(dishNamePrefix());
        PageResult<Dish> page = dishService.queryAll(criteria, new Page<Object>(1, 10));
        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals(Arrays.asList(firstTag.getId(), secondTag.getId()), page.getContent().stream()
            .filter(item -> item.getId().equals(firstDish.getId())).findFirst().get().getTagIds());

        updateDish(firstDish.getId(), null);
        assertEquals(Arrays.asList(firstTag.getId(), secondTag.getId()), dishService.getById(firstDish.getId()).getTagIds());
        assertThrows(BadRequestException.class, () -> updateDish(firstDish.getId(), Collections.singletonList(Integer.MAX_VALUE)));
        assertEquals(Arrays.asList(firstTag.getId(), secondTag.getId()), dishService.getById(firstDish.getId()).getTagIds());

        updateDish(firstDish.getId(), Collections.emptyList());
        assertTrue(dishService.getById(firstDish.getId()).getTagIds().isEmpty());
        updateDish(firstDish.getId(), Collections.singletonList(firstTag.getId()));
        dishService.deleteAll(Collections.singletonList(secondDish.getId()));
        dishIds.remove(secondDish.getId());
        assertTrue(tagMapper.selectById(firstTag.getId()) != null);
        assertEquals(Collections.singletonList(firstTag.getId()), dishService.getById(firstDish.getId()).getTagIds());
    }

    @Test
    void concurrentReplacementIsWholeAndBindingCannotRaceTagDeletionIntoDanglingRelation() throws Exception {
        DishTag firstTag = createTag("_A");
        DishTag secondTag = createTag("_B");
        DishTag racingTag = createTag("_C");
        Dish replaceDish = createDish("_replace", Collections.emptyList());

        CountDownLatch updateStart = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> updateFirst = executor.submit(() -> {
                await(updateStart);
                updateDish(replaceDish.getId(), Collections.singletonList(firstTag.getId()));
            });
            Future<?> updateSecond = executor.submit(() -> {
                await(updateStart);
                updateDish(replaceDish.getId(), Collections.singletonList(secondTag.getId()));
            });
            updateStart.countDown();
            updateFirst.get(20, TimeUnit.SECONDS);
            updateSecond.get(20, TimeUnit.SECONDS);

            List<Integer> finalTagIds = dishService.getById(replaceDish.getId()).getTagIds();
            assertTrue(finalTagIds.equals(Collections.singletonList(firstTag.getId()))
                || finalTagIds.equals(Collections.singletonList(secondTag.getId())));

            Dish raceDish = createDish("_race", Collections.emptyList());
            CountDownLatch raceStart = new CountDownLatch(1);
            Future<Boolean> binding = executor.submit(() -> attempt(raceStart, () ->
                updateDish(raceDish.getId(), Collections.singletonList(racingTag.getId()))));
            Future<Boolean> deletion = executor.submit(() -> attempt(raceStart, () ->
                tagService.delete(racingTag.getId())));
            raceStart.countDown();

            boolean bindingSucceeded = binding.get(20, TimeUnit.SECONDS);
            boolean deletionSucceeded = deletion.get(20, TimeUnit.SECONDS);
            assertNotEquals(bindingSucceeded, deletionSucceeded);

            boolean tagExists = tagMapper.selectById(racingTag.getId()) != null;
            List<Integer> persisted = dishService.getById(raceDish.getId()).getTagIds();
            assertEquals(!deletionSucceeded, tagExists);
            assertEquals(bindingSucceeded, persisted.contains(racingTag.getId()));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private DishTag createTag(String suffix) {
        DishTagSaveDto request = new DishTagSaveDto();
        request.setName("it_dish_tag_" + uniqueSuffix + suffix);
        DishTag tag = tagService.create(request);
        tagIds.add(tag.getId());
        return tag;
    }

    private Dish createDish(String suffix, List<Integer> selectedTagIds) {
        Dish dish = new Dish();
        dish.setName(dishNamePrefix() + suffix);
        dish.setDishType("MAIN");
        dish.setMealTypes(Collections.singletonList("LUNCH"));
        dish.setMealPackages(Collections.emptyList());
        dish.setEnabled(true);
        dish.setTagIds(selectedTagIds);
        dishService.create(dish);
        dishIds.add(dish.getId());
        return dish;
    }

    private void updateDish(Integer dishId, List<Integer> selectedTagIds) {
        Dish request = new Dish();
        request.setId(dishId);
        request.setTagIds(selectedTagIds);
        dishService.update(request);
    }

    private String dishNamePrefix() {
        return "it_dish_" + uniqueSuffix;
    }

    private boolean attempt(CountDownLatch start, Runnable action) {
        await(start);
        try {
            action.run();
            return true;
        } catch (BadRequestException ex) {
            return false;
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发测试未收到开始信号");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试线程被中断", ex);
        }
    }
}
