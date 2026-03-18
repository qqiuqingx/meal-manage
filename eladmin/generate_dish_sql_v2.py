#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成菜品和配料的SQL插入语句 - 优化版
使用配料词典进行匹配
"""
import pandas as pd
import json
import re

# 常见配料词典（按类别分组）
INGREDIENT_DICT = {
    # 肉类
    '猪肉', '五花肉', '梅花肉', '里脊肉', '猪腱子', '猪蹄', '猪脚', '猪手', '猪排骨', '排骨', '颈骨', '猪板筋', '板筋',
    '牛肉', '牛腩', '牛柳', '牛里脊', '牛肋排', '牛肋条', '牛肋肉', '牛排', '牛腱子', '牛颈肉', '吊龙',
    '鸡肉', '鸡', '三黄鸡', '土鸡', '公鸡', '土公鸡', '乌鸡', '鸡胸肉', '鸡腿肉', '鸡翅', '鸡翅中', '掌中宝',
    '鸭肉', '鸭子', '老鸭', '麻鸭', '小土鸭', '鸽子', '乳鸽', '鹌鹑蛋',
    '兔肉', '兔子', '兔丁', '羊肚', '猪肚', '肚条',

    # 海鲜水产
    '虾', '虾仁', '基围虾', '河虾', '青虾', '大虾', '鲜虾',
    '鱼', '鲫鱼', '乌鱼', '鳕鱼', '小黄鱼', '黄花鱼', '带鱼', '鲈鱼', '多宝鱼',
    '鲍鱼', '花蛤', '墨鱼', '鱿鱼', '海带', '紫菜',

    # 丸子类
    '丸子', '肉丸', '猪肉丸', '牛肉丸', '虾丸', '墨斗丸', '潮汕虾丸', '虾滑',

    # 蔬菜类
    '白菜', '娃娃菜', '小白菜', '大白菜', '杭白菜', '包心芥菜', '芥菜', '芥兰', '芥兰苗',
    '上海青', '菜心', '油菜苔', '油麦菜', '生菜', '苦菊',
    '菠菜', '芹菜', '西芹', '小芹菜', '凤尾', '茭白', '莴笋', '青笋', '芦笋',
    '西兰花', '西南花', '花菜', '莲花白', '莲白', '卷心菜', '紫甘蓝',
    '豆角', '豇豆', '四季豆', '荷兰豆', '豌豆', '毛豆', '青豆', '黄豆', '雪豆', '红豆', '赤小豆', '红腰豆', '黑豆',
    '萝卜', '白萝卜', '胡萝卜', '樱桃萝卜', '小萝卜',
    '冬瓜', '南瓜', '贝贝南瓜', '丝瓜', '黄瓜', '三月瓜',
    '番茄', '西红柿', '小番茄', '山姆小番茄',
    '土豆', '小土豆', '山药', '芋头', '芋儿', '紫薯',
    '莲藕', '藕', '茄子', '秋葵',
    '青椒', '红椒', '黄椒', '绿椒', '彩椒', '辣椒', '洋葱',

    # 菌菇类
    '香菇', '干香菇', '平菇', '口蘑', '金针菇', '杏鲍菇', '白玉菇', '蟹味菇', '海鲜菇',
    '木耳', '黑木耳', '银耳', '白木耳', '金耳', '黄花', '黄花菜',
    '松茸', '牛肝菌', '羊肚菌', '鹿茸菌', '赤松茸',

    # 豆制品
    '豆腐', '豆腐干', '豆皮', '腐竹',

    # 调料香料
    '葱', '小葱', '大葱', '葱段', '葱花', '葱丝',
    '姜', '老姜', '生姜', '姜片', '蒜', '大蒜', '蒜蓉',
    '陈皮', '法香', '虫草花', '枸杞', '红枣', '大枣', '莲子', '百合', '桂圆',

    # 坚果
    '核桃', '核桃仁', '花生', '松仁', '松子', '腰果', '板栗', '栗子', '桃仁',

    # 谷物
    '大米', '黑米', '糙米', '红米', '燕麦米', '小米', '藜麦', '三色藜麦',
    '玉米', '鲜玉米', '玉米粒', '玉米碎',

    # 水果
    '橙子', '青桔', '青提', '苹果', '凤梨', '话梅',

    # 其他
    '鸡蛋', '蛋', '蛋清', '蛋黄', '蛋皮', '蛋饺',
    '西米', '粉丝', '面包糠', '芝士', '芝麻', '白芝麻', '黑芝麻',
    '椰肉', '椰子水', '马蹄', '酥肉',
}

def extract_ingredient_names(ingredients_text):
    """从配料文本中提取纯配料名称"""
    if not ingredients_text:
        return []

    found_ingredients = set()

    # 遍历配料词典，查找匹配的配料
    for ingredient in INGREDIENT_DICT:
        if ingredient in ingredients_text:
            found_ingredients.add(ingredient)

    return list(found_ingredients)

def clean_text(text):
    """清理文本"""
    if pd.isna(text):
        return None
    return str(text).strip()

def map_dish_type(type_name):
    """映射菜品类型"""
    type_map = {
        '汤': 'SOUP',
        '主菜': 'MAIN',
        '副菜': 'SIDE',
        '素菜': 'VEGETABLE',
        '米饭': 'RICE'
    }
    return type_map.get(type_name, 'MAIN')

def map_meal_type(meal_name):
    """映射餐次"""
    if meal_name == '午餐':
        return 'LUNCH'
    elif meal_name == '晚餐':
        return 'DINNER'
    return None

def generate_sql():
    file_path = '/Users/qqx/job/code/erp/菜品_副本.xlsx'
    all_sheets = pd.read_excel(file_path, sheet_name=None)

    dish_sql_list = []
    ingredient_set = set()
    dish_id = 1

    # 遍历每个sheet（每周）
    for week_num, (sheet_name, df) in enumerate(all_sheets.items(), start=1):
        print(f"\n处理 {sheet_name} (第{week_num}周)...")

        # 跳过前两行
        data_rows = df.iloc[2:]

        current_meal_type = None
        current_dish_type = None

        for idx, row in data_rows.iterrows():
            meal = clean_text(row['餐别'])
            dish_type = clean_text(row['类型'])

            if meal:
                current_meal_type = map_meal_type(meal)
            if dish_type:
                current_dish_type = map_dish_type(dish_type)

            if not current_meal_type or not current_dish_type:
                continue

            # 处理7天的数据
            day_columns = [
                ('菜名', '配菜', 1),
                ('菜名.1', '配菜.1', 2),
                ('菜名.2', '配菜.2', 3),
                ('菜名.3', '配菜.3', 4),
                ('菜名.4', '配菜.4', 5),
                ('菜名.5', 'Unnamed: 13', 6),
                ('菜名.6', '配菜.5', 7),
            ]

            for name_col, ingredient_col, day in day_columns:
                dish_name = clean_text(row[name_col])
                ingredients_text = clean_text(row[ingredient_col])

                if not dish_name or dish_name in ['菜名', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日']:
                    continue

                # 清理菜名
                dish_name = dish_name.split('\n')[0].strip()

                # 提取配料名称
                ingredient_names = extract_ingredient_names(ingredients_text)

                # 收集配料
                for ing in ingredient_names:
                    ingredient_set.add(ing)

                # 生成排期
                schedule = f"{week_num}-{day}"

                # 转义单引号
                ingredients_escaped = ingredients_text.replace("'", "''") if ingredients_text else ''
                dish_name_escaped = dish_name.replace("'", "''")

                # 生成菜品SQL
                meal_types_json = json.dumps([current_meal_type], ensure_ascii=False)
                schedule_json = json.dumps([schedule], ensure_ascii=False)

                sql = f"""INSERT INTO dish (name, ingredients, dish_type, meal_types, schedule, sort, enabled, create_time, update_time)
