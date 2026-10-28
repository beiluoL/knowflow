# V1 test evidence — 2026-10-28

Evidence paths are relative to this directory, local only.

- Initial Maven attempt interrupted (130): missing synthetic JWT environment; overlapping Maven compilation also invalidated class loading. Not an acceptance result.
- First valid complete suite:1190 tests,4 failures,2 errors,10 skipped, exit1 (`evidence/maven-final.log`). Four failures: plan DTO rejected by tool whitelist (three action tests and one conversation-title test). Two errors: QA guard constructor eagerly reads absent private package. No assertions weakened.
- Fixed targeted:10/10 exit0 (`regressions.log`), then8/8 exit0 including conversation-title and real MySQL restore (`final-targeted.log`).
- Final frontend:238 files /1758 tests pass, exit0; vue-tsc noEmit exit0; build exit0; staging build exit0 (`vitest-final.log`, `types-final.log`, `build-final.log`, `build-staging-final.log`). Node22.11 warns below Vite minimum; CI pins22.12. Shared local node_modules used; clean npm-ci remote not verified.
- MySQL5.7.16: product replay1/1 exit0 (`mysql-fixed2.log`); earlier failures preserved. Fresh base schema exit0 (`mysql-base-fresh.log`). Restored selected rows test1/1 exit0 (`final-targeted.log`); full data comparison exit0, before/after dumps at `/private/tmp/v1-before.sql` and `/private/tmp/v1-after.sql`. utf8mb4 backup `evidence/mysql-backup.sql` contains synthetic data only.
- Browser and API scope: see release report; API result `api-smoke.json`, restarted readback `restart-readback.json`; provider and staging logs retained. Native file picker stalled before returning; no success inferred during that wait.
- Workflow parsed by Ruby YAML; Python provider compiled; git diff --check exit0. GitHub Actions not run. No MySQL8 claim.

Full suite after business fixes:1190 total,1180 passed,0 failures,0 errors,10 skipped, exit0 (`maven-complete.log`).
Portable Fixture regression:6/6 exit0 (`portable-fixture-final.log`); initial synthetic fixture lacked explicit nullable parentKey, corrected to the existing JSON contract without modifying assertions.
Final full suite including portable fixtures:1190 total,1180 passed,0 failures,0 errors,10 skipped, exit0 (`maven-release-final.log`), finished2026-10-28 12:51:40 CST. Command: JWT_SECRET=<synthetic-test-key> mvn -f backend/pom.xml test. Skips retain opt-in browser/real-MySQL conditions; no exclusions added.

Independent bounded code review: no actionable defect found; existing-table column definitions are not changed by CREATE IF NOT EXISTS. MySQL8, all-module historical migration and production readiness remain unverified. Workflow YAML and all embedded shell blocks parse successfully; this is static validation only.

Final base MySQL schema import after Diff correction: exit0 (`mysql-base-final.log`). Original CURRENT_TIMESTAMP defaults preserved; only six previously implicit nullable timestamp columns changed. Runtime services created by this run were stopped; port check confirms no listeners on13316/15173/18080/18081/19091. Original release worktree still has only the exact unchanged888001 fixture diff. git diff --check exit0; cached content diff empty.

## 后续分支统一测试（2026-10-28）
首次整合全量1209项中，作者预览2项因缺少可移植作者包失败；完整合成作者包及显式author-root补齐，原9项回归通过。最终完整Maven1209项1199通过、0失败、0错误、10跳过，退出0（13:44:16结束）。前端241文件1768项、vue-tsc、build均退出0；启动器25项通过。原断言全部保留。日志位于/private/tmp/knowflow-main-consolidation-20261028/maven-final.log、vitest-all.log、types.log、build.log、launcher-tests.log。此为合并版本的新结果，不覆盖此前历史记录，也不等同MySQL8或完整Staging验收。
