package me.zhengjie.modules.customer.order.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import me.zhengjie.annotation.Log;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderQueryCriteria;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderSaveDto;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import me.zhengjie.utils.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 客户订单 REST 控制器
 */
@Api(tags = "客户订单管理")
@RestController
@RequestMapping("/api/customer/order")
public class CustomerOrderController {

    @Autowired
    private CustomerOrderService orderService;

    @ApiOperation("分页查询订单")
    @GetMapping
    @PreAuthorize("@el.check('customerOrder:list')")
    public ResponseEntity<PageResult<?>> query(CustomerOrderQueryCriteria criteria,
            @RequestParam(name = "page", defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(orderService.query(criteria, current, size));
    }

    @ApiOperation("根据客户ID分页查询订单")
    @GetMapping("/byCustomer/{customerId}")
    @PreAuthorize("@el.check('customerOrder:list')")
    public ResponseEntity<PageResult<?>> getOrdersByCustomer(@PathVariable Long customerId,
            @RequestParam(name = "page", defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(orderService.getOrdersByCustomerId(customerId, current, size));
    }

    @ApiOperation("查询可关联试餐订单")
    @GetMapping("/trial-options")
    @PreAuthorize("@el.check('customerOrder:list')")
    public ResponseEntity<List<?>> getTrialOrderOptions(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Long excludeId) {
        return ResponseEntity.ok(orderService.getTrialOrderOptions(keyword, excludeId));
    }

    @ApiOperation("获取订单详情")
    @GetMapping("/{id}")
    @PreAuthorize("@el.check('customerOrder:list')")
    public ResponseEntity<CustomerOrderDetailDto> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getDetail(id));
    }

    @ApiOperation("新增订单")
    @PostMapping
    @Log("新增订单")
    @PreAuthorize("@el.check('customerOrder:add')")
    public ResponseEntity<Void> create(@Validated @RequestBody CustomerOrderSaveDto dto) {
        orderService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @ApiOperation("校验订单冲突")
    @PostMapping("/validate")
    @PreAuthorize("@el.check('customerOrder:add')")
    public ResponseEntity<Void> validateOrder(@Validated @RequestBody CustomerOrderSaveDto dto) {
        orderService.validateOrderConflict(dto, dto.getId());
        return ResponseEntity.ok().build();
    }

    @ApiOperation("编辑订单")
    @PutMapping
    @Log("编辑订单")
    @PreAuthorize("@el.check('customerOrder:edit')")
    public ResponseEntity<Void> update(@Validated @RequestBody CustomerOrderSaveDto dto) {
        orderService.update(dto);
        return ResponseEntity.ok().build();
    }

    @ApiOperation("删除订单")
    @DeleteMapping
    @Log("删除订单")
    @PreAuthorize("@el.check('customerOrder:del')")
    public ResponseEntity<Void> delete(@RequestBody Set<Long> ids) {
        orderService.delete(ids);
        return ResponseEntity.ok().build();
    }
}
