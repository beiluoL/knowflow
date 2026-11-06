#!/usr/bin/env node
/**
 * KnowFlow 统一工程检查（零依赖，Node 18+）。
 * 用法：node scripts/engineering/check.mjs [--json] [--code-only]
 *
 * 零容忍项：出现即失败。
 * 棘轮项：与 scripts/engineering/baseline.json 比较，只能持平或下降。
 */
import { readdir, readFile, stat } from 'node:fs/promises'
import { readFileSync, existsSync, writeFileSync, rmSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fingerprint, additions } from './snapshot.mjs'
import { boundaryViolations, directoryViolations } from './module-boundary.mjs'
import { jdbcBoundaryViolations } from './jdbc-boundary.mjs'
import { mapperViolations } from './mapper-boundary.mjs'
import { fileURLToPath } from 'node:url'

const rootArg = process.argv.indexOf('--root')
const root = rootArg >= 0 ? path.resolve(process.argv[rootArg + 1] || '.') : path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const asJson = process.argv.includes('--json')
// Source-only distributions omit local agent mirrors and process documentation.
// All code, ownership, architecture, security-pattern and ratchet checks still run.
const CODE_ONLY = process.argv.includes('--code-only')
// 格式化合规检查默认关闭：避免对存量（尚未全量格式化的）代码与未提交改动造成破坏性失败。
// CI/verify 可传入 --format 并搭配 --manifest/--files（仅本次改动文件）开启严格检查。
const CHECK_FORMAT = process.argv.includes('--format')
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

// --- 5. 分层架构约束（文件级存量白名单 + 棘轮）---------------------------------
// 与 baseline.json 的「计数棘轮」互补：计数棘轮存在「修一处、加一处」的漏洞，
// 这里按「具体文件/方法」锁定存量，任何新位置出现违规即失败。
const ARCH_JAVA_RULES = {
  'backend.arch.controllerMapper': (text, rel) =>
    /Controller\.java$/.test(rel) && /import\s+com\.knowflow\.[\w.]*mapper\./.test(text),
  'backend.arch.controllerJdbc': (text, rel) =>
    /Controller\.java$/.test(rel) && /\bJdbcTemplate\b/.test(text),
  'backend.arch.serviceJdbc': (text, rel) =>
    /\/service\//.test(rel) && /\bJdbcTemplate\b/.test(text),
  'backend.arch.longFile': (text) => text.split('\n').length > 800,
  'backend.arch.emptyCatch': (text) => /catch\s*\([^)]*\)\s*\{\s*\}/.test(text)
}

