const ACTION_ROUTES = Object.freeze({
  OPEN_CREATE_CUSTOMER_WITH_ORDER_FORM: '/customer/profile',
  OPEN_CREATE_ORDER_FORM: '/customer/order'
})
const CONVERT_ACTION = 'CONVERT_TO_CREATE_CUSTOMER_WITH_ORDER'
const CONVERT_MESSAGE = '请将当前新增订单草稿转换为新增客户及首单草稿'

/** 将服务端固定动作转换为本地路由；任何 URL、path、route 和额外 query 都被忽略。 */
export function resolveFormDraftNavigation(action) {
  if (!action || action.enabled === false || !ACTION_ROUTES[action.type]) return null
  const payload = action.payload && typeof action.payload === 'object' ? action.payload : {}
  const draftId = safeId(payload.draftId, /^afd_[A-Za-z0-9_-]{16,64}$/)
  if (!draftId) return null
  const query = { draftId }
  const sourceSessionId = safeId(payload.sourceSessionId, /^[A-Za-z0-9_-]{1,128}$/)
  if (sourceSessionId) query.sourceSessionId = sourceSessionId
  return { path: ACTION_ROUTES[action.type], query }
}

/** 将固定草稿动作归一为导航或对话转换命令，拒绝模型提供的任意路由字段。 */
export function resolveFormDraftAction(action) {
  if (!action || action.enabled === false) return null
  if (action.type === CONVERT_ACTION) {
    const payload = action.payload && typeof action.payload === 'object' ? action.payload : {}
    const draftId = safeId(payload.draftId, /^afd_[A-Za-z0-9_-]{16,64}$/)
    if (!draftId) return null
    const sourceSessionId = safeId(payload.sourceSessionId, /^[A-Za-z0-9_-]{1,128}$/)
    return {
      kind: 'CONVERT',
      type: CONVERT_ACTION,
      draftId,
      sourceSessionId,
      formDraftId: draftId,
      message: CONVERT_MESSAGE
    }
  }
  const navigation = resolveFormDraftNavigation(action)
  return navigation ? { kind: 'NAVIGATE', type: action.type, ...navigation } : null
}

/** 只保留当前支持且参数完整的固定动作。 */
export function normalizeFormDraftActions(actions) {
  return (Array.isArray(actions) ? actions : []).filter(action => resolveFormDraftAction(action))
}

/** 校验受控标识并去除两端空格。 */
function safeId(value, pattern) {
  if (typeof value !== 'string') return ''
  const normalized = value.trim()
  return pattern.test(normalized) ? normalized : ''
}
