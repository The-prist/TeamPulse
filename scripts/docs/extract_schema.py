#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从运行中的 MySQL 容器导出完整库结构到 JSON，作为设计文档的唯一事实源。

用 MySQL 的 JSON_ARRAYAGG 在服务端拼 JSON，避免注释里的制表符/换行破坏解析。

用法：
    python scripts/docs/extract_schema.py --password <root密码> --out .run-logs/schema.json
"""
import argparse
import json
import subprocess
import sys

DEFAULT_CONTAINER = "tp-mysql"
DEFAULT_DB = "teampulse"


def run_mysql(container: str, password: str, db: str, sql: str) -> list:
    """执行 SQL 并解析返回的 JSON 数组。"""
    proc = subprocess.run(
        ["docker", "exec", container, "mysql", "-uroot", f"-p{password}", "-N", "-B",
         "--default-character-set=utf8mb4", db, "-e", sql],
        capture_output=True,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"mysql 失败: {proc.stderr.decode('utf-8', 'replace')}")
    text = proc.stdout.decode("utf-8", "replace").strip()
    if not text or text == "NULL":
        return []
    return json.loads(text)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--container", default=DEFAULT_CONTAINER)
    ap.add_argument("--db", default=DEFAULT_DB)
    ap.add_argument("--password", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    info = "information_schema"

    tables = run_mysql(args.container, args.password, info, f"""
        SELECT JSON_ARRAYAGG(JSON_OBJECT(
                 'name', TABLE_NAME,
                 'comment', IFNULL(TABLE_COMMENT, ''),
                 'rows', IFNULL(TABLE_ROWS, 0)))
        FROM TABLES
        WHERE TABLE_SCHEMA = '{args.db}' AND TABLE_TYPE = 'BASE TABLE'
    """)

    columns = run_mysql(args.container, args.password, info, f"""
        SELECT JSON_ARRAYAGG(JSON_OBJECT(
                 'table', TABLE_NAME, 'name', COLUMN_NAME, 'type', COLUMN_TYPE,
                 'nullable', IS_NULLABLE = 'YES', 'default', COLUMN_DEFAULT,
                 'key', COLUMN_KEY, 'extra', IFNULL(EXTRA, ''),
                 'comment', IFNULL(COLUMN_COMMENT, ''), 'pos', ORDINAL_POSITION))
        FROM COLUMNS WHERE TABLE_SCHEMA = '{args.db}'
    """)

    indexes = run_mysql(args.container, args.password, info, f"""
        SELECT JSON_ARRAYAGG(JSON_OBJECT(
                 'table', TABLE_NAME, 'name', INDEX_NAME,
                 'columns', cols, 'unique', nonuniq = 0))
        FROM (
          SELECT TABLE_NAME, INDEX_NAME,
                 GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols,
                 MAX(NON_UNIQUE) AS nonuniq
          FROM STATISTICS WHERE TABLE_SCHEMA = '{args.db}'
          GROUP BY TABLE_NAME, INDEX_NAME
        ) t
    """)

    fks = run_mysql(args.container, args.password, info, f"""
        SELECT JSON_ARRAYAGG(JSON_OBJECT(
                 'table', TABLE_NAME, 'column', COLUMN_NAME,
                 'ref_table', REFERENCED_TABLE_NAME, 'ref_column', REFERENCED_COLUMN_NAME,
                 'constraint', CONSTRAINT_NAME))
        FROM KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = '{args.db}' AND REFERENCED_TABLE_NAME IS NOT NULL
    """)

    migrations = run_mysql(args.container, args.password, args.db, """
        SELECT JSON_ARRAYAGG(JSON_OBJECT(
                 'rank', installed_rank, 'version', version, 'description', description,
                 'script', script, 'success', success = 1))
        FROM flyway_schema_history
    """)

    by_table_cols: dict[str, list] = {}
    for c in columns or []:
        by_table_cols.setdefault(c["table"], []).append(c)
    by_table_idx: dict[str, list] = {}
    for i in indexes or []:
        by_table_idx.setdefault(i["table"], []).append(i)

    for t in tables or []:
        t["columns"] = sorted(by_table_cols.get(t["name"], []), key=lambda x: x["pos"])
        t["indexes"] = sorted(by_table_idx.get(t["name"], []), key=lambda x: x["name"])
    (tables or []).sort(key=lambda x: x["name"])

    data = {
        "database": args.db,
        "tables": tables or [],
        "foreign_keys": fks or [],
        "migrations": sorted(migrations or [], key=lambda m: m["rank"]),
    }
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=2)

    total_cols = sum(len(t["columns"]) for t in data["tables"])
    print(f"已导出 {len(data['tables'])} 张表 / {total_cols} 个字段 / "
          f"{len(data['foreign_keys'])} 个外键 / {len(data['migrations'])} 条迁移 → {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
