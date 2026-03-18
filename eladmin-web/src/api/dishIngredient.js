import request from '@/utils/request'

export function queryIngredients(params) {
  return request({
    url: 'api/dish-ingredients',
    method: 'get',
    params
  })
}

export function getIngredient(id) {
  return request({
    url: `api/dish-ingredients/${id}`,
    method: 'get'
  })
}

export function getIngredientsByDishId(dishId) {
  return request({
    url: `api/dish-ingredients/dish/${dishId}`,
    method: 'get'
  })
}

export function addIngredient(data) {
  return request({
    url: 'api/dish-ingredients',
    method: 'post',
    data
  })
}

export function editIngredient(data) {
  return request({
    url: 'api/dish-ingredients',
    method: 'put',
    data
  })
}

export function delIngredients(ids) {
  return request({
    url: 'api/dish-ingredients',
    method: 'delete',
    data: ids
  })
}

export function downloadIngredients(params) {
  return request({
    url: 'api/dish-ingredients/download',
    method: 'get',
    params,
    responseType: 'blob'
  })
}

export default { queryIngredients, getIngredient, getIngredientsByDishId, addIngredient, editIngredient, delIngredients, downloadIngredients }
