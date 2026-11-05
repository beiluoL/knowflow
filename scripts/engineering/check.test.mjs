import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'

const checker = path.resolve('scripts/engineering/check.mjs')
for (const scenario of ['code-only', 'code-only-violation', 'valid', 'mirror', 'entry', 'any', 'select', 'thread', 'exception', 'token', 'token-valid', 'baseline-up', 'offset', 'bad-arg', 'missing-mapper-registry']) {
  test(scenario, () => {
    const root = mkdtempSync(path.join(tmpdir(), 'knowflow-check-'))
    const put = (file, text = '') => { mkdirSync(path.dirname(path.join(root, file)), { recursive: true }); writeFileSync(path.join(root, file), text) }
    try {
      for (const base of ['.agents', '.trae', '.workbuddy']) put(`${base}/skills/example/SKILL.md`, 'same')
      for (const file of ['AGENTS.md', 'CLAUDE.md', 'GEMINI.md', '.cursor/rules/knowflow-engineering.mdc', '.github/copilot-instructions.md', ...['ENGINEERING_STANDARDS', 'BACKEND_JAVA_STANDARD', 'FRONTEND_VUE_TS_STANDARD', 'SECURITY_AND_AI_STANDARD', 'TESTING_AND_AUTOMATION_GATES'].map(x => `docs/engineering/${x}.md`)]) put(file)
      put('scripts/engineering/baseline.json', JSON.stringify({ ratchets: Object.fromEntries(['backend.java.systemOut', 'backend.java.customThreads', 'backend.java.selectStar', 'backend.sql.selectStar', 'backend.sql.foreignKey', 'frontend.explicitAny'].map(x => [x, 0])) }))
      put('scripts/engineering/approved-snapshot.json', JSON.stringify({ratchets:Object.fromEntries(['backend.java.systemOut', 'backend.java.customThreads', 'backend.java.selectStar', 'backend.sql.selectStar', 'backend.sql.foreignKey', 'frontend.explicitAny'].map(x=>[x,0])),fingerprints:[]}))
      // Complete the isolated project fixture for the current checker inputs.
      put('scripts/engineering/architecture-baseline.json', JSON.stringify({rules:Object.fromEntries(['backend.arch.commonReverseDep','backend.arch.controllerJdbc','backend.arch.controllerMapper','backend.arch.emptyCatch','backend.arch.longFile','backend.arch.longMethod','backend.arch.serviceJdbc','frontend.arch.apiOutsideApiDir','frontend.arch.directHttp','frontend.arch.listenerLeak','frontend.arch.longVue'].map(rule => [rule, []]))}))
      put('scripts/engineering/mapper-ownership.json', JSON.stringify({legacy:[],owned:{}}))
      for (const file of ['.editorconfig', 'frontend/.prettierrc.json', 'frontend/.prettierignore', 'scripts/format-changed.mjs', 'backend/tools/google-java-format.jar']) put(file)
      put('backend/pom.xml', '<plugin><artifactId>spotless-maven-plugin</artifactId></plugin>')
      if (scenario === 'missing-mapper-registry') rmSync(path.join(root, 'scripts/engineering/mapper-ownership.json'))
      if (scenario === 'token') put('frontend/src/Sample.vue', '<style>\n.a { color: #ff0000; --kb-color: red }\n</style>')
      if (scenario === 'token-valid') put('frontend/src/Sample.vue', '<style>\n.a { color: var(--kf-text) }\n</style>')
      if (scenario === 'baseline-up') put('scripts/engineering/baseline.json', JSON.stringify({ratchets:{'frontend.explicitAny':61}}))
      if (scenario === 'offset') { put('frontend/src/new.ts', 'let x: any'); put('scripts/engineering/baseline.json',JSON.stringify({ratchets:{'frontend.explicitAny':1}})); put('scripts/engineering/approved-snapshot.json',JSON.stringify({ratchets:{'frontend.explicitAny':1},fingerprints:['old.ts:FE-any:old']})) }
      if (scenario === 'mirror') put('.trae/skills/example/SKILL.md', 'drift')
      if (scenario === 'entry') rmSync(path.join(root, 'AGENTS.md'))
      if (scenario === 'any') put('frontend/src/sample.ts', 'let value: any')
      if (scenario === 'select') put('backend/src/main/java/Sample.java', 'SELECT * FROM sample')
      if (scenario === 'thread') put('backend/src/main/java/Sample.java', 'new Thread(')
      if (scenario === 'exception') put('scripts/engineering/baseline.json', '{')
      if (scenario.startsWith('code-only')) {
        for (const directory of ['docs', '.agents', '.trae', '.workbuddy']) rmSync(path.join(root, directory), { recursive: true, force: true })
        rmSync(path.join(root, 'backend/tools/google-java-format.jar'))
        if (scenario === 'code-only-violation') put('frontend/src/unsafe.ts', 'window.alert(1)')
      }
      const result = spawnSync(process.execPath, [checker, '--root', root, '--json', ...(scenario === 'bad-arg' ? ['--invalid'] : []), ...(scenario.startsWith('code-only') ? ['--code-only'] : [])], { encoding: 'utf8' })
      const report = JSON.parse(result.stdout)
      assert.equal(result.status, ['valid', 'token-valid', 'code-only'].includes(scenario) ? 0 : 1, JSON.stringify(report.failures))
      assert.equal(report.ok, ['valid', 'token-valid', 'code-only'].includes(scenario))
      assert.ok(Array.isArray(report.checks))
      if (scenario === 'missing-mapper-registry') assert.ok(report.checks.some(x => x.name === 'mapper-ownership' && !x.ok))
      if (scenario === 'exception') assert.ok(report.checks.some(x => x.name === 'checker-error'))
    } finally { rmSync(root, { recursive: true, force: true }) }
  })
}
