# V1 release gates — 2026-10-28

NOT_READY / NO-GO. Local slices do not certify the whole gate.

| Gate | Status | Evidence / remaining scope |
|---|---|---|
| G01 product integration | NOT_VERIFIED | Entry and pack/quiz/tutor chain checked; all16 not exhaustively accepted |
| G02 MySQL | NOT_VERIFIED | Real5.7 synthetic product replay passed; MySQL8 and all-module history pending |
| G03 backup restore | NOT_VERIFIED | Local5.7 dump/restore full-data comparison passed; full historical/target-version drill pending |
| G04 Maven | PASS | Final1190 total/1180 passed/0 failures/0 errors/10 skipped; exit0 |
| G05 frontend | PASS | Final1758 tests, type check and default/staging builds exit0 |
| G06 Staging | NOT_VERIFIED | Real5.7 core subset passed; interview/adaptive/permission full flows pending |
| G07 security | NOT_VERIFIED | Pack isolation checked, host capability off; full role/revocation matrix pending |
| G08 CI | NOT_VERIFIED | YAML parsed locally; no remote run/push |
| G09 operations | NOT_VERIFIED | App restart/readback passed; production privileges/CORS/rollback/restore readiness incomplete |
| G10 scope | PASS | F11 excluded; no commit/push/merge/deploy |
