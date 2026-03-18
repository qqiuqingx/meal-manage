# 菜品管理功能实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现菜品管理模块的完整 CRUD 功能，支持按排期查询和根据客户忌口过滤菜品

**Architecture:** 遵循现有 meal 模块的结构，使用 Mybatis-Plus + Spring Boot，后端 API + 前端 Vue 页面

**Tech Stack:** Spring Boot 2.7.18, Mybatis-Plus 3.5.3.1, Vue 2.7, element-ui

---

## 文件结构

### 后端 (eladmin-system)

```
eladmin-system/src/main/java/me/zhengjie/modules/meal/
├── domain/
│   ├── Dish.java                          # 菜品实体
│   ├── enums/
│   │   ├── DishTypeEnum.java              # 菜品类型枚举
│   │   └── MealTypeEnum.java              # 餐次枚举
│   └── dto/
│       └── DishQueryCriteria.java         # 查询条件
├── mapper/
│   └── DishMapper.java                    # Mapper接口
├── service/
│   ├── DishService.java                   # Service接口
│   └── impl/
│       └── DishServiceImpl.java           # Service实现
└── rest/
    └── DishController.java                # Controller

eladmin-system/src/main/resources/mapper/
└── DishMapper.xml                         # Mapper XML
```

### 前端 (eladmin-web)

```
eladmin-web/src/api/
└── dish.js                                # API调用

eladmin-web/src/views/meal/
└── dish/
    ├── index.vue                          # 列表页
    └── dish.vue                           # 新增/编辑弹窗
```

---

## Chunk 1: 后端枚举类

### Task 1.1: 创建菜品类型枚举 DishTypeEnum

**Files:**
- Create: `eladmin-system/src/main/java/me/zhengjie/modules/meal/domain/enums/DishTypeEnum.java`

```java
package me.zhengjie.modules.meal.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 菜品类型枚举
 * @author qqx
 * @date 2026-03-14
 */
@Getter
@AllArgsConstructor
public enum DishTypeEnum {

    MAIN("MAIN", "主菜"),
    SIDE("SIDE", "副菜"),
    SOUP("SOUP", "汤"),
    VEGETABLE("VEGETABLE", "素菜"),
    RICE("RICE", "米饭");

    private final String code;
    private final String desc;

    @JsonValue
    public String getDesc() {
        return desc;
    }

    public static DishTypeEnum fromCode(String code) {
        for (DishTypeEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
```

### Task 1.2: 创建餐次枚举 MealTypeEnum

**Files:**
- Create: `eladmin-system/src/main/java/me/zhengjie/modules/meal/domain/enums/MealTypeEnum.java`

```java
package me.zhengjie.modules.meal.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 餐次枚举
 * @author qqx
 * @date 2026-03-14
 */
@Getter
@AllArgsConstructor
public enum MealTypeEnum {

    LUNCH("LUNCH", "午餐"),
    DINNER("DINNER", "晚餐");

    private final String code;
    private final String desc;

    @JsonValue
    public String getDesc() {
        return desc;
    }

    public static MealTypeEnum fromCode(String code) {
        for (MealTypeEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
```

- [ ] 创建 DishTypeEnum.java
- [ ] 创建 MealTypeEnum.java
- [ ] Commit: `feat(meal): 添加菜品类型和餐次枚举`

---

## Chunk 2: 后端实体类和查询条件

### Task 2.1: 创建菜品实体 Dish.java

**Files:**
- Create: `eladmin-system/src/main/java/me/zhengjie/modules/meal/domain/Dish.java`

