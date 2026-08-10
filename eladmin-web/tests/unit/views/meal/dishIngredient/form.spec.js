/* eslint-env jest */
jest.mock('@/api/dishIngredient', () => ({
  addIngredient: jest.fn(),
  editIngredient: jest.fn(),
  getIngredient: jest.fn()
}))

jest.mock('@/api/dishIngredientCategory', () => ({
  queryCategoryTree: jest.fn()
}))

const IngredientForm = require('@/views/meal/dishIngredient/form.vue').default

describe('IngredientForm.syncCategoryOptions', () => {
  test('clears selected parent and child when current parent category is deleted', () => {
    const ctx = {
      level1Categories: [
        { id: 2, name: '蔬菜', children: [{ id: 21, name: '叶菜类' }] }
      ],
      currentLevel2Categories: [],
      form: {
        parentCategoryId: 1,
        parentCategoryName: '肉类',
        categoryId: 11,
        categoryName: '禽肉'
      }
    }

    DishIngredientFormSync.call(ctx)

    expect(ctx.form.parentCategoryId).toBe(null)
    expect(ctx.form.parentCategoryName).toBe(null)
    expect(ctx.form.categoryId).toBe(null)
    expect(ctx.form.categoryName).toBe(null)
    expect(ctx.currentLevel2Categories).toEqual([])
  })

  test('preserves custom typed categories when they are not yet in the tree', () => {
    const ctx = {
      level1Categories: [
        { id: 2, name: '蔬菜', children: [{ id: 21, name: '叶菜类' }] }
      ],
      currentLevel2Categories: [],
      form: {
        parentCategoryId: null,
        parentCategoryName: '自定义一级分类',
        categoryId: null,
        categoryName: '自定义二级分类'
      }
    }

    DishIngredientFormSync.call(ctx)

    expect(ctx.form.parentCategoryName).toBe('自定义一级分类')
    expect(ctx.form.categoryName).toBe('自定义二级分类')
    expect(ctx.currentLevel2Categories).toEqual([])
  })
})

function DishIngredientFormSync() {
  return IngredientForm.methods.syncCategoryOptions.call(this)
}
