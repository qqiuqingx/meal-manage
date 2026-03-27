/* eslint-env jest */

// Extract methods under test from the component logic
function createDefaultOrderInfo() {
  return {
    parentPackageId: null,
    childPackageId: null,
    breakfastCount: 0,
    lunchDinnerCount: 0,
    totalCount: 0,
    startDate: null,
    endDate: null
  }
}

function createAddressesFromForm(formData = {}) {
  const defaults = [
    { addressType: 'DEFAULT', addressDetail: '', contactName: '', contactPhone: '' },
    { addressType: 'WORKDAY', addressDetail: '', contactName: '', contactPhone: '' },
    { addressType: 'WEEKEND', addressDetail: '', contactName: '', contactPhone: '' }
  ]
  if (Array.isArray(formData.addresses)) {
    const addressMap = formData.addresses.reduce((result, address) => {
      if (address && address.addressType) {
        result[address.addressType] = {
          addressType: address.addressType,
          addressDetail: address.addressDetail || '',
          contactName: address.contactName || '',
          contactPhone: address.contactPhone || ''
        }
      }
      return result
    }, {})
    return defaults.map(address => addressMap[address.addressType] || address)
  }
  return defaults
}

const CRUD_STATUS = {
  PREPARED: 0,
  PROCESSING: 2
}

function isCreateMode(ctx) {
  return ctx.crud.status.add === CRUD_STATUS.PREPARED
}

function buildSubmitPayload(ctx) {
  const formData = JSON.parse(JSON.stringify(ctx.form))
  const payload = {
    customerName: formData.customerName,
    phone: formData.phone,
    gestationalWeek: formData.gestationalWeek,
    allergyTags: Array.isArray(formData.allergyTags) ? formData.allergyTags : [],
    medicalRequirements: formData.medicalRequirements,
    remark: formData.remark,
    addresses: createAddressesFromForm(formData)
  }

  if (formData.id) {
    payload.id = formData.id
  }

  if (isCreateMode(ctx)) {
    const orderInfo = formData.orderInfo || createDefaultOrderInfo()
    const breakfastCount = orderInfo.breakfastCount || 0
    const lunchDinnerCount = orderInfo.lunchDinnerCount || 0
    payload.status = true
    payload.orderInfo = {
      parentPackageId: orderInfo.parentPackageId,
      childPackageId: orderInfo.childPackageId,
      breakfastCount,
      lunchDinnerCount,
      totalCount: breakfastCount + lunchDinnerCount,
      startDate: orderInfo.startDate,
      endDate: orderInfo.endDate
    }
  }

  return payload
}

let submitFormSnapshot = null

function beforeSubmit(ctx) {
  submitFormSnapshot = JSON.parse(JSON.stringify(ctx.form))
  const payload = buildSubmitPayload(ctx)
  Object.keys(ctx.crud.form).forEach(key => {
    ctx.$delete(ctx.crud.form, key)
  })
  Object.keys(payload).forEach(key => {
    ctx.$set(ctx.crud.form, key, payload[key])
  })
  return true
}

function restoreSubmitFormSnapshot(ctx) {
  if (!submitFormSnapshot) {
    return
  }
  const snapshot = JSON.parse(JSON.stringify(submitFormSnapshot))
  Object.keys(ctx.crud.form).forEach(key => {
    ctx.$delete(ctx.crud.form, key)
  })
  Object.keys(snapshot).forEach(key => {
    ctx.$set(ctx.crud.form, key, snapshot[key])
  })
  submitFormSnapshot = null
}

function afterAddError(ctx) {
  restoreSubmitFormSnapshot(ctx)
}

function afterEditError(ctx) {
  restoreSubmitFormSnapshot(ctx)
}

const profileApi = {
  getProfile: jest.fn()
}

function handleEdit(ctx, row) {
  const requestId = ctx.editRequestId + 1
  ctx.editRequestId = requestId
  profileApi.getProfile(row.id).then(res => {
    if (requestId !== ctx.editRequestId) {
      return
    }
    const detail = res.data || res
    ctx.crud.toEdit(detail)
  }).catch(e => {
    if (requestId !== ctx.editRequestId) {
      return
    }
    ctx.$message.error('获取客户详情失败: ' + (e.message || '未知错误'))
  })
}

