/* eslint-env jest */
jest.mock('@/api/customer/profile', () => ({
  getProfiles: jest.fn(),
  getMealStats: jest.fn(),
  getProfile: jest.fn(),
  getDietOptions: jest.fn(() => Promise.resolve({ data: [] })),
  generateCode: jest.fn(),
  parseIntakeText: jest.fn(),
  previewCustomerImport: jest.fn(),
  confirmCustomerImport: jest.fn(),
  add: jest.fn(),
  edit: jest.fn(),
  del: jest.fn()
}))
jest.mock('@/api/dishIngredient', () => ({ queryIngredients: jest.fn() }))
jest.mock('@/api/dish', () => ({ queryDishes: jest.fn() }))
jest.mock('@/utils/calendar', () => ({ MealTypeName: {}, normalizeDeliveryDates: value => value }))
jest.mock('@/components/Calendar/MealScheduleCalendar.vue', () => ({ __esModule: true, default: { name: 'MealScheduleCalendar' }}))
jest.mock('@/components/Order/OrderForm.vue', () => ({
  __esModule: true,
  default: { name: 'OrderForm' },
  createFirstOrderDefaultForm: () => ({})
}))
jest.mock('@/views/customer/profile/CustomerDetailDialog.vue', () => ({ __esModule: true, default: { name: 'CustomerDetailDialog' }}))
jest.mock('@crud/crud', () => {
  const crud = jest.fn(() => ({}))
  crud.STATUS = { PREPARED: 0 }
  crud.HOOK = { beforeToAdd: 'beforeToAdd', beforeToCU: 'beforeToCU', beforeSubmit: 'beforeSubmit' }
  return {
    __esModule: true,
    default: crud,
    presenter: () => ({}),
    header: () => ({}),
    form: () => ({}),
    crud: () => ({})
  }
})
jest.mock('@crud/RR.operation', () => ({ __esModule: true, default: { name: 'RROperation' }}))
jest.mock('@crud/CRUD.operation', () => ({ __esModule: true, default: { name: 'CRUDOperation' }}))
jest.mock('vuex', () => ({ mapGetters: () => ({}) }))

const customerProfilePage = require('@/views/customer/profile/index.vue').default

describe('customer diet import preview', () => {
  test('refreshes profile diet choices after the dictionary changes', async() => {
    const api = require('@/api/customer/profile')
    api.getDietOptions.mockClear()
    api.getDietOptions.mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [{ type: 'INGREDIENT', id: 501, name: '秋葵' }] })
    const context = {
      dietOptionsPromise: null,
      dietOptions: [],
      $message: { error: jest.fn() },
      toDietSelection: customerProfilePage.methods.toDietSelection
    }
    const load = customerProfilePage.methods.loadDietOptions
    const pending = load.call(context)
    expect(load.call(context)).toBe(pending)
    await pending
    await load.call(context)
    expect(api.getDietOptions).toHaveBeenCalledTimes(2)
    expect(context.dietOptions[0].selectionKey).toBe('INGREDIENT:501')
  })
  test('allows confirmation when a same-name term will add every candidate', () => {
    const context = {
      importPreview: {
        structureValid: true,
        dictionaryHash: 'dictionary-hash',
        dietSheetPresent: true,
        importableCount: 1,
        drafts: [{
          importable: true,
          dietMatches: [{
            status: 'MULTI',
            selectedItems: [
              { type: 'DISH', id: 1, name: '海鲜' },
              { type: 'INGREDIENT', id: 2, name: '海鲜' },
              { type: 'DISH_TAG', id: 3, name: '海鲜' }
            ]
          }]
        }]
      },
      importFile: {},
      importDate: '2026-09-28',
      importLoading: false,
      importConfirmLoading: false,
      importResult: null
    }

    expect(customerProfilePage.computed.canConfirmImport.call(context)).toBe(true)
    expect(context.importPreview.drafts[0].dietMatches[0].selectedItems).toHaveLength(3)
  })
})

describe('monthly customer order continuation preview', () => {
  test('labels new, same and older month actions without confusing an old month with a balance update', () => {
    const text = customerProfilePage.methods.importActionText
    expect(text({ importable: true, importAction: 'UPDATE_MONTH' })).toBe('新月份续导')
    expect(text({ importable: true, importAction: 'UPDATE_SAME_MONTH' })).toBe('同月更新')
    expect(text({ importable: true, importAction: 'BACKFILL_MONTH' })).toBe('旧月份补录')
    expect(text({ importable: false, importAction: 'UPDATE_MONTH' })).toBe('跳过')
    expect(customerProfilePage.methods.importOrderStatusText({ afterOrderStatus: 4, paused: false })).toBe('暂停')
  })

  test('requires the order revision marker for a continuation and sends it on confirmation', async() => {
    const api = require('@/api/customer/profile')
    const context = {
      importPreview: {
        structureValid: true, importableCount: 1, fileHash: 'file-hash', dictionaryHash: 'dictionary-hash',
        drafts: [{ targetOrderId: 20, importable: true, afterRemainingCount: 25 }]
      },
      importFile: {}, importDate: '2026-10-20', importDietOnly: false,
      importLoading: false, importConfirmLoading: false, importResult: null,
      $confirm: jest.fn(() => Promise.resolve()),
      $message: { success: jest.fn(), error: jest.fn() },
      crud: { refresh: jest.fn() }
    }
    expect(customerProfilePage.computed.canConfirmImport.call(context)).toBe(false)
    context.importPreview.orderStateHash = 'order-state-hash'
    context.canConfirmImport = customerProfilePage.computed.canConfirmImport.call(context)
    expect(context.canConfirmImport).toBe(true)
    api.confirmCustomerImport.mockResolvedValue({ data: { updatedCount: 1, createdCount: 0, failedCount: 0 }})
    await customerProfilePage.methods.confirmCustomerImport.call(context)
    expect(api.confirmCustomerImport).toHaveBeenCalledWith(context.importFile, 'file-hash', 'dictionary-hash',
      '2026-10-20', false, 'order-state-hash')
    expect(context.crud.refresh).toHaveBeenCalledTimes(1)
  })
})
