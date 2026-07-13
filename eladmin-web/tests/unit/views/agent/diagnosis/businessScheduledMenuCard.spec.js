import BusinessScheduledMenuCard from '@/views/agent/diagnosis/components/businessScheduledMenuCard'

describe('BusinessScheduledMenuCard', () => {
  test('keeps public menus grouped by returned meal type', () => {
    const result = {
      groups: [
        { mealTypeCode: 'LUNCH', mealTypeName: '午餐', total: 2, items: [{ dishName: '番茄炒蛋' }] },
        { mealTypeCode: 'DINNER', mealTypeName: '晚餐', total: 1, items: [{ dishName: '清蒸鲈鱼' }] }
      ]
    }

    expect(BusinessScheduledMenuCard.computed.groups.call({ result })).toEqual(result.groups)
  })
})
