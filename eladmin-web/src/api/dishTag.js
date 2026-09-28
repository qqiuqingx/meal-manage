import request from '@/utils/request'

/**
 * 分页查询菜品标签。
 * @param {Object} params 标签名称与分页参数
 * @returns {Promise<Object>} 标签分页结果
 */
export function queryDishTags(params) {
  return request({
    url: 'api/dish-tags',
    method: 'get',
    params
  })
}

/** 新增菜品标签并返回生成的ID。 */
export function addDishTag(data) {
  return request({
    url: 'api/dish-tags',
    method: 'post',
    data
  })
}

/** 修改菜品标签名称。 */
export function editDishTag(data) {
  return request({
    url: 'api/dish-tags',
    method: 'put',
    data
  })
}

/** 删除未被菜品引用的菜品标签。 */
export function deleteDishTag(id) {
  return request({
    url: `api/dish-tags/${id}`,
    method: 'delete'
  })
}

export default { queryDishTags, addDishTag, editDishTag, deleteDishTag }
