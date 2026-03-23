import request from '@/utils/request'

export function queryDishes(params) {
  return request({
    url: 'api/dishes',
    method: 'get',
    params
  })
}

export function getDish(id) {
  return request({
    url: `api/dishes/${id}`,
    method: 'get'
  })
}

export function addDish(data) {
  return request({
    url: 'api/dishes',
    method: 'post',
    data
  })
}

export function editDish(data) {
  return request({
    url: 'api/dishes',
    method: 'put',
    data
  })
}

export function delDish(ids) {
  return request({
    url: 'api/dishes/',
    method: 'delete',
    data: ids
  })
}

export function queryBySchedule(params) {
  return request({
    url: 'api/dishes/schedule',
    method: 'get',
    params
  })
}

export function queryAvailableDishes(params) {
  return request({
    url: 'api/dishes/available',
    method: 'get',
    params
  })
}

export function queryScheduleList(params) {
  return request({
    url: 'api/dishes/schedule/list',
    method: 'get',
    params
  })
}

export function generateSchedule(date, params) {
  return request({
    url: `api/dishes/schedule/${date}`,
    method: 'post',
    params
  })
}

export function delSchedule(id) {
  return request({
    url: `api/dishes/schedule/${id}`,
    method: 'delete'
  })
}

export function queryDailyCustomerStats(params) {
  return request({
    url: 'api/dishes/schedule/customer-stats',
    method: 'get',
    params
  })
}

export function queryCustomerSourceStats(params) {
  return request({
    url: 'api/dishes/schedule/customer-source-stats',
    method: 'get',
    params
  })
}

export default { queryDishes, getDish, addDish, editDish, delDish, queryBySchedule, queryAvailableDishes, queryScheduleList, generateSchedule, delSchedule, queryDailyCustomerStats, queryCustomerSourceStats }
