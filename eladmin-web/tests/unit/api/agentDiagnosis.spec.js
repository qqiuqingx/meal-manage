/* eslint-env jest */
jest.mock('@/utils/request', () => jest.fn())

import axios from '@/utils/request'
import {
  chatMealPlan,
  diagnoseMealPlan,
  queryChatSessions,
  createChatSession,
  getChatSession,
  archiveChatSession,
  updateChatSessionTitle
} from '@/api/agentDiagnosis'

describe('agentDiagnosis api', () => {
  beforeEach(() => {
    axios.mockClear()
  })

  test('posts diagnose request to existing endpoint', () => {
    diagnoseMealPlan({ customerId: 1001 })

    expect(axios).toHaveBeenCalledWith({
      url: '/api/agent/meal-plan/diagnose',
      method: 'post',
      data: { customerId: 1001 }
    })
  })

  test('posts chat request to chat endpoint', () => {
    chatMealPlan({ sessionId: null, clientMessageId: 'msg-1', message: '查 C10001 今天午餐' })

    expect(axios).toHaveBeenCalledWith({
      url: '/api/agent/meal-plan/chat',
      method: 'post',
      data: { sessionId: null, clientMessageId: 'msg-1', message: '查 C10001 今天午餐' }
    })
  })

  test('queries chat session list from session endpoint', () => {
    queryChatSessions({ keyword: 'C10001', archived: false })

    expect(axios).toHaveBeenCalledWith({
      url: '/api/agent/chat-sessions',
      method: 'get',
      params: { keyword: 'C10001', archived: false }
    })
  })

  test('creates and fetches chat sessions from session endpoints', () => {
    createChatSession({ title: '新会话' })
    getChatSession('session-1')

    expect(axios).toHaveBeenNthCalledWith(1, {
      url: '/api/agent/chat-sessions',
      method: 'post',
      data: { title: '新会话' }
    })
    expect(axios).toHaveBeenNthCalledWith(2, {
      url: '/api/agent/chat-sessions/session-1',
      method: 'get'
    })
  })

  test('archives and renames chat sessions through dedicated endpoints', () => {
    archiveChatSession('session-1', true)
    updateChatSessionTitle('session-1', { title: '午餐排查' })

    expect(axios).toHaveBeenNthCalledWith(1, {
      url: '/api/agent/chat-sessions/session-1/archive',
      method: 'put',
      params: { archived: true }
    })
    expect(axios).toHaveBeenNthCalledWith(2, {
      url: '/api/agent/chat-sessions/session-1/title',
      method: 'put',
      data: { title: '午餐排查' }
    })
  })
})
