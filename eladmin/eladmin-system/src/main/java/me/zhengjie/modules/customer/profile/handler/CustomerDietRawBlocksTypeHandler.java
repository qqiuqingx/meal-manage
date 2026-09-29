package me.zhengjie.modules.customer.profile.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

import java.util.List;

/**
 * 使用 fastjson2 映射导入来源的原文块 JSON 数组。
 */
public class CustomerDietRawBlocksTypeHandler extends AbstractJsonTypeHandler<List<String>> {

    /**
     * 解析数据库中的原文块数组。
     *
     * @param json 数据库 JSON 文本
     * @return 原文块列表；JSON null 返回 null
     */
    @Override
    protected List<String> parse(String json) {
        Object value = JSON.parse(json);
        if (value == null) {
            return null;
        }
        if (!(value instanceof JSONArray)) {
            throw new IllegalArgumentException("customer diet raw blocks must be a JSON array");
        }
        return ((JSONArray) value).toJavaList(String.class);
    }

    /**
     * 将原文块列表序列化为数据库 JSON 数组。
     *
     * @param blocks 按来源顺序排列的原文块
     * @return JSON 数组文本
     */
    @Override
    protected String toJson(List<String> blocks) {
        return JSON.toJSONString(blocks);
    }
}
