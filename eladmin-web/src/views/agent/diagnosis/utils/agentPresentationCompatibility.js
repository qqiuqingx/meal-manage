import {
  isSafeLabel,
  readPath
} from './agentPresentationFormatters'

/** 当前系统已经登记、可由前端只读兼容的 11 类业务卡片。 */
export const KNOWN_CARD_TYPES = [
  'CUSTOMER_PROFILE_LIST',
  'SERVICE_CUSTOMER_LIST',
  'SERVICE_CUSTOMER_DETAIL',
  'MEAL_PLAN_LIST',
  'VERIFICATION_LIST',
  'REFUND_LIST',
  'DISH_LIST',
  'DISH_CANDIDATE_LIST',
  'PACKAGE_DETAIL',
  'METRIC_RESULT',
  'BUSINESS_RULE'
]

const MAX_LEGACY_ROWS = 50
const MAX_LEGACY_COLUMNS = 8
const MAX_LEGACY_DEPTH = 4
const MAX_LEGACY_KEYS = 30
const SAFE_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_]*$/
const SENSITIVE_KEY_PATTERN = /(customerid|orderid|dishid|packageid|verificationid|mealplanid|address|phone|mobile|amount|price|money|payment|token|secret|password|permission|authorization|sql|jdbc|url)/i
const SENSITIVE_VALUE_PATTERN = /(bearer\s+[a-z0-9._-]+|select\s+.+\s+from|insert\s+into|update\s+.+\s+set|delete\s+from|drop\s+table)/i
const PHONE_PATTERN = /(?:^|\D)1[3-9]\d{9}(?:$|\D)/

const CUSTOMER_COLUMNS = [
  ['customerCode', '客户编号', 'TEXT'],
  ['customerName', '姓名', 'TEXT'],
  ['orderCode', '订单编号', 'TEXT'],
  ['orderTime', '下单时间', 'DATE_TIME'],
  ['status', '订单状态', 'STATUS'],
  ['parentPackageName', '套餐', 'TEXT']
]

const PROFILE_COLUMNS = [
  ['customerCode', '客户编号', 'TEXT'],
  ['customerName', '姓名', 'TEXT'],
  ['hasOrder', '是否有订单', 'BOOLEAN'],
  ['createTime', '创建时间', 'DATE_TIME'],
  ['maskedPhone', '手机号摘要', 'TEXT']
]

const MEAL_PLAN_COLUMNS = [
  ['recordDate', '日期', 'DATE'],
  ['mealType', '餐次', 'MEAL_TYPE'],
  ['status', '状态', 'STATUS'],
  ['verified', '已核销', 'BOOLEAN'],
  ['failureReason', '失败原因', 'TEXT'],
  ['dishes', '菜品摘要', 'TEXT']
]

const VERIFICATION_COLUMNS = [
  ['recordDate', '日期', 'DATE'],
  ['mealType', '餐次', 'MEAL_TYPE'],
  ['count', '数量', 'NUMBER'],
  ['refunded', '已退餐', 'BOOLEAN'],
  ['operateTime', '操作时间', 'DATE_TIME']
]

const REFUND_COLUMNS = [
  ['breakfastCount', '早餐数量', 'NUMBER'],
  ['lunchDinnerCount', '午晚餐数量', 'NUMBER'],
  ['verifiedBreakfastCount', '已核销早餐', 'NUMBER'],
  ['verifiedLunchDinnerCount', '已核销午晚餐', 'NUMBER'],
  ['reason', '原因', 'TEXT'],
  ['operateTime', '操作时间', 'DATE_TIME']
]

/** 判断值是否为普通对象，避免读取原型链或宿主对象。 */
function isPlainObject(value) {
  return value && Object.prototype.toString.call(value) === '[object Object]'
}

/** 创建不依赖 JSON 序列化的浅层卡片副本。 */
function copyOwnProperties(value) {
  const result = {}
  Object.keys(value || {}).forEach(key => {
    result[key] = value[key]
  })
  return result
}

/** 递归复制旧卡片数据，并把旧 maskedName 映射到兼容展示字段 customerName。 */
function normalizeLegacyNames(value, depth) {
  if (depth > MAX_LEGACY_DEPTH) {
    return undefined
  }
  if (Array.isArray(value)) {
    return value.slice(0, MAX_LEGACY_ROWS).map(item => normalizeLegacyNames(item, depth + 1))
  }
  if (!isPlainObject(value)) {
    return value
  }
  const result = {}
  Object.keys(value).forEach(key => {
    const normalized = normalizeLegacyNames(value[key], depth + 1)
    if (normalized !== undefined) {
      result[key] = normalized
    }
  })
  if (!Object.prototype.hasOwnProperty.call(result, 'customerName') &&
    Object.prototype.hasOwnProperty.call(result, 'maskedName')) {
    result.customerName = result.maskedName
  }
  return result
}

