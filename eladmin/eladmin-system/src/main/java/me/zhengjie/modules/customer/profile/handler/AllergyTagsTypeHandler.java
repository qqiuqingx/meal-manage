package me.zhengjie.modules.customer.profile.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

import java.util.List;

/**
 * 客户过敏标签的 JSON 数组映射，支持字符串包裹的数组文本。
 */
public class AllergyTagsTypeHandler extends AbstractJsonTypeHandler<List<String>> {

    /**
     * 将数据库中的 JSON 数组解析为过敏标签列表。
     *
     * @param json 数据库读取的 JSON 文本
     * @return 过敏标签列表；JSON null 返回 null
     */
    @Override
    protected List<String> parse(String json) {
        Object value = JSON.parse(json);
        if (value instanceof String) {
            value = JSON.parse((String) value);
        }
        if (value == null) {
            return null;
        }
        if (!(value instanceof JSONArray)) {
            throw new IllegalArgumentException("allergy_tags must be a JSON array");
        }
        return ((JSONArray) value).toJavaList(String.class);
    }

    /**
     * 将过敏标签列表序列化为数据库 JSON 数组。
     *
     * @param tags 过敏标签列表
     * @return JSON 数组文本
     */
    @Override
    protected String toJson(List<String> tags) {
        return JSON.toJSONString(tags);
    }
}
