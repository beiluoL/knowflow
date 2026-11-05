/** Narrow Batch4 boundaries; existing unrelated mapper edges are deliberately outside scope. */
const appsTypes = new Map([
  ['AppsController', 'apps.controller'],
  ['UserAppUpdateRequest', 'apps.dto'],
  ['UserAppVO', 'apps.vo'],
  ['UserAppMapper', 'apps.mapper'],
  ['UserApp', 'apps.entity'],
  ['UserAppService', 'apps'],
  ['OfficialAppRegistry', 'apps'],
  ['AppPurchaseAccess', 'apps.contract'],
])
const appsLayers = /^com\.knowflow\.apps\.(controller|dto|vo|mapper|entity)(\.|$)/
const purchaseAccess = 'com.knowflow.apps.contract.AppPurchaseAccess'

function appsViolations(file, code) {
  const name = file.split('/').at(-1).replace(/\.java$/, '')
  const pkg = code.match(/\bpackage\s+([\w.]+)\s*;/)?.[1] || ''
  const expected = appsTypes.get(name)
  const violations = []
  if (expected && (pkg !== `com.knowflow.${expected}`
    || !file.endsWith(`/com/knowflow/${expected.replaceAll('.', '/')}/${name}.java`))) {
    violations.push(`${name} must live in com.knowflow.${expected}`)
  }
  if (appsLayers.test(pkg) && !expected) {
    violations.push(`${name} needs reviewed Apps layer ownership`)
  }
  if (/^com\.knowflow\.apps(?:\.|$)/.test(pkg)
    && /\bcom\.knowflow\.store\./.test(code)) {
    violations.push('Apps must not depend on Store implementation')
  }
  if (/^com\.knowflow\.store(?:\.|$)/.test(pkg)) {
    const references = code.match(/\bcom\.knowflow\.apps\.[\w.*]+/g) || []
    if (references.some(reference => reference !== purchaseAccess)) {
      violations.push('Store may only access Apps through AppPurchaseAccess')
    }
  }
  return violations
}

// Existing Catalog reads and Quiz admin writes are scoped to these exact files.
const catalogMapperEdges = new Map([
  ['QuizQuestionAdminService.java', new Set(['QuizQuestionMapper'])],
])