/** 判断未知卡片字段名是否可以作为安全键值摘要的字段路径。 */
function isSafeLegacyKey(key) {
  return Object.prototype.toString.call(key) === '[object String]' &&
    SAFE_KEY_PATTERN.test(key) && !SENSITIVE_KEY_PATTERN.test(key)
}

/** 判断未知卡片的标量值是否不会直接暴露手机号、Token 或 SQL。 */
function isSafeLegacyScalar(value) {
  if (value === null || value === undefined) {
    return false
  }
  if (Object.prototype.toString.call(value) === '[object Number]') {
    return isFinite(value) && !isNaN(value)
  }
  if (Object.prototype.toString.call(value) === '[object Boolean]') {
    return true
  }
  if (Object.prototype.toString.call(value) !== '[object String]') {
    return false
  }
  const text = value.trim()
  return !!text && !PHONE_PATTERN.test(text) && !SENSITIVE_VALUE_PATTERN.test(text)
}

/** 递归裁剪未知旧卡片，只保留有限深度和有限数量的安全键值。 */
function sanitizeLegacyValue(value, depth) {
  if (depth > MAX_LEGACY_DEPTH) {
    return undefined
  }
  if (isSafeLegacyScalar(value)) {
    return value
  }
  if (Array.isArray(value)) {
    return value.slice(0, MAX_LEGACY_ROWS)
      .map(item => sanitizeLegacyValue(item, depth + 1))
      .filter(item => item !== undefined)
  }
  if (!isPlainObject(value)) {
    return undefined
  }
  const result = {}
  Object.keys(value).filter(isSafeLegacyKey).slice(0, MAX_LEGACY_KEYS).forEach(key => {
    const normalized = sanitizeLegacyValue(value[key], depth + 1)
    if (normalized !== undefined) {
      result[key] = normalized
    }
  })
  return result
}

/** 把旧卡片字段转换成 Phase 05 表格所需的固定列定义。 */
function columns(definitions) {
  return definitions.map(item => ({ field: item[0], label: item[1], format: item[2] }))
}

/** 创建已知卡片的基础展示描述，所有文本和视图均为前端固定值。 */
function baseDescriptor(sourceToolCallId, cardType, title, defaultView, availableViews) {
  return {
    schemaVersion: 'v1',
    sourceToolCallId,
    cardType,
    decisionSource: 'SYSTEM',
    title,
    layout: 'TABS',
    defaultView,
    availableViews
  }
}

/** 创建固定单表描述。 */
function tableDescriptor(sourceToolCallId, cardType, title, path, definitions) {
  return Object.assign(baseDescriptor(sourceToolCallId, cardType, title, 'TABLE', ['TABLE']), {
    table: { dataPath: path, columns: columns(definitions) }
  })
}

/** 创建固定摘要描述。 */
function summaryDescriptor(sourceToolCallId, cardType, title, path, definitions) {
  return Object.assign(baseDescriptor(sourceToolCallId, cardType, title, 'TEXT', ['TEXT']), {
    summary: { dataPath: path, fields: columns(definitions) }
  })
}

/** 返回服务客户详情的固定摘要与四个中文子表分区。 */
function serviceCustomerDetailDescriptor(sourceToolCallId) {
  const descriptor = baseDescriptor(sourceToolCallId, 'SERVICE_CUSTOMER_DETAIL', '服务客户详情', 'TABLE', ['TABLE'])
  descriptor.summary = { dataPath: 'data.profile', fields: columns(PROFILE_COLUMNS) }
  descriptor.table = {
    dataPath: 'data',
    columns: [],
    sections: [
      { id: 'orders', title: '订单', dataPath: 'data.orders', columns: columns(CUSTOMER_COLUMNS) },
      { id: 'mealPlans', title: '排餐', dataPath: 'data.mealPlans', columns: columns(MEAL_PLAN_COLUMNS) },
      { id: 'verifications', title: '核销', dataPath: 'data.verifications', columns: columns(VERIFICATION_COLUMNS) },
      { id: 'refunds', title: '退餐', dataPath: 'data.refunds', columns: columns(REFUND_COLUMNS) }
    ]
  }
  return descriptor
}

