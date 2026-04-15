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
})
