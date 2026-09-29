package me.zhengjie.modules.customer.profile.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;

import java.util.List;

/**
 * 使用 fastjson2 在客户饮食对象 JSON 数组与 Java 列表之间转换。
 */
public class CustomerDietItemsTypeHandler extends AbstractJsonTypeHandler<List<CustomerDietItemDto>> {

    /**
     * 解析数据库中的客户饮食对象数组。
     *
     * @param json 数据库 JSON 文本
     * @return 饮食对象列表；JSON null 返回 null
     */
    @Override
    protected List<CustomerDietItemDto> parse(String json) {
        Object value = JSON.parse(json);
        if (value == null) {
            return null;
        }
        if (!(value instanceof JSONArray)) {
            throw new IllegalArgumentException("customer diet items must be a JSON array");
        }
        return ((JSONArray) value).toJavaList(CustomerDietItemDto.class);
    }

    /**
     * 将客户饮食对象列表序列化为数据库 JSON 数组。
     *
     * @param items 饮食对象列表
     * @return JSON 数组文本
     */
    @Override
    protected String toJson(List<CustomerDietItemDto> items) {
        return JSON.toJSONString(items);
    }
}
