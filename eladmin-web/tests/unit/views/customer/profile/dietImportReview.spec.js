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
  del: jest.fn(),
  saveMealScheduleAdjustments: jest.fn()
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

function createReviewContext() {
  const first = {
    sourceKey: 'DIET:4:4:0',
    side: 'DISH_REQUIREMENTS',
    status: 'AMBIGUOUS',
    candidates: [
      { type: 'DISH', id: 1, name: '香菜' },
      { type: 'INGREDIENT', id: 2, name: '香菜' }
    ]
  }
  const second = {
    sourceKey: 'DIET:8:5:0',
    side: 'DIETARY_RESTRICTIONS',
    status: 'AMBIGUOUS',
    candidates: [
      { type: 'DISH_TAG', id: 3, name: '海鲜' },
      { type: 'INGREDIENT_TAG', id: 4, name: '海鲜' }
    ]
  }
  return {
    importPreview: {
      structureValid: true,
      dictionaryHash: 'dictionary-hash',
      dietSheetPresent: true,
      importableCount: 2,
      drafts: [
        { dietMatchesNeedReview: true, dietMatches: [first], errors: ['有 1 个饮食词项存在多个候选，请逐项选择或明确跳过'] },
        { dietMatchesNeedReview: true, dietMatches: [second], errors: ['有 1 个饮食词项存在多个候选，请逐项选择或明确跳过'] }
      ]
    },
    dietSelectionValues: {},
    importFile: {},
    importDate: '2026-09-28',
    importLoading: false,
    importConfirmLoading: false,
    importResult: null,
    dietMatchOptionValue: customerProfilePage.methods.dietMatchOptionValue,
    $set(target, key, value) { target[key] = value }
  }
}

describe('customer diet import review', () => {
  test('counts unresolved ambiguity across drafts, including rows outside the current filter', () => {
    const context = createReviewContext()

    expect(customerProfilePage.computed.unresolvedDietMatchCount.call(context)).toBe(2)
    customerProfilePage.methods.setDietMatchSelection.call(context, context.importPreview.drafts[0].dietMatches[0], 'SKIP')

    expect(customerProfilePage.computed.unresolvedDietMatchCount.call(context)).toBe(1)
    expect(customerProfilePage.computed.canConfirmImport.call(context)).toBe(false)
  })

  test('serializes explicit skip and typed candidate choices by stable source key', () => {
    const context = createReviewContext()
    context.dietSelectionValues['DIET:4:4:0'] = 'SKIP'
    context.dietSelectionValues['DIET:8:5:0'] = 'INGREDIENT_TAG:4'

    expect(customerProfilePage.methods.serializeDietSelections.call(context)).toEqual([
      { sourceKey: 'DIET:4:4:0', action: 'SKIP' },
      { sourceKey: 'DIET:8:5:0', action: 'SELECT', type: 'INGREDIENT_TAG', id: 4 }
    ])
    context.unresolvedDietMatchCount = customerProfilePage.computed.unresolvedDietMatchCount.call(context)
    expect(context.unresolvedDietMatchCount).toBe(0)
    expect(customerProfilePage.computed.canConfirmImport.call(context)).toBe(true)
  })
})
