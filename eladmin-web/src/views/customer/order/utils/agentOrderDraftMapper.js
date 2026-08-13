/** 将 CREATE_ORDER v1 草稿显式映射到现有新增订单表单。 */
export function mapAgentOrderDraft(claim, defaults) {
  if (!claim || claim.draftType !== 'CREATE_ORDER' || claim.schemaVersion !== 'v1' ||
    !['READY', 'CLAIMED'].includes(claim.status) || !claim.orderPayload || !claim.orderPayload.customerId) return null
  const source = claim.orderPayload
  const form = Object.assign({}, JSON.parse(JSON.stringify(defaults || {})), pick(source, [
    'customerId', 'customerCode', 'parentPackageId', 'childPackageId', 'breakfastCount', 'lunchDinnerCount',
    'breakfastPrice', 'lunchDinnerPrice', 'totalAmount', 'depositAmount', 'finalAmount', 'dealTime',
    'firstDeliveryTime', 'startDate', 'startMealType', 'endDate', 'mealType', 'scheduleMode', 'customerSource',
    'trialConverted', 'trialOrderId', 'mainDishCount', 'sideDishCount', 'vegCount', 'riceCount', 'riceType',
    'soupCount', 'remark', 'replaceRules'
  ]))
  form.deliveryDatesWithMealTypes = Array.isArray(source.deliveryDates) ? source.deliveryDates : []
  form.deliveryDates = form.deliveryDatesWithMealTypes
  return {
    form,
    draftId: claim.draftId,
    revision: claim.revision,
    sourceSessionId: claim.sourceSessionId,
    missingFields: claim.missingFields || [],
    warnings: claim.warnings || []
  }
}

/** 仅复制登记字段，避免服务端字段或图片字段穿透正式保存 DTO。 */
function pick(source, fields) {
  return fields.reduce((result, field) => {
    if (Object.prototype.hasOwnProperty.call(source || {}, field)) result[field] = source[field]
    return result
  }, {})
}
