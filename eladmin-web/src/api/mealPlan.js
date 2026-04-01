import request from '@/utils/request'

export function generateMealPlan(data) {
  return request({
    url: '/api/meal-plan/generate',
    method: 'post',
    data
  })
}
