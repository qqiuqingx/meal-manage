package me.zhengjie.modules.meal.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.config.AuthorityConfig;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.domain.DishIngredientTag;
import me.zhengjie.modules.meal.domain.dto.DishIngredientQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagSaveDto;
import me.zhengjie.modules.meal.mapper.DishIngredientTagMapper;
import me.zhengjie.modules.meal.rest.DishIngredientTagController;
import me.zhengjie.modules.meal.service.DishIngredientService;
import me.zhengjie.modules.meal.service.DishIngredientTagService;
import me.zhengjie.utils.PageResult;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.ArrayList;
import java.util.Arrays;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 使用真实 MySQL 验证标签持久化、筛选、事务回滚及行锁竞争。
 * 仅在显式设置 ingredientTag.mysql.integration=true 时运行；测试数据按本次生成的主键清理。
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "ingredientTag.mysql.integration", matches = "true")
class DishIngredientTagMySqlIntegrationTest {

    @MockBean(name = "serverEndpointExporter")
    private ServerEndpointExporter serverEndpointExporter;

    @MockBean(name = "el")
    private AuthorityConfig authorityConfig;

    @Autowired
    private DishIngredientService ingredientService;

    @Autowired
    private DishIngredientTagService tagService;

    @Autowired
    private DishIngredientTagMapper tagMapper;

    @Autowired
    private DishIngredientTagController tagController;

    private final List<Integer> ingredientIds = new ArrayList<>();
    private final List<Integer> tagIds = new ArrayList<>();
    private String uniqueSuffix;

    @BeforeEach
    void initializeTestIdentifier() {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @AfterEach
    void cleanOnlyThisTestData() {
        if (!ingredientIds.isEmpty()) {
            ingredientService.delete(new ArrayList<>(ingredientIds));
        }
        for (Integer tagId : tagIds) {
            if (tagMapper.selectById(tagId) != null) {
                tagService.delete(tagId);
            }
        }
    }

    @Test
    void persistsMultipleTagsFiltersExportsAndRollsBackInvalidReplacement() throws Exception {
        DishIngredientTag firstTag = createTag("_A");
        DishIngredientTag secondTag = createTag("_B");
        DishIngredientTagSaveDto duplicateName = new DishIngredientTagSaveDto();
        duplicateName.setName(firstTag.getName().toUpperCase(Locale.ROOT));
        assertThrows(BadRequestException.class, () -> tagService.create(duplicateName));

        String originalSecondTagName = secondTag.getName();
        DishIngredient firstIngredient = createIngredient("_1", Arrays.asList(firstTag.getId(), secondTag.getId()));
        DishIngredient secondIngredient = createIngredient("_2", Collections.singletonList(firstTag.getId()));

        DishIngredient detail = ingredientService.findById(firstIngredient.getId());
        assertEquals(Arrays.asList(firstTag.getId(), secondTag.getId()), detail.getTagIds());
        assertEquals(Arrays.asList(firstTag.getName(), originalSecondTagName), detail.getTags().stream()
            .map(tag -> tag.getName()).collect(Collectors.toList()));

        String renamedSecondTag = originalSecondTagName + "_renamed";
        DishIngredientTagSaveDto renameRequest = new DishIngredientTagSaveDto();
        renameRequest.setId(secondTag.getId());
        renameRequest.setName(renamedSecondTag);
        tagService.update(renameRequest);
        secondTag.setName(renamedSecondTag);
        assertEquals(Arrays.asList(firstTag.getName(), renamedSecondTag), ingredientService.findById(firstIngredient.getId()).getTags().stream()
            .map(tag -> tag.getName()).collect(Collectors.toList()));

        DishIngredientQueryCriteria criteria = new DishIngredientQueryCriteria();
        criteria.setName(ingredientNamePrefix());
        criteria.setTagId(firstTag.getId());
        assertEquals(2, ingredientService.queryAll(criteria).size());
        PageResult<DishIngredient> firstPage = ingredientService.queryAll(criteria, new Page<Object>(1, 1));
        assertEquals(2, firstPage.getTotalElements());
        assertEquals(1, firstPage.getContent().size());
        MockHttpServletResponse exportResponse = new MockHttpServletResponse();
        ingredientService.download(ingredientService.queryAll(criteria), exportResponse);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(exportResponse.getContentAsByteArray()))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            DataFormatter formatter = new DataFormatter();
            int nameColumn = -1;
            int tagColumn = -1;
            for (int column = 0; column < header.getLastCellNum(); column++) {
                String heading = formatter.formatCellValue(header.getCell(column));
                if ("配料名称".equals(heading)) nameColumn = column;
                if ("标签".equals(heading)) tagColumn = column;
            }
            assertTrue(nameColumn >= 0);
            assertTrue(tagColumn >= 0);
            boolean firstIngredientExported = false;
            for (int rowIndex = 1; rowIndex <= workbook.getSheetAt(0).getLastRowNum(); rowIndex++) {
                Row row = workbook.getSheetAt(0).getRow(rowIndex);
                if (firstIngredient.getName().equals(formatter.formatCellValue(row.getCell(nameColumn)))) {
                    assertEquals(firstTag.getName() + "、" + secondTag.getName(), formatter.formatCellValue(row.getCell(tagColumn)));
                    firstIngredientExported = true;
                }
            }
            assertTrue(firstIngredientExported);
        }
        criteria.setTagId(secondTag.getId());
        assertEquals(1, ingredientService.queryAll(criteria).size());
        criteria.setTagId(Integer.MAX_VALUE);
        assertTrue(ingredientService.queryAll(criteria).isEmpty());