```java
package me.zhengjie.modules.meal.domain;

import lombok.Data;
import cn.hutool.core.bean.BeanUtil;
import io.swagger.annotations.ApiModelProperty;
import cn.hutool.core.bean.copier.CopyOptions;
import javax.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.io.Serializable;
import java.sql.Timestamp;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

/**
 * 菜品实体
 * @author qqx
 * @date 2026-03-14
 **/
@Data
@TableName(value = "dish", autoResultMap = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Dish implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Integer id;

    @NotBlank(message = "菜品名称不能为空")
    @ApiModelProperty(value = "菜品名称")
    @TableField("name")
    private String name;

    @ApiModelProperty(value = "做法/流程")
    @TableField("cooking_method")
    private String cookingMethod;

    @ApiModelProperty(value = "配料")
    @TableField("ingredients")
    private String ingredients;

    @ApiModelProperty(value = "图片路径")
    @TableField("image_url")
    private String imageUrl;

    @NotBlank(message = "菜品类型不能为空")
    @ApiModelProperty(value = "菜品类型：MAIN主菜、SIDE副菜、SOUP汤、VEGETABLE素菜、RICE米饭")
    @TableField("dish_type")
    private String dishType;

    @ApiModelProperty(value = "餐次：LUNCH午餐、DINNER晚餐")
    @TableField(value = "meal_types", typeHandler = JacksonTypeHandler.class)
    private List<String> mealTypes;

    @ApiModelProperty(value = "所属套餐")
    @TableField(value = "meal_packages", typeHandler = JacksonTypeHandler.class)
    private List<String> mealPackages;

    @ApiModelProperty(value = "排期：格式如1-1表示第1周周一")
    @TableField(value = "schedule", typeHandler = JacksonTypeHandler.class)
    private List<String> schedule;

    @ApiModelProperty(value = "排序")
    @TableField("sort")
    private Integer sort;

    @ApiModelProperty(value = "是否启用")
    @TableField("enabled")
    private Boolean enabled;

    @ApiModelProperty(value = "创建时间")
    @TableField("create_time")
    private Timestamp createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField("update_time")
    private Timestamp updateTime;

    public void copy(Dish source){
        BeanUtil.copyProperties(source,this, CopyOptions.create().setIgnoreNullValue(true));
    }
}
```

### Task 2.2: 创建查询条件 DishQueryCriteria.java

**Files:**
- Create: `eladmin-system/src/main/java/me/zhengjie/modules/meal/domain/dto/DishQueryCriteria.java`

```java
package me.zhengjie.modules.meal.domain.dto;

import lombok.Data;
import io.swagger.annotations.ApiModelProperty;

/**
 * 菜品查询条件
 * @author qqx
 * @date 2026-03-14
 **/
@Data
public class DishQueryCriteria {

    @ApiModelProperty(value = "菜品名称")
    private String name;

    @ApiModelProperty(value = "菜品类型")
    private String dishType;

    @ApiModelProperty(value = "餐次")
    private String mealType;

    @ApiModelProperty(value = "套餐")
    private String mealPackage;

    @ApiModelProperty(value = "是否启用")
    private Boolean enabled;

    @ApiModelProperty(value = "页码")
    private Integer page = 0;

    @ApiModelProperty(value = "每页数量")
    private Integer size = 10;
}
```

- [ ] 创建 Dish.java
- [ ] 创建 DishQueryCriteria.java
- [ ] Commit: `feat(meal): 添加菜品实体和查询条件`

---

## Chunk 3: 后端 Mapper

### Task 3.1: 创建 DishMapper.java

**Files:**
- Create: `eladmin-system/src/main/java/me/zhengjie/modules/meal/mapper/DishMapper.java`

```java
package me.zhengjie.modules.meal.mapper;

import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.dto.DishQueryCriteria;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 菜品 Mapper 接口
 * @author qqx
 * @date 2026-03-14
 **/
@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    IPage<Dish> findAll(@Param("criteria") DishQueryCriteria criteria, Page<Object> page);

    List<Dish> findAll(@Param("criteria") DishQueryCriteria criteria);

    List<Dish> findBySchedule(@Param("week") Integer week, @Param("day") Integer day, @Param("mealType") String mealType);

    List<Dish> findAvailableByCustomerId(@Param("customerId") Integer customerId, @Param("mealType") String mealType, @Param("week") Integer week, @Param("day") Integer day);
}
```

### Task 3.2: 创建 DishMapper.xml

