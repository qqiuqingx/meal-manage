jest.mock('@/api/dish', () => ({
  addDish: jest.fn(), editDish: jest.fn(), queryPackages: jest.fn(),
  getDish: jest.fn(), recognizeIngredients: jest.fn()
}))
jest.mock('@/api/dishIngredient', () => ({ queryIngredients: jest.fn() }))
jest.mock('@/api/dishTag', () => ({ queryDishTags: jest.fn() }))

import Vue from 'vue'
import ElementUI from 'element-ui'
import { createLocalVue, mount } from '@vue/test-utils'
import { addDish, editDish, getDish, queryPackages, recognizeIngredients } from '@/api/dish'
import DishForm from '@/views/meal/dish/dish'

const localVue = createLocalVue()
localVue.use(ElementUI)
function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}
async function flush() {
  for (let i = 0; i < 10; i++) await Promise.resolve()
  await Vue.nextTick()
}
const row = (id = 1, name = '胡萝卜') => ({
  ingredientId: id, ingredientName: name, unit: 'g', quantity: null, remark: ''
})
const dish = (id = 9, cookingMethod = '胡萝卜切丁') => ({
  id, name: '旧菜品', cookingMethod, ingredientList: [row()], tagIds: [], tags: []
})
let wrapper
let message
function createForm() {
  message = { success: jest.fn(), warning: jest.fn(), error: jest.fn() }
  wrapper = mount(DishForm, { localVue, stubs: { DishTagEditor: { render: h => h('div'), methods: { resetState() {} }}, ElSelect: true }, mocks: { $message: message }})
  return wrapper.vm
}
async function openNew() {
  const vm = createForm()
  vm.handleAdd()
  vm.form.name = '测试菜品'
  await flush()
  return vm
}
async function input(text) {
  wrapper.find('textarea[placeholder="输入菜品的具体制作流程..."]').setValue(text)
  await flush()
}
async function recognizeAfterDebounce() {
  jest.advanceTimersByTime(500)
  await flush()
}

