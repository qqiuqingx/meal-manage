/* eslint-env jest */
jest.mock('@/layout/index', () => ({}))
jest.mock('@/components/ParentView', () => ({}))
jest.mock('@/router/routers', () => ({ constantRouterMap: [] }))

import { filterAsyncRouter } from '@/store/modules/permission'
import tagsView from '@/store/modules/tagsView'

describe('order menu cache policy', () => {
  test.each([false, true])('enables order caching for sidebar/router conversion: %s', rewrite => {
    const input = [{ path: '/customer', component: 'Layout', children: [{
      path: 'order', name: '订单管理', component: 'customer/order/index',
      meta: { title: '订单管理', icon: 'documentation', noCache: true, permission: 'customerOrder:list' }
    }] }]
    const order = filterAsyncRouter(input, false, rewrite)[0].children[0]
    expect(order.name).toBe('CustomerOrder')
    expect(order.meta).toEqual({ title: '订单管理', icon: 'documentation', noCache: false, permission: 'customerOrder:list' })
    const state = { cachedViews: [] }
    tagsView.mutations.ADD_CACHED_VIEW(state, order)
    expect(state.cachedViews).toEqual(['CustomerOrder'])
    tagsView.mutations.DEL_CACHED_VIEW(state, order)
    expect(state.cachedViews).toEqual([])
  })

  test('retains the configured cache policy for other pages', () => {
    const [dish] = filterAsyncRouter([{
      path: '/dish', name: 'Dish', component: 'meal/dish/index', meta: { title: '菜品管理', noCache: true }
    }])
    expect(dish.name).toBe('Dish')
    expect(dish.meta.noCache).toBe(true)
  })
})
