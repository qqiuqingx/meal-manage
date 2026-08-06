const SAFE_PATH_PATTERN = /^[A-Za-z][A-Za-z0-9_]*(\[\])?(\.[A-Za-z][A-Za-z0-9_]*(\[\])?)*$/
const SAFE_LABEL_PATTERN = /^[^<>{}]*$/
const DANGEROUS_PATH_SEGMENTS = ['__proto__', 'prototype', 'constructor', 'window', 'globalthis', 'document', 'this']

const SUPPORTED_FORMATS = ['TEXT', 'DATE', 'DATE_TIME', 'STATUS', 'MEAL_TYPE', 'NUMBER', 'BOOLEAN']
const EMPTY_VALUE = '-'

const STATUS_LABELS = {
  ACTIVE: '进行中',
  IN_PROGRESS: '进行中',
  PENDING: '待处理',
  WAITING: '待处理',
  SUCCESS: '成功',
  COMPLETED: '已完成',
  COMPLETE: '已完成',
  CANCELLED: '已取消',
  CANCELED: '已取消',
  REFUNDED: '已退餐',
  VERIFIED: '已核销',
  UNVERIFIED: '待核销',
  ENABLED: '启用',
  DISABLED: '停用',
  AVAILABLE: '可用',
  UNAVAILABLE: '不可用',
  EXPIRED: '已过期',
  FAILED: '失败',
  ERROR: '异常',
  DRAFT: '草稿'
}

const NUMERIC_STATUS_LABELS = {
  0: '已取消',
  1: '进行中',
  2: '已完成',
  3: '已退餐'
}

const MEAL_TYPE_LABELS = {
  BREAKFAST: '早餐',
  LUNCH: '午餐',
  DINNER: '晚餐'
}

/** 判断展示描述中的格式类型是否属于前端固定白名单。 */
export function isSupportedFormat(format) {
  return SUPPORTED_FORMATS.indexOf(String(format || '').toUpperCase()) >= 0
}

/** 判断字段路径是否只包含点路径和固定的数组遍历标记。 */
export function isSafePath(path) {
  if (path === null || path === undefined || Object.prototype.toString.call(path) !== '[object String]' || !path || !SAFE_PATH_PATTERN.test(path)) {
    return false
  }
  return path.split('.').every(segment => {
    const name = segment.replace(/\[\]$/, '').toLowerCase()
    return DANGEROUS_PATH_SEGMENTS.indexOf(name) < 0
  })
}

/** 判断标题和字段标签是否为可安全插值的普通文本。 */
export function isSafeLabel(label, maxLength = 100) {
  return label !== null && label !== undefined && Object.prototype.toString.call(label) === '[object String]' &&
    label.length <= maxLength && SAFE_LABEL_PATTERN.test(label)
}

function parsePath(path) {
  if (!isSafePath(path)) {
    return null
  }
  return path.split('.').map(segment => ({
    name: segment.replace(/\[\]$/, ''),
    array: segment.slice(-2) === '[]'
  }))
}

function appendResolvedValue(target, value) {
  if (Array.isArray(value)) {
    value.forEach(item => target.push(item))
    return
  }
  if (value !== undefined) {
    target.push(value)
  }
}

function resolveTokens(value, tokens, index) {
  if (index >= tokens.length) {
    return value
  }
  if (Array.isArray(value)) {
    const resolved = []
    value.forEach(item => appendResolvedValue(resolved, resolveTokens(item, tokens, index)))
    return resolved
  }
  if (!value || Object.prototype.toString.call(value) !== '[object Object]') {
    return undefined
  }

  const token = tokens[index]
  if (!Object.prototype.hasOwnProperty.call(value, token.name)) {
    return undefined
  }
  const nextValue = value[token.name]
  if (!token.array) {
    return resolveTokens(nextValue, tokens, index + 1)
  }
  if (!Array.isArray(nextValue)) {
    return undefined
  }
  if (index === tokens.length - 1) {
    return nextValue
  }

  const resolved = []
  nextValue.forEach(item => appendResolvedValue(resolved, resolveTokens(item, tokens, index + 1)))
  return resolved
}

/** 按固定路径读取对象，非法路径或原型链字段一律返回 undefined。 */
export function readPath(source, path) {
  const tokens = parsePath(path)
  return tokens ? resolveTokens(source, tokens, 0) : undefined
}

function isEmptyValue(value) {
  return value === undefined || value === null ||
    (Object.prototype.toString.call(value) === '[object String]' && value.trim() === '')
}

