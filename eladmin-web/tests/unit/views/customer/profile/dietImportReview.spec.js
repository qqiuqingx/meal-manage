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
