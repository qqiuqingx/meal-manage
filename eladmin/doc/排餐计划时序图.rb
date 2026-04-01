sequenceDiagram
    autonumber

    participant Client as 调用方
    participant Controller as MealPlanController
    participant Service as MealPlanService
    participant ScheduleUtil as 排期计算器
    participant DishRepo as dish / dish_ingredient_relation
    participant OrderRepo as customer_order
    participant CustomerRepo as customer_profile
    participant PackageRepo as sub_package
    participant PlanRepo as meal_plan / meal_plan_customer / meal_plan_customer_item

    Client->>Controller: POST /api/meal-plan/generate(adate, mealType)
    Controller->>Service: generateMealPlan(adate, mealType)

    Note over Service: 1. 参数校验

    Service->>ScheduleUtil: calcScheduleKey(adate)
    ScheduleUtil-->>Service: scheduleKey(如 1-1 / 2-3)

    Note over Service: 2. 生成当天候选菜池

    Service->>DishRepo: 查询启用菜品(enabled=1)
    DishRepo-->>Service: dish列表

    Service->>Service: 过滤 meal_types 包含 mealType
    Service->>Service: 过滤 schedule 包含 scheduleKey
    Service->>Service: 过滤 meal_packages 包含 parentPackageId
    Service->>Service: 按 parentPackageId + dish_type 分组\nMAIN/SIDE/VEGETABLE/SOUP/RICE

    Note over Service: 3. 查询符合条件订单

    Service->>OrderRepo: 查询 customer_order\nstatus=1, remaining_count>0,\nstart_date<=adate<=end_date
    OrderRepo-->>Service: 订单列表

    loop 遍历订单
        Service->>Service: 判断 meal_type 是否匹配\n(LUNCH/DINNER/ALL)
        Service->>Service: 判断 schedule_mode\n(DAILY/WEEKDAY/WEEKEND/SCHEDULE)

        alt 订单不符合排餐条件
            Service->>Service: 跳过当前订单
        else 订单符合排餐条件
            Note over Service: 4. 加载客户与套餐规则

            Service->>CustomerRepo: 根据 customer_id 查询 customer_profile
            CustomerRepo-->>Service: 客户信息 + allergy_tags

            Service->>PackageRepo: 根据 child_package_id 查询 sub_package
            PackageRepo-->>Service: meat_count, veg_count,\ninclude_soup, include_rice

            Note over Service: 5. 为当前客户分配菜品

            Service->>Service: 按 meatCount 选择荤菜\n=1: MAIN优先, SIDE兜底\n=2: 固定 MAIN + SIDE
            Service->>Service: 从候选池挑选素菜\nVEGETABLE
            Service->>Service: 如需汤则挑选 SOUP
            Service->>Service: 如需米饭则挑选 RICE

            loop 候选菜过敏校验
                Service->>DishRepo: 查询 dish_ingredient_relation by dish_id
                DishRepo-->>Service: 菜品配料列表
                Service->>Service: 配料名称 vs allergy_tags 判断是否过敏
            end

            alt 满足子套餐规则
                Service->>PlanRepo: 保存 meal_plan_customer 成功记录
                PlanRepo-->>Service: customer_plan_id

                loop 遍历分配菜品
                    Service->>PlanRepo: 保存 meal_plan_customer_item
                    PlanRepo-->>Service: ok
                end

            else 不满足子套餐规则
                Service->>PlanRepo: 保存 meal_plan_customer 失败记录(FAILED, fail_reason)
                PlanRepo-->>Service: ok
            end
        end
    end

    Note over Service: 6. 汇总结果
    Service->>PlanRepo: 更新 meal_plan.success_count / fail_count / status
    Service-->>Controller: successCount / failCount / failDetails
    Controller-->>Client: 返回排餐结果
