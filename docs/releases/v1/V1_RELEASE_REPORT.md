# V1 release closure — 2026-10-28

Status: NOT_READY / NO-GO. No commit, push, merge or deployment.

## Scope and workspace
Original release/v1.0-learning worktree remains locked to another chat and unchanged. User-authorized alternate worktree: `/Users/beiluo/.codex/worktrees/v1-release-closure/knowflow`, detached at `3216b609`. Only the known 888001 fixture correction was carried over; no bulk copy of main changes. F11 PR untouched.

## Changes and impact
- LearningCenterPage: feature-gated study-pack entry; existing route, styles and stores reused.
- application-staging.yml / frontend/.env.staging: six matching README feature flags, isolated MySQL and local AI settings; loopback/18080, host capabilities off, runtime DB switching off. Default production flags remain opt-in.
- Product Schema classes: explicit nullable timestamps and required timestamp defaults for MySQL 5.7 strict legacy timestamp mode; no historic grade updates. Base MySQL schema explicitly declares previously nullable timestamps. Existing full base SQL is fresh-database initialization, not a repeatable migration.
- LearningActionToolExecutor: accept the four existing planning DTO fields; unknown/identity fields still rejected. Actor/store/NextAction/Evidence algorithms unchanged.
- ContentStudioHashMapService: validate database/profile/actor before reading QA bundle, no authorization relaxation. Threadpool test uses synthetic packaged fixture rather than a personal absolute path. Human approval remains required; no content activation.
- ProductMySqlReleaseTest: explicit disposable-loopback MySQL only, repeat Schema application, frozen answers/zero scores/version/UTF-8/binary and uniqueness checks; verify-only restore mode.
- .github/workflows/v1-release.yml: Java17 full Maven, Node22.12, frontend gates, MySQL8 service, actual dump/restore and full-data comparison. Configuration is not a remote CI PASS.
- scripts/release/v1_fake_provider.py: loopback-only deterministic synthetic provider; no paid API, no model-quality claim.
- Known release files hidden by machine-local excludes are exposed using git intent-to-add only; no content commit or push. Evidence remains local; global documentation ignore rules are unchanged.

## Runtime evidence and limits
Local real MySQL **5.7.16**, isolated 13316 port and `/private/tmp/knowflow-v1-mysql/data`; created knowflow_v1_ci/restore/boot only. Legacy timestamp mode failures recorded, product compatibility fixed. Application startup additionally requires `explicit_defaults_for_timestamp=ON` on this 5.7 instance due to existing ChatManagementSchema; this matches MySQL8 default but does not certify legacy-mode startup. No business database accessed.

MySQL dump initially lost emoji under the 5.7 client default charset; explicit utf8mb4 fixed it. All-table data dump comparison after restore exited0. Application restart on boot database preserved pack, tutor and original quiz history. Historical verification covers synthetic product slices, not all 16 modules or real production history. No historical rescoring.

Chromium in-app browser: synthetic login; new entry; upload; 5-step pack generation; quiz confirmation; pack/card save; quiz67; diagnosis; micro100; original quiz retest100; original67 retained; Tutor SSE and refresh readback. Stable 1440/1280/1024/390 widths: scrollWidth1429/1269/1013/379. API smoke checks HTTP/body codes separately; **no browser Network capture**. Other-user pack404, anonymous401, owner200. Full Admin/revocation matrix, interview/adaptive full E2E are NOT_VERIFIED; real logout/relogin restoration passed.

## Handoff
Preserve alternate worktree changes and original fixture. Use the reports and evidence in this directory. Next required work: finish remaining MySQL8/permission/browser closure and obtain explicit push authorization to execute GitHub CI. Do not mark RC_READY based on local H2/5.7 or static YAML validation.

Additional actual validation: READER/EDITOR source membership HTTP200 for quiz/Tutor; revoked membership HTTP403/body403 for both. Fixture was set directly in disposable DB and original document category restored. Not a complete membership-management API test. Full Staging backup restored to knowflow_v1_stage_restore; independent18081 app login, pack SHA, three original history rows and Tutor binding equal to original18080. See evidence/restored-application-readback.json.

## Final verification
Final Maven1190 total/1180 passed/0 failures/0 errors/10 skipped, exit0; frontend1758 tests/type/default+staging build exit0. All21 changed/new files remain uncommitted at detached3216b609. Temporary services stopped; original release worktree and F11 untouched. G04/G05/G10 PASS; other aggregate gates remain NOT_VERIFIED. Final NOT_READY / NO-GO.
