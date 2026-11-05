import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
test('learning recent activity does not access Exam or Interview persistence', () => {
 const source = readFileSync('backend/src/main/java/com/knowflow/learning/actions/LearningContextService.java','utf8');
 assert.doesNotMatch(source,/com\.knowflow\.(exam|interview)\.(mapper|entity)\./);
 assert.match(source,/RecentExamActivityQuery/);
 assert.match(source,/RecentInterviewActivityQuery/);
});
