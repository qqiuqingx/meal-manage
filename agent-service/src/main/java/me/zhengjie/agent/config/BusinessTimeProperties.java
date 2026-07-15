package me.zhengjie.agent.config;

import me.zhengjie.agent.analysis.domain.BusinessTemporalExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/** Agent 业务时间配置，所有相对时间都按该时区解析。 */
@ConfigurationProperties(prefix = "agent.business-time")
public class BusinessTimeProperties {
    private String zoneId = "Asia/Shanghai";
    private BusinessTemporalExpression defaultDailyExpression = BusinessTemporalExpression.CURRENT_DAY;

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public BusinessTemporalExpression getDefaultDailyExpression() { return defaultDailyExpression; }
    public void setDefaultDailyExpression(BusinessTemporalExpression defaultDailyExpression) { this.defaultDailyExpression = defaultDailyExpression; }

    /** 返回校验后的业务时区；非法配置会在启动阶段显式失败。 */
    public ZoneId toZoneId() { return ZoneId.of(zoneId); }
}
