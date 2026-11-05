import test from 'node:test'
import assert from 'node:assert/strict'
import { jdbcBoundaryViolations } from './jdbc-boundary.mjs'
const service = 'backend/src/main/java/com/knowflow/techenglish/service/TechVocabService.java'
test('governed mapper-only service passes', () => {
  assert.deepEqual(jdbcBoundaryViolations(service, 'private final TechVocabTermMapper terms;'), [])
})
test('governed JDBC references fail including qualified forms', () => {
  for (const code of ['import org.springframework.jdbc.core.JdbcTemplate;', 'private org.springframework.jdbc.core.JdbcTemplate jdbc;', 'NamedParameterJdbcTemplate jdbc;']) {
    assert.equal(jdbcBoundaryViolations(service, code).length, 1)
  }
})
test('comments strings text blocks and test sources do not fail', () => {
  assert.deepEqual(jdbcBoundaryViolations(service, '// JdbcTemplate\nString note="JdbcTemplate"; /* JdbcTemplate */ String sql="""\nJdbcTemplate\n""";'), [])
  assert.deepEqual(jdbcBoundaryViolations(service.replace('/main/', '/test/'), 'JdbcTemplate jdbc;'), [])
})
test('non-governed locks upserts batches and DDL stay outside scope', () => {
  for (const sql of ['SELECT user_id FROM owners FOR UPDATE', 'INSERT INTO owners VALUES (?) ON DUPLICATE KEY UPDATE user_id=user_id', 'batchUpdate', 'CREATE TABLE IF NOT EXISTS owners']) {
    assert.deepEqual(jdbcBoundaryViolations('backend/src/main/java/com/knowflow/learning/actions/LearningWriteGuard.java', `JdbcTemplate jdbc; String sql="${sql}";`), [])
  }
})
