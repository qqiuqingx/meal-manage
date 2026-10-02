jest.mock('@/api/dish', () => ({
  addDish: jest.fn(),
  editDish: jest.fn(),
  queryPackages: jest.fn(),
  getDish: jest.fn(),
  recognizeIngredients: jest.fn()
}))

jest.mock('@/api/dishIngredient', () => ({
  queryIngredients: jest.fn()
}))

jest.mock('@/api/dishTag', () => ({
  queryDishTags: jest.fn()
}))

import { addDish, editDish, getDish, queryPackages } from '@/api/dish'
import component from '@/views/meal/dish/dish'

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

function createVm() {
  return Object.assign(component.data(), component.methods, {
    $set: (target, key, value) => { target[key] = value },
    $refs: {},
    $message: { success: jest.fn() },
    $emit: jest.fn()
  })
}

describe('dish form tags', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    queryPackages.mockResolvedValue([])
  })

  test('loads selected tag IDs and labels when editing a dish', async() => {
    getDish.mockResolvedValue({
      id: 30,
      name: '清蒸鲈鱼',
      dishType: 'MAIN',
      tagIds: [92],
      tags: [{ id: 92, name: '低盐' }]
    })
    const vm = createVm()

    vm.handleUpdate({ id: 30 })
    await flushPromises()

    expect(vm.form.tagIds).toEqual([92])
    expect(vm.form.tags).toEqual([{ id: 92, name: '低盐' }])
    expect(vm.dialogVisible).toBe(true)
  })

  test('clears tag selections when the form is reset', () => {
    const vm = createVm()
    const resetState = jest.fn()
    vm.form.tagIds = [2, 8]
    vm.form.tags = [{ id: 2, name: '清真' }]
    vm.$refs = { dishTagEditor: { resetState } }

    vm.resetForm()

    expect(vm.form.tagIds).toEqual([])
    expect(vm.form.tags).toEqual([])
    expect(resetState).toHaveBeenCalled()
  })

  test('cancelling a new dish discards pending tag bindings without dictionary calls', () => {
    const vm = createVm()
    vm.form.tagIds = [13]
    vm.form.tags = [{ id: 13, name: '新标签' }]

    vm.cancel()
    vm.dialogClose()

    expect(vm.dialogVisible).toBe(false)
    expect(vm.form.tagIds).toEqual([])
    expect(vm.form.tags).toEqual([])
    expect(addDish).not.toHaveBeenCalled()
    expect(editDish).not.toHaveBeenCalled()
  })

  test('submits selected tag IDs through the existing dish save action', async() => {
    editDish.mockResolvedValue({})
    const vm = createVm()
    vm.dialogVisible = true
    vm.form.id = 12
    vm.form.name = '清蒸鲈鱼'
    vm.form.tagIds = [2, 8]
    vm.$refs = { form: { validate: callback => callback(true) } }

    vm.submitForm()
    await flushPromises()

    expect(editDish).toHaveBeenCalledWith(vm.form)
    expect(vm.$emit).toHaveBeenCalledWith('refresh')
  })
})
