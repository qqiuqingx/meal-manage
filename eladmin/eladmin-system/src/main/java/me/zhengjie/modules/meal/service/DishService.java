package me.zhengjie.modules.meal.service;

import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.dto.DishQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishScheduleResult;
import me.zhengjie.modules.meal.domain.dto.DishScheduleRecordQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishScheduleRecordVO;

import java.util.List;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import me.zhengjie.modules.meal.domain.dto.DishScheduleStats;
import me.zhengjie.modules.meal.domain.dto.DailyCustomerStats;
import me.zhengjie.utils.PageResult;

/**
 * 菜品服务接口
 * @author qqx
 * @date 2026-03-14
 **/
public interface DishService extends IService<Dish> {

    /**
     * 查询数据分页
     * @param criteria 条件
     * @param page 分页参数
     * @return PageResult
     */
    PageResult<Dish> queryAll(DishQueryCriteria criteria, Page<Object> page);

    /**
     * 查询所有数据不分页
     * @param criteria 条件参数
     * @return List<Dish>
     */
    List<Dish> queryAll(DishQueryCriteria criteria);

    /**
     * 创建
     * @param resources /
     */
    void create(Dish resources);

    /**
     * 编辑
     * @param resources /
     */
    void update(Dish resources);

    /**
     * 多选删除
     * @param ids /
     */
    void deleteAll(List<Integer> ids);

    /**
     * 导出数据
     * @param all 待导出的数据
     * @param response /
     * @throws IOException /
     */
    void download(List<Dish> all, HttpServletResponse response) throws IOException;

    /**
     * 按排期查询菜品
     * @param week 周数
     * @param day 星期
     * @param mealType 餐次
     * @return List<Dish>
     */
    List<Dish> findBySchedule(Integer week, Integer day, String mealType);

    /**
     * 获取客户可用菜品（根据忌口过滤）
     * @param customerId 客户ID
     * @param mealType 餐次
     * @param week 周数
     * @param day 星期
     * @return List<Dish>
     */
    List<Dish> findAvailableByCustomerId(Integer customerId, String mealType, Integer week, Integer day);

    /**
     * 获取排餐结果并保存记录
     * @param date 日期（yyyy-MM-dd）
     * @param mealType 餐次（LUNCH/DINNER/ALL）
     * @param customerId 客户ID（可选，不传则为所有生效客户）
     * @return DishScheduleResult
     */
    DishScheduleResult getScheduleAndSave(String date, String mealType, Integer customerId);
    DishScheduleResult getScheduleAndSaveNew(String date, String mealType, Integer customerId);
    /**
     * 获取首页排餐统计数据
     * @param date 日期（yyyy-MM-dd）
     * @return DishScheduleStats
     */
    DishScheduleStats getScheduleStats(String date);

    /**
     * 查询排餐记录列表（分页）
     * @param criteria 查询条件
     * @param page 分页参数
     * @return PageResult
     */
    PageResult<DishScheduleRecordVO> queryScheduleRecord(DishScheduleRecordQueryCriteria criteria, Page<Object> page);

    /**
     * 删除排餐记录（软删除，同时删除关联的客户菜单记录）
     * @param id 排餐记录ID
     */
    void deleteSchedule(Long id);

    /**
     * 获取当天客户总数按套餐和餐次分组统计
     * @param date 日期（yyyy-MM-dd）
     * @return DailyCustomerStats
     */
    DailyCustomerStats getDailyCustomerStats(String date);
}
