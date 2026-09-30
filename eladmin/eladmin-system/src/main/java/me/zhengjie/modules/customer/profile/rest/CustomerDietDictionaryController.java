package me.zhengjie.modules.customer.profile.rest;

import io.swagger.annotations.ApiOperation;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;
import me.zhengjie.modules.customer.profile.service.CustomerDietDictionaryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户饮食对象只读字典接口。
 */
@RestController
@RequestMapping("/api/customerProfile/diet-options")
public class CustomerDietDictionaryController {

    private final CustomerDietDictionaryService dictionaryService;

    public CustomerDietDictionaryController(CustomerDietDictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    /**
     * 返回客户维护、订单行内编辑和导入预览可使用的五类当前有效饮食对象。
     *
     * @return 可选饮食对象列表
     */
    @GetMapping
    @ApiOperation("查询客户饮食对象选项")
    @PreAuthorize("@el.check('customerProfile:list') or @el.check('customerProfile:add') or @el.check('customerProfile:edit') or @el.check('customerProfile:import') or @el.check('customerOrder:edit')")
    public List<CustomerDietOptionDto> listOptions() {
        return dictionaryService.listActiveOptions();
    }
}
