/** Incremental lexical mapper registry guard; not a Java compiler or SQL write analyzer. */
export function mapperViolations(sources, registry) {
  const errors = [], entities = new Map()
  const owned = registry.owned || {}, legacy = new Set(registry.legacy || [])
  const clean = text => text.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\n]*|\/\*[\s\S]*?\*\//g, value => value.replace(/[^\n]/g,' '))
  const tables = new Map()
  for (const text of Object.values(sources)) {
    const pkg = text.match(/\bpackage\s+([\w.]+)\s*;/)?.[1]
    const name = text.match(/\bclass\s+(\w+)/)?.[1]
    const table = text.match(/@TableName\s*\(\s*(?:value\s*=\s*)?"([^"]+)"/)?.[1]
    if (pkg && name && table) tables.set(`${pkg}.${name}`, table)
  }
  const registered = new Set()
  for (const [file, text] of Object.entries(sources)) {
    const code = clean(text), pkg = code.match(/\bpackage\s+([\w.]+)\s*;/)?.[1]
    const name = code.match(/\binterface\s+(\w+)\b/)?.[1]
    if (name && (/\bBaseMapper\s*</.test(code) || /@(?:org\.apache\.ibatis\.annotations\.)?Mapper\b/.test(code))) {
      const fqn = `${pkg}.${name}`, rule = owned[fqn]
      registered.add(fqn)
      if (!legacy.has(fqn)) {
        if (!rule) errors.push(`${file}: new mapper requires reviewed ownership registry`)
        if (!/^com\.knowflow\.[a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)*\.mapper$/.test(pkg || '') || !file.endsWith(pkg?.replaceAll('.','/') + '/' + name + '.java')) errors.push(`${file}: illegal mapper package or source path`)
        if (!/^[A-Z][A-Za-z0-9]*Mapper$/.test(name) || name === 'CommonMapper') errors.push(`${file}: invalid mapper name`)
        const entity = code.match(/\bBaseMapper\s*<\s*([\w.]+)\s*>/)?.[1]
        if (!entity || (rule && entity.split('.').at(-1) !== rule.entity)) errors.push(`${file}: mapped entity requires explicit review`)
      }
      const entity = code.match(/\bBaseMapper\s*<\s*([\w.]+)\s*>/)?.[1]
      const imported = code.match(new RegExp('\\bimport\\s+([\\w.]+\\.' + (entity?.split('.').at(-1) || 'NONE') + ')\\s*;'))?.[1]
      const entityKey = imported || (entity?.includes('.') ? entity : `${pkg}.${entity}`)
      const key = tables.get(entityKey) || entityKey
      if (entities.has(key)) errors.push(`${file}: duplicate mapped entity with ${entities.get(key)}`)
      else entities.set(key,file)
    }
    for (const [mapper, rule] of Object.entries(owned)) {
      if (pkg === mapper.slice(0,mapper.lastIndexOf('.'))) continue
      const simple=mapper.split('.').at(-1), ownerPackage=mapper.slice(0,mapper.lastIndexOf('.'))
      const references = code.includes(mapper) || (code.includes(`import ${ownerPackage}.*;`) && new RegExp(`\\b${simple}\\b`).test(code))
      if (references && !(rule.callers || []).includes(file)) errors.push(`${file}: unreviewed direct dependency on ${mapper}`)
    }
  }
  for (const mapper of Object.keys(owned)) if (!registered.has(mapper)) errors.push(`${mapper}: governed mapper missing`)
  return errors
}
