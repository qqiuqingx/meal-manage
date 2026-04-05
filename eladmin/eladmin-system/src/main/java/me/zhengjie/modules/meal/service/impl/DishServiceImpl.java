package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.modules.meal.domain.CustomerDietaryRestrictions;
import me.zhengjie.modules.meal.domain.CustomerMenuRecord;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.DishScheduleRecord;
import me.zhengjie.modules.meal.domain.dto.DishQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishScheduleResult;
import me.zhengjie.modules.meal.domain.dto.DishScheduleStats;
import me.zhengjie.modules.meal.domain.dto.DailyCustomerStats;
import me.zhengjie.modules.meal.domain.dto.DishScheduleRecordQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishScheduleRecordVO;
import me.zhengjie.modules.meal.domain.dto.DishIngredientDto;
import me.zhengjie.modules.meal.domain.DishIngredientRelation;
import me.zhengjie.modules.meal.domain.enums.MealPackageEnum;
import me.zhengjie.modules.meal.domain.enums.DishTypeEnum;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.meal.mapper.CustomerDietaryRestrictionsMapper;
import me.zhengjie.modules.meal.mapper.CustomerMenuRecordMapper;
import me.zhengjie.modules.meal.service.CustomerMenuRecordService;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.DishScheduleRecordMapper;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.system.service.DictDetailService;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.system.domain.DictDetail;
import me.zhengjie.utils.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.zhengjie.modules.meal.service.DishService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import me.zhengjie.utils.PageUtil;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import me.zhengjie.utils.PageResult;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 菜品服务实现
 * @author qqx
 * @date 2026-03-14
 **/
