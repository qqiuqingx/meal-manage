package me.zhengjie.agent.presentation;

/** 未登记卡片的受控展示规划端口。 */
public interface PresentationPlanner {
    /**
     * 根据无值结构摘要生成展示候选。
     *
     * @param cardType 未登记的卡片类型
     * @param schema 只含结构和完整性标记的摘要
     * @return 未经后端二次校验的模型候选
     */
    PresentationSuggestion plan(String cardType, CardSchemaInspector.SchemaSummary schema);
}