**Files:**
- Create: `eladmin-system/src/main/resources/mapper/DishMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd" >
<mapper namespace="me.zhengjie.modules.meal.mapper.DishMapper">
    <resultMap id="BaseResultMap" type="me.zhengjie.modules.meal.domain.Dish">
        <id column="id" property="id"/>
        <result column="name" property="name"/>
        <result column="cooking_method" property="cookingMethod"/>
        <result column="ingredients" property="ingredients"/>
        <result column="image_url" property="imageUrl"/>
        <result column="dish_type" property="dishType"/>
        <result column="meal_types" property="mealTypes" typeHandler="com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler"/>
        <result column="meal_packages" property="mealPackages" typeHandler="com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler"/>
        <result column="schedule" property="schedule" typeHandler="com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler"/>
        <result column="sort" property="sort"/>
        <result column="enabled" property="enabled"/>
        <result column="create_time" property="createTime"/>
        <result column="update_time" property="updateTime"/>
    </resultMap>

    <sql id="Base_Column_List">
        id, name, cooking_method, ingredients, image_url, dish_type, meal_types, meal_packages, schedule, sort, enabled, create_time, update_time
    </sql>

    <select id="findAll" resultMap="BaseResultMap">
        select
        <include refid="Base_Column_List"/>
        from dish
        <where>
            <if test="criteria.name != null">
                and name like concat('%',#{criteria.name},'%')
            </if>
            <if test="criteria.dishType != null">
                and dish_type = #{criteria.dishType}
            </if>
            <if test="criteria.mealType != null">
                and JSON_CONTAINS(meal_types, '"${criteria.mealType}"') = 1
            </if>
            <if test="criteria.mealPackage != null">
                and JSON_CONTAINS(meal_packages, '"${criteria.mealPackage}"') = 1
            </if>
            <if test="criteria.enabled != null">
                and enabled = #{criteria.enabled}
            </if>
        </where>
        order by sort asc, id desc
    </select>

    <select id="findBySchedule" resultMap="BaseResultMap">
        select
        <include refid="Base_Column_List"/>
        from dish
        where enabled = 1
        <if test="week != null and day != null">
            and JSON_CONTAINS(schedule, '"${week}-${day}"') = 1
        </if>
        <if test="mealType != null">
            and JSON_CONTAINS(meal_types, '"${mealType}"') = 1
        </if>
        order by sort asc
    </select>

    <select id="findAvailableByCustomerId" resultMap="BaseResultMap">
        select d.*
        from dish d
        left join customer_dietaryRestrictions cdr on 1=1
        where d.enabled = 1
        <if test="mealType != null">
            and JSON_CONTAINS(d.meal_types, '"${mealType}"') = 1
        </if>
        <if test="week != null and day != null">
            and JSON_CONTAINS(d.schedule, '"${week}-${day}"') = 1
        </if>
        <if test="customerId != null">
            and not exists (
                select 1 from customer_dietary_restrictions cdr2
                where cdr2.id = #{customerId}
                and (
                    select count(*) from JSON_TABLE(
                        cdr2.restrictions,
                        '$[*]' columns (restriction_path varchar(100) path '$')
                    ) jt
                    where d.ingredients like concat('%', jt.restriction_path, '%')
                ) > 0
            )
        </if>
        order by d.sort asc
    </select>
</mapper>
```

- [ ] 创建 DishMapper.java
- [ ] 创建 DishMapper.xml
- [ ] Commit: `feat(meal): 添加菜品 Mapper`

---

## Chunk 4: 后端 Service

### Task 4.1: 创建 DishService.java

**Files:**
- Create: `eladmin-system/src/main/java/me/zhengjie/modules/meal/service/DishService.java`

```java
package me.zhengjie.modules.meal.service;

import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.dto.DishQueryCriteria;

import java.util.List;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import me.zhengjie.utils.PageResult;

/**
 * 菜品服务接口
 * @author qqx
 * @date 2026-03-14
 **/
public interface DishService extends IService<Dish> {

    /**
     * 查询数据分页
     * @param criteria 条件
     * @param page 分页参数
     * @return PageResult
     */
    PageResult<Dish> queryAll(DishQueryCriteria criteria, Page<Object> page);

    /**
     * 查询所有数据不分页
     * @param criteria 条件参数
     * @return List<Dish>
     */
    List<Dish> queryAll(DishQueryCriteria criteria);

    /**
     * 创建
     * @param resources /
     */
    void create(Dish resources);

    /**
     * 编辑
     * @param resources /
     */
    void update(Dish resources);

    /**
     * 多选删除
     * @param ids /
     */
    void deleteAll(List<Integer> ids);

    /**
     * 导出数据
     * @param all 待导出的数据
     * @param response /
     * @throws IOException /
     */
    void download(List<Dish> all, HttpServletResponse response) throws IOException;

    /**
     * 按排期查询菜品
     * @param week 周数
     * @param day 星期
     * @param mealType 餐次
     * @return List<Dish>
     */
    List<Dish> findBySchedule(Integer week, Integer day, String mealType);

    /**
     * 获取客户可用菜品（根据忌口过滤）
     * @param customerId 客户ID
     * @param mealType 餐次
     * @param week 周数
     * @param day 星期
     * @return List<Dish>
     */
    List<Dish> findAvailableByCustomerId(Integer customerId, String mealType, Integer week, Integer day);
}
```

