package me.zhengjie.modules.meal.mapper;

import me.zhengjie.modules.meal.domain.DishScheduleRecord;
import me.zhengjie.modules.meal.domain.CustomerMenuRecord;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 排餐记录 Mapper 接口
 * @author qqx
 * @date 2026-03-14
 **/
@Mapper
public interface DishScheduleRecordMapper extends BaseMapper<DishScheduleRecord> {
}
