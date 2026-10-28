import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
// Explicit first-party UI scope; excludes dependencies, build output and historical reports.
const roots = ['src', 'tests']; const failures = []
async function scan(dir) {
 for (const e of await readdir(dir, { withFileTypes: true })) {
  const file = path.join(dir,e.name)
  if(e.isDirectory()) { await scan(file); continue }
  if(!/\.(vue|css|ts|js|mjs|cjs|html)$/.test(file)) continue
  const text=await readFile(file,'utf8')
  // Business routes (/kb-titles, /admin/kb-members), kb_id and persisted kb-theme /
  // kb-immersive:* / kb-lcg-feynman-draft are deliberately outside UI syntax.
  text.split('\n').forEach((line,i)=> {
   if(/--kb-[\w-]+|\.kb-[\w-]+|@keyframes\s+kb-|(?:class|name)=["']kb-|data-kb-input-modality/.test(line)) failures.push(`${file}:${i+1}: ${line.trim()}`)
  })
 }
}
for(const root of roots) await scan(root)
const tailwind=await readFile('tailwind.config.js','utf8');if(tailwind.includes('--kb-')) failures.push('tailwind.config.js: legacy token')
if(failures.length) { console.error(failures.join('\n'));process.exitCode=1 } else console.log('UI prefix check passed (src, tests, tailwind; business/storage identifiers preserved)')
