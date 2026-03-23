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
package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.modules.meal.domain.CustomerDietaryRestrictions;
import me.zhengjie.utils.FileUtil;
import me.zhengjie.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.zhengjie.modules.meal.service.CustomerDietaryRestrictionsService;
import me.zhengjie.modules.meal.domain.dto.CustomerDietaryRestrictionsQueryCriteria;
import me.zhengjie.modules.meal.mapper.CustomerDietaryRestrictionsMapper;
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
import java.sql.Timestamp;

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
        // created_at (varchar) - 存储创建人用户名
        resources.setCreateAt(SecurityUtils.getCurrentUsername());
        // create_time (datetime) - 存储创建时间
        resources.setCreateTime(new Timestamp(System.currentTimeMillis()));
        // 新增时，剩余餐数默认等于餐数
        if (resources.getNum() != null) {
            resources.setRemainingMeals(resources.getNum());
        }
        customerDietaryRestrictionsMapper.insert(resources);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerDietaryRestrictions resources) {
        CustomerDietaryRestrictions customerDietaryRestrictions = getById(resources.getId());
        customerDietaryRestrictions.copy(resources);
        // updated_at (varchar) - 存储更新人用户名
        customerDietaryRestrictions.setUpdateAt(SecurityUtils.getCurrentUsername());
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
            map.put("updateDate", customerDietaryRestrictions.getUpdateDate());
            map.put("createdAt", customerDietaryRestrictions.getCreateAt());
            map.put("updatedAt", customerDietaryRestrictions.getUpdateAt());
            map.put("createTime", customerDietaryRestrictions.getCreateTime());
            map.put("餐数", customerDietaryRestrictions.getNum());
            map.put("开始时间", customerDietaryRestrictions.getStartDate());
            map.put("结束时间", customerDietaryRestrictions.getEndDate());
            map.put("客户地址", customerDietaryRestrictions.getCustomerAddress());
            map.put("客户手机号", customerDietaryRestrictions.getPhone());
            map.put("剩余餐数", customerDietaryRestrictions.getRemainingMeals());
            map.put("客户套餐", customerDietaryRestrictions.getMealPackage());
            map.put("来源", customerDietaryRestrictions.getSource());
            list.add(map);
        }
        FileUtil.downloadExcel(list, response);
    }
}