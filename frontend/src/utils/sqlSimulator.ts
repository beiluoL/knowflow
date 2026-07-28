// 前端 in-browser SQL 模拟执行器（轻量级，用于代码练习的 SQL 题目）。
// 支持基础 SELECT 语句、SHOW TABLES、DESCRIBE，并提供内置示例数据库。
// 不依赖任何第三方库，仅用于教学/练习场景，不能用于生产。
//
// 设计目标：
// 1. 让 SQL 题目可以在浏览器中真正"运行"并产生输出；
// 2. 输出格式与 MySQL 命令行一致（制表符分隔列、用 | 分隔）；
// 3. 支持基础过滤、排序、限制、聚合，覆盖 80% 练习场景。

/** 内置示例数据库：3 张表，便于用户编写 SELECT 练习。 */
interface Row { [column: string]: string | number | null }
interface Table { columns: string[]; rows: Row[] }

const SAMPLE_DB: Record<string, Table> = {
  users: {
    columns: ['id', 'name', 'age', 'email', 'city'],
    rows: [
      { id: 1, name: 'Alice', age: 28, email: 'alice@example.com', city: 'Shenzhen' },
      { id: 2, name: 'Bob', age: 34, email: 'bob@example.com', city: 'Beijing' },
      { id: 3, name: 'Charlie', age: 22, email: 'charlie@example.com', city: 'Shanghai' },
      { id: 4, name: 'Diana', age: 41, email: 'diana@example.com', city: 'Shenzhen' },
      { id: 5, name: 'Eve', age: 30, email: 'eve@example.com', city: 'Guangzhou' },
    ],
  },
  orders: {
    columns: ['id', 'user_id', 'product', 'amount', 'status', 'created_at'],
    rows: [
      { id: 101, user_id: 1, product: 'Laptop', amount: 8999, status: 'paid', created_at: '2024-01-15' },
      { id: 102, user_id: 2, product: 'Phone', amount: 4999, status: 'paid', created_at: '2024-01-16' },
      { id: 103, user_id: 1, product: 'Mouse', amount: 99, status: 'pending', created_at: '2024-01-17' },
      { id: 104, user_id: 3, product: 'Keyboard', amount: 299, status: 'paid', created_at: '2024-01-18' },
      { id: 105, user_id: 4, product: 'Monitor', amount: 2199, status: 'cancelled', created_at: '2024-01-19' },
      { id: 106, user_id: 2, product: 'Headset', amount: 599, status: 'paid', created_at: '2024-01-20' },
    ],
  },
  products: {
    columns: ['id', 'name', 'category', 'price', 'stock'],
    rows: [
      { id: 1, name: 'Laptop', category: 'electronics', price: 8999, stock: 15 },
      { id: 2, name: 'Phone', category: 'electronics', price: 4999, stock: 30 },
      { id: 3, name: 'Mouse', category: 'accessories', price: 99, stock: 100 },
      { id: 4, name: 'Keyboard', category: 'accessories', price: 299, stock: 50 },
      { id: 5, name: 'Monitor', category: 'electronics', price: 2199, stock: 20 },
    ],
  },
}

export interface SqlRunResult {
  output: string
  error: string | null
  /** 影响行数（SELECT 为返回行数） */
  rows?: number
}

/** 模糊匹配表名（不区分大小写）。 */
const findTable = (name: string): Table | null => {
  const key = Object.keys(SAMPLE_DB).find((k) => k.toLowerCase() === name.toLowerCase())
  return key ? SAMPLE_DB[key] : null
}

/** 将值格式化为字符串（NULL 显示为 NULL）。 */
const fmt = (v: string | number | null): string => (v === null ? 'NULL' : String(v))

/**
 * 格式化结果集为类 MySQL 命令行输出。
 * 例如：
 *   id | name
 *   ---|-----
 *   1  | Alice
 */
const formatRows = (columns: string[], rows: Row[]): string => {
  if (rows.length === 0) return 'Empty set'
  const headers = columns.join(' | ')
  const sep = columns.map(() => '---').join(' | ')
  const body = rows
    .map((r) => columns.map((c) => fmt(r[c] ?? null)).join(' | '))
    .join('\n')
  return `${headers}\n${sep}\n${body}\n\n${rows.length} row${rows.length === 1 ? '' : 's'} in set`
}

