#!/usr/bin/env node
/**
 * KnowFlow · AI 编程智能体自动格式化器（真实执行机制，非 LLM 改写）
 *
 * 设计约束（来自任务书）：
 *  - 只格式化「本次 AI 生成/修改的文件清单」，绝不扫描全量未提交改动 → 保护用户已有修改。
 *  - 按扩展名分流：前端(Vue/TS/JS/JSON/CSS/MD) → Prettier；Java → google-java-format。
 *  - 格式化器本身即语法校验：解析失败则保留原文件并记录错误，绝不写入损坏内容。
 *  - 不改变业务逻辑，不通过 LLM 再次改写代码。
 *
 * 用法：
 *  node scripts/format-changed.mjs --manifest path/to/files.json
 *  node scripts/format-changed.mjs --files a.ts b.vue c.java
 *  node scripts/format-changed.mjs --manifest m.json --dry-run      # 只报告，不动盘
 *  node scripts/format-changed.mjs --manifest m.json --report out.json
 *
 * manifest JSON 格式：{ "files": ["relative/or/absolute paths"] }
 */
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const prettierBin = path.join(root, 'frontend', 'node_modules', '.bin', 'prettier')
const prettierConfig = path.join(root, 'frontend', '.prettierrc.json')
const prettierIgnore = path.join(root, 'frontend', '.prettierignore')
const gjfJar = path.join(root, 'backend', 'tools', 'google-java-format.jar')

const PRETTIER_EXTS = new Set(['.ts', '.tsx', '.vue', '.js', '.mjs', '.cjs', '.json', '.css', '.scss', '.less', '.md'])
const JAVA_EXTS = new Set(['.java'])
const SKIP_EXTS = new Set(['.png', '.jpg', '.jpeg', '.gif', '.svg', '.ico', '.woff', '.woff2', '.ttf', '.eot', '.map', '.lock', '.bin', '.jar', '.class', '.pdf', '.zip'])

function sha1(file) {
  try {
    return createHash('sha1').update(readFileSync(file)).digest('hex')
  } catch {
    return null
  }
}

