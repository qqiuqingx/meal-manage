/* eslint-env jest */
jest.mock('@/api/customer/order', () => ({
  getOrder: jest.fn(),
  updateInline: jest.fn()
}))
jest.mock('@/api/customer/profile', () => ({ getDietOptions: jest.fn() }))
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
    beforeSubmit: 'beforeSubmit',
    beforeRefresh: 'beforeRefresh'
  }
  return {
    __esModule: true,
    default: crudFactory,
    presenter: () => ({ methods: { parseTime: (...args) => require('@/utils/index').parseTime(...args) }}),
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

const { mount, createLocalVue } = require('@vue/test-utils')
const ElementUI = require('element-ui')
const fs = require('fs')
const path = require('path')
const orderApi = require('@/api/customer/order')
const profileApi = require('@/api/customer/profile')
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
    loadOrders: jest.fn(() => Promise.resolve(true)),
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

// 使用页面真实模板和 Element UI 多选框，仅替换表格容器以提供测试订单行。
function mountDietPage(row) {
  const localVue = createLocalVue()
  localVue.use(ElementUI)
  return mount({
    ...customerOrderPage,
    created() {},
    mounted() {},
    activated() {},
    methods: { ...customerOrderPage.methods, loadOrders: jest.fn(() => Promise.resolve(true)) },
    data() {
      return Object.assign({}, customerOrderPage.data.call(this), {
        roles: ['customerOrder:edit'],
        query: {},
        form: {},
        crud: {
          props: { searchToggle: false },
          status: { cu: 0, add: 0 },
          page: { current: 1, size: 10, total: 1 },
          data: [row],
          selectionChangeHandler: jest.fn(),
          sizeChangeHandler: jest.fn(),
          pageChangeHandler: jest.fn(),
          refresh: jest.fn(() => Promise.resolve())
        },
        dietOptions: [
          { type: 'DISH', id: 2, name: '新菜' },
          { type: 'INGREDIENT', id: 3, name: '花生' }
        ]
      })
    }
  }, {
    localVue,
    sync: false,
    attachToDocument: true,
    mocks: { checkPer: () => false, $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() }},
    stubs: {
      transition: false,
      'transition-group': false,
      crudOperation: true,
      'el-pagination': true,
      'el-table': { render(h) { return h('div', this.$slots.default) } },
      'el-table-column': {
        props: ['columnKey'],
        render(h) {
          const isDiet = ['dishRequirements', 'dietaryRestrictions'].includes(this.columnKey)
          return h('div', { attrs: { 'data-field': this.columnKey }}, isDiet ? this.$scopedSlots.default({ row }) : [])
        }
      }
    }
  })
}

async function flushDietUi() {
  for (let index = 0; index < 8; index++) await Promise.resolve()
}

function clickDietOption(wrapper, cell, index = 0) {
  const select = wrapper.find(`${cell} .el-select`).vm
  select.popperElm.querySelectorAll('.el-select-dropdown__item')[index].click()
}

