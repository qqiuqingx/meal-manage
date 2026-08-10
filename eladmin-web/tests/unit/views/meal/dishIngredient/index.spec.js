/* eslint-env jest */
jest.mock('@/api/dishIngredient', () => ({
  queryIngredients: jest.fn(),
  editIngredient: jest.fn(),
  delIngredients: jest.fn(),
  downloadIngredients: jest.fn()
}))

jest.mock('@/api/dishIngredientCategory', () => ({
  queryCategoryTree: jest.fn()
}))

jest.mock('@/views/meal/dishIngredient/form.vue', () => ({
  name: 'IngredientForm',
  render(h) {
    return h('div')
  }
}))

jest.mock('@/views/meal/dishIngredient/categoryManager.vue', () => ({
  name: 'CategoryManager',
  render(h) {
    return h('div')
  }
}))

const DishIngredient = require('@/views/meal/dishIngredient/index.vue').default

describe('DishIngredient.syncQueryCategoryState', () => {
  test('clears selected parent and child when parent category is deleted', () => {
    const ctx = {
      queryParams: {
        parentCategoryId: 1,
        categoryId: 11
      },
      level2Categories: [],
      getLevel2CategoriesByParentId: DishIngredient.methods.getLevel2CategoriesByParentId
    }

    DishIngredient.methods.syncQueryCategoryState.call(ctx, [
      { id: 2, name: '蔬菜', children: [{ id: 21, name: '叶菜类' }] }
    ])

    expect(ctx.queryParams.parentCategoryId).toBe(null)
    expect(ctx.queryParams.categoryId).toBe(null)
    expect(ctx.level2Categories).toEqual([])
  })

  test('keeps selected parent and clears deleted child category', () => {
    const tree = [
      { id: 1, name: '肉类', children: [{ id: 12, name: '畜肉' }] }
    ]
    const ctx = {
      queryParams: {
        parentCategoryId: 1,
        categoryId: 11
      },
      level2Categories: [],
      getLevel2CategoriesByParentId: DishIngredient.methods.getLevel2CategoriesByParentId
    }

    DishIngredient.methods.syncQueryCategoryState.call(ctx, tree)

    expect(ctx.queryParams.parentCategoryId).toBe(1)
    expect(ctx.queryParams.categoryId).toBe(null)
    expect(ctx.level2Categories).toEqual(tree[0].children)
  })
})
