import axios from '@/utils/request'

export function getOrders(params) {
  return axios({
    url: '/api/customer/order',
    method: 'get',
    params
  })
}

export function getOrder(id) {
  return axios({
    url: `/api/customer/order/${id}`,
    method: 'get'
  })
}

export function add(data) {
  return axios({
    url: '/api/customer/order',
    method: 'post',
    data
  })
}

export function edit(data) {
  return axios({
    url: '/api/customer/order',
    method: 'put',
    data
  })
}

export function del(ids) {
  return axios({
    url: '/api/customer/order',
    method: 'delete',
    data: ids
  })
}

export function getOrdersByCustomer(customerId, params) {
  return axios({
    url: `/api/customer/order/byCustomer/${customerId}`,
    method: 'get',
    params
  })
}

export function validateOrder(data) {
  return axios({
    url: '/api/customer/order/validate',
    method: 'post',
    data
  })
}

export default { getOrders, getOrder, add, edit, del, getOrdersByCustomer, validateOrder }
