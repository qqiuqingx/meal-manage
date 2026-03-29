# 套餐管理数据结构重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `customer_package_category` 单表二级树重构为 3 张独立表（parent_package / sub_package / parent_package_sub），前端改为树形折叠交互，并完成订单/客户详情等关联模块的套餐数据源切换。

**Architecture:**
- 后端新增 `modules/customer/package/` 模块，含 3 个 Entity、3 个 Mapper、2 个 Service（含 Impl）、1 个 Controller
- 前端新建套餐管理页面，父套餐行点击展开子套餐列表
- 旧 `customer_package_category` 表删除，相关代码数据源切换到新表

**Tech Stack:** Spring Boot + MyBatis-Plus + Vue 2 + element-ui

---

## 文件结构总览

```
后端新建:
  eladmin-system/src/main/java/me/zhengjie/modules/customer/package/
    domain/
      ParentPackage.java
      SubPackage.java
      ParentPackageSub.java
      dto/
        ParentPackageDto.java
        ParentPackageQueryCriteria.java
    mapper/
      ParentPackageMapper.java
      SubPackageMapper.java
      ParentPackageSubMapper.java
    service/
      ParentPackageService.java
      impl/ParentPackageServiceImpl.java
      SubPackageService.java
      impl/SubPackageServiceImpl.java
    rest/
      ParentPackageController.java
  eladmin-system/src/main/resources/mapper/package/
    ParentPackageMapper.xml
    SubPackageMapper.xml
    ParentPackageSubMapper.xml

后端修改:
  eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerProfilePackage.java
  eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/impl/CustomerProfilePackageServiceImpl.java
  eladmin-system/src/main/java/me/zhengjie/modules/order/service/impl/OrderServiceImpl.java  # 套餐选择器数据源
  eladmin-web/src/api/customer/package.js                          (新建)
  eladmin-web/src/views/customer/package/index.vue                  (新建)
  eladmin-web/src/views/customer/profile/CustomerDetailDialog.vue  (修改: 套餐名展示)
  eladmin-web/src/components/Order/OrderForm.vue                   (修改: 套餐选择器数据源)

数据库:
  sql/package-management.sql  (新建 DDL + 种子数据 + sys_menu 路由)
```

---

## 任务列表

### 阶段一：数据库准备

#### Task 1: 编写 DDL + 种子数据 SQL

**Files:**
- Create: `eladmin/sql/package-management.sql`

