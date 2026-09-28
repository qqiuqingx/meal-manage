package me.zhengjie.modules.meal.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.zhengjie.annotation.Log;
import me.zhengjie.modules.meal.domain.DishTag;
import me.zhengjie.modules.meal.domain.dto.DishTagQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishTagSaveDto;
import me.zhengjie.modules.meal.service.DishTagService;
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
 * 菜品标签查询接口。
 */
@RestController
@RequiredArgsConstructor
@Api(tags = "菜品标签")
@RequestMapping("/api/dish-tags")
public class DishTagController {

    private final DishTagService tagService;

    /**
     * 分页查询菜品标签；菜品列表、新增或编辑权限可读取选择项。
     * @param criteria 名称与分页条件
     * @return 标签分页结果
     */
    @GetMapping
    @ApiOperation("分页查询菜品标签")
    @PreAuthorize("@el.check('dish:list') or @el.check('dish:add') or @el.check('dish:edit')")
    public ResponseEntity<PageResult<DishTag>> query(DishTagQueryCriteria criteria) {
        return new ResponseEntity<>(tagService.query(criteria), HttpStatus.OK);
    }

    /**
     * 新建标签并返回生成的标签ID。
     * @param resources 标签名称
     * @return 已创建标签
     */
    @PostMapping
    @Log("新增菜品标签")
    @ApiOperation("新增菜品标签")
    @PreAuthorize("@el.check('dishTag:add')")
    public ResponseEntity<DishTag> create(@Validated @RequestBody DishTagSaveDto resources) {
        return new ResponseEntity<>(tagService.create(resources), HttpStatus.CREATED);
    }

    /**
     * 修改标签名称。
     * @param resources 标签ID和新名称
     * @return 无响应正文
     */
    @PutMapping
    @Log("修改菜品标签")
    @ApiOperation("修改菜品标签")
    @PreAuthorize("@el.check('dishTag:edit')")
    public ResponseEntity<Object> update(@Validated @RequestBody DishTagSaveDto resources) {
        tagService.update(resources);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * 删除未被任何菜品使用的标签。
     * @param id 标签ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Log("删除菜品标签")
    @ApiOperation("删除菜品标签")
    @PreAuthorize("@el.check('dishTag:del')")
    public ResponseEntity<Object> delete(@PathVariable Integer id) {
        tagService.delete(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
