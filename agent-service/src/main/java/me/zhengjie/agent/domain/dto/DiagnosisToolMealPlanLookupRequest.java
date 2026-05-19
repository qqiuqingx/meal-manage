package me.zhengjie.agent.domain.dto;

public class DiagnosisToolMealPlanLookupRequest {

    private String recordDate;
    private String mealType;

    public String getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(String recordDate) {
        this.recordDate = recordDate;
    }

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }
}
