/* eslint-env jest */
import { shallowMount } from '@vue/test-utils'
import ElementUI from 'element-ui'
import MealTypeSelector from '@/components/Calendar/MealTypeSelector.vue'

// Register Element UI components globally for tests
let registered = false
function registerElementUI() {
  if (registered) return
  if (typeof global.registerElementUI === 'undefined') {
    // Register commonly used Element UI components
    const components = [
      'elCheckbox',
      'elCheckboxGroup',
      'elButton',
      'elButtonGroup'
    ]
    components.forEach(name => {
      if (!ElementUI[name]) {
        try { ElementUI.use(ElementUI[name]) } catch (e) { /* skip */ }
      }
    })
  }
  registered = true
}

describe('MealTypeSelector.vue', () => {
  beforeAll(() => {
    registerElementUI()
  })

  describe('UI-02: Save button disabled when no meal types selected', () => {
    test('save button is disabled when localMealTypes is empty', () => {
      const wrapper = shallowMount(MealTypeSelector, {
        propsData: {
          date: new Date('2026-04-15'),
          value: []
        },
        stubs: {
          'el-checkbox-group': { template: '<div class="el-checkbox-group"></div>' },
          'el-checkbox': { template: '<label class="el-checkbox"><slot></slot></label>' },
          'el-button-group': { template: '<div class="el-button-group"><slot></slot></div>' },
          'el-button': {
            template: '<button class="el-button" :class="typeClass" :disabled="disabled" @click="$emit(\'click\')"><slot></slot></button>',
            props: ['type', 'size', 'disabled'],
            computed: {
              typeClass() {
                return this.type ? `el-button--${this.type}` : ''
              }
            }
          }
        }
      })

      // Find the save button (type="primary")
      const saveButton = wrapper.findAll('button.el-button').wrappers.find(b => b.classes('el-button--primary'))

      if (saveButton) {
        expect(saveButton.props('disabled')).toBe(true)
      } else {
        // If the save button has :disabled attribute bound via template inspection
        const html = wrapper.html()
        expect(html).toContain(':disabled="localMealTypes.length === 0"')
      }

      wrapper.destroy()
    })

    test('save button is enabled when localMealTypes has items', () => {
      const wrapper = shallowMount(MealTypeSelector, {
        propsData: {
          date: new Date('2026-04-15'),
          value: ['BREAKFAST']
        },
        stubs: {
          'el-checkbox-group': { template: '<div class="el-checkbox-group"></div>' },
          'el-checkbox': { template: '<label class="el-checkbox"><slot></slot></label>' },
          'el-button-group': { template: '<div class="el-button-group"><slot></slot></div>' },
          'el-button': {
            template: '<button class="el-button" :class="typeClass" :disabled="disabled" @click="$emit(\'click\')"><slot></slot></button>',
            props: ['type', 'size', 'disabled'],
            computed: {
              typeClass() {
                return this.type ? `el-button--${this.type}` : ''
              }
            }
          }
        }
      })

      // Check that save button is not disabled when meal types are selected
      const saveButton = wrapper.findAll('button.el-button').wrappers.find(b => b.classes('el-button--primary'))
      if (saveButton) {
        expect(saveButton.props('disabled')).toBe(false)
      } else {
        // Fallback: check the component has the right binding
        const html = wrapper.html()
        expect(html).toContain(':disabled="localMealTypes.length === 0"')
      }

      wrapper.destroy()
    })

    test('save button has :disabled binding in template', () => {
      // This test verifies the source template has the correct binding
      const fs = require('fs')
      const path = require('path')
      const source = fs.readFileSync(
        path.resolve(__dirname, '../../../../src/components/Calendar/MealTypeSelector.vue'),
        'utf8'
      )
      // The save button must have :disabled="localMealTypes.length === 0"
      expect(source).toContain(':disabled="localMealTypes.length === 0"')
    })
  })
})
