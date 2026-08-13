import axios from '@/utils/request'

/** 领取当前客服所属的表单草稿完整 payload。 */
export function claimFormDraft(draftId) {
  return axios({
    url: `/api/agent/form-drafts/${encodeURIComponent(draftId)}/claim`,
    method: 'post'
  })
}

export default { claimFormDraft }
