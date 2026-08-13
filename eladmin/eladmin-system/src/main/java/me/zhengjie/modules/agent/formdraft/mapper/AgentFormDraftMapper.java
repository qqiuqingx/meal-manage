package me.zhengjie.modules.agent.formdraft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.agent.formdraft.domain.AgentFormDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.sql.Timestamp;
import java.util.List;

/** Agent 表单草稿持久化 Mapper。 */
@Mapper
public interface AgentFormDraftMapper extends BaseMapper<AgentFormDraft> {

    /** 按创建幂等键查询既有草稿。 */
    @Select("SELECT * FROM agent_form_draft WHERE owner_user_id=#{ownerUserId} AND source_session_id=#{sessionId} "
        + "AND source_client_message_id=#{clientMessageId} AND is_del=0 LIMIT 1")
    AgentFormDraft selectByCreateIdempotencyKey(@Param("ownerUserId") Long ownerUserId,
                                                 @Param("sessionId") String sessionId,
                                                 @Param("clientMessageId") String clientMessageId);

    /** 按草稿 ID 和所有者查询，未知和越权使用相同空结果。 */
    @Select("SELECT * FROM agent_form_draft WHERE draft_id=#{draftId} AND owner_user_id=#{ownerUserId} AND is_del=0 LIMIT 1")
    AgentFormDraft selectOwned(@Param("draftId") String draftId, @Param("ownerUserId") Long ownerUserId);

    /** 查询当前客服会话最近一个仍允许修订的草稿。 */
    @Select("SELECT * FROM agent_form_draft WHERE owner_user_id=#{ownerUserId} AND source_session_id=#{sessionId} "
        + "AND status IN ('EDITABLE','READY') AND is_del=0 ORDER BY update_time DESC,id DESC LIMIT 1")
    AgentFormDraft selectLatestRevisable(@Param("ownerUserId") Long ownerUserId, @Param("sessionId") String sessionId);

    /** 在正式业务事务中锁定所属草稿，防止重复提交。 */
    @Select("SELECT * FROM agent_form_draft WHERE draft_id=#{draftId} AND owner_user_id=#{ownerUserId} AND is_del=0 LIMIT 1 FOR UPDATE")
    AgentFormDraft selectOwnedForUpdate(@Param("draftId") String draftId, @Param("ownerUserId") Long ownerUserId);

    /** 使用期望版本更新可修订草稿，避免并发覆盖。 */
    @Update("UPDATE agent_form_draft SET payload=#{draft.payload}, recognized_fields=#{draft.recognizedFields}, "
        + "draft_type=#{draft.draftType}, target_permission=#{draft.targetPermission}, "
        + "missing_fields=#{draft.missingFields}, warnings=#{draft.warnings}, status=#{draft.status}, "
        + "revision=revision+1, expires_at=#{draft.expiresAt}, converted_from=#{draft.convertedFrom}, "
        + "update_by=#{draft.updateBy}, update_time=#{draft.updateTime} WHERE draft_id=#{draft.draftId} "
        + "AND owner_user_id=#{draft.ownerUserId} AND revision=#{expectedRevision} "
        + "AND status IN ('EDITABLE','READY') AND is_del=0")
    int updateRevision(@Param("draft") AgentFormDraft draft, @Param("expectedRevision") Integer expectedRevision);

    /** 查询待过期的活动草稿，限制批次避免长事务。 */
    @Select("SELECT * FROM agent_form_draft WHERE status IN ('EDITABLE','READY','CLAIMED') AND expires_at<=#{now} "
        + "AND is_del=0 ORDER BY id LIMIT #{limit}")
    List<AgentFormDraft> selectExpiredBatch(@Param("now") Timestamp now, @Param("limit") int limit);

    /** 仅当记录仍为查询到的活动状态时执行过期，避免覆盖并发提交。 */
    @Update("UPDATE agent_form_draft SET status='EXPIRED', payload=NULL, update_time=#{updateTime} "
        + "WHERE id=#{id} AND status=#{expectedStatus} AND expires_at<=#{now} AND is_del=0")
    int expireIfActive(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                       @Param("now") Timestamp now, @Param("updateTime") Timestamp updateTime);
}
