import {
  renderAssistantMessage,
  normalizeLineBreaks
} from '@/views/agent/diagnosis/utils/assistantMessageRenderer'

describe('assistantMessageRenderer', () => {
  test('normalizes literal escaped line breaks', () => {
    expect(normalizeLineBreaks('第一段\\n\\n第二段')).toBe('第一段\n\n第二段')
    expect(normalizeLineBreaks('第一行\r\n第二行')).toBe('第一行\n第二行')
  })

  test('renders markdown paragraphs, emphasis and lists', () => {
    const html = renderAssistantMessage('确认**客户 B5600**已参与排餐。\\n\\n- 订单处于**进行中**\\n- 排餐模式为每日')

    expect(html).toContain('<p>确认<strong>客户 B5600</strong>已参与排餐。</p>')
    expect(html).toContain('<ul><li>订单处于<strong>进行中</strong></li><li>排餐模式为每日</li></ul>')
  })

  test('escapes raw html and does not expose executable markup', () => {
    const html = renderAssistantMessage('<script>alert(1)</script> **安全文本**')

    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(html).toContain('<strong>安全文本</strong>')
    expect(html).not.toContain('<script>')
  })
})
