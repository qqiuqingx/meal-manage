/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.modules.meal.util;

import me.zhengjie.exception.BadRequestException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 排期 key 工具
 * @author qqx
 * @date 2026-03-31
 **/
public final class ScheduleKeyUtil {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ScheduleKeyUtil() {
    }

    public static LocalDate parseDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            throw new BadRequestException("排餐日期不能为空");
        }
        try {
            return LocalDate.parse(date, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("排餐日期格式必须为yyyy-MM-dd");
        }
    }

    public static String calcScheduleKey(String date) {
        return calcScheduleKey(parseDate(date));
    }

    public static String calcScheduleKey(LocalDate date) {
        return calcWeek(date) + "-" + calcDay(date);
    }

    public static int calcWeek(LocalDate date) {
        int week = (date.getDayOfMonth() - 1) / 7 + 1;
        return week > 4 ? 1 : week;
    }

    public static int calcDay(LocalDate date) {
        return date.getDayOfWeek().getValue();
    }

    public static boolean isWeekday(LocalDate date) {
        int day = calcDay(date);
        return day >= 1 && day <= 5;
    }

    public static boolean isWeekend(LocalDate date) {
        int day = calcDay(date);
        return day == 6 || day == 7;
    }
}
