package me.zhengjie.modules.meal.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 餐次枚举
 * @author qqx
 * @date 2026-03-14
 */
@Getter
@AllArgsConstructor
public enum MealTypeEnum {

    LUNCH("LUNCH", "午餐"),
    DINNER("DINNER", "晚餐");

    private final String code;
    private final String desc;

    @JsonValue
    public String getDesc() {
        return desc;
    }

    public static MealTypeEnum fromCode(String code) {
        for (MealTypeEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
