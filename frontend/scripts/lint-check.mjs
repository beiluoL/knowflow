import { ESLint } from 'eslint'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fingerprint, additions } from './lint-fingerprint.mjs'
const results = await new ESLint().lintFiles(['src/**/*.{ts,tsx,vue,js}'])
const entries = []
for (const result of results) {
  const lines = (result.source ?? await readFile(result.filePath, 'utf8')).split('\n')
  for (const m of result.messages) entries.push({ file: path.relative(process.cwd(), result.filePath), rule: m.ruleId, line: m.line, message: m.message, fatal: !!m.fatal, key: fingerprint(path.relative(process.cwd(), result.filePath), m.ruleId ?? 'parse-error', lines[(m.line || 1) - 1] || '') })
}
if (process.argv.includes('--report')) console.log(JSON.stringify(entries, null, 2))
else {
  const baseline = JSON.parse(await readFile(new URL('../eslint-baseline.json', import.meta.url), 'utf8'))
  const fresh = additions(entries.map(x => x.key), baseline.fingerprints)
  const failed = entries.filter(x => x.fatal || fresh.includes(x.key))
  for (const e of failed) console.error(`${e.file}:${e.line} ${e.rule}: ${e.message}`)
  console.log(`ESLint: ${entries.length} current, ${baseline.fingerprints.length} historical, ${failed.length} new/fatal`)
  process.exitCode = failed.length ? 1 : 0
}