### Task 4.2: 创建 DishServiceImpl.java

**Files:**
- Create: `eladmin-system/src/main/java/me/zhengjie/modules/meal/service/impl/DishServiceImpl.java`

```java
package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.utils.FileUtil;
import me.zhengjie.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.zhengjie.modules.meal.service.DishService;
import me.zhengjie.modules.meal.domain.dto.DishQueryCriteria;
import me.zhengjie.modules.meal.mapper.DishMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import me.zhengjie.utils.PageUtil;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import me.zhengjie.utils.PageResult;
import java.sql.Timestamp;

/**
 * 菜品服务实现
 * @author qqx
 * @date 2026-03-14
 **/
@Service
@RequiredArgsConstructor
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {

    private final DishMapper dishMapper;

    @Override
    public PageResult<Dish> queryAll(DishQueryCriteria criteria, Page<Object> page){
        return PageUtil.toPage(dishMapper.findAll(criteria, page));
    }

    @Override
    public List<Dish> queryAll(DishQueryCriteria criteria){
        return dishMapper.findAll(criteria);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(Dish resources) {
        resources.setCreateTime(new Timestamp(System.currentTimeMillis()));
        dishMapper.insert(resources);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Dish resources) {
        Dish dish = getById(resources.getId());
        dish.copy(resources);
        dish.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        dishMapper.updateById(dish);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAll(List<Integer> ids) {
        dishMapper.deleteBatchIds(ids);
    }

    @Override
    public void download(List<Dish> all, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Dish dish : all) {
            Map<String,Object> map = new LinkedHashMap<>();
            map.put("菜品名称", dish.getName());
            map.put("做法/流程", dish.getCookingMethod());
            map.put("配料", dish.getIngredients());
            map.put("图片路径", dish.getImageUrl());
            map.put("菜品类型", dish.getDishType());
            map.put("餐次", dish.getMealTypes());
            map.put("所属套餐", dish.getMealPackages());
            map.put("排期", dish.getSchedule());
            map.put("排序", dish.getSort());
            map.put("是否启用", dish.getEnabled());
            map.put("创建时间", dish.getCreateTime());
            map.put("更新时间", dish.getUpdateTime());
            list.add(map);
        }
        FileUtil.downloadExcel(list, response);
    }

    @Override
    public List<Dish> findBySchedule(Integer week, Integer day, String mealType) {
        return dishMapper.findBySchedule(week, day, mealType);
    }

    @Override
    public List<Dish> findAvailableByCustomerId(Integer customerId, String mealType, Integer week, Integer day) {
        return dishMapper.findAvailableByCustomerId(customerId, mealType, week, day);
    }
}
```

- [ ] 创建 DishService.java
- [ ] 创建 DishServiceImpl.java
- [ ] Commit: `feat(meal): 添加菜品 Service`

---

## Chunk 5: 后端 Controller

### Task 5.1: 创建 DishController.java

**Files:**
- Create: `eladmin-system/src/main/java/me/zhengjie/modules/meal/rest/DishController.java`

