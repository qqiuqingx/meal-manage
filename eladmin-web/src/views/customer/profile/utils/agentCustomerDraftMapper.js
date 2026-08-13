/** 将 CREATE_CUSTOMER_WITH_ORDER v1 草稿显式映射到现有客户与首单表单。 */
export function mapAgentCustomerDraft(claim, defaults) {
  if (!claim || claim.draftType !== 'CREATE_CUSTOMER_WITH_ORDER' || claim.schemaVersion !== 'v1' ||
    !['READY', 'CLAIMED'].includes(claim.status) || !claim.customerWithOrderPayload) return null
  const source = claim.customerWithOrderPayload
  const customer = source.customer || {}
  const order = source.order || {}
  const form = JSON.parse(JSON.stringify(defaults || {}))
  Object.assign(form, pick(customer, [
    'customerCode', 'customerName', 'phone', 'gestationalWeek', 'allergyTags', 'excludedDishIds',
    'excludedDates', 'medicalRequirements', 'specialRequirements', 'productionDate', 'remark', 'addresses'
  ]))
  form.addresses = Array.isArray(customer.addresses) ? customer.addresses.map(item => pick(item, [
    'addressType', 'addressDetail', 'contactName', 'contactPhone'
  ])) : form.addresses
  form.orderInfo = Object.assign({}, form.orderInfo || {}, pick(order, [
    'parentPackageId', 'childPackageId', 'breakfastCount', 'lunchDinnerCount', 'breakfastPrice',
    'lunchDinnerPrice', 'totalAmount', 'depositAmount', 'finalAmount', 'dealTime', 'firstDeliveryTime',
    'startDate', 'startMealType', 'endDate', 'mealType', 'scheduleMode', 'customerSource', 'trialConverted',
    'trialOrderId', 'mainDishCount', 'sideDishCount', 'vegCount', 'riceCount', 'riceType', 'soupCount',
    'remark', 'replaceRules'
  ]))
  form.orderInfo.deliveryDatesWithMealTypes = Array.isArray(order.deliveryDates) ? order.deliveryDates : []
  return {
    form,
    draftId: claim.draftId,
    revision: claim.revision,
    sourceSessionId: claim.sourceSessionId,
    missingFields: claim.missingFields || [],
    warnings: claim.warnings || []
  }
}

/** 仅复制登记字段，避免未来响应字段穿透正式保存 DTO。 */
function pick(source, fields) {
  return fields.reduce((result, field) => {
    if (Object.prototype.hasOwnProperty.call(source || {}, field)) result[field] = source[field]
    return result
  }, {})
}
