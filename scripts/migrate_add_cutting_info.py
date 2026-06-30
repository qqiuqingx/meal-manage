#!/usr/bin/env python3
"""
数据库迁移脚本：添加切配信息字段
"""

import argparse
import os

import pymysql


def parse_args():
    parser = argparse.ArgumentParser(description="添加 dish.cutting_info 切配信息字段。")
    parser.add_argument("--host", default=os.getenv("DB_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("DB_PORT", "3306")))
    parser.add_argument("--user", default=os.getenv("DB_USER", "root"))
    parser.add_argument("--password", default=os.getenv("DB_PWD", os.getenv("DB_PASSWORD", "")))
    parser.add_argument("--database", default=os.getenv("DB_NAME", "eladmin"))
    return parser.parse_args()


def connect(args):
    return pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
        autocommit=False,
    )


def main():
    args = parse_args()
    print(f"连接数据库: {args.host}:{args.port}/{args.database}")

    conn = connect(args)
    try:
        with conn.cursor() as cursor:
            # 检查字段是否已存在，保证脚本重复执行时幂等。
            cursor.execute("""
                SELECT COLUMN_NAME
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = %s
                  AND TABLE_NAME = 'dish'
                  AND COLUMN_NAME = 'cutting_info'
            """, (args.database,))
            result = cursor.fetchone()

            if result:
                print("cutting_info 字段已存在，跳过创建")
            else:
                cursor.execute("""
                    ALTER TABLE dish
                    ADD COLUMN cutting_info TEXT COMMENT '切配信息' AFTER ingredients
                """)
                conn.commit()
                print("成功添加 cutting_info 字段到 dish 表")

    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
        print("数据库连接已关闭")


if __name__ == "__main__":
    main()