```java
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
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.service.DishService;
import me.zhengjie.modules.meal.domain.dto.DishQueryCriteria;
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
 * 菜品管理
 * @author qqx
 * @date 2026-03-14
 **/
@RestController
@RequiredArgsConstructor
@Api(tags = "菜品管理")
@RequestMapping("/api/dishes")
public class DishController {

    private final DishService dishService;

    @ApiOperation("导出菜品数据")
    @GetMapping(value = "/download")
    @PreAuthorize("@el.check('dish:list')")
    public void exportDish(HttpServletResponse response, DishQueryCriteria criteria) throws IOException {
        dishService.download(dishService.queryAll(criteria), response);
    }

    @GetMapping
    @ApiOperation("查询菜品")
    @PreAuthorize("@el.check('dish:list')")
    public ResponseEntity<PageResult<Dish>> queryDish(DishQueryCriteria criteria){
        Page<Object> page = new Page<>(criteria.getPage(), criteria.getSize());
        return new ResponseEntity<>(dishService.queryAll(criteria,page),HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @ApiOperation("查询菜品详情")
    @PreAuthorize("@el.check('dish:list')")
    public ResponseEntity<Dish> queryDishById(@PathVariable Integer id){
        return new ResponseEntity<>(dishService.getById(id),HttpStatus.OK);
    }

    @PostMapping
    @Log("新增菜品")
    @ApiOperation("新增菜品")
    @PreAuthorize("@el.check('dish:add')")
    public ResponseEntity<Object> createDish(@Validated @RequestBody Dish resources){
        dishService.create(resources);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PutMapping
    @Log("修改菜品")
    @ApiOperation("修改菜品")
    @PreAuthorize("@el.check('dish:edit')")
    public ResponseEntity<Object> updateDish(@Validated @RequestBody Dish resources){
        dishService.update(resources);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping
    @Log("删除菜品")
    @ApiOperation("删除菜品")
    @PreAuthorize("@el.check('dish:del')")
    public ResponseEntity<Object> deleteDish(@ApiParam(value = "传ID数组[]") @RequestBody List<Integer> ids) {
        dishService.deleteAll(ids);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/schedule")
    @ApiOperation("按排期查询菜品")
    @PreAuthorize("@el.check('dish:list')")
    public ResponseEntity<List<Dish>> queryBySchedule(
            @RequestParam Integer week,
            @RequestParam Integer day,
            @RequestParam(required = false) String mealType){
        return new ResponseEntity<>(dishService.findBySchedule(week, day, mealType),HttpStatus.OK);
    }

    @GetMapping("/available")
    @ApiOperation("获取客户可用菜品（根据忌口过滤）")
    @PreAuthorize("@el.check('dish:list')")
    public ResponseEntity<List<Dish>> queryAvailableDishes(
            @RequestParam Integer customerId,
            @RequestParam String mealType,
            @RequestParam(required = false) Integer week,
            @RequestParam(required = false) Integer day){
        return new ResponseEntity<>(dishService.findAvailableByCustomerId(customerId, mealType, week, day),HttpStatus.OK);
    }
}
```

- [ ] 创建 DishController.java
- [ ] Commit: `feat(meal): 添加菜品 Controller`

---

## Chunk 6: 前端 API

### Task 6.1: 创建 dish.js

**Files:**
- Create: `eladmin-web/src/api/dish.js`

```javascript
import request from '@/utils/request'

export function queryDishes(params) {
  return request({
    url: 'api/dishes',
    method: 'get',
    params
  })
}

export function getDish(id) {
  return request({
    url: `api/dishes/${id}`,
    method: 'get'
  })
}

export function addDish(data) {
  return request({
    url: 'api/dishes',
    method: 'post',
    data
  })
}

export function editDish(data) {
  return request({
    url: 'api/dishes',
    method: 'put',
    data
  })
}

export function delDish(ids) {
  return request({
    url: 'api/dishes/',
    method: 'delete',
    data: ids
  })
}

export function queryBySchedule(params) {
  return request({
    url: 'api/dishes/schedule',
    method: 'get',
    params
  })
}

export function queryAvailableDishes(params) {
  return request({
    url: 'api/dishes/available',
    method: 'get',
    params
  })
}

export default { queryDishes, getDish, addDish, editDish, delDish, queryBySchedule, queryAvailableDishes }
}
```

- [ ] 创建 dish.js
- [ ] Commit: `feat(meal): 添加菜品 API`

---

## Chunk 7: 前端页面

### Task 7.1: 创建菜品列表页 index.vue

**Files:**
- Create: `eladmin-web/src/views/meal/dish/index.vue`

