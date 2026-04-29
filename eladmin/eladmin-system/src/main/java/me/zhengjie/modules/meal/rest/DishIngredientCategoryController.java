package me.zhengjie.modules.meal.rest;

import me.zhengjie.annotation.Log;
import me.zhengjie.modules.meal.domain.DishIngredientCategory;
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

    @DeleteMapping("/{id}")
    @Log("删除配料分类")
    @ApiOperation("删除配料分类")
    @PreAuthorize("@el.check('dishIngredient:del')")
    public ResponseEntity<Object> delete(@PathVariable Integer id) {
        categoryService.delete(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