describe('dish ingredient recognition through the mounted shared form', () => {
  beforeEach(() => {
    jest.resetAllMocks()
    jest.useFakeTimers()
    queryPackages.mockResolvedValue([])
    recognizeIngredients.mockResolvedValue([])
    addDish.mockResolvedValue({})
    editDish.mockResolvedValue({})
  })
  afterEach(() => {
    if (wrapper) wrapper.destroy()
    wrapper = null
    jest.clearAllTimers()
    jest.useRealTimers()
  })

  test('input and pasted text debounce for 500ms and only append unique IDs', async() => {
    const vm = await openNew()
    recognizeIngredients.mockResolvedValue([row(), row(), row(2, '生姜')])
    await input('胡萝卜')
    jest.advanceTimersByTime(499)
    expect(recognizeIngredients).not.toHaveBeenCalled()
    await input('胡萝卜切丁，生姜炒熟')
    jest.advanceTimersByTime(499)
    expect(recognizeIngredients).not.toHaveBeenCalled()
    jest.advanceTimersByTime(1)
    await flush()
    expect(recognizeIngredients).toHaveBeenCalledTimes(1)
    expect(recognizeIngredients).toHaveBeenCalledWith({ cookingMethod: '胡萝卜切丁，生姜炒熟' })
    expect(vm.form.ingredientList).toEqual([row(), row(2, '生姜')])
    expect(addDish).not.toHaveBeenCalled()
    expect(editDish).not.toHaveBeenCalled()
  })

  test('opening history and changing other fields before saving makes no recognition calls', async() => {
    getDish.mockResolvedValue(dish())
    const vm = createForm()
    await vm.handleUpdate({ id: 9 })
    await flush()
    vm.form.name = '仅改名称'
    await vm.submitForm()
    expect(recognizeIngredients).not.toHaveBeenCalled()
    expect(editDish).toHaveBeenCalledWith(expect.objectContaining({ name: '仅改名称', ingredientList: [row()] }))
  })

  test('saving an unchanged historical process above the recognition limit preserves existing CRUD behavior', async() => {
    const original = '姜'.repeat(10001)
    getDish.mockResolvedValue(dish(9, original))
    const vm = createForm()
    await vm.handleUpdate({ id: 9 })
    await flush()
    vm.form.name = '只改旧菜品名称'
    await vm.submitForm()
    expect(recognizeIngredients).not.toHaveBeenCalled()
    expect(editDish).toHaveBeenCalledWith(expect.objectContaining({ cookingMethod: original }))
  })

  test('recognition keeps existing row identity, quantity, unit and remarks', async() => {
    const vm = await openNew()
    const existing = { ...row(), quantity: 35, unit: '个', remark: '切丁少盐' }
    vm.form.ingredientList.push(existing)
    recognizeIngredients.mockResolvedValue([row(), row(2, '生姜')])
    await input('胡萝卜生姜')
    await recognizeAfterDebounce()
    expect(vm.form.ingredientList[0]).toBe(existing)
    expect(existing).toEqual({ ...row(), quantity: 35, unit: '个', remark: '切丁少盐' })
    expect(vm.form.ingredientList[1]).toEqual(row(2, '生姜'))
  })

  test('real Element UI quantity input displays blank and preserves null in the saved payload', async() => {
    const vm = await openNew()
    recognizeIngredients.mockResolvedValue([row()])
    await input('胡萝卜切丁')
    await recognizeAfterDebounce()
    expect(wrapper.find('.quantity-input input').element.value).toBe('')
    expect(vm.form.ingredientList[0].quantity).toBeNull()
    await vm.submitForm()
    expect(addDish.mock.calls[0][0].ingredientList[0].quantity).toBeNull()
  })

  test('manual quantity edits accept real zero and clearing returns null', async() => {
    const vm = await openNew()
    recognizeIngredients.mockResolvedValue([row()])
    await input('胡萝卜')
    await recognizeAfterDebounce()
    const numberInput = wrapper.find('.quantity-input input')
    numberInput.setValue('0')
    numberInput.trigger('change')
    await flush()
    expect(vm.form.ingredientList[0].quantity).toBe(0)
    numberInput.setValue('')
    numberInput.trigger('change')
    await flush()
    expect(vm.form.ingredientList[0].quantity).toBeNull()
  })

  test('manual deletion remains excluded across later input and an in-flight response', async() => {
    const vm = await openNew()
    vm.form.ingredientList.push(row())
    const pending = deferred()
    recognizeIngredients.mockReturnValueOnce(pending.promise).mockResolvedValue([row(), row(2, '生姜')])
    await input('胡萝卜切丁')
    await recognizeAfterDebounce()
    wrapper.find('button[title="删除配料"]').trigger('click')
    await flush()
    pending.resolve([row(), row(2, '生姜')])
    await flush()
    expect(vm.form.ingredientList).toEqual([row(2, '生姜')])
    await input('胡萝卜切丁，加生姜')
    await recognizeAfterDebounce()
    expect(vm.form.ingredientList).toEqual([row(2, '生姜')])
    expect(vm.excludedIngredientIds.has(1)).toBe(true)
  })

  test('manual re-add releases exclusion and retains the manual quantity default', async() => {
    const vm = await openNew()
    vm.form.ingredientList.push(row())
    vm.removeIngredientRow(0)
    vm.ingredientMap[1] = { id: 1, name: '胡萝卜', unit: 'g' }
    vm.selectIngredientId = 1
    vm.addIngredientRow()
    expect(vm.excludedIngredientIds.has(1)).toBe(false)
    expect(vm.form.ingredientList[0].quantity).toBe(100)
    recognizeIngredients.mockResolvedValue([row()])
    await input('胡萝卜')
    await recognizeAfterDebounce()
    expect(vm.form.ingredientList).toHaveLength(1)
    expect(vm.form.ingredientList[0].quantity).toBe(100)
  })

  test('out-of-order responses and stale failures cannot overwrite the latest state', async() => {
    const vm = await openNew()
    const oldRequest = deferred()
    const middleRequest = deferred()
    recognizeIngredients.mockReturnValueOnce(oldRequest.promise).mockReturnValueOnce(middleRequest.promise)
      .mockResolvedValueOnce([row(2, '生姜')])
    await input('胡萝卜')
    await recognizeAfterDebounce()
    await input('蒜')
    await recognizeAfterDebounce()
    await input('生姜')
    await recognizeAfterDebounce()
    oldRequest.resolve([row()])
    middleRequest.reject(new Error('旧请求失败'))
    await flush()
    expect(vm.form.ingredientList).toEqual([row(2, '生姜')])
    expect(vm.recognitionError).toBe(false)
    expect(message.error).not.toHaveBeenCalled()
  })

  test('clearing the process invalidates an old request without removing existing ingredients', async() => {
    const vm = await openNew()
    const existing = row(3, '蒜')
    vm.form.ingredientList.push(existing)
    const pending = deferred()
    recognizeIngredients.mockReturnValueOnce(pending.promise)
    await input('胡萝卜')
    await recognizeAfterDebounce()
    await input('')
    pending.resolve([row()])
    await flush()
    await vm.submitForm()
    expect(recognizeIngredients).toHaveBeenCalledTimes(1)
    expect(addDish.mock.calls[0][0].ingredientList).toEqual([existing])
  })

  test('returning to the original historical text before debounce makes no recognition calls', async() => {
    getDish.mockResolvedValue(dish())
    const vm = createForm()
    await vm.handleUpdate({ id: 9 })
    await flush()
    await input('生姜炒熟')
    await input('胡萝卜切丁')
    await vm.submitForm()
    jest.advanceTimersByTime(500)
    expect(recognizeIngredients).not.toHaveBeenCalled()
    expect(editDish).toHaveBeenCalledTimes(1)
  })

  test('immediate saving flushes debounce, waits for recognition and prevents duplicate saves', async() => {
    const vm = await openNew()
    const pending = deferred()
    recognizeIngredients.mockReturnValue(pending.promise)
    await input('胡萝卜切丁')
    const saving = vm.submitForm()
    await flush()
    vm.submitForm()
    expect(vm.saving).toBe(true)
    expect(addDish).not.toHaveBeenCalled()
    expect(recognizeIngredients).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.dialog-footer .btn-primary').element.disabled).toBe(true)
    pending.resolve([row()])
    await saving
    expect(addDish).toHaveBeenCalledTimes(1)
    expect(addDish.mock.calls[0][0].ingredientList).toEqual([row()])
    expect(wrapper.emitted().saved[0][0].ingredientList).toEqual([row()])
  })

  test('saving reuses an in-flight latest request', async() => {
    const vm = await openNew()
    const pending = deferred()
    recognizeIngredients.mockReturnValue(pending.promise)
    await input('胡萝卜')
    await recognizeAfterDebounce()
    const saving = vm.submitForm()
    await flush()
    expect(recognizeIngredients).toHaveBeenCalledTimes(1)
    pending.resolve([row()])
    await saving
    expect(addDish).toHaveBeenCalledTimes(1)
  })

  test('typing while save waits makes it wait again for the newer text', async() => {
    const vm = await openNew()
    const first = deferred()
    const second = deferred()
    recognizeIngredients.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    await input('胡萝卜')
    const saving = vm.submitForm()
    await flush()
    await input('生姜')
    first.resolve([row()])
    await flush()
    expect(addDish).not.toHaveBeenCalled()
    expect(recognizeIngredients).toHaveBeenLastCalledWith({ cookingMethod: '生姜' })
    second.resolve([row(2, '生姜')])
    await saving
    expect(addDish.mock.calls[0][0]).toEqual(expect.objectContaining({
      cookingMethod: '生姜', ingredientList: [row(2, '生姜')]
    }))
  })

  test('latest failure keeps the draft, blocks save and retries on the next save', async() => {
    const vm = await openNew()
    vm.form.ingredientList.push(row(3, '蒜'))
    recognizeIngredients.mockRejectedValueOnce(new Error('识别失败')).mockResolvedValueOnce([row()])
    await input('胡萝卜')
    await vm.submitForm()
    expect(addDish).not.toHaveBeenCalled()
    expect(vm.dialogVisible).toBe(true)
    expect(vm.saving).toBe(false)
    expect(vm.form.cookingMethod).toBe('胡萝卜')
    expect(vm.form.ingredientList).toEqual([row(3, '蒜')])
    expect(message.error).toHaveBeenCalled()
    await vm.submitForm()
    expect(recognizeIngredients).toHaveBeenCalledTimes(2)
    expect(addDish.mock.calls[0][0].ingredientList).toEqual([row(3, '蒜'), row()])
  })

  test('closing before recognition finishes cancels pending save and reopening starts fresh', async() => {
    const vm = await openNew()
    const pending = deferred()
    recognizeIngredients.mockReturnValueOnce(pending.promise).mockResolvedValueOnce([row()])
    await input('胡萝卜')
    const saving = vm.submitForm()
    await flush()
    vm.cancel()
    vm.handleAdd()
    vm.form.name = '新会话'
    await flush()
    pending.resolve([row(2, '生姜')])
    await saving
    expect(vm.form.ingredientList).toEqual([])
    expect(addDish).not.toHaveBeenCalled()
    await input('胡萝卜')
    await recognizeAfterDebounce()
    expect(vm.form.ingredientList).toEqual([row()])
  })

  test('cancel clears debounce and exclusions and writes no dish data', async() => {
    const vm = await openNew()
    vm.form.ingredientList.push(row())
    vm.removeIngredientRow(0)
    await input('胡萝卜')
    vm.cancel()
    vm.dialogClose()
    jest.advanceTimersByTime(500)
    await flush()
    expect(recognizeIngredients).not.toHaveBeenCalled()
    expect(vm.excludedIngredientIds.size).toBe(0)
    expect(vm.form.ingredientList).toEqual([])
    expect(addDish).not.toHaveBeenCalled()
    expect(editDish).not.toHaveBeenCalled()
  })

  test('rapid dish switching ignores old detail responses and old recognition', async() => {
    const vm = createForm()
    const firstDetail = deferred()
    getDish.mockReturnValueOnce(firstDetail.promise).mockResolvedValueOnce(dish(10, '生姜炒熟'))
    const firstOpening = vm.handleUpdate({ id: 9 })
    await vm.handleUpdate({ id: 10 })
    firstDetail.resolve(dish(9))
    await firstOpening
    await flush()
    expect(vm.form.id).toBe(10)
    expect(recognizeIngredients).not.toHaveBeenCalled()
    const pending = deferred()
    recognizeIngredients.mockReturnValueOnce(pending.promise)
    await input('胡萝卜')
    await recognizeAfterDebounce()
    getDish.mockResolvedValueOnce(dish(11, '蒜切碎'))
    await vm.handleUpdate({ id: 11 })
    pending.resolve([row(2, '生姜')])
    await flush()
    expect(vm.form.id).toBe(11)
    expect(vm.form.ingredientList).toEqual([row()])
    expect(vm.excludedIngredientIds.size).toBe(0)
  })

  test('removing every ingredient sends an explicit empty list through existing edit API', async() => {
    getDish.mockResolvedValue(dish())
    const vm = createForm()
    await vm.handleUpdate({ id: 9 })
    await flush()
    vm.removeIngredientRow(0)
    await vm.submitForm()
    expect(editDish.mock.calls[0][0].ingredientList).toEqual([])
    expect(recognizeIngredients).not.toHaveBeenCalled()
  })
})
