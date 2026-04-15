package me.zhengjie.modules.customer.profile.handler;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;

import java.util.Collections;
import java.util.List;

/**
 * JSON type handler for excluded_dates.
 */
public class ExcludedDateListTypeHandler extends AbstractJsonTypeHandler<List<ExcludedDateDto>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<ExcludedDateDto>> TYPE_REFERENCE =
        new TypeReference<List<ExcludedDateDto>>() {};

    @Override
    protected List<ExcludedDateDto> parse(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json, TYPE_REFERENCE);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse excluded_dates JSON", e);
        }
    }

    @Override
    protected String toJson(List<ExcludedDateDto> obj) {
        if (obj == null || obj.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize excluded_dates JSON", e);
        }
    }
}