```vue
<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form ref="queryForm" :model="queryParams" :inline="true">
        <el-form-item label="菜品名称" prop="name">
          <el-input
            v-model="queryParams.name"
            placeholder="请输入菜品名称"
            clearable
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="菜品类型" prop="dishType">
          <el-select v-model="queryParams.dishType" placeholder="请选择菜品类型" clearable>
            <el-option label="主菜" value="MAIN" />
            <el-option label="副菜" value="SIDE" />
            <el-option label="汤" value="SOUP" />
            <el-option label="素菜" value="VEGETABLE" />
            <el-option label="米饭" value="RICE" />
          </el-select>
        </el-form-item>
        <el-form-item label="餐次" prop="mealType">
          <el-select v-model="queryParams.mealType" placeholder="请选择餐次" clearable>
            <el-option label="午餐" value="LUNCH" />
            <el-option label="晚餐" value="DINNER" />
          </el-select>
        </el-form-item>
        <el-form-item label="套餐" prop="mealPackage">
          <el-select v-model="queryParams.mealPackage" placeholder="请选择套餐" clearable>
            <el-option label="月子餐" value="yuezi" />
            <el-option label="孕期餐" value="yunqi" />
            <el-option label="小月子" value="xiaoyuezi" />
            <el-option label="营养餐" value="yingyang" />
            <el-option label="分娩餐" value="fenmian" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-select v-model="queryParams.enabled" placeholder="请选择状态" clearable>
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card class="table-card" shadow="never">
      <div slot="header" class="clearfix">
        <el-button type="primary" icon="el-icon-plus" @click="handleAdd">新增</el-button>
        <el-button type="danger" icon="el-icon-delete" :disabled="multiple" @click="handleDelete">删除</el-button>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="dishList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="菜品名称" prop="name" align="center" />
        <el-table-column label="菜品类型" prop="dishType" align="center">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.dishType === 'MAIN'" type="danger">主菜</el-tag>
            <el-tag v-else-if="scope.row.dishType === 'SIDE'" type="warning">副菜</el-tag>
            <el-tag v-else-if="scope.row.dishType === 'SOUP'" type="info">汤</el-tag>
            <el-tag v-else-if="scope.row.dishType === 'VEGETABLE'" type="success">素菜</el-tag>
            <el-tag v-else-if="scope.row.dishType === 'RICE'">米饭</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="餐次" prop="mealTypes" align="center">
          <template slot-scope="scope">
            <el-tag v-for="item in scope.row.mealTypes" :key="item" style="margin-right: 5px;">
              {{ item === 'LUNCH' ? '午餐' : '晚餐' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="套餐" prop="mealPackages" align="center">
          <template slot-scope="scope">
            <el-tag v-for="item in scope.row.mealPackages" :key="item" style="margin-right: 5px;">
              {{ getMealPackageName(item) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="排期" prop="schedule" align="center">
          <template slot-scope="scope">
            <span>{{ formatSchedule(scope.row.schedule) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="enabled" align="center">
          <template slot-scope="scope">
            <el-switch
              v-model="scope.row.enabled"
              :active-value="true"
              :inactive-value="false"
              @change="handleStatusChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150">
          <template slot-scope="scope">
            <el-button type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)">编辑</el-button>
            <el-button type="text" icon="el-icon-delete" style="color: #f56c6c;" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <pagination
        v-show="total > 0"
        :total="total"
        :page.sync="queryParams.page"
        :limit.sync="queryParams.size"
        @pagination="getList"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <dish-form ref="dishForm" @refresh="getList" />
  </div>
</template>

<script>
import { queryDishes, editDish, delDish } from '@/api/dish'
import DishForm from './dish'

export default {
  name: 'Dish',
  components: {
    DishForm
  },
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      total: 0,
      dishList: [],
      queryParams: {
        page: 0,
        size: 10,
        name: null,
        dishType: null,
        mealType: null,
        mealPackage: null,
        enabled: null
      },
      mealPackageMap: {
        yuezi: '月子餐',
        yunqi: '孕期餐',
        xiaoyuezi: '小月子',
        yingyang: '营养餐',
        fenmian: '分娩餐'
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      queryDishes(this.queryParams).then(response => {
        this.dishList = response.content
        this.total = response.totalElements
        this.loading = false
      })
    },
    handleQuery() {
      this.queryParams.page = 0
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleAdd() {
      this.$refs.dishForm.handleAdd()
    },
    handleUpdate(row) {
      this.$refs.dishForm.handleUpdate(row.id)
    },
    handleStatusChange(row) {
      editDish(row).then(() => {
        this.$message.success('更新成功')
      }).catch(() => {
        row.enabled = !row.enabled
      })
    },
    handleDelete(row) {
      const ids = row.id ? [row.id] : this.ids
      this.$confirm('是否确认删除菜品编号为"' + ids + '"的数据项?', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        return delDish(ids)
      }).then(() => {
        this.getList()
        this.$message.success('删除成功')
      }).catch(() => {})
    },
    getMealPackageName(code) {
      return this.mealPackageMap[code] || code
    },
    formatSchedule(schedule) {
      if (!schedule || !schedule.length) return '-'
      return schedule.map(s => {
        const [week, day] = s.split('-')
        return `第${week}周周${['一', '二', '三', '四', '五', '六', '日'][day - 1]}`
      }).join(', ')
    }
  }
}
</script>

<style scoped>
.search-card {
  margin-bottom: 15px;
}
.table-card {
  margin-bottom: 15px;
}
</style>
```

