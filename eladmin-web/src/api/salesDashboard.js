import axios from '@/utils/request'

/**
 * 获取销售看板金额概览（4 张 KPI 卡片）
 * @param {Object} params - { startDate?, endDate? }
 */
export function getOverview(params) {
  return axios({
    url: '/api/sales/dashboard/overview',
    method: 'get',
    params
  })
}

/**
 * 获取月度销售金额趋势
 * @param {Object} params - { year }
 */
export function getMonthly(params) {
  return axios({
    url: '/api/sales/dashboard/monthly',
    method: 'get',
    params
  })
}

/**
 * 获取 TOP3 数据（产品销量、产品金额、销售员、渠道）
 * @param {Object} params - { startDate?, endDate? }
 */
export function getTop(params) {
  return axios({
    url: '/api/sales/dashboard/top',
    method: 'get',
    params
  })
}

/**
 * 获取销售明细表（分页）
 * @param {Object} params - { page?, size?, startDate?, endDate?, customerSource?, parentPackageId?, childPackageId? }
 */
export function getDetail(params) {
  return axios({
    url: '/api/sales/dashboard/detail',
    method: 'get',
    params
  })
}

/**
 * 获取渠道查询卡片数据
 * @param {Object} params - { customerSource, startDate?, endDate? }
 */
export function getChannelSummary(params) {
  return axios({
    url: '/api/sales/dashboard/channel-summary',
    method: 'get',
    params
  })
}

/**
 * 获取销售员查询卡片数据
 * @param {Object} params - { parentPackageId, startDate?, endDate? }
 */
export function getSalespersonSummary(params) {
  return axios({
    url: '/api/sales/dashboard/salesperson-summary',
    method: 'get',
    params
  })
}

export default { getOverview, getMonthly, getTop, getDetail, getChannelSummary, getSalespersonSummary }
