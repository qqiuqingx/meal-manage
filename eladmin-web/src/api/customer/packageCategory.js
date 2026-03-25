import axios from '@/utils/request'

export function getTree(params) {
  return axios({
    url: '/api/customerPackageCategory/tree',
    method: 'get',
    params
  })
}

export function getParents(params) {
  return axios({
    url: '/api/customerPackageCategory/parents',
    method: 'get',
    params
  })
}

export function add(data) {
  return axios({
    url: '/api/customerPackageCategory',
    method: 'post',
    data
  })
}

export function edit(data) {
  return axios({
    url: '/api/customerPackageCategory',
    method: 'put',
    data
  })
}

export function editStatus(id, enabled) {
  return axios({
    url: `/api/customerPackageCategory/${id}/status`,
    method: 'put',
    data: { enabled }
  })
}

export function del(id) {
  return axios({
    url: `/api/customerPackageCategory/${id}`,
    method: 'delete'
  })
}