function textValue(value) {
  if (isEmptyValue(value)) {
    return EMPTY_VALUE
  }
  if (Array.isArray(value)) {
    const values = value.map(item => textValue(item)).filter(item => item !== EMPTY_VALUE)
    return values.length ? values.join('、') : EMPTY_VALUE
  }
  if (Object.prototype.toString.call(value) === '[object Object]') {
    return EMPTY_VALUE
  }
  return String(value)
}

function dateParts(value) {
  if (value instanceof Date && !isNaN(value.getTime())) {
    const pad = part => `0${part}`.slice(-2)
    return {
      date: `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`,
      time: `${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
    }
  }
  if (Object.prototype.toString.call(value) !== '[object String]') {
    return null
  }
  const normalized = value.trim().replace('T', ' ')
  const match = normalized.match(/^(\d{4}-\d{2}-\d{2})(?:\s+(\d{2}:\d{2}(?::\d{2})?))?/)
  if (!match) {
    return null
  }
  return { date: match[1], time: match[2] || '' }
}

/** 按页面现有的本地字符串习惯格式化日期，避免无谓的时区换算。 */
export function formatDate(value, withTime) {
  if (isEmptyValue(value)) {
    return EMPTY_VALUE
  }
  const parts = dateParts(value)
  if (!parts) {
    return textValue(value)
  }
  if (!withTime || !parts.time) {
    return parts.date
  }
  return `${parts.date} ${parts.time}`
}

function enumValue(value, labels) {
  if (isEmptyValue(value)) {
    return EMPTY_VALUE
  }
  const key = String(value).trim().toUpperCase()
  return Object.prototype.hasOwnProperty.call(labels, key) ? labels[key] : textValue(value)
}

function statusValue(value) {
  if (isEmptyValue(value)) {
    return EMPTY_VALUE
  }
  const key = String(value).trim().toUpperCase()
  if (Object.prototype.hasOwnProperty.call(STATUS_LABELS, key)) {
    return STATUS_LABELS[key]
  }
  if (Object.prototype.hasOwnProperty.call(NUMERIC_STATUS_LABELS, String(value))) {
    return NUMERIC_STATUS_LABELS[String(value)]
  }
  return textValue(value)
}

function numberValue(value) {
  if (isEmptyValue(value)) {
    return EMPTY_VALUE
  }
  if (Object.prototype.toString.call(value) === '[object Number]') {
    return isFinite(value) && !isNaN(value) ? String(value) : EMPTY_VALUE
  }
  if (Object.prototype.toString.call(value) === '[object String]' && value.trim() !== '' && isFinite(Number(value))) {
    return String(Number(value))
  }
  return textValue(value)
}

function booleanValue(value) {
  if (isEmptyValue(value)) {
    return EMPTY_VALUE
  }
  if (value === true || value === 1 || String(value).toUpperCase() === 'TRUE' || String(value).toUpperCase() === 'YES') {
    return '是'
  }
  if (value === false || value === 0 || String(value).toUpperCase() === 'FALSE' || String(value).toUpperCase() === 'NO') {
    return '否'
  }
  return textValue(value)
}

/** 按 descriptor 的固定 format 将单个值转换为普通文本。 */
export function formatValue(value, format) {
  const normalizedFormat = String(format || 'TEXT').toUpperCase()
  if (normalizedFormat === 'DATE') return formatDate(value, false)
  if (normalizedFormat === 'DATE_TIME') return formatDate(value, true)
  if (normalizedFormat === 'STATUS') return statusValue(value)
  if (normalizedFormat === 'MEAL_TYPE') return enumValue(value, MEAL_TYPE_LABELS)
  if (normalizedFormat === 'NUMBER') return numberValue(value)
  if (normalizedFormat === 'BOOLEAN') return booleanValue(value)
  return textValue(value)
}

/** 读取字段并格式化；orderTime 缺失时按 dealTime、createTime 顺序回退。 */
export function formatFieldValue(row, field, format) {
  let value = readPath(row, field)
  if (field === 'orderTime' && isEmptyValue(value) && row && Object.prototype.toString.call(row) === '[object Object]') {
    value = !isEmptyValue(row.dealTime) ? row.dealTime : row.createTime
  }
  return formatValue(value, format)
}

export const FORMAT_TYPES = SUPPORTED_FORMATS.slice()

export default {
  FORMAT_TYPES,
  formatDate,
  formatFieldValue,
  formatValue,
  isSafeLabel,
  isSafePath,
  isSupportedFormat,
  readPath
}
