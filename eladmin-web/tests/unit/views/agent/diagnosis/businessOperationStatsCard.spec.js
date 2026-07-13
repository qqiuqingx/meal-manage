/* eslint-env jest */
import BusinessOperationStatsCard from '@/views/agent/diagnosis/components/businessOperationStatsCard.vue'

describe('BusinessOperationStatsCard', () => {
  test('renders each requested metric with its own meal type breakdown', () => {
    const ctx = {
      result: {
        reportMetrics: ['DAILY_SCHEDULED_CUSTOMER_COUNT', 'DAILY_UNVERIFIED_CUSTOMER_COUNT'],
        breakdownDimensions: ['MEAL_TYPE'],
        metricMealTypeBreakdown: {
          DAILY_SCHEDULED_CUSTOMER_COUNT: { BREAKFAST: 3, LUNCH: 5 },
          DAILY_UNVERIFIED_CUSTOMER_COUNT: { BREAKFAST: 1, DINNER: 2 }
        }
      },
      mealTypeText: BusinessOperationStatsCard.methods.mealTypeText,
      dimensionLabel: BusinessOperationStatsCard.methods.dimensionLabel
    }
    Object.defineProperty(ctx, 'breakdownMetrics', {
      get: () => BusinessOperationStatsCard.computed.breakdownMetrics.call(ctx)
    })
    Object.defineProperty(ctx, 'breakdownSource', {
      get: () => BusinessOperationStatsCard.computed.breakdownSource.call(ctx)
    })
    Object.defineProperty(ctx, 'isMealTypeBreakdown', {
      get: () => BusinessOperationStatsCard.computed.isMealTypeBreakdown.call(ctx)
    })
    ctx.dimensionText = BusinessOperationStatsCard.methods.dimensionText

    expect(ctx.breakdownMetrics).toEqual(['DAILY_SCHEDULED_CUSTOMER_COUNT', 'DAILY_UNVERIFIED_CUSTOMER_COUNT'])
    expect(BusinessOperationStatsCard.computed.breakdownRows.call(ctx)).toEqual([
      { dimension: '早餐', DAILY_SCHEDULED_CUSTOMER_COUNT: 3, DAILY_UNVERIFIED_CUSTOMER_COUNT: 1 },
      { dimension: '午餐', DAILY_SCHEDULED_CUSTOMER_COUNT: 5, DAILY_UNVERIFIED_CUSTOMER_COUNT: 0 },
      { dimension: '晚餐', DAILY_SCHEDULED_CUSTOMER_COUNT: 0, DAILY_UNVERIFIED_CUSTOMER_COUNT: 2 }
    ])
  })

  test('uses the returned package dimension instead of relabeling it as meal type', () => {
    const ctx = {
      result: {
        reportMetrics: ['DAILY_UNSCHEDULED_CUSTOMER_COUNT'],
        breakdownDimensions: ['PACKAGE'],
        metricDimensionBreakdown: { DAILY_UNSCHEDULED_CUSTOMER_COUNT: { '轻食套餐': 4, '未配置套餐': 1 } }
      },
      mealTypeText: BusinessOperationStatsCard.methods.mealTypeText,
      dimensionLabel: BusinessOperationStatsCard.methods.dimensionLabel
    }
    Object.defineProperty(ctx, 'breakdownSource', { get: () => BusinessOperationStatsCard.computed.breakdownSource.call(ctx) })
    Object.defineProperty(ctx, 'breakdownMetrics', { get: () => BusinessOperationStatsCard.computed.breakdownMetrics.call(ctx) })
    Object.defineProperty(ctx, 'isMealTypeBreakdown', { get: () => BusinessOperationStatsCard.computed.isMealTypeBreakdown.call(ctx) })
    ctx.dimensionText = BusinessOperationStatsCard.methods.dimensionText

    expect(BusinessOperationStatsCard.computed.breakdownLabel.call(ctx)).toBe('套餐')
    expect(BusinessOperationStatsCard.computed.breakdownRows.call(ctx)).toEqual([
      { dimension: '轻食套餐', DAILY_UNSCHEDULED_CUSTOMER_COUNT: 4 },
      { dimension: '未配置套餐', DAILY_UNSCHEDULED_CUSTOMER_COUNT: 1 }
    ])
  })
})
