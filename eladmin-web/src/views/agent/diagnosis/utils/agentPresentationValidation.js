import {
  isSafeLabel,
  isSafePath,
  isSupportedFormat,
  readPath
} from './agentPresentationFormatters'

/** v1 展示描述允许的视图白名单。 */
export const PRESENTATION_VIEWS = ['TEXT', 'TABLE', 'BAR', 'LINE', 'PIE']
export const VIEW_WHITELIST = PRESENTATION_VIEWS
export const MAX_TITLE_LENGTH = 100
export const MAX_LABEL_LENGTH = 30
export const MAX_TABLE_COLUMNS = 20
export const MAX_CHART_METRICS = 4
export const MAX_SECTIONS = 8

const CHART_VIEWS = ['BAR', 'LINE', 'PIE']
const SENSITIVE_FIELD_PATTERN = /(customerid|orderid|dishid|packageid|verificationid|mealplanid|address|phone|mobile|amount|price|money|payment|token|secret|password|permission|authorization|sql|jdbc|url)/i
const ALLOWED_DECISION_SOURCES = ['SYSTEM', 'LLM']
const SAFE_CARD_TYPE_PATTERN = /^[A-Za-z][A-Za-z0-9_]*$/

/** 判断值是否为普通对象，避免对数组和宿主对象做不安全遍历。 */
function isPlainObject(value) {
  return value && Object.prototype.toString.call(value) === '[object Object]'
}

/** 将视图枚举规范化为大写，非法值返回空字符串。 */
function normalizeView(value) {
  const view = String(value || '').toUpperCase()
  return PRESENTATION_VIEWS.indexOf(view) >= 0 ? view : ''
}

/** 判断字段路径是否为安全业务字段，禁止内部 ID 和敏感字段。 */
export function isSafePresentationField(path) {
  if (!isSafePath(path)) {
    return false
  }
  return path.split('.').every(segment => {
    const name = segment.replace(/\[\]$/, '')
    return !SENSITIVE_FIELD_PATTERN.test(name)
  })
}

/**
 * 在卡片 data 内解析安全路径；仅为旧快照兼容缺失的 data. 前缀，不改变真实值。
 *
 * @param {Object|Array} data 卡片已有的安全数据
 * @param {String} path 展示描述中的数据路径
 * @return {{path: String, value: *}|null} 实际可读取路径和值
 */
function resolveDataPath(data, path) {
  if (!isSafePath(path)) {
    return null
  }
  const value = readPath(data, path)
  if (value !== undefined) {
    return { path, value }
  }
  if (path.indexOf('data.') === 0) {
    const fallbackPath = path.slice(5)
    if (isSafePath(fallbackPath)) {
      const fallbackValue = readPath(data, fallbackPath)
      if (fallbackValue !== undefined) {
        return { path: fallbackPath, value: fallbackValue }
      }
    }
  }
  return null
}

/** 判断卡片或其 data 信封是否明确标记为截断。 */
function hasTruncatedFlag(card) {
  const data = card && card.data
  return !!(card && card.truncated === true) ||
    !!(data && data.truncated === true) ||
    !!(data && data.data && data.data.truncated === true)
}

/** 判断告警是否意味着数据不完整，展示降级告警本身不影响业务完整性。 */
export function hasIntegrityWarning(warnings) {
  const values = Array.isArray(warnings) ? warnings : []
  return values.some(value => {
    const warning = String(value || '').toUpperCase()
    return /TRUNCAT|INCOMPLETE|PARTIAL|BUDGET_EXCEEDED|PERMISSION_DENIED|TOOL_OUTPUT_INVALID|TOOL_EXECUTION_FAILED|QUERY_.*FAILED|IMPLAUSIBLE/.test(warning)
  })
}

/** 判断当前卡片是否必须隐藏图表，表格和原始告警仍然保留。 */
export function shouldHideChart(card, warnings, partial) {
  return partial === true || hasTruncatedFlag(card) || hasIntegrityWarning(warnings)
}

/** 过滤并限制单个字段定义，非法字段只影响所在视图。 */
function normalizeFields(fields) {
  if (!Array.isArray(fields)) {
    return []
  }
  return fields
    .filter(field => field && isSafePresentationField(field.field) &&
      isSafeLabel(field.label, MAX_LABEL_LENGTH) && isSupportedFormat(field.format))
    .slice(0, MAX_TABLE_COLUMNS)
    .map(field => ({
      field: field.field,
      label: field.label,
      format: String(field.format).toUpperCase()
    }))
}