        DishIngredient preserveRequest = new DishIngredient();
        preserveRequest.setId(firstIngredient.getId());
        preserveRequest.setTagIds(null);
        ingredientService.update(preserveRequest);
        assertEquals(Arrays.asList(firstTag.getId(), secondTag.getId()), ingredientService.findById(firstIngredient.getId()).getTagIds());

        DishIngredient invalidRequest = new DishIngredient();
        invalidRequest.setId(firstIngredient.getId());
        invalidRequest.setTagIds(Collections.singletonList(Integer.MAX_VALUE));
        assertThrows(BadRequestException.class, () -> ingredientService.update(invalidRequest));
        assertEquals(Arrays.asList(firstTag.getId(), secondTag.getId()), ingredientService.findById(firstIngredient.getId()).getTagIds());

        DishIngredient clearRequest = new DishIngredient();
        clearRequest.setId(firstIngredient.getId());
        clearRequest.setTagIds(Collections.emptyList());
        ingredientService.update(clearRequest);
        assertTrue(ingredientService.findById(firstIngredient.getId()).getTagIds().isEmpty());

        assertThrows(BadRequestException.class, () -> tagService.delete(firstTag.getId()));
        DishIngredient clearSecondRequest = new DishIngredient();
        clearSecondRequest.setId(secondIngredient.getId());
        clearSecondRequest.setTagIds(Collections.emptyList());
        ingredientService.update(clearSecondRequest);
        tagService.delete(firstTag.getId());
        assertTrue(tagMapper.selectById(firstTag.getId()) == null);
    }

    @Test
    void concurrentReplacementIsWholeAndBindingCannotRaceTagDeletionIntoDanglingRelation() throws Exception {
        DishIngredientTag firstTag = createTag("_A");
        DishIngredientTag secondTag = createTag("_B");
        DishIngredientTag racingTag = createTag("_C");
        DishIngredient ingredient = createIngredient("_1", Collections.emptyList());

        CountDownLatch updateStart = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> updateFirst = executor.submit(() -> {
                await(updateStart);
                ingredientService.update(updateRequest(ingredient.getId(), firstTag.getId()));
            });
            Future<?> updateSecond = executor.submit(() -> {
                await(updateStart);
                ingredientService.update(updateRequest(ingredient.getId(), secondTag.getId()));
            });
            updateStart.countDown();
            updateFirst.get(20, TimeUnit.SECONDS);
            updateSecond.get(20, TimeUnit.SECONDS);

            List<Integer> finalTagIds = ingredientService.findById(ingredient.getId()).getTagIds();
            assertTrue(finalTagIds.equals(Collections.singletonList(firstTag.getId()))
                || finalTagIds.equals(Collections.singletonList(secondTag.getId())));

            CountDownLatch raceStart = new CountDownLatch(1);
            Future<Boolean> binding = executor.submit(() -> {
                await(raceStart);
                try {
                    ingredientService.update(updateRequest(ingredient.getId(), racingTag.getId()));
                    return true;
                } catch (BadRequestException ex) {
                    return false;
                }
            });
            Future<Boolean> deletion = executor.submit(() -> {
                await(raceStart);
                try {
                    tagService.delete(racingTag.getId());
                    return true;
                } catch (BadRequestException ex) {
                    return false;
                }
            });
            raceStart.countDown();
            boolean bindingSucceeded = binding.get(20, TimeUnit.SECONDS);
            boolean deletionSucceeded = deletion.get(20, TimeUnit.SECONDS);
            assertNotEquals(bindingSucceeded, deletionSucceeded);

            boolean tagExists = tagMapper.selectById(racingTag.getId()) != null;
            List<Integer> persisted = ingredientService.findById(ingredient.getId()).getTagIds();
            assertEquals(tagExists, !deletionSucceeded);
            assertEquals(bindingSucceeded, persisted.contains(racingTag.getId()));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void methodSecurityDeniesMissingPermissionAndAllowsExistingIngredientReadPermission() {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("ingredient-tag-test", "test", Collections.emptyList())
        );
        try {
            when(authorityConfig.check("dishIngredientTag:list")).thenReturn(false);
            when(authorityConfig.check("dishIngredient:list")).thenReturn(false);
            when(authorityConfig.check("dishIngredient:add")).thenReturn(false);
            when(authorityConfig.check("dishIngredient:edit")).thenReturn(false);
            assertThrows(AccessDeniedException.class, () -> tagController.query(new DishIngredientTagQueryCriteria()));

            when(authorityConfig.check("dishIngredient:add")).thenReturn(true);
            assertDoesNotThrow(() -> tagController.query(new DishIngredientTagQueryCriteria()));
        } finally {
            SecurityContextHolder.clearContext();
            if (previous != null) {
                SecurityContextHolder.getContext().setAuthentication(previous);
            }
        }
    }

    private DishIngredientTag createTag(String suffix) {
        DishIngredientTagSaveDto request = new DishIngredientTagSaveDto();
        request.setName("it_" + uniqueSuffix + suffix);
        DishIngredientTag tag = tagService.create(request);
        tagIds.add(tag.getId());
        return tag;
    }

    private DishIngredient createIngredient(String suffix, List<Integer> selectedTagIds) {
        DishIngredient ingredient = new DishIngredient();
        ingredient.setName(ingredientNamePrefix() + suffix);
        ingredient.setEnabled(true);
        ingredient.setTagIds(selectedTagIds);
        ingredientService.create(ingredient);
        ingredientIds.add(ingredient.getId());
        return ingredient;
    }

    private String ingredientNamePrefix() {
        return "it_ingredient_" + uniqueSuffix;
    }

    private DishIngredient updateRequest(Integer ingredientId, Integer tagId) {
        DishIngredient request = new DishIngredient();
        request.setId(ingredientId);
        request.setTagIds(Collections.singletonList(tagId));
        return request;
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
