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
package me.zhengjie.modules.customer.pkg.service;

import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.domain.dto.ParentPackageDto;
import me.zhengjie.modules.customer.pkg.domain.dto.ParentPackageQueryCriteria;
import me.zhengjie.utils.PageResult;
import java.util.List;

public interface ParentPackageService {

    PageResult<ParentPackageDto> query(ParentPackageQueryCriteria criteria);

    List<ParentPackageDto> getTree();

    ParentPackageDto findById(Long id);

    void create(ParentPackage resources, List<Long> subPackageIds);

    void update(ParentPackage resources, List<Long> subPackageIds);

    void updateStatus(Long id, Integer status);

    void delete(Long id);
}