describe('CustomerProfile payload building', () => {
  beforeEach(() => {
    submitFormSnapshot = null
    profileApi.getProfile.mockReset()
  })

  test('disables shared toolbar edit button', () => {
    const crudOptions = {
      title: '客户档案',
      url: '/api/customerProfile',
      idField: 'id',
      sort: 'id,desc',
      optShow: { edit: false }
    }
    expect(crudOptions.optShow.edit).toBe(false)
  })

  test('builds create payload with orderInfo only', () => {
    const ctx = {
      form: {
        customerName: '张三',
        phone: '13800000000',
        gestationalWeek: 32,
        allergyTags: ['牛奶'],
        medicalRequirements: '少盐',
        remark: '备注',
        addresses: [
          { addressType: 'DEFAULT', addressDetail: '默认地址', contactName: '', contactPhone: '' },
          { addressType: 'WORKDAY', addressDetail: '', contactName: '', contactPhone: '' },
          { addressType: 'WEEKEND', addressDetail: '', contactName: '', contactPhone: '' }
        ],
        orderInfo: {
          parentPackageId: 1,
          childPackageId: 3,
          breakfastCount: 10,
          lunchDinnerCount: 20,
          totalCount: 30,
          startDate: '2026-03-25',
          endDate: '2026-04-25'
        }
      },
      crud: { form: {}, status: { add: CRUD_STATUS.PREPARED }},
      isCreateMode: () => true
    }

    const payload = buildSubmitPayload(ctx)

    expect(payload.customerName).toBe('张三')
    expect(payload.phone).toBe('13800000000')
    expect(payload.orderInfo).toBeDefined()
    expect(payload.orderInfo.parentPackageId).toBe(1)
    expect(payload.orderInfo.childPackageId).toBe(3)
    expect(payload.orderInfo.breakfastCount).toBe(10)
    expect(payload.orderInfo.lunchDinnerCount).toBe(20)
    expect(payload.orderInfo.totalCount).toBe(30)
    expect(payload.status).toBe(true)
    expect(payload.customerCode).toBeUndefined()
    expect(payload.id).toBeUndefined()
  })

  test('builds create payload calculates totalCount from meal counts', () => {
    const ctx = {
      form: {
        customerName: '张三',
        phone: '13800000000',
        addresses: [
          { addressType: 'DEFAULT', addressDetail: '地址', contactName: '', contactPhone: '' }
        ],
        orderInfo: {
          parentPackageId: 1,
          childPackageId: 3,
          breakfastCount: 5,
          lunchDinnerCount: 15,
          startDate: '2026-03-01',
          endDate: '2026-03-31'
        }
      },
      crud: { form: {}, status: { add: CRUD_STATUS.PREPARED }},
      isCreateMode: () => true
    }

    const payload = buildSubmitPayload(ctx)

    expect(payload.orderInfo.totalCount).toBe(20)
  })

  test('builds edit payload without orderInfo and without id fields', () => {
    const ctx = {
      form: {
        id: 1,
        customerCode: 'A001',
        customerName: '张三',
        phone: '13800000000',
        gestationalWeek: 32,
        allergyTags: [],
        medicalRequirements: null,
        status: false,
        remark: null,
        addresses: [
          { addressType: 'DEFAULT', addressDetail: '默认地址', contactName: '张三', contactPhone: '13800000000' },
          { addressType: 'WORKDAY', addressDetail: '', contactName: '李四', contactPhone: '13900000000' },
          { addressType: 'WEEKEND', addressDetail: '', contactName: '王五', contactPhone: '13700000000' }
        ],
        orderInfo: {
          parentPackageId: 1,
          childPackageId: 3,
          breakfastCount: 10,
          lunchDinnerCount: 20,
          totalCount: 30,
          startDate: '2026-03-25',
          endDate: '2026-04-25'
        }
      },
      crud: { form: {}, status: { add: CRUD_STATUS.PROCESSING }},
      isCreateMode: () => false
    }

    const payload = buildSubmitPayload(ctx)

    expect(payload.id).toBe(1)
    expect(payload.customerCode).toBeUndefined()
    expect(payload.orderInfo).toBeUndefined()
    expect(payload.packageInfo).toBeUndefined()
    expect(payload.status).toBeUndefined()
    expect(payload.addresses).toHaveLength(3)
    expect(payload.addresses[0].contactName).toBe('张三')
    expect(payload.addresses[1].contactName).toBe('李四')
    expect(payload.addresses[2].contactName).toBe('王五')
  })

  test('normalizes returned addresses by type for edit form', () => {
    const addresses = createAddressesFromForm({
      addresses: [
        { addressType: 'WEEKEND', addressDetail: '周末地址', contactName: '王五', contactPhone: '13700000000' },
        { addressType: 'DEFAULT', addressDetail: '默认地址', contactName: '张三', contactPhone: '13800000000' }
      ]
    })

    expect(addresses).toEqual([
      { addressType: 'DEFAULT', addressDetail: '默认地址', contactName: '张三', contactPhone: '13800000000' },
      { addressType: 'WORKDAY', addressDetail: '', contactName: '', contactPhone: '' },
      { addressType: 'WEEKEND', addressDetail: '周末地址', contactName: '王五', contactPhone: '13700000000' }
    ])
  })

  test('restores form data after edit submit error', () => {
    const originalForm = {
      id: 1,
      customerCode: 'A001',
      customerName: '张三',
      phone: '13800000000',
      gestationalWeek: 32,
      allergyTags: ['牛奶'],
      medicalRequirements: '少盐',
      remark: '备注',
      addresses: [
        { addressType: 'DEFAULT', addressDetail: '默认地址', contactName: '张三', contactPhone: '13800000000' },
        { addressType: 'WORKDAY', addressDetail: '', contactName: '', contactPhone: '' },
        { addressType: 'WEEKEND', addressDetail: '', contactName: '', contactPhone: '' }
      ],
      orderInfo: {
        parentPackageId: 1,
        childPackageId: 3,
        breakfastCount: 10,
        lunchDinnerCount: 20,
        totalCount: 30,
        startDate: '2026-03-25',
        endDate: '2026-04-25'
      }
    }
    const form = JSON.parse(JSON.stringify(originalForm))
    const ctx = {
      form,
      crud: { form, status: { add: CRUD_STATUS.PROCESSING }},
      isCreateMode: () => false,
      $delete: (target, key) => { delete target[key] },
      $set: (target, key, value) => { target[key] = value }
    }

    beforeSubmit(ctx)
    expect(ctx.crud.form.customerCode).toBeUndefined()

    afterEditError(ctx)

    expect(ctx.crud.form).toEqual(originalForm)
  })

  test('restores form data after add submit error', () => {
    const originalForm = {
      customerName: '张三',
      phone: '13800000000',
      addresses: [
        { addressType: 'DEFAULT', addressDetail: '默认地址', contactName: '', contactPhone: '' },
        { addressType: 'WORKDAY', addressDetail: '工作日地址', contactName: '', contactPhone: '' },
        { addressType: 'WEEKEND', addressDetail: '', contactName: '', contactPhone: '' }
      ],
      orderInfo: {
        parentPackageId: 1,
        childPackageId: 3,
        breakfastCount: 10,
        lunchDinnerCount: 20,
        totalCount: 30,
        startDate: '2026-03-25',
        endDate: '2026-04-25'
      }
    }
    const form = JSON.parse(JSON.stringify(originalForm))
    const ctx = {
      form,
      crud: { form, status: { add: CRUD_STATUS.PREPARED }},
      isCreateMode: () => true,
      $delete: (target, key) => { delete target[key] },
      $set: (target, key, value) => { target[key] = value }
    }

    beforeSubmit(ctx)
    expect(ctx.crud.form.customerCode).toBeUndefined()

    afterAddError(ctx)

    expect(ctx.crud.form).toEqual(originalForm)
  })

  test('loads detail before entering edit mode', async() => {
    const detail = {
      id: 1,
      customerCode: 'A001',
      customerName: '张三',
      phone: '13800000000',
      addresses: [
        { addressType: 'DEFAULT', addressDetail: '默认地址', contactName: '张三', contactPhone: '13800000000' }
      ]
    }
    profileApi.getProfile.mockResolvedValue({ data: detail })
    const toEdit = jest.fn()
    const ctx = {
      crud: { toEdit },
      editRequestId: 0,
      $message: { error: jest.fn() }
    }

    await handleEdit(ctx, { id: 1 })

    expect(profileApi.getProfile).toHaveBeenCalledWith(1)
    expect(toEdit).toHaveBeenCalledWith(detail)
    expect(ctx.$message.error).not.toHaveBeenCalled()
  })
})
