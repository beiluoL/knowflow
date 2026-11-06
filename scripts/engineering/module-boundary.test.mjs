import test from 'node:test'
import assert from 'node:assert/strict'
import { boundaryViolations } from './module-boundary.mjs'
const doc = 'backend/src/main/java/com/knowflow/knowledge/service/impl/DocServiceImpl.java'
const port = 'backend/src/main/java/com/knowflow/example/ThingQueryPort.java'
test('governed edge rejects import and fully qualified references', () => {
  assert.equal(boundaryViolations(doc, 'import com.knowflow.identity.mapper.SysUserMapper;').length, 1)
  assert.equal(boundaryViolations(doc, 'private com.knowflow.identity.mapper.SysUserMapper users;').length, 1)
})
test('comments strings and test sources do not create edges', () => {
  assert.deepEqual(boundaryViolations(doc, '// SysUserMapper\nString x="SysUserMapper"; /* SysUserMapper */'), [])
  assert.deepEqual(boundaryViolations(doc.replace('/main/', '/test/'), 'SysUserMapper users;'), [])
})
test('public query contracts reject persistence types including generics', () => {
  for (const type of ['SysUserMapper', 'LambdaQueryWrapper<User>', 'Connection', 'List<SysUser>']) {
    const imports = 'import com.knowflow.identity.entity.SysUser;\n'
    assert.ok(boundaryViolations(port, `${imports} public interface ThingQueryPort { ${type} find(); }`).length)
  }
})
test('DTO and shared infrastructure allowed; private implementation excluded', () => {
  assert.deepEqual(boundaryViolations(port, 'public interface ThingQueryPort { List<UserSummary> find(Long id); }'), [])
  assert.deepEqual(boundaryViolations('backend/src/main/java/x/ThingService.java', 'private SysUserMapper mapper;'), [])
  assert.deepEqual(boundaryViolations(port, 'public class ThingQueryPort { private Connection connection; public UserSummary find() { return null; } }'), [])
})

const java = 'backend/src/main/java/com/knowflow/'
test('Apps owned types require their exact packages and paths', () => {
  assert.ok(boundaryViolations(java + 'controller/AppsController.java',
    'package com.knowflow.controller; public class AppsController {}').length)
  assert.ok(boundaryViolations(java + 'apps/entity/Product.java',
    'package com.knowflow.apps.entity; public class Product {}').length)
  assert.deepEqual(boundaryViolations(java + 'apps/entity/UserApp.java',
    'package com.knowflow.apps.entity; public class UserApp {}'), [])
})

test('Apps and Store can only connect through the purchase contract', () => {
  assert.ok(boundaryViolations(java + 'apps/UserAppService.java',
    'package com.knowflow.apps; import static com.knowflow.store.EconomyError.INVALID_APP;').length)
  assert.ok(boundaryViolations(java + 'store/PurchaseService.java',
    'package com.knowflow.store; com.knowflow.apps.UserAppService apps;').length)
  assert.ok(boundaryViolations(java + 'store/PurchaseService.java',
    'package com.knowflow.store; import com.knowflow.apps.contract.*;').length)
  assert.deepEqual(boundaryViolations(java + 'store/PurchaseService.java',
    'package com.knowflow.store; import com.knowflow.apps.contract.AppPurchaseAccess;'), [])
})

test('Catalog uses Coding detail contract and rejects legacy Mapper access', () => {
  const file = java + 'catalog/QuestionCatalogService.java'
  assert.ok(boundaryViolations(file, 'import com.knowflow.mapper.*; CodeQuestionMapper legacy;').length)
  assert.ok(boundaryViolations(file, 'com.knowflow.coding.mapper.CodeQuestionMapper legacy;').length)
  assert.ok(boundaryViolations(file, 'QuizQuestionMapper quiz;').length)
  assert.ok(boundaryViolations(file, 'LearningScenarioMapper scenarios;').length)
  assert.ok(boundaryViolations(file, 'import com.knowflow.learning.entity.QuizQuestion;').length)
  assert.deepEqual(boundaryViolations(file, 'LegacyCodeCatalogQueryPort legacy; LearningQuestionCatalogQueryPort learning;'), [])
})

test('new Catalog classes cannot add direct foreign Mapper edges', () => {
  const file = java + 'catalog/NewCatalogService.java'
  assert.ok(boundaryViolations(file, 'import com.knowflow.learning.mapper.QuizQuestionMapper;').length)
  assert.deepEqual(boundaryViolations(java + 'catalog/QuizQuestionAdminService.java',
    'import com.knowflow.learning.mapper.QuizQuestionMapper;'), [])
})

test('Catalog cannot bypass query contracts with new raw owner-table SQL', () => {
  const sql = 'jdbc.query("SELECT id FROM code_question WHERE id=?", row, id);'
  assert.ok(boundaryViolations(java + 'catalog/NewCatalogService.java', sql).length)
  assert.deepEqual(boundaryViolations(java + 'catalog/QuestionCatalogService.java', sql), [])
})

