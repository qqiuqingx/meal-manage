#!/usr/bin/env python3
"""
数据库迁移脚本：添加切配信息字段
"""

import pymysql
import yaml
import os
import re

# 读取数据库配置
config_path = os.path.expanduser('~/.claude/mcp-database-config.yml')
with open(config_path, 'r', encoding='utf-8') as f:
    config_data = yaml.safe_load(f)

# 从配置中找到 eladmin 数据库配置
db_config = None
for ds_config in config_data['datasources']['configs']:
    if ds_config['name'] == 'mysql-eladmin':
        db_config = ds_config
        break

if not db_config:
    raise Exception("未找到 mysql-eladmin 配置")

# 解析 JDBC URL，从本地私有配置读取主机、端口和库名
url = db_config['url']
match = re.search(r'jdbc:mysql://([^:]+):(\d+)/([^?]+)', url)
if not match:
    raise Exception(f"无法解析 JDBC URL: {url}")

host = match.group(1)
port = int(match.group(2))
database = match.group(3)
username = db_config['username']
password = db_config['password']

print(f"连接数据库: {host}:{port}/{database}")

# 连接数据库
conn = pymysql.connect(
    host=host,
    port=port,
    user=username,
    password=password,
    database=database
)

try:
    with conn.cursor() as cursor:
        # 检查字段是否已存在
        cursor.execute("""
            SELECT COLUMN_NAME
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = %s
              AND TABLE_NAME = 'dish'
              AND COLUMN_NAME = 'cutting_info'
        """, (database,))
        result = cursor.fetchone()

        if result:
            print("✓ cutting_info 字段已存在，跳过创建")
        else:
            # 执行 ALTER TABLE 添加字段
            cursor.execute("""
                ALTER TABLE dish
                ADD COLUMN cutting_info TEXT COMMENT '切配信息' AFTER ingredients
            """)
            conn.commit()
            print("✓ 成功添加 cutting_info 字段到 dish 表")

finally:
    conn.close()
    print("数据库连接已关闭")
