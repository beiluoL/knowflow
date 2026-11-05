import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
test('Learning context reads Mastery through its public projection', () => {
  const source = readFileSync('backend/src/main/java/com/knowflow/learning/actions/LearningContextService.java', 'utf8')
  assert.doesNotMatch(source, /KnowledgeMasteryMapper|LambdaQueryWrapper<KnowledgeMastery>/)
  assert.match(source, /MasteryReviewQuery/)
  assert.match(source, /mastery\.listCandidates\(user\)/)
})

test('Relocated classes have one source, matching package and unchanged persistence annotations', () => {
  const moves = ['PACKAGE_RELOCATION_2026-11-05.json', 'PACKAGE_RELOCATION_REMAINDER_2026-11-05.json'].flatMap(file => JSON.parse(readFileSync(`backend/src/test/resources/architecture/${file}`, 'utf8')))
  for (const move of moves) {
    const source = readFileSync(move.target, 'utf8')
    assert.match(source, new RegExp(`package ${move.newType.slice(0, move.newType.lastIndexOf('.')).replaceAll('.', '\\.')};`))
    assert.equal(existsSync(move.source), false, move.source)
    const annotations = text => text.match(/@(?:TableName|TableId|TableLogic|TableField|Select|Update|Insert|Delete|Transactional|RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\b(?:\((?:[^()"\n]|"(?:\\.|[^"\\])*")*\))?/g) || []
    assert.deepEqual(annotations(source), move.expectedAnnotations, move.target)
  }
})