### Task 7.2: 创建菜品新增/编辑弹窗 dish.vue

**Files:**
- Create: `eladmin-web/src/views/meal/dish/dish.vue`

```vue
<template>
  <el-dialog :title="title" :visible.sync="dialogVisible" width="700px" @close="dialogClose">
    <el-form ref="form" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="菜品名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入菜品名称" />
      </el-form-item>
      <el-form-item label="做法/流程" prop="cookingMethod">
        <el-input v-model="form.cookingMethod" type="textarea" :rows="3" placeholder="请输入做法/流程" />
      </el-form-item>
      <el-form-item label="配料" prop="ingredients">
        <el-input v-model="form.ingredients" type="textarea" :rows="2" placeholder="请输入配料，用逗号分隔" />
      </el-form-item>
      <el-form-item label="图片" prop="imageUrl">
        <el-input v-model="form.imageUrl" placeholder="请输入图片路径" />
      </el-form-item>
      <el-form-item label="菜品类型" prop="dishType">
        <el-radio-group v-model="form.dishType">
          <el-radio label="MAIN">主菜</el-radio>
          <el-radio label="SIDE">副菜</el-radio>
          <el-radio label="SOUP">汤</el-radio>
          <el-radio label="VEGETABLE">素菜</el-radio>
          <el-radio label="RICE">米饭</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="餐次" prop="mealTypes">
        <el-checkbox-group v-model="form.mealTypes">
          <el-checkbox label="LUNCH">午餐</el-checkbox>
          <el-checkbox label="DINNER">晚餐</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="套餐" prop="mealPackages">
        <el-checkbox-group v-model="form.mealPackages">
          <el-checkbox label="yuezi">月子餐</el-checkbox>
          <el-checkbox label="yunqi">孕期餐</el-checkbox>
          <el-checkbox label="xiaoyuezi">小月子</el-checkbox>
          <el-checkbox label="yingyang">营养餐</el-checkbox>
          <el-checkbox label="fenmian">分娩餐</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="排期" prop="schedule">
        <div class="schedule-container">
          <div class="schedule-weeks">
            <span class="label">选择周数：</span>
            <el-checkbox-group v-model="selectedWeeks">
              <el-checkbox label="1">第1周</el-checkbox>
              <el-checkbox label="2">第2周</el-checkbox>
              <el-checkbox label="3">第3周</el-checkbox>
              <el-checkbox label="4">第4周</el-checkbox>
            </el-checkbox-group>
          </div>
          <div class="schedule-days">
            <span class="label">选择星期：</span>
            <el-checkbox-group v-model="selectedDays">
              <el-checkbox label="1">周一</el-checkbox>
              <el-checkbox label="2">周二</el-checkbox>
              <el-checkbox label="3">周三</el-checkbox>
              <el-checkbox label="4">周四</el-checkbox>
              <el-checkbox label="5">周五</el-checkbox>
              <el-checkbox label="6">周六</el-checkbox>
              <el-checkbox label="7">周日</el-checkbox>
            </el-checkbox-group>
          </div>
        </div>
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number v-model="form.sort" :min="0" />
      </el-form-item>
      <el-form-item label="是否启用" prop="enabled">
        <el-switch v-model="form.enabled" :active-value="true" :inactive-value="false" />
      </el-form-item>
    </el-form>
    <div slot="footer" class="dialog-footer">
      <el-button type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="cancel">取 消</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { addDish, editDish, getDish } from '@/api/dish'

export default {
  name: 'DishForm',
  data() {
    return {
      dialogVisible: false,
      title: '',
      form: {
        id: null,
        name: '',
        cookingMethod: '',
        ingredients: '',
        imageUrl: '',
        dishType: 'MAIN',
        mealTypes: ['LUNCH'],
        mealPackages: [],
        schedule: [],
        sort: 0,
        enabled: true
      },
      rules: {
        name: [{ required: true, message: '菜品名称不能为空', trigger: 'blur' }],
        dishType: [{ required: true, message: '菜品类型不能为空', trigger: 'change' }],
        mealTypes: [{ required: true, message: '餐次不能为空', trigger: 'change' }]
      },
      selectedWeeks: [],
      selectedDays: []
    }
  },
  watch: {
    selectedWeeks() {
      this.updateSchedule()
    },
    selectedDays() {
      this.updateSchedule()
    }
  },
  methods: {
    handleAdd() {
      this.title = '新增菜品'
      this.dialogVisible = true
      this.resetForm()
    },
    handleUpdate(id) {
      this.title = '编辑菜品'
      getDish(id).then(response => {
        this.form = response
        this.parseSchedule()
        this.dialogVisible = true
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (valid) {
          const action = this.form.id ? editDish : addDish
          action(this.form).then(() => {
            this.$message.success('保存成功')
            this.dialogVisible = false
            this.$emit('refresh')
          })
        }
      })
    },
    cancel() {
      this.dialogVisible = false
    },
    dialogClose() {
      this.resetForm()
    },
    resetForm() {
      this.form = {
        id: null,
        name: '',
        cookingMethod: '',
        ingredients: '',
        imageUrl: '',
        dishType: 'MAIN',
        mealTypes: ['LUNCH'],
        mealPackages: [],
        schedule: [],
        sort: 0,
        enabled: true
      }
      this.selectedWeeks = []
      this.selectedDays = []
      if (this.$refs.form) {
        this.$refs.form.resetFields()
      }
    },
    updateSchedule() {
      const schedule = []
      this.selectedWeeks.forEach(week => {
        this.selectedDays.forEach(day => {
          schedule.push(`${week}-${day}`)
        })
      })
      this.form.schedule = schedule
    },
    parseSchedule() {
      const weeks = new Set()
      const days = new Set()
      if (this.form.schedule && this.form.schedule.length) {
        this.form.schedule.forEach(s => {
          const [week, day] = s.split('-')
          weeks.add(week)
          days.add(day)
        })
      }
      this.selectedWeeks = Array.from(weeks)
      this.selectedDays = Array.from(days)
    }
  }
}
</script>

<style scoped>
.schedule-container {
  border: 1px solid #eee;
  padding: 15px;
  border-radius: 4px;
}
.schedule-weeks,
.schedule-days {
  margin-bottom: 10px;
}
.schedule-weeks .label,
.schedule-days .label {
  display: inline-block;
  width: 80px;
  font-weight: 500;
}
.el-checkbox-group {
  display: inline-block;
}
</style>
```

