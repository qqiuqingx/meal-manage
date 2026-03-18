#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成菜品和配料的SQL插入语句
"""
import pandas as pd
import json
import re

def clean_text(text):
    """清理文本，去除多余空格和换行"""
    if pd.isna(text):
        return None
    return str(text).strip()

def extract_ingredient_names(ingredients_text):
    """从配料文本中提取纯配料名称（去除数量、单位等）"""
    if not ingredients_text:
        return []

    ingredient_names = []

    # 按行分割
    lines = ingredients_text.split('\n')

    for line in lines:
        line = line.strip()
        if not line:
            continue

        # 移除序号（如 1、2、3、）
        line = re.sub(r'^\d+[、.]', '', line)
        line = line.strip()

        # 按逗号、顿号、加号分割多个配料
        parts = re.split(r'[,，、+➕]', line)

        for part in parts:
            part = part.strip()
            if not part:
                continue

            # 提取配料名称：匹配中文名称（2-8个字）在数字或量词之前
            match = re.match(r'^([一-龥]{2,8}?)(?=\d|克|g|毫升|ml|个|只|条|片|块|根|朵|颗|粒|段|节|瓣|圈|牙|半|对|切|去|打|改|见|约|左右|净|毛|走|份|人|用|小|大|中|特|一|二|三|四|五|六|七|八|九|十)', part)

            if match:
                name = match.group(1).strip()

                # 过滤掉明显的非配料词（动词、形容词等）
                if name and len(name) >= 2 and not re.match(r'^(开菜|备注|需要|提前|反复|洗净|挤水|侵泡|泡发|洗干净|装盒|点缀|备用|分开|一起|过油|不过|不是|如果|假如|要是|注意|说明|提示|重要|关键|买|挑|选|问题|品种|均匀|嫩的|小的|大的|新鲜|老|嫩|生|熟|冷|热|温|凉)', name):
                    ingredient_names.append(name)

    return ingredient_names

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
    ingredient_set = set()  # 用于去重配料
    dish_id = 1

    # 遍历每个sheet（每周）
    for week_num, (sheet_name, df) in enumerate(all_sheets.items(), start=1):
        print(f"\n处理 {sheet_name} (第{week_num}周)...")

        # 跳过前两行（星期标题和列标题）
        data_rows = df.iloc[2:]

        current_meal_type = None
        current_dish_type = None

        for idx, row in data_rows.iterrows():
            # 获取餐别和类型
            meal = clean_text(row['餐别'])
            dish_type = clean_text(row['类型'])

            if meal:
                current_meal_type = map_meal_type(meal)
            if dish_type:
                current_dish_type = map_dish_type(dish_type)

            if not current_meal_type or not current_dish_type:
                continue

            # 处理7天的数据（星期一到星期日）
            day_columns = [
                ('菜名', '配菜', 1),      # 星期一
                ('菜名.1', '配菜.1', 2),  # 星期二
                ('菜名.2', '配菜.2', 3),  # 星期三
                ('菜名.3', '配菜.3', 4),  # 星期四
                ('菜名.4', '配菜.4', 5),  # 星期五
                ('菜名.5', 'Unnamed: 13', 6),  # 星期六
                ('菜名.6', '配菜.5', 7),  # 星期日
            ]

            for name_col, ingredient_col, day in day_columns:
                dish_name = clean_text(row[name_col])
                ingredients_text = clean_text(row[ingredient_col])

                if not dish_name or dish_name in ['菜名', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日']:
                    continue

                # 清理菜名（去除换行和额外说明）
                dish_name = dish_name.split('\n')[0].strip()

                # 提取纯配料名称
                ingredient_names = extract_ingredient_names(ingredients_text)

                # 收集所有配料名称（去重）
                for ing in ingredient_names:
                    ingredient_set.add(ing)

                # 生成排期：格式为 week-day（如 1-1 表示第1周星期一）
                schedule = f"{week_num}-{day}"

                # 转义单引号
                ingredients_escaped = ingredients_text.replace("'", "''") if ingredients_text else ''
                dish_name_escaped = dish_name.replace("'", "''")

                # 生成菜品SQL（JSON字段不需要额外引号）
                meal_types_json = json.dumps([current_meal_type], ensure_ascii=False)
                schedule_json = json.dumps([schedule], ensure_ascii=False)

                sql = f"""INSERT INTO dish (name, ingredients, dish_type, meal_types, schedule, sort, enabled, create_time, update_time)
VALUES ('{dish_name_escaped}', '{ingredients_escaped}', '{current_dish_type}', '{meal_types_json}', '{schedule_json}', {dish_id}, 1, NOW(), NOW());"""

                dish_sql_list.append(sql)
                dish_id += 1

    # 生成配料SQL
    ingredient_sql_list = []
    for idx, ingredient in enumerate(sorted(ingredient_set), start=1):
        # 转义单引号
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
