import request from '@/utils/request'

export function verifyMeal(data) {
  return request({
    url: 'api/meal-verification/verify',
    method: 'post',
    data
  })
}

export function getVerificationLog(id) {
  return request({
    url: 'api/meal-verification/logs/' + id,
    method: 'get'
  })
}

export function getVerificationLogsByOrderId(orderId) {
  return request({
    url: 'api/meal-verification/logs/order/' + orderId,
    method: 'get'
  })
}

export default {
  verifyMeal,
  getVerificationLog,
  getVerificationLogsByOrderId
}
