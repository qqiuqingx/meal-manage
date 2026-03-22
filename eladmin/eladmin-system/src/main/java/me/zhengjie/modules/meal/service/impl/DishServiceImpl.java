package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.modules.meal.domain.CustomerDietaryRestrictions;
import me.zhengjie.modules.meal.domain.CustomerMenuRecord;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.DishScheduleRecord;
import me.zhengjie.modules.meal.domain.dto.DishQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishScheduleResult;
import me.zhengjie.modules.meal.domain.dto.DishScheduleStats;
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

    @Override
    public PageResult<Dish> queryAll(DishQueryCriteria criteria, Page<Object> page){
        PageResult<Dish> result = PageUtil.toPage(dishMapper.findAll(criteria, page));
        // 填充配料列表
        for (Dish dish : result.getContent()) {
            dish.setIngredientList(convertToDtoList(dishIngredientMapper.findRelationsByDishId(dish.getId())));
        }
        return result;
    }

    @Override
    public List<Dish> queryAll(DishQueryCriteria criteria){
        List<Dish> list = dishMapper.findAll(criteria);
        // 填充配料列表
        for (Dish dish : list) {
            dish.setIngredientList(convertToDtoList(dishIngredientMapper.findRelationsByDishId(dish.getId())));
        }
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
            map.put("所属套餐", dish.getMealPackages());
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
        // 填充配料列表
        for (Dish dish : dishes) {
            dish.setIngredientList(convertToDtoList(dishIngredientMapper.findRelationsByDishId(dish.getId())));
        }
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
    private List<DishScheduleResult.CustomerMenu> buildCustomerMenus(String dateStr, int week, int day, String mealType, Integer customerId) {
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
            if (!isCustomerActive(customer, targetDate)) {
                continue;
            }
            String pkg = customer.getMealPackage();
            for (String mt : collectMealTypes(mealType)) {
                String key = mt + "|" + pkg;
                if (!packageAndMealKeys.contains(key)) {
                    packageAndMealKeys.add(key);
                    List<Dish> dishes = dishMapper.findAll(buildCriteria(mt, pkg));
                    for (Dish dish : dishes) {
                        dish.setIngredientList(convertToDtoList(dishIngredientMapper.findRelationsByDishId(dish.getId())));
                    }
                    dishesByPackageAndMeal.put(key, dishes);
                    log.info("[buildCustomerMenus] 预查询菜品: mealType={}, mealPackage={}, count={}", mt, pkg, dishes.size());
                }
            }
        }

        for (CustomerDietaryRestrictions customer : allCustomers) {
            // 检查客户是否生效
            if (!isCustomerActive(customer, targetDate)) {
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
            if (mealType == null || MEAL_TYPE_ALL.equals(mealType) || "LUNCH".equals(mealType)) {
                List<Dish> lunchDishes = dishesByPackageAndMeal.get("LUNCH|" + mealPackage);
                log.info("[客户:{}, 套餐:{}] 构建午餐菜单, 周:{}-{}", customer.getCustomerName(), mealPackage, week, day);
                DishScheduleResult.CustomerMenuMapResult lunchResult = buildCustomerMenuMap(week, day, "LUNCH", mealPackage, customer.getRestrictions(), lunchDishes != null ? lunchDishes : new ArrayList<>(), cache);
                menuByMealType.setLunch(lunchResult.getMenuMap());
                allUnableToReplace.addAll(lunchResult.getUnableToReplaceDishes());
                log.info("[客户:{}] 午餐菜单菜品数: {}", customer.getCustomerName(), lunchResult.getMenuMap().size());
            }
            if (mealType == null || MEAL_TYPE_ALL.equals(mealType) || "DINNER".equals(mealType)) {
                List<Dish> dinnerDishes = dishesByPackageAndMeal.get("DINNER|" + mealPackage);
                log.info("[客户:{}, 套餐:{}] 构建晚餐菜单, 周:{}-{}", customer.getCustomerName(), mealPackage, week, day);
                DishScheduleResult.CustomerMenuMapResult dinnerResult = buildCustomerMenuMap(week, day, "DINNER", mealPackage, customer.getRestrictions(), dinnerDishes != null ? dinnerDishes : new ArrayList<>(), cache);
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
     * 检查客户是否在生效期间且有剩余餐数
     */
    private boolean isCustomerActive(CustomerDietaryRestrictions customer, LocalDate targetDate) {
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
     * 检查菜品配料是否包含忌口（只查配料关系表，不依赖 ingredients 字符串字段）
     */
    private boolean containsRestriction(Dish dish, List<String> restrictions) {
        if (restrictions == null || restrictions.isEmpty()) {
            return false;
        }
        List<DishIngredientDto> ingredientList = dish.getIngredientList();
        if (ingredientList == null || ingredientList.isEmpty()) {
            // 配料关系表无数据，不做过滤，但记录日志提醒补充数据
            log.warn("[containsRestriction] 菜品配料关系为空，请补充数据: dishId={}, dishName={}", dish.getId(), dish.getName());
            return false;
        }
        for (DishIngredientDto dto : ingredientList) {
            String name = dto.getIngredientName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            for (String restriction : restrictions) {
                if (name.contains(restriction)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 为客户构建菜单，处理忌口替换
     * @param allDishes  该套餐+餐次的所有菜品（已填充配料，由调用方预查询并共享）
     * @param cache       替换菜品候选缓存（按 dishType+restrictions 缓存已排序的候选菜，索引式分配）
     * @return CustomerMenuMapResult 包含最终菜单和无法替换的菜品列表
     */
    private DishScheduleResult.CustomerMenuMapResult buildCustomerMenuMap(int week, int day, String mealType, String mealPackage, List<String> restrictions, List<Dish> allDishes, Map<String, ReplacementCacheEntry> cache) {
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
                    DishScheduleResult.DishVO dishVO = checkAndReplaceDish(dish, allDishes, dishType, week, day, restrictions, cache);
                    if (dishVO != null) {
                        if ("忌口：无替换菜品".equals(dishVO.getReason())) {
                            // 找不到替换菜，记录到无法替换列表（不入菜单）
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
                DishScheduleResult.DishVO dishVO = findReplacementDish(null, allDishes, dishType, week, day, restrictions, cache);
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
     * 检查菜品是否需要替换，如果需要则返回替换后的菜品
     */
    private DishScheduleResult.DishVO checkAndReplaceDish(Dish dish, List<Dish> allDishes, String dishType, int week, int day, List<String> restrictions, Map<String, ReplacementCacheEntry> cache) {
        if (dish == null || !containsRestriction(dish, restrictions)) {
            return dish != null ? DishScheduleResult.DishVO.fromDish(dish) : null;
        }
        // 需要替换：查找同类不含忌口的菜品（缓存保证同 key 不重复分配）
        DishScheduleResult.DishVO replacement = findReplacementDish(dish, allDishes, dishType, week, day, restrictions, cache);
        if (replacement != null) {
            replacement.setReplaced(true);
            replacement.setOriginalId(dish.getId());
            replacement.setReason("忌口");
        } else {
            // 找不到替换菜，仍返回原菜品（reason 标注无法替换，由调用方决定是否放入菜单）
            replacement = DishScheduleResult.DishVO.fromDish(dish);
            replacement.setReplaced(true);
            replacement.setOriginalId(dish.getId());
            replacement.setReason("忌口：无替换菜品");
        }
        return replacement;
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
     * 为当前用户从缓存中分配一个可用的候选菜
     * @return 分配的菜品（已从缓存中移除），如果没有则返回 null
     */
    private Dish pickFromCacheAndAssign(Map<String, ReplacementCacheEntry> cache, int week, int day, String dishType, List<String> restrictions) {
        String cacheKey = makeReplacementCacheKey(week, day, dishType);
        String restrictionsKey = (restrictions == null || restrictions.isEmpty())
            ? "" : restrictions.stream().sorted().collect(Collectors.joining(","));
        ReplacementCacheEntry entry = cache.get(cacheKey);
        if (entry == null || entry.sortedAll.isEmpty()) {
            return null;
        }
        // 获取或初始化该忌口组的已分配记录
        Set<Integer> taken = entry.takenDishIds.computeIfAbsent(restrictionsKey, k -> new HashSet<>());
        // 过滤：排除含忌口的 + 已被本忌口组分配过的
        for (Dish dish : entry.sortedAll) {
            if (taken.contains(dish.getId())) continue;
            if (containsRestriction(dish, restrictions)) continue;
            taken.add(dish.getId());
            return dish;
        }
        return null;
    }

    /**
     * 查找替换菜品（当前周所有天中找同类不含忌口的菜品）
     * 优先选择排期最接近目标日的菜品（同一周内先检查目标日本身，再依次检查相邻天）
     */
    private DishScheduleResult.DishVO findReplacementDish(Dish replacedDish, List<Dish> allDishes, String dishType, int week, int day, List<String> restrictions, Map<String, ReplacementCacheEntry> cache) {
        String cacheKey = makeReplacementCacheKey(week, day, dishType);

        // ① 尝试从缓存分配（缓存中的候选菜已按排期排序，按顺序取第一个满足忌口且未被该组占用的菜）
        Dish cachedDish = pickFromCacheAndAssign(cache, week, day, dishType, restrictions);
        if (cachedDish != null) {
            log.info("[findReplacementDish] 缓存命中: dishType={}, replacedDish={}", dishType, replacedDish != null ? replacedDish.getName() : "null");
            DishScheduleResult.DishVO vo = DishScheduleResult.DishVO.fromDish(cachedDish);
            if (replacedDish != null) {
                vo.setReplaced(true);
                vo.setOriginalId(replacedDish.getId());
                vo.setReason("忌口");
            }
            return vo;
        }

        // ② 缓存未命中或候选菜已耗尽：从 allDishes 过滤并排序，再写入缓存
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

        // 预计算每个候选菜的排期天
        final int targetDay = day;
        Map<Dish, Integer> scheduleDayMap = candidates.stream()
            .collect(Collectors.toMap(d -> d, d -> firstScheduleDayOfWeek(d.getSchedule(), week)));

        // 按排期距离升序，相同则按菜品ID排
        List<Dish> sorted = candidates.stream()
            .sorted(Comparator
                .comparingInt((Dish d) -> Math.abs(scheduleDayMap.get(d) - targetDay))
                .thenComparingInt(Dish::getId))
            .collect(Collectors.toList());

        // 写入缓存（供后续用户复用）
        cache.put(cacheKey, new ReplacementCacheEntry(sorted));
        log.info("[findReplacementDish] 缓存写入: cacheKey={}, cachedCount={}", cacheKey, sorted.size());

        // 分配第一个
        Dish assigned = pickFromCacheAndAssign(cache, week, day, dishType, restrictions);
        if (assigned == null) {
            return null;
        }

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
    public DishScheduleResult getScheduleAndSave(String date, String mealType, Integer customerId) {
        log.info("========== 开始生成排餐计划 ==========");
        log.info("请求参数: date={}, mealType={}, customerId={}", date, mealTypeCn(mealType), customerId);

        // 步骤1: 构建排餐结果

        DishScheduleResult result = buildScheduleResult(date, mealType, customerId);
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
    private DishScheduleResult buildScheduleResult(String dateStr, String mealType, Integer customerId) {
        DishScheduleResult result = new DishScheduleResult();
        result.setDate(dateStr);

        // 1. 计算周数和星期
        int[] weekAndDay = calculateWeekAndDay(dateStr);
        int week = weekAndDay[0];
        int day = weekAndDay[1];
        result.setWeek(week);
        result.setDay(day);

        // 2. 查找生效客户并生成客户菜单
        List<DishScheduleResult.CustomerMenu> customerMenus = buildCustomerMenus(dateStr, week, day, mealType, customerId);
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
}
