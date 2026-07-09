import axios from '@/utils/request'

export function diagnoseMealPlan(data) {
  return axios({
    url: '/api/agent/meal-plan/diagnose',
    method: 'post',
    data
  })
}

export function chatMealPlan(data) {
  return axios({
    url: '/api/agent/meal-plan/chat',
    method: 'post',
    data
  })
}

export function queryChatSessions(params) {
  return axios({
    url: '/api/agent/chat-sessions',
    method: 'get',
    params
  })
}

export function createChatSession(data) {
  return axios({
    url: '/api/agent/chat-sessions',
    method: 'post',
    data
  })
}

export function getChatSession(sessionId) {
  return axios({
    url: `/api/agent/chat-sessions/${sessionId}`,
    method: 'get'
  })
}

export function archiveChatSession(sessionId, archived = true) {
  return axios({
    url: `/api/agent/chat-sessions/${sessionId}/archive`,
    method: 'put',
    params: { archived }
  })
}

export function updateChatSessionTitle(sessionId, data) {
  return axios({
    url: `/api/agent/chat-sessions/${sessionId}/title`,
    method: 'put',
    data
  })
}

export function confirmActionDraft(data) {
  return axios({
    url: '/api/agent/action-drafts/confirm',
    method: 'post',
    data
  })
}

export function queryActionAudits(params) {
  return axios({
    url: '/api/agent/action-drafts/audits',
    method: 'get',
    params
  })
}

export function submitDiagnosisFeedback(data) {
  return axios({
    url: '/api/agent/feedback',
    method: 'post',
    data
  })
}

export function queryDiagnosisFeedback(params) {
  return axios({
    url: '/api/agent/feedback',
    method: 'get',
    params
  })
}

export function queryDiagnosisFeedbackStats(params) {
  return axios({
    url: '/api/agent/feedback/stats',
    method: 'get',
    params
  })
}

export function queryAgentOperationStats(params) {
  return axios({
    url: '/api/agent/operation/stats',
    method: 'get',
    params
  })
}

export function queryAgentRuleGaps(params) {
  return axios({
    url: '/api/agent/rule-gaps',
    method: 'get',
    params
  })
}

export function updateAgentRuleGapStatus(id, data) {
  return axios({
    url: `/api/agent/rule-gaps/${id}/status`,
    method: 'put',
    data
  })
}

export default {
  diagnoseMealPlan,
  chatMealPlan,
  queryChatSessions,
  createChatSession,
  getChatSession,
  archiveChatSession,
  updateChatSessionTitle,
  confirmActionDraft,
  queryActionAudits,
  submitDiagnosisFeedback,
  queryDiagnosisFeedback,
  queryDiagnosisFeedbackStats,
  queryAgentOperationStats,
  queryAgentRuleGaps,
  updateAgentRuleGapStatus
}