VALUES ('{dish_name_escaped}', '{ingredients_escaped}', '{current_dish_type}', '{meal_types_json}', '{schedule_json}', {dish_id}, 1, NOW(), NOW());"""

                dish_sql_list.append(sql)
                dish_id += 1

    # 生成配料SQL
    ingredient_sql_list = []
    for ingredient in sorted(ingredient_set):
        ingredient_escaped = ingredient.replace("'", "''")
        sql = f"""INSERT INTO dish_ingredient (name, enabled, create_time, update_time)
VALUES ('{ingredient_escaped}', 1, NOW(), NOW());"""
        ingredient_sql_list.append(sql)

    # 写入SQL文件
    output_file = '/Users/qqx/job/code/eladmin-mp/eladmin/sql/insert_dishes.sql'
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write("-- 菜品数据插入SQL\n")
        f.write("-- 生成时间: " + pd.Timestamp.now().strftime('%Y-%m-%d %H:%M:%S') + "\n\n")

        f.write("-- ============================================\n")
        f.write("-- 配料表数据\n")
        f.write("-- ============================================\n\n")
        for sql in ingredient_sql_list:
            f.write(sql + "\n")

        f.write("\n\n")
        f.write("-- ============================================\n")
        f.write("-- 菜品表数据\n")
        f.write("-- ============================================\n\n")
        for sql in dish_sql_list:
            f.write(sql + "\n")

    print(f"\n✅ SQL生成完成！")
    print(f"📊 统计信息：")
    print(f"   - 配料数量: {len(ingredient_set)}")
    print(f"   - 菜品数量: {len(dish_sql_list)}")
    print(f"   - 输出文件: {output_file}")

if __name__ == '__main__':
    generate_sql()
