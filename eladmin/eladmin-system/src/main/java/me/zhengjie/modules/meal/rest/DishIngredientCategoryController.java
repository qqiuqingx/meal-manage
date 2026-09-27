package me.zhengjie.modules.meal.rest;

import me.zhengjie.annotation.Log;
import me.zhengjie.modules.meal.domain.DishIngredientCategory;
import me.zhengjie.modules.meal.domain.dto.DishIngredientCategoryCreateDto;
import me.zhengjie.modules.meal.domain.dto.DishIngredientCategoryUpdateDto;
import me.zhengjie.modules.meal.service.DishIngredientCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.annotations.*;
import java.util.List;

/**
 * 配料分类管理
 * @author qqx
 * @date 2026-04-24
 **/
@RestController
@RequiredArgsConstructor
@Api(tags = "配料分类管理")
@RequestMapping("/api/dish-ingredient-categories")
public class DishIngredientCategoryController {

    private final DishIngredientCategoryService categoryService;

    @GetMapping("/tree")
    @ApiOperation("获取分类树结构")
    @PreAuthorize("@el.check('dishIngredient:list')")
    public ResponseEntity<List<DishIngredientCategory>> tree() {
        return new ResponseEntity<>(categoryService.tree(), HttpStatus.OK);
    }

    @GetMapping
    @ApiOperation("获取所有启用的分类列表")
    @PreAuthorize("@el.check('dishIngredient:list')")
    public ResponseEntity<List<DishIngredientCategory>> list() {
        return new ResponseEntity<>(categoryService.listEnabled(), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @ApiOperation("获取分类详情")
    @PreAuthorize("@el.check('dishIngredient:list')")
    public ResponseEntity<DishIngredientCategory> getById(@PathVariable Integer id) {
        return new ResponseEntity<>(categoryService.getById(id), HttpStatus.OK);
    }

    @GetMapping("/parent/{parentId}")
    @ApiOperation("根据父分类ID获取子分类")
    @PreAuthorize("@el.check('dishIngredient:list')")
    public ResponseEntity<List<DishIngredientCategory>> listByParentId(@PathVariable Integer parentId) {
        return new ResponseEntity<>(categoryService.listByParentId(parentId), HttpStatus.OK);
    }

    /**
     * 新增一级或二级配料分类。
     * @param request 分类名称、层级、父分类和可选排序
     * @return 新建分类
     */
    @PostMapping
    @Log("新增配料分类")
    @ApiOperation("新增配料分类")
    @PreAuthorize("@el.check('dishIngredient:add')")
    public ResponseEntity<DishIngredientCategory> create(@RequestBody DishIngredientCategoryCreateDto request) {
        return new ResponseEntity<>(categoryService.createCategory(request), HttpStatus.CREATED);
    }

    /**
     * 修改分类名称或排序，不支持调整层级及父分类。
     * @param id 分类ID
     * @param request 新名称和可选排序
     * @return 无响应正文
     */
    @PutMapping("/{id}")
    @Log("编辑配料分类")
    @ApiOperation("编辑配料分类")
    @PreAuthorize("@el.check('dishIngredient:edit')")
    public ResponseEntity<Object> update(@PathVariable Integer id,
                                         @RequestBody DishIngredientCategoryUpdateDto request) {
        categoryService.updateCategory(id, request);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping("/{id}")
    @Log("删除配料分类")
    @ApiOperation("删除配料分类")
    @PreAuthorize("@el.check('dishIngredient:del')")
    public ResponseEntity<Object> delete(@PathVariable Integer id) {
        categoryService.delete(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