test('Coding Catalog query contract cannot depend on Catalog implementation', () => {
  const file = java + 'coding/contract/LegacyCodeCatalogQueryPort.java'
  assert.ok(boundaryViolations(file, 'import com.knowflow.catalog.QuestionCatalogItem;').length)
  assert.ok(boundaryViolations(file, 'interface LegacyCodeCatalogQueryPort { QueryWrapper read(); }').length)
  assert.deepEqual(boundaryViolations(file, 'interface LegacyCodeCatalogQueryPort { JsonNode findDetail(long id); }'), [])
})

test('Store owns its vertical paths and excludes foreign persistence', () => {
  assert.ok(boundaryViolations(java + 'controller/StoreController.java',
    'package com.knowflow.controller;').length)
  assert.ok(boundaryViolations(java + 'store/controller/StoreController.java',
    'package com.knowflow.store.controller; ProductMapper mapper;').length)
  assert.ok(boundaryViolations(java + 'store/PurchaseService.java',
    'package com.knowflow.store; com.knowflow.mapper.WalletMapper mapper;').length)
  assert.ok(boundaryViolations(java + 'common/Product.java',
    'package com.knowflow.common;').length)
  assert.ok(boundaryViolations(java + 'store/NewProduct.java',
    'package com.knowflow.store; @TableName("new_product") class NewProduct {}').length)
  assert.deepEqual(boundaryViolations(java + 'store/ProductService.java',
    'package com.knowflow.store; import com.knowflow.store.mapper.ProductMapper;'), [])
})

test('Learning content keeps legacy flashcard reads and delegates generated writes', () => {
  const file = java + 'service/impl/LearningContentServiceImpl.java'
  const source = 'private final LearningFlashcardMapper flashcardMapper;'
  assert.ok(boundaryViolations(file, source + ' flashcardMapper.insert(card);').length)
  assert.ok(boundaryViolations(file, source + ' flashcardMapper.updateById(card);').length)
  assert.deepEqual(boundaryViolations(file, source + ' flashcardMapper.selectList(query);'), [])
  assert.deepEqual(boundaryViolations(file, 'generatedFlashcards.saveAcceptedDraft(userId, draft);'), [])
})

test('Mastery has one projection writer and only its owner query may read persistence', () => {
  const projection = 'private KnowledgeMasteryMapper mastery;'
  const legacy = java + 'learning/actions/LearningContextService.java'
  assert.ok(boundaryViolations(java + 'controller/NewMasteryController.java', projection).length)
  assert.ok(boundaryViolations(legacy, projection + ' mastery.updateById(row);').length)
  assert.ok(boundaryViolations(java + 'service/impl/MasteryEngine.java', 'class MasteryEngine {}').length)
  assert.ok(boundaryViolations(legacy, projection + ' mastery.selectList(query);').length)
  assert.deepEqual(boundaryViolations(legacy, 'private MasteryReviewQuery mastery; mastery.listCandidates(user);'), [])
  const reviewQuery = java + 'mastery/service/MasteryReviewQueryService.java'
  assert.ok(boundaryViolations(reviewQuery, projection + ' mastery.updateById(row);').length)
  assert.deepEqual(boundaryViolations(reviewQuery, projection + ' mastery.selectList(query);'), [])
  assert.deepEqual(boundaryViolations(java + 'mastery/service/KnowledgeMasteryServiceImpl.java',
    projection + ' mastery.updateById(row);'), [])
})

test('quiz generation reads Knowledge sources through the governed contract', () => {
  const file = java + 'controller/admin/AdminQuizQuestionController.java'
  assert.ok(boundaryViolations(file, 'private DocDocumentMapper documents;').length)
  assert.ok(boundaryViolations(file, 'private DocCategoryMapper categories;').length)
  assert.deepEqual(boundaryViolations(file, 'private QuizSourceQueryService sources;'), [])
})

test('Common only accepts shared types and advice depends on the shared error contract', () => {
  assert.ok(boundaryViolations(java + 'common/Bad.java',
    'package com.knowflow.common; import com.knowflow.ai.AiException;').length)
  assert.ok(boundaryViolations(java + 'common/BadMapper.java',
    'package com.knowflow.common; interface BadMapper {}').length)
  assert.ok(boundaryViolations(java + 'exception/GlobalExceptionHandler.java',
    'package com.knowflow.exception; import com.knowflow.ai.AiException;').length)
  assert.deepEqual(boundaryViolations(java + 'common/Good.java',
    'package com.knowflow.common; import com.knowflow.common.error.ErrorCode;'), [])
})

