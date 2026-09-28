/* eslint-env jest */

jest.mock('@/api/dishIngredientCategory', () => ({
  addCategory: jest.fn(),
  delCategory: jest.fn(),
  editCategory: jest.fn()
}))

import { addCategory, delCategory, editCategory } from '@/api/dishIngredientCategory'
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

  test('opens either create form directly without showing the manager', () => {
    const vm = createVm()
    vm.visible = false

    vm.handleAddLevelOne()
    expect(vm.formVisible).toBe(true)
    expect(vm.form).toEqual({ name: '', level: 1, parentId: null, sort: null })
    expect(vm.visible).toBe(false)

    vm.handleAddLevelTwo({ id: 3, name: '蔬菜' })
    expect(vm.form).toEqual({ name: '', level: 2, parentId: 3, sort: null })
    expect(vm.parentCategoryName).toBe('蔬菜')
    expect(vm.visible).toBe(false)
  })

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

  test('confirms and deletes a category, then refreshes categories', async() => {
    delCategory.mockResolvedValue(undefined)
    const vm = createVm()

    await vm.handleDelete({ id: 7, name: '叶菜类' })

    expect(vm.$confirm).toHaveBeenCalledWith('是否确认删除分类“叶菜类”？', '提示', expect.any(Object))
    expect(delCategory).toHaveBeenCalledWith(7)
    expect(vm.$emit).toHaveBeenCalledWith('refresh-categories')
    expect(vm.deleteLoadingId).toBeNull()
  })
})
