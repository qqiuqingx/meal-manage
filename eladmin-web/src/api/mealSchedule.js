import request from '@/utils/request'

export function queryMealSchedule(params) {
  return request({
    url: 'api/meal-schedule',
    method: 'get',
    params
  })
}

export function addMealSchedule(data) {
  return request({
    url: 'api/meal-schedule',
    method: 'post',
    data
  })
}

export function editMealSchedule(data) {
  return request({
    url: `api/meal-schedule/${data.id}`,
    method: 'put',
    data
  })
}

export function deleteMealSchedule(id) {
  return request({
    url: `api/meal-schedule/${id}`,
    method: 'delete'
  })
}

export function batchAddMealSchedule(data) {
  return request({
    url: 'api/meal-schedule/batch',
    method: 'post',
    data
  })
}

export function copyMealSchedule(data) {
  return request({
    url: 'api/meal-schedule/copy',
    method: 'post',
    data
  })
}
