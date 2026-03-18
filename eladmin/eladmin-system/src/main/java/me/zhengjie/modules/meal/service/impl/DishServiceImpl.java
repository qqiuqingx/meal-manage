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
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.meal.mapper.CustomerDietaryRestrictionsMapper;
import me.zhengjie.modules.meal.mapper.CustomerMenuRecordMapper;
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
    private final DishIngredientMapper dishIngredientMapper;

    private static final String[] DISH_TYPES = {"MAIN", "SIDE", "SOUP", "VEGETABLE", "RICE"};
    private static final String[] MEAL_TYPES = {"LUNCH", "DINNER"};
    private static final String MEAL_TYPE_ALL = "ALL";

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

            // 根据 mealType 参数决定处理哪些餐次
            if (mealType == null || MEAL_TYPE_ALL.equals(mealType) || "LUNCH".equals(mealType)) {
                log.info("[客户:{}, 套餐:{}] 构建午餐菜单, 周:{}-{}", customer.getCustomerName(), mealPackage, week, day);
                Map<String, DishScheduleResult.DishVO> lunchMenu = buildCustomerMenuMap(week, day, "LUNCH", mealPackage, customer.getRestrictions());
                menuByMealType.setLunch(lunchMenu);
                log.info("[客户:{}] 午餐菜单菜品数: {}", customer.getCustomerName(), lunchMenu.size());
            }
            if (mealType == null || MEAL_TYPE_ALL.equals(mealType) || "DINNER".equals(mealType)) {
                log.info("[客户:{}, 套餐:{}] 构建晚餐菜单, 周:{}-{}", customer.getCustomerName(), mealPackage, week, day);
                Map<String, DishScheduleResult.DishVO> dinnerMenu = buildCustomerMenuMap(week, day, "DINNER", mealPackage, customer.getRestrictions());
                menuByMealType.setDinner(dinnerMenu);
                log.info("[客户:{}] 晚餐菜单菜品数: {}", customer.getCustomerName(), dinnerMenu.size());
            }
            customerMenu.setMenu(menuByMealType);

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
     * 为客户构建菜单，处理忌口替换
     */
    private Map<String, DishScheduleResult.DishVO> buildCustomerMenuMap(int week, int day, String mealType, String mealPackage, List<String> restrictions) {
        Map<String, DishScheduleResult.DishVO> menuMap = new LinkedHashMap<>();

        // 获取该套餐、餐次的所有菜品
        DishQueryCriteria criteria = new DishQueryCriteria();
        criteria.setMealType(mealType);
        criteria.setMealPackage(mealPackage);
        List<Dish> allDishes = dishMapper.findAll(criteria);
        log.info("[buildCustomerMenuMap] 查询到菜品数量: {}, 套餐:{}, 餐次:{}", allDishes.size(), mealPackage, mealType);
        // 填充配料列表
        for (Dish dish : allDishes) {
            dish.setIngredientList(convertToDtoList(dishIngredientMapper.findRelationsByDishId(dish.getId())));
        }

        final String scheduleKey = week + "-" + day;
        log.info("[buildCustomerMenuMap] 排期key: {}, 菜品排期列表: {}", scheduleKey, allDishes.stream().map(Dish::getSchedule).collect(Collectors.toList()));

        // 先获取默认菜单
        for (Dish dish : allDishes) {
            List<String> schedule = dish.getSchedule();
            if (schedule != null && schedule.contains(scheduleKey)) {
                String dishType = dish.getDishType();
                if (!menuMap.containsKey(dishType)) {
                    // 检查是否需要替换
                    DishScheduleResult.DishVO dishVO = checkAndReplaceDish(dish, allDishes, dishType, scheduleKey, restrictions);
                    menuMap.put(dishType, dishVO);
                    log.info("[buildCustomerMenuMap] 匹配到菜品: {} - {}", dishType, dish.getName());
                }
            }
        }

        // 补齐缺失的菜品类型
        for (String dishType : DISH_TYPES) {
            if (!menuMap.containsKey(dishType)) {
                DishScheduleResult.DishVO dishVO = findReplacementDish(allDishes, dishType, scheduleKey, restrictions);
                if (dishVO != null) {
                    menuMap.put(dishType, dishVO);
                    log.info("[buildCustomerMenuMap] 补齐菜品: {} - {}", dishType, dishVO.getName());
                } else {
                    log.warn("[buildCustomerMenuMap] 无法找到菜品类型: {} 的可用菜品，请检查菜品排期配置!", dishType);
                }
            }
        }

        return menuMap;
    }

    /**
     * 检查菜品是否需要替换，如果需要则返回替换后的菜品
     */
    private DishScheduleResult.DishVO checkAndReplaceDish(Dish dish, List<Dish> allDishes, String dishType, String scheduleKey, List<String> restrictions) {
        // 如果没有忌口，直接返回原菜品
        if (restrictions == null || restrictions.isEmpty()) {
            return DishScheduleResult.DishVO.fromDish(dish);
        }

        // 检查配料是否包含忌口
        String ingredients = dish.getIngredients();
        if (ingredients != null && !ingredients.isEmpty()) {
            for (String restriction : restrictions) {
                if (ingredients.contains(restriction)) {
                    // 需要替换
                    DishScheduleResult.DishVO replacement = findReplacementDish(allDishes, dishType, scheduleKey, restrictions);
                    if (replacement != null) {
                        replacement.setReplaced(true);
                        replacement.setOriginalId(dish.getId());
                        replacement.setReason("忌口" + restriction);
                        return replacement;
                    }
                }
            }
        }

        return DishScheduleResult.DishVO.fromDish(dish);
    }

    /**
     * 查找替换菜品（同类菜品中查找不包含忌口的）
     */
    private DishScheduleResult.DishVO findReplacementDish(List<Dish> allDishes, String dishType, String scheduleKey, List<String> restrictions) {
        // 筛选同类菜品
        List<Dish> sameTypeDishes = allDishes.stream()
            .filter(d -> d.getDishType().equals(dishType))
            .filter(d -> {
                List<String> schedule = d.getSchedule();
                return schedule != null && schedule.contains(scheduleKey);
            })
            .collect(Collectors.toList());

        // 如果没有忌口，返回第一个
        if (restrictions == null || restrictions.isEmpty()) {
            if (!sameTypeDishes.isEmpty()) {
                return DishScheduleResult.DishVO.fromDish(sameTypeDishes.get(0));
            }
            return null;
        }

        // 查找不包含忌口的菜品
        for (Dish dish : sameTypeDishes) {
            String ingredients = dish.getIngredients();
            if (ingredients == null || ingredients.isEmpty()) {
                return DishScheduleResult.DishVO.fromDish(dish);
            }

            boolean containsRestriction = false;
            for (String restriction : restrictions) {
                if (ingredients.contains(restriction)) {
                    containsRestriction = true;
                    break;
                }
            }

            if (!containsRestriction) {
                return DishScheduleResult.DishVO.fromDish(dish);
            }
        }

        // 如果找不到合适的替换，返回原菜品
        if (!sameTypeDishes.isEmpty()) {
            DishScheduleResult.DishVO vo = DishScheduleResult.DishVO.fromDish(sameTypeDishes.get(0));
            vo.setReplaced(true);
            vo.setReason("无合适替换菜品");
            return vo;
        }

        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishScheduleResult getScheduleAndSave(String date, String mealType, Integer customerId) {
        log.info("========== 开始生成排餐计划 ==========");
        log.info("请求参数: date={}, mealType={}, customerId={}", date, mealType, customerId);

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
        log.info("[步骤3] 待处理餐次: {}", Arrays.toString(mealTypesToProcess));

        for (String mt : mealTypesToProcess) {
            log.info("---------- 开始处理餐次: {} ----------", mt);

            // 步骤4: 软删除已存在的排餐记录

            QueryWrapper<DishScheduleRecord> deleteWrapper = new QueryWrapper<>();
            deleteWrapper.eq("record_date", date).eq("meal_type", mt).eq("deleted", false);
            List<DishScheduleRecord> existingRecords = dishScheduleRecordMapper.selectList(deleteWrapper);


            for (DishScheduleRecord existingRecord : existingRecords) {
                // 软删除关联的客户菜单记录
                QueryWrapper<CustomerMenuRecord> menuDeleteWrapper = new QueryWrapper<>();
                menuDeleteWrapper.eq("record_id", existingRecord.getId()).eq("deleted", false);
                List<CustomerMenuRecord> existingMenus = customerMenuRecordMapper.selectList(menuDeleteWrapper);


                for (CustomerMenuRecord menu : existingMenus) {
                    menu.setDeleted(true);
                    customerMenuRecordMapper.updateById(menu);
                }
                // 软删除排餐记录
                existingRecord.setDeleted(true);
                dishScheduleRecordMapper.updateById(existingRecord);
            }

            // 步骤5: 统计该餐次的客户数量
            log.info("[步骤5] 统计餐次客户数量...");
            int customerCount = 0;
            if (result.getCustomers() != null) {
                for (DishScheduleResult.CustomerMenu cm : result.getCustomers()) {
                    if (cm.getMenu() != null) {
                        DishScheduleResult.MenuByPackage menuByPackage = cm.getMenu();
                        if ("LUNCH".equals(mt) && menuByPackage.getLunch() != null && !menuByPackage.getLunch().isEmpty()) {
                            customerCount++;
                        } else if ("DINNER".equals(mt) && menuByPackage.getDinner() != null && !menuByPackage.getDinner().isEmpty()) {
                            customerCount++;
                        }
                    }
                }
            }
            log.info("[步骤5] {}餐次客户数: {}", mt, customerCount);

            // 步骤6: 保存排餐记录
            log.info("[步骤6] 保存排餐记录...");
            DishScheduleRecord record = new DishScheduleRecord();
            record.setRecordDate(date);
            record.setMealType(mt);
            record.setWeekNum(weekNum);
            record.setDayOfWeek(dayOfWeek);
            record.setCustomerCount(customerCount);
            record.setCreateTime(new Timestamp(System.currentTimeMillis()));
            dishScheduleRecordMapper.insert(record);
            log.info("[步骤6] 排餐记录保存成功, ID: {}", record.getId());

            // 步骤7: 保存客户菜单记录
            log.info("[步骤7] 保存客户菜单记录...");
            int menuRecordCount = 0;
            if (result.getCustomers() != null) {
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
                            menuRecord.setDishIngredients(dishVO.getIngredients());
                            menuRecord.setIsReplaced(dishVO.getReplaced());
                            menuRecord.setOriginalDishId(dishVO.getOriginalId());
                            menuRecord.setReplacementReason(dishVO.getReason());
                            menuRecord.setCreateTime(new Timestamp(System.currentTimeMillis()));
                            customerMenuRecordMapper.insert(menuRecord);
                            menuRecordCount++;
                        }
                    }
                }
            }
            log.info("[步骤7] 客户菜单记录保存完成, 共{}条", menuRecordCount);
            log.info("---------- 餐次 {} 处理完成 ----------", mt);
        }

        log.info("========== 排餐计划生成完成 ==========");
        log.info("排餐日期: {}, 处理餐次: {}, 总客户数: {}", date, Arrays.toString(mealTypesToProcess),
                result.getCustomers() != null ? result.getCustomers().size() : 0);

        return result;
    }

    @Override
    public DishScheduleStats getScheduleStats(String date) {
        DishScheduleStats stats = new DishScheduleStats();
        stats.setDate(date);

        // 查询排餐记录
        QueryWrapper<DishScheduleRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("record_date", date).eq("deleted", false);
        List<DishScheduleRecord> records = dishScheduleRecordMapper.selectList(recordWrapper);

        Map<String, DishScheduleStats.MealTypeStats> statsMap = new HashMap<>();

        for (DishScheduleRecord record : records) {
            DishScheduleStats.MealTypeStats mealTypeStats = new DishScheduleStats.MealTypeStats();
            mealTypeStats.setCustomerCount(record.getCustomerCount());

            // 查询该餐次的客户菜单记录
            QueryWrapper<CustomerMenuRecord> menuWrapper = new QueryWrapper<>();
            menuWrapper.eq("record_id", record.getId()).eq("deleted", false);
            List<CustomerMenuRecord> menuRecords = customerMenuRecordMapper.selectList(menuWrapper);

            // 统计替换数量和菜单
            int replacedCount = 0;
            Map<String, DishScheduleStats.DishTypeMenu> menuMap = new LinkedHashMap<>();

            for (CustomerMenuRecord menuRecord : menuRecords) {
                if (Boolean.TRUE.equals(menuRecord.getIsReplaced())) {
                    replacedCount++;
                }

                String dishType = menuRecord.getDishType();
                if (!menuMap.containsKey(dishType)) {
                    DishScheduleStats.DishTypeMenu dishTypeMenu = new DishScheduleStats.DishTypeMenu();
                    dishTypeMenu.setDishId(menuRecord.getDishId());
                    dishTypeMenu.setDishName(menuRecord.getDishName());
                    dishTypeMenu.setReplacedCount(0);
                    menuMap.put(dishType, dishTypeMenu);
                }

                if (Boolean.TRUE.equals(menuRecord.getIsReplaced())) {
                    DishScheduleStats.DishTypeMenu dtm = menuMap.get(dishType);
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

        List<DishScheduleRecordVO> voList = new ArrayList<>();

        // 2. 对每条排餐记录，查询关联的客户菜单记录
        for (DishScheduleRecord record : recordResult.getRecords()) {
            DishScheduleRecordVO vo = new DishScheduleRecordVO();
            vo.setRecordId(record.getId());
            vo.setRecordDate(record.getRecordDate());
            vo.setMealType(record.getMealType());
            vo.setWeekNum(record.getWeekNum());
            vo.setDayOfWeek(record.getDayOfWeek());
            vo.setCustomerCount(record.getCustomerCount());
            vo.setCreateTime(record.getCreateTime());

            // 查询客户菜单记录
            QueryWrapper<CustomerMenuRecord> menuWrapper = new QueryWrapper<>();
            menuWrapper.eq("record_id", record.getId()).eq("deleted", false);

            // 客户ID过滤
            if (criteria.getCustomerId() != null) {
                menuWrapper.eq("customer_id", criteria.getCustomerId());
            }

            // 客户名称模糊查询
            if (criteria.getCustomerName() != null && !criteria.getCustomerName().isEmpty()) {
                menuWrapper.like("customer_name", criteria.getCustomerName());
            }

            // 套餐类型/菜品类型过滤
            if (criteria.getDishType() != null && !criteria.getDishType().isEmpty()) {
                menuWrapper.eq("dish_type", criteria.getDishType());
            }

            List<CustomerMenuRecord> menuRecords = customerMenuRecordMapper.selectList(menuWrapper);

            // 转换为 VO
            List<DishScheduleRecordVO.CustomerMenuVO> customerMenus = new ArrayList<>();
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

        // 3. 转换为 PageResult
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

        // 2. 构建按套餐分组的菜单
        Map<String, DishScheduleResult.MenuByPackage> menuByPackage = new LinkedHashMap<>();
        for (MealPackageEnum mealPackage : MealPackageEnum.values()) {
            String packageCode = mealPackage.getCode();
            DishScheduleResult.MenuByPackage menuByMealType = new DishScheduleResult.MenuByPackage();

            // 根据 mealType 参数决定处理哪些餐次
            if (mealType == null || MEAL_TYPE_ALL.equals(mealType) || "LUNCH".equals(mealType)) {
                menuByMealType.setLunch(buildMenuMap(week, day, "LUNCH", packageCode));
            }
            if (mealType == null || MEAL_TYPE_ALL.equals(mealType) || "DINNER".equals(mealType)) {
                menuByMealType.setDinner(buildMenuMap(week, day, "DINNER", packageCode));
            }
            menuByPackage.put(packageCode, menuByMealType);
        }
        result.setMenuByPackage(menuByPackage);

        // 3. 查找生效客户并生成客户菜单
        List<DishScheduleResult.CustomerMenu> customerMenus = buildCustomerMenus(dateStr, week, day, mealType, customerId);
        result.setCustomers(customerMenus);

        return result;
    }
}
