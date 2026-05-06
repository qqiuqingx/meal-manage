-- 首次成功排餐判定查询
-- 维度：order_id + meal_type
-- 仅 status=1（成功）参与首次判定，失败记录不占用首次资格
-- 使用 NOT EXISTS 模式：当前记录是该 order_id + meal_type 下最早的成功记录

-- 单条查询示例（替换 customerPlanId 为实际值）
SELECT current_rec.id
FROM meal_plan_customer current_rec
JOIN meal_plan current_plan ON current_plan.id = current_rec.meal_plan_id
WHERE current_rec.deleted = 0
  AND current_plan.deleted = 0
  AND current_rec.status = 1
  AND current_rec.id IN (101, 102)
  AND NOT EXISTS (
    SELECT 1
    FROM meal_plan_customer previous_rec
    JOIN meal_plan previous_plan ON previous_plan.id = previous_rec.meal_plan_id
    WHERE previous_rec.deleted = 0
      AND previous_plan.deleted = 0
      AND previous_rec.status = 1
      AND previous_rec.order_id = current_rec.order_id
      AND previous_plan.meal_type = current_plan.meal_type
      AND (
          previous_plan.record_date < current_plan.record_date
          OR (previous_plan.record_date = current_plan.record_date AND previous_rec.id < current_rec.id)
      )
  );