function catalogViolations(file, code, source) {
  const root = 'backend/src/main/java/com/knowflow/catalog/'
  if (file.endsWith('/coding/contract/LegacyCodeCatalogQueryPort.java')
      || file.endsWith('/coding/service/impl/LegacyCodeCatalogQueryService.java')) {
    return /\bcom\.knowflow\.catalog\./.test(code)
      ? ['Coding legacy query must not depend on Catalog implementation'] : []
  }
  if (!file.startsWith(root)) return []
  const allowed = catalogMapperEdges.get(file.slice(root.length)) || new Set()
  const mapperNames = code.match(/\b[A-Z]\w*Mapper\b/g) || []
  const violations = [...new Set(mapperNames)]
    .filter(name => !allowed.has(name) && name !== 'ObjectMapper')
    .map(name => `Catalog must use an owning-module contract for ${name}`)
  if (file !== root + 'QuizQuestionAdminService.java'
      && /\bcom\.knowflow\.(?:[a-z][a-z0-9]*\.)?entity\.(?:QuizQuestion|LearningScenario|CodeQuestion|DocCategory|DocDocument)\b/.test(code)) {
    violations.push('Catalog must use source-owned projections')
  }
  if (file !== root + 'QuestionCatalogService.java'
      && /\b(?:from|join|update|into)\s+[`"]?(?:code_question|code_problem|quiz_question|learning_scenario|doc_category|doc_document|kg_entity|question_topic)\b/i.test(source)) {
    violations.push('Catalog must not add raw access to an owner table')
  }
  return violations
}

const storeTypes = new Map([
  ['StoreController', 'controller'],
  ['ProductMapper', 'mapper'], ['EntitlementMapper', 'mapper'],
  ['OrderMapper', 'mapper'], ['OrderItemMapper', 'mapper'],
  ['Product', 'entity'], ['Entitlement', 'entity'], ['Order', 'entity'], ['OrderItem', 'entity'],
  ['ProductVO', 'vo'], ['PurchaseResultVO', 'vo'],
])

function storeViolations(file, code) {
  const name = file.split('/').at(-1).replace(/\.java$/, '')
  const pkg = code.match(/\bpackage\s+([\w.]+)\s*;/)?.[1] || ''
  const layer = storeTypes.get(name)
  const violations = []
  if (layer && (pkg !== `com.knowflow.store.${layer}`
      || !file.endsWith(`/com/knowflow/store/${layer}/${name}.java`))) {
    violations.push(`${name} must live in com.knowflow.store.${layer}`)
  }
  if (!/^com\.knowflow\.store(?:\.|$)/.test(pkg)) return violations
  const mapperNames = code.match(/\b[A-Z]\w*Mapper\b/g) || []
  const persistenceNames = mapperNames.filter(mapper => mapper !== 'ObjectMapper')
  if (pkg.startsWith('com.knowflow.store.controller') && persistenceNames.length) {
    violations.push('Store HTTP handlers must use a Service')
  }
  if (persistenceNames.some(mapper => mapper !== 'BaseMapper' && storeTypes.get(mapper) !== 'mapper')) {
    violations.push('Store must use an owning-module service for foreign persistence')
  }
  if (/\b(?:interface|class)\s+\w+Mapper\b/.test(code) && pkg !== 'com.knowflow.store.mapper') {
    violations.push('Store Mapper must live in store.mapper')
  }
  if (/@(?:com\.baomidou\.mybatisplus\.annotation\.)?TableName\b/.test(code) && pkg !== 'com.knowflow.store.entity') {
    violations.push('Store Entity must live in store.entity')
  }
  return violations
}

function flashcardViolations(file, code) {
  if (!file.endsWith('/service/impl/LearningContentServiceImpl.java')) return []
  const mappers = code.matchAll(/\bLearningFlashcardMapper\s+(\w+)\b/g)
  for (const [, name] of mappers) {
    const calls = code.matchAll(new RegExp(`\\b${name}\\s*\\.\\s*(\\w+)\\s*\\(`, 'g'))
    for (const [, method] of calls) {
      if (!['selectList'].includes(method)) {
        return ['Learning content must delegate flashcard writes to GeneratedFlashcardWriter']
      }
    }
  }
  return []
}

function masteryViolations(file, code) {
  const root = 'backend/src/main/java/com/knowflow/'
  if (/\bclass\s+MasteryEngine\b/.test(code)
      && (file !== root + 'mastery/service/MasteryEngine.java'
        || !/\bpackage\s+com\.knowflow\.mastery\.service\s*;/.test(code))) {
    return ['MasteryEngine must live in mastery.service']
  }
  if (!/\bKnowledgeMasteryMapper\b/.test(code)) return []
  if (file === root + 'mastery/service/KnowledgeMasteryServiceImpl.java'
      || file === root + 'mastery/mapper/KnowledgeMasteryMapper.java') return []
  if (file === root + 'mastery/service/MasteryReviewQueryService.java') {
    const fields = code.matchAll(/\bKnowledgeMasteryMapper\s+(\w+)\b/g)
    for (const [, name] of fields) {
      const calls = code.matchAll(new RegExp(`\\b${name}\\s*\\.\\s*(\\w+)\\s*\\(`, 'g'))
      if ([...calls].some(([, method]) => method !== 'selectList')) {
        return ['Mastery review readers may only call selectList']
      }
    }
    return []
  }
  return ['Mastery projection access must use KnowledgeMasteryService']
}

function knowledgeViolations(file, code) {
  if (file.endsWith('/controller/admin/AdminQuizQuestionController.java')
      && /\b(?:DocDocumentMapper|DocCategoryMapper)\b/.test(code)) {
    return ['Admin quiz generation must use QuizSourceQueryService']
  }
  return []
}

function commonViolations(file, code) {
  const references = [...code.matchAll(/\bcom\.knowflow\.([\w.*]+)/g)].map(match => match[1])
  if (file.endsWith('/exception/GlobalExceptionHandler.java')
      && references.some(reference => reference !== 'exception' && !reference.startsWith('common.'))) {
    return ['GlobalExceptionHandler must use shared error contracts']
  }
  if (!/\bpackage\s+com\.knowflow\.common(?:\.[\w.]+)?\s*;/.test(code)) return []
  const violations = []
  const securityBridge = file.endsWith('/common/SecurityUtils.java')
  if (references.some(reference => reference !== 'common' && !reference.startsWith('common.')
      && !(securityBridge && /^framework\.security\.SecurityUtils(?:\.|$)/.test(reference)))) {
    violations.push('Common must not depend on business modules')
  }
  if (/\b(?:class|interface|record)\s+\w+(?:Mapper|Controller|Entity)\b|@(?:TableName|Mapper|RestController|Controller)\b/.test(code)
      && !file.endsWith('/common/persistence/BaseEntity.java')) {
    violations.push('Common must not contain business persistence or HTTP types')
  }
  return violations
}

function frameworkViolations(code) {
  if (!/\bpackage\s+com\.knowflow\.framework(?:\.[\w.]+)?\s*;/.test(code)) return []
  const references = [...code.matchAll(/\bcom\.knowflow\.([\w.*]+)/g)].map(match => match[1])
  const infrastructure = new Set([
    'framework.security',
    'utils.JwtUtils',
    'security.TokenBlacklistService',
    'config.datasource.DynamicRoutingDataSource',
    'exception.BusinessException',
  ])
  if (references.some(reference => reference !== 'framework' && !reference.startsWith('framework.')
      && !reference.startsWith('common.') && !infrastructure.has(reference))) {
    return ['Framework may only depend on shared types and infrastructure']
  }
  return []
}

function rewardViolations(code) {
  if (!/\bpackage\s+com\.knowflow\.reward(?:\.[\w.]+)?\s*;/.test(code)) return []
  if (/\bSysUserMapper\b|\bLearningWriteGuard\b|\bcom\.knowflow\.(?:identity\.(?!contract\.)|learning\.(?!contract\.)|store\.)/.test(code)) {
    return ['Reward must use public Identity and Learning contracts']
  }
  return []
}

function agentViolations(file, code) {
  const isAgent = /\bpackage\s+com\.knowflow\.agent(?:\.[\w.]+)?\s*;/.test(code)
    || /\/(?:CodeAgent|AgentTool|AgentWorkflow)Controller\.java$/.test(file)
  if (isAgent && /\bcom\.knowflow\.(?:reward|store)\.(?:mapper|entity)\./.test(code)) {
    return ['Agent must not access Reward or Store persistence directly']
  }
  return []
}

export function boundaryViolations(file, source, entityNames = []) {
  if (!file.replaceAll('\\', '/').startsWith('backend/src/main/java/')) return []
  const code = source.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\n]*|\/\*[\s\S]*?\*\//g, value => value.replace(/[^\n]/g, ' '))
  const violations = commonViolations(file.replaceAll('\\', '/'), code)
  violations.push(...frameworkViolations(code))
  violations.push(...rewardViolations(code))
  violations.push(...agentViolations(file, code))
  violations.push(...appsViolations(file.replaceAll('\\', '/'), code))
  violations.push(...catalogViolations(file.replaceAll('\\', '/'), code, source))
  violations.push(...storeViolations(file.replaceAll('\\', '/'), code))
  violations.push(...flashcardViolations(file.replaceAll('\\', '/'), code))
  violations.push(...masteryViolations(file.replaceAll('\\', '/'), code))
  violations.push(...knowledgeViolations(file.replaceAll('\\', '/'), code))
  if (file.endsWith('/DocServiceImpl.java') && /\bSysUserMapper\b/.test(code)) violations.push('DocServiceImpl must delegate user writes through UserService')
  if (!file.endsWith('QueryPort.java')) return violations
  const entities = [...entityNames, ...[...code.matchAll(/import\s+com\.knowflow\.(?:[a-z][a-z0-9]*\.)?entity\.([\w]+)\s*;/g)].map(match => match[1])]
  const contracts = [...code.matchAll(/(?:\bpublic\s+(?:(?:static|abstract|default|final)\s+)*|(?<=[{;])\s*)([\w<>?,.\s\[\]]+)\s+\w+\s*\([^;{}]*\)\s*(?:throws\s+[\w,.\s]+)?[;{]/g)]
  for (const match of contracts) {
    const signature = match[0]
    if (/\b(?:private|protected)\b/.test(signature)) continue
    if (/\b\w*Mapper\b|\b\w*Wrapper\b|\bConnection\b|\b\w*Entity\b|com\.knowflow\.(?:[a-z][a-z0-9]*\.)?entity\./.test(signature)
        || entities.some(entity => new RegExp(`\\b${entity}\\b`).test(signature))) {
      violations.push('QueryPort public contract exposes persistence type')
    }
  }
  return violations
}
