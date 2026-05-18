import axios from '@/utils/request'

export function diagnoseMealPlan(data) {
  return axios({
    url: '/api/agent/meal-plan/diagnose',
    method: 'post',
    data
  })
}

export default { diagnoseMealPlan }
