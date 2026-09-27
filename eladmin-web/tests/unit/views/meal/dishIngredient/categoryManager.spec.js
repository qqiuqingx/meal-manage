/* eslint-env jest */

jest.mock('@/api/dishIngredientCategory', () => ({
  addCategory: jest.fn(),
  delCategory: jest.fn(),
  editCategory: jest.fn()
}))

import { addCategory, editCategory } from '@/api/dishIngredientCategory'
import component from '@/views/meal/dishIngredient/categoryManager'

function createVm() {
  const vm = Object.assign(component.data(), component.methods, {
    $refs: {
      categoryForm: { validate: callback => callback(true) }
    },
    $confirm: jest.fn().mockResolvedValue(),
    $emit: jest.fn(),
    $message: { success: jest.fn() }
  })
  return vm
}

describe('ingredient category manager', () => {
  beforeEach(() => jest.clearAllMocks())

  test('creates a category and preserves an explicit sort of zero', async() => {
    addCategory.mockResolvedValue({ id: 4 })
    const vm = createVm()
    vm.form = { name: ' 蔬菜 ', level: 1, parentId: null, sort: 0 }

    await vm.submitForm()

    expect(addCategory).toHaveBeenCalledWith({ name: '蔬菜', level: 1, parentId: null, sort: 0 })
    expect(vm.$emit).toHaveBeenCalledWith('refresh-categories')
    expect(vm.formVisible).toBe(false)
  })

  test('warns before changing a category name and updates after confirmation', async() => {
    editCategory.mockResolvedValue(undefined)
    const vm = createVm()
    vm.formMode = 'edit'
    vm.editingCategoryId = 7
    vm.originalName = '蔬菜'
    vm.form = { name: '叶菜', sort: 10 }

    await vm.submitForm()

    expect(vm.$confirm).toHaveBeenCalledWith(
      expect.stringContaining('过敏标签不会随分类改名自动更新'),
      '分类改名提示',
      expect.any(Object)
    )
    expect(editCategory).toHaveBeenCalledWith(7, { name: '叶菜', sort: 10 })
    expect(vm.$emit).toHaveBeenCalledWith('refresh-categories')
  })

  test('does not update when the rename warning is canceled', async() => {
    const vm = createVm()
    vm.$confirm.mockRejectedValue('cancel')
    vm.formMode = 'edit'
    vm.editingCategoryId = 7
    vm.originalName = '蔬菜'
    vm.form = { name: '叶菜', sort: 10 }

    await vm.submitForm()

    expect(editCategory).not.toHaveBeenCalled()
    expect(vm.$emit).not.toHaveBeenCalled()
  })
})
