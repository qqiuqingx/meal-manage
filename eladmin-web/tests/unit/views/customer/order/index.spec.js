/* eslint-env jest */

const orderApi = {
  getOrder: jest.fn()
}

function createOrderDefaultForm() {
  return {
    id: null,
    customerId: null,
    orderCode: null,
    parentPackageId: null,
    childPackageId: null,
    depositAmount: 0,
    totalAmount: null,
    finalAmount: null,
    breakfastCount: 0,
    lunchDinnerCount: 0,
    breakfastPrice: 0,
    lunchDinnerPrice: 0,
    verifiedCount: 0,
    verifiedAmount: 0,
    mealBalance: 0,
    remainingCount: 0,
    dealTime: null,
    firstDeliveryTime: null,
    startDate: null,
    endDate: null,
    status: 1,
    mealType: 'ALL',
    scheduleMode: 'SCHEDULE',
    deliveryDatesWithMealTypes: [],
    deliveryDates: [],
    remark: null,
    customerSource: null,
    mainDishCount: 0,
    sideDishCount: 0,
    vegCount: 0,
    riceCount: 0,
    soupCount: 0
  }
}

function beforeToCU(ctx) {
  const currentForm = { ...ctx.form }
  Object.assign(ctx.form, createOrderDefaultForm(), currentForm)
  return true
}

async function handleEdit(ctx, row) {
  const requestId = ctx.editRequestId + 1
  ctx.editRequestId = requestId
  try {
    const res = await orderApi.getOrder(row.id)
    if (requestId !== ctx.editRequestId) {
      return
    }
    const detail = res.data || res
    ctx.crud.toEdit(detail)
  } catch (e) {
    if (requestId !== ctx.editRequestId) {
      return
    }
    ctx.$message.error('获取订单详情失败: ' + (e.message || '未知错误'))
  }
}

describe('CustomerOrder edit flow', () => {
  beforeEach(() => {
    orderApi.getOrder.mockReset()
  })

  test('loads order detail before entering edit mode', async() => {
    const detail = {
      id: 12,
      customerId: 3,
      parentPackageId: 51,
      childPackageId: 7,
      mainDishCount: 2,
      sideDishCount: 1,
      vegCount: 1,
      riceCount: 1,
      soupCount: 1,
      deliveryDates: '[{\"date\":\"2026-04-24\",\"mealTypes\":[\"LUNCH\",\"DINNER\"]}]'
    }
    orderApi.getOrder.mockResolvedValue({ data: detail })
    const toEdit = jest.fn()
    const ctx = {
      crud: { toEdit },
      editRequestId: 0,
      $message: { error: jest.fn() }
    }

    await handleEdit(ctx, { id: 12 })

    expect(orderApi.getOrder).toHaveBeenCalledWith(12)
    expect(toEdit).toHaveBeenCalledWith(detail)
    expect(toEdit.mock.calls[0][0].parentPackageId).toBe(51)
    expect(toEdit.mock.calls[0][0].childPackageId).toBe(7)
    expect(toEdit.mock.calls[0][0].mainDishCount).toBe(2)
    expect(toEdit.mock.calls[0][0].sideDishCount).toBe(1)
    expect(toEdit.mock.calls[0][0].vegCount).toBe(1)
    expect(toEdit.mock.calls[0][0].riceCount).toBe(1)
    expect(toEdit.mock.calls[0][0].soupCount).toBe(1)
    expect(ctx.$message.error).not.toHaveBeenCalled()
  })

  test('beforeToCU restores missing default fields on edit form', () => {
    const ctx = {
      form: {
        id: 12,
        customerId: 3,
        deliveryDates: '[{\"date\":\"2026-04-24\",\"mealTypes\":[\"LUNCH\",\"DINNER\"]}]'
      }
    }

    expect(beforeToCU(ctx)).toBe(true)
    expect(ctx.form.scheduleMode).toBe('SCHEDULE')
    expect(ctx.form.deliveryDatesWithMealTypes).toEqual([])
    expect(ctx.form.deliveryDates).toBe('[{\"date\":\"2026-04-24\",\"mealTypes\":[\"LUNCH\",\"DINNER\"]}]')
  })
})
