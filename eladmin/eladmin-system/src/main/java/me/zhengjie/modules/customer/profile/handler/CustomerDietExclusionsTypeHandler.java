package me.zhengjie.modules.customer.profile.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import java.util.List;

/** 使用 fastjson2 保存人工排除的禁忌对象稳定键数组。 */
public class CustomerDietExclusionsTypeHandler extends AbstractJsonTypeHandler<List<String>> {

    /** 读取稳定键 JSON 数组；数据库 JSON null 返回 null。 */
    @Override
    protected List<String> parse(String json) {
        Object value = JSON.parse(json);
        if (value == null) {
            return null;
        }
        if (!(value instanceof JSONArray)) {
            throw new IllegalArgumentException("customer diet exclusions must be a JSON array");
        }
        return ((JSONArray) value).toJavaList(String.class);
    }

    /** 将去重后的 type:id 稳定键序列化为 JSON 数组。 */
    @Override
    protected String toJson(List<String> keys) {
        return JSON.toJSONString(keys);
    }
}