/** 根据旧排期卡片中已有的数据形态选择固定菜品路径，不查询实时数据。 */
function dishListDescriptor(sourceToolCallId, cardData) {
  const scheduledItems = readPath(cardData, 'data.groups[].items')
  if (Array.isArray(scheduledItems)) {
    return tableDescriptor(sourceToolCallId, 'DISH_LIST', '排期菜单', 'data.groups[].items', [
      ['dishName', '菜品名称', 'TEXT'],
      ['dishTypeName', '菜品类型', 'TEXT'],
      ['enabled', '启用状态', 'BOOLEAN'],
      ['ingredientNames', '配料摘要', 'TEXT'],
      ['mealTypes', '适用餐次', 'MEAL_TYPE']
    ])
  }
  return tableDescriptor(sourceToolCallId, 'DISH_LIST', '菜品列表', 'items', [
    ['name', '菜品名称', 'TEXT'],
    ['dishType', '菜品类型', 'TEXT'],
    ['enabled', '启用状态', 'BOOLEAN'],
    ['ingredients', '配料摘要', 'TEXT']
  ])
}

/** 为已知 cardType 生成本地只读描述；该函数不访问 API、LLM 或业务数据源。 */
export function createLegacyPresentation(card, sourceToolCallId) {
  const cardType = cardTypeOf(card)
  const callId = sourceToolCallId || sourceIdOf(card) || 'legacy-card'
  const data = card && card.data ? card.data : {}
  switch (cardType) {
    case 'CUSTOMER_PROFILE_LIST':
      return tableDescriptor(callId, cardType, '客户档案', 'items', PROFILE_COLUMNS)
    case 'SERVICE_CUSTOMER_LIST':
      return tableDescriptor(callId, cardType, '服务客户订单', 'items', CUSTOMER_COLUMNS)
    case 'SERVICE_CUSTOMER_DETAIL':
      return serviceCustomerDetailDescriptor(callId)
    case 'MEAL_PLAN_LIST':
      return tableDescriptor(callId, cardType, '排餐记录', 'items', MEAL_PLAN_COLUMNS)
    case 'VERIFICATION_LIST':
      return tableDescriptor(callId, cardType, '核销记录', 'items', VERIFICATION_COLUMNS)
    case 'REFUND_LIST':
      return tableDescriptor(callId, cardType, '退餐记录', 'items', REFUND_COLUMNS)
    case 'DISH_LIST':
      return dishListDescriptor(callId, data)
    case 'DISH_CANDIDATE_LIST':
      return Object.assign(baseDescriptor(callId, cardType, '候选菜预览', 'TABLE', ['TABLE']), {
        summary: {
          dataPath: 'data',
          fields: columns([
            ['present', '客户存在', 'BOOLEAN'],
            ['customerCode', '客户编号', 'TEXT'],
            ['recordDate', '日期', 'DATE'],
            ['mealTypeCode', '餐次', 'MEAL_TYPE'],
            ['totalCandidateCount', '候选总数', 'NUMBER'],
            ['availableCandidateCount', '可用数量', 'NUMBER'],
            ['filteredCandidateCount', '过滤数量', 'NUMBER']
          ])
        },
        table: {
          dataPath: 'data.items',
          columns: columns([
            ['dishName', '候选菜品', 'TEXT'],
            ['dishTypeCode', '菜品类型', 'TEXT'],
            ['available', '可用', 'BOOLEAN'],
            ['filterReasons', '过滤原因', 'TEXT']
          ])
        }
      })
    case 'PACKAGE_DETAIL':
      return Object.assign(baseDescriptor(callId, cardType, '套餐详情', 'TABLE', ['TABLE']), {
        summary: {
          dataPath: 'data',
          fields: columns([
            ['packageCode', '套餐编号', 'TEXT'],
            ['packageName', '套餐名称', 'TEXT']
          ])
        },
        table: {
          dataPath: 'data.subPackages',
          columns: columns([
            ['subPackageCode', '子套餐编号', 'TEXT'],
            ['subPackageName', '子套餐名称', 'TEXT'],
            ['meatCount', '荤菜数', 'NUMBER'],
            ['vegCount', '素菜数', 'NUMBER'],
            ['includeSoup', '含汤', 'BOOLEAN'],
            ['includeRice', '含米饭', 'BOOLEAN'],
            ['enabled', '启用状态', 'BOOLEAN']
          ])
        }
      })
    case 'METRIC_RESULT':
      return metricDescriptor(callId, data)
    case 'BUSINESS_RULE':
      return summaryDescriptor(callId, cardType, '业务规则', 'data', [
        ['ruleId', '规则编号', 'TEXT'],
        ['version', '版本', 'TEXT'],
        ['title', '标题', 'TEXT'],
        ['content', '规则内容', 'TEXT'],
        ['effectiveFrom', '生效时间', 'DATE_TIME'],
        ['updatedAt', '更新时间', 'DATE_TIME']
      ])
    default:
      return null
  }
}

