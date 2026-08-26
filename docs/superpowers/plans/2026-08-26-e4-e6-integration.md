# E4-E6 Integration Experiment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the vCampus project from user/course-only integration toward a five-module integration baseline, excluding final acceptance packaging materials.

**Architecture:** Keep shared protocol changes in `common`, module business contracts in each module, server-side authorization in message handlers, and Swing pages as lightweight client entry points. Use in-memory implementations for modules whose Access persistence is not yet complete, so the project remains runnable and testable.

**Tech Stack:** Java 8, Maven multi-module, Socket object messages, Swing GUI, JUnit tests, Access/UCanAccess planning.

---

### Task 1: Public Protocol Expansion

**Files:**
- Modify: `common/src/main/java/cn/vcampus/common/MessageType.java`
- Test: `common/src/test/java/cn/vcampus/common/MessageTest.java`

- [x] **Step 1: Add tests for new module messages**

Verify that student review, library, and store order-query message types are available.

- [x] **Step 2: Implement minimal enum additions**

Add `STUDENT_REVIEW`, `STORE_ORDER_QUERY`, and module management message types only where needed by this experiment.

### Task 2: Student Academic Review Endpoint

**Files:**
- Create: `server/src/main/java/cn/vcampus/server/StudentMessageHandler.java`
- Modify: `server/src/main/java/cn/vcampus/server/ServerApplication.java`
- Test: `server/src/test/java/cn/vcampus/server/StudentMessageHandlerTest.java`

- [x] **Step 1: Add handler tests**

Students can query their own review; forged student ID is forbidden; academic admin can review any student.

- [x] **Step 2: Implement handler and dispatch**

Use token identity for student self-service and `ACADEMIC_REVIEW` for academic administrators.

### Task 3: Library and Store In-Memory Baseline

**Files:**
- Modify/Create classes under `library/src/main/java/cn/vcampus/library/`
- Modify/Create classes under `store/src/main/java/cn/vcampus/store/`
- Create: `server/src/main/java/cn/vcampus/server/LibraryMessageHandler.java`
- Create: `server/src/main/java/cn/vcampus/server/StoreMessageHandler.java`
- Test: server and module tests

- [x] **Step 1: Add behavior tests**

Cover query, borrow/return, purchase/order query, and permission denial.

- [x] **Step 2: Implement minimal services and handlers**

Keep services in memory for now and enforce permissions in handlers.

### Task 4: Client Integration Baseline

**Files:**
- Create or modify client remote services and panels
- Modify: `client/src/main/java/cn/vcampus/client/view/MainFrame.java`
- Test: client view/navigation tests

- [x] **Step 1: Add tests for module page availability**

Ensure allowed roles can see appropriate module entries.

- [x] **Step 2: Implement simple Swing pages**

Show functional page shells for 学籍、图书馆、商店 and preserve existing course panel.

### Task 5: Documentation and Report

**Files:**
- Create: `docs/EXPERIMENT_E4_E6_REPORT.md`
- Modify: `docs/INTERFACES.md`
- Modify: `docs/MODULE_INTEGRATION_GUIDE.md`
- Modify: `docs/NEXT_EXPERIMENT_PLAN.md`

- [x] **Step 1: Update interface docs**

Document new message types, payloads, status codes and permissions.

- [x] **Step 2: Produce the new report**

Summarize completed work, remaining final-acceptance-only tasks, and next recommended actions.

### Task 6: Verification and Push

**Files:**
- All changed files

- [x] **Step 1: Run `mvn clean test`**

Expected: all modules pass.

- [x] **Step 2: Run `mvn package -DskipTests`**

Expected: client and server jars are generated.

- [x] **Step 3: Run jar smoke demo**

Expected: server starts, client `--demo` can register, login, authorize, logout, and old-token authorization is rejected.

- [x] **Step 4: Commit local changes**

Expected: branch contains one reviewable experiment commit.

- [ ] **Step 5: Push**

Push branch `codex/e4-e6-integration-experiment`.
