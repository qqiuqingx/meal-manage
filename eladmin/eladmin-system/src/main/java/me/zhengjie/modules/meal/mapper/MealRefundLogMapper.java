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
package me.zhengjie.modules.meal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.modules.meal.domain.MealRefundLog;
import me.zhengjie.modules.meal.domain.dto.MealRefundLogVO;
import me.zhengjie.modules.meal.domain.dto.MealRefundQueryCriteria;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 退餐日志Mapper
 * @author qqx
 * @date 2026-04-19
 **/
@Mapper
public interface MealRefundLogMapper extends BaseMapper<MealRefundLog> {

    /**
     * 分页查询退餐日志
     * @param criteria 查询条件
     * @param page     分页参数
     * @return 退餐日志分页结果
     */
    Page<MealRefundLogVO> selectPageByCriteria(@Param("criteria") MealRefundQueryCriteria criteria,
                                                Page<MealRefundLogVO> page);
}
