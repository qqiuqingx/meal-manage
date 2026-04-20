import request from '@/utils/request'

export function refundMeal(data) {
  return request({
    url: 'api/meal-refund',
    method: 'post',
    data
  })
}

export function queryRefundLogs(params) {
  return request({
    url: 'api/meal-refund/logs',
    method: 'get',
    params
  })
}

export function getRefundLogsByOrderId(orderId) {
  return request({
    url: 'api/meal-refund/logs/order/' + orderId,
    method: 'get'
  })
}

export default {
  refundMeal,
  queryRefundLogs,
  getRefundLogsByOrderId
}