/** 简单 WHERE 条件求值：支持 col op value 形式，op ∈ =,!=,<,<=,>,>=,LIKE。 */
const evalCondition = (row: Row, condition: string): boolean => {
  // 简化处理：仅支持单一条件
  const m = condition.trim().match(/^([\w.]+)\s*(=|!=|<=|>=|<|>|LIKE)\s*(.+)$/i)
  if (!m) return true
  const [, col, op, rawVal] = m
  let val: string | number = rawVal.trim()
  // 去引号
  if (/^'.*'$/.test(val) || /^".*"$/.test(val)) val = val.slice(1, -1)
  // 数字转换
  if (/^-?\d+(\.\d+)?$/.test(val)) val = Number(val)

  const cell = row[col] ?? null
  switch (op.toUpperCase()) {
    case '=':
      return cell == val // eslint-disable-line eqeqeq
    case '!=':
      return cell != val // eslint-disable-line eqeqeq
    case '<':
      return (cell as number) < (val as number)
    case '<=':
      return (cell as number) <= (val as number)
    case '>':
      return (cell as number) > (val as number)
    case '>=':
      return (cell as number) >= (val as number)
    case 'LIKE': {
      const pattern = String(val).replace(/%/g, '.*').replace(/_/g, '.')
      return new RegExp(`^${pattern}$`, 'i').test(String(cell ?? ''))
    }
    default:
      return true
  }
}

/**
 * 解析并执行 SQL 语句。
 * 支持：
 * - SHOW TABLES
 * - DESCRIBE <table> / DESC <table>
 * - SELECT [DISTINCT] <cols> FROM <table> [WHERE ...] [ORDER BY ...] [LIMIT n]
 * - SELECT COUNT(*) FROM <table> ...
 */
