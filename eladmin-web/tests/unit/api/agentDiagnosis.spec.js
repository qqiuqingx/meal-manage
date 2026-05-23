/* eslint-env jest */
jest.mock('@/utils/request', () => jest.fn())

const axios = require('@/utils/request')
const { chatMealPlan, diagnoseMealPlan } = require('@/api/agentDiagnosis')

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
    chatMealPlan({ sessionId: null, message: '查 C10001 今天午餐' })

    expect(axios).toHaveBeenCalledWith({
      url: '/api/agent/meal-plan/chat',
      method: 'post',
      data: { sessionId: null, message: '查 C10001 今天午餐' }
    })
  })
})
