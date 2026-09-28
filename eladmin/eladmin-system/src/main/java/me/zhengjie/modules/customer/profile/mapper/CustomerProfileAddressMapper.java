package me.zhengjie.modules.customer.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 客户地址 Mapper 接口
 */
@Mapper
public interface CustomerProfileAddressMapper extends BaseMapper<CustomerProfileAddress> {

    /**
     * 锁定客户已有的指定类型地址，供订单行内旧值比较。
     *
     * @param customerId 客户主键
     * @param addressType 地址槽位代码
     * @return 地址记录；槽位不存在时返回 null
     */
    @Select("SELECT id, customer_id AS customerId, address_type AS addressType, " +
            "address_detail AS addressDetail, update_time AS updateTime " +
            "FROM customer_profile_address WHERE customer_id = #{customerId} " +
            "AND address_type = #{addressType} FOR UPDATE")
    CustomerProfileAddress selectForInlineUpdate(@Param("customerId") Long customerId,
                                                 @Param("addressType") String addressType);

    /**
     * 更新已锁定地址的详细地址和时间。
     *
     * @param id 地址主键
     * @param addressDetail 新的详细地址
     * @param updateTime 本次修改时间
     * @return 更新行数
     */
    @Update("UPDATE customer_profile_address SET " +
            "address_detail = #{addressDetail, typeHandler=org.apache.ibatis.type.StringTypeHandler}, " +
            "update_time = #{updateTime, jdbcType=TIMESTAMP} WHERE id = #{id}")
    int updateAddressDetailInline(@Param("id") Long id,
                                  @Param("addressDetail") String addressDetail,
                                  @Param("updateTime") LocalDateTime updateTime);
}