function run(cmd, args, cwd = root) {
  return spawnSync(cmd, args, { cwd, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
}

function resolveFiles() {
  const argv = process.argv.slice(2)
  const mi = argv.indexOf('--manifest')
  const fi = argv.indexOf('--files')
  const files = []
  if (mi >= 0 && argv[mi + 1]) {
    const raw = JSON.parse(readFileSync(path.resolve(argv[mi + 1]), 'utf8'))
    if (Array.isArray(raw.files)) files.push(...raw.files)
    else if (Array.isArray(raw)) files.push(...raw)
  }
  if (fi >= 0) {
    for (let i = fi + 1; i < argv.length && !argv[i].startsWith('--'); i++) files.push(argv[i])
  }
  return files.map((f) => path.resolve(root, f))
}

function formatWithPrettier(file) {
  // 先 --check 判断是否已格式化（幂等检测），再按需 --write
  const check = run(prettierBin, ['--check', '--config', prettierConfig, '--ignore-path', prettierIgnore, file])
  if (check.status !== 0) {
    // 可能是「需要格式化」也可能是「解析失败/无法解析」
    if (/No parser could be inferred|Couldn't resolve parser|Unknown language/i.test(check.stderr + check.stdout)) {
      return { ok: false, error: 'Prettier 无法解析该文件（语法或语言不支持）：' + (check.stderr || check.stdout).trim().split('\n')[0] }
    }
    // 视为「需要格式化」，执行写入
    const write = run(prettierBin, ['--write', '--config', prettierConfig, '--ignore-path', prettierIgnore, file])
    if (write.status !== 0) {
      return { ok: false, error: 'Prettier 写入失败：' + (write.stderr || write.stdout).trim().split('\n')[0] }
    }
    return { ok: true }
  }
  return { ok: true, unchanged: true }
}

function formatWithGjf(file) {
  // 直接 --replace：已格式化时内容不变（幂等），语法错误时退出非 0 由调用方捕获
  const r = run('java', ['-jar', gjfJar, '--replace', file])
  if (r.status !== 0) {
    return { ok: false, error: 'google-java-format 失败（多为语法错误）：' + (r.stderr || r.stdout).trim().split('\n').slice(-3).join(' | ') }
  }
  return { ok: true }
}

function classify(file) {
  const ext = path.extname(file).toLowerCase()
  if (JAVA_EXTS.has(ext)) return 'java'
  if (PRETTIER_EXTS.has(ext)) return 'prettier'
  if (SKIP_EXTS.has(ext)) return 'skip-binary'
  return 'skip-unsupported'
}

function main() {
  const argv = process.argv.slice(2)
  const dryRun = argv.includes('--dry-run')
  const reportIdx = argv.indexOf('--report')
  const reportPath = reportIdx >= 0 ? argv[reportIdx + 1] : null
  const log = []
  const ts = new Date().toISOString()

  const files = resolveFiles()
  if (!files.length) {
    console.error('用法: node scripts/format-changed.mjs --manifest <files.json> | --files <a> <b> ...')
    console.error('错误: 未提供任何文件清单。为保护未提交改动，本工具不会扫描 git 全量改动，必须由调用方显式传入本次修改的文件。')
    process.exitCode = 2
    return
  }

  const result = {
    tool: 'knowflow-format-changed',
    timestamp: ts,
    dryRun,
    repoRoot: root,
    total: files.length,
    formatted: 0,
    unchanged: 0,
    skipped: 0,
    failed: 0,
    files: [],
  }

  for (const file of files) {
    const rel = path.relative(root, file)
    const entry = { file: rel, type: null, status: null, changed: false, error: null }
    if (!existsSync(file) || !statSync(file).isFile()) {
      entry.status = 'missing'
      result.failed += 1
      log.push(`✗ 缺失 ${rel}`)
      result.files.push(entry)
      continue
    }
    if (!file.startsWith(root)) {
      entry.status = 'out-of-repo'
      result.failed += 1
      log.push(`✗ 越界(非本仓库) ${rel}`)
      result.files.push(entry)
      continue
    }
    const kind = classify(file)
    entry.type = kind
    if (kind === 'skip-binary' || kind === 'skip-unsupported') {
      entry.status = 'skipped'
      result.skipped += 1
      log.push(`· 跳过(${kind}) ${rel}`)
      result.files.push(entry)
      continue
    }

    const before = sha1(file)
    let fr
    if (dryRun) {
      // dry-run：只读预检，不动盘
      if (kind === 'prettier') {
        const c = run(prettierBin, ['--check', '--config', prettierConfig, '--ignore-path', prettierIgnore, file])
        if (c.status === 0) fr = { ok: true, unchanged: true }
        else if (/No parser|Couldn't resolve|Unknown language/i.test(c.stderr + c.stdout))
          fr = { ok: false, error: 'Prettier 无法解析：' + (c.stderr || c.stdout).trim().split('\n')[0] }
        else fr = { ok: true, unchanged: false }
      } else {
        // 注意：gjf --dry-run 退出码始终为 0，需以 stdout 是否打印文件名判定是否需要格式化
        const c = run('java', ['-jar', gjfJar, '--dry-run', file])
        const needs = (c.stdout || '').trim().length > 0 || c.status !== 0
        fr = { ok: true, unchanged: !needs }
      }
    } else if (kind === 'prettier') {
      fr = formatWithPrettier(file)
    } else {
      fr = formatWithGjf(file)
    }

    if (!fr.ok) {
      entry.status = 'failed'
      entry.error = fr.error
      result.failed += 1
      log.push(`✗ 失败 ${rel} — ${fr.error}`)
      result.files.push(entry)
      continue
    }

    const after = dryRun ? before : sha1(file)
    const changed = !dryRun && before !== after
    if (fr.unchanged && !changed) {
      entry.status = 'unchanged'
      result.unchanged += 1
      log.push(`· 已合规 ${rel}`)
    } else if (changed) {
      entry.status = 'formatted'
      result.formatted += 1
      log.push(`✓ 已格式化 ${rel}`)
    } else {
      // dry-run 将改动，或真实运行无变化（已合规）
      if (dryRun) {
        entry.status = 'would-format'
        log.push(`[dry] 将格式化 ${rel}`)
      } else {
        entry.status = 'unchanged'
        result.unchanged += 1
        log.push(`· 已合规 ${rel}`)
      }
    }
    result.files.push(entry)
  }

  const summary = `总计 ${result.total} · 已格式化 ${result.formatted} · 已合规 ${result.unchanged} · 跳过 ${result.skipped} · 失败 ${result.failed}`
  log.push('')
  log.push(summary)
  console.log(log.join('\n'))

  if (reportPath) {
    writeFileSync(path.resolve(root, reportPath), JSON.stringify(result, null, 2))
    console.log(`\n报告已写入 ${reportPath}`)
  }

  // 有失败则非零退出，便于接入 CI/verify 流程
  process.exitCode = result.failed > 0 ? 1 : 0
}

main()
