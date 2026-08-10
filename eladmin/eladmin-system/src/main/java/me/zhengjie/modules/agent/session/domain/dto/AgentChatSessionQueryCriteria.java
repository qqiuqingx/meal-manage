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

    /** 默认只查询进行中的会话；归档页显式传 true。 */
    private Boolean archived = false;

    private Integer page = 0;

    /** 会话侧栏固定使用 20 条一页，服务端仍会进一步限制最大值。 */
    private Integer size = 20;

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
