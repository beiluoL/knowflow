#!/usr/bin/env python3
"""一次性脚本：把 H2 版 schema.sql 转换为 MySQL 8 兼容版。

处理 4 类 H2 专有语法 + 建表选项/长文本类型：
1. CREATE [UNIQUE] INDEX IF NOT EXISTS  -> MySQL 8 不支持 IF NOT EXISTS，去掉并做重复名去重
2. ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS -> 转为 CREATE UNIQUE INDEX
3. ALTER TABLE ... ADD COLUMN IF NOT EXISTS     -> 合并进对应 CREATE TABLE（MySQL 不支持该语法）
4. CREATE TABLE 结尾补 ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
5. TEXT/MEDIUMTEXT -> LONGTEXT（存万字正文，避免 64KB 截断）
"""
import re
import sys
from pathlib import Path

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
text = src.read_text(encoding="utf-8")

# ---- 1. 收集 ALTER TABLE ADD COLUMN IF NOT EXISTS，稍后合并进建表语句 ----
add_col_re = re.compile(
    r"^ALTER\s+TABLE\s+(\w+)\s+ADD\s+COLUMN\s+IF\s+NOT\s+EXISTS\s+(.+?);\s*$",
    re.IGNORECASE | re.MULTILINE,
)
pending_cols: dict[str, list[str]] = {}
for m in add_col_re.finditer(text):
    pending_cols.setdefault(m.group(1).lower(), []).append(m.group(2).strip())
text = add_col_re.sub("", text)

# ---- 2. ADD CONSTRAINT IF NOT EXISTS xxx UNIQUE (cols) -> CREATE UNIQUE INDEX ----
def constraint_to_index(m: re.Match) -> str:
    table, name, cols = m.group(1), m.group(2), m.group(3)
    return f"CREATE UNIQUE INDEX {name} ON {table} ({cols});"

text = re.sub(
    r"^ALTER\s+TABLE\s+(\w+)\s+ADD\s+CONSTRAINT\s+IF\s+NOT\s+EXISTS\s+(\w+)\s+UNIQUE\s*\(([^)]+)\)\s*;",
    constraint_to_index,
    text,
    flags=re.IGNORECASE | re.MULTILINE,
)

# ---- 3. 去掉索引语句里的 IF NOT EXISTS ----
text = re.sub(
    r"(CREATE\s+(?:UNIQUE\s+)?INDEX\s+)IF\s+NOT\s+EXISTS\s+",
    r"\1",
    text,
    flags=re.IGNORECASE,
)

# ---- 4. 把 pending 列合并进 CREATE TABLE ----
def inject_columns(m: re.Match) -> str:
    head, table, body, tail = m.group(1), m.group(2), m.group(3), m.group(4)
    cols = pending_cols.pop(table.lower(), [])
    if cols:
        body = body.rstrip()
        if not body.endswith(","):
            body += ","
        body += "\n    " + ",\n    ".join(cols) + "\n"
    return f"{head}{table} ({body}{tail}"

text = re.sub(
    r"(CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+)(\w+)\s*\((.*?)(\);)",
    inject_columns,
    text,
    flags=re.IGNORECASE | re.DOTALL,
)

# ---- 5. 建表补 ENGINE / CHARSET ----
text = re.sub(
    r"\)\s*;(\s*(?:--[^\n]*)?\s*\n)",
    lambda m: ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;" + m.group(1),
    text,
)
# 上一步会误伤索引语句，这里只对 CREATE TABLE 块生效：先全量还原再精确处理
text = text.replace(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;", ");")

def add_engine(m: re.Match) -> str:
    return m.group(0)[:-1] + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"

text = re.sub(
    r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+\w+\s*\(.*?\n\);",
    add_engine,
    text,
    flags=re.IGNORECASE | re.DOTALL,
)

# ---- 6. 长文本类型提升 ----
text = re.sub(r"\bMEDIUMTEXT\b", "LONGTEXT", text, flags=re.IGNORECASE)
text = re.sub(r"(\s)TEXT(\s|,|\n)", r"\1LONGTEXT\2", text)

# ---- 7. 索引重名去重（MySQL 同表内索引名唯一，且无 IF NOT EXISTS 兜底）----
seen: set[tuple[str, str]] = set()
out_lines: list[str] = []
idx_re = re.compile(
    r"^CREATE\s+(UNIQUE\s+)?INDEX\s+(\w+)\s+ON\s+(\w+)\s*\(", re.IGNORECASE
)
for line in text.splitlines():
    m = idx_re.match(line.strip())
    if m:
        key = (m.group(2).lower(), m.group(3).lower())
        if key in seen:
            continue
        seen.add(key)
    out_lines.append(line)
text = "\n".join(out_lines)

# 压缩多余空行
text = re.sub(r"\n{3,}", "\n\n", text)

header = (
    "-- ============================================================\n"
    "-- MySQL 8.x 方言建表脚本（由 db/h2/schema.sql 转换生成）\n"
    "-- 与 H2 版差异：去除 H2 专有 IF NOT EXISTS 索引/约束语法、\n"
    "-- 建表补 InnoDB + utf8mb4、TEXT 提升为 LONGTEXT。\n"
    "-- 注意：索引采用先建后判策略，重复执行前请确保库为空或使用迁移工具。\n"
    "-- ============================================================\n\n"
)
dst.parent.mkdir(parents=True, exist_ok=True)
dst.write_text(header + text.lstrip(), encoding="utf-8")

remaining = [k for k in pending_cols]
print(f"已生成: {dst}")
print(f"未匹配到建表语句的 ADD COLUMN: {remaining if remaining else '无'}")
print(f"索引总数: {len(seen)}")
