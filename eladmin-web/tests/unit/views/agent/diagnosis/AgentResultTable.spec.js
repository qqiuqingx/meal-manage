/* eslint-env jest */
import AgentResultTable from '@/views/agent/diagnosis/components/AgentResultTable.vue'

function createContext(data, descriptor, pageSize = 2) {
  const context = {
    data,
    descriptor,
    table: null,
    card: null,
    pageSize,
    currentPages: {}
  }
  Object.defineProperty(context, 'sourceData', { get: () => AgentResultTable.computed.sourceData.call(context) })
  Object.defineProperty(context, 'tableDescriptor', { get: () => AgentResultTable.computed.tableDescriptor.call(context) })
  Object.defineProperty(context, 'pageSizeNumber', { get: () => AgentResultTable.computed.pageSizeNumber.call(context) })
  Object.defineProperty(context, 'sections', { get: () => AgentResultTable.computed.sections.call(context) })
  Object.assign(context, AgentResultTable.methods)
  return context
}

describe('AgentResultTable', () => {
  test('keeps descriptor column order and applies local pagination', () => {
    const context = createContext(
      { items: [
        { customerCode: 'C001', customerName: '张三', orderCode: 'O001' },
        { customerCode: 'C002', customerName: '李四', orderCode: 'O002' },
        { customerCode: 'C003', customerName: '王五', orderCode: 'O003' }
      ] },
      {
        table: {
          dataPath: 'items',
          columns: [
            { field: 'customerCode', label: '客户编号', format: 'TEXT' },
            { field: 'customerName', label: '姓名', format: 'TEXT' },
            { field: 'orderCode', label: '订单编号', format: 'TEXT' }
          ]
        }
      }
    )

    expect(context.sections[0].columns.map(column => column.field)).toEqual(['customerCode', 'customerName', 'orderCode'])
    expect(context.pagedRows(context.sections[0])).toHaveLength(2)
    context.changePage('section-0', 2)
    expect(context.pagedRows(context.sections[0]).map(row => row.customerCode)).toEqual(['C003'])
    expect(context.cellClass('customerCode')).toBe('customer-code')
    expect(context.cellClass('customerName')).toBe('customer-name')
  })

  test('renders controlled sections without combining or aggregating their rows', () => {
    const context = createContext(
      {
        data: {
          profile: { customerCode: 'C001' },
          orders: [{ orderCode: 'O001' }],
          mealPlans: [{ recordDate: '2026-08-05' }]
        }
      },
      {
        table: {
          dataPath: 'data',
          columns: [],
          sections: [
            { id: 'orders', title: '订单', dataPath: 'data.orders', columns: [{ field: 'orderCode', label: '订单编号', format: 'TEXT' }] },
            { id: 'mealPlans', title: '排餐', dataPath: 'data.mealPlans', columns: [{ field: 'recordDate', label: '日期', format: 'DATE' }] }
          ]
        }
      }
    )

    expect(context.sections.map(section => section.title)).toEqual(['订单', '排餐'])
    expect(context.sections.map(section => section.rows.length)).toEqual([1, 1])
  })

  test('uses a safe empty state for invalid paths and fields', () => {
    const context = createContext({}, {
      table: {
        dataPath: 'items.constructor',
        columns: [{ field: 'value[0]', label: '值', format: 'TEXT' }]
      }
    })
    expect(context.sections).toEqual([])
  })

  test('only offers candidate selection for rows with a customer code', () => {
    const context = createContext({}, { table: { dataPath: 'items', columns: [] } })

    expect(context.canSelectRow({ customerCode: 'C001', customerId: 1 })).toBe(true)
    expect(context.canSelectRow({ customerId: 1, customerName: '张三' })).toBe(false)
    expect(context.canSelectRow(null)).toBe(false)
  })
})
