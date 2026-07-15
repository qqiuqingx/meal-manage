package me.zhengjie.agent.analysis.domain;

/**
 * 业务问题中的受控时间意图。具体日期仅允许用于显式日期或显式范围。
 */
public class BusinessTemporalIntent {
    private BusinessTemporalExpression expression = BusinessTemporalExpression.UNSPECIFIED;
    private String explicitDate;
    private String explicitStartDate;
    private String explicitEndDate;

    public BusinessTemporalExpression getExpression() { return expression; }
    public void setExpression(BusinessTemporalExpression expression) { this.expression = expression; }
    public String getExplicitDate() { return explicitDate; }
    public void setExplicitDate(String explicitDate) { this.explicitDate = explicitDate; }
    public String getExplicitStartDate() { return explicitStartDate; }
    public void setExplicitStartDate(String explicitStartDate) { this.explicitStartDate = explicitStartDate; }
    public String getExplicitEndDate() { return explicitEndDate; }
    public void setExplicitEndDate(String explicitEndDate) { this.explicitEndDate = explicitEndDate; }
}