/** 校验摘要描述并保留有效字段。 */
function normalizeSummary(summary, data) {
  const resolved = isPlainObject(summary) ? resolveDataPath(data, summary.dataPath) : null
  if (!resolved) {
    return null
  }
  const fields = normalizeFields(summary.fields)
  return fields.length ? { dataPath: resolved.path, fields } : null
}

/** 校验分区描述；分区路径安全即可保留空分区，避免把业务空结果误判为格式错误。 */
function normalizeSections(sections) {
  if (!Array.isArray(sections)) {
    return []
  }
  return sections
    .slice(0, MAX_SECTIONS)
    .filter(section => section && isSafeLabel(section.id, MAX_TITLE_LENGTH) &&
      isSafeLabel(section.title, MAX_TITLE_LENGTH) && isSafePath(section.dataPath))
    .map(section => ({
      id: section.id,
      title: section.title,
      dataPath: section.dataPath,
      columns: normalizeFields(section.columns)
    }))
    .filter(section => section.columns.length > 0)
}

/** 校验表格描述，并支持 Phase 05 的单表与一层 sections 结构。 */
function normalizeTable(table, data) {
  const resolved = isPlainObject(table) ? resolveDataPath(data, table.dataPath) : null
  if (!resolved) {
    return null
  }
  const columns = normalizeFields(table.columns)
  const sections = normalizeSections(table.sections)
  if (!columns.length && !sections.length) {
    return null
  }
  return {
    dataPath: resolved.path,
    columns,
    sections
  }
}

/** 校验图表字段和指标数量，禁止服务端直接注入 ECharts option。 */
function normalizeChart(chart, data) {
  if (!isPlainObject(chart)) {
    return null
  }
  const type = normalizeView(chart.type)
  const metricFields = Array.isArray(chart.metricFields) ? chart.metricFields : []
  const metricLabels = Array.isArray(chart.metricLabels) ? chart.metricLabels : []
  const resolved = resolveDataPath(data, chart.dataPath)
  if (CHART_VIEWS.indexOf(type) < 0 || !resolved || !Array.isArray(resolved.value) ||
    !isSafePresentationField(chart.dimensionField) ||
    metricFields.length < 1 || metricFields.length > MAX_CHART_METRICS ||
    metricLabels.length !== metricFields.length || !isSafeLabel(chart.dimensionLabel, MAX_LABEL_LENGTH)) {
    return null
  }
  const normalizedMetrics = metricFields.map((field, index) => ({
    field,
    label: metricLabels[index]
  }))
  if (normalizedMetrics.some(metric => !isSafePresentationField(metric.field) ||
    !isSafeLabel(metric.label, MAX_LABEL_LENGTH))) {
    return null
  }
  return {
    type,
    dataPath: resolved.path,
    dimensionField: chart.dimensionField,
    metricFields: normalizedMetrics.map(metric => metric.field),
    dimensionLabel: chart.dimensionLabel,
    metricLabels: normalizedMetrics.map(metric => metric.label)
  }
}

/** 返回视图级校验结果，非法图表不会牵连同一描述中的表格或摘要。 */
function validateViews(presentation, data, options) {
  const availableViews = Array.isArray(presentation.availableViews) ? presentation.availableViews : []
  const validViews = []
  const invalidViews = []
  const normalized = {}
  const summary = normalizeSummary(presentation.summary, data)
  const table = normalizeTable(presentation.table, data)
  const chart = normalizeChart(presentation.chart, data)
  const chartHidden = shouldHideChart(options.card, options.warnings, options.partial)

  availableViews.forEach(value => {
    const view = normalizeView(value)
    if (!view || validViews.indexOf(view) >= 0) {
      invalidViews.push(value)
      return
    }
    if (view === 'TEXT' && summary) {
      validViews.push(view)
      normalized.summary = summary
      return
    }
    if (view === 'TABLE' && table) {
      validViews.push(view)
      normalized.table = table
      return
    }
    if (CHART_VIEWS.indexOf(view) >= 0 && chart && chart.type === view && !chartHidden) {
      validViews.push(view)
      normalized.chart = chart
      return
    }
    invalidViews.push(value)
  })

  return { validViews, invalidViews, normalized }
}

