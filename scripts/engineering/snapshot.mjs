import { createHash } from 'node:crypto'
export function fingerprint(file, rule, source) {
  return `${file}:${rule}:${createHash('sha256').update(source.replace(/\s+/g, ' ').trim()).digest('hex')}`
}
export function additions(current, baseline) {
  const remaining = new Map()
  for (const key of baseline) remaining.set(key, (remaining.get(key) || 0) + 1)
  return current.filter(key => {
    const count = remaining.get(key) || 0
    if (count) { remaining.set(key, count - 1); return false }
    return true
  })
}
