jest.mock('@/api/dishTag', () => ({
  queryDishTags: jest.fn(),
  addDishTag: jest.fn(),
  editDishTag: jest.fn(),
  deleteDishTag: jest.fn()
}))

import { addDishTag, deleteDishTag, editDishTag, queryDishTags } from '@/api/dishTag'
import component from '@/views/meal/dish/components/DishTagEditor'

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

function createVm(value = [], selectedTags = []) {
  const vm = Object.assign(component.data(), {
    value,
    selectedTags,
    $set: (target, key, nextValue) => { target[key] = nextValue },
    $delete: (target, key) => { delete target[key] },
    $emit: jest.fn(),
    $message: { error: jest.fn(), success: jest.fn() },
    $confirm: jest.fn(() => Promise.resolve()),
    checkPer: permissions => permissions.some(permission => ['admin', 'dishTag:add', 'dishTag:edit', 'dishTag:del'].includes(permission))
  })
  Object.keys(component.methods).forEach(name => {
    vm[name] = component.methods[name].bind(vm)
  })
  Object.keys(component.computed).forEach(name => {
    Object.defineProperty(vm, name, { get: () => component.computed[name].call(vm) })
  })
  selectedTags.forEach(tag => { vm.tagById[tag.id] = tag })
  return vm
}

