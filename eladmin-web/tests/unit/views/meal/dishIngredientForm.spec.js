jest.mock('@/api/dishIngredient', () => ({
  addIngredient: jest.fn(),
  editIngredient: jest.fn(),
  getIngredient: jest.fn()
}))

jest.mock('@/api/dishIngredientTag', () => ({
  queryIngredientTags: jest.fn()
}))

import { addIngredient, getIngredient } from '@/api/dishIngredient'
import { queryIngredientTags } from '@/api/dishIngredientTag'
import component from '@/views/meal/dishIngredient/form'

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

function createVm() {
  return Object.assign(component.data(), component.methods, {
    $nextTick: callback => callback(),
    $refs: {}
  })
}

describe('dish ingredient form tags', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('remote search loads pages and preserves selected options', async() => {
    queryIngredientTags
      .mockResolvedValueOnce({ content: [{ id: 2, name: '谷物' }], totalElements: 21 })
      .mockResolvedValueOnce({ content: [{ id: 3, name: '低盐' }], totalElements: 21 })
    const vm = createVm()
    vm.form.tagIds = [88]
    vm.tagOptions = [{ id: 88, name: '历史选中标签' }]

    vm.searchTags('grain')
    await flushPromises()

    expect(queryIngredientTags).toHaveBeenNthCalledWith(1, { name: 'grain', page: 0, size: 20 })
    expect(vm.tagOptions.map(item => item.id)).toEqual([88, 2])

    vm.loadMoreTags()
    await flushPromises()

    expect(queryIngredientTags).toHaveBeenNthCalledWith(2, { name: 'grain', page: 1, size: 20 })
    expect(vm.tagOptions.map(item => item.id)).toEqual([88, 2, 3])
  })

  test('edit loads tags outside the first option page into the selected cache', async() => {
    getIngredient.mockResolvedValue({
      id: 30,
      name: '燕麦',
      enabled: true,
      tagIds: [92],
      tags: [{ id: 92, name: '无麸质' }]
    })
    const vm = createVm()
    vm.categoryTree = []

    vm.handleUpdate(30)
    await flushPromises()

    expect(vm.form.tagIds).toEqual([92])
    expect(vm.tagOptions).toContainEqual({ id: 92, name: '无麸质' })
    expect(vm.dialogVisible).toBe(true)
  })

  test('reset clears tags so a new ingredient starts untagged', () => {
    const vm = createVm()
    vm.form.tagIds = [3, 7]
    vm.tagOptions = [{ id: 3, name: '旧标签' }]

    vm.resetForm()

    expect(vm.form.tagIds).toEqual([])
    expect(vm.tagOptions).toEqual([])
  })

  test('new ingredient inherits the selected parent and child category', () => {
    const vm = createVm()
    vm.level1Categories = [{ id: 2, name: '蔬菜', children: [{ id: 21, name: '瓜类' }] }]
    vm.handleAdd(2, 21)

    expect(vm.form).toEqual(expect.objectContaining({
      parentCategoryId: 2,
      parentCategoryName: '蔬菜',
      categoryId: 21,
      categoryName: '瓜类'
    }))
    expect(vm.currentLevel2Categories).toEqual([{ id: 21, name: '瓜类' }])
  })

  test('new ingredient under a parent leaves the child unselected', () => {
    const vm = createVm()
    vm.level1Categories = [{ id: 2, name: '蔬菜', children: [{ id: 21, name: '瓜类' }] }]
    vm.handleAdd(2, null)

    expect(vm.form.parentCategoryName).toBe('蔬菜')
    expect(vm.form.categoryId).toBeNull()
    expect(vm.form.categoryName).toBeNull()
  })

  test('submits selected tag IDs with the ingredient request', async() => {
    addIngredient.mockResolvedValue({})
    const vm = createVm()
    vm.form.name = '鸡肉'
    vm.form.tagIds = [2, 8]
    vm.$refs = { form: { validate: callback => callback(true) }}
    vm.$message = { success: jest.fn() }
    vm.$emit = jest.fn()

    vm.submitForm()
    await flushPromises()

    expect(addIngredient).toHaveBeenCalledWith(vm.form)
    expect(vm.$emit).toHaveBeenCalledWith('refresh')
  })

  test('ignores a stale remote search response after the query changes', async() => {
    let resolveFirst
    queryIngredientTags
      .mockImplementationOnce(() => new Promise(resolve => { resolveFirst = resolve }))
      .mockResolvedValueOnce({ content: [{ id: 6, name: '第二个结果' }], totalElements: 1 })
    const vm = createVm()

    vm.searchTags('first')
    vm.searchTags('second')
    await flushPromises()
    resolveFirst({ content: [{ id: 5, name: '过期结果' }], totalElements: 1 })
    await flushPromises()

    expect(vm.tagOptions).toEqual([{ id: 6, name: '第二个结果' }])
  })
})