/** 为历史指标卡片保留服务端已有的 breakdown，不在浏览器重新聚合业务数据。 */
function metricDescriptor(sourceToolCallId, cardData) {
  const descriptor = baseDescriptor(sourceToolCallId, 'METRIC_RESULT', '运营指标', 'TEXT', ['TEXT'])
  descriptor.summary = {
    dataPath: 'data',
    fields: columns([
      ['metric', '指标', 'TEXT'],
      ['total', '总数', 'NUMBER'],
      ['queriedAt', '查询时间', 'DATE_TIME']
    ])
  }
  const breakdown = readPath(cardData, 'data.breakdown')
  if (Array.isArray(breakdown)) {
    descriptor.availableViews = ['TEXT', 'TABLE', 'BAR']
    descriptor.table = {
      dataPath: 'data.breakdown',
      columns: columns([
        ['label', '分组', 'TEXT'],
        ['value', '数值', 'NUMBER']
      ])
    }
    descriptor.chart = {
      type: 'BAR',
      dataPath: 'data.breakdown',
      dimensionField: 'label',
      metricFields: ['value'],
      dimensionLabel: '分组',
      metricLabels: ['数值']
    }
  }
  return descriptor
}

/** 返回卡片类型，兼容历史快照中 type/cardType 两种字段名称。 */
export function cardTypeOf(card) {
  return card && (card.type || card.cardType) ? String(card.type || card.cardType) : ''
}

/** 返回原有调用 ID；恢复时若缺失由 mapLegacyCards 生成本地稳定 ID。 */
function sourceIdOf(card) {
  return card && card.sourceToolCallId ? String(card.sourceToolCallId) : ''
}

/** 复制历史卡片并仅补齐展示所需的 customerName，不补查或推断姓名。 */
function normalizeKnownCard(card, sourceToolCallId) {
  const result = isPlainObject(card) ? copyOwnProperties(card) : {}
  result.type = cardTypeOf(card) || 'UNKNOWN_CARD'
  result.sourceToolCallId = sourceToolCallId
  result.data = normalizeLegacyNames(card && card.data ? card.data : {}, 0)
  return result
}

/** 从安全旧对象中提取第一组有限行，供未知卡片的通用表格使用。 */
function findSafeRows(value, depth) {
  if (depth > MAX_LEGACY_DEPTH) {
    return null
  }
  if (Array.isArray(value)) {
    const rows = value.filter(item => isPlainObject(item) || isSafeLegacyScalar(item)).slice(0, MAX_LEGACY_ROWS)
    if (rows.length || value.length === 0) {
      return rows.map(item => isPlainObject(item) ? item : { value: item })
    }
    return null
  }
  if (!isPlainObject(value)) {
    return null
  }
  const preferred = ['items', 'rows', 'records', 'results', 'breakdown', 'data']
  const keys = preferred.concat(Object.keys(value)).filter((key, index, values) => values.indexOf(key) === index)
  for (let index = 0; index < keys.length; index++) {
    const key = keys[index]
    if (!Object.prototype.hasOwnProperty.call(value, key) || !isSafeLegacyKey(key)) {
      continue
    }
    const rows = findSafeRows(value[key], depth + 1)
    if (rows) {
      return rows
    }
  }
  return null
}

/** 把未知旧卡片中的安全数组行限制为可渲染的直接字段。 */
function normalizeSafeRows(rows) {
  return (rows || []).map(row => {
    const result = {}
    Object.keys(row || {}).filter(isSafeLegacyKey).slice(0, MAX_LEGACY_COLUMNS).forEach(key => {
      const value = row[key]
      if (isSafeLegacyScalar(value)) {
        result[key] = value
      } else if (Array.isArray(value)) {
        const values = value.filter(isSafeLegacyScalar).slice(0, 10)
        if (values.length) result[key] = values
      }
    })
    return result
  }).filter(row => Object.keys(row).length > 0)
}

/** 为未知安全表格生成字段列，字段名只作为普通文本标签使用。 */
function safeRowColumns(rows) {
  const names = []
  ;(rows || []).forEach(row => Object.keys(row || {}).forEach(key => {
    if (names.indexOf(key) < 0 && names.length < MAX_LEGACY_COLUMNS && isSafeLegacyKey(key)) names.push(key)
  }))
  return names.map(field => ({
    field,
    label: isSafeLabel(field, 30) ? field : '字段',
    format: 'TEXT'
  }))
}