- [ ] 创建 index.vue
- [ ] 创建 dish.vue
- [ ] Commit: `feat(meal): 添加菜品管理前端页面`

---

## Chunk 8: 数据库脚本

### Task 8.1: 创建数据库建表脚本

**Files:**
- Create: `sql/dish.sql`

```sql
-- 菜品表
CREATE TABLE dish (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '菜品名称',
    cooking_method TEXT COMMENT '做法/流程',
    ingredients TEXT COMMENT '配料',
    image_url VARCHAR(500) COMMENT '图片路径',
    dish_type VARCHAR(20) NOT NULL COMMENT '菜品类型：MAIN主菜、SIDE副菜、SOUP汤、VEGETABLE素菜、RICE米饭',
    meal_types JSON COMMENT '餐次：LUNCH午餐、DINNER晚餐',
    meal_packages JSON COMMENT '所属套餐',
    schedule JSON COMMENT '排期：格式如1-1表示第1周周一',
    sort INT DEFAULT 0 COMMENT '排序',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_dish_type (dish_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品表';
```

- [ ] 创建 dish.sql
- [ ] Commit: `feat(meal): 添加菜品表 DDL`

---

## 实施顺序

1. **Chunk 1**: 创建枚举类 (DishTypeEnum, MealTypeEnum)
2. **Chunk 2**: 创建实体类和查询条件 (Dish, DishQueryCriteria)
3. **Chunk 3**: 创建 Mapper (DishMapper.java, DishMapper.xml)
4. **Chunk 4**: 创建 Service (DishService, DishServiceImpl)
5. **Chunk 5**: 创建 Controller (DishController)
6. **Chunk 6**: 创建前端 API (dish.js)
7. **Chunk 7**: 创建前端页面 (index.vue, dish.vue)
8. **Chunk 8**: 创建数据库脚本 (dish.sql)

---

## 注意事项

1. 前端需要配置路由和菜单权限
2. Mapper XML 中的 JSON 查询需要注意 MySQL 版本兼容性
3. 客户忌口过滤逻辑需要根据实际数据结构调整