```sql
-- =============================================
-- 套餐管理模块 - 数据库脚本
-- =============================================

-- 1. 父套餐表
CREATE TABLE `parent_package` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `package_code` VARCHAR(50) NOT NULL COMMENT '套餐编码',
  `prefix` CHAR(1) NOT NULL COMMENT '单字母前缀，如A/B/C，用于拼接客户编号',
  `package_name` VARCHAR(100) NOT NULL COMMENT '套餐名称',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-停用',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_code` (`package_code`),
  UNIQUE KEY `uk_prefix` (`prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='父套餐表';

-- 2. 子套餐表（含规则字段）
CREATE TABLE `sub_package` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sub_package_code` VARCHAR(50) NOT NULL COMMENT '子套餐编码',
  `sub_package_name` VARCHAR(100) NOT NULL COMMENT '子套餐名称',
  `meat_count` INT NOT NULL DEFAULT 0 COMMENT '荤菜数量',
  `veg_count` INT NOT NULL DEFAULT 0 COMMENT '素菜数量',
  `include_soup` TINYINT NOT NULL DEFAULT 0 COMMENT '是否含汤：1=是 0=否',
  `include_rice` TINYINT NOT NULL DEFAULT 1 COMMENT '是否含米饭：1=是 0=否',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-停用',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sub_package_code` (`sub_package_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='子套餐表';

-- 3. 父子关联表
CREATE TABLE `parent_package_sub` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_package_id` BIGINT NOT NULL COMMENT '父套餐ID',
  `sub_package_id` BIGINT NOT NULL COMMENT '子套餐ID',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_parent_sub` (`parent_package_id`, `sub_package_id`),
  KEY `idx_parent_package_id` (`parent_package_id`),
  KEY `idx_sub_package_id` (`sub_package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='父套餐与子套餐关系表';

-- =============================================
-- 种子数据
-- =============================================

-- 父套餐：健康餐（prefix: J）、营养餐（prefix: Y）
INSERT INTO parent_package (package_code, prefix, package_name, status, remark) VALUES
('JKC001', 'J', '健康餐', 1, '健康餐系列'),
('YYC001', 'Y', '营养餐', 1, '营养餐系列');

-- 子套餐（健康餐系列）
INSERT INTO sub_package (sub_package_code, sub_package_name, meat_count, veg_count, include_soup, include_rice, status, remark) VALUES
('JKC001-01', '一荤一素', 1, 1, 1, 1, 1, '一荤一素含汤含米饭'),
('JKC001-02', '两荤一素', 2, 1, 1, 1, 1, '两荤一素含汤含米饭'),
('JKC001-03', '减脂餐', 1, 2, 0, 1, 1, '减脂餐无汤');

-- 子套餐（营养餐系列）
INSERT INTO sub_package (sub_package_code, sub_package_name, meat_count, veg_count, include_soup, include_rice, status, remark) VALUES
('YYC001-01', '标准餐', 1, 1, 1, 1, 1, '标准配置'),
('YYC001-02', '轻食餐', 0, 2, 0, 1, 1, '全素轻食');

-- 父子关联
INSERT INTO parent_package_sub (parent_package_id, sub_package_id) VALUES
(1, 1), (1, 2), (1, 3),
(2, 4), (2, 5);

-- =============================================
-- sys_menu 路由配置（替代旧的套餐分类管理）
-- =============================================

-- 删除旧路由（可选，确认无问题后执行）
-- DELETE FROM sys_menu WHERE title = '套餐分类管理';

-- 新增套餐管理菜单
INSERT INTO sys_menu (pid, sub_count, type, title, component, path, icon, is_aes, is_cache, is_show, status, sort, create_by, update_by, created_at, updated_at) VALUES
(122, 0, 1, '套餐管理', 'customer/package/index', 'customer/package/index', 'component', 0, 0, 1, 1, 5, 'admin', 'admin', NOW(), NOW());

-- 获取新菜单ID后，插入按钮权限
-- @AUTO_REMAIN: 权限标识使用 customerPackage:list/add/edit/del/status
```

---

### 阶段二：后端实体类

#### Task 2: 创建父套餐 Entity

**Files:**
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/domain/ParentPackage.java`

```java
package me.zhengjie.modules.customer.package.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("parent_package")
public class ParentPackage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String packageCode;

    private String prefix;  // 单个大写字母

    private String packageName;

    private Integer status;  // 1=启用, 0=停用

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

#### Task 3: 创建子套餐 Entity（含规则字段）

**Files:**
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/domain/SubPackage.java`

```java
package me.zhengjie.modules.customer.package.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sub_package")
public class SubPackage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String subPackageCode;

    private String subPackageName;

    private Integer meatCount;     // 荤菜数量

    private Integer vegCount;      // 素菜数量

    private Integer includeSoup;    // 是否含汤

    private Integer includeRice;    // 是否含米饭

    private Integer status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

#### Task 4: 创建父子关联 Entity

**Files:**
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/domain/ParentPackageSub.java`

```java
package me.zhengjie.modules.customer.package.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("parent_package_sub")
public class ParentPackageSub {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentPackageId;

    private Long subPackageId;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

#### Task 5: 创建 ParentPackageDto（含子套餐列表）

**Files:**
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/domain/dto/ParentPackageDto.java`

```java
package me.zhengjie.modules.customer.package.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class ParentPackageDto {

    private Long id;
    private String packageCode;
    private String prefix;
    private String packageName;
    private Integer status;
    private String remark;

    // 关联的子套餐列表（用于展开展示）
    private List<SubPackageDto> children;
}
```

#### Task 6: 创建 SubPackageDto

**Files:**
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/domain/dto/SubPackageDto.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/domain/dto/SubPackageCreateDto.java`

```java
// SubPackageDto.java — 用于列表展示和详情
package me.zhengjie.modules.customer.package.domain.dto;
import lombok.Data;

@Data
public class SubPackageDto {
    private Long id;
    private String subPackageCode;
    private String subPackageName;
    private Integer meatCount;
    private Integer vegCount;
    private Integer includeSoup;
    private Integer includeRice;
    private Integer status;
    private String remark;
}

// SubPackageCreateDto.java — 用于新增子套餐（包含所属父套餐ID）
package me.zhengjie.modules.customer.package.domain.dto;
import lombok.Data;

@Data
public class SubPackageCreateDto {
    private String subPackageCode;
    private String subPackageName;
    private Integer meatCount;
    private Integer vegCount;
    private Integer includeSoup;
    private Integer includeRice;
    private String remark;
    private Long parentPackageId;  // 所属父套餐
}
```

#### Task 7: 创建 ParentPackageQueryCriteria

**Files:**
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/domain/dto/ParentPackageQueryCriteria.java`

```java
package me.zhengjie.modules.customer.package.domain.dto;

import lombok.Data;

@Data
public class ParentPackageQueryCriteria {

    private String packageName;
    private String packageCode;
    private String prefix;
    private Integer status;
}
```

---

### 阶段三：后端 Mapper 层

#### Task 8: 创建三个 Mapper 接口

**Files:**
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/mapper/ParentPackageMapper.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/mapper/SubPackageMapper.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/mapper/ParentPackageSubMapper.java`

```java
// ParentPackageMapper.java
package me.zhengjie.modules.customer.package.mapper;

import me.zhengjie.modules.customer.package.domain.ParentPackage;
import me.zhengjie.modules.customer.package.domain.dto.ParentPackageQueryCriteria;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ParentPackageMapper extends BaseMapper<ParentPackage> {

    List<ParentPackage> queryList(ParentPackageQueryCriteria criteria);

    Boolean existsByPrefix(@Param("prefix") String prefix, @Param("excludeId") Long excludeId);

    Long countSubPackagesByParentId(@Param("parentPackageId") Long parentPackageId);

    Integer countOrderByParentPackageId(@Param("parentPackageId") Long parentPackageId);
}
```

```java
// SubPackageMapper.java
package me.zhengjie.modules.customer.package.mapper;

import me.zhengjie.modules.customer.package.domain.SubPackage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SubPackageMapper extends BaseMapper<SubPackage> {

    List<SubPackage> findByParentPackageId(@Param("parentPackageId") Long parentPackageId);

    Integer countOrderBySubPackageId(@Param("subPackageId") Long subPackageId);

    Integer countOrderByParentPackageId(@Param("parentPackageId") Long parentPackageId);
}
```

```java
// ParentPackageSubMapper.java
package me.zhengjie.modules.customer.package.mapper;

import me.zhengjie.modules.customer.package.domain.ParentPackageSub;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ParentPackageSubMapper extends BaseMapper<ParentPackageSub> {

    List<ParentPackageSub> findByParentPackageId(@Param("parentPackageId") Long parentPackageId);

    void deleteByParentPackageId(@Param("parentPackageId") Long parentPackageId);

    void batchInsert(@Param("records") List<ParentPackageSub> records);
}
```

#### Task 9: 创建 XML Mapper

**Files:**
- Create: `eladmin/eladmin-system/src/main/resources/mapper/package/ParentPackageMapper.xml`
- Create: `eladmin/eladmin-system/src/main/resources/mapper/package/SubPackageMapper.xml`
- Create: `eladmin/eladmin-system/src/main/resources/mapper/package/ParentPackageSubMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="me.zhengjie.modules.customer.package.mapper.ParentPackageMapper">

    <select id="queryList" resultType="me.zhengjie.modules.customer.package.domain.ParentPackage">
        SELECT * FROM parent_package
        <where>
            <if test="packageName != null and packageName != ''">
                AND package_name LIKE CONCAT('%', #{packageName}, '%')
            </if>
            <if test="packageCode != null and packageCode != ''">
                AND package_code = #{packageCode}
            </if>
            <if test="prefix != null and prefix != ''">
                AND prefix = #{prefix}
            </if>
            <if test="status != null">
                AND status = #{status}
            </if>
        </where>
        ORDER BY prefix ASC, id ASC
    </select>

    <select id="existsByPrefix" resultType="java.lang.Boolean">
        SELECT COUNT(1) > 0 FROM parent_package
        WHERE prefix = #{prefix}
        <if test="excludeId != null">
            AND id != #{excludeId}
        </if>
    </select>

    <select id="countSubPackagesByParentId" resultType="java.lang.Long">
        SELECT COUNT(1) FROM parent_package_sub WHERE parent_package_id = #{parentPackageId}
    </select>

    <select id="countOrderByParentPackageId" resultType="java.lang.Integer">
        SELECT COUNT(1) FROM customer_order
        WHERE parent_package_id = #{parentPackageId}
    </select>
</mapper>
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="me.zhengjie.modules.customer.package.mapper.SubPackageMapper">

    <select id="findByParentPackageId" resultType="me.zhengjie.modules.customer.package.domain.SubPackage">
        SELECT sp.* FROM sub_package sp
        INNER JOIN parent_package_sub pps ON sp.id = pps.sub_package_id
        WHERE pps.parent_package_id = #{parentPackageId}
        ORDER BY sp.id ASC
    </select>

    <select id="countOrderBySubPackageId" resultType="java.lang.Integer">
        SELECT COUNT(1) FROM customer_order WHERE child_package_id = #{subPackageId}
    </select>

    <select id="countOrderByParentPackageId" resultType="java.lang.Integer">
        SELECT COUNT(1) FROM customer_order WHERE parent_package_id = #{parentPackageId}
    </select>
</mapper>
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="me.zhengjie.modules.customer.package.mapper.ParentPackageSubMapper">

    <select id="findByParentPackageId" resultType="me.zhengjie.modules.customer.package.domain.ParentPackageSub">
        SELECT * FROM parent_package_sub WHERE parent_package_id = #{parentPackageId}
    </select>

    <delete id="deleteByParentPackageId">
        DELETE FROM parent_package_sub WHERE parent_package_id = #{parentPackageId}
    </delete>

    <insert id="batchInsert" parameterType="java.util.List">
        INSERT INTO parent_package_sub (parent_package_id, sub_package_id, status, created_at)
        VALUES
        <foreach collection="records" item="item" separator=",">
            (#{item.parentPackageId}, #{item.subPackageId}, #{item.status}, NOW())
        </foreach>
    </insert>
</mapper>
```

---

### 阶段四：后端 Service 层

#### Task 10: 创建 ParentPackageService（含完整业务逻辑）

**Files:**
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/service/ParentPackageService.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/service/impl/ParentPackageServiceImpl.java`

**ParentPackageService.java:**
```java
package me.zhengjie.modules.customer.package.service;

import me.zhengjie.modules.customer.package.domain.ParentPackage;
import me.zhengjie.modules.customer.package.domain.dto.ParentPackageDto;
import me.zhengjie.modules.customer.package.domain.dto.ParentPackageQueryCriteria;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public interface ParentPackageService {

    Object query(ParentPackageQueryCriteria criteria);

    List<ParentPackageDto> getTree();

    ParentPackageDto findById(Long id);

    void create(ParentPackage resources, List<Long> subPackageIds);

    void update(ParentPackage resources, List<Long> subPackageIds);

    void updateStatus(Long id, Integer status);

    void delete(Long id);
}
```

**ParentPackageServiceImpl.java 要实现的核心逻辑:**

1. `getTree()` — 查询所有父套餐，每个父套餐通过 `SubPackageMapper.findByParentPackageId` 加载子套餐列表，返回 `List<ParentPackageDto>`
2. `create(resources, subPackageIds)` — 保存父套餐 → 批量插入 `parent_package_sub` 关联记录
3. `update(resources, subPackageIds)` — 更新父套餐 → 先删旧关联 → 再批量插入新关联
4. `updateStatus(id, status)` — 如果停用(status=0)，先检查 `ParentPackageSubMapper.findByParentPackageId` 是否有启用状态的子套餐关联，有则抛异常
5. `delete(id)` — 先检查 `ParentPackageMapper.countSubPackagesByParentId`，有子套餐关联则抛异常 → 再删关联记录 → 再删父套餐
6. Prefix 唯一性校验：在 `create` / `update` 中调用 `ParentPackageMapper.existsByPrefix`

#### Task 11: 创建 SubPackageService（含订单引用检查）

**Files:**
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/service/SubPackageService.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/service/impl/SubPackageServiceImpl.java`

**SubPackageService.java:**
```java
package me.zhengjie.modules.customer.package.service;

import me.zhengjie.modules.customer.package.domain.SubPackage;
import me.zhengjie.modules.customer.package.domain.dto.SubPackageDto;
import java.util.List;

public interface SubPackageService {

    SubPackageDto findById(Long id);

    void create(SubPackage resources, Long parentPackageId);

    void update(SubPackage resources);

    void updateStatus(Long id, Integer status);

    void delete(Long id);
}
```

**SubPackageServiceImpl.java 要实现的核心逻辑:**

1. `create(resources, parentPackageId)` — 保存子套餐 → 插入 `parent_package_sub` 关联记录（status=1）
2. `update(resources)` — 更新子套餐字段
3. `delete(id)` — 删除前调用 `SubPackageMapper.countOrderBySubPackageId` 和 `countOrderByParentPackageId` 检查 `customer_order` 是否有引用，有则抛"该子套餐已被订单引用，无法删除"异常 → 删除关联记录 → 删除子套餐

---

### 阶段五：后端 Controller 层

#### Task 12: 创建 ParentPackageController

**Files:**
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/package/rest/ParentPackageController.java`

参考现有 `CustomerPackageCategoryController` 的风格：

```java
package me.zhengjie.modules.customer.package.rest;

@RestController
@RequestMapping("/api/package")
@RequiredArgsConstructor
public class ParentPackageController {

    private final ParentPackageService parentPackageService;
    private final SubPackageService subPackageService;

    @GetMapping
    @PreAuthorize("@el.check('package:list')")
    public Object query(ParentPackageQueryCriteria criteria) {
        return parentPackageService.query(criteria);
    }

    @GetMapping("/tree")
    @PreAuthorize("@el.check('package:list')")
    public List<ParentPackageDto> getTree() {
        return parentPackageService.getTree();
    }

    @GetMapping("/parent/{id}")
    @PreAuthorize("@el.check('package:list')")
    public ParentPackageDto findById(@PathVariable Long id) {
        return parentPackageService.findById(id);
    }

    @PostMapping
    @Log("新增套餐")
    @PreAuthorize("@el.check('package:add')")
    public void create(@RequestBody ParentPackageDto dto) {
        // dto 含父套餐字段 + children[].id（子套餐ID列表）
    }

    @PutMapping
    @Log("编辑套餐")
    @PreAuthorize("@el.check('package:edit')")
    public void update(@RequestBody ParentPackageDto dto) {
    }

    @PutMapping("/status/{id}")
    @Log("修改套餐状态")
    @PreAuthorize("@el.check('package:status')")
    public void updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        parentPackageService.updateStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @Log("删除套餐")
    @PreAuthorize("@el.check('package:del')")
    public void delete(@PathVariable Long id) {
        parentPackageService.delete(id);
    }

    // ===== 子套餐独立操作 =====
    @GetMapping("/sub/{id}")
    public SubPackageDto findSubById(@PathVariable Long id) {
        return subPackageService.findById(id);
    }

    @PostMapping("/sub")
    @Log("新增子套餐")
    @PreAuthorize("@el.check('package:add')")
    public void createSub(@RequestBody SubPackageCreateDto dto) {
        // dto 含子套餐字段 + parentPackageId
    }

    @PutMapping("/sub")
    @Log("编辑子套餐")
    @PreAuthorize("@el.check('package:edit')")
    public void updateSub(@RequestBody SubPackage resources) {
        subPackageService.update(resources);
    }

    @PutMapping("/sub/status/{id}")
    @Log("修改子套餐状态")
    @PreAuthorize("@el.check('package:status')")
    public void updateSubStatus(@PathVariable Long id, @RequestParam Integer status) {
        subPackageService.updateStatus(id, status);
    }

    @DeleteMapping("/sub/{id}")
    @Log("删除子套餐")
    @PreAuthorize("@el.check('package:del')")
    public void deleteSub(@PathVariable Long id) {
        subPackageService.delete(id);
    }
}
```

---

### 阶段六：前端新建套餐管理页面

#### Task 13: 创建前端 API 文件

**Files:**
- Create: `eladmin-web/src/api/customer/package.js`

参考现有 `packageCategory.js` 风格，导出:
- `getTree()` → GET `/api/package/tree`
- `getParents()` → GET `/api/package` (query)
- `add(data)` → POST `/api/package`
- `edit(data)` → PUT `/api/package`
- `editStatus(id, status)` → PUT `/api/package/status/{id}`
- `del(id)` → DELETE `/api/package/{id}`
- 子套餐相关: `addSub(data)`, `editSub(data)`, `editSubStatus(id, status)`, `delSub(id)`

#### Task 14: 创建套餐管理页面组件

**Files:**
- Create: `eladmin-web/src/views/customer/package/index.vue`

参考 `views/customer/packageCategory/index.vue` 的 CRUD 框架，改写为核心功能：

**数据结构:**
```javascript
treeData: []  // List<ParentPackageDto>
// 每个节点: { id, packageCode, prefix, packageName, status, remark, children: List<SubPackageDto> }
// 子节点: { id, subPackageCode, subPackageName, meatCount, vegCount, includeSoup, includeRice, status, remark }
```

**核心交互:**
1. 顶部新增父套餐按钮 → 弹窗表单（packageCode, prefix[单字母], packageName, remark）
2. `el-table` 展示父套餐，`el-table-column` type="expand" + `v-show="scope.row.children && scope.row.children.length"` 展开子套餐
3. 展开行内用内嵌 `el-table` 展示子套餐列表（列：子套餐名、荤素数量、含汤/米饭、状态、操作）
4. 子套餐行内操作：编辑（弹窗含所有规则字段）、删除、状态切换
5. 父套餐行内操作：新增子套餐按钮、编辑、删除、状态切换
6. 编辑父套餐弹窗：支持勾选已关联的子套餐（多选下拉或穿梭框）

---

### 阶段七：关联模块数据源切换

#### Task 15: 切换客户详情套餐名展示

**Files:**
- Modify: `eladmin-web/src/views/customer/profile/CustomerDetailDialog.vue`

将套餐名称展示从旧表查询切换为调用新 API:
- `parentPackageId` → 查 `/api/package/parent/{id}` 获取 `packageName`
- `childPackageId` → 查 `/api/package/sub/{id}` 获取 `subPackageName`

#### Task 16: 切换订单表单套餐选择器

**Files:**
- Modify: `eladmin-web/src/components/Order/OrderForm.vue`
- Modify: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/order/service/impl/OrderServiceImpl.java`

前端:
- 父套餐下拉：调用 GET `/api/package/tree`，展示 `packageName`，提交 `id`
- 子套餐下拉：根据选中的父套餐 ID，调用子套餐列表接口

后端:
- `OrderServiceImpl` 中的套餐选择器数据源改为 `ParentPackageMapper` / `SubPackageMapper`

#### Task 17: 切换客户签约套餐数据源

**Files:**
- Modify: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/impl/CustomerProfileServiceImpl.java`
  - 该 Service 注入了 `CustomerProfilePackageMapper`，签约套餐数据展示改为查询新表
  - 具体修改点：查询套餐名称时，将旧 `CustomerPackageCategoryMapper` 调用替换为新 `ParentPackageMapper` / `SubPackageMapper` 调用
  - 字段名 `parentPackageId` / `childPackageId` 不变，只需修改 ID 来源查询

> **说明**：无独立的 `CustomerProfilePackageServiceImpl`，签约相关逻辑在 `CustomerProfileServiceImpl` 中。

---

### 阶段八：旧表删除与菜单配置

#### Task 18: 更新 sys_menu 并删除旧表

**Files:**
- Modify: `eladmin/sql/package-management.sql` (追加 DELETE + INSERT)

```sql
-- 删除旧的套餐分类管理菜单和旧表
DELETE FROM sys_menu WHERE title = '套餐分类管理';

DROP TABLE IF EXISTS customer_package_category;
```

**注意**: 执行顺序：1）确认新套餐模块正常运行 → 2）删除旧菜单路由 → 3）删除旧表。旧表数据直接丢弃（用户已确认无需迁移），新表已有种子数据。

---

## 验收标准

- [ ] `parent_package` / `sub_package` / `parent_package_sub` 三张表正常创建，种子数据正确
- [ ] 套餐管理页面父套餐列表正常展示，点击行展开显示子套餐
- [ ] 父套餐 CRUD 正常（新增/编辑/删除/状态切换）
- [ ] 子套餐 CRUD 正常（新增/编辑/删除/状态切换），含规则字段
- [ ] 删除父套餐时服务层自动清理关联记录
- [ ] 删除子套餐时正确检查 `customer_order` 引用，有引用则报错
- [ ] 订单表单套餐选择器数据源切换到新表
- [ ] 客户详情弹窗套餐名称展示正确
- [ ] 旧表 `customer_package_category` 删除干净
- [ ] `customer_profile_package` 签约模块数据源切换正确
