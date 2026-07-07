package me.zhengjie.modules.agent.rest;

import me.zhengjie.modules.agent.domain.AgentActionAudit;
import me.zhengjie.modules.agent.domain.dto.AgentActionAuditQueryCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentActionConfirmRequest;
import me.zhengjie.modules.agent.domain.dto.AgentActionConfirmResponse;
import me.zhengjie.modules.agent.service.AgentActionConfirmService;
import me.zhengjie.utils.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentActionDraftControllerTest {

    @Test
    void shouldDelegateActionAuditQuery() {
        AgentActionConfirmService service = new AgentActionConfirmService() {
            @Override
            public AgentActionConfirmResponse confirm(AgentActionConfirmRequest request) {
                return new AgentActionConfirmResponse();
            }

            @Override
            public PageResult<AgentActionAudit> queryAudits(AgentActionAuditQueryCriteria criteria) {
                AgentActionAudit audit = new AgentActionAudit();
                audit.setSessionId(criteria.getSessionId());
                audit.setRequestId(criteria.getRequestId());
                audit.setStatus("CONFIRMED");
                return new PageResult<>(Collections.singletonList(audit), 1);
            }
        };
        AgentActionDraftController controller = new AgentActionDraftController(service);

        AgentActionAuditQueryCriteria criteria = new AgentActionAuditQueryCriteria();
        criteria.setSessionId("session-1");
        criteria.setRequestId("req-1");
        ResponseEntity<PageResult<AgentActionAudit>> response = controller.queryAudits(criteria);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("session-1", response.getBody().getContent().get(0).getSessionId());
        assertEquals("req-1", response.getBody().getContent().get(0).getRequestId());
        assertEquals("CONFIRMED", response.getBody().getContent().get(0).getStatus());
    }
}
