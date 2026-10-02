/* eslint-env jest */
import request from '@/utils/request'
import { confirmCustomerImport } from '@/api/customer/profile'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({})))

describe('customer import confirmation HTTP payload', () => {
  test('sends the preview order state with full monthly import', async() => {
    const file = new File(['anonymous'], 'anonymous.xlsx')
    await confirmCustomerImport(file, 'file-hash', 'dictionary-hash', '2026-10-20', false, 'order-hash')
    const call = request.mock.calls[request.mock.calls.length - 1][0]
    expect(call.url).toBe('/api/customerProfile/import/confirm')
    expect(call.data.get('orderStateHash')).toBe('order-hash')
    expect(call.data.get('fileHash')).toBe('file-hash')
    expect(call.data.get('importDate')).toBe('2026-10-20')
    expect(call.data.get('dietOnly')).toBe('false')
  })

  test('keeps second-sheet mode without an order state marker', async() => {
    await confirmCustomerImport(new File(['anonymous'], 'anonymous.xlsx'), 'file-hash', 'dictionary-hash', '2026-10-20', true)
    const call = request.mock.calls[request.mock.calls.length - 1][0]
    expect(call.data.get('dietOnly')).toBe('true')
    expect(call.data.get('orderStateHash')).toBeNull()
  })
})