describe('dish tag editor', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('creates a tag after an empty search and selects it for the dish', async() => {
    addDishTag.mockResolvedValue({ id: 13, name: '新标签' })
    const vm = createVm()
    vm.searchText = ' 新标签 '
    vm.searchCompleted = true

    vm.createTag()
    await flushPromises()

    expect(addDishTag).toHaveBeenCalledWith({ name: '新标签' })
    expect(vm.tagOptions).toEqual([{ id: 13, name: '新标签' }])
    expect(vm.$emit).toHaveBeenCalledWith('input', [13])
    expect(vm.$emit).toHaveBeenCalledWith('tags-change', [{ id: 13, name: '新标签' }])
  })

  test('keeps the create input and shows duplicate-name error on failure', async() => {
    addDishTag.mockRejectedValue({ response: { data: { message: '标签名称已存在' } } })
    const vm = createVm()
    vm.searchText = '已有名称'
    vm.searchCompleted = true

    vm.createTag()
    await flushPromises()

    expect(vm.searchText).toBe('已有名称')
    expect(vm.creatingTag).toBe(false)
    expect(vm.$message.error).toHaveBeenCalledWith('标签名称已存在')
  })

  test('blocks a second create while the first request is pending', async() => {
    let finishCreate
    addDishTag.mockImplementation(() => new Promise(resolve => { finishCreate = resolve }))
    const vm = createVm()
    vm.searchText = '新标签'
    vm.searchCompleted = true

    vm.createTag()
    vm.createTag()
    expect(addDishTag).toHaveBeenCalledTimes(1)

    finishCreate({ id: 18, name: '新标签' })
    await flushPromises()
    expect(vm.creatingTag).toBe(false)
  })

  test('renames inline and updates the current selected label', async() => {
    editDishTag.mockResolvedValue({})
    const vm = createVm([3], [{ id: 3, name: '原名' }])
    const tag = { id: 3, name: '原名' }
    vm.tagOptions = [tag]
    vm.renamingTagId = 3
    vm.renameValue = '新名称'

    vm.submitRename(tag)
    await flushPromises()

    expect(vm.$confirm).toHaveBeenCalledWith(
      '重命名会影响所有使用该标签的菜品，确定保存吗？',
      '确认重命名',
      expect.any(Object)
    )
    expect(editDishTag).toHaveBeenCalledWith({ id: 3, name: '新名称' })
    expect(vm.tagOptions).toEqual([{ id: 3, name: '新名称' }])
    expect(vm.$emit).toHaveBeenCalledWith('tags-change', [{ id: 3, name: '新名称' }])
    expect(vm.renamingTagId).toBeNull()
  })

  test('keeps the rename input after a duplicate-name failure', async() => {
    editDishTag.mockRejectedValue({ response: { data: { message: '标签名称已存在' } } })
    const vm = createVm()
    const tag = { id: 3, name: '原名' }
    vm.tagOptions = [tag]
    vm.renamingTagId = 3
    vm.renameValue = '重复名称'

    vm.submitRename(tag)
    await flushPromises()

    expect(vm.renamingTagId).toBe(3)
    expect(vm.renameValue).toBe('重复名称')
    expect(vm.savingRename).toBe(false)
    expect(vm.$message.error).toHaveBeenCalledWith('标签名称已存在')
  })

  test('deletes an unused tag and removes its unsaved selection', async() => {
    deleteDishTag.mockResolvedValue({})
    queryDishTags.mockResolvedValue({ content: [{ id: 4, name: '保留' }], totalElements: 1 })
    const vm = createVm([3, 4], [{ id: 3, name: '未使用' }, { id: 4, name: '保留' }])
    vm.tagOptions = [{ id: 3, name: '未使用' }, { id: 4, name: '保留' }]
    vm.totalElements = 2

    vm.deleteTag(vm.tagOptions[0])
    await flushPromises()

    expect(deleteDishTag).toHaveBeenCalledWith(3)
    expect(vm.tagOptions.map(tag => tag.id)).toEqual([4])
    expect(vm.$emit).toHaveBeenCalledWith('input', [4])
    expect(vm.totalElements).toBe(1)
  })

  test('keeps a selected tag when the server rejects deletion because it is used', async() => {
    deleteDishTag.mockRejectedValue({
      response: { data: { message: '标签正在被菜品使用，请先从菜品中移除并保存关联后再删除' } }
    })
    const vm = createVm([3], [{ id: 3, name: '已使用' }])
    vm.tagOptions = [{ id: 3, name: '已使用' }]

    vm.deleteTag(vm.tagOptions[0])
    await flushPromises()

    expect(vm.tagOptions).toEqual([{ id: 3, name: '已使用' }])
    expect(vm.value).toEqual([3])
    expect(vm.$message.error).toHaveBeenCalledWith(
      '标签正在被菜品使用，请先从菜品中移除并保存关联后再删除'
    )
  })

  test('shows dictionary actions only to users with matching permissions', () => {
    const vm = createVm()
    vm.checkPer = permissions => permissions.includes('dishTag:edit')

    expect(component.computed.canCreateTag.call(vm)).toBe(false)
    expect(component.computed.canEditTag.call(vm)).toBe(true)
    expect(component.computed.canDeleteTag.call(vm)).toBe(false)
  })

  test('searches pages and retains selected labels across results', async() => {
    queryDishTags
      .mockResolvedValueOnce({ content: [{ id: 2, name: '谷物' }], totalElements: 21 })
      .mockResolvedValueOnce({ content: [{ id: 3, name: '低盐' }], totalElements: 21 })
    const vm = createVm([88], [{ id: 88, name: '历史选中标签' }])

    vm.searchTags('grain')
    await flushPromises()

    expect(queryDishTags).toHaveBeenNthCalledWith(1, { name: 'grain', page: 0, size: 20 })
    expect(vm.tagOptions.map(item => item.id)).toEqual([2])
    expect(vm.selectedTagOptions).toEqual([{ id: 88, name: '历史选中标签' }])

    vm.loadMoreTags()
    await flushPromises()

    expect(queryDishTags).toHaveBeenNthCalledWith(2, { name: 'grain', page: 1, size: 20 })
    expect(vm.tagOptions.map(item => item.id)).toEqual([2, 3])
  })

  test('retries the same page after a load-more request fails', async() => {
    queryDishTags
      .mockResolvedValueOnce({ content: [{ id: 1, name: '第一页' }], totalElements: 21 })
      .mockRejectedValueOnce(new Error('network failure'))
      .mockResolvedValueOnce({ content: [{ id: 21, name: '第二页' }], totalElements: 21 })
    const vm = createVm()

    vm.searchTags('')
    await flushPromises()
    vm.loadMoreTags()
    await flushPromises()
    expect(vm.page).toBe(0)

    vm.loadMoreTags()
    await flushPromises()

    expect(queryDishTags).toHaveBeenNthCalledWith(2, { name: '', page: 1, size: 20 })
    expect(queryDishTags).toHaveBeenNthCalledWith(3, { name: '', page: 1, size: 20 })
    expect(vm.tagOptions.map(item => item.id)).toEqual([1, 21])
  })

  test('keeps the latest result when searches finish out of order', async() => {
    let resolveFirst
    queryDishTags
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

  test('does not offer dictionary creation when a search request fails', async() => {
    queryDishTags.mockRejectedValue(new Error('network failure'))
    const vm = createVm()
    vm.searchText = '标签'

    vm.searchTags('标签')
    await flushPromises()

    expect(vm.searchCompleted).toBe(false)
    expect(vm.canCreateFromSearch).toBe(false)
  })

  test('emits unique IDs and selected label data when choices change', () => {
    const vm = createVm([2], [{ id: 2, name: '谷物' }])
    vm.tagById[3] = { id: 3, name: '低盐' }

    vm.updateSelected([2, 3, 3])

    expect(vm.$emit).toHaveBeenNthCalledWith(1, 'input', [2, 3])
    expect(vm.$emit).toHaveBeenNthCalledWith(2, 'tags-change', [
      { id: 2, name: '谷物' },
      { id: 3, name: '低盐' }
    ])
  })

  test('removes only a selected binding and resets remote state', () => {
    const vm = createVm([2, 3], [{ id: 2, name: '谷物' }, { id: 3, name: '低盐' }])
    vm.removeTag(2)
    expect(vm.$emit).toHaveBeenCalledWith('input', [3])

    vm.selectorVisible = true
    vm.searchText = 'grain'
    vm.tagOptions = [{ id: 3, name: '低盐' }]
    vm.page = 2
    vm.resetState()

    expect(vm.selectorVisible).toBe(false)
    expect(vm.searchText).toBe('')
    expect(vm.tagOptions).toEqual([])
    expect(vm.page).toBe(0)
  })
})