/** 校验 v1 展示描述并返回供固定组件使用的安全副本。 */
export function validatePresentation(presentation, card, options) {
  const result = {
    valid: false,
    allViewsInvalid: true,
    presentation: null,
    validViews: [],
    invalidViews: [],
    errors: []
  }
  if (!isPlainObject(presentation)) {
    result.errors.push('PRESENTATION_INVALID')
    return result
  }

  const data = card && card.data ? card.data : {}
  const sourceToolCallId = typeof presentation.sourceToolCallId === 'string'
    ? presentation.sourceToolCallId : ''
  const cardSourceToolCallId = card && typeof card.sourceToolCallId === 'string'
    ? card.sourceToolCallId : ''
  const cardType = card && (card.type || card.cardType)
  const normalizedCardType = typeof cardType === 'string' ? cardType : ''
  const schemaValid = presentation.schemaVersion === 'v1'
  const sourceValid = !!sourceToolCallId && !!cardSourceToolCallId && sourceToolCallId === cardSourceToolCallId
  const cardTypeValid = typeof presentation.cardType === 'string' && !!normalizedCardType &&
    SAFE_CARD_TYPE_PATTERN.test(presentation.cardType) && presentation.cardType === normalizedCardType
  const decisionSource = typeof presentation.decisionSource === 'string'
    ? presentation.decisionSource.toUpperCase() : ''
  const decisionValid = ALLOWED_DECISION_SOURCES.indexOf(decisionSource) >= 0
  const layoutValid = presentation.layout === 'TABS'
  const titleValid = isSafeLabel(presentation.title, MAX_TITLE_LENGTH)
  const viewsArrayValid = Array.isArray(presentation.availableViews) && presentation.availableViews.length > 0
  const normalizedDefaultView = normalizeView(presentation.defaultView)
  const declaredViews = viewsArrayValid ? presentation.availableViews.map(normalizeView) : []
  const declaredDefaultValid = !!normalizedDefaultView && declaredViews.indexOf(normalizedDefaultView) >= 0

  if (!schemaValid) result.errors.push('PRESENTATION_SCHEMA_VERSION_INVALID')
  if (!sourceValid) result.errors.push('PRESENTATION_SOURCE_TOOL_CALL_INVALID')
  if (!cardTypeValid) result.errors.push('PRESENTATION_CARD_TYPE_INVALID')
  if (!decisionValid) result.errors.push('PRESENTATION_DECISION_SOURCE_INVALID')
  if (!layoutValid) result.errors.push('PRESENTATION_LAYOUT_INVALID')
  if (!titleValid) result.errors.push('PRESENTATION_TITLE_INVALID')
  if (!viewsArrayValid) result.errors.push('PRESENTATION_VIEW_INVALID')

  const viewResult = validateViews(presentation, data, Object.assign({ card, warnings: [], partial: false }, options || {}))
  result.validViews = viewResult.validViews
  result.invalidViews = viewResult.invalidViews
  result.allViewsInvalid = result.validViews.length === 0
  if (!declaredDefaultValid) result.errors.push('PRESENTATION_DEFAULT_VIEW_INVALID')

  const metadataValid = schemaValid && sourceValid && cardTypeValid && decisionValid && layoutValid &&
    titleValid && viewsArrayValid && declaredDefaultValid
  // 关联、协议或顶层元数据失败时不产生可渲染副本，避免串卡或显示非法描述。
  if (!metadataValid) {
    result.validViews = []
    result.invalidViews = []
    result.allViewsInvalid = true
    return result
  }

  const normalizedPresentation = {
    schemaVersion: 'v1',
    sourceToolCallId,
    cardType: presentation.cardType,
    decisionSource,
    title: presentation.title,
    layout: 'TABS',
    defaultView: normalizedDefaultView || '',
    availableViews: result.validViews
  }
  if (viewResult.normalized.summary) normalizedPresentation.summary = viewResult.normalized.summary
  if (viewResult.normalized.table) normalizedPresentation.table = viewResult.normalized.table
  if (viewResult.normalized.chart) normalizedPresentation.chart = viewResult.normalized.chart
  result.presentation = normalizedPresentation
  // defaultView 保留快照原决策；完整性告警移除图表后允许它暂时不在 validViews 中。
  result.valid = result.validViews.length > 0
  return result
}

/** 返回固定组件实际可以渲染的视图名称。 */
export function getValidPresentationViews(presentation, card, options) {
  return validatePresentation(presentation, card, options).validViews
}

/** 返回通过防御性校验的描述；无可用视图时返回 null。 */
export function sanitizePresentation(presentation, card, options) {
  const result = validatePresentation(presentation, card, options)
  return result.validViews.length ? result.presentation : null
}

export const validatePresentationDescriptor = validatePresentation

export default {
  MAX_CHART_METRICS,
  MAX_LABEL_LENGTH,
  MAX_SECTIONS,
  MAX_TABLE_COLUMNS,
  MAX_TITLE_LENGTH,
  PRESENTATION_VIEWS,
  VIEW_WHITELIST,
  getValidPresentationViews,
  hasIntegrityWarning,
  isSafePresentationField,
  sanitizePresentation,
  shouldHideChart,
  validatePresentation,
  validatePresentationDescriptor
}
