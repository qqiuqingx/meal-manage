/* eslint-env jest */
jest.mock('@/api/customer/order', () => ({
  getOrder: jest.fn(),
  updateInline: jest.fn()
}))
jest.mock('@/api/system/dictDetail', () => ({ get: jest.fn() }))
jest.mock('@/api/mealRefund', () => ({ refundMeal: jest.fn() }))
jest.mock('@/components/Order/OrderForm.vue', () => ({
  __esModule: true,
  default: { name: 'OrderForm' },
  createOrderDefaultForm: () => ({
    id: null,
    customerId: null,
    scheduleMode: 'SCHEDULE',
    startMealType: 'BREAKFAST',
    deliveryDatesWithMealTypes: [],
    deliveryDates: []
  })
}))
jest.mock('@crud/crud', () => {
  const crudFactory = jest.fn(() => ({}))
  crudFactory.STATUS = { PREPARED: 0 }
  crudFactory.HOOK = {
    beforeToCU: 'beforeToCU',
    beforeToAdd: 'beforeToAdd',
    beforeSubmit: 'beforeSubmit'
  }
  return {
    __esModule: true,
    default: crudFactory,
    presenter: () => ({}),
    header: () => ({}),
    form: () => ({}),
    crud: () => ({})
  }
})
jest.mock('@crud/RR.operation', () => ({ __esModule: true, default: {}}))
jest.mock('@crud/CRUD.operation', () => ({ __esModule: true, default: {}}))
jest.mock('@/utils/index', () => ({ parseTime: value => value }))
jest.mock('@/utils/auth', () => ({ getToken: () => 'test-token' }))
jest.mock('vuex', () => ({ mapGetters: () => ({}) }))

const fs = require('fs')
const path = require('path')
const orderApi = require('@/api/customer/order')
const customerOrderPage = require('@/views/customer/order/index.vue').default

function beforeToCU(ctx) {
  return customerOrderPage.methods.beforeToCU.call(ctx)
}

async function handleEdit(ctx, row) {
  return customerOrderPage.methods.handleEdit.call(ctx, row)
}

function createVm(overrides = {}) {
  return Object.assign({}, customerOrderPage.data(), customerOrderPage.methods, {
    roles: ['customerOrder:edit'],
    crud: { refresh: jest.fn(() => Promise.resolve()) },
    $refs: {
      table: {
        bodyWrapper: { scrollLeft: 24 },
        headerWrapper: { scrollLeft: 24 }
      }
    },
    $set(target, key, value) {
      target[key] = value
    },
    $delete(target, key) {
      delete target[key]
    },
    $message: {
      success: jest.fn(),
      warning: jest.fn(),
      error: jest.fn()
    },
    $nextTick(callback) {
      return Promise.resolve().then(() => callback && callback())
    }
  }, overrides)
}

