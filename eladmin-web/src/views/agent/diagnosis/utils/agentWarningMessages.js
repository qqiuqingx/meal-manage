/**
 * 解析 Agent 返回的工具前缀告警，保留原文供技术详情使用，只把最后一段作为稳定错误码。
 *
 * @param {*} value Agent 返回的原始告警
 * @return {{raw: string, source: string, code: string}} 脱敏后的告警结构
 */
export function parseAgentWarning(value) {
  const raw = value === null || value === undefined ? '' : String(value).trim()
  if (!raw) {
    return { raw: '', source: '', code: '' }
  }
  const parts = raw.split(':')
  const last = parts.pop()
  const code = String(last || '').trim().toUpperCase() || raw.toUpperCase()
  return {
    raw,
    source: parts.join(':').trim(),
    code
  }
}

/** 将告警数组转换为稳定码，过滤空值并保持首次出现顺序。 */
function parsedWarnings(warnings) {
  const values = Array.isArray(warnings) ? warnings : []
  const seen = {}
  return values
    .map(parseAgentWarning)
    .filter(item => item.code)
    .filter(item => {
      if (seen[item.code]) return false
      seen[item.code] = true
      return true
    })
}

/** 判断告警是否意味着业务结果不完整；纯展示降级告警不属于此类。 */
export function hasBusinessIntegrityWarning(warnings) {
  return parsedWarnings(warnings).some(item => {
    return /TRUNCAT|INCOMPLETE|PARTIAL|BUDGET_EXCEEDED|PERMISSION_DENIED|TOOL_OUTPUT_INVALID|TOOL_EXECUTION_FAILED|QUERY_.*FAILED|IMPLAUSIBLE/.test(item.code)
  })
}

/**
 * 将告警和 partial 标记映射为一条业务化提示，未知错误码不直接暴露给客服。
 *
 * @param {Array} warnings Agent 返回的告警列表
 * @param {Boolean} partial 消息是否只有部分结果
 * @return {String} 页面顶部展示的单条告警摘要
 */
export function businessWarningMessage(warnings, partial) {
  const values = parsedWarnings(warnings)
  const hasCode = pattern => values.some(item => pattern.test(item.code))

  if (hasCode(/PERMISSION_DENIED/)) {
    return '当前账号缺少该类业务数据的查询权限，系统未返回对象是否存在或相关明细。'
  }
  if (hasCode(/MENU_RESULT_IMPLAUSIBLE/)) {
    return '查询结果仅包含米饭类型菜品，不能确认这是完整公共菜单；请核对排期配置或改查客户实际排餐。'
  }
  if (hasCode(/INCOMPLETE|IMPLAUSIBLE/)) {
    return '查询结果可能不完整，请结合业务配置和当前明细继续核对。'
  }
  if (hasCode(/BUDGET_EXCEEDED/)) {
    return '本轮查询达到安全调用上限，已返回可用部分结果。'
  }
  if (hasCode(/TOOL_OUTPUT_INVALID|TOOL_EXECUTION_FAILED|QUERY_.*FAILED/)) {
    return '部分业务查询暂不可用，当前结果不构成完整结论。'
  }
  if (hasCode(/TRUNCAT/)) {
    return '结果已按安全上限截断，请缩小查询范围后重试。'
  }
  if (partial) {
    return '结果可能不完整，请缩小查询范围后重试。'
  }
  if (hasCode(/^PRESENTATION_/)) {
    return '展示格式暂不可用，业务结果仍可查看。'
  }
  if (values.length) {
    return '业务查询存在告警，请结合当前结果继续核对。'
  }
  return ''
}

export default {
  businessWarningMessage,
  hasBusinessIntegrityWarning,
  parseAgentWarning
}
