-- 菜品配料关系表数据插入SQL
-- 生成时间: 2026-03-17 23:30:54

-- ============================================
-- 菜品配料关系数据
-- ============================================

INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '猴头菇老鸭汤'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '虫草花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '猴头菇老鸭汤'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '猴头菇老鸭汤'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '鸭子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '核桃仁炖乌鸡'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '生姜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '核桃仁炖乌鸡'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '核桃仁';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '核桃仁炖乌鸡'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '乌鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '核桃仁炖乌鸡'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '核桃';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '核桃仁炖乌鸡'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '花生';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '核桃仁炖乌鸡'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '姜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '核桃仁炖乌鸡'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '核桃仁炖乌鸡'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '核桃仁炖乌鸡'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '桃仁';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '栗子南瓜鸡汤'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '南瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '栗子南瓜鸡汤'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '鸡肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '栗子南瓜鸡汤'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '板栗';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '栗子南瓜鸡汤'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '豆腐';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '牛肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '香菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '鸡蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '生菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕黑豆排骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '排骨';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕黑豆排骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '莲藕';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕黑豆排骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '姜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕黑豆排骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '花生';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕黑豆排骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '藕';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕黑豆排骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '黑豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜猪腱子汤'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜猪腱子汤'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '白萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜猪腱子汤'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜猪腱子汤'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '红枣';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '豆腐';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '鸡蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '白萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '鲫鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '贝贝南瓜蒸排骨'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '贝贝南瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '贝贝南瓜蒸排骨'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '南瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '贝贝南瓜蒸排骨'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '贝贝南瓜蒸排骨'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '排骨';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '珍珠丸子玻璃芡'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '马蹄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '珍珠丸子玻璃芡'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '西米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '珍珠丸子玻璃芡'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '丸子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '珍珠丸子玻璃芡'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '彩椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秘制猪手（黄焖酱味）'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '花生';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秘制猪手（黄焖酱味）'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '猪手';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秘制猪手（黄焖酱味）'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '红腰豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秘制猪手（黄焖酱味）'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '西南花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '葱香多宝鱼（开后一天的盐焗鸡）'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '葱香多宝鱼（开后一天的盐焗鸡）'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '多宝鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '葱香多宝鱼（开后一天的盐焗鸡）'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '葱香多宝鱼（开后一天的盐焗鸡）'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '葱香多宝鱼（开后一天的盐焗鸡）'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '盐焗鸡'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '盐焗鸡'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '法香';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '盐焗鸡'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '橙子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '凤梨牛肉粒'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '凤梨';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '凤梨牛肉粒'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '牛里脊';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '凤梨牛肉粒'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '法香';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '凤梨牛肉粒'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '里脊肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '番茄烧排骨'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '番茄烧排骨'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '排骨';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '番茄烧排骨'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '番茄烧排骨'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '小萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '番茄烧排骨'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '西南花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄油大虾'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄油大虾'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '法香';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '粉蒸牛颈肉'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '牛肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '粉蒸牛颈肉'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '土豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白玉萝卜煲虾仁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白玉萝卜煲虾仁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '肉丸';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白玉萝卜煲虾仁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '虫草花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白玉萝卜煲虾仁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '娃娃菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白玉萝卜煲虾仁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '丸子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白玉萝卜煲虾仁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '白萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白玉萝卜煲虾仁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '猪肉丸';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白玉萝卜煲虾仁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '虾仁';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白玉萝卜煲虾仁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '猪肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白玉萝卜煲虾仁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '腐竹焖五花肉（买第二天的山姆花蛤肉）'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '腐竹';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '腐竹焖五花肉（买第二天的山姆花蛤肉）'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '五花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '腐竹焖五花肉（买第二天的山姆花蛤肉）'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '香菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '腐竹焖五花肉（买第二天的山姆花蛤肉）'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '小葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '腐竹焖五花肉（买第二天的山姆花蛤肉）'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西芹鲍鱼片'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '红椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西芹鲍鱼片'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '西芹';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西芹鲍鱼片'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西芹鲍鱼片'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '鲍鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西芹鲍鱼片'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '花蛤';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秋葵虾滑'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秋葵虾滑'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '肉丸';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秋葵虾滑'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '玉米碎';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秋葵虾滑'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秋葵虾滑'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '猪肉丸';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秋葵虾滑'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秋葵虾滑'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '虾仁';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秋葵虾滑'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '秋葵';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秋葵虾滑'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '猪肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秋葵虾滑'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '秋葵虾滑'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '泉水牛肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '泉水牛肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '西芹';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '泉水牛肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '土豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '泉水牛肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '小土豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '泉水牛肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '泉水牛肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '泉水牛肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '牛腱子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菠菜炒蛋'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '菠菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菠菜炒蛋'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '蛋皮';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菠菜炒蛋'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菠菜炒蛋'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菠菜炒蛋'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '鸡蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豉油上海青'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '葱丝';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豉油上海青'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豉油上海青'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '彩椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豉油上海青'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '上海青';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '时蔬肉沫'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '油麦菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '时蔬肉沫'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '猪肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菜心胡萝卜粒'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菜心胡萝卜粒'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菜心胡萝卜粒'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菜心胡萝卜粒'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '鲜玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菜心胡萝卜粒'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '菜心';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菜心胡萝卜粒'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒青笋'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '紫甘蓝';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒青笋'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '青笋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒青笋'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '彩椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '冬瓜豌豆粒'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '冬瓜豌豆粒'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '冬瓜豌豆粒'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '冬瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '冬瓜豌豆粒'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '豌豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '小炒杭白菜'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '虫草花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '小炒杭白菜'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '杭白菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '小炒杭白菜'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '白菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '三色藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '玉米碎';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆东北长粒香'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '黑米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆东北长粒香'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆东北长粒香'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '糙米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米4'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '红米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米4'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '燕麦米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米4'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '三色藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '玉米碎';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆茉莉香'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '黑米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆茉莉香'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆茉莉香'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '糙米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米4'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '红米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米4'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '燕麦米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米4'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '三色藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '玉米碎';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜吊龙汤'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜吊龙汤'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '吊龙';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜吊龙汤'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '白萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄豆海带排骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '海带';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄豆海带排骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '姜片';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄豆海带排骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '排骨';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄豆海带排骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '姜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄豆海带排骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '黄豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄豆海带排骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '胡萝卜瘦肉汤'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '胡萝卜瘦肉汤'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '胡萝卜瘦肉汤'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '胡萝卜瘦肉汤'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '姜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山药莲子颈骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '莲子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山药莲子颈骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '颈骨';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山药莲子颈骨汤'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '山药';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气养血鸽子汤'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '莲子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气养血鸽子汤'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '红枣';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气养血鸽子汤'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '山药';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气养血鸽子汤'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气养血鸽子汤'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '鸽子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '杂烩煲汤'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '酥肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '杂烩煲汤'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '冬瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '杂烩煲汤'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '鹌鹑蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '杂烩煲汤'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '杂烩煲汤'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '上海青';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '杂烩煲汤'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '蛋饺';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '杂烩煲汤'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '黑木耳';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '杂烩煲汤'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '木耳';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '丝瓜潮汕虾丸汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '丝瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '丝瓜潮汕虾丸汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '墨斗丸';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '丝瓜潮汕虾丸汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '虾丸';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '丝瓜潮汕虾丸汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '丝瓜潮汕虾丸汤'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '潮汕虾丸';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋藕丁掌中宝'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '彩椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋藕丁掌中宝'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋藕丁掌中宝'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋藕丁掌中宝'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '青笋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋藕丁掌中宝'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '鸡蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋藕丁掌中宝'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '藕';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋藕丁掌中宝'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '掌中宝';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '麻油金黄鱼'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '五花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '麻油金黄鱼'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '彩椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '麻油金黄鱼'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '麻油金黄鱼'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '上海青';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '麻油金黄鱼'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '小黄鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '蒜香排骨'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '排骨';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '蒜香排骨'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '青桔';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '蒜香排骨'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '法香';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '蒜香排骨'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '蒜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '蒜香排骨'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '大蒜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '平菇焗虾'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '海鲜菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '平菇焗虾'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '基围虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '平菇焗虾'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '平菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '平菇焗虾'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '平菇焗虾'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香焖牛肋排'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '山姆小番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香焖牛肋排'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香焖牛肋排'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '小番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香焖牛肋排'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '西南花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香焖牛肋排'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '牛肋排';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '桃仁肉片'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '红椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '桃仁肉片'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '黄椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '桃仁肉片'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '黄瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '桃仁肉片'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '桃仁肉片'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '桃仁';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋焖兔'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '兔子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋焖兔'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋焖兔'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋焖兔'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '青笋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '茭白肉丝'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '虫草花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '茭白肉丝'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '茭白肉丝'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '茭白';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '木耳荷兰板筋'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '荷兰豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '木耳荷兰板筋'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '红椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '木耳荷兰板筋'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '猪板筋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '木耳荷兰板筋'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '藕';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '木耳荷兰板筋'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '板筋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '木耳荷兰板筋'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '木耳';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩缤纷鸡丁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩缤纷鸡丁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '黄瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩缤纷鸡丁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '豌豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩缤纷鸡丁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩缤纷鸡丁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩缤纷鸡丁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '鲜玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩缤纷鸡丁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩缤纷鸡丁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩缤纷鸡丁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '鸡胸肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩缤纷鸡丁'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '腰果';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金沙鸡翅'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '红椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金沙鸡翅'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '面包糠';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金沙鸡翅'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '西红柿';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金沙鸡翅'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '鸡翅';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金沙鸡翅'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金沙鸡翅'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金沙鸡翅'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '洋葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '花菜小炒肉'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '花菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '花菜小炒肉'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '花菜小炒肉'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '彩椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '雪豆烧鸭'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '雪豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '雪豆烧鸭'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '麻鸭';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '雪豆烧鸭'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '红腰豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '口蘑酿肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '黄椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '口蘑酿肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '肉丸';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '口蘑酿肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '马蹄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '口蘑酿肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '口蘑';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '口蘑酿肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '丸子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '口蘑酿肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '猪肉丸';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '口蘑酿肉'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '猪肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒娃娃菜'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '板栗';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒娃娃菜'
  AND JSON_CONTAINS(d.schedule, '"1-1"')
  AND di.name = '娃娃菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒芥菜'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '金耳';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒芥菜'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '芥菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒油菜苔'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '彩椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒油菜苔'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '油菜苔';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒小白菜'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '小白菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒小白菜'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒小白菜'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '白菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西红柿炒蛋'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西红柿炒蛋'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西红柿炒蛋'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '鸡蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '蒜蓉凤尾'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '蒜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '蒜蓉凤尾'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '凤尾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '蒜蓉凤尾'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒黄瓜'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒黄瓜'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒黄瓜'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '黄瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '三色藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '玉米碎';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"1-2"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '黑米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"1-3"')
  AND di.name = '糙米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '红米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '燕麦米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"1-4"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '三色藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '玉米碎';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"1-5"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '黑米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"1-6"')
  AND di.name = '糙米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '红米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '燕麦米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"1-7"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '干香菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '羊肚菌';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '虫草花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '姜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '香菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '红枣';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '土鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '山药';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '羊肚';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '豆腐';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '鸡蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '白萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豆腐鲫鱼汤'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '鲫鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '番茄滑肉汤'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '番茄滑肉汤'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '里脊肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气乳鸽汤'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '莲子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气乳鸽汤'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '百合';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气乳鸽汤'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '红枣';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气乳鸽汤'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '乳鸽';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气乳鸽汤'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '山药';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '萝卜颈骨汤'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '萝卜颈骨汤'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '白萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '萝卜颈骨汤'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '颈骨';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '赤小豆陈皮猪瘦肉汤'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '赤小豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '赤小豆陈皮猪瘦肉汤'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '陈皮';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '赤小豆陈皮猪瘦肉汤'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '油焖大虾（酱油烧）'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '芝麻';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '油焖大虾（酱油烧）'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '青桔';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '油焖大虾（酱油烧）'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '法香';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '油焖大虾（酱油烧）'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '白芝麻';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '油焖大虾（酱油烧）'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '鲜虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '油焖大虾（酱油烧）'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '葱段';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '油焖大虾（酱油烧）'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '油焖大虾（酱油烧）'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '葱烧鸭'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '小葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '葱烧鸭'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄焖鸡'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄焖鸡'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '红椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄焖鸡'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '黄椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄焖鸡'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '香菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄焖鸡'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '绿椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄焖鸡'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黄焖鸡'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '干烧小黄鱼'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '干烧小黄鱼'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '黄椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '干烧小黄鱼'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '小黄鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '干烧小黄鱼'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '五花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香菇焖排骨'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '香菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香菇焖排骨'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '绿椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香菇焖排骨'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '西南花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芋儿烧兔（黄豆酱）'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '兔子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芋儿烧兔（黄豆酱）'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '小葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芋儿烧兔（黄豆酱）'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '芋头';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芋儿烧兔（黄豆酱）'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '葱段';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芋儿烧兔（黄豆酱）'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '四喜丸子（四喜墨斗丸）'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '四喜丸子（四喜墨斗丸）'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '墨斗丸';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '四喜丸子（四喜墨斗丸）'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '虾丸';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '四喜丸子（四喜墨斗丸）'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '鹌鹑蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '四喜丸子（四喜墨斗丸）'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '四喜丸子（四喜墨斗丸）'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '西南花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '四喜丸子（四喜墨斗丸）'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '白萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '四喜丸子（四喜墨斗丸）'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '山姆小番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '芝麻';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '黄瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '小番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '白芝麻';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芥菜煲'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '包心芥菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芥菜煲'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '红椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芥菜煲'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '芥菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芥菜煲'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '香菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芥菜煲'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '小葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芥菜煲'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芥菜煲'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芥菜煲'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '一品西红柿（开后一天的五花肉）'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '马蹄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '一品西红柿（开后一天的五花肉）'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '猪肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '一品西红柿（开后一天的五花肉）'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '西红柿';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '一品西红柿（开后一天的五花肉）'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '法香';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芦笋肉丝'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '红椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芦笋肉丝'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '芦笋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芦笋肉丝'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '大白菜滑虾仁加肉末'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '大白菜滑虾仁加肉末'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '大虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '大白菜滑虾仁加肉末'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '豌豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '大白菜滑虾仁加肉末'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '娃娃菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '大白菜滑虾仁加肉末'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '大白菜滑虾仁加肉末'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '大白菜滑虾仁加肉末'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '虾仁';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '大白菜滑虾仁加肉末'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '广式生炒芥蓝苗'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '芥兰';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '广式生炒芥蓝苗'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '芥兰苗';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '广式生炒芥蓝苗'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '黄椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '广式生炒芥蓝苗'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金针菇肥牛'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '彩椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金针菇肥牛'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '金针菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒油麦菜'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '油麦菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒油麦菜'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芹菜豆腐干'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芹菜豆腐干'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '豆腐';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芹菜豆腐干'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '芹菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芹菜豆腐干'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '芹菜豆腐干'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '豆腐干';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '上汤娃娃菜'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '上汤娃娃菜'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '上汤娃娃菜'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '杏鲍菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '上汤娃娃菜'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '娃娃菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒小白菜'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '小白菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒小白菜'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '金耳';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒小白菜'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '白菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '鸡汁冬瓜'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '冬瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '鸡汁冬瓜'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '彩椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '百合蒸南瓜'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '南瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '百合蒸南瓜'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '百合蒸南瓜'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '百合';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '小炒西南花'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '平菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '小炒西南花'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '西南花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '三色藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '玉米碎';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"2-1"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '黑米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"2-2"')
  AND di.name = '糙米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '红米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '燕麦米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"2-3"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '三色藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '玉米碎';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"2-4"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '黑米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"2-5"')
  AND di.name = '糙米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '红米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '燕麦米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"2-6"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '三色藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '玉米碎';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"2-7"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜吊龙汤'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜吊龙汤'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '吊龙';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜吊龙汤'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '白萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '紫菜红豆排骨汤'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '紫菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '紫菜红豆排骨汤'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '排骨';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '紫菜红豆排骨汤'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '红豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '冬瓜圆子汤'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '马蹄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '冬瓜圆子汤'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '冬瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '冬瓜圆子汤'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '丸子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '冬瓜圆子汤'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '干香菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '羊肚菌';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '虫草花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '姜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '香菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '土鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '羊肚菌煨土鸡'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '羊肚';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '赤小豆陈皮猪瘦肉汤'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '赤小豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '赤小豆陈皮猪瘦肉汤'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '陈皮';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '赤小豆陈皮猪瘦肉汤'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕颈骨汤'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '莲藕';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕颈骨汤'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '颈骨';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕颈骨汤'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '花生';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕颈骨汤'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '藕';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕颈骨汤'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '海带丝老鸭汤'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '海带';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '海带丝老鸭汤'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '老鸭';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黑椒牛肉粒'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '洋葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黑椒牛肉粒'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '牛里脊';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黑椒牛肉粒'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '彩椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '黑椒牛肉粒'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '椒盐掌中宝'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '黄椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '椒盐掌中宝'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '青笋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '椒盐掌中宝'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '掌中宝';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '土豆香菇烧鸭'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '香菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '土豆香菇烧鸭'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '鸭肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '土豆香菇烧鸭'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '豆角';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '土豆香菇烧鸭'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '土豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清真滑骨'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '排骨';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清真滑骨'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '黄花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清真滑骨'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '虫草花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清真滑骨'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清真滑骨'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '蟹味菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜牛排'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜牛排'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '牛排';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜牛排'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '白萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜牛排'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '西南花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜牛排'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '豌豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '鲜玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '兔丁';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '干烧带鱼'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '五花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '干烧带鱼'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '干烧带鱼'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '带鱼';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '干烧带鱼'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '杏鲍菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '紫薯虾球'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '芝士';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '紫薯虾球'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '紫薯虾球'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '法香';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '紫薯虾球'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '紫薯';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '紫薯虾球'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白菜酿肉'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '黄椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白菜酿肉'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '莲藕';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白菜酿肉'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '葱丝';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白菜酿肉'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '娃娃菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白菜酿肉'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '马蹄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白菜酿肉'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '丸子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白菜酿肉'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白菜酿肉'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '藕';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '焗香鸡翅'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '焗香鸡翅'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '鸡翅';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '焗香鸡翅'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '焗香鸡翅'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '鸡翅中';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '焗香鸡翅'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '小萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '焗香鸡翅'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '苦菊';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西芹肉丝'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西芹肉丝'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西芹肉丝'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '西芹';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西芹肉丝'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金玉满堂'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金玉满堂'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '豌豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金玉满堂'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金玉满堂'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金玉满堂'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '鸡蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金玉满堂'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金玉满堂'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '红腰豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金玉满堂'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '虾仁';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金玉满堂'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豉油鸡'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豉油鸡'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '樱桃萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豉油鸡'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '三黄鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豉油鸡'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豉油鸡'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '苦菊';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豉油鸡'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '生菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金汤灌羊肚菌'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '羊肚菌';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金汤灌羊肚菌'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金汤灌羊肚菌'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '马蹄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金汤灌羊肚菌'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '鲜玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金汤灌羊肚菌'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '西南花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金汤灌羊肚菌'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金汤灌羊肚菌'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '猪肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金汤灌羊肚菌'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '羊肚';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '腐竹肉末（不放盐）'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '腐竹';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '腐竹肉末（不放盐）'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '黄椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '腐竹肉末（不放盐）'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '绿椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '腐竹肉末（不放盐）'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '香菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒上海青'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '虫草花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒上海青'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '上海青';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '上汤菠菜'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '上汤菠菜'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '上汤菠菜'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '菠菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '上汤菠菜'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '杏鲍菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩滑嫩炒蛋'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '银耳';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩滑嫩炒蛋'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '绿椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩滑嫩炒蛋'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩滑嫩炒蛋'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '鸡蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩滑嫩炒蛋'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩滑嫩炒蛋'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '黑木耳';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '五彩滑嫩炒蛋'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '木耳';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒油麦菜'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '虫草花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒油麦菜'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '油麦菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '金汤西南花'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '西南花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒连白'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒连白'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '花生';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒连白'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒连白'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '莲花白';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '黑米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"3-1"')
  AND di.name = '糙米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '红米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '燕麦米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"3-2"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '红米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '燕麦米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"3-3"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '三色藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '玉米碎';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"3-4"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '黑米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"3-5"')
  AND di.name = '糙米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '红米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '燕麦米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米5'
  AND JSON_CONTAINS(d.schedule, '"3-6"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '三色藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '玉米碎';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"3-7"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '雪豆猪腱子汤'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '雪豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '雪豆猪腱子汤'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '猪腱子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '雪豆猪腱子汤'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '豆腐';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '牛肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '香菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '鸡蛋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '西湖牛肉羹'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '生菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '伊藤带丝颈骨'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '颈骨';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '椰子鸡汤'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '椰子水';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '椰子鸡汤'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '大枣';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '椰子鸡汤'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '椰子鸡汤'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '椰子鸡汤'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '鸡肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '椰子鸡汤'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '椰子鸡汤'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '椰肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '冬瓜圆子汤'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '马蹄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '冬瓜圆子汤'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '冬瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '冬瓜圆子汤'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '丸子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '冬瓜圆子汤'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '颈骨高汤'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '颈骨高汤'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '颈骨高汤'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '板栗';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '颈骨高汤'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '颈骨';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气乳鸽汤'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '莲子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气乳鸽汤'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '百合';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气乳鸽汤'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '红枣';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气乳鸽汤'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '乳鸽';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '补气乳鸽汤'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '山药';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '广东菜心滑牛柳'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '黄椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '广东菜心滑牛柳'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '牛柳';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '广东菜心滑牛柳'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '菜心';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豇豆烧鸭'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '红腰豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豇豆烧鸭'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '豇豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '豇豆烧鸭'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '老鸭';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '嫩鸡焖虾(虾需要过油）'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '基围虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '嫩鸡焖虾(虾需要过油）'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '嫩鸡焖虾(虾需要过油）'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '嫩鸡焖虾(虾需要过油）'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '大葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '嫩鸡焖虾(虾需要过油）'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '虾';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜烧牛腩'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜烧牛腩'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '白萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜烧牛腩'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '白萝卜烧牛腩'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '牛腩';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋烧肚条'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋烧肚条'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '黄椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋烧肚条'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋烧肚条'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '青笋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '青笋烧肚条'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '猪肚';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '广东手撕葱油鸡'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '葱花';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '广东手撕葱油鸡'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '广东手撕葱油鸡'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '黄瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '广东手撕葱油鸡'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '苹果猪扒'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '苹果猪扒'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '小番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '苹果猪扒'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '青桔';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '苹果猪扒'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '苹果猪扒'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '苹果猪扒'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '洋葱';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '苹果猪扒'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '苹果';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '一品西红柿'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '马蹄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '一品西红柿'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '丸子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '一品西红柿'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '西红柿';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '一品西红柿'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '法香';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '芝麻';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '黄瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '小番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '白芝麻';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '京酱肉丝'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕炒肉'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '红椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕炒肉'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '莲藕';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕炒肉'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '绿椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕炒肉'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '梅花肉';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '莲藕炒肉'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '藕';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香酥金排'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '山姆小番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香酥金排'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '芦笋';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香酥金排'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香酥金排'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '小番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香酥金排'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '豌豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香酥金排'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香酥金排'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '橙子';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '香酥金排'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '紫薯';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '宫保鸡丁'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '宫保鸡丁'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '黄瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '宫保鸡丁'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '彩椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '豌豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '鲜玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '兔丁';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '玉米嫩兔丁'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菜心捌香菇腿肉'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '红椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菜心捌香菇腿肉'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '黄椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菜心捌香菇腿肉'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '香菇';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菜心捌香菇腿肉'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '鸡';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '菜心捌香菇腿肉'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '菜心';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒水果黄瓜'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '黄瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒水果黄瓜'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '彩椒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '碗豆冬瓜粒'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '冬瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '碗豆冬瓜粒'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '豌豆';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '碗豆冬瓜粒'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '碗豆冬瓜粒'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '油麦菜炒豆皮'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '豆皮';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '油麦菜炒豆皮'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '油麦菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '油麦菜炒豆皮'
  AND JSON_CONTAINS(d.schedule, '"4-3"')
  AND di.name = '枸杞';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒小白菜'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '小白菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒小白菜'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '金耳';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清炒小白菜'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '白菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清烧莲白'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '清烧莲白'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '番茄花菜'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '番茄';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '番茄花菜'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '花菜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '胡萝卜葫芦丝'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '胡萝卜葫芦丝'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '胡萝卜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '胡萝卜葫芦丝'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '三月瓜';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '红米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '燕麦米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米2'
  AND JSON_CONTAINS(d.schedule, '"4-1"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '三色藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '玉米碎';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"4-2"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '红米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '燕麦米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米1'
  AND JSON_CONTAINS(d.schedule, '"4-4"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '红米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '燕麦米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米3'
  AND JSON_CONTAINS(d.schedule, '"4-5"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '三色藜麦';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '玉米粒';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '玉米碎';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米'
  AND JSON_CONTAINS(d.schedule, '"4-6"')
  AND di.name = '玉米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '黑米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '大米';
INSERT INTO dish_ingredient_relation (dish_id, ingredient_id, create_time)
SELECT d.id, di.id, NOW()
FROM dish d, dish_ingredient di
WHERE d.name = '山姆有机杂粮米6'
  AND JSON_CONTAINS(d.schedule, '"4-7"')
  AND di.name = '糙米';
