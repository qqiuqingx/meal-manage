import request from '@/utils/request'

export function queryCategoryTree() {
  return request({
    url: 'api/dish-ingredient-categories/tree',
    method: 'get'
  })
}

export function queryCategories() {
  return request({
    url: 'api/dish-ingredient-categories',
    method: 'get'
  })
}

export function getCategory(id) {
  return request({
    url: `api/dish-ingredient-categories/${id}`,
    method: 'get'
  })
}

export function getCategoriesByParentId(parentId) {
  return request({
    url: `api/dish-ingredient-categories/parent/${parentId}`,
    method: 'get'
  })
}

export function delCategory(id) {
  return request({
    url: `api/dish-ingredient-categories/${id}`,
    method: 'delete'
  })
}

export default { queryCategoryTree, queryCategories, getCategory, getCategoriesByParentId, delCategory }
