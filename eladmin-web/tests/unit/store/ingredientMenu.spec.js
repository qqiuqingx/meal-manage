jest.mock('@/layout/index', () => ({}))
jest.mock('@/components/ParentView', () => ({}))
jest.mock('@/router/routers', () => ({ constantRouterMap: [] }))

import Vue from 'vue'
import VueRouter from 'vue-router'
import { filterAsyncRouter } from '@/store/modules/permission'

function menu() {
  return [{
    path: '/dishIngredient',
    component: 'meal/dishIngredient/index',
    alwaysShow: true,
    redirect: 'noredirect',
    meta: { title: '配菜管理', icon: 'component' },
    children: [{ path: 'dishIngredientTag', component: 'meal/dishIngredientTag/index', meta: { title: '配料标签' }}]
  }]
}

describe('ingredient menu entry', () => {
  test('exposes the existing parent page alongside the tag entry in the sidebar', () => {
    const [route] = filterAsyncRouter(menu())
    expect(route.children.map(child => child.path)).toEqual(['', 'dishIngredientTag'])
    expect(route.children[0].meta.title).toBe('配料列表')
    expect(route.children[1].meta.title).toBe('配料标签')
  })

  test('does not insert a route that would override the parent page', () => {
    const [route] = filterAsyncRouter(menu(), false, true)
    expect(route.children.map(child => child.path)).toEqual(['dishIngredientTag'])
  })

  test('leaves unrelated menus and ingredient pages without children unchanged', () => {
    const routes = menu()
    routes[0].component = 'other/index'
    expect(filterAsyncRouter(routes)[0].children).toHaveLength(1)
    const leaf = menu()
    delete leaf[0].children
    expect(filterAsyncRouter(leaf)[0].children).toBeUndefined()
  })
})

// 使用真实 Vue Router 匹配，覆盖后端返回 noredirect 时的实际跳转。
describe('ingredient route navigation', () => {
  test.each([false, true])('opens the ingredient page with nested parent: %s', nested => {
    Vue.use(VueRouter)
    const input = menu()
    if (nested) input[0].path = 'dishIngredient'
    const routes = filterAsyncRouter(nested ? [{ path: '/customer', component: 'Layout', children: input }] : input, false, true)
    const basePath = nested ? '/customer/dishIngredient' : '/dishIngredient'
    const router = new VueRouter({ mode: 'abstract', routes: [
      ...routes,
      { path: '/404', component: {}},
      { path: '*', redirect: '/404' }
    ] })
    const list = router.resolve(basePath).route
    expect(list.path).toBe(basePath)
    expect(list.matched[list.matched.length - 1].path).toBe(basePath)
    const tags = router.resolve(basePath + '/dishIngredientTag').route
    expect(tags.path).toBe(basePath + '/dishIngredientTag')
    expect(tags.matched).toHaveLength(nested ? 3 : 2)
  })
})
