/* eslint-env jest */
import AgentMessageActions from '@/views/agent/diagnosis/components/AgentMessageActions.vue'

function createContext(overrides) {
  const context = Object.assign({
    message: {
      status: 'ANSWERED',
      content: '查询完成',
      quickReplies: ['早餐', '午餐']
    },
    latest: true,
    loading: false,
    navigationTargets: []
  }, overrides || {})
  Object.keys(AgentMessageActions.computed).forEach(name => {
    Object.defineProperty(context, name, {
      get: () => AgentMessageActions.computed[name].call(context)
    })
  })
  return context
}

describe('AgentMessageActions', () => {
  test('only exposes quick replies on the latest assistant message', () => {
    const latest = createContext()
    const history = createContext({ latest: false })

    expect(latest.visibleQuickReplies).toEqual(['早餐', '午餐'])
    expect(history.visibleQuickReplies).toEqual([])
    expect(latest.canCopy).toBe(true)
  })

  test('exposes retry only when an error keeps the original question', () => {
    const retryable = createContext({
      message: { status: 'ERROR', content: '服务暂不可用', retryText: '查 C10001 今天' }
    })
    const historicalError = createContext({
      message: { status: 'ERROR', content: '服务暂不可用', retryText: '' }
    })

    expect(retryable.canRetry).toBe(true)
    expect(historicalError.canRetry).toBe(false)
    expect(retryable.canCopy).toBe(false)
  })

  test('hides quick replies while loading but keeps the retry action stateful', () => {
    const context = createContext({
      loading: true,
      message: { status: 'ERROR', content: '服务暂不可用', retryText: '查 C10001' }
    })

    expect(context.visibleQuickReplies).toEqual([])
    expect(context.canRetry).toBe(true)
    expect(context.hasActions).toBe(true)
  })
})