export const runSql = (sql: string): SqlRunResult => {
  const code = sql.trim().replace(/;+\s*$/, '')
  if (!code) return { output: '', error: '空 SQL 语句' }

  try {
    const upper = code.toUpperCase()

    // SHOW TABLES
    if (upper === 'SHOW TABLES' || upper === 'SHOW TABLES;') {
      const cols = ['Tables_in_sample_db']
      const rows: Row[] = Object.keys(SAMPLE_DB).map((t) => ({ Tables_in_sample_db: t }))
      return { output: formatRows(cols, rows), error: null, rows: rows.length }
    }

    // DESCRIBE table
    const descMatch = code.match(/^DESCRIBE\s+(\w+)$/i) || code.match(/^DESC\s+(\w+)$/i)
    if (descMatch) {
      const t = findTable(descMatch[1])
      if (!t) return { output: '', error: `表 ${descMatch[1]} 不存在` }
      const cols = ['Field', 'Type']
      const rows: Row[] = t.columns.map((c) => ({
        Field: c,
        Type: typeof t.rows[0]?.[c] === 'number' ? 'int' : 'varchar',
      }))
      return { output: formatRows(cols, rows), error: null, rows: rows.length }
    }

    // SELECT
    if (upper.startsWith('SELECT')) {
      // 提取各子句
      const selectMatch = code.match(
        /^SELECT\s+(DISTINCT\s+)?(.+?)\s+FROM\s+(\w+)(?:\s+WHERE\s+(.+?))?(?:\s+ORDER\s+BY\s+(.+?))?(?:\s+LIMIT\s+(\d+))?$/i
      )
      if (!selectMatch) return { output: '', error: 'SQL 语法解析失败：仅支持 SELECT ... FROM ... [WHERE] [ORDER BY] [LIMIT]' }
      const [, distinctRaw, colsRaw, tableName, whereRaw, orderRaw, limitRaw] = selectMatch
      const table = findTable(tableName)
      if (!table) return { output: '', error: `表 ${tableName} 不存在，可用表：${Object.keys(SAMPLE_DB).join(', ')}` }

      let rows = [...table.rows]
      // WHERE
      if (whereRaw) {
        // 简化：拆分 AND
        const conds = whereRaw.split(/\s+AND\s+/i)
        rows = rows.filter((r) => conds.every((c) => evalCondition(r, c)))
      }
      // ORDER BY
      if (orderRaw) {
        const orderParts = orderRaw.trim().split(/\s+/)
        const orderCol = orderParts[0]
        const direction = (orderParts[1] || 'ASC').toUpperCase()
        rows.sort((a, b) => {
          const va = a[orderCol] ?? 0
          const vb = b[orderCol] ?? 0
          if (typeof va === 'number' && typeof vb === 'number') {
            return direction === 'DESC' ? vb - va : va - vb
          }
          return direction === 'DESC'
            ? String(vb).localeCompare(String(va))
            : String(va).localeCompare(String(vb))
        })
      }
      // LIMIT
      if (limitRaw) {
        rows = rows.slice(0, Number(limitRaw))
      }

      // 列选择
      const colsTrim = colsRaw.trim()
      // COUNT(*)
      const countMatch = colsTrim.match(/^COUNT\s*\(\s*\*\s*\)$/i)
      if (countMatch) {
        const out = `COUNT(*)\n---\n${rows.length}\n\n1 row in set`
        return { output: out, error: null, rows: 1 }
      }
      // COUNT(col)
      const countColMatch = colsTrim.match(/^COUNT\s*\(\s*([\w.]+)\s*\)$/i)
      if (countColMatch) {
        const cnt = rows.filter((r) => r[countColMatch[1]] != null).length
        const out = `COUNT(${countColMatch[1]})\n---\n${cnt}\n\n1 row in set`
        return { output: out, error: null, rows: 1 }
      }
      // SUM/AVG/MAX/MIN
      const aggMatch = colsTrim.match(/^(SUM|AVG|MAX|MIN)\s*\(\s*([\w.]+)\s*\)$/i)
      if (aggMatch) {
        const [, fn, col] = aggMatch
        const vals = rows.map((r) => Number(r[col] ?? 0)).filter((n) => !isNaN(n))
        let result = 0
        if (fn.toUpperCase() === 'SUM') result = vals.reduce((s, n) => s + n, 0)
        else if (fn.toUpperCase() === 'AVG') result = vals.length ? vals.reduce((s, n) => s + n, 0) / vals.length : 0
        else if (fn.toUpperCase() === 'MAX') result = vals.length ? Math.max(...vals) : 0
        else if (fn.toUpperCase() === 'MIN') result = vals.length ? Math.min(...vals) : 0
        const out = `${fn.toUpperCase()}(${col})\n---\n${Math.round(result * 100) / 100}\n\n1 row in set`
        return { output: out, error: null, rows: 1 }
      }

      // SELECT *
      let selectedCols: string[] = []
      if (colsTrim === '*') {
        selectedCols = [...table.columns]
      } else {
        selectedCols = colsTrim.split(',').map((c) => c.trim())
        // 校验列存在
        for (const c of selectedCols) {
          if (!table.columns.includes(c)) {
            return { output: '', error: `列 ${c} 不存在于表 ${tableName}，可用列：${table.columns.join(', ')}` }
          }
        }
      }

      // DISTINCT
      if (distinctRaw) {
        const seen = new Set<string>()
        rows = rows.filter((r) => {
          const key = selectedCols.map((c) => fmt(r[c] ?? null)).join('|')
          if (seen.has(key)) return false
          seen.add(key)
          return true
        })
      }

      const projectedRows: Row[] = rows.map((r) => {
        const out: Row = {}
        for (const c of selectedCols) out[c] = r[c] ?? null
        return out
      })

      return { output: formatRows(selectedCols, projectedRows), error: null, rows: projectedRows.length }
    }

    return { output: '', error: '不支持的 SQL 语句：仅支持 SELECT / SHOW TABLES / DESCRIBE' }
  } catch (e) {
    return { output: '', error: e instanceof Error ? e.message : String(e) }
  }
}

/** 返回所有可用表名，供编辑器提示使用。 */
export const listSampleTables = (): string[] => Object.keys(SAMPLE_DB)
