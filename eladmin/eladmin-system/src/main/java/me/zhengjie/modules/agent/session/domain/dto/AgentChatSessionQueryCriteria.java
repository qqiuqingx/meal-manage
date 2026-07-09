package me.zhengjie.modules.agent.session.domain.dto;

import lombok.Data;

/**
 * 智能排查会话查询条件。
 */
@Data
public class AgentChatSessionQueryCriteria {

    private String keyword;

    private Long customerId;

    private String customerCode;

    private String recordDateStart;

    private String recordDateEnd;

    private String mealType;

    private Boolean archived;

    private Integer page = 0;

    private Integer size = 10;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getRecordDateStart() {
        return recordDateStart;
    }

    public void setRecordDateStart(String recordDateStart) {
        this.recordDateStart = recordDateStart;
    }

    public String getRecordDateEnd() {
        return recordDateEnd;
    }

    public void setRecordDateEnd(String recordDateEnd) {
        this.recordDateEnd = recordDateEnd;
    }

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