test('Framework security uses the subject SPI and has no account persistence dependency', () => {
  const framework = java + 'framework/security/JwtAuthenticationFilter.java'
  assert.ok(boundaryViolations(framework,
    'package com.knowflow.framework.security; import com.knowflow.identity.mapper.SysUserMapper;').length)
  assert.ok(boundaryViolations(framework,
    'package com.knowflow.framework.security; import com.knowflow.identity.security.AccountAuthenticationSubjectResolver;').length)
  assert.deepEqual(boundaryViolations(framework,
    'package com.knowflow.framework.security; class JwtAuthenticationFilter { AuthenticationSubjectResolver resolver; }'), [])
})

test('Reward uses an Identity contract and avoids Learning internals', () => {
  const reward = java + 'reward/WalletService.java'
  assert.ok(boundaryViolations(reward,
    'package com.knowflow.reward; import com.knowflow.identity.mapper.SysUserMapper;').length)
  assert.ok(boundaryViolations(reward,
    'package com.knowflow.reward; class RewardEngine { com.knowflow.learning.actions.LearningWriteGuard guard; }').length)
  assert.deepEqual(boundaryViolations(reward,
    'package com.knowflow.reward; import com.knowflow.identity.contract.RewardAccountQuery;'), [])
})

test('Agent cannot use Reward and Store persistence directly', () => {
  const agent = java + 'agent/AgentRuntimeService.java'
  assert.ok(boundaryViolations(agent,
    'package com.knowflow.agent; import com.knowflow.reward.mapper.WalletMapper;').length)
  assert.ok(boundaryViolations(agent,
    'package com.knowflow.agent; import com.knowflow.store.entity.Order;').length)
  assert.deepEqual(boundaryViolations(agent,
    'package com.knowflow.agent; import com.knowflow.agent.tool.ToolRegistry;'), [])
})

import { directoryViolations as check } from './module-boundary.mjs'
const roleFile = (pkg, name) => `backend/src/main/java/com/knowflow/${pkg.replaceAll('.', '/')}/${name}.java`
test('新增根目录角色被拒绝，合法传统分层通过', () => {
 for (const [name,layer,decl] of [['ThingController','controller','@RestController class'],['ThingMapper','mapper','@Mapper interface'],['Thing','entity','@TableName("x") class'],['ThingService','service','class'],['ThingServiceImpl','service.impl','class'],['ThingDTO','dto','record'],['ThingVO','vo','record']]) {
  assert.ok(check(roleFile('learning',name), `package com.knowflow.learning; ${decl} ${name} {}`).length)
  assert.deepEqual(check(roleFile(`learning.${layer}`,name),`package com.knowflow.learning.${layer}; ${decl} ${name} {}`),[])
 }
})
test('保留技术专包及公开契约数据，注释不冒充注解',()=>{
 for(const [pkg,name,code] of [['framework.web','CorsConfig','class'],['voice.asr','Provider','interface'],['identity.contract','UserDTO','record']]) assert.deepEqual(check(roleFile(pkg,name),`package com.knowflow.${pkg}; ${code} ${name} {}`),[])
 assert.deepEqual(check(roleFile('ai.transport','Client'),'package com.knowflow.ai.transport; /* @Service */ class Client {}'),[])
 assert.ok(check(roleFile('ai.domain','Model'),'package com.knowflow.ai.domain; class Model {}').length)
})

test('V2路径继承历史检查，不扩大Catalog持久化例外', () => {
  assert.deepEqual(boundaryViolations(java + 'apps/service/UserAppService.java', 'package com.knowflow.apps.service; class UserAppService {}'), [])
  assert.ok(boundaryViolations(java + 'apps/service/UserAppService.java', 'package com.knowflow.apps.service; import com.knowflow.store.mapper.ProductMapper;').length)
  assert.ok(boundaryViolations(java + 'catalog/service/QuestionCatalogService.java', 'package com.knowflow.catalog.service; CodeQuestionMapper mapper;').length)
  assert.ok(boundaryViolations(java + 'catalog/service/NewService.java', 'package com.knowflow.catalog.service; QuizQuestionMapper mapper;').length)
})

test('公开契约不能伪装持久化类型，层包路径必须一致', () => {
 assert.ok(check(roleFile('learning.contract','BadMapper'), 'package com.knowflow.learning.contract; @Mapper interface BadMapper {}').length)
 assert.ok(check(roleFile('learning.contract','BadEntity'), 'package com.knowflow.learning.contract; @TableName("x") class BadEntity {}').length)
 assert.ok(check(roleFile('learning.service','GoodService'), 'package com.knowflow.learning; class GoodService {}').length)
})

test('未知业务模块不得承载角色，既有技术层保持合法', () => {
 assert.ok(check(roleFile('newbusiness.service','NewService'), 'package com.knowflow.newbusiness.service; @Service class NewService {}').length)
 assert.deepEqual(check(roleFile('framework.web','TechnicalController'), 'package com.knowflow.framework.web; class TechnicalController {}'), [])
})
