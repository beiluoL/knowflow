<div align="right">
  <a href="README.md">简体中文</a> · <strong>English</strong>
</div>

# KnowFlow

**Turn your materials into a personal knowledge base you can question, practice, explain and review.**

KnowFlow is a Java + Vue knowledge-management and AI-learning project. It connects documents and notes with a learning tutor, private quizzes, FSRS review, interview practice and traceable learning evidence.

[Latest features](#latest-learning-features) · [Run locally](#run-locally) · [Feature flags](#optional-learning-feature-flags) · [Verification](#verification-and-boundaries)

## Product preview

**Notes workspace and AI learning assistant**

![KnowFlow notes workspace, three tutor modes and private quiz entry](assets/readme/notes-workspace.png)

| Learning action center | Learning evidence report |
| :---: | :---: |
| ![Next action and daily learning plan](assets/readme/learning-center.png) | ![Activities, original answers and retest evidence](assets/readme/evidence-report.png) |

*Screenshots show a local isolated demo with synthetic Java / MySQL materials. Records and scores are demonstration data, not evidence of real-user learning gains. Some features require the flags below. The current application interface is primarily Chinese.*

## A connected learning workflow

- **Manage materials:** documents, categories, search, favorites, reading progress and server-backed notes, with Markdown / visual editing, revision history and source references.
- **Learn from sources:** RAG answers with citations, questions about selected text, three tutor modes around saved notes, and explicit confirmation before adopting AI suggestions.
- **Practice:** private quizzes, rule-based question banks and exams, mistakes, two-minute challenges, same-question retests, variants and interleaved practice. Eligible objective questions use the existing grading service.
- **Choose the next step:** learning paths and prerequisites, a shared NextAction, tasks and time budgets, FSRS-6 flashcards and explainable personal path revisions.
- **Inspect the evidence:** graph mastery states, original answers, teach-back evaluations, hint conditions, human corrections and retest history. AI judgments, objective grades and activity counts remain distinct.

## Latest learning features

As of **October 27, 2026**, the following **16 features have working minimum slices**. This does not mean that every advanced specification or production-release requirement is complete.

| Stage | Implemented features |
| --- | --- |
| Materials → learning | **F03 Private quizzes:** generation from notes, preview and confirmation, frozen question versions and objective answers. **F16 Three-mode tutor:** Socratic, Direct and Teach-back modes with shared conversation history. **F17 AI study packs:** controlled TXT, Markdown and text-based PDF import, individual outputs, retries and adoption. |
| Reading → understanding | **F10 Selection Q&A:** text and revision context. **F05 Progressive hints:** incremental help, explicit reveal and usage history. **F02 Teach-back assessment:** original explanations, source quotes and subsequent retellings. **F07 Two-minute challenges:** short learning content, original single-question attempts and history. |
| Mistakes → another attempt | **F08 Error annotations:** grounded AI suggestions, UNKNOWN and human corrections. **F04 Same-question retests:** frozen retests after practice and historical comparisons. **F09 Question variants:** two scenarios with confirmed private question versions. **F13 Mock interviews:** general role directions, accessible topics, three text questions and relevant follow-ups. |
| Evidence → adjustment | **F06 Graph mastery:** entity / mastery views with evidence and retests. **F01 Adaptive paths:** sourced suggestions, locked chapters and personal order revisions. **F14 Plan reordering:** budgeted previews, lock / deadline protection, shorter allocations and deferral. **F15 Interleaved practice:** 4–6 questions alternating between two topics. **F12 Evidence reports:** original records from the last seven days, knowledge filters and source checks. |

**F11 Automated Java judging still requires a genuinely isolated execution sandbox and is not a delivered capability.** Ordinary host execution is not safe execution of untrusted code.

## Other core capabilities

- **Collaborative knowledge bases:** OWNER / EDITOR / READER roles and server-side resource authorization.
- **AI conversations:** multi-turn history, streamed responses, configurable models and citations. AI calls require usable provider credentials.
- **Review and planning:** FSRS-6 flashcards, mistakes, learning goals, chapter progress, task lists, calendars and Pomodoro sessions.
- **Knowledge workbench:** organization, recall practice, Cornell notes, Feynman explanations, mind maps and drawing tools.
- **Coding assistance:** problem and editor interfaces, tool calls and agent workflows. Host tools and execution capabilities have separate flags and authorization requirements.

## Main routes

| Page | Route |
| --- | --- |
| Notes and learning assistant | `/notes` |
| Learning center / paths | `/learning/center` · `/learning/paths` |
| Daily plan | `/learning/today-plan` |
| Flashcard review | `/learning/flashcards` |
| Question bank and interleaved practice | `/learning/quiz` |
| Knowledge graph | `/learning/knowledge-graph` |
| Learning evidence report | `/learning/report` |
| Mock interviews | `/interview` |
| AI study packs, when enabled | `/learning/study-packs` |

## Technology

| Layer | Stack |
| --- | --- |
| Backend | Java 17, Spring Boot 3.2.5, Spring Security, JWT, MyBatis Plus, JdbcTemplate |
| Frontend | Vue 3, TypeScript, Vite, Pinia, Vue Router, Tailwind CSS |
| Editing and visualization | Milkdown, Markdown, Mermaid, Dagre, Vue Flow |
| Data and learning | H2 for development, MySQL support, FSRS-6, original learning events and evidence receipts |
| AI and document processing | Configurable model services, SSE, RAG, Apache Tika |

## Run locally

Use **JDK 17, Maven, and Node.js `^20.19.0` or `>=22.12.0`**, following the current Vite engines requirement. Commands below use Bash. Check that ports 8080 and 5173 are available before starting.

```bash
git clone https://github.com/beiluoL/knowflow.git
cd knowflow
```

Start the backend:

```bash
cd backend
export JWT_SECRET="$(openssl rand -hex 32)"
export KNOWFLOW_AI_CAPABILITY_ENABLED=false
mvn spring-boot:run
```

Start the frontend in another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. The default H2 database is in-memory: data does not survive a restart. Configure file-backed H2 or MySQL separately when persistence is needed. Do not use a production database for development checks.

Basic browsing, rule-based exercises and review do not require AI. To enable AI, configure your own model service and credentials and enable the AI capability switch. See the [local configuration example](backend/src/main/resources/application-local.example.yml). Keep real `application-local.yml`, `.env` files and credentials out of Git.

## Optional learning feature flags

New learning modules are disabled by default. Enable the matching backend and frontend flags, together with their dependencies.

| Module | Backend property | Frontend environment variable |
| --- | --- | --- |
| Private quizzes | `knowflow.product.private-quiz.enabled` | `VITE_PRODUCT_PRIVATE_QUIZ` |
| Three-mode tutor | `knowflow.product.tutor.enabled` | `VITE_PRODUCT_TUTOR` |
| Study packs | `knowflow.product.study-pack.enabled` | `VITE_PRODUCT_STUDY_PACK` |
| Hints / teach-back / micro challenges | `knowflow.product.tutor-coaching.enabled` | `VITE_PRODUCT_TUTOR_COACHING` |
| Error annotations / retests / variants / interviews | `knowflow.product.interview-learning.enabled` | `VITE_PRODUCT_INTERVIEW_LEARNING` |
| Graph / paths / reordering / interleaving / reports | `knowflow.product.adaptive-learning.enabled` | `VITE_PRODUCT_ADAPTIVE_LEARNING` |

Set backend properties to `true` in local configuration or startup arguments, and set the frontend variables to `true` before restarting Vite. The complete workflow needs the relevant modules together; a frontend flag never bypasses backend checks. AI modules also need `knowflow.ai.capability.enabled` and a configured model.

## Verification and boundaries

Local verification snapshot, October 27, 2026:

- Frontend: **237 test files, 1,750 passing tests**, with type checking and production build passing.
- Fourth-batch backend: **47 affected-scope tests** and **12 final hardening tests** passed. These groups overlap and must not be added as independent coverage.
- The 16 learning features were verified in separate isolated batches. All five fourth-batch slices passed real-browser operations, persistence, refresh and re-login checks. The fourth batch made zero new cloud-model calls.
- The latest full backend run is a historical third-batch result: 1,175 tests, with 1,165 passing, one existing `888001` fixture error and nine skipped. A fully passing backend suite is not claimed.

```bash
# Run from the repository root
(cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build)

# Related backend contracts
(cd backend && mvn -Dtest=ProductGraphInsightHttpTest,ProductAdaptivePathHttpTest,ProductSmartPlanHttpTest,ProductInterleavedHttpTest,ProductEvidenceReportHttpTest test)
```

AI ratings are not objective exam grades. An unproven original first attempt stays UNKNOWN; incompatible conditions must not produce an improvement claim. Disabled Host / Judge capabilities are not a production sandbox.

**Global release status: NOT_READY.** MySQL / production migrations, deployment and release gates remain unverified; T03/T04 are incomplete. Current development focuses on the regular-user PC interface. Narrow-screen checks do not constitute a separate mobile-client release.

## Repositories and maintainer

[GitHub](https://github.com/beiluoL/knowflow) · [Gitee](https://gitee.com/beiluol/knowflow) · [Beiluo — Java Backend × AI Application Engineer](https://github.com/beiluoL)