const ARCH_FE_RULES = {
  'frontend.arch.longVue': (text) => text.split('\n').length > 800,
  'frontend.arch.directHttp': (text, rel) =>
    /\b(fetch|axios)\s*\(/.test(text) && !/\/api\//.test(rel),
  'frontend.arch.apiOutsideApiDir': (text, rel) =>
    /\b(fetch|axios)\s*\(\s*['"`]/.test(text) && !/\/api\//.test(rel),
  'frontend.arch.listenerLeak': (text) =>
    // 只统计 window/document 级监听：元素级监听随元素销毁，不构成跨组件泄漏。
    (text.match(/(?:window|document)\.addEventListener\s*\(/g) || []).length >
    (text.match(/(?:window|document)\.removeEventListener\s*\(/g) || []).length
}

/** 提取「文件#方法名」形式的超长方法标识（>100 行），与审计脚本口径一致。 */
function longMethodKeys(text, rel) {
  const arr = text.split('\n')
  const signature = /^\s{2,}(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?[\w<>\[\],.?\s]+\s+(\w+)\s*\([^;]*$/
  const keys = []
  for (let i = 0; i < arr.length; i += 1) {
    const m = arr[i].match(signature)
    if (!m) continue
    let depth = 0
    let opened = false
    let end = i
    for (let j = i; j < arr.length; j += 1) {
      for (const ch of arr[j]) {
        if (ch === '{') { depth += 1; opened = true } else if (ch === '}') depth -= 1
      }
      if (opened && depth <= 0) { end = j; break }
    }
    if (opened && end - i + 1 > 100) keys.push(`${rel}#${m[1]}`)
  }
  return keys
}

async function checkArchitecture() {
  let baseline
  try {
    baseline = JSON.parse(await readFile(path.join(root, 'scripts/engineering/architecture-baseline.json'), 'utf8'))
  } catch {
    record('architecture', false, '缺少 scripts/engineering/architecture-baseline.json', 'zero')
    return
  }
  const rules = baseline.rules || {}
  const toRel = (f) => path.relative(root, f).split(path.sep).join('/')
  const javaFiles = (await walk(path.join(root, 'backend/src/main/java'))).filter((f) => f.endsWith('.java'))
  const feFiles = (await walk(path.join(root, 'frontend/src'))).filter((f) => f.endsWith('.vue'))

  // 1) 逐文件规则
  const actual = {}
  for (const [rule, predicate] of Object.entries({ ...ARCH_JAVA_RULES, ...ARCH_FE_RULES })) {
    actual[rule] = []
    for (const file of rule.startsWith('backend.') ? javaFiles : feFiles) {
      const rel = toRel(file)
      if (predicate(readFileSync(file, 'utf8'), rel)) actual[rule].push(rel)
    }
  }

  // 2) 超长方法（标识为 文件#方法）
  actual['backend.arch.longMethod'] = []
  for (const file of javaFiles) {
    actual['backend.arch.longMethod'].push(...longMethodKeys(readFileSync(file, 'utf8'), toRel(file)))
  }

  // 3) common 不得反向依赖任何业务模块
  const featurePkgs = ['agent', 'ai', 'apps', 'catalog', 'flashcards', 'knowledge', 'mastery',
    'reward', 'seed', 'store', 'voice', 'websocket', 'exam', 'interview', 'learning', 'coding']
  actual['backend.arch.commonReverseDep'] = []
  for (const file of javaFiles) {
    const rel = toRel(file)
    if (!rel.includes('/common/')) continue
    const text = readFileSync(file, 'utf8')
    if (featurePkgs.some((p) => new RegExp(`import\\s+com\\.knowflow\\.${p}\\.`).test(text))) {
      actual['backend.arch.commonReverseDep'].push(rel)
    }
  }

  // 4) 与白名单比对：新增 = 失败；白名单已失效 = 提示下调
  for (const rule of Object.keys(actual).sort()) {
    if (!Array.isArray(rules[rule])) {
      record(`arch:${rule}`, false, 'architecture-baseline.json 缺少该规则', 'zero')
      continue
    }
    const allowed = new Set(rules[rule])
    const fresh = actual[rule].filter((item) => !allowed.has(item))
    const stale = rules[rule].filter((item) => !actual[rule].includes(item))
    record(`arch:${rule}`, fresh.length === 0,
      fresh.length
        ? `新增 ${fresh.length} 处违规：${fresh.slice(0, 5).join(', ')}${fresh.length > 5 ? ' …' : ''}`
        : `存量 ${actual[rule].length} 处（白名单锁定，只减不增）`,
      'ratchet')
    if (stale.length) warnings.push(`arch:${rule} 白名单有 ${stale.length} 条已失效，请下调：${stale.slice(0, 3).join(', ')}`)
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

async function checkModuleBoundaries() {
  const violations = []
  const directories = []
  const entityNames = (await walk(path.join(root, 'backend/src/main/java/com/knowflow/entity'))).filter(file => file.endsWith('.java')).map(file => path.basename(file, '.java'))
  for (const file of await walk(path.join(root, 'backend/src/main/java'))) {
    if (!file.endsWith('.java')) continue
    const relative = path.relative(root, file).split(path.sep).join('/')
    directories.push(...directoryViolations(relative, await readFile(file, 'utf8')).map(detail => `${relative}: ${detail}`))
    for (const detail of boundaryViolations(relative, await readFile(file, 'utf8'), entityNames)) {
      violations.push(`${relative}: ${detail}`)
    }
  }
  record('backend-v2-directory', directories.length === 0, directories.join('; ') || 'V2目录通过', 'zero')
  record('module-boundary-scoped', violations.length === 0,
    violations.length ? violations.join('; ') : 'DocServiceImpl user mapper edge absent; QueryPort contracts checked', 'zero')
}

async function checkJdbcBoundary() {
  const file = 'backend/src/main/java/com/knowflow/techenglish/service/TechVocabService.java'
  let source
  try { source = await readFile(path.join(root, file), 'utf8') }
  catch (error) { if (error.code === 'ENOENT') return; throw error }
  const violations = jdbcBoundaryViolations(file, source)
  record('jdbc-boundary-tech-vocab', violations.length === 0,
    violations.length ? violations.join('; ') : 'TechVocabService scoped JDBC regression guard passed', 'zero')
}

async function checkMapperOwnership() {
  const registryFile = path.join(root, 'scripts/engineering/mapper-ownership.json')
  if (!existsSync(registryFile)) {
    record('mapper-ownership', false, 'Missing reviewed mapper ownership registry', 'zero')
    return
  }
  const sources = {}
  for (const file of await walk(path.join(root, 'backend/src/main/java'))) {
    if (file.endsWith('.java')) sources[path.relative(root, file).split(path.sep).join('/')] = await readFile(file, 'utf8')
  }
  const violations = mapperViolations(sources, JSON.parse(await readFile(registryFile, 'utf8')))
  record('mapper-ownership', violations.length === 0, violations.length ? violations.join('; ') : 'Mapper identity ratchet and reviewed module dependencies passed', 'zero')
}

// --- 6. 格式化工具链与（可选）逐文件合规 -------------------------------------
// 默认只校验「工具链是否齐备」（不扫描全量代码，不影响现有门禁）。
// 传入 --format 且提供 --manifest/--files 时，才对「本次改动文件」做格式化合规检查。
async function checkFormatting() {
  const tooling = [
    ['.editorconfig', path.join(root, '.editorconfig')],
    ['frontend/.prettierrc.json', path.join(root, 'frontend', '.prettierrc.json')],
    ['frontend/.prettierignore', path.join(root, 'frontend', '.prettierignore')],
    ['scripts/format-changed.mjs', path.join(root, 'scripts', 'format-changed.mjs')],
    ...(CHECK_FORMAT ? [['backend/tools/google-java-format.jar', path.join(root, 'backend', 'tools', 'google-java-format.jar')]] : [])
  ]
  const missing = tooling.filter(([, p]) => !existsSync(p)).map(([n]) => n)
  if (missing.length) {
    record('format-tooling', false, `缺少格式化工具链：${missing.join(', ')}`, 'zero')
    return
  }
  let pom = ''
  try {
    pom = readFileSync(path.join(root, 'backend', 'pom.xml'), 'utf8')
  } catch {
    pom = ''
  }
  if (!/spotless-maven-plugin/.test(pom)) {
    record('format-tooling', false, 'backend/pom.xml 未声明 spotless-maven-plugin', 'zero')
    return
  }
  record('format-tooling', true, '格式化工具链齐备（EditorConfig / Prettier / Spotless / 自动格式化器）', 'zero')

  if (!CHECK_FORMAT) return

  // 仅对显式传入的「本次改动文件」做合规检查（绝不扫描全量未提交改动）
  const argv = process.argv.slice(2)
  const files = []
  const mi = argv.indexOf('--manifest')
  if (mi >= 0 && argv[mi + 1] && !argv[mi + 1].startsWith('--')) {
    try {
      const raw = JSON.parse(readFileSync(path.resolve(root, argv[mi + 1]), 'utf8'))
      files.push(...(Array.isArray(raw.files) ? raw.files : Array.isArray(raw) ? raw : []))
    } catch {
      /* ignore malformed manifest */
    }
  }
  const fi = argv.indexOf('--files')
  if (fi >= 0) {
    for (let i = fi + 1; i < argv.length && !argv[i].startsWith('--'); i++) files.push(argv[i])
  }
  if (!files.length) {
    warnings.push('format-compliance: 已开启 --format 但未提供 --manifest/--files，跳过逐文件格式化检查')
    return
  }
  const tmpManifest = path.join(root, 'scripts', '.fmt-check-manifest.json')
  writeFileSync(tmpManifest, JSON.stringify({ files }))
  try {
    const r = spawnSync('node', [path.join(root, 'scripts', 'format-changed.mjs'), '--manifest', tmpManifest, '--dry-run'], {
      cwd: root,
      encoding: 'utf8'
    })
    const would = (r.stdout.match(/将格式化/g) || []).length
    if (would > 0) {
      record('format-compliance', false, `有 ${would} 个改动文件未格式化，请先运行格式化器（node scripts/format-changed.mjs）`, 'zero')
    } else {
      record('format-compliance', true, `改动文件（${files.length}）均已符合格式化规范`, 'zero')
    }
  } finally {
    rmSync(tmpManifest, { force: true })
  }
}

try {
  const args = process.argv.slice(2)
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--root') { if (!args[++i] || args[i].startsWith('--')) throw new Error('--root requires a path') }
    else if (args[i] === '--manifest') { if (!args[++i] || args[i].startsWith('--')) throw new Error('--manifest requires a path') }
    else if (args[i] === '--files') {
      let j = i + 1
      while (j < args.length && !args[j].startsWith('--')) j += 1
      i = j - 1
    } else if (args[i] === '--json' || args[i] === '--format' || args[i] === '--code-only') {
      /* 无值 flag */
    } else throw new Error(`Unknown argument: ${args[i]}`)
  }
  await stat(root)
  if (!CODE_ONLY) {
    await checkSkillSync()
    await checkEntryFiles()
  }
  await checkPatterns()
  if (!CODE_ONLY) await checkDocLinks()
  await checkArchitecture()
  await checkModuleBoundaries()
  await checkJdbcBoundary()
  await checkMapperOwnership()
  await checkSnapshot()
  await checkFormatting()
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
