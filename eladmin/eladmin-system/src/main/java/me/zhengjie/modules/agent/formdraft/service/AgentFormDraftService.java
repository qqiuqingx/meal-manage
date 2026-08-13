package me.zhengjie.modules.agent.formdraft.service;

import me.zhengjie.modules.agent.formdraft.domain.AgentFormDraft;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftClaimResult;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftSaveRequest;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftSaveResult;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftSummary;
import me.zhengjie.modules.agent.security.AgentAccessContext;

import java.util.Map;

/** Agent 表单草稿生命周期服务。 */
public interface AgentFormDraftService {

    /**
     * 使用已验签的 Agent 上下文创建或修订草稿。
     *
     * @param request 强类型保存请求
     * @param context 已验签且绑定当前会话的访问上下文
     * @return 不含完整 payload 的保存结果
     */
    AgentFormDraftSaveResult save(AgentFormDraftSaveRequest request, AgentAccessContext context);

    /**
     * 由所属客服领取可用草稿。
     *
     * @param draftId 草稿业务 ID
     * @param operatorId 当前登录客服 ID
     * @return 包含对应类型完整 payload 的领取结果
     */
    AgentFormDraftClaimResult claim(String draftId, Long operatorId);

    /**
     * 查询所属草稿的脱敏摘要。
     *
     * @param draftId 草稿业务 ID
     * @param operatorId 当前登录客服 ID
     * @return 不含手机号、地址和完整 payload 的摘要
     */
    AgentFormDraftSummary summary(String draftId, Long operatorId);

    /**
     * 读取当前会话可修订草稿的可信模型上下文。
     *
     * @param draftId 固定动作指定的草稿 ID；为空时取会话最近活动草稿
     * @param sessionId 当前会话 ID
     * @param operatorId 当前客服 ID
     * @return 包含类型化 payload 和乐观锁版本的上下文；无可用草稿时为空 Map
     */
    Map<String, Object> conversationContext(String draftId, String sessionId, Long operatorId);

    /**
     * 在正式新增事务中锁定并校验草稿。
     *
     * @param draftId 草稿业务 ID
     * @param operatorId 当前登录客服 ID
     * @param expectedRevision 页面领取的草稿版本
     * @param expectedType 正式新增入口要求的草稿类型
     * @return 已加数据库行锁的草稿实体
     */
    AgentFormDraft lockForSubmission(String draftId, Long operatorId, Integer expectedRevision, String expectedType);

    /**
     * 将已锁定草稿标记为一次性提交成功。
     *
     * @param lockedDraft lockForSubmission 返回的实体
     * @param targetBusinessId 新建客户或订单 ID
     */
    void markSubmitted(AgentFormDraft lockedDraft, Long targetBusinessId);

    /**
     * 过期并清空一批草稿的敏感 payload。
     *
     * @param batchSize 单批最大数量
     * @return 实际清理数量
     */
    int expireBatch(int batchSize);
}
