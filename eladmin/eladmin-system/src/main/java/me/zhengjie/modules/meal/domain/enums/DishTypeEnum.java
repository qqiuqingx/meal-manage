package me.zhengjie.modules.meal.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 菜品类型枚举
 * @author qqx
 * @date 2026-03-14
 */
@Getter
@AllArgsConstructor
public enum DishTypeEnum {

    MAIN("MAIN", "主菜"),
    SIDE("SIDE", "副菜"),
    SOUP("SOUP", "汤"),
    VEGETABLE("VEGETABLE", "素菜"),
    RICE("RICE", "米饭"),
    RICE_TYPE("RICE_TYPE", "米饭类型");

    private final String code;
    private final String desc;

    @JsonValue
    public String getDesc() {
        return desc;
    }

    public static DishTypeEnum fromCode(String code) {
        for (DishTypeEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