/** 提取未知卡片对象中的安全标量，无法安全识别时不输出原始对象。 */
function safeSummaryFields(value) {
  const source = isPlainObject(value && value.data) ? value.data : value
  const summary = {}
  if (!isPlainObject(source)) {
    return summary
  }
  Object.keys(source).filter(isSafeLegacyKey).slice(0, MAX_LEGACY_COLUMNS).forEach(key => {
    const item = source[key]
    if (isSafeLegacyScalar(item)) {
      summary[key] = item
    } else if (Array.isArray(item)) {
      const values = item.filter(isSafeLegacyScalar).slice(0, 10)
      if (values.length) summary[key] = values
    }
  })
  return summary
}

/** 创建未知旧卡片的安全键值摘要或有限通用表格，不把原对象序列化到页面。 */
export function createSafeLegacyCard(card, sourceToolCallId) {
  const callId = sourceToolCallId || sourceIdOf(card) || 'legacy-card'
  const safeData = sanitizeLegacyValue(card && card.data ? card.data : {}, 0)
  const rows = normalizeSafeRows(findSafeRows(safeData, 0))
  const normalizedCard = isPlainObject(card) ? copyOwnProperties(card) : {}
  normalizedCard.type = cardTypeOf(card) || 'UNKNOWN_CARD'
  normalizedCard.sourceToolCallId = callId
  if (rows.length) {
    normalizedCard.data = { legacyItems: rows }
    return {
      card: normalizedCard,
      presentation: Object.assign(baseDescriptor(callId, normalizedCard.type, '历史数据摘要', 'TABLE', ['TABLE']), {
        table: { dataPath: 'legacyItems', columns: safeRowColumns(rows) }
      })
    }
  }

  const summary = safeSummaryFields(safeData)
  if (!Object.keys(summary).length) {
    summary.message = '展示格式暂不可用'
  }
  normalizedCard.data = { legacySummary: summary }
  const fields = Object.keys(summary).map(key => ({
    field: key,
    label: key === 'message' ? '提示' : key,
    format: 'TEXT'
  }))
  return {
    card: normalizedCard,
    presentation: Object.assign(baseDescriptor(callId, normalizedCard.type, '历史数据摘要', 'TEXT', ['TEXT']), {
      summary: { dataPath: 'legacySummary', fields }
    })
  }
}

/** 为一张旧卡片生成展示状态；已知卡片使用固定中文规则，未知卡片只使用安全摘要。 */
export function mapLegacyCard(card, sourceToolCallId) {
  const cardType = cardTypeOf(card)
  const normalizedCard = normalizeKnownCard(card, sourceToolCallId)
  const knownPresentation = KNOWN_CARD_TYPES.indexOf(cardType) >= 0
    ? createLegacyPresentation(normalizedCard, sourceToolCallId)
    : null
  if (knownPresentation) {
    return { card: normalizedCard, presentation: knownPresentation }
  }
  return createSafeLegacyCard(card, sourceToolCallId)
}

/** 为仅含 cards 的历史消息补齐本地只读展示，并保证每张卡片有独立关联 ID。 */
export function mapLegacyCards(cards) {
  const sourceCards = Array.isArray(cards) ? cards : []
  const usedIds = {}
  const mappedCards = []
  const presentations = []
  sourceCards.forEach((card, index) => {
    const originalId = sourceIdOf(card)
    let sourceToolCallId = originalId || `legacy-card-${index + 1}`
    if (usedIds[sourceToolCallId]) {
      sourceToolCallId = `legacy-card-${index + 1}`
    }
    usedIds[sourceToolCallId] = true
    const mapped = mapLegacyCard(card, sourceToolCallId)
    mappedCards.push(mapped.card)
    presentations.push(mapped.presentation)
  })
  return { cards: mappedCards, presentations }
}

/** 判断卡片类型是否在历史兼容白名单中。 */
export function isKnownCardType(cardType) {
  return KNOWN_CARD_TYPES.indexOf(String(cardType || '')) >= 0
}

export const mapHistoricalCards = mapLegacyCards
export const buildLegacyPresentation = createLegacyPresentation

export default {
  KNOWN_CARD_TYPES,
  buildLegacyPresentation,
  cardTypeOf,
  createLegacyPresentation,
  createSafeLegacyCard,
  isKnownCardType,
  mapHistoricalCards,
  mapLegacyCard,
  mapLegacyCards
}