describe('CustomerOrder edit flow', () => {
  beforeEach(() => {
    orderApi.getOrder.mockReset()
    orderApi.updateInline.mockReset()
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
    expect(ctx.form.startMealType).toBe('BREAKFAST')
    expect(ctx.form.deliveryDatesWithMealTypes).toEqual([])
    expect(ctx.form.deliveryDates).toBe('[{\"date\":\"2026-04-24\",\"mealTypes\":[\"LUNCH\",\"DINNER\"]}]')
  })

  test('order list shows scheduled count column', () => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../../../src/views/customer/order/index.vue'), 'utf8')

    expect(source).toContain('<el-table-column label="已排餐" prop="scheduledCount" width="80" align="center" />')
    expect(source).toContain('<el-table-column label="手机号" prop="phone" width="145">')
    expect(source).toContain("@click=\"beginInlineEdit(scope.row, 'phone')\"")
    expect(source).toContain('@click.native="beginInlineEdit(scope.row, addressInlineField(addr))"')
    expect(source).toContain("@keyup.enter.native=\"submitInlineDraft(scope.row, 'breakfastCount')\"")
    expect(source).toContain("v-if=\"isInlineEditing(scope.row, 'mainDishCount')\"")
    expect(source).toContain("@click=\"beginInlineEdit(scope.row, 'mainDishCount')\"")
    expect(source).toContain("@click=\"beginInlineEdit(scope.row, 'sideDishCount')\"")
    expect(source).toContain("@click=\"beginInlineEdit(scope.row, 'vegCount')\"")
    expect(source).toContain('@click="openCustomMenuDialog(scope.row)"')
    expect(source).toContain("{{ menuDialogRow.customMenuImage ? '替换菜单图片' : '上传菜单图片' }}")
    expect(source).toContain('<el-table-column column-key="dietInfo" label="饮食资料" width="100" align="center">')
    expect(source).toContain('<CustomerDietInfo :customer="scope.row" />')
    expect(source).toContain('<CustomerDietInfo :customer="form" />')
  })

  test('clicking one value opens only its editor and focuses the existing value', async() => {
    const vm = createVm()
    const row = { id: 20, status: 1, mainDishCount: 2, sideDishCount: 1 }
    const select = jest.fn()
    const focus = jest.fn()
    vm.$refs[vm.inlineInputRef(row, 'mainDishCount')] = {
      focus,
      $el: { querySelector: () => ({ select }) }
    }

    vm.beginInlineEdit(row, 'mainDishCount')
    await Promise.resolve()

    expect(vm.isInlineEditing(row, 'mainDishCount')).toBe(true)
    expect(vm.isInlineEditing(row, 'sideDishCount')).toBe(false)
    expect(vm.getInlineDraft(row, 'mainDishCount')).toBe(2)
    expect(focus).toHaveBeenCalledTimes(1)
    expect(select).toHaveBeenCalledTimes(1)
  })

  test('submitting one specification value keeps the other specification values unchanged', async() => {
    orderApi.updateInline.mockResolvedValue({})
    const vm = createVm()
    const row = { id: 22, status: 1, mainDishCount: 2, sideDishCount: 1, vegCount: 3 }
    vm.beginInlineEdit(row, 'sideDishCount')
    vm.setInlineDraft(row, 'sideDishCount', '4')

    await vm.submitInlineDraft(row, 'sideDishCount')

    expect(orderApi.updateInline).toHaveBeenCalledWith(22, {
      field: 'sideDishCount',
      value: 4,
      expectedValue: 1
    })
    expect(vm.activeInlineKey).toBeNull()
    expect(row.mainDishCount).toBe(2)
    expect(row.vegCount).toBe(3)
  })

  test('saves a numeric field with its row value as the expected old value and refreshes the page', async() => {
    orderApi.updateInline.mockResolvedValue({})
    const vm = createVm()
    const row = { id: 13, status: 1, breakfastCount: 2 }

    const saved = await vm.saveInlineValue(row, 'breakfastCount', 3, row.breakfastCount)

    expect(saved).toBe(true)
    expect(orderApi.updateInline).toHaveBeenCalledWith(13, {
      field: 'breakfastCount',
      value: 3,
      expectedValue: 2
    })
    expect(vm.crud.refresh).toHaveBeenCalledTimes(1)
    expect(vm.$refs.table.bodyWrapper.scrollLeft).toBe(24)
    expect(vm.$message.success).toHaveBeenCalledWith('保存成功')
  })

  test('commits blank special requirements as null when the text input blurs', async() => {
    orderApi.updateInline.mockResolvedValue({})
    const vm = createVm()
    const row = { id: 14, status: 4, specialRequirements: '少盐' }
    vm.beginInlineEdit(row, 'specialRequirements')
    vm.setInlineDraft(row, 'specialRequirements', '   ')

    await vm.submitInlineDraft(row, 'specialRequirements')

    expect(orderApi.updateInline).toHaveBeenCalledWith(14, {
      field: 'specialRequirements',
      value: null,
      expectedValue: '少盐'
    })
  })

  test('updates a customer phone from the clicked value', async() => {
    orderApi.updateInline.mockResolvedValue({})
    const vm = createVm()
    const row = { id: 26, status: 1, phone: '13800000000' }
    vm.beginInlineEdit(row, 'phone')
    vm.setInlineDraft(row, 'phone', '13900000000')

    await vm.submitInlineDraft(row, 'phone')

    expect(orderApi.updateInline).toHaveBeenCalledWith(26, {
      field: 'phone',
      value: '13900000000',
      expectedValue: '13800000000'
    })
  })

  test('updates only the clicked address slot with its own expected value', async() => {
    orderApi.updateInline.mockResolvedValue({})
    const vm = createVm()
    const row = {
      id: 27,
      status: 1,
      addresses: [
        { addressType: 'DEFAULT', type: '默认', detail: '旧默认地址' },
        { addressType: 'WEEKEND', type: '周末', detail: '旧周末地址' }
      ]
    }
    const field = vm.addressInlineField(row.addresses[1])
    vm.beginInlineEdit(row, field)
    vm.setInlineDraft(row, field, '新周末地址')

    await vm.submitInlineDraft(row, field)

    expect(orderApi.updateInline).toHaveBeenCalledWith(27, {
      field: 'addressDetail:WEEKEND',
      value: '新周末地址',
      expectedValue: '旧周末地址'
    })
    expect(row.addresses[0].detail).toBe('旧默认地址')
  })

  test('rejects an invalid phone or empty address before sending a request', async() => {
    const vm = createVm()
    const row = { id: 28, status: 1, phone: '13800000000', addresses: [{ addressType: 'DEFAULT', detail: '旧地址' }] }
    vm.beginInlineEdit(row, 'phone')
    vm.setInlineDraft(row, 'phone', '123')
    await vm.submitInlineDraft(row, 'phone')
    vm.beginInlineEdit(row, 'addressDetail:DEFAULT')
    vm.setInlineDraft(row, 'addressDetail:DEFAULT', '   ')
    await vm.submitInlineDraft(row, 'addressDetail:DEFAULT')

    expect(orderApi.updateInline).not.toHaveBeenCalled()
    expect(vm.$message.warning).toHaveBeenCalledWith('手机号格式不正确')
    expect(vm.$message.warning).toHaveBeenCalledWith('地址不能为空且不能超过 200 个字符')
  })

  test('saves selection values as soon as the user changes the selector', async() => {
    orderApi.updateInline.mockResolvedValue({})
    const vm = createVm()
    const row = { id: 141, status: 1, soupCount: 0 }

    await vm.saveInlineSelection(row, 'soupCount', 1)

    expect(orderApi.updateInline).toHaveBeenCalledWith(141, {
      field: 'soupCount',
      value: 1,
      expectedValue: 0
    })
  })

  test('closing the allergy editor discards an unfinished tag', () => {
    const vm = createVm()
    const row = { id: 143, status: 1, allergyTags: [] }
    vm.beginInlineEdit(row, 'allergyTags')
    vm.setAllergyDraft(row, '花生')

    vm.cancelInlineDraft(row, 'allergyTags')

    expect(vm.isInlineEditing(row, 'allergyTags')).toBe(false)
    expect(vm.getAllergyDraft(row)).toBe('')
  })

  test('rejects a fractional meal count before sending a request', async() => {
    const vm = createVm()
    const row = { id: 15, status: 1, breakfastCount: 2 }
    vm.beginInlineEdit(row, 'breakfastCount')
    vm.setInlineDraft(row, 'breakfastCount', '2.5')

    await vm.submitInlineDraft(row, 'breakfastCount')

    expect(orderApi.updateInline).not.toHaveBeenCalled()
    expect(vm.$message.warning).toHaveBeenCalledWith('餐数必须是大于或等于 0 的整数')
    expect(vm.activeInlineKey).toBeNull()
  })

  test('escape closes the chosen editor without saving its draft on blur', async() => {
    const vm = createVm()
    const row = { id: 21, status: 1, mainDishCount: 2 }
    vm.beginInlineEdit(row, 'mainDishCount')
    vm.setInlineDraft(row, 'mainDishCount', '3')

    vm.cancelInlineDraft(row, 'mainDishCount')
    await vm.submitInlineDraft(row, 'mainDishCount')

    expect(vm.activeInlineKey).toBeNull()
    expect(vm.inlineDrafts['21:mainDishCount']).toBeUndefined()
    expect(orderApi.updateInline).not.toHaveBeenCalled()
  })

  test('shows terminal orders and rows without edit permission as read-only', () => {
    const vm = createVm()
    expect(vm.isInlineEditable({ id: 16, status: 2 })).toBe(false)
    vm.roles = ['customerOrder:list']
    expect(vm.isInlineEditable({ id: 17, status: 1 })).toBe(false)
  })

  test('refreshes the row and reports stale-value conflicts without keeping a draft', async() => {
    const error = new Error('conflict')
    error.response = { status: 409 }
    orderApi.updateInline.mockRejectedValue(error)
    const vm = createVm()
    const row = { id: 18, status: 1, mainDishCount: 1 }
    vm.setInlineDraft(row, 'mainDishCount', 2)

    const saved = await vm.saveInlineValue(row, 'mainDishCount', 2, 1)

    expect(saved).toBe(false)
    expect(vm.$message.warning).toHaveBeenCalledWith('数据已被其他操作修改，已刷新最新值')
    expect(vm.crud.refresh).toHaveBeenCalledTimes(1)
    expect(vm.inlineDrafts['18:mainDishCount']).toBeUndefined()
    expect(row.mainDishCount).toBe(1)
  })

  test('restores the stored value when the update request fails', async() => {
    orderApi.updateInline.mockRejectedValue(new Error('validation failed'))
    const vm = createVm()
    const row = { id: 181, status: 1, vegCount: 1 }
    vm.setInlineDraft(row, 'vegCount', 2)

    const saved = await vm.saveInlineValue(row, 'vegCount', 2, 1)

    expect(saved).toBe(false)
    expect(vm.$message.error).toHaveBeenCalledWith('validation failed')
    expect(vm.inlineDrafts['181:vegCount']).toBeUndefined()
    expect(row.vegCount).toBe(1)
  })

  test('reuses the existing upload response format and patches the image path', async() => {
    orderApi.updateInline.mockResolvedValue({})
    const vm = createVm()
    const row = { id: 19, status: 1, customMenuImage: null }
    vm.openCustomMenuDialog(row)
    const beforeUpload = vm.getCustomMenuBeforeUpload(row)

    expect(vm.menuDialogVisible).toBe(true)
    expect(beforeUpload({ type: 'image/jpeg', size: 1024 })).toBe(true)
    await vm.getCustomMenuUploadSuccess(row)({ type: 'image', realName: 'menu-019.jpg' })

    expect(orderApi.updateInline).toHaveBeenCalledWith(19, {
      field: 'customMenuImage',
      value: '/file/image/menu-019.jpg',
      expectedValue: null
    })
    expect(vm.uploadingRows[19]).toBeUndefined()
    expect(vm.menuDialogVisible).toBe(false)
  })

  test('keeps menu preview available without edit permission and hides empty menu', () => {
    const vm = createVm({ roles: ['customerOrder:list'] })
    vm.openCustomMenuDialog({ id: 23, status: 1, customMenuImage: null })
    expect(vm.menuDialogVisible).toBe(false)

    const row = { id: 24, status: 1, customMenuImage: '/file/image/menu.jpg' }
    vm.openCustomMenuDialog(row)
    expect(vm.menuDialogRow).toBe(row)
    expect(vm.menuDialogVisible).toBe(true)
  })

  test('deletes the menu image through the existing inline update request', async() => {
    orderApi.updateInline.mockResolvedValue({})
    const vm = createVm()
    const row = { id: 25, status: 1, customMenuImage: '/file/image/menu.jpg' }
    vm.openCustomMenuDialog(row)

    await vm.removeCustomMenuImage()

    expect(orderApi.updateInline).toHaveBeenCalledWith(25, {
      field: 'customMenuImage',
      value: null,
      expectedValue: '/file/image/menu.jpg'
    })
    expect(vm.menuDialogVisible).toBe(false)
  })
})
