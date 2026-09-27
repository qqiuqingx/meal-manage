jest.mock('@/api/dishIngredient', () => ({
  delIngredients: jest.fn(),
  downloadIngredients: jest.fn(),
  editIngredient: jest.fn(),
  queryIngredients: jest.fn()
}))

jest.mock('@/api/dishIngredientCategory', () => ({
  queryCategoryTree: jest.fn()
}))

jest.mock('@/api/dishIngredientTag', () => ({
  queryIngredientTags: jest.fn()
}))

import { downloadIngredients, queryIngredients } from '@/api/dishIngredient'
import { queryIngredientTags } from '@/api/dishIngredientTag'
import component from '@/views/meal/dishIngredient/index'

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

function createVm() {
  return Object.assign(component.data(), component.methods)
}

describe('dish ingredient tag filter', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('searches tag options remotely', async () => {
    queryIngredientTags.mockResolvedValue({
      content: [{ id: 12, name: '清真' }],
      totalElements: 1
    })
    const vm = createVm()

    vm.searchTagOptions('清真')
    await flushPromises()

    expect(queryIngredientTags).toHaveBeenCalledWith({ name: '清真', page: 0, size: 20 })
    expect(vm.tagOptions).toEqual([{ id: 12, name: '清真' }])
  })

  test('opens tag management under the current ingredient route', () => {
    const vm = createVm()
    vm.$route = { path: '/customer/dishIngredient' }
    vm.$router = { push: jest.fn() }

    vm.handleTagManage()

    expect(vm.$router.push).toHaveBeenCalledWith({ path: '/customer/dishIngredient/dishIngredientTag' })
    expect(component.computed.isTagManagementRoute.call({
      $route: { path: '/customer/dishIngredient/dishIngredientTag' }
    })).toBe(true)
  })

  test('applies tagId with the other list filters and resets the page', async () => {
    queryIngredients.mockResolvedValue({ content: [], totalElements: 0 })
    const vm = createVm()
    vm.queryParams.page = 4
    vm.queryParams.tagId = 12

    vm.handleQuery()
    await flushPromises()

    expect(vm.queryParams.page).toBe(0)
    expect(queryIngredients).toHaveBeenCalledWith(vm.queryParams)
    expect(queryIngredients.mock.calls[0][0].tagId).toBe(12)
  })

  test('uses the selected tag in the export request', async () => {
    downloadIngredients.mockResolvedValue('file')
    const vm = createVm()
    vm.queryParams.tagId = 12
    vm.queryParams.name = '鸡肉'
    const link = { click: jest.fn(), href: '', download: '' }
    const createElement = jest.spyOn(document, 'createElement').mockReturnValue(link)
    URL.createObjectURL = jest.fn(() => 'blob:ingredients')
    URL.revokeObjectURL = jest.fn()

    vm.handleDownload()
    await flushPromises()

    expect(downloadIngredients).toHaveBeenCalledWith({
      name: '鸡肉',
      parentCategoryId: null,
      categoryId: null,
      tagId: 12,
      enabled: null
    })
    expect(link.click).toHaveBeenCalled()
    createElement.mockRestore()
  })
})

describe('ingredient supermarket navigation', () => {
  beforeEach(() => jest.clearAllMocks())

  test('switches parent category and clears the previous child and page', async () => {
    queryIngredients.mockResolvedValue({ content: [], totalElements: 0 })
    const vm = createVm()
    vm.level1Categories = [{ id: 2, name: '肉类', children: [{ id: 21, name: '猪肉' }] }]
    vm.queryParams.categoryId = 11
    vm.queryParams.page = 3
    vm.queryParams.tagId = 12
    vm.selectParentCategory(2)
    await flushPromises()
    expect(vm.level2Categories).toEqual([{ id: 21, name: '猪肉' }])
    expect(queryIngredients).toHaveBeenCalledWith(expect.objectContaining({ parentCategoryId: 2, categoryId: null, page: 0, tagId: 12 }))
    vm.selectCategory(21)
    expect(queryIngredients).toHaveBeenLastCalledWith(expect.objectContaining({ parentCategoryId: 2, categoryId: 21, page: 0 }))
  })

  test('keeps filters when switching views and clears table selection', () => {
    const vm = createVm()
    vm.queryParams.categoryId = 21
    vm.handleSelectionChange([{ id: 1 }])
    vm.setViewMode('table')
    expect(vm.queryParams.categoryId).toBe(21)
    expect(vm.ids).toEqual([])
    expect(vm.multiple).toBe(true)
  })

  test('ignores an older category response arriving after the latest query', async () => {
    let resolveOld
    queryIngredients.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValueOnce({ content: [{ id: 2, name: '牛肉' }], totalElements: 1 })
    const vm = createVm()
    vm.selectParentCategory(1)
    vm.selectParentCategory(2)
    await flushPromises()
    resolveOld({ content: [{ id: 1, name: '南瓜' }], totalElements: 30 })
    await flushPromises()
    expect(vm.ingredientList).toEqual([{ id: 2, name: '牛肉' }])
    expect(vm.total).toBe(1)
    expect(vm.loading).toBe(false)
  })
})
