/* eslint-env jest */

function hasExcludedMeal(ctx, date, mealType) {
  return ctx.calendarExcludedDates.some(item => item.date === date && Array.isArray(item.mealTypes) && item.mealTypes.includes(mealType))
}

function isBaseMeal(day, mealType) {
  return Array.isArray(day.baseMealTypes) && day.baseMealTypes.includes(mealType)
}

function isMealAdded(ctx, day, mealType) {
  return ctx.calendarAdditions.some(item => item.date === day.date && item.mealType === mealType)
}

function resolveAdditionOrderId(ctx, mealType) {
  const row = ctx.rows.find(item => item.customerId === ctx.selectedRow.customerId && (
    (mealType === 'BREAKFAST' && item.mealBucket === 'BREAKFAST') ||
    (mealType !== 'BREAKFAST' && item.mealBucket === 'LUNCH_DINNER')
  ))
  return row && row.orderId
}

function addExcludedMeal(ctx, date, mealType) {
  let item = ctx.calendarExcludedDates.find(value => value.date === date)
  if (!item) {
    item = { date, mealTypes: [] }
    ctx.calendarExcludedDates.push(item)
  }
  if (!item.mealTypes.includes(mealType)) {
    item.mealTypes.push(mealType)
  }
}

function removeExcludedMeal(ctx, date, mealType) {
  const item = ctx.calendarExcludedDates.find(value => value.date === date)
  if (!item) {
    return
  }
  item.mealTypes = item.mealTypes.filter(value => value !== mealType)
  if (item.mealTypes.length === 0) {
    ctx.calendarExcludedDates = ctx.calendarExcludedDates.filter(value => value.date !== date)
  }
}

function toggleMeal(ctx, day, mealType) {
  if (!day.currentMonth) {
    return
  }
  if (hasExcludedMeal(ctx, day.date, mealType)) {
    removeExcludedMeal(ctx, day.date, mealType)
    return
  }
  if (isMealAdded(ctx, day, mealType)) {
    ctx.calendarAdditions = ctx.calendarAdditions.filter(item => !(item.date === day.date && item.mealType === mealType))
    return
  }
  if (isBaseMeal(day, mealType)) {
    addExcludedMeal(ctx, day.date, mealType)
    return
  }
  const orderId = resolveAdditionOrderId(ctx, mealType)
  if (orderId) {
    ctx.calendarAdditions.push({ orderId, date: day.date, mealType, remark: '' })
  }
}

function mealButtonClass(ctx, day, mealType) {
  const excluded = hasExcludedMeal(ctx, day.date, mealType)
  const scheduled = Array.isArray(day.scheduledMealTypes) && day.scheduledMealTypes.includes(mealType)
  return {
    'readonly-calendar__meal-button--base': isBaseMeal(day, mealType),
    'readonly-calendar__meal-button--excluded': excluded,
    'readonly-calendar__meal-button--added': isMealAdded(ctx, day, mealType),
    'readonly-calendar__meal-button--scheduled': scheduled,
    'readonly-calendar__meal-button--scheduled-cancelled': scheduled && excluded
  }
}

describe('CustomerMealStats editable calendar state', () => {
  test('toggles base meal into exclusion and restores it', () => {
    const ctx = { calendarExcludedDates: [], calendarAdditions: [], rows: [], selectedRow: { customerId: 1 }}
    const day = { date: '2026-05-24', currentMonth: true, baseMealTypes: ['LUNCH'], scheduledMealTypes: [] }

    toggleMeal(ctx, day, 'LUNCH')
    expect(ctx.calendarExcludedDates).toEqual([{ date: '2026-05-24', mealTypes: ['LUNCH'] }])

    toggleMeal(ctx, day, 'LUNCH')
    expect(ctx.calendarExcludedDates).toEqual([])
  })

  test('toggles non-base meal into manual addition and removes it', () => {
    const ctx = {
      calendarExcludedDates: [],
      calendarAdditions: [],
      selectedRow: { customerId: 1 },
      rows: [{ customerId: 1, mealBucket: 'LUNCH_DINNER', orderId: 99 }]
    }
    const day = { date: '2026-05-25', currentMonth: true, baseMealTypes: [], scheduledMealTypes: [] }

    toggleMeal(ctx, day, 'DINNER')
    expect(ctx.calendarAdditions).toEqual([{ orderId: 99, date: '2026-05-25', mealType: 'DINNER', remark: '' }])

    toggleMeal(ctx, day, 'DINNER')
    expect(ctx.calendarAdditions).toEqual([])
  })

  test('allows scheduled base meal to be selected for cancellation', () => {
    const ctx = { calendarExcludedDates: [], calendarAdditions: [], rows: [], selectedRow: { customerId: 1 }}
    const day = {
      date: '2026-05-25',
      currentMonth: true,
      baseMealTypes: ['LUNCH'],
      scheduledMealTypes: ['LUNCH']
    }

    toggleMeal(ctx, day, 'LUNCH')

    expect(ctx.calendarExcludedDates).toEqual([{ date: '2026-05-25', mealTypes: ['LUNCH'] }])
  })

  test('marks scheduled cancellation state for grey check rendering', () => {
    const ctx = {
      calendarExcludedDates: [{ date: '2026-05-25', mealTypes: ['LUNCH'] }],
      calendarAdditions: []
    }
    const day = {
      date: '2026-05-25',
      currentMonth: true,
      baseMealTypes: ['LUNCH'],
      scheduledMealTypes: ['LUNCH']
    }

    expect(mealButtonClass(ctx, day, 'LUNCH')).toMatchObject({
      'readonly-calendar__meal-button--scheduled': true,
      'readonly-calendar__meal-button--excluded': true,
      'readonly-calendar__meal-button--scheduled-cancelled': true
    })
  })
})
