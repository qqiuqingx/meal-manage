/* eslint-env jest */
/* eslint-disable no-console */
import { shallowMount } from '@vue/test-utils'
import MealScheduleCalendar from '@/components/Calendar/MealScheduleCalendar.vue'

// Suppress console.log noise from the calendar component during tests
const originalLog = console.log
beforeAll(() => { console.log = jest.fn() })
afterAll(() => { console.log = originalLog })

describe('MealScheduleCalendar.vue', () => {
  describe('hideSummary prop', () => {
    // After GREEN: hideSummary prop will be defined in props section of the component
    test('hideSummary prop is accepted as a valid prop', () => {
      const wrapper = shallowMount(MealScheduleCalendar, {
        propsData: {
          value: [],
          hideSummary: true
        },
        stubs: {
          'CalendarDay': { template: '<div class="calendar-day"></div>' },
          'MealTypeSelector': { template: '<div class="meal-type-selector"></div>' },
          'el-button': { template: '<button><slot></slot></button>' },
          'el-button-group': { template: '<div><slot></slot></div>' },
          'el-dialog': { template: '<div class="el-dialog" :visible.sync="visible"><slot></slot></div>', props: ['visible'] },
          'el-message': { template: '<div class="el-message"></div>' }
        }
      })
      // hideSummary=true should not cause a prop validation error
      expect(wrapper.vm.$props.hideSummary).toBe(true)
      wrapper.destroy()
    })

    test('calendar-summary is hidden when hideSummary=true (v-if=!hideSummary)', () => {
      const wrapper = shallowMount(MealScheduleCalendar, {
        propsData: {
          value: [{ date: '2026-04-15', mealTypes: ['BREAKFAST'] }],
          hideSummary: true
        },
        stubs: {
          'CalendarDay': { template: '<div class="calendar-day"></div>' },
          'MealTypeSelector': { template: '<div class="meal-type-selector"></div>' },
          'el-button': { template: '<button><slot></slot></button>' },
          'el-button-group': { template: '<div><slot></slot></div>' },
          'el-dialog': { template: '<div class="el-dialog" :visible.sync="visible"><slot></slot></div>', props: ['visible'] },
          'el-message': { template: '<div class="el-message"></div>' }
        }
      })

      // After GREEN implementation: <div v-if="!hideSummary" class="calendar-summary"> exists
      // When hideSummary=true → v-if evaluates to false → element NOT rendered
      const summaryHtml = wrapper.find('.calendar-summary').exists()
      expect(summaryHtml).toBe(false)
      wrapper.destroy()
    })

    test('calendar-summary is shown when hideSummary=false (default)', () => {
      const wrapper = shallowMount(MealScheduleCalendar, {
        propsData: {
          value: [{ date: '2026-04-15', mealTypes: ['BREAKFAST'] }],
          hideSummary: false
        },
        stubs: {
          'CalendarDay': { template: '<div class="calendar-day"></div>' },
          'MealTypeSelector': { template: '<div class="meal-type-selector"></div>' },
          'el-button': { template: '<button><slot></slot></button>' },
          'el-button-group': { template: '<div><slot></slot></div>' },
          'el-dialog': { template: '<div class="el-dialog" :visible.sync="visible"><slot></slot></div>', props: ['visible'] },
          'el-message': { template: '<div class="el-message"></div>' }
        }
      })

      // After GREEN: v-if="!hideSummary" with hideSummary=false renders the summary
      // Before GREEN: the div always renders, so this test will pass (it's a baseline)
      const summaryHtml = wrapper.find('.calendar-summary').exists()
      expect(summaryHtml).toBe(true)
      wrapper.destroy()
    })
  })

  describe('meal selector reopen behavior', () => {
    test('can reopen meal selector for the same selected date after close', async() => {
      const wrapper = shallowMount(MealScheduleCalendar, {
        propsData: {
          value: [{ date: '2026-05-06', mealTypes: ['LUNCH', 'DINNER'] }]
        },
        stubs: {
          'CalendarDay': { template: '<div class="calendar-day"></div>' },
          'MealTypeSelector': { template: '<div class="meal-type-selector"></div>' },
          'el-button': { template: '<button><slot></slot></button>' },
          'el-button-group': { template: '<div><slot></slot></div>' },
          'el-dialog': { template: '<div class="el-dialog"><slot></slot></div>', props: ['visible'] },
          'el-message': { template: '<div class="el-message"></div>' }
        }
      })

      const date = new Date('2026-05-06T00:00:00')
      const mealTypes = ['LUNCH', 'DINNER']

      wrapper.vm.openMealSelector(date, mealTypes)
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.showMealSelector).toBe(true)

      wrapper.vm.handleMealSelectorVisibleChange(false)
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.showMealSelector).toBe(false)
      expect(wrapper.vm.selectedDate).toBe(null)

      wrapper.vm.openMealSelector(date, mealTypes)
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.showMealSelector).toBe(true)
      expect(wrapper.vm.selectedDate).toEqual(date)

      wrapper.destroy()
    })

    test('opening another selected date while visible updates the selector content', async() => {
      const wrapper = shallowMount(MealScheduleCalendar, {
        propsData: {
          value: [{ date: '2026-05-06', mealTypes: ['LUNCH', 'DINNER'] }]
        },
        stubs: {
          'CalendarDay': { template: '<div class="calendar-day"></div>' },
          'MealTypeSelector': { template: '<div class="meal-type-selector"></div>' },
          'el-button': { template: '<button><slot></slot></button>' },
          'el-button-group': { template: '<div><slot></slot></div>' },
          'el-dialog': { template: '<div class="el-dialog"><slot></slot></div>', props: ['visible'] },
          'el-message': { template: '<div class="el-message"></div>' }
        }
      })

      const firstDate = new Date('2026-05-06T00:00:00')
      const secondDate = new Date('2026-05-07T00:00:00')

      wrapper.vm.openMealSelector(firstDate, ['LUNCH'])
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.showMealSelector).toBe(true)
      expect(wrapper.vm.selectedDate).toEqual(firstDate)

      wrapper.vm.openMealSelector(secondDate, ['DINNER'])
      await wrapper.vm.$nextTick()

      expect(wrapper.vm.showMealSelector).toBe(true)
      expect(wrapper.vm.selectedDate).toEqual(secondDate)
      expect(wrapper.vm.selectedDateMealTypes).toEqual(['DINNER'])

      wrapper.destroy()
    })
  })

  describe('clear button behavior', () => {
    test('clear confirmation can be triggered repeatedly with a foreground z-index and resets selection', async() => {
      const confirmMock = jest.fn(() => Promise.resolve())
      const warningMock = jest.fn()
      const wrapper = shallowMount(MealScheduleCalendar, {
        propsData: {
          value: [{ date: '2026-05-06', mealTypes: ['LUNCH', 'DINNER'] }],
          startDate: '2026-05-01',
          endDate: '2026-05-31'
        },
        mocks: {
          $confirm: confirmMock,
          $message: { warning: warningMock }
        },
        stubs: {
          'CalendarDay': { template: '<div class="calendar-day"></div>' },
          'MealTypeSelector': { template: '<div class="meal-type-selector"></div>' },
          'el-button': { template: '<button><slot></slot></button>' },
          'el-button-group': { template: '<div><slot></slot></div>' },
          'el-dialog': { template: '<div class="el-dialog"><slot></slot></div>', props: ['visible'] },
          'el-message': { template: '<div class="el-message"></div>' }
        }
      })

      wrapper.vm.clearAllDates()
      await Promise.resolve()
      await wrapper.vm.$nextTick()

      wrapper.vm.clearAllDates()
      await Promise.resolve()
      await wrapper.vm.$nextTick()

      expect(confirmMock).toHaveBeenCalledTimes(2)
      expect(warningMock).not.toHaveBeenCalled()
      expect(confirmMock.mock.calls[0][2].zIndex).toEqual(expect.any(Number))
      expect(confirmMock.mock.calls[1][2].zIndex).toBeGreaterThan(confirmMock.mock.calls[0][2].zIndex)

      const inputEvents = wrapper.emitted('input') || []
      expect(inputEvents.length).toBeGreaterThan(0)
      expect(inputEvents[inputEvents.length - 1][0]).toEqual([])

      wrapper.destroy()
    })
  })
})
