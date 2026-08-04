package me.zhengjie.agent.tool.input;

import java.util.ArrayList;
import java.util.List;

/** 查询公共排期菜单的强类型输入。 */
public class ListScheduledDishesInput {
    private String recordDate;
    private List<MealType> mealTypes = new ArrayList<>();

    public String getRecordDate() { return recordDate; }
    public void setRecordDate(String value) { recordDate = value; }
    public List<MealType> getMealTypes() { return mealTypes; }
    public void setMealTypes(List<MealType> value) { mealTypes = value == null ? new ArrayList<>() : value; }
}
