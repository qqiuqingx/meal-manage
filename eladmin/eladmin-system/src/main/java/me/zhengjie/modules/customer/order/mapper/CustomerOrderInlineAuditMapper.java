package me.zhengjie.modules.customer.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.customer.order.domain.CustomerOrderInlineAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单行内修改审计 Mapper。
 */
@Mapper
public interface CustomerOrderInlineAuditMapper extends BaseMapper<CustomerOrderInlineAudit> {
}
