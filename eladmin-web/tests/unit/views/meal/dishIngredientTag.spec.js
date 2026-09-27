jest.mock('@/api/dishIngredientTag', () => ({
  addIngredientTag: jest.fn(),
  deleteIngredientTag: jest.fn(),
  editIngredientTag: jest.fn(),
  queryIngredientTags: jest.fn()
}))

import { addIngredientTag, queryIngredientTags } from '@/api/dishIngredientTag'
import component from '@/views/meal/dishIngredient/tagManager'

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

function createVm() {
  return Object.assign(component.data(), component.methods, {
    $message: { success: jest.fn() },
    $emit: jest.fn(),
    $refs: { tagForm: { validate: callback => callback(true), resetFields: jest.fn() }}
  })
}

describe('dishIngredientTag page', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('loads the default tag page and total count', async() => {
    queryIngredientTags.mockResolvedValue({
      content: [{ id: 4, name: '清真' }],
      totalElements: 1
    })
    const vm = createVm()

    vm.getList()
    await flushPromises()

    expect(queryIngredientTags).toHaveBeenCalledWith({ page: 0, size: 20, name: null })
    expect(vm.tagList).toEqual([{ id: 4, name: '清真' }])
    expect(vm.total).toBe(1)
    expect(vm.loading).toBe(false)
  })

  test('creates a trimmed tag through the add API', async() => {
    addIngredientTag.mockResolvedValue({ id: 8, name: '无麸质' })
    const vm = createVm()
    vm.form = { id: null, name: '无麸质' }
    vm.getList = jest.fn()

    vm.submitForm()
    await flushPromises()

    expect(addIngredientTag).toHaveBeenCalledWith({ name: '无麸质' })
    expect(vm.$message.success).toHaveBeenCalledWith('保存成功')
    expect(vm.formVisible).toBe(false)
    expect(vm.getList).toHaveBeenCalled()
    expect(vm.$emit).toHaveBeenCalledWith('refresh-tags')
  })

  test('editing reuses the selected tag ID', () => {
    const vm = createVm()

    vm.handleEdit({ id: 17, name: '低盐' })

    expect(vm.form).toEqual({ id: 17, name: '低盐' })
    expect(vm.dialogTitle).toBe('编辑标签')
    expect(vm.formVisible).toBe(true)
  })
})
