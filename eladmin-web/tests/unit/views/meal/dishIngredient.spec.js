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
