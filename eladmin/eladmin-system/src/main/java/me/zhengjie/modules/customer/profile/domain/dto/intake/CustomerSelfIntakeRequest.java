package me.zhengjie.modules.customer.profile.domain.dto.intake;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 客户自助资料登记测试请求。
 *
 * 当前请求仅用于验证 H5 到后端的接收链路，不代表正式客户建档数据。
 *
 * @author qqx
 * @date 2026-09-21
 */
@Data
public class CustomerSelfIntakeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 客户姓名，去除首尾空格后长度为 1～50 个字符。
     */
    @NotBlank(message = "客户姓名不能为空")
    @Size(max = 50, message = "客户姓名长度不能超过50个字符")
    private String customerName;

    /**
     * 客户手机号，必须符合大陆手机号格式。
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 客户地址，去除首尾空格后长度为 5～500 个字符。
     */
    @NotBlank(message = "地址不能为空")
    @Size(min = 5, max = 500, message = "地址长度必须为5～500个字符")
    private String address;

    /**
     * 客户备注，去除首尾空格后最多 1000 个字符。
     */
    @Size(max = 1000, message = "备注长度不能超过1000个字符")
    private String remark;

    /**
     * 设置客户姓名并去除首尾空格，使参数校验基于规范化后的值。
     *
     * @param customerName 客户姓名
     */
    public void setCustomerName(String customerName) {
        this.customerName = trim(customerName);
    }

    /**
     * 设置手机号并去除首尾空格，使参数校验基于规范化后的值。
     *
     * @param phone 客户手机号
     */
    public void setPhone(String phone) {
        this.phone = trim(phone);
    }

    /**
     * 设置客户地址并去除首尾空格，使参数校验基于规范化后的值。
     *
     * @param address 客户地址
     */
    public void setAddress(String address) {
        this.address = trim(address);
    }

    /**
     * 设置客户备注并去除首尾空格，使日志中的字段保持规范格式。
     *
     * @param remark 客户备注
     */
    public void setRemark(String remark) {
        this.remark = trim(remark);
    }

    /**
     * 去除文本首尾空格，保留 null 以便必填字段由 Bean Validation 统一校验。
     *
     * @param value 待处理文本
     * @return 去除首尾空格后的文本，输入为 null 时返回 null
     */
    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