describe('CustomerOrder edit flow', () => {
  beforeEach(() => {
    orderApi.getOrder.mockReset()
    orderApi.updateInline.mockReset()
    profileApi.getDietOptions.mockReset()
    profileApi.getDietOptions.mockResolvedValue({ data: [
      { type: 'DISH', id: 2, name: '新菜' },
      { type: 'INGREDIENT', id: 3, name: '花生' }
    ] })
  })

  test('reloads diet choices on the next edit and shares concurrent pending requests', async() => {
    const oldOptions = [{ type: 'DISH', id: 2, name: '新菜' }]
    const newOptions = oldOptions.concat({ type: 'INGREDIENT', id: 501, name: '秋葵' })
    profileApi.getDietOptions.mockResolvedValueOnce({ data: oldOptions }).mockResolvedValueOnce({ data: newOptions })
    const vm = createVm()
    const row = { id: 99, status: 1, dietaryRestrictions: [] }
    const first = vm.loadInlineDietOptions()
    expect(vm.loadInlineDietOptions()).toBe(first)
    await first
    await vm.beginInlineDietEdit(row, 'dietaryRestrictions')
    expect(profileApi.getDietOptions).toHaveBeenCalledTimes(2)
    expect(vm.getInlineDietChoices(row, 'dietaryRestrictions').some(option => option.name === '秋葵')).toBe(true)
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

    expect(source).toContain('<el-table-column label="已排餐" prop="scheduledCount" width="74" align="center" class-name="compact-column" label-class-name="compact-column" />')
    expect(source).toContain('<el-table-column label="手机号" prop="phone" width="110" class-name="compact-column" label-class-name="compact-column">')
    expect(source).toContain("@click=\"beginInlineEdit(scope.row, 'phone')\"")
    expect(source).toContain('@click.native="beginInlineEdit(scope.row, addressInlineField(addr))"')
    expect(source).toContain("@keyup.enter.native=\"submitInlineDraft(scope.row, 'breakfastCount')\"")
    expect(source).toContain("v-if=\"isInlineEditing(scope.row, 'mainDishCount')\"")
    expect(source).toContain("@click=\"beginInlineEdit(scope.row, 'mainDishCount')\"")
    expect(source).toContain("@click=\"beginInlineEdit(scope.row, 'sideDishCount')\"")
    expect(source).toContain("@click=\"beginInlineEdit(scope.row, 'vegCount')\"")
    expect(source).toContain('@click="openCustomMenuDialog(scope.row)"')
    expect(source).toContain("{{ menuDialogRow.customMenuImage ? '替换菜单图片' : '上传菜单图片' }}")
    expect(source).toContain('<el-table-column column-key="dishRequirements" label="菜品特殊要求" min-width="240">')
    expect(source).toContain('<el-table-column column-key="dietaryRestrictions" label="禁忌食物" min-width="240">')
    expect(source).toContain('<CustomerDietCell :raw="scope.row.dishRequirementsRaw" :items="scope.row.dishRequirements" />')
    expect(source).toContain('<CustomerDietCell :raw="scope.row.dietaryRestrictionsRaw" :items="scope.row.dietaryRestrictions" />')
    expect(source).toContain("@click=\"beginInlineDietEdit(scope.row, 'dishRequirements')\"")
    expect(source).toContain("@click=\"beginInlineDietEdit(scope.row, 'dietaryRestrictions')\"")
    expect(source).toContain("@visible-change=\"!$event && saveInlineDietDraft(scope.row, 'dishRequirements')\"")
    expect(source).toContain("@keydown.esc.native.capture.stop=\"cancelInlineDietDraft(scope.row, 'dietaryRestrictions')\"")
    expect(source).not.toContain('编辑对象')
    expect(source).not.toContain('inline-diet-actions')
    expect(source).not.toContain('<el-popover placement="left" width="380" trigger="click">')
  })

  test('defaults to meal plan columns and lets users restore hidden order columns', async() => {
    const localVue = createLocalVue()
    localVue.use(ElementUI)
    const operation = jest.requireActual('@crud/CRUD.operation').default
    window.localStorage.setItem('customer-order-column-order-v1', JSON.stringify(['orderCode', 'customerName', 'phone']))
    window.localStorage.removeItem('customer-order-column-order-v2')
    const wrapper = mount({
      ...customerOrderPage,
      components: {
        ...customerOrderPage.components,
        crudOperation: {
          ...operation,
          data() { return { ...operation.data.call(this), crud: this.$parent.crud } }
        }
      },
      created() {
        this.columnSorter = null
        this.columnHeaderRow = null
        this.columnOrderLoaded = false
      },
      mounted() {
        this.crud.props.table = this.$refs.table
        this.$nextTick(() => this.initializeColumnOrder())
      },
      data() {
        return {
          ...customerOrderPage.data.call(this),
          roles: ['admin'],
          query: {},
          form: {},
          crud: {
            props: { searchToggle: false, table: null },
            optShow: {},
            status: { cu: 0, add: 0 },
            page: { current: 1, size: 10, total: 1 },
            data: [{ id: 1, status: 1, medicalRequirements: '少盐', postoperativeInfo: '4个月' }],
            updateProp: jest.fn(),
            getTable() { return this.props.table },
            selectionChangeHandler: jest.fn(),
            sizeChangeHandler: jest.fn(),
            pageChangeHandler: jest.fn()
          }
        }
      }
    }, {
      localVue,
      sync: false,
      mocks: { checkPer: () => true },
      stubs: { 'el-pagination': true }
    })

    try {
      await flushDietUi()
      const labels = () => wrapper.vm.$refs.table.store.states.columns.filter(column => column.type === 'default').map(column => column.label)
      const expected = [
        '手机号', '地址', '客户编号', '客户姓名', '特殊要求', '排餐模式', '餐次', '规格', '含汤',
        '早餐', '午晚', '合计', '核销', '已排餐', '剩余', '预计剩余', '状态', '基本情况',
        '成单时间', '术后天数', '菜品特殊要求', '过敏食物', '禁忌食物', '自定义菜单'
      ]
      expect(labels()).toEqual(expected.concat('操作'))
      expect(wrapper.find('.el-table__body').text()).toContain('少盐')
      expect(wrapper.find('.el-table__body').text()).toContain('4个月')

      const toolbar = wrapper.vm.$children.find(child => Array.isArray(child.tableColumns))
      const hiddenColumns = toolbar.tableColumns.filter(column => !column.visible)
      expect(hiddenColumns.map(column => column.label)).toEqual(['余额', '销售渠道', '订单期间', '定金', '总金额', '成交金额', '订单编号'])
      hiddenColumns.forEach(column => {
        column.visible = true
        toolbar.handleCheckedTableColumnsChange(column)
      })
      await flushDietUi()
      expect(labels()).toEqual(expected.concat(hiddenColumns.map(column => column.label), '操作'))
      const vm = wrapper.vm
      await wrapper.find('.fixed-column-select').setValue('3')
      await flushDietUi()
      const fixedKeys = () => vm.$refs.table.store.states.fixedColumns.map(column => column.type === 'selection' ? 'selection' : column.property || column.columnKey)
      expect(fixedKeys()).toEqual(['selection', 'phone', 'addresses', 'customerCode'])
      expect(window.localStorage.getItem('customer-order-fixed-column-count')).toBe('3')
      expect(vm.readFixedColumnCount()).toBe(3)
      expect(wrapper.find('.el-table__fixed').exists()).toBe(true)
      const phoneColumn = toolbar.tableColumns.find(column => column.key === 'phone')
      phoneColumn.visible = false
      toolbar.handleCheckedTableColumnsChange(phoneColumn)
      await flushDietUi()
      expect(fixedKeys()).toEqual(['selection', 'addresses', 'customerCode', 'customerName'])
      vm.resetColumnOrder()
      await flushDietUi()
      expect(fixedKeys()).toEqual(['selection', 'addresses', 'customerCode', 'customerName'])
      await wrapper.find('.fixed-column-select').setValue('0')
      await flushDietUi()
      expect(fixedKeys()).toEqual([])
      expect(wrapper.find('.el-table__fixed').exists()).toBe(false)
    } finally {
      wrapper.destroy()
      window.localStorage.removeItem('customer-order-column-order-v1')
      window.localStorage.removeItem('customer-order-column-order-v2')
      window.localStorage.removeItem('customer-order-fixed-column-count')
    }
  })

  test('loads active dictionary options and keeps selected historical references in the draft', async() => {
    profileApi.getDietOptions.mockResolvedValue({ data: [{ type: 'DISH', id: 2, name: '新菜' }] })
    const vm = createVm()
    const row = {
      id: 28,
      status: 1,
      dishRequirements: [{ type: 'INGREDIENT_TAG', id: 9, name: '旧标签名' }]
    }

    await vm.beginInlineDietEdit(row, 'dishRequirements')

    expect(profileApi.getDietOptions).toHaveBeenCalledTimes(1)
    expect(vm.isInlineEditing(row, 'dishRequirements')).toBe(true)
    expect(vm.getInlineDietDraft(row, 'dishRequirements')).toEqual(['INGREDIENT_TAG:9'])
    expect(vm.getInlineDietChoices(row, 'dishRequirements')).toEqual([
      { type: 'DISH', id: 2, name: '新菜', selectionKey: 'DISH:2', historical: false },
      { type: 'INGREDIENT_TAG', id: 9, name: '旧标签名', selectionKey: 'INGREDIENT_TAG:9', historical: true }
    ])
  })

  test('saves one complete diet selection array with its entry snapshot as expectedValue', async() => {
    profileApi.getDietOptions.mockResolvedValue({ data: [{ type: 'DISH', id: 2, name: '服务端名称' }] })
    orderApi.updateInline.mockResolvedValue({})
    const vm = createVm({
      dietOptions: [{ type: 'DISH', id: 2, name: '服务端名称' }]
    })
    const oldItems = [{ type: 'INGREDIENT_TAG', id: 9, name: '历史名称' }]
    const row = { id: 29, status: 1, dishRequirements: oldItems }
    await vm.beginInlineDietEdit(row, 'dishRequirements')
    vm.setInlineDietDraft(row, 'dishRequirements', ['INGREDIENT_TAG:9', 'DISH:2'])

    await vm.saveInlineDietDraft(row, 'dishRequirements')

    expect(orderApi.updateInline).toHaveBeenCalledWith(29, {
      field: 'dishRequirements',
      value: [
        { type: 'INGREDIENT_TAG', id: 9, name: '历史名称' },
        { type: 'DISH', id: 2, name: '服务端名称' }
      ],
      expectedValue: oldItems
    })
    expect(vm.activeInlineKey).toBeNull()
    expect(vm.inlineDietDrafts['29:dishRequirements']).toBeUndefined()
    expect(vm.loadOrders).toHaveBeenCalledWith(true, true)
  })

  test.each(['dishRequirements', 'dietaryRestrictions'])('clicking %s opens the multiselect and closing it saves all changes once', async field => {
    orderApi.updateInline.mockResolvedValue({})
    const row = { id: 31, status: 1, dishRequirements: [], dietaryRestrictions: [] }
    const wrapper = mountDietPage(row)
    const cell = `[data-field="${field}"]`
    try {
      wrapper.find(`${cell} .inline-diet-display`).trigger('click')
      await flushDietUi()
      const select = wrapper.find(`${cell} .el-select`).vm
      expect(select.visible).toBe(true)
      clickDietOption(wrapper, cell)
      await flushDietUi()
      expect(select.visible).toBe(true)
      expect(orderApi.updateInline).not.toHaveBeenCalled()
      clickDietOption(wrapper, cell, 1)
      await flushDietUi()
      if (field === 'dishRequirements') {
        wrapper.find(`${cell} .el-select__input`).trigger('keydown', { keyCode: 9, key: 'Tab' })
      } else {
        document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
        document.body.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
      }
      await flushDietUi()
      expect(orderApi.updateInline).toHaveBeenCalledTimes(1)
      expect(orderApi.updateInline).toHaveBeenCalledWith(31, {
        field,
        value: [{ type: 'DISH', id: 2, name: '新菜' }, { type: 'INGREDIENT', id: 3, name: '花生' }],
        expectedValue: []
      })
      expect(wrapper.find(`${cell} .inline-diet-editor`).exists()).toBe(false)
    } finally {
      wrapper.destroy()
    }
  })

  test('Esc cancels changes before the multiselect close event can save them', async() => {
    const wrapper = mountDietPage({ id: 32, status: 1, dishRequirements: [], dietaryRestrictions: [] })
    const cell = '[data-field="dietaryRestrictions"]'
    try {
      wrapper.find(`${cell} .inline-diet-display`).trigger('click')
      await flushDietUi()
      clickDietOption(wrapper, cell)
      await flushDietUi()
      wrapper.find(`${cell} .el-select__input`).trigger('keydown', { keyCode: 27, key: 'Escape' })
      await flushDietUi()
      expect(orderApi.updateInline).not.toHaveBeenCalled()
      expect(wrapper.find(`${cell} .inline-diet-editor`).exists()).toBe(false)
      expect(wrapper.vm.inlineDietDrafts['32:dietaryRestrictions']).toBeUndefined()
    } finally {
      wrapper.destroy()
    }
  })

  test('closing an unchanged diet selection skips updates even after reordering selections', async() => {
    const vm = createVm()
    const row = {
      id: 33,
      status: 1,
      dishRequirements: [{ type: 'DISH', id: 2, name: '新菜' }, { type: 'INGREDIENT', id: 3, name: '花生' }]
    }
    await vm.beginInlineDietEdit(row, 'dishRequirements')
    vm.setInlineDietDraft(row, 'dishRequirements', ['INGREDIENT:3', 'DISH:2'])

    expect(await vm.saveInlineDietDraft(row, 'dishRequirements')).toBe(true)
    expect(orderApi.updateInline).not.toHaveBeenCalled()
    expect(vm.activeInlineKey).toBeNull()
  })

  test('removing the last diet object submits an empty array with the original snapshot', async() => {
    orderApi.updateInline.mockResolvedValue({})
    const vm = createVm()
    const expectedValue = [{ type: 'INGREDIENT', id: 3, name: '花生' }]
    const row = { id: 34, status: 1, dietaryRestrictions: expectedValue }
    await vm.beginInlineDietEdit(row, 'dietaryRestrictions')
    vm.setInlineDietDraft(row, 'dietaryRestrictions', [])

    await vm.saveInlineDietDraft(row, 'dietaryRestrictions')

    expect(orderApi.updateInline).toHaveBeenCalledWith(34, { field: 'dietaryRestrictions', value: [], expectedValue })
    expect(vm.inlineDietDrafts['34:dietaryRestrictions']).toBeUndefined()
  })

  test('cancelling a diet selection draft does not send an update', async() => {
    const vm = createVm({ dietOptions: [] })
    const row = { id: 30, status: 1, dietaryRestrictions: [] }
    await vm.beginInlineDietEdit(row, 'dietaryRestrictions')
    vm.setInlineDietDraft(row, 'dietaryRestrictions', ['DISH:1'])

    vm.cancelInlineDietDraft(row, 'dietaryRestrictions')

    expect(vm.activeInlineKey).toBeNull()
    expect(orderApi.updateInline).not.toHaveBeenCalled()
    expect(vm.inlineDietDrafts['30:dietaryRestrictions']).toBeUndefined()
  })

  test('clicking one value opens only its editor and focuses the existing value', async() => {
    const vm = createVm()
    const row = { id: 20, status: 1, mainDishCount: 2, sideDishCount: 1 }
    const cell = document.createElement('div')
    const hidden = document.createElement('td')
    hidden.className = 'is-hidden'
    const hiddenInput = document.createElement('input')
    hiddenInput.name = vm.inlineInputName(row, 'mainDishCount')
    hidden.appendChild(hiddenInput)
    cell.appendChild(hidden)
    const input = document.createElement('input')
    input.name = vm.inlineInputName(row, 'mainDishCount')
    cell.appendChild(input)
    vm.$refs.table.$el = cell
    const select = jest.spyOn(input, 'select')
    const focus = jest.spyOn(input, 'focus')
    const hiddenFocus = jest.spyOn(hiddenInput, 'focus')

    vm.beginInlineEdit(row, 'mainDishCount')
    await Promise.resolve()

    expect(vm.isInlineEditing(row, 'mainDishCount')).toBe(true)
    expect(vm.isInlineEditing(row, 'sideDishCount')).toBe(false)
    expect(vm.getInlineDraft(row, 'mainDishCount')).toBe(2)
    expect(focus).toHaveBeenCalledTimes(1)
    expect(select).toHaveBeenCalledTimes(1)
    expect(hiddenFocus).not.toHaveBeenCalled()
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
    expect(vm.loadOrders).toHaveBeenCalledWith(true, true)
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

  test('keeps cancelled and refunded orders and rows without permission read-only', () => {
    const vm = createVm()
    expect(vm.isInlineEditable({ id: 16, status: 0 })).toBe(false)
    expect(vm.isInlineEditable({ id: 16, status: 3 })).toBe(false)
    vm.roles = ['customerOrder:list']
    expect(vm.isInlineEditable({ id: 17, status: 1 })).toBe(false)
  })

  test('allows completed order fields to be edited while keeping status read-only', async() => {
    orderApi.updateInline.mockResolvedValue({})
    const vm = createVm()
    const row = { id: 162, status: 2, mainDishCount: 1 }
    expect(vm.isInlineEditable(row)).toBe(true)
    vm.beginInlineEdit(row, 'mainDishCount')
    vm.setInlineDraft(row, 'mainDishCount', '2')
    await vm.submitInlineDraft(row, 'mainDishCount')
    expect(orderApi.updateInline).toHaveBeenCalledWith(162, {
      field: 'mainDishCount', value: 2, expectedValue: 1
    })
    expect(row.status).toBe(2)
    expect(vm.isInlineEditable(row, 'status')).toBe(false)
    vm.beginInlineEdit(row, 'status')
    expect(vm.activeInlineKey).toBeNull()
    expect(await vm.saveInlineValue(row, 'status', 1, 2)).toBe(false)
    expect(orderApi.updateInline).toHaveBeenCalledTimes(1)
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
    expect(vm.loadOrders).toHaveBeenCalledWith(true, true)
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