@Service
@RequiredArgsConstructor
@Slf4j
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {

    private final DishMapper dishMapper;
    private final CustomerDietaryRestrictionsMapper customerDietaryRestrictionsMapper;
    private final DishScheduleRecordMapper dishScheduleRecordMapper;
    private final CustomerMenuRecordMapper customerMenuRecordMapper;
    private final CustomerMenuRecordService customerMenuRecordService;
    private final DishIngredientMapper dishIngredientMapper;
    private final DictDetailService dictDetailService;
    private final CustomerOrderMapper customerOrderMapper;
    private final ParentPackageMapper parentPackageMapper;
    private final CustomerProfileMapper customerProfileMapper;
    private final MealPlanMapper mealPlanMapper;
    private final MealPlanCustomerMapper mealPlanCustomerMapper;

    private static final String[] DISH_TYPES = {"MAIN", "SIDE", "SOUP", "VEGETABLE", "RICE"};
    private static final String[] MEAL_TYPES = {"LUNCH", "DINNER"};
    private static final String MEAL_TYPE_ALL = "ALL";
    // 超出星期有效范围（1-7）的值，用于表示菜品在该周无排期，排期天距离此值的差值最大，排序时会排在末尾
    private static final int NO_SCHEDULE_DAY = 8;

    private static final Map<String, String> MEAL_TYPE_CN = new LinkedHashMap<>();
    static {
        MEAL_TYPE_CN.put("LUNCH", "午餐");
        MEAL_TYPE_CN.put("DINNER", "晚餐");
    }

    private String mealTypeCn(String mealType) {
        return MEAL_TYPE_CN.getOrDefault(mealType, mealType);
    }

    private String dishTypeCn(String dishType) {
        DishTypeEnum e = DishTypeEnum.fromCode(dishType);
        return e != null ? e.getDesc() : dishType;
    }

    /**
     * 将配料DTO列表拼接为逗号分隔的字符串（用于存储到 dish_ingredients 字段）
     */
    private String buildIngredientsStr(List<DishIngredientDto> ingredientList) {
        if (ingredientList == null || ingredientList.isEmpty()) {
            return null;
        }
        return ingredientList.stream()
            .map(DishIngredientDto::getIngredientName)
            .filter(name -> name != null && !name.isEmpty())
            .collect(Collectors.joining(", "));
    }

    private List<DishScheduleRecord> findActiveScheduleRecords(String date, String mealType) {
        QueryWrapper<DishScheduleRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("record_date", date).eq("meal_type", mealType).eq("deleted", false);
        return dishScheduleRecordMapper.selectList(wrapper);
    }

    private void softDeleteCustomerMenus(List<DishScheduleRecord> records, Integer customerId) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // 收集所有 recordId，一次性查询所有相关菜单（消除 N+1）
        List<Integer> recordIds = records.stream()
                .map(DishScheduleRecord::getId)
                .collect(Collectors.toList());

        QueryWrapper<CustomerMenuRecord> menuWrapper = new QueryWrapper<>();
        menuWrapper.in("record_id", recordIds)
                .eq("customer_id", customerId)
                .eq("deleted", false);

        List<CustomerMenuRecord> existingMenus = customerMenuRecordMapper.selectList(menuWrapper);

        if (!existingMenus.isEmpty()) {
            existingMenus.forEach(menu -> menu.setDeleted(true));
            customerMenuRecordService.updateBatchById(existingMenus);
        }
    }

    private int countActiveCustomersForRecord(Integer recordId) {
        QueryWrapper<CustomerMenuRecord> menuWrapper = new QueryWrapper<>();
        menuWrapper.eq("record_id", recordId).eq("deleted", false);
        List<CustomerMenuRecord> menuRecords = customerMenuRecordMapper.selectList(menuWrapper);
        return (int) menuRecords.stream()
                .map(CustomerMenuRecord::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    /**
     * 批量填充菜品配料列表（一次性 IN 查询，消除 N+1）
     */
    private void fillIngredientsBatch(List<Dish> dishes) {
        if (dishes == null || dishes.isEmpty()) return;
        List<Integer> dishIds = dishes.stream().map(Dish::getId).collect(Collectors.toList());
        List<DishIngredientRelation> relations = dishIngredientMapper.findRelationsByDishIds(dishIds);
        Map<Integer, List<DishIngredientDto>> map = new HashMap<>();
        for (DishIngredientRelation r : relations) {
            DishIngredientDto dto = new DishIngredientDto();
            dto.setIngredientId(r.getIngredientId());
            dto.setIngredientName(r.getIngredientName());
            dto.setQuantity(r.getQuantity());
            dto.setUnit(r.getUnit());
            dto.setRemark(r.getRemark());
            map.computeIfAbsent(r.getDishId(), k -> new ArrayList<>()).add(dto);
        }
        for (Dish dish : dishes) {
            dish.setIngredientList(map.get(dish.getId()));
        }
    }

    /**
     * 批量填充菜品套餐详情（一次性 IN 查询，消除 N+1）
     */
    private void fillMealPackageDetailsBatch(List<Dish> dishes) {
        if (dishes == null || dishes.isEmpty()) return;
        // 收集所有套餐ID（兼容字符串和数字类型）
        Set<Object> packageIds = new HashSet<>();
        for (Dish dish : dishes) {
            if (dish.getMealPackages() != null) {
                packageIds.addAll(dish.getMealPackages());
            }
        }
        if (packageIds.isEmpty()) return;

        // 转换为 Set<Long> 用于数据库查询
        Set<Long> numericIds = new HashSet<>();
        Set<String> stringIds = new HashSet<>();
        for (Object id : packageIds) {
            if (id instanceof Number) {
                numericIds.add(((Number) id).longValue());
            } else if (id instanceof String) {
                try {
                    numericIds.add(Long.parseLong((String) id));
                } catch (NumberFormatException e) {
                    // 不是数字的字符串（如 "yuezi"），记录下来
                    stringIds.add((String) id);
                }
            }
        }

        // 查询数字ID对应的套餐（包含 id, packageCode, packageName）
        Map<Long, ParentPackage> packageMap = new HashMap<>();
        if (!numericIds.isEmpty()) {
            List<ParentPackage> packages = parentPackageMapper.selectBatchIds(numericIds);
            for (ParentPackage pkg : packages) {
                packageMap.put(pkg.getId(), pkg);
            }
        }

        // 回填套餐详情
        for (Dish dish : dishes) {
            if (dish.getMealPackages() != null) {
                List<Dish.PackageInfo> details = new ArrayList<>();
                for (Object id : dish.getMealPackages()) {
                    if (id instanceof Number) {
                        Long numericId = ((Number) id).longValue();
                        ParentPackage pkg = packageMap.get(numericId);
                        if (pkg != null) {
                            details.add(new Dish.PackageInfo(pkg.getId(), pkg.getPackageCode(), pkg.getPackageName()));
                        } else {
                            details.add(new Dish.PackageInfo(numericId, null, String.valueOf(id)));
                        }
                    } else if (id instanceof String) {
                        String strId = (String) id;
                        try {
                            Long numericId = Long.parseLong(strId);
                            ParentPackage pkg = packageMap.get(numericId);
                            if (pkg != null) {
                                details.add(new Dish.PackageInfo(pkg.getId(), pkg.getPackageCode(), pkg.getPackageName()));
                            } else {
                                details.add(new Dish.PackageInfo(numericId, null, strId));
                            }
                        } catch (NumberFormatException e) {
                            // 无法解析的字符串（如 "yuezi"），packageCode 和 packageName 都用字符串本身
                            details.add(new Dish.PackageInfo(null, strId, strId));
                        }
                    } else {
                        details.add(new Dish.PackageInfo(null, null, String.valueOf(id)));
                    }
                }
                dish.setMealPackageDetails(details);
            }
        }
    }

    @Override
    public PageResult<Dish> queryAll(DishQueryCriteria criteria, Page<Object> page){
        PageResult<Dish> result = PageUtil.toPage(dishMapper.findAll(criteria, page));
        fillIngredientsBatch(result.getContent());
        fillMealPackageDetailsBatch(result.getContent());
        return result;
    }

    @Override
    public List<Dish> queryAll(DishQueryCriteria criteria){
        List<Dish> list = dishMapper.findAll(criteria);
        fillIngredientsBatch(list);
        fillMealPackageDetailsBatch(list);
        return list;
    }

    /**
     * 将 DishIngredientRelation 转换为 DishIngredientDto
     */
    private List<DishIngredientDto> convertToDtoList(List<DishIngredientRelation> relations) {
        if (relations == null || relations.isEmpty()) {
            return null;
        }
        List<DishIngredientDto> dtoList = new ArrayList<>();
        for (DishIngredientRelation relation : relations) {
            DishIngredientDto dto = new DishIngredientDto();
            dto.setIngredientId(relation.getIngredientId());
            dto.setIngredientName(relation.getIngredientName());
            dto.setQuantity(relation.getQuantity());
            dto.setUnit(relation.getUnit());
            dto.setRemark(relation.getRemark());
            dtoList.add(dto);
        }
        return dtoList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(Dish resources) {
        resources.setCreateTime(new Timestamp(System.currentTimeMillis()));
        // 回填 ingredients 字符串字段（供兼容读取）
        resources.setIngredients(buildIngredientsStr(resources.getIngredientList()));
        dishMapper.insert(resources);

        // 保存配料关联
        if (resources.getIngredientList() != null && !resources.getIngredientList().isEmpty()) {
            for (DishIngredientDto dto : resources.getIngredientList()) {
                dishIngredientMapper.insertRelation(resources.getId(), dto);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Dish resources) {
        Dish dish = getById(resources.getId());
        dish.copy(resources);
        // 回填 ingredients 字符串字段（供兼容读取）
        // 若 ingredientList 为空则从关系表查询已有配料，避免误覆盖
        List<DishIngredientDto> dtoList = resources.getIngredientList();
        if (dtoList == null || dtoList.isEmpty()) {
            List<DishIngredientRelation> existing = dishIngredientMapper.findRelationsByDishId(resources.getId());
            dtoList = convertToDtoList(existing);
        }
        dish.setIngredients(buildIngredientsStr(dtoList));
        dish.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        dishMapper.updateById(dish);

        // 更新配料关联：先删除旧的，再插入新的
        if (resources.getIngredientList() != null) {
            dishIngredientMapper.deleteRelationsByDishId(resources.getId());
            for (DishIngredientDto dto : resources.getIngredientList()) {
                dishIngredientMapper.insertRelation(resources.getId(), dto);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAll(List<Integer> ids) {
        // 先删除配料关联
        for (Integer id : ids) {
            dishIngredientMapper.deleteRelationsByDishId(id);
        }
        dishMapper.deleteBatchIds(ids);
    }

    @Override
    public void download(List<Dish> all, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Dish dish : all) {
            Map<String,Object> map = new LinkedHashMap<>();
            map.put("菜品名称", dish.getName());
            map.put("做法/流程", dish.getCookingMethod());
            map.put("配料", dish.getIngredients());
            map.put("图片路径", dish.getImageUrl());
            map.put("菜品类型", dish.getDishType());
            map.put("餐次", dish.getMealTypes());
            map.put("所属套餐", dish.getMealPackageDetails() != null
                ? dish.getMealPackageDetails().stream()
                    .map(Dish.PackageInfo::getPackageName)
                    .collect(Collectors.joining(", "))
                : "");
            map.put("排期", dish.getSchedule());
            map.put("排序", dish.getSort());
            map.put("是否启用", dish.getEnabled());
            map.put("创建时间", dish.getCreateTime());
            map.put("更新时间", dish.getUpdateTime());
            list.add(map);
        }
        FileUtil.downloadExcel(list, response);
    }

    @Override
    public List<Dish> findBySchedule(Integer week, Integer day, String mealType) {
        return dishMapper.findBySchedule(week, day, mealType);
    }

    @Override
    public List<Dish> findAvailableByCustomerId(Integer customerId, String mealType, Integer week, Integer day) {
        // 先查询符合条件的菜品
        List<Dish> dishes = dishMapper.findAvailableByCustomerId(customerId, mealType, week, day);

        // 如果没有客户ID，直接返回
        if (customerId == null) {
            return dishes;
        }

        // 获取客户忌口
        CustomerDietaryRestrictions customer = customerDietaryRestrictionsMapper.selectById(customerId);
        if (customer == null || customer.getRestrictions() == null || customer.getRestrictions().isEmpty()) {
            return dishes;
        }

        // 根据配料过滤掉包含忌口的菜品
        List<String> restrictions = customer.getRestrictions();
        return dishes.stream()
            .filter(dish -> {
                String ingredients = dish.getIngredients();
                if (ingredients == null || ingredients.isEmpty()) {
                    return true;
                }
                // 检查配料中是否包含任一忌口词
                for (String restriction : restrictions) {
                    if (ingredients.contains(restriction)) {
                        return false;
                    }
                }
                return true;
            })
            .collect(Collectors.toList());
    }

    /**
     * 计算周数和星期（月中周：1-4周）
     */
    private int[] calculateWeekAndDay(String dateStr) {
        LocalDate targetDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        // 星期：1=周一，7=周日（从LocalDate直接获取）
        int dayOfWeek = targetDate.getDayOfWeek().getValue();
        // 月中周：1-7日=第1周，8-14日=第2周，15-21日=第3周，22-28日=第4周，29-31日=第5周
        int weekNum = (targetDate.getDayOfMonth() - 1) / 7 + 1;

        return new int[]{weekNum, dayOfWeek};
    }

    /**
     * 构建指定套餐、餐次的菜单Map
     */
    private Map<String, DishScheduleResult.DishVO> buildMenuMap(int week, int day, String mealType, String mealPackage) {
        Map<String, DishScheduleResult.DishVO> menuMap = new LinkedHashMap<>();

        DishQueryCriteria criteria = new DishQueryCriteria();
        criteria.setMealType(mealType);
        criteria.setMealPackage(mealPackage);

        List<Dish> dishes = dishMapper.findAll(criteria);
        // 批量填充配料列表
        fillIngredientsBatch(dishes);
        // 按排期过滤
        final int finalWeek = week;
        final int finalDay = day;
        final String scheduleKey = finalWeek + "-" + finalDay;

        for (Dish dish : dishes) {
            List<String> schedule = dish.getSchedule();
            if (schedule != null && schedule.contains(scheduleKey)) {
                String dishType = dish.getDishType();
                if (!menuMap.containsKey(dishType)) {
                    menuMap.put(dishType, DishScheduleResult.DishVO.fromDish(dish));
                }
            }
        }

        // 补齐缺失的菜品类型
        for (String dishType : DISH_TYPES) {
            if (!menuMap.containsKey(dishType)) {
                // 查找该套餐、餐次下的任意同类型菜品作为备选
                final String finalDishType = dishType;
                Optional<Dish> anyDish = dishes.stream()
                    .filter(d -> d.getDishType().equals(finalDishType))
                    .findAny();
                if (anyDish.isPresent()) {
                    menuMap.put(dishType, DishScheduleResult.DishVO.fromDish(anyDish.get()));
                }
            }
        }

        return menuMap;
    }

    /**
     * 根据参数返回需要处理的餐次列表
     */
    private List<String> collectMealTypes(String mealType) {
        if (mealType == null || MEAL_TYPE_ALL.equals(mealType)) {
            return Arrays.asList(MEAL_TYPES);
        }
        return Collections.singletonList(mealType);
    }

    /**
     * 构建菜品查询条件
     */
    private DishQueryCriteria buildCriteria(String mealType, String mealPackage) {
        DishQueryCriteria criteria = new DishQueryCriteria();
        criteria.setMealType(mealType);
        criteria.setMealPackage(mealPackage);
        return criteria;
    }

    /**
     * 构建客户菜单列表
     */
    private List<DishScheduleResult.CustomerMenu> buildCustomerMenus(String dateStr, int week, int day, String mealType, Integer customerId, String scheduleMode) {
        List<DishScheduleResult.CustomerMenu> customerMenus = new ArrayList<>();

        // 查找所有客户
        QueryWrapper<CustomerDietaryRestrictions> wrapper = new QueryWrapper<>();
        if (customerId != null) {
            wrapper.eq("id", customerId);
        }
        List<CustomerDietaryRestrictions> allCustomers = customerDietaryRestrictionsMapper.selectList(wrapper);

        LocalDate targetDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 替换菜品候选缓存（方法内新建，每次请求独立；按 dishType+restrictions 缓存已排序的候选菜）
        Map<String, ReplacementCacheEntry> cache = new LinkedHashMap<>();

        // 预查询并填充所有套餐+餐次的菜品（按 (mealType, mealPackage) 分组，避免重复查询数据库）
        Map<String, List<Dish>> dishesByPackageAndMeal = new LinkedHashMap<>();
        Set<String> packageAndMealKeys = new HashSet<>();
        for (CustomerDietaryRestrictions customer : allCustomers) {
            String customerScheduleMode = getCustomerScheduleMode(customer.getId());

            if (!isCustomerActive(customer, targetDate, customerScheduleMode)) {
                continue;
            }
            String pkg = customer.getMealPackage();
            for (String mt : collectMealTypes(mealType)) {
                String key = mt + "|" + pkg;
                if (!packageAndMealKeys.contains(key)) {
                    packageAndMealKeys.add(key);
                    List<Dish> dishes = dishMapper.findAll(buildCriteria(mt, pkg));
                    dishesByPackageAndMeal.put(key, dishes);
                    log.info("[buildCustomerMenus] 预查询菜品: mealType={}, mealPackage={}, count={}", mt, pkg, dishes.size());
                }
            }
        }
        // 一次性批量加载所有菜品的配料
        List<Dish> allDishesForIngredients = new ArrayList<>(dishesByPackageAndMeal.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));
        Map<Integer, List<DishIngredientDto>> allDishIngredients = precomputeDishIngredients(allDishesForIngredients);
        log.info("[buildCustomerMenus] 配料批量加载完成，总菜品={}", allDishesForIngredients.size());

        for (CustomerDietaryRestrictions customer : allCustomers) {
            // 检查客户是否生效
            String custScheduleMode = getCustomerScheduleMode(customer.getId());
            if (!isCustomerActive(customer, targetDate, custScheduleMode)) {
                continue;
            }

            DishScheduleResult.CustomerMenu customerMenu = new DishScheduleResult.CustomerMenu();
            customerMenu.setCustomerId(customer.getId());
            customerMenu.setCustomerName(customer.getCustomerName());
            customerMenu.setMealPackage(customer.getMealPackage());
            customerMenu.setRestrictions(customer.getRestrictions());

            // 获取该客户的套餐菜单并处理忌口替换
            String mealPackage = customer.getMealPackage();
            DishScheduleResult.MenuByPackage menuByMealType = new DishScheduleResult.MenuByPackage();

            List<DishScheduleResult.DishVO> allUnableToReplace = new ArrayList<>();

            // 根据 mealType 参数决定处理哪些餐次
            Set<String> restrictionsSet = customer.getRestrictions() != null ? new HashSet<>(customer.getRestrictions()) : Collections.emptySet();
            if (mealType == null || MEAL_TYPE_ALL.equals(mealType) || "LUNCH".equals(mealType)) {
                List<Dish> lunchDishes = dishesByPackageAndMeal.get("LUNCH|" + mealPackage);
                log.info("[客户:{}, 套餐:{}] 构建午餐菜单, 周:{}-{}", customer.getCustomerName(), mealPackage, week, day);
                DishScheduleResult.CustomerMenuMapResult lunchResult = buildCustomerMenuMap(week, day, "LUNCH", mealPackage, restrictionsSet, lunchDishes != null ? lunchDishes : new ArrayList<>(), allDishIngredients, cache);
                menuByMealType.setLunch(lunchResult.getMenuMap());
                allUnableToReplace.addAll(lunchResult.getUnableToReplaceDishes());
                log.info("[客户:{}] 午餐菜单菜品数: {}", customer.getCustomerName(), lunchResult.getMenuMap().size());
            }
            if (mealType == null || MEAL_TYPE_ALL.equals(mealType) || "DINNER".equals(mealType)) {
                List<Dish> dinnerDishes = dishesByPackageAndMeal.get("DINNER|" + mealPackage);
                log.info("[客户:{}, 套餐:{}] 构建晚餐菜单, 周:{}-{}", customer.getCustomerName(), mealPackage, week, day);
                DishScheduleResult.CustomerMenuMapResult dinnerResult = buildCustomerMenuMap(week, day, "DINNER", mealPackage, restrictionsSet, dinnerDishes != null ? dinnerDishes : new ArrayList<>(), allDishIngredients, cache);
                menuByMealType.setDinner(dinnerResult.getMenuMap());
                allUnableToReplace.addAll(dinnerResult.getUnableToReplaceDishes());
                log.info("[客户:{}] 晚餐菜单菜品数: {}", customer.getCustomerName(), dinnerResult.getMenuMap().size());
            }
            customerMenu.setMenu(menuByMealType);
            customerMenu.setUnableToReplaceDishes(allUnableToReplace);

            customerMenus.add(customerMenu);
        }

        return customerMenus;
    }

    /**
     * 获取客户的最新订单排餐模式
     * @param customerId 客户ID
     * @return 排餐模式（SCHEDULE=指定日期送,DAILY=每天送,WEEKEND=周末送,WEEKDAY=工作日送）
     */
    private String getCustomerScheduleMode(Integer customerId) {
        me.zhengjie.modules.customer.order.domain.CustomerOrder order = customerOrderMapper.findLatestByCustomerId(customerId.longValue());
        return order != null ? order.getScheduleMode() : "SCHEDULE";
    }

    /**
     * 根据订单排餐模式判断是否应该排餐
     * @param scheduleMode 排餐模式
     * @param targetDate 目标日期
     * @return true表示应该排餐
     */
    private boolean shouldScheduleForDate(String scheduleMode, LocalDate targetDate) {
        if ("SCHEDULE".equals(scheduleMode) || "DAILY".equals(scheduleMode)) {
            // 指定日期送和每天送：正常排餐
            return true;
        } else if ("WEEKEND".equals(scheduleMode)) {
            // 周末送：只排周末
            int dayOfWeek = targetDate.getDayOfWeek().getValue();
            return dayOfWeek == 6 || dayOfWeek == 7;
        } else if ("WEEKDAY".equals(scheduleMode)) {
            // 工作日送：只排工作日
            int dayOfWeek = targetDate.getDayOfWeek().getValue();
            return dayOfWeek >= 1 && dayOfWeek <= 5;
        }
        return false;
    }

    /**
     * 检查客户是否在生效期间且有剩余餐数
     */
    private boolean isCustomerActive(CustomerDietaryRestrictions customer, LocalDate targetDate, String scheduleMode) {
        // 检查剩余餐数
        if (customer.getRemainingMeals() == null || customer.getRemainingMeals() <= 0) {
            return false;
        }

        // 检查日期范围
        try {
            if (customer.getStartDate() != null && !customer.getStartDate().isEmpty()) {
                LocalDate startDate = LocalDate.parse(customer.getStartDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                if (targetDate.isBefore(startDate)) {
                    return false;
                }
            }
            if (customer.getEndDate() != null && !customer.getEndDate().isEmpty()) {
                LocalDate endDate = LocalDate.parse(customer.getEndDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                if (targetDate.isAfter(endDate)) {
                    return false;
                }
            }
        } catch (Exception e) {
            // 日期解析失败，跳过该客户
            return false;
        }

        return true;
    }

    /**
     * 预计算给定菜品的配料集合（用于批量判断是否含忌口）
     * @return Map: dishId -> DishIngredientDto 列表
     */
    private Map<Integer, List<DishIngredientDto>> precomputeDishIngredients(List<Dish> dishes) {
        if (dishes == null || dishes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Integer> dishIds = dishes.stream().map(Dish::getId).collect(Collectors.toList());
        List<DishIngredientRelation> relations = dishIngredientMapper.findRelationsByDishIds(dishIds);
        Map<Integer, List<DishIngredientDto>> result = new HashMap<>();
        for (DishIngredientRelation r : relations) {
            DishIngredientDto dto = new DishIngredientDto();
            dto.setIngredientId(r.getIngredientId());
            dto.setIngredientName(r.getIngredientName());
            dto.setQuantity(r.getQuantity());
            dto.setUnit(r.getUnit());
            dto.setRemark(r.getRemark());
            result.computeIfAbsent(r.getDishId(), k -> new ArrayList<>()).add(dto);
        }
        return result;
    }

    /**
     * 为客户构建菜单，处理忌口替换
     * @param allDishes           该套餐+餐次的所有菜品
     * @param restrictions        客户忌口词集合（用于分组 + 判断是否含忌口）
     * @param cache               替换菜品候选缓存
     * @return CustomerMenuMapResult 包含最终菜单和无法替换的菜品列表
     */
    private DishScheduleResult.CustomerMenuMapResult buildCustomerMenuMap(int week, int day, String mealType, String mealPackage, Set<String> restrictions, List<Dish> allDishes, Map<Integer, List<DishIngredientDto>> allDishIngredients, Map<String, ReplacementCacheEntry> cache) {
        Map<String, DishScheduleResult.DishVO> menuMap = new LinkedHashMap<>();
        List<DishScheduleResult.DishVO> unableToReplace = new ArrayList<>();

        log.info("[buildCustomerMenuMap] 菜品数量: {}, 套餐:{}, 餐次:{}", allDishes.size(), mealPackage, mealTypeCn(mealType));

        final String scheduleKey = week + "-" + day;
        log.info("[buildCustomerMenuMap] 排期key: {}, 菜品排期列表: {}", scheduleKey, allDishes.stream().map(Dish::getSchedule).collect(Collectors.toList()));

        // 先获取默认菜单
        for (Dish dish : allDishes) {
            List<String> schedule = dish.getSchedule();
            if (schedule != null && schedule.contains(scheduleKey)) {
                String dishType = dish.getDishType();
                if (!menuMap.containsKey(dishType)) {
                    // 检查是否需要替换
                    DishScheduleResult.DishVO dishVO = checkAndReplaceDish(dish, allDishes, dishType, week, day, restrictions, allDishIngredients, cache);
                    if (dishVO != null) {
                        if ("忌口：无替换菜品".equals(dishVO.getReason())) {
                            unableToReplace.add(dishVO);
                            log.warn("[buildCustomerMenuMap] 菜品含忌口且无替换菜品，已跳过: {} - {}", dishTypeCn(dishType), dishVO.getName());
                        } else {
                            menuMap.put(dishType, dishVO);
                            log.info("[buildCustomerMenuMap] 匹配到菜品: {} - {}", dishTypeCn(dishType), dishVO.getName());
                        }
                    }
                }
            }
        }

        // 补齐缺失的菜品类型
        for (String dishType : DISH_TYPES) {
            if (!menuMap.containsKey(dishType)) {
                DishScheduleResult.DishVO dishVO = findReplacementDish(null, allDishes, dishType, week, day, restrictions, allDishIngredients, cache);
                if (dishVO != null) {
                    menuMap.put(dishType, dishVO);
                    log.info("[buildCustomerMenuMap] 补齐菜品: {} - {}", dishTypeCn(dishType), dishVO.getName());
                } else {
                    log.warn("[buildCustomerMenuMap] 无法找到菜品类型: {} 的可用菜品，请检查菜品排期配置!", dishTypeCn(dishType));
                }
            }
        }

        DishScheduleResult.CustomerMenuMapResult result = new DishScheduleResult.CustomerMenuMapResult();
        result.setMenuMap(menuMap);
        result.setUnableToReplaceDishes(unableToReplace);
        return result;
    }

    /**
     * 检查菜品是否需要替换（使用客户自己的忌口）
     */
    private DishScheduleResult.DishVO checkAndReplaceDish(Dish dish, List<Dish> allDishes, String dishType, int week, int day, Set<String> restrictions, Map<Integer, List<DishIngredientDto>> allDishIngredients, Map<String, ReplacementCacheEntry> cache) {
        // 填充配料到 Dish 对象，确保 DishVO.fromDish 能拿到
        dish.setIngredientList(allDishIngredients.get(dish.getId()));
        if (dish == null || !containsRestriction(dish, restrictions, allDishIngredients)) {
            return dish != null ? DishScheduleResult.DishVO.fromDish(dish) : null;
        }
        DishScheduleResult.DishVO replacement = findReplacementDish(dish, allDishes, dishType, week, day, restrictions, allDishIngredients, cache);
        if (replacement != null) {
            replacement.setReplaced(true);
            replacement.setOriginalId(dish.getId());
            replacement.setReason("忌口");
        } else {
            DishScheduleResult.DishVO dishVO = DishScheduleResult.DishVO.fromDish(dish);
            dishVO.setReplaced(true);
            dishVO.setOriginalId(dish.getId());
            dishVO.setReason("忌口：无替换菜品");
            return dishVO;
        }
        return replacement;
    }

    /**
     * 检查菜品配料是否包含忌口（使用客户自己的忌口列表，O(n) 遍历）
     */
    /**
     * 检查菜品配料是否包含忌口（直接查 Map，不依赖 Dish.ingredientList，避免二次查询覆盖）
     */
    private boolean containsRestriction(Dish dish, Set<String> restrictions, Map<Integer, List<DishIngredientDto>> allDishIngredients) {
        if (restrictions == null || restrictions.isEmpty()) {
            return false;
        }
        List<DishIngredientDto> ingredientList = allDishIngredients.get(dish.getId());
        if (ingredientList == null || ingredientList.isEmpty()) {
            log.warn("[containsRestriction] 菜品配料关系为空: dishId={}, dishName={}, restrictions={}", dish.getId(), dish.getName(), restrictions);
            return false;
        }
        for (DishIngredientDto dto : ingredientList) {
            String name = dto.getIngredientName();
            if (name == null || name.isEmpty()) continue;
            for (String restriction : restrictions) {
                if (name.contains(restriction)) {
                    log.info("[containsRestriction] 菜品含忌口: dishId={}, dishName={}, 配料={}, 忌口={}", dish.getId(), dish.getName(), name, restriction);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 替换菜品候选缓存结构
     * - candidates: 按排期距离排序好的候选菜列表（同一 cacheKey 只计算一次）
     * - nextIndex: 下一个可分配候选在列表中的索引（递增式分配，保证不重复）
     */
    private static class ReplacementCacheEntry {
        // 按排期距离升序排序的完整候选菜列表（不过滤忌口，供所有用户共享）
        final List<Dish> sortedAll;
        // 已分配给各忌口组的菜品 ID 集合（key = 排序后的 restrictionsKey，追踪该组已分配的菜品，避免重复）
        final Map<String, Set<Integer>> takenDishIds = new LinkedHashMap<>();
        ReplacementCacheEntry(List<Dish> sortedAll) { this.sortedAll = sortedAll; }
    }

    /**
     * 生成替换菜品缓存的 Key（不含忌口，所有用户共享同一份候选菜列表）
     */
    private String makeReplacementCacheKey(int week, int day, String dishType) {
        return week + "|" + day + "|" + dishType;
    }

    /**
     * 为当前用户从缓存中分配一个可用的候选菜（使用客户自己的忌口）
     */
    private Dish pickFromCacheAndAssign(Map<String, ReplacementCacheEntry> cache, int week, int day, String dishType, Set<String> restrictions, Map<Integer, List<DishIngredientDto>> allDishIngredients) {
        String cacheKey = makeReplacementCacheKey(week, day, dishType);
        ReplacementCacheEntry entry = cache.get(cacheKey);
        if (entry == null || entry.sortedAll.isEmpty()) {
            return null;
        }
        // 按忌口词组合键分组，同组客户共享已分配记录
        String takenKey = (restrictions == null || restrictions.isEmpty())
            ? "" : restrictions.stream().sorted().collect(Collectors.joining(","));
        Set<Integer> taken = entry.takenDishIds.computeIfAbsent(takenKey, k -> new HashSet<>());
        for (Dish dish : entry.sortedAll) {
            if (taken.contains(dish.getId())) continue;
            if (containsRestriction(dish, restrictions, allDishIngredients)) continue;
            taken.add(dish.getId());
            return dish;
        }
        return null;
    }

    /**
     * 查找替换菜品（当前周所有天中找同类不含忌口的菜品）
     */
    private DishScheduleResult.DishVO findReplacementDish(Dish replacedDish, List<Dish> allDishes, String dishType, int week, int day, Set<String> restrictions, Map<Integer, List<DishIngredientDto>> allDishIngredients, Map<String, ReplacementCacheEntry> cache) {
        String cacheKey = makeReplacementCacheKey(week, day, dishType);

        // ① 尝试从缓存分配
        Dish cachedDish = pickFromCacheAndAssign(cache, week, day, dishType, restrictions, allDishIngredients);
        if (cachedDish != null) {
            log.info("[findReplacementDish] 缓存命中: dishType={}, replacedDish={}", dishType, replacedDish != null ? replacedDish.getName() : "null");
            cachedDish.setIngredientList(allDishIngredients.get(cachedDish.getId()));
            DishScheduleResult.DishVO vo = DishScheduleResult.DishVO.fromDish(cachedDish);
            if (replacedDish != null) {
                vo.setReplaced(true);
                vo.setOriginalId(replacedDish.getId());
                vo.setReason("忌口");
            }
            return vo;
        }

        // ② 缓存未命中：从 allDishes 过滤并排序，再写入缓存
        List<String> weekScheduleKeys = IntStream.rangeClosed(1, 7)
            .mapToObj(d -> week + "-" + d)
            .collect(Collectors.toList());

        List<Dish> candidates = allDishes.stream()
            .filter(d -> d.getDishType().equals(dishType))
            .filter(d -> {
                List<String> schedule = d.getSchedule();
                if (schedule == null) return false;
                return schedule.stream().anyMatch(weekScheduleKeys::contains);
            })
            .filter(d -> replacedDish == null || !d.getId().equals(replacedDish.getId()))
            .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return null;
        }

        // 按排期距离升序，相同则按菜品ID排
        final int targetDay = day;
        Map<Dish, Integer> scheduleDayMap = candidates.stream()
            .collect(Collectors.toMap(d -> d, d -> firstScheduleDayOfWeek(d.getSchedule(), week)));

        List<Dish> sorted = candidates.stream()
            .sorted(Comparator
                .comparingInt((Dish d) -> Math.abs(scheduleDayMap.get(d) - targetDay))
                .thenComparingInt(Dish::getId))
            .collect(Collectors.toList());

        // 写入缓存（供后续用户复用）
        cache.put(cacheKey, new ReplacementCacheEntry(sorted));
        log.info("[findReplacementDish] 缓存写入: cacheKey={}, cachedCount={}", cacheKey, sorted.size());

        // 分配第一个
        Dish assigned = pickFromCacheAndAssign(cache, week, day, dishType, restrictions, allDishIngredients);
        if (assigned == null) {
            return null;
        }
        assigned.setIngredientList(allDishIngredients.get(assigned.getId()));
        DishScheduleResult.DishVO vo = DishScheduleResult.DishVO.fromDish(assigned);
        if (replacedDish != null) {
            vo.setReplaced(true);
            vo.setOriginalId(replacedDish.getId());
            vo.setReason("忌口");
        }
        return vo;
    }

    /**
     * 从 schedule 列表中提取该菜在指定周第一次排期所对应的天（1-7）。
     * 如果该周无排期则返回 NO_SCHEDULE_DAY（8），使其与任意目标日的距离差最大，排序时排在末尾。
     * 同一个 dish 同一周通常只有一条排期记录（如 4-3），所以"第一次"即为该周的排期天。
     */
    private int firstScheduleDayOfWeek(List<String> schedule, int week) {
        if (schedule == null || schedule.isEmpty()) {
            return NO_SCHEDULE_DAY;
        }
        for (String s : schedule) {
            if (s == null || !s.startsWith(week + "-")) {
                continue;
            }
            int idx = s.indexOf('-');
            if (idx < 0) continue;
            try {
                int d = Integer.parseInt(s.substring(idx + 1));
                if (d >= 1 && d <= 7) {
                    return d;
                }
            } catch (NumberFormatException ignored) {}
        }
        return NO_SCHEDULE_DAY;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishScheduleResult getScheduleAndSave(String date, String mealType, String scheduleMode, Integer customerId) {
        log.info("========== 开始生成排餐计划 ==========");
        log.info("请求参数: date={}, mealType={}, scheduleMode={}, customerId={}", date, mealTypeCn(mealType), scheduleMode, customerId);

        // 步骤1: 构建排餐结果

        DishScheduleResult result = buildScheduleResult(date, mealType, customerId, scheduleMode);
        log.info("[步骤1] 排餐结果构建完成，有效客户数: {}", result.getCustomers() != null ? result.getCustomers().size() : 0);

        // 检查是否有生效的客户
        if (result.getCustomers() == null || result.getCustomers().isEmpty()) {
            log.warn("[步骤1] 排餐结果为空，无生效客户");
            if (customerId != null) {
                throw new BadRequestException("客户ID " + customerId + " 在该日期没有生效的餐食计划");
            } else {
                throw new BadRequestException("该日期没有生效的客户，无法生成排餐计划");
            }
        }

        // 步骤2: 计算日期信息
        int[] weekAndDay = calculateWeekAndDay(date);
        int weekNum = weekAndDay[0];
        int dayOfWeek = weekAndDay[1];
        log.info("[步骤2] 日期计算完成: 第{}周, 周{}", weekNum, dayOfWeek);

        // 步骤3: 确定需要处理的餐次
        log.info("[步骤3] 确定处理的餐次...");
        String[] mealTypesToProcess;
        if (mealType == null || MEAL_TYPE_ALL.equals(mealType)) {
            mealTypesToProcess = MEAL_TYPES;
        } else {
            mealTypesToProcess = new String[]{mealType};
        }
        log.info("[步骤3] 待处理餐次: {}", Arrays.stream(mealTypesToProcess).map(this::mealTypeCn).collect(Collectors.toList()));

        for (String mt : mealTypesToProcess) {
            log.info("---------- 开始处理餐次: {} ----------", mealTypeCn(mt));

            // 步骤4: 软删除已存在的排餐记录
            List<DishScheduleRecord> existingRecords = findActiveScheduleRecords(date, mt);
            DishScheduleRecord record = null;
            boolean reuseExistingRecord = customerId != null && !existingRecords.isEmpty();
            if (reuseExistingRecord) {
                record = existingRecords.get(0);
                softDeleteCustomerMenus(existingRecords, customerId);
                log.info("[步骤4] 单客户重排，复用排餐记录 ID: {}，仅清理客户 {} 的历史菜单", record.getId(), customerId);
            } else {
                for (DishScheduleRecord existingRecord : existingRecords) {
                    // 软删除关联的客户菜单记录（批量更新，避免 N+1）
                    QueryWrapper<CustomerMenuRecord> menuDeleteWrapper = new QueryWrapper<>();
                    menuDeleteWrapper.eq("record_id", existingRecord.getId()).eq("deleted", false);
                    List<CustomerMenuRecord> existingMenus = customerMenuRecordMapper.selectList(menuDeleteWrapper);
                    if (!existingMenus.isEmpty()) {
                        existingMenus.forEach(menu -> menu.setDeleted(true));
                        customerMenuRecordService.updateBatchById(existingMenus);
                    }
                    // 软删除排餐记录
                    existingRecord.setDeleted(true);
                    dishScheduleRecordMapper.updateById(existingRecord);
                }
            }

            // 步骤5: 统计该餐次的客户数量（从本次生成结果中统计，有当前餐次菜单的客户才计数）
            log.info("[步骤5] 统计餐次客户数量...");
            int customerCount = 0;
            if (result.getCustomers() != null) {
                for (DishScheduleResult.CustomerMenu cm : result.getCustomers()) {
                    if (cm.getMenu() == null) {
                        continue;
                    }
                    DishScheduleResult.MenuByPackage menuByPackage = cm.getMenu();
                    if ("LUNCH".equals(mt) && menuByPackage.getLunch() != null && !menuByPackage.getLunch().isEmpty()) {
                        customerCount++;
                    } else if ("DINNER".equals(mt) && menuByPackage.getDinner() != null && !menuByPackage.getDinner().isEmpty()) {
                        customerCount++;
                    }
                }
            }
            log.info("[步骤5] {}餐次客户数: {}", mealTypeCn(mt), customerCount);

            // 步骤6: 保存排餐记录
            log.info("[步骤6] 保存排餐记录...");
            if (reuseExistingRecord) {
                record.setWeekNum(weekNum);
                record.setDayOfWeek(dayOfWeek);
                record.setCustomerCount(customerCount);
                dishScheduleRecordMapper.updateById(record);
                log.info("[步骤6] 复用排餐记录成功, ID: {}", record.getId());
            } else {
                record = new DishScheduleRecord();
                record.setRecordDate(date);
                record.setMealType(mt);
                record.setWeekNum(weekNum);
                record.setDayOfWeek(dayOfWeek);
                record.setCustomerCount(customerCount);
                record.setCreateTime(new Timestamp(System.currentTimeMillis()));
                dishScheduleRecordMapper.insert(record);
                log.info("[步骤6] 排餐记录保存成功, ID: {}", record.getId());
            }

            // 步骤7: 批量保存客户菜单记录
            log.info("[步骤7] 保存客户菜单记录...");
            List<CustomerMenuRecord> menuRecordsToSave = new ArrayList<>();
            if (result.getCustomers() != null) {
                Timestamp now = new Timestamp(System.currentTimeMillis());
                for (DishScheduleResult.CustomerMenu cm : result.getCustomers()) {
                    Map<String, DishScheduleResult.DishVO> menuMap = null;
                    if ("LUNCH".equals(mt) && cm.getMenu() != null && cm.getMenu().getLunch() != null) {
                        menuMap = cm.getMenu().getLunch();
                    } else if ("DINNER".equals(mt) && cm.getMenu() != null && cm.getMenu().getDinner() != null) {
                        menuMap = cm.getMenu().getDinner();
                    }

                    if (menuMap != null) {
                        for (Map.Entry<String, DishScheduleResult.DishVO> entry : menuMap.entrySet()) {
                            DishScheduleResult.DishVO dishVO = entry.getValue();
                            CustomerMenuRecord menuRecord = new CustomerMenuRecord();
                            menuRecord.setRecordId(record.getId());
                            menuRecord.setCustomerId(cm.getCustomerId());
                            menuRecord.setCustomerName(cm.getCustomerName());
                            menuRecord.setDishType(entry.getKey());
                            menuRecord.setDishId(dishVO.getId());
                            menuRecord.setDishName(dishVO.getName());
                            menuRecord.setDishIngredients(buildIngredientsStr(dishVO.getIngredientList()));
                            menuRecord.setIsReplaced(dishVO.getReplaced());
                            menuRecord.setOriginalDishId(dishVO.getOriginalId());
                            menuRecord.setReplacementReason(dishVO.getReason());
                            menuRecord.setCreateTime(now);
                            menuRecordsToSave.add(menuRecord);
                        }
                    }

                    // 保存无法替换的菜品（已记录到表，reason 为"忌口：无替换菜品"）
                    List<DishScheduleResult.DishVO> unableToReplace = cm.getUnableToReplaceDishes();
                    if (unableToReplace != null && !unableToReplace.isEmpty()) {
                        for (DishScheduleResult.DishVO dishVO : unableToReplace) {
                            CustomerMenuRecord menuRecord = new CustomerMenuRecord();
                            menuRecord.setRecordId(record.getId());
                            menuRecord.setCustomerId(cm.getCustomerId());
                            menuRecord.setCustomerName(cm.getCustomerName());
                            menuRecord.setDishType(dishVO.getDishType());
                            menuRecord.setDishId(dishVO.getId());
                            menuRecord.setDishName(dishVO.getName());
                            menuRecord.setDishIngredients(buildIngredientsStr(dishVO.getIngredientList()));
                            menuRecord.setIsReplaced(dishVO.getReplaced());
                            menuRecord.setOriginalDishId(dishVO.getOriginalId());
                            menuRecord.setReplacementReason(dishVO.getReason());
                            menuRecord.setCreateTime(now);
                            menuRecordsToSave.add(menuRecord);
                        }
                    }
                }
            }
            if (!menuRecordsToSave.isEmpty()) {
                customerMenuRecordService.saveBatch(menuRecordsToSave);
                log.info("[步骤7] 客户菜单记录批量保存完成, 共{}条", menuRecordsToSave.size());
            }
            if (reuseExistingRecord) {
                int updatedCustomerCount = countActiveCustomersForRecord(record.getId());
                record.setCustomerCount(updatedCustomerCount);
                dishScheduleRecordMapper.updateById(record);
                log.info("[步骤7] 复用排餐记录客户数更新完成: {}", updatedCustomerCount);
            }
            log.info("[步骤7] 客户菜单记录保存完成, 共{}条", menuRecordsToSave.size());
            log.info("---------- 餐次 {} 处理完成 ----------", mealTypeCn(mt));
        }

        log.info("========== 排餐计划生成完成 ==========");
        log.info("排餐日期: {}, 处理餐次: {}, 总客户数: {}", date,
                Arrays.stream(mealTypesToProcess).map(this::mealTypeCn).collect(Collectors.toList()),
                result.getCustomers() != null ? result.getCustomers().size() : 0);

        return result;
    }


    /**
     * 排餐计划生成
     * 查询指定日期 指定餐次 的菜单
     * @param date
     * @param mealType
     * @param customerId
     * @return
     */
    @Override
    public DishScheduleResult getScheduleAndSaveNew(String date, String mealType, Integer customerId) {
        return null;
    }

    @Override
    public DishScheduleStats getScheduleStats(String date) {
        DishScheduleStats stats = new DishScheduleStats();
        stats.setDate(date);

        // 查询排餐记录
        QueryWrapper<DishScheduleRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("record_date", date).eq("deleted", false);
        List<DishScheduleRecord> records = dishScheduleRecordMapper.selectList(recordWrapper);

        if (records.isEmpty()) {
            return stats;
        }

        // 一次性查询所有关联的客户菜单记录（消除 N+1）
        List<Integer> recordIds = records.stream()
                .map(DishScheduleRecord::getId)
                .collect(Collectors.toList());
        QueryWrapper<CustomerMenuRecord> menuQueryWrapper = new QueryWrapper<>();
        menuQueryWrapper.in("record_id", recordIds).eq("deleted", false);
        List<CustomerMenuRecord> allMenuRecords = customerMenuRecordMapper.selectList(menuQueryWrapper);

        // 按 recordId 分组，避免循环内查询
        Map<Integer, List<CustomerMenuRecord>> menusByRecordId = allMenuRecords.stream()
                .collect(Collectors.groupingBy(CustomerMenuRecord::getRecordId));

        Map<String, DishScheduleStats.MealTypeStats> statsMap = new HashMap<>();

        for (DishScheduleRecord record : records) {
            DishScheduleStats.MealTypeStats mealTypeStats = new DishScheduleStats.MealTypeStats();
            mealTypeStats.setCustomerCount(record.getCustomerCount());

            List<CustomerMenuRecord> menuRecords = menusByRecordId.getOrDefault(record.getId(), Collections.emptyList());

            int replacedCount = 0;
            Map<String, DishScheduleStats.DishTypeMenu> menuMap = new LinkedHashMap<>();

            for (CustomerMenuRecord menuRecord : menuRecords) {
                if (Boolean.TRUE.equals(menuRecord.getIsReplaced())) {
                    replacedCount++;
                }

                String dishType = menuRecord.getDishType();
                DishScheduleStats.DishTypeMenu dtm = menuMap.computeIfAbsent(dishType, k -> {
                    DishScheduleStats.DishTypeMenu d = new DishScheduleStats.DishTypeMenu();
                    d.setDishId(menuRecord.getDishId());
                    d.setDishName(menuRecord.getDishName());
                    d.setReplacedCount(0);
                    return d;
                });

                if (Boolean.TRUE.equals(menuRecord.getIsReplaced())) {
                    dtm.setReplacedCount(dtm.getReplacedCount() + 1);
                }
            }

            mealTypeStats.setReplacedCount(replacedCount);
            mealTypeStats.setMenu(menuMap);

            statsMap.put(record.getMealType(), mealTypeStats);
        }

        stats.setLunch(statsMap.get("LUNCH"));
        stats.setDinner(statsMap.get("DINNER"));

        return stats;
    }

    @Override
    public PageResult<DishScheduleRecordVO> queryScheduleRecord(DishScheduleRecordQueryCriteria criteria, Page<Object> page) {
        // 1. 查询排餐记录（主表）
        QueryWrapper<DishScheduleRecord> recordWrapper = new QueryWrapper<>();

        // 只查询未删除的记录
        recordWrapper.eq("deleted", false);

        // 日期范围查询
        if (criteria.getStartDate() != null && !criteria.getStartDate().isEmpty()) {
            recordWrapper.ge("record_date", criteria.getStartDate());
        }
        if (criteria.getEndDate() != null && !criteria.getEndDate().isEmpty()) {
            recordWrapper.le("record_date", criteria.getEndDate());
        }

        // 餐次过滤
        if (criteria.getMealTypes() != null && !criteria.getMealTypes().isEmpty()) {
            recordWrapper.in("meal_type", criteria.getMealTypes());
        }

        recordWrapper.orderByDesc("record_date", "meal_type");

        // 分页查询排餐记录
        Page<DishScheduleRecord> recordPage = new Page<>(page.getCurrent(), page.getSize());
        Page<DishScheduleRecord> recordResult = dishScheduleRecordMapper.selectPage(recordPage, recordWrapper);

        if (recordResult.getRecords().isEmpty()) {
            return new PageResult<>(new ArrayList<>(), recordResult.getTotal());
        }

        // 2. 一次性查询所有关联的客户菜单记录（消除 N+1）
        List<Integer> recordIds = recordResult.getRecords().stream()
                .map(DishScheduleRecord::getId)
                .collect(Collectors.toList());

        QueryWrapper<CustomerMenuRecord> menuQueryWrapper = new QueryWrapper<>();
        menuQueryWrapper.in("record_id", recordIds).eq("deleted", false);

        // 客户ID过滤
        if (criteria.getCustomerId() != null) {
            menuQueryWrapper.eq("customer_id", criteria.getCustomerId());
        }

        // 客户名称模糊查询
        if (criteria.getCustomerName() != null && !criteria.getCustomerName().isEmpty()) {
            menuQueryWrapper.like("customer_name", criteria.getCustomerName());
        }

        // 套餐类型/菜品类型过滤
        if (criteria.getDishType() != null && !criteria.getDishType().isEmpty()) {
            menuQueryWrapper.eq("dish_type", criteria.getDishType());
        }

        List<CustomerMenuRecord> allMenuRecords = customerMenuRecordMapper.selectList(menuQueryWrapper);

        // 按 recordId 分组（内存中操作，避免循环查询）
        Map<Integer, List<CustomerMenuRecord>> menusByRecordId = allMenuRecords.stream()
                .collect(Collectors.groupingBy(CustomerMenuRecord::getRecordId));

        // 3. 组装 VO
        List<DishScheduleRecordVO> voList = new ArrayList<>(recordResult.getRecords().size());

        for (DishScheduleRecord record : recordResult.getRecords()) {
            DishScheduleRecordVO vo = new DishScheduleRecordVO();
            vo.setRecordId(record.getId());
            vo.setRecordDate(record.getRecordDate());
            vo.setMealType(record.getMealType());
            vo.setWeekNum(record.getWeekNum());
            vo.setDayOfWeek(record.getDayOfWeek());
            vo.setCustomerCount(record.getCustomerCount());
            vo.setCreateTime(record.getCreateTime());

            List<DishScheduleRecordVO.CustomerMenuVO> customerMenus = new ArrayList<>();
            List<CustomerMenuRecord> menuRecords = menusByRecordId.getOrDefault(record.getId(), Collections.emptyList());
            for (CustomerMenuRecord menuRecord : menuRecords) {
                DishScheduleRecordVO.CustomerMenuVO customerMenuVO = new DishScheduleRecordVO.CustomerMenuVO();
                customerMenuVO.setId(menuRecord.getId());
                customerMenuVO.setCustomerId(menuRecord.getCustomerId());
                customerMenuVO.setCustomerName(menuRecord.getCustomerName());
                customerMenuVO.setDishType(menuRecord.getDishType());
                customerMenuVO.setDishId(menuRecord.getDishId());
                customerMenuVO.setDishName(menuRecord.getDishName());
                customerMenuVO.setDishIngredients(menuRecord.getDishIngredients());
                customerMenuVO.setIsReplaced(menuRecord.getIsReplaced());
                customerMenuVO.setOriginalDishId(menuRecord.getOriginalDishId());
                customerMenuVO.setReplacementReason(menuRecord.getReplacementReason());
                customerMenus.add(customerMenuVO);
            }
            vo.setCustomerMenus(customerMenus);
            voList.add(vo);
        }

        // 4. 转换为 PageResult
        return new PageResult<>(voList, recordResult.getTotal());
    }

    /**
     * 构建排餐结果
     */
    private DishScheduleResult buildScheduleResult(String dateStr, String mealType, Integer customerId, String scheduleMode) {
        DishScheduleResult result = new DishScheduleResult();
        result.setDate(dateStr);

        // 1. 计算周数和星期
        int[] weekAndDay = calculateWeekAndDay(dateStr);
        int week = weekAndDay[0];
        int day = weekAndDay[1];
        result.setWeek(week);
        result.setDay(day);

        // 2. 查找生效客户并生成客户菜单
        List<DishScheduleResult.CustomerMenu> customerMenus = buildCustomerMenus(dateStr, week, day, mealType, customerId, scheduleMode);
        result.setCustomers(customerMenus);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSchedule(Long id) {
        // 1. 查询排餐记录
        DishScheduleRecord record = dishScheduleRecordMapper.selectById(id);
        if (record == null || record.getDeleted()) {
            return;
        }
        // 2. 软删除关联的客户菜单记录
        QueryWrapper<CustomerMenuRecord> menuWrapper = new QueryWrapper<>();
        menuWrapper.eq("record_id", id).eq("deleted", false);
        List<CustomerMenuRecord> existingMenus = customerMenuRecordMapper.selectList(menuWrapper);
        if (!existingMenus.isEmpty()) {
            existingMenus.forEach(menu -> menu.setDeleted(true));
            customerMenuRecordService.updateBatchById(existingMenus);
        }
        // 3. 软删除排餐记录
        record.setDeleted(true);
        dishScheduleRecordMapper.updateById(record);
    }

    @Override
    public DailyCustomerStats getDailyCustomerStats(String date) {
        DailyCustomerStats stats = new DailyCustomerStats();
        stats.setDate(date);

        // 1. 查询指定日期的排餐计划 (新表 meal_plan)
        QueryWrapper<MealPlan> planWrapper = new QueryWrapper<>();
        planWrapper.eq("record_date", date).eq("deleted", false);
        List<MealPlan> mealPlans = mealPlanMapper.selectList(planWrapper);

        if (mealPlans.isEmpty()) {
            stats.setTotalCustomerCount(0);
            stats.setGroups(Collections.emptyList());
            stats.setSourceGroups(Collections.emptyList());
            return stats;
        }

        // 2. 收集所有 mealPlanId，一次性查询所有客户排餐计划 (新表 meal_plan_customer)
        List<Long> mealPlanIds = mealPlans.stream()
                .map(MealPlan::getId)
                .collect(Collectors.toList());

        QueryWrapper<MealPlanCustomer> customerWrapper = new QueryWrapper<>();
        customerWrapper.in("meal_plan_id", mealPlanIds).eq("deleted", false);
        List<MealPlanCustomer> planCustomers = mealPlanCustomerMapper.selectList(customerWrapper);

        if (planCustomers.isEmpty()) {
            stats.setTotalCustomerCount(0);
            stats.setGroups(Collections.emptyList());
            stats.setSourceGroups(Collections.emptyList());
            return stats;
        }

        // 3. 收集所有客户ID和订单ID，获取套餐信息
        Set<Long> customerIds = planCustomers.stream()
                .map(MealPlanCustomer::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> orderIds = planCustomers.stream()
                .map(MealPlanCustomer::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3.1 查询客户档案获取来源
        Map<Long, me.zhengjie.modules.customer.profile.domain.CustomerProfile> customerProfileMap = new HashMap<>();
        if (!customerIds.isEmpty()) {
            QueryWrapper<me.zhengjie.modules.customer.profile.domain.CustomerProfile> profileWrapper = new QueryWrapper<>();
            profileWrapper.in("id", customerIds);
            List<me.zhengjie.modules.customer.profile.domain.CustomerProfile> profiles = customerProfileMapper.selectList(profileWrapper);
            for (me.zhengjie.modules.customer.profile.domain.CustomerProfile p : profiles) {
                customerProfileMap.put(p.getId(), p);
            }
        }

        // 3.2 查询订单获取套餐ID
        Map<Long, me.zhengjie.modules.customer.order.domain.CustomerOrder> orderMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            QueryWrapper<me.zhengjie.modules.customer.order.domain.CustomerOrder> orderWrapper = new QueryWrapper<>();
            orderWrapper.in("id", orderIds);
            List<me.zhengjie.modules.customer.order.domain.CustomerOrder> orders = customerOrderMapper.selectList(orderWrapper);
            for (me.zhengjie.modules.customer.order.domain.CustomerOrder o : orders) {
                orderMap.put(o.getId(), o);
            }
        }

        // 3.3 查询套餐获取套餐名称
        Set<Long> packageIds = orderMap.values().stream()
                .map(me.zhengjie.modules.customer.order.domain.CustomerOrder::getParentPackageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, me.zhengjie.modules.customer.pkg.domain.ParentPackage> packageMap = new HashMap<>();
        if (!packageIds.isEmpty()) {
            QueryWrapper<me.zhengjie.modules.customer.pkg.domain.ParentPackage> packageWrapper = new QueryWrapper<>();
            packageWrapper.in("id", packageIds);
            List<me.zhengjie.modules.customer.pkg.domain.ParentPackage> packages = parentPackageMapper.selectList(packageWrapper);
            for (me.zhengjie.modules.customer.pkg.domain.ParentPackage p : packages) {
                packageMap.put(p.getId(), p);
            }
        }

        // 4. 把 mealPlanId -> mealType 映射
        Map<Long, String> mealPlanMealTypeMap = mealPlans.stream()
                .collect(Collectors.toMap(MealPlan::getId, MealPlan::getMealType));

        // 5. 按 (mealType, mealPackage) 分组，统计 distinct customerId
        // 复合键: mealType|mealPackage
        Map<String, Set<Long>> groupCustomerIds = new LinkedHashMap<>();

        for (MealPlanCustomer pc : planCustomers) {
            String mealType = mealPlanMealTypeMap.get(pc.getMealPlanId());
            if (mealType == null) continue;

            me.zhengjie.modules.customer.order.domain.CustomerOrder order = orderMap.get(pc.getOrderId());
            if (order == null) continue;

            me.zhengjie.modules.customer.pkg.domain.ParentPackage pkg = packageMap.get(order.getParentPackageId());
            String mealPackage = (pkg != null) ? pkg.getPackageCode() : null;
            String mealPackageDesc = (pkg != null) ? pkg.getPackageName() : null;
            if (mealPackage == null) continue;

            String key = mealType + "|" + mealPackage;
            groupCustomerIds.computeIfAbsent(key, k -> new HashSet<>()).add(pc.getCustomerId());
        }

        // 6. 组装结果 - 按 (餐次, 套餐) 分组
        List<DailyCustomerStats.MealPackageGroup> groups = new ArrayList<>();
        int totalCount = 0;

        List<String> sortedKeys = groupCustomerIds.keySet().stream()
                .sorted((a, b) -> {
                    String aType = a.split("\\|")[0];
                    String bType = b.split("\\|")[0];
                    int typeCmp = aType.compareTo(bType);
                    if (typeCmp != 0) return typeCmp;
                    return a.compareTo(b);
                })
                .collect(Collectors.toList());

        for (String key : sortedKeys) {
            String[] parts = key.split("\\|");
            String mealType = parts[0];
            String mealPackage = parts[1];

            Set<Long> customerIdSet = groupCustomerIds.get(key);
            int count = customerIdSet != null ? customerIdSet.size() : 0;
            totalCount += count;

            DailyCustomerStats.MealPackageGroup group = new DailyCustomerStats.MealPackageGroup();
            group.setMealType(mealType);
            group.setMealPackage(mealPackage);
            // 从 packageMap 获取套餐名称
            String pkgDesc = packageMap.values().stream()
                    .filter(p -> p.getPackageCode().equals(mealPackage))
                    .findFirst()
                    .map(me.zhengjie.modules.customer.pkg.domain.ParentPackage::getPackageName)
                    .orElse(mealPackage);
            group.setMealPackageDesc(pkgDesc);
            group.setCustomerCount(count);
            groups.add(group);
        }

        // 7. 按来源分组，统计 distinct customerId
        Map<String, Set<Long>> sourceCustomerIds = new LinkedHashMap<>();
        for (MealPlanCustomer pc : planCustomers) {
            me.zhengjie.modules.customer.order.domain.CustomerOrder order = orderMap.get(pc.getOrderId());
            if (order == null) continue;
            String source = (order.getCustomerSource() != null && !order.getCustomerSource().isEmpty())
                    ? order.getCustomerSource() : "未知来源";
            sourceCustomerIds.computeIfAbsent(source, k -> new HashSet<>()).add(pc.getCustomerId());
        }

        List<DailyCustomerStats.SourceGroup> sourceGroups = new ArrayList<>();
        for (Map.Entry<String, Set<Long>> entry : sourceCustomerIds.entrySet()) {
            DailyCustomerStats.SourceGroup sg = new DailyCustomerStats.SourceGroup();
            sg.setSource(entry.getKey());
            sg.setSourceDesc(entry.getKey());
            sg.setCustomerCount(entry.getValue().size());
            sourceGroups.add(sg);
        }

        stats.setTotalCustomerCount(totalCount);
        stats.setGroups(groups);
        stats.setSourceGroups(sourceGroups);
        return stats;
    }

    @Override
    public List<Map<String, Object>> getCustomerSourceStats(String date) {
        // 1. 查询指定日期的排餐计划 (新表 meal_plan)
        QueryWrapper<MealPlan> planWrapper = new QueryWrapper<>();
        planWrapper.eq("record_date", date).eq("deleted", false);
        List<MealPlan> mealPlans = mealPlanMapper.selectList(planWrapper);

        if (mealPlans.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 查询所有客户排餐计划 (新表 meal_plan_customer)
        List<Long> mealPlanIds = mealPlans.stream()
                .map(MealPlan::getId)
                .collect(Collectors.toList());

        QueryWrapper<MealPlanCustomer> customerWrapper = new QueryWrapper<>();
        customerWrapper.in("meal_plan_id", mealPlanIds).eq("deleted", false);
        List<MealPlanCustomer> planCustomers = mealPlanCustomerMapper.selectList(customerWrapper);

        if (planCustomers.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 收集客户ID，查询客户档案
        Set<Long> customerIds = planCustomers.stream()
                .map(MealPlanCustomer::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, me.zhengjie.modules.customer.profile.domain.CustomerProfile> customerProfileMap = new HashMap<>();
        if (!customerIds.isEmpty()) {
            QueryWrapper<me.zhengjie.modules.customer.profile.domain.CustomerProfile> profileWrapper = new QueryWrapper<>();
            profileWrapper.in("id", customerIds);
            List<me.zhengjie.modules.customer.profile.domain.CustomerProfile> profiles = customerProfileMapper.selectList(profileWrapper);
            for (me.zhengjie.modules.customer.profile.domain.CustomerProfile p : profiles) {
                customerProfileMap.put(p.getId(), p);
            }
        }

        // 4. 按来源分组计数（从订单获取客户来源）
        Map<String, Integer> sourceCountMap = new LinkedHashMap<>();
        for (MealPlanCustomer pc : planCustomers) {
            me.zhengjie.modules.customer.order.domain.CustomerOrder order = orderMap.get(pc.getOrderId());
            if (order == null) continue;
            String source = (order.getCustomerSource() != null && !order.getCustomerSource().isEmpty())
                    ? order.getCustomerSource() : "other";
            sourceCountMap.merge(source, 1, Integer::sum);
        }

        // 5. 组装结果
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sourceCountMap.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", entry.getKey());
            item.put("sourceDesc", entry.getKey());
            item.put("customerCount", entry.getValue());
            result.add(item);
        }
        return result;
    }
}
