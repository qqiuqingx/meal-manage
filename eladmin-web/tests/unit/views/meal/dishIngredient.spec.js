/* eslint-env jest */

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
  queryIngredientTags: jest.fn(),
  addIngredientTag: jest.fn()
}))

import { downloadIngredients, editIngredient, queryIngredients } from '@/api/dishIngredient'
import { queryCategoryTree } from '@/api/dishIngredientCategory'
import { addIngredientTag, queryIngredientTags } from '@/api/dishIngredientTag'
import component from '@/views/meal/dishIngredient/index'

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

function createVm() {
  const vm = Object.assign(component.data(), component.methods, {
    $set: (target, key, value) => { target[key] = value },
    $delete: (target, key) => { delete target[key] },
    $message: { success: jest.fn() },
    canCreateTag: true,
    canEditIngredient: true,
    tagOptionsLoaded: true
  })
  for (const key of ['filteredTagOptions', 'pendingCardTagName']) {
    Object.defineProperty(vm, key, { get: () => component.computed[key].call(vm) })
  }
  return vm
}

describe('dish ingredient tag filter', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('searches tag options remotely', async() => {
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

  test('opens tag management in the current page', () => {
    const vm = createVm()
    vm.handleTagManage()
    expect(vm.tagManagerVisible).toBe(true)
  })

  test('applies tagId with the other list filters and resets the page', async() => {
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

  test('uses the selected tag in the export request', async() => {
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

  test('switches parent category and clears the previous child and page', async() => {
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

  test('refreshes category tree and ingredient list after category edits', async() => {
    queryCategoryTree.mockResolvedValue([
      { id: 1, name: '蔬菜', children: [{ id: 11, name: '叶菜' }] }
    ])
    queryIngredients.mockResolvedValue({ content: [], totalElements: 0 })
    const vm = createVm()
    vm.queryParams.parentCategoryId = 1
    vm.queryParams.categoryId = 11

    await vm.handleCategoriesChanged()
    await flushPromises()

    expect(vm.categoryTree[0].name).toBe('蔬菜')
    expect(vm.level2Categories).toEqual([{ id: 11, name: '叶菜' }])
    expect(queryIngredients).toHaveBeenCalledTimes(1)
  })

  test('resets pagination when a deleted category invalidates the active filter', async() => {
    queryCategoryTree.mockResolvedValue([])
    queryIngredients.mockResolvedValue({ content: [], totalElements: 0 })
    const vm = createVm()
    vm.queryParams.parentCategoryId = 9
    vm.queryParams.categoryId = 91
    vm.queryParams.page = 3

    await vm.handleCategoriesChanged()
    await flushPromises()

    expect(vm.queryParams.parentCategoryId).toBe(null)
    expect(vm.queryParams.categoryId).toBe(null)
    expect(vm.queryParams.page).toBe(0)
    expect(queryIngredients).toHaveBeenCalledTimes(1)
  })

  test('ignores an older category response arriving after the latest query', async() => {
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

describe('card tag creation', () => {
  beforeEach(() => jest.clearAllMocks())

  test('allows admin and explicit add permission to create tags', () => {
    for (const role of ['admin', 'dishIngredientTag:add']) {
      expect(component.computed.canCreateTag.call({ checkPer: permissions => permissions.includes(role) })).toBe(true)
    }
    expect(component.computed.canCreateTag.call({ checkPer: permissions => permissions.includes('dishIngredient:list') })).toBe(false)
  })

  test('creates and binds a tag to the original card despite selection changes', async() => {
    let resolveCreate
    addIngredientTag.mockImplementationOnce(() => new Promise(resolve => { resolveCreate = resolve }))
    editIngredient.mockResolvedValue(undefined)
    const vm = createVm()
    const card = { id: 1, name: '鸡肉', tagIds: [4] }
    vm.activeCard = card
    vm.draftTagIds = [4]
    vm.tagOptionsAll = [{ id: 4, name: '原有标签' }]
    vm.cardTagSearchText = ' 新标签 '
    vm.saveCardTags()
    vm.saveCardTags()
    expect(addIngredientTag).toHaveBeenCalledTimes(1)
    expect(addIngredientTag).toHaveBeenCalledWith({ name: '新标签' })
    vm.activeCard = { id: 2, name: '牛肉', tagIds: [] }
    vm.draftTagIds = []
    resolveCreate({ id: 8, name: '新标签' })
    await flushPromises()
    expect(editIngredient).toHaveBeenCalledWith({ id: 1, name: '鸡肉', tagIds: [4, 8] })
    expect(card.tagIds).toEqual([4, 8])
    expect(vm.activeCard.tagIds).toEqual([])
    expect(vm.savingTags).toBe(false)
  })

  test('retries binding without recreating a successfully created tag', async() => {
    addIngredientTag.mockResolvedValue({ id: 8, name: '新标签' })
    editIngredient.mockRejectedValueOnce(new Error('保存失败')).mockResolvedValueOnce(undefined)
    const vm = createVm()
    vm.activeCard = { id: 1, name: '鸡肉', tagIds: [] }
    vm.cardTagSearchText = '新标签'
    vm.saveCardTags()
    await flushPromises()
    expect(vm.cardTagSearchText).toBe('')
    expect(vm.draftTagIds).toEqual([8])
    vm.saveCardTags()
    await flushPromises()
    expect(addIngredientTag).toHaveBeenCalledTimes(1)
    expect(vm.activeCard.tagIds).toEqual([8])
  })
})

describe('combined tag search and creation', () => {
  beforeEach(() => jest.clearAllMocks())

  test('does not create a tag for matching, empty, or unauthorized searches', async() => {
    editIngredient.mockResolvedValue(undefined)
    const vm = createVm()
    vm.activeCard = { id: 1, name: '鸡肉' }
    vm.tagOptionsAll = [{ id: 8, name: '测试标签' }]
    vm.draftTagIds = [8]
    for (const keyword of ['测试', '测试标签', '   ']) {
      vm.cardTagSearchText = keyword
      expect(vm.pendingCardTagName).toBe('')
      vm.saveCardTags()
      await flushPromises()
    }
    vm.canCreateTag = false
    vm.cardTagSearchText = '新名称'
    expect(vm.pendingCardTagName).toBe('')
    vm.saveCardTags()
    await flushPromises()
    expect(addIngredientTag).not.toHaveBeenCalled()
  })

  test('loads later pages before deciding whether a tag exists', async() => {
    queryIngredientTags.mockResolvedValueOnce({ content: [{ id: 1, name: '其他' }], totalElements: 2 })
      .mockResolvedValueOnce({ content: [{ id: 8, name: '测试' }], totalElements: 2 })
    const vm = createVm()
    vm.tagOptionsLoaded = false
    vm.activeCard = { id: 1, name: '鸡肉' }
    vm.cardTagSearchText = '测试'
    const loading = vm.ensureTagOptions()
    vm.saveCardTags()
    expect(editIngredient).not.toHaveBeenCalled()
    await loading
    expect(queryIngredientTags).toHaveBeenLastCalledWith({ page: 1, size: 200 })
    expect(vm.filteredTagOptions).toEqual([{ id: 8, name: '测试' }])
    expect(vm.pendingCardTagName).toBe('')
  })
})

describe('ingredient card actions', () => {
  beforeEach(() => jest.clearAllMocks())

  test('passes the selected parent and child category to the add form', () => {
    const vm = createVm()
    vm.$refs = { ingredientForm: { handleAdd: jest.fn() }}
    vm.queryParams.parentCategoryId = 2
    vm.queryParams.categoryId = 21

    vm.handleAdd()

    expect(vm.$refs.ingredientForm.handleAdd).toHaveBeenCalledWith(2, 21)
  })

  test('removes a tag only from the selected ingredient', async() => {
    editIngredient.mockResolvedValue(undefined)
    const vm = createVm()
    vm.getList = jest.fn()
    const card = { id: 1, name: '黄瓜', tagIds: [8, 9], tags: [{ id: 8, name: '低盐' }, { id: 9, name: '清真' }] }

    vm.removeCardTag(card, { id: 8, name: '低盐' })
    vm.removeCardTag(card, { id: 8, name: '低盐' })
    expect(editIngredient).toHaveBeenCalledTimes(1)
    expect(editIngredient).toHaveBeenCalledWith({ id: 1, name: '黄瓜', tagIds: [9] })
    await flushPromises()

    expect(card.tagIds).toEqual([9])
    expect(card.tags).toEqual([{ id: 9, name: '清真' }])
    expect(vm.getList).toHaveBeenCalledTimes(1)
  })

  test('keeps the tag visible if removal fails', async() => {
    editIngredient.mockRejectedValue(new Error('保存失败'))
    const vm = createVm()
    vm.getList = jest.fn()
    const card = { id: 1, name: '黄瓜', tagIds: [8], tags: [{ id: 8, name: '低盐' }] }

    vm.removeCardTag(card, card.tags[0])
    await flushPromises()

    expect(card.tagIds).toEqual([8])
    expect(card.tags).toEqual([{ id: 8, name: '低盐' }])
    expect(vm.getList).not.toHaveBeenCalled()
    expect(vm.tagRemovalPending[1]).toBeUndefined()
  })
})
