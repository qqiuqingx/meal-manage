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
package me.zhengjie.modules.service.impl;

import me.zhengjie.modules.domain.CustomerDietaryRestrictions;
import me.zhengjie.utils.FileUtil;
import me.zhengjie.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.zhengjie.modules.service.CustomerDietaryRestrictionsService;
import me.zhengjie.modules.domain.dto.CustomerDietaryRestrictionsQueryCriteria;
import me.zhengjie.modules.mapper.CustomerDietaryRestrictionsMapper;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
* @description 服务实现
* @author qqx
* @date 2026-03-14
**/
@Service
@RequiredArgsConstructor
public class CustomerDietaryRestrictionsServiceImpl extends ServiceImpl<CustomerDietaryRestrictionsMapper, CustomerDietaryRestrictions> implements CustomerDietaryRestrictionsService {

    private final CustomerDietaryRestrictionsMapper customerDietaryRestrictionsMapper;

    @Override
    public PageResult<CustomerDietaryRestrictions> queryAll(CustomerDietaryRestrictionsQueryCriteria criteria, Page<Object> page){
        return PageUtil.toPage(customerDietaryRestrictionsMapper.findAll(criteria, page));
    }

    @Override
    public List<CustomerDietaryRestrictions> queryAll(CustomerDietaryRestrictionsQueryCriteria criteria){
        return customerDietaryRestrictionsMapper.findAll(criteria);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(CustomerDietaryRestrictions resources) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        resources.setCreateTime(now);
        resources.setCreatedBy(SecurityUtils.getCurrentUsername());
        customerDietaryRestrictionsMapper.insert(resources);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerDietaryRestrictions resources) {
        CustomerDietaryRestrictions customerDietaryRestrictions = getById(resources.getId());
        customerDietaryRestrictions.copy(resources);
        customerDietaryRestrictions.setUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        customerDietaryRestrictions.setUpdatedBy(SecurityUtils.getCurrentUsername());
        customerDietaryRestrictionsMapper.updateById(customerDietaryRestrictions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAll(List<Integer> ids) {
        customerDietaryRestrictionsMapper.deleteBatchIds(ids);
    }

    @Override
    public void download(List<CustomerDietaryRestrictions> all, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (CustomerDietaryRestrictions customerDietaryRestrictions : all) {
            Map<String,Object> map = new LinkedHashMap<>();
            map.put("客户名称", customerDietaryRestrictions.getCustomerName());
            map.put("特殊要求", customerDietaryRestrictions.getSpecialNeeds());
            map.put("忌口", customerDietaryRestrictions.getRestrictions());
            map.put("orderDate", customerDietaryRestrictions.getOrderDate());
            map.put("updateDate", customerDietaryRestrictions.getUpdateDate());
            map.put("createTime", customerDietaryRestrictions.getCreateTime());
            map.put("updateTime", customerDietaryRestrictions.getUpdateTime());
            map.put("createdBy", customerDietaryRestrictions.getCreatedBy());
            map.put("updatedBy", customerDietaryRestrictions.getUpdatedBy());
            map.put("餐数", customerDietaryRestrictions.getNum());
            map.put("开始时间", customerDietaryRestrictions.getStartDate());
            map.put("结束时间", customerDietaryRestrictions.getEndDate());
            list.add(map);
        }
        FileUtil.downloadExcel(list, response);
    }
}