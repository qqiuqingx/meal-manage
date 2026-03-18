/*
*  Copyright 2019-2025 Zheng Jie
*
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*/
package me.zhengjie.modules.meal.rest;

import me.zhengjie.annotation.Log;
import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.service.DishIngredientService;
import me.zhengjie.modules.meal.domain.dto.DishIngredientQueryCriteria;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.annotations.*;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.utils.PageResult;

/**
 * 配料管理
 * @author qqx
 * @date 2026-03-15
 **/
@RestController
@RequiredArgsConstructor
@Api(tags = "配料管理")
@RequestMapping("/api/dish-ingredients")
public class DishIngredientController {

    private final DishIngredientService dishIngredientService;

    @ApiOperation("导出配料数据")
    @GetMapping(value = "/download")
    @PreAuthorize("@el.check('dishIngredient:list')")
    public void exportDishIngredient(HttpServletResponse response, DishIngredientQueryCriteria criteria) throws IOException {
        dishIngredientService.download(dishIngredientService.queryAll(criteria), response);
    }

    @GetMapping
    @ApiOperation("查询配料")
    @PreAuthorize("@el.check('dishIngredient:list')")
    public ResponseEntity<PageResult<DishIngredient>> queryDishIngredient(DishIngredientQueryCriteria criteria){
        Page<Object> page = new Page<>(criteria.getPage() + 1, criteria.getSize());
        return new ResponseEntity<>(dishIngredientService.queryAll(criteria,page),HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @ApiOperation("查询配料详情")
    @PreAuthorize("@el.check('dishIngredient:list')")
    public ResponseEntity<DishIngredient> queryDishIngredientById(@PathVariable Integer id){
        return new ResponseEntity<>(dishIngredientService.findById(id),HttpStatus.OK);
    }

    @GetMapping("/dish/{dishId}")
    @ApiOperation("根据菜品ID查询配料")
    @PreAuthorize("@el.check('dishIngredient:list')")
    public ResponseEntity<List<DishIngredient>> queryByDishId(@PathVariable Integer dishId){
        return new ResponseEntity<>(dishIngredientService.findByDishId(dishId),HttpStatus.OK);
    }

    @PostMapping
    @Log("新增配料")
    @ApiOperation("新增配料")
    @PreAuthorize("@el.check('dishIngredient:add')")
    public ResponseEntity<Object> createDishIngredient(@Validated @RequestBody DishIngredient resources){
        dishIngredientService.create(resources);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PutMapping
    @Log("修改配料")
    @ApiOperation("修改配料")
    @PreAuthorize("@el.check('dishIngredient:edit')")
    public ResponseEntity<Object> updateDishIngredient(@Validated @RequestBody DishIngredient resources){
        dishIngredientService.update(resources);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping
    @Log("删除配料")
    @ApiOperation("删除配料")
    @PreAuthorize("@el.check('dishIngredient:del')")
    public ResponseEntity<Object> deleteDishIngredient(@ApiParam(value = "传ID数组[]") @RequestBody List<Integer> ids) {
        dishIngredientService.delete(ids);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
