/* eslint-env jest */
jest.mock('@/api/dish', () => ({
  queryDishes: jest.fn(),
  editDish: jest.fn(),
  delDish: jest.fn(),
  queryPackages: jest.fn()
}))

jest.mock('@/views/meal/dish/dish.vue', () => ({
  name: 'DishForm',
  render(h) {
    return h('div')
  }
}))

const DishList = require('@/views/meal/dish/list.vue').default

describe('DishList.sortDishList', () => {
  test('sorts dish list when mealTimeInfo is a JSON array', () => {
    const ctx = {
      dishList: [
        { id: 2, dishType: 'MAIN', sort: 2, mealTimeInfo: ['DINNER'] },
        { id: 1, dishType: 'SOUP', sort: 1, mealTimeInfo: ['LUNCH'] }
      ]
    }

    DishList.methods.sortDishList.call(ctx)

    expect(ctx.dishList.map(item => item.id)).toEqual([1, 2])
  })
})
