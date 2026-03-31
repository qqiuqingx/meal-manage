import axios from '@/utils/request'

export function getProfiles(params) {
  return axios({
    url: '/api/customerProfile',
    method: 'get',
    params
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

export default { getProfiles, getProfile, generateCode, add, edit, del }

