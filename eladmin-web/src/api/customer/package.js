import request from '@/utils/request'

export function getTree() {
  return request({
    url: 'api/package/tree',
    method: 'get'
  })
}

export function getParents(params) {
  return request({
    url: 'api/package',
    method: 'get',
    params
  })
}

export function add(data) {
  return request({
    url: 'api/package',
    method: 'post',
    data
  })
}

export function edit(data) {
  return request({
    url: 'api/package',
    method: 'put',
    data
  })
}

export function editStatus(id, status) {
  return request({
    url: `api/package/status/${id}`,
    method: 'put',
    params: { status }
  })
}

export function del(id) {
  return request({
    url: `api/package/${id}`,
    method: 'delete'
  })
}

// 子套餐相关
export function getSubById(id) {
  return request({
    url: `api/package/sub/${id}`,
    method: 'get'
  })
}

export function addSub(data) {
  return request({
    url: 'api/package/sub',
    method: 'post',
    data
  })
}

export function editSub(data) {
  return request({
    url: 'api/package/sub',
    method: 'put',
    data
  })
}

export function editSubStatus(id, status) {
  return request({
    url: `api/package/sub/status/${id}`,
    method: 'put',
    params: { status }
  })
}

export function delSub(id) {
  return request({
    url: `api/package/sub/${id}`,
    method: 'delete'
  })
}

export default {
  getTree,
  getParents,
  add,
  edit,
  editStatus,
  del,
  getSubById,
  addSub,
  editSub,
  editSubStatus,
  delSub
}
