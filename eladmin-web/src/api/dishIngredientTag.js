import request from '@/utils/request'

export function queryIngredientTags(params) {
  return request({
    url: 'api/dish-ingredient-tags',
    method: 'get',
    params
  })
}

export function addIngredientTag(data) {
  return request({
    url: 'api/dish-ingredient-tags',
    method: 'post',
    data
  })
}

export function editIngredientTag(data) {
  return request({
    url: 'api/dish-ingredient-tags',
    method: 'put',
    data
  })
}

export function deleteIngredientTag(id) {
  return request({
    url: `api/dish-ingredient-tags/${id}`,
    method: 'delete'
  })
}

export default { queryIngredientTags, addIngredientTag, editIngredientTag, deleteIngredientTag }
