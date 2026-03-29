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
package me.zhengjie.modules.customer.package.service;

import me.zhengjie.modules.customer.package.domain.SubPackage;
import me.zhengjie.modules.customer.package.domain.dto.SubPackageDto;
import java.util.List;

/**
 * 子套餐服务接口
 * @author qqx
 */
public interface SubPackageService {

    SubPackageDto findById(Long id);

    void create(SubPackage resources, Long parentPackageId);

    void update(SubPackage resources);

    void updateStatus(Long id, Integer status);

    void delete(Long id);
}
