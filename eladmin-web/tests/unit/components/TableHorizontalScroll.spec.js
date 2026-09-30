/* eslint-env jest */
import { mount } from '@vue/test-utils'
import TableHorizontalScroll from '@/components/TableHorizontalScroll.vue'

function dimensions(element, values) {
  Object.keys(values).forEach(key => Object.defineProperty(element, key, { configurable: true, value: values[key] }))
}

function table() {
  const bodyWrapper = document.createElement('div')
  bodyWrapper.appendChild(document.createElement('table'))
  dimensions(bodyWrapper, { clientWidth: 300, scrollWidth: 1000 })
  return { bodyWrapper, headerWrapper: document.createElement('div'), footerWrapper: document.createElement('div') }
}

describe('TableHorizontalScroll', () => {
  let wrapper
  let observers
  let originalObserver
  beforeEach(() => {
    observers = []
    originalObserver = global.ResizeObserver
    global.ResizeObserver = class {
      constructor(callback) {
        this.callback = callback
        this.observe = jest.fn()
        this.disconnect = jest.fn()
        observers.push(this)
      }
    }
  })
  afterEach(() => {
    if (wrapper) wrapper.destroy()
    global.ResizeObserver = originalObserver
  })
  async function create(target) {
    wrapper = mount(TableHorizontalScroll, { propsData: { table: target }})
    await wrapper.vm.$nextTick()
    dimensions(wrapper.find('input').element, { clientWidth: 300 })
    wrapper.vm.updateMetrics()
    await wrapper.vm.$nextTick()
  }

  test('renders a persistent control and synchronizes body, header and footer on input', async() => {
    const target = table()
    await create(target)
    const range = wrapper.find('input[type="range"]')
    expect(range.exists()).toBe(true)
    expect(range.attributes('max')).toBe('700')
    expect(wrapper.vm.thumbWidth).toBe(90)
    range.element.value = '280'
    await range.trigger('input')
    expect(target.bodyWrapper.scrollLeft).toBe(280)
    expect(target.headerWrapper.scrollLeft).toBe(280)
    expect(target.footerWrapper.scrollLeft).toBe(280)
    target.bodyWrapper.scrollLeft = 450
    target.bodyWrapper.dispatchEvent(new Event('scroll'))
    await wrapper.vm.$nextTick()
    expect(range.element.value).toBe('450')
  })

  test('updates the range after columns resize and keeps a disabled track when everything fits', async() => {
    const target = table()
    await create(target)
    expect(observers[0].observe).toHaveBeenCalledWith(target.bodyWrapper)
    expect(observers[0].observe).toHaveBeenCalledWith(target.bodyWrapper.querySelector('table'))
    wrapper.vm.scrollTo(650)
    dimensions(target.bodyWrapper, { scrollWidth: 500 })
    observers[0].callback()
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.maxScroll).toBe(200)
    expect(target.bodyWrapper.scrollLeft).toBe(200)
    dimensions(target.bodyWrapper, { scrollWidth: 300 })
    window.dispatchEvent(new Event('resize'))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('input').exists()).toBe(true)
    expect(wrapper.find('input').element.disabled).toBe(true)
    expect(wrapper.vm.thumbWidth).toBe(300)
    expect(target.headerWrapper.scrollLeft).toBe(0)
  })

  test('rebinds when the table changes and releases listeners when leaving the page', async() => {
    const oldTable = table()
    const remove = jest.spyOn(oldTable.bodyWrapper, 'removeEventListener')
    await create(oldTable)
    const firstObserver = observers[0]
    const nextTable = table()
    await wrapper.setProps({ table: nextTable })
    await wrapper.vm.$nextTick()
    expect(remove).toHaveBeenCalledWith('scroll', wrapper.vm.syncFromTable)
    expect(firstObserver.disconnect).toHaveBeenCalledTimes(1)
    wrapper.vm.deactivate()
    expect(observers[1].disconnect).toHaveBeenCalledTimes(1)
    wrapper.vm.activate()
    await wrapper.vm.$nextTick()
    expect(observers).toHaveLength(3)
    wrapper.destroy()
    expect(observers[2].disconnect).toHaveBeenCalledTimes(1)
    wrapper = null
  })
})
