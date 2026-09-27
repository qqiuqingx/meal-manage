package me.zhengjie.modules.meal.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.zhengjie.annotation.Log;
import me.zhengjie.modules.meal.domain.DishIngredientTag;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagSaveDto;
import me.zhengjie.modules.meal.service.DishIngredientTagService;
import me.zhengjie.utils.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配料标签管理接口。
 */
@RestController
@RequiredArgsConstructor
@Api(tags = "配料标签管理")
@RequestMapping("/api/dish-ingredient-tags")
public class DishIngredientTagController {

    private final DishIngredientTagService tagService;

    /**
     * 分页查询标签；配料维护权限可读取选项，标签管理权限可访问维护页。
     * @param criteria 名称与分页条件
     * @return 标签分页结果
     */
    @GetMapping
    @ApiOperation("分页查询配料标签")
    @PreAuthorize("@el.check('dishIngredientTag:list') or @el.check('dishIngredient:list') or @el.check('dishIngredient:add') or @el.check('dishIngredient:edit')")
    public ResponseEntity<PageResult<DishIngredientTag>> query(DishIngredientTagQueryCriteria criteria) {
        return new ResponseEntity<>(tagService.query(criteria), HttpStatus.OK);
    }

    /**
     * 新建标签并返回生成的标签ID。
     * @param resources 标签名称
     * @return 已创建标签
     */
    @PostMapping
    @Log("新增配料标签")
    @ApiOperation("新增配料标签")
    @PreAuthorize("@el.check('dishIngredientTag:add')")
    public ResponseEntity<DishIngredientTag> create(@Validated @RequestBody DishIngredientTagSaveDto resources) {
        return new ResponseEntity<>(tagService.create(resources), HttpStatus.CREATED);
    }

    /**
     * 修改标签名称。
     * @param resources 标签ID和新名称
     * @return 无响应正文
     */
    @PutMapping
    @Log("修改配料标签")
    @ApiOperation("修改配料标签")
    @PreAuthorize("@el.check('dishIngredientTag:edit')")
    public ResponseEntity<Object> update(@Validated @RequestBody DishIngredientTagSaveDto resources) {
        tagService.update(resources);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * 删除未被使用的标签。
     * @param id 标签ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Log("删除配料标签")
    @ApiOperation("删除配料标签")
    @PreAuthorize("@el.check('dishIngredientTag:del')")
    public ResponseEntity<Object> delete(@PathVariable Integer id) {
        tagService.delete(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
