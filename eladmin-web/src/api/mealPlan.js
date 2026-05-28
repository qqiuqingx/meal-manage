import request from '@/utils/request'

export function generateMealPlan(data) {
  return request({
    url: '/api/meal-plan/generate',
    method: 'post',
    data
  })
}

export function getMealPlanList(params, includeDetail = false) {
  return request({
    url: '/api/meal-plan',
    method: 'get',
    params: {
      ...params,
      includeDetail
    }
  })
}

export function getMealPlanFullDetail(id) {
  return request({
    url: `/api/meal-plan/${id}/detail`,
    method: 'get'
  })
}

export function getMealPlanCustomerAddresses(id) {
  return request({
    url: `/api/meal-plan/${id}/customer-addresses`,
    method: 'get'
  })
}

export function delMealPlan(params) {
  return request({
    url: '/api/meal-plan',
    method: 'delete',
    params
  })
}

export function delMealPlanCustomers(data) {
  return request({
    url: '/api/meal-plan/customers',
    method: 'delete',
    data
  })
}

export function getMealPackageStatistics(params) {
  return request({
    url: '/api/meal-plan/statistics-by-date',
    method: 'get',
    params
  })
}

export function getDepletionWarnings(params) {
  return request({
    url: '/api/meal-plan/depletion-warnings',
    method: 'get',
    params
  })
}
