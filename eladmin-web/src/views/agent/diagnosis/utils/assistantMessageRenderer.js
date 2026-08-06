const HTML_ENTITIES = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;'
}

/**
 * 转换助手响应中的转义换行，兼容接口返回实际换行和字面量\\n两种格式。
 *
 * @param {string} value 助手响应文本
 * @returns {string} 统一使用换行符的文本
 */
function normalizeLineBreaks(value) {
  return String(value == null ? '' : value)
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .replace(/\\r\\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\\r/g, '\n')
}

/**
 * 转义用户或模型返回的文本，避免通过 v-html 插入未信任的 HTML。
 *
 * @param {string} value 待转义文本
 * @returns {string} HTML 安全文本
 */
function escapeHtml(value) {
  return String(value == null ? '' : value).replace(/[&<>"']/g, character => HTML_ENTITIES[character])
}

/**
 * 渲染有限的行内 Markdown 语法；原始 HTML 不在支持范围内。
 *
 * @param {string} value 行内文本
 * @returns {string} HTML 片段
 */
function renderInlineMarkdown(value) {
  let result = escapeHtml(value)
  const protectedTokens = []
  const protect = html => {
    const tokenIndex = protectedTokens.push(html) - 1
    return `\u0000${tokenIndex}\u0000`
  }

  result = result.replace(/`([^`\n]+)`/g, (match, code) => protect(`<code>${code}</code>`))
  result = result.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
  result = result.replace(/__([^_\n]+)__/g, '<strong>$1</strong>')
  result = result.replace(/~~([^~\n]+)~~/g, '<del>$1</del>')
  result = result.replace(/\*([^*\n]+)\*/g, '<em>$1</em>')
  result = result.replace(/_([^_\n]+)_/g, '<em>$1</em>')

  return result.replace(/\u0000(\d+)\u0000/g, (match, tokenIndex) => protectedTokens[Number(tokenIndex)])
}

/**
 * 将助手返回的 Markdown 文本转换为安全 HTML，支持段落、换行、加粗、斜体、代码和列表。
 *
 * @param {string} content 助手响应文本
 * @returns {string} 可安全交给 v-html 渲染的 HTML
 */
export function renderAssistantMessage(content) {
  const normalized = normalizeLineBreaks(content)
  if (!normalized) {
    return ''
  }

  const blocks = []
  const lines = normalized.split('\n')
  let paragraphLines = []
  let listType = null
  let listItems = []

  const flushParagraph = () => {
    if (!paragraphLines.length) {
      return
    }
    blocks.push(`<p>${paragraphLines.map(renderInlineMarkdown).join('<br>')}</p>`)
    paragraphLines = []
  }

  const flushList = () => {
    if (!listType) {
      return
    }
    blocks.push(`<${listType}>${listItems.map(item => `<li>${renderInlineMarkdown(item)}</li>`).join('')}</${listType}>`)
    listType = null
    listItems = []
  }

  lines.forEach(line => {
    const trimmed = line.trim()
    if (!trimmed) {
      flushParagraph()
      flushList()
      return
    }

    const heading = trimmed.match(/^(#{1,6})\s+(.+)$/)
    if (heading) {
      flushParagraph()
      flushList()
      const level = heading[1].length
      blocks.push(`<h${level}>${renderInlineMarkdown(heading[2])}</h${level}>`)
      return
    }

    const unorderedItem = trimmed.match(/^[-*+]\s+(.+)$/)
    if (unorderedItem) {
      flushParagraph()
      if (listType !== 'ul') {
        flushList()
        listType = 'ul'
        listItems = []
      }
      listItems.push(unorderedItem[1])
      return
    }

    const orderedItem = trimmed.match(/^\d+[.)]\s+(.+)$/)
    if (orderedItem) {
      flushParagraph()
      if (listType !== 'ol') {
        flushList()
        listType = 'ol'
        listItems = []
      }
      listItems.push(orderedItem[1])
      return
    }

    const quote = trimmed.match(/^>\s?(.*)$/)
    if (quote) {
      flushParagraph()
      flushList()
      blocks.push(`<blockquote>${renderInlineMarkdown(quote[1])}</blockquote>`)
      return
    }

    flushList()
    paragraphLines.push(line)
  })

  flushParagraph()
  flushList()
  return blocks.join('')
}

export { escapeHtml, normalizeLineBreaks }
