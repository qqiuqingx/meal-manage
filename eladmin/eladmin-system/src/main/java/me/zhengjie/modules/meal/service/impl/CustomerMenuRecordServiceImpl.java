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

import me.zhengjie.modules.meal.domain.CustomerMenuRecord;
import me.zhengjie.modules.meal.service.CustomerMenuRecordService;
import me.zhengjie.modules.meal.mapper.CustomerMenuRecordMapper;
import me.zhengjie.modules.meal.service.impl.CustomerMenuRecordServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Primary;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
* @description 服务实现
* @author qqx
* @date 2026-03-14
**/
@Service
@Primary
public class CustomerMenuRecordServiceImpl extends ServiceImpl<CustomerMenuRecordMapper, CustomerMenuRecord> implements CustomerMenuRecordService {

}
