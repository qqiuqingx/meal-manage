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

export function saveMealScheduleAdjustments(data) {
  return axios({
    url: '/api/customerProfile/mealStats/scheduleAdjustments',
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

export function previewCustomerImport(file, importDate) {
  const data = new FormData()
  data.append('file', file)
  if (importDate) data.append('importDate', importDate)
  return axios({
    url: '/api/customerProfile/import/preview',
    method: 'post',
    data
  })
}

export function confirmCustomerImport(file, fileHash, importDate) {
  const data = new FormData()
  data.append('file', file)
  data.append('fileHash', fileHash)
  if (importDate) data.append('importDate', importDate)
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
  getProfile,
  generateCode,
  parseIntakeText,
  previewCustomerImport,
  confirmCustomerImport,
  add,
  edit,
  del,
  saveMealScheduleAdjustments
}
