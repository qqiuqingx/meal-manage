import axios from '@/utils/request'

export function getProfiles(params) {
  return axios({
    url: '/api/customerProfile',
    method: 'get',
    params
  })
}

export function getMealStats(params) {
  return axios({
    url: '/api/customerProfile/mealStats',
    method: 'get',
    params
  })
}

export function getOrderMealCalendar(orderId, statsMonth) {
  return axios({
    url: `/api/customerProfile/mealStats/orders/${orderId}/calendar`,
    method: 'get',
    params: { statsMonth }
  })
}

export function saveOrderMealCalendar(orderId, data) {
  return axios({
    url: `/api/customerProfile/mealStats/orders/${orderId}/calendar`,
    method: 'put',
    data
  })
}

export function getProfile(id) {
  return axios({
    url: `/api/customerProfile/${id}`,
    method: 'get'
  })
}

export function getDietOptions() {
  return axios({
    url: '/api/customerProfile/diet-options',
    method: 'get'
  })
}

export function generateCode(parentPackageId) {
  return axios({
    url: '/api/customerProfile/generateCode',
    method: 'get',
    params: { parentPackageId }
  })
}

export function parseIntakeText(data) {
  return axios({
    url: '/api/customerProfile/intake/parse',
    method: 'post',
    data
  })
}

export function previewCustomerImport(file, importDate, dietOnly = false) {
  const data = new FormData()
  data.append('file', file)
  if (importDate) data.append('importDate', importDate)
  data.append('dietOnly', String(dietOnly))
  return axios({
    url: '/api/customerProfile/import/preview',
    method: 'post',
    data
  })
}

export function confirmCustomerImport(file, fileHash, dictionaryHash, dietSelections, importDate, dietOnly = false) {
  const data = new FormData()
  data.append('file', file)
  data.append('fileHash', fileHash)
  if (dictionaryHash) data.append('dictionaryHash', dictionaryHash)
  if (dietSelections && dietSelections.length) data.append('dietSelections', JSON.stringify(dietSelections))
  if (importDate) data.append('importDate', importDate)
  data.append('dietOnly', String(dietOnly))
  return axios({
    url: '/api/customerProfile/import/confirm',
    method: 'post',
    data
  })
}

export function add(data) {
  return axios({
    url: '/api/customerProfile',
    method: 'post',
    data
  })
}

export function edit(data) {
  return axios({
    url: '/api/customerProfile',
    method: 'put',
    data
  })
}

export function del(ids) {
  return axios({
    url: '/api/customerProfile',
    method: 'delete',
    data: ids
  })
}

export default {
  getProfiles,
  getMealStats,
  getOrderMealCalendar,
  saveOrderMealCalendar,
  getProfile,
  getDietOptions,
  generateCode,
  parseIntakeText,
  previewCustomerImport,
  confirmCustomerImport,
  add,
  edit,
  del
}
