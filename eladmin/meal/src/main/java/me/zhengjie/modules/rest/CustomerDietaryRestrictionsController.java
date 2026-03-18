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
package me.zhengjie.modules.rest;

import me.zhengjie.annotation.Log;
import me.zhengjie.modules.domain.CustomerDietaryRestrictions;
import me.zhengjie.modules.service.CustomerDietaryRestrictionsService;
import me.zhengjie.modules.domain.dto.CustomerDietaryRestrictionsQueryCriteria;
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
* @author qqx
* @date 2026-03-14
**/
@RestController
@RequiredArgsConstructor
@Api(tags = "meal")
@RequestMapping("/api/customerDietaryRestrictions")
public class CustomerDietaryRestrictionsController {

    private final CustomerDietaryRestrictionsService customerDietaryRestrictionsService;

    @ApiOperation("导出数据")
    @GetMapping(value = "/download")
    @PreAuthorize("@el.check('customerDietaryRestrictions:list')")
    public void exportCustomerDietaryRestrictions(HttpServletResponse response, CustomerDietaryRestrictionsQueryCriteria criteria) throws IOException {
        customerDietaryRestrictionsService.download(customerDietaryRestrictionsService.queryAll(criteria), response);
    }

    @GetMapping
    @ApiOperation("查询meal")
    @PreAuthorize("@el.check('customerDietaryRestrictions:list')")
    public ResponseEntity<PageResult<CustomerDietaryRestrictions>> queryCustomerDietaryRestrictions(CustomerDietaryRestrictionsQueryCriteria criteria){
        Page<Object> page = new Page<>(criteria.getPage(), criteria.getSize());
        return new ResponseEntity<>(customerDietaryRestrictionsService.queryAll(criteria,page),HttpStatus.OK);
    }

    @PostMapping
    @Log("新增meal")
    @ApiOperation("新增meal")
    @PreAuthorize("@el.check('customerDietaryRestrictions:add')")
    public ResponseEntity<Object> createCustomerDietaryRestrictions(@Validated @RequestBody CustomerDietaryRestrictions resources){
        customerDietaryRestrictionsService.create(resources);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PutMapping
    @Log("修改meal")
    @ApiOperation("修改meal")
    @PreAuthorize("@el.check('customerDietaryRestrictions:edit')")
    public ResponseEntity<Object> updateCustomerDietaryRestrictions(@Validated @RequestBody CustomerDietaryRestrictions resources){
        customerDietaryRestrictionsService.update(resources);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping
    @Log("删除meal")
    @ApiOperation("删除meal")
    @PreAuthorize("@el.check('customerDietaryRestrictions:del')")
    public ResponseEntity<Object> deleteCustomerDietaryRestrictions(@ApiParam(value = "传ID数组[]") @RequestBody List<Integer> ids) {
        customerDietaryRestrictionsService.deleteAll(ids);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}