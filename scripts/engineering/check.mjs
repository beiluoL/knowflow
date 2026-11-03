#!/usr/bin/env node
/**
 * KnowFlow 统一工程检查（零依赖，Node 18+）。
 * 用法：node scripts/engineering/check.mjs [--json]
 *
 * 零容忍项：出现即失败。
 * 棘轮项：与 scripts/engineering/baseline.json 比较，只能持平或下降。
 */
import { readdir, readFile, stat } from 'node:fs/promises'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fingerprint, additions } from './snapshot.mjs'
import { fileURLToPath } from 'node:url'

const rootArg = process.argv.indexOf('--root')
const root = rootArg >= 0 ? path.resolve(process.argv[rootArg + 1] || '.') : path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const asJson = process.argv.includes('--json')
const SKIP_DIRS = /^(node_modules|target|build|dist|\.git|\.idea)$/
const SKIP_FILES = /^\.DS_Store$/

const failures = []
const warnings = []
const results = []

function record(name, ok, detail, kind) {
  results.push({ name, ok, detail, kind })
  if (!ok) failures.push(`${name}: ${detail}`)
}

async function walk(dir, out = []) {
  let entries
  try {
    entries = await readdir(dir, { withFileTypes: true })
  } catch (error) {
    if (error.code === 'ENOENT') return out
    throw error
  }
  for (const entry of entries) {
    if (SKIP_FILES.test(entry.name)) continue
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      if (SKIP_DIRS.test(entry.name)) continue
      await walk(full, out)
    } else {
      out.push(full)
    }
  }
  return out
}

function countMatches(files, regex) {
  let total = 0
  for (const file of files) {
    const text = readFileSync(file, 'utf8')
    const found = text.match(new RegExp(regex.source, regex.flags))
    if (found) total += found.length
  }
  return total
}

// --- 1. Agent Skill 三处副本一致 -------------------------------------------
async function checkSkillSync() {
  const canonical = path.join(root, '.agents/skills')
  const mirrors = [path.join(root, '.trae/skills'), path.join(root, '.workbuddy/skills')]
  let skillDirs
  try {
    skillDirs = (await readdir(canonical, { withFileTypes: true }))
      .filter((e) => e.isDirectory())
      .map((e) => e.name)
  } catch {
    record('skill-sync', false, '缺少权威目录 .agents/skills', 'zero')
    return
  }
  if (!skillDirs.length) {
    record('skill-sync', false, '.agents/skills 下没有任何技能', 'zero')
    return
  }
  const before = failures.length
  for (const mirror of mirrors) {
    for (const name of skillDirs) {
      const sourceFiles = await walk(path.join(canonical, name))
      if (!sourceFiles.length) {
        record('skill-sync', false, `.agents/skills/${name} 为空目录`, 'zero')
        continue
      }
      for (const source of sourceFiles) {
        const rel = path.relative(path.join(canonical, name), source)
        const target = path.join(mirror, name, rel)
        let targetText
        try {
          targetText = await readFile(target, 'utf8')
        } catch {
          record('skill-sync', false, `缺少副本 ${path.relative(root, target)}`, 'zero')
          continue
        }
        const sourceText = await readFile(source, 'utf8')
        if (sourceText !== targetText) {
          record('skill-sync', false, `副本不一致 ${path.relative(root, target)}`, 'zero')
        }
      }
    }
  }
  if (failures.length === before) {
    record('skill-sync', true, `已比对 ${skillDirs.length} 个技能 × 3 处副本`, 'zero')
  }
}

// --- 2. 工具入口文件存在 -------------------------------------------------------
async function checkEntryFiles() {
  const required = [
    'AGENTS.md',
    'CLAUDE.md',
    'GEMINI.md',
    '.cursor/rules/knowflow-engineering.mdc',
    '.github/copilot-instructions.md',
    'docs/engineering/ENGINEERING_STANDARDS.md',
    'docs/engineering/BACKEND_JAVA_STANDARD.md',
    'docs/engineering/FRONTEND_VUE_TS_STANDARD.md',
    'docs/engineering/SECURITY_AND_AI_STANDARD.md',
    'docs/engineering/TESTING_AND_AUTOMATION_GATES.md'
  ]
  const before = failures.length
  for (const rel of required) {
    try {
      await stat(path.join(root, rel))
    } catch {
      record('entry-files', false, `缺少 ${rel}`, 'zero')
    }
  }
  if (failures.length === before) {
    record('entry-files', true, `已检查 ${required.length} 个入口文件`, 'zero')
  }
}

// --- 3. 零容忍与棘轮模式 -------------------------------------------------------
async function readBaseline() {
  const raw = await readFile(path.join(root, 'scripts/engineering/baseline.json'), 'utf8')
  return JSON.parse(raw).ratchets
}

async function checkPatterns() {
  const baseline = await readBaseline()
  const javaFiles = (await walk(path.join(root, 'backend/src/main/java'))).filter((f) => f.endsWith('.java'))
  const sqlFiles = (await walk(path.join(root, 'backend/src/main/resources'))).filter((f) => f.endsWith('.sql'))
  const feFiles = (await walk(path.join(root, 'frontend/src'))).filter((f) => /\.(ts|tsx|vue|js)$/.test(f))

  const zero = [
    ['frontend.dialogs', countMatches(feFiles, /window\.(alert|confirm|prompt)\s*\(/g), '前端禁用 window.alert/confirm/prompt'],
    ['backend.printStackTrace', countMatches(javaFiles, /printStackTrace\s*\(/g), '后端禁用 printStackTrace()']
  ]
  for (const [name, actual, desc] of zero) {
    record(name, actual === 0, `${desc}，实际 ${actual} 处（必须为 0）`, 'zero')
  }

  const ratchets = [
    ['backend.java.systemOut', countMatches(javaFiles, /System\.out\.println/g), 'Java 中 System.out.println 出现次数'],
    ['backend.java.customThreads', countMatches(javaFiles, /Executors\.new|new Thread\s*\(/g), 'Java 中 Executors.new / new Thread( 出现次数'],
    ['backend.java.selectStar', countMatches(javaFiles, /select\s+\*/gi), 'Java 中 SELECT * 出现次数'],
    ['backend.sql.selectStar', countMatches(sqlFiles, /select\s+\*/gi), 'SQL 中 SELECT * 出现次数'],
    ['backend.sql.foreignKey', countMatches(sqlFiles, /foreign\s+key/gi), 'SQL 中物理外键出现次数'],
    ['frontend.explicitAny', countMatches(feFiles, /: any\b|as any\b|<any>|any\[\]/g), '前端显式 any 出现次数']
  ]
  for (const [name, actual, desc] of ratchets) {
    const expected = baseline[name]
    if (typeof expected !== 'number') {
      record(name, false, `baseline.json 缺少键 ${name}`, 'ratchet')
      continue
    }
    if (actual > expected) {
      record(name, false, `${desc} ${actual} > 基线 ${expected}（禁止新增，须修复代码）`, 'ratchet')
    } else if (actual < expected) {
      record(name, true, `${desc} ${actual} < 基线 ${expected}（已下降，请同步下调 baseline）`, 'ratchet')
      warnings.push(`${name}: 实际 ${actual} 低于基线 ${expected}，建议更新 baseline.json`)
    } else {
      record(name, true, `${desc} ${actual} = 基线`, 'ratchet')
    }
  }
}

// --- 4. 规范文档相对链接 -------------------------------------------------------
async function checkDocLinks() {
  const dir = path.join(root, 'docs/engineering')
  const files = (await walk(dir)).filter((f) => f.endsWith('.md'))
  const before = failures.length
  let checked = 0
  for (const file of files) {
    const text = await readFile(file, 'utf8')
    for (const match of text.matchAll(/\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g)) {
      const target = match[1]
      if (/^(https?:|mailto:|#)/.test(target)) continue
      const clean = target.split('#')[0]
      if (!clean) continue
      checked += 1
      const resolved = path.resolve(path.dirname(file), clean)
      try {
        await stat(resolved)
      } catch {
        record('doc-links', false, `${path.relative(root, file)} → ${target} 不存在`, 'zero')
      }
    }
  }
  if (failures.length === before) {
    record('doc-links', true, `已检查 ${checked} 个相对链接（${files.length} 份文档）`, 'zero')
  }
}

async function checkSnapshot() {
  const approved = JSON.parse(await readFile(path.join(root, 'scripts/engineering/approved-snapshot.json'), 'utf8'))
  const baseline = await readBaseline()
  for (const [key, value] of Object.entries(baseline)) {
    record(`baseline-safety:${key}`, Number.isInteger(value) && value >= 0 && value <= approved.ratchets[key], `TG-11: 当前 ${value}，审计上限 ${approved.ratchets[key]}；上调须单独人工审计`, 'zero')
  }
  const keys = []
  for (const file of await walk(path.join(root, 'frontend/src'))) {
    if (!/\.(ts|tsx|vue|css)$/.test(file)) continue
    const source = await readFile(file, 'utf8')
    const relative = path.relative(root, file)
    const lines = source.split('\n')
    lines.forEach((line, index) => {
      const rules = []
      if (/:\s*any\b|as\s+any\b|<any>|any\[\]/.test(line)) rules.push('FE-any')
      if (/--kb-/.test(line)) rules.push('FE-token-prefix')
      // CSS declarations only; SVG attributes, JS theme initialization and strings are excluded.
      if ((file.endsWith('.css') || (file.endsWith('.vue') && source.slice(0, source.indexOf(line)).lastIndexOf('<style') > source.slice(0, source.indexOf(line)).lastIndexOf('</style'))) && /(?:color|background(?:-color)?|border(?:-color)?)\s*:\s*#[0-9a-f]{3,8}\b/i.test(line)) rules.push('FE-token-color')
      for (const rule of rules) keys.push({ key: fingerprint(relative, rule, line), file: relative, line: index + 1, rule })
    })
  }
  const fresh = additions(keys.map(x => x.key), approved.fingerprints)
  record('location-ratchet', fresh.length === 0, fresh.length ? JSON.stringify(keys.filter(x => fresh.includes(x.key))) : '无新增位置违规；改用 unknown / --kf-*', 'zero')
}

try {
  const args = process.argv.slice(2)
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--root') { if (!args[++i] || args[i].startsWith('--')) throw new Error('--root requires a path') }
    else if (args[i] !== '--json') throw new Error(`Unknown argument: ${args[i]}`)
  }
  await stat(root)
  await checkSkillSync()
  await checkEntryFiles()
  await checkPatterns()
  await checkDocLinks()
  await checkSnapshot()
} catch (error) {
  record('checker-error', false, error.message, 'zero')
}

const payload = {
  ok: failures.length === 0,
  failures,
  warnings,
  checks: results
}

if (asJson) {
  console.log(JSON.stringify(payload, null, 2))
} else {
  for (const item of results) {
    console.log(`${item.ok ? 'PASS' : 'FAIL'} [${item.kind}] ${item.name} — ${item.detail}`)
  }
  for (const warning of warnings) console.log(`WARN ${warning}`)
  console.log(failures.length === 0 ? '\nENGINEERING CHECK PASSED' : `\nENGINEERING CHECK FAILED (${failures.length})`)
}

process.exitCode = failures.length === 0 ? 0 : 1
