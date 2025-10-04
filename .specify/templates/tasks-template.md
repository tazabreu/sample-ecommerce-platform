# Tasks: [FEATURE NAME]

**Input**: Design documents from `/specs/[###-feature-name]/`
**Prerequisites**: plan.md (required), research.md, data-model.md, contracts/

## Execution Flow (main)
```
1. Load plan.md from feature directory
   → If not found: ERROR "No implementation plan found"
   → Extract: tech stack, libraries, structure
2. Load optional design documents:
   → data-model.md: Extract entities → model tasks
   → contracts/: Each file → contract test task
   → research.md: Extract decisions → setup tasks
3. Generate tasks by category:
   → Setup: project init, dependencies, linting
   → Tests: contract tests, integration tests
   → Core: models, services, CLI commands
   → Integration: DB, middleware, logging
   → Polish: unit tests, performance, docs
4. Apply task rules:
   → Different files = mark [P] for parallel
   → Same file = sequential (no [P])
   → Tests before implementation (TDD)
5. Number tasks sequentially (T001, T002...)
6. Generate dependency graph
7. Create parallel execution examples
8. Validate task completeness:
   → All contracts have tests?
   → All entities have models?
   → All endpoints implemented?
9. Return: SUCCESS (tasks ready for execution)
```

## Format: `[ID] [P?] Description`
- **[P]**: Can run in parallel (different files, no dependencies)
- Include exact file paths in descriptions

## Path Conventions
- **Single project**: `src/main/java/`, `src/test/java/` at repository root
- **Web app**: `backend/src/main/java/`, `frontend/src/`
- **Mobile**: `api/src/main/java/`, `ios/` or `android/`
- Paths shown below assume single Spring Boot project - adjust based on plan.md structure

## Phase 3.1: Setup
- [ ] T001 Create project structure per implementation plan
- [ ] T002 Initialize Spring Boot project with Maven/Gradle dependencies
- [ ] T003 [P] Configure Google Java Style Guide and static analysis (SonarQube)
- [ ] T004 [P] Configure Resilience4j circuit breakers and retry policies
- [ ] T005 [P] Setup Spring Boot Actuator health checks and Prometheus metrics

## Phase 3.2: Tests First (TDD) ⚠️ MUST COMPLETE BEFORE 3.3
**CRITICAL: These tests MUST be written and MUST FAIL before ANY implementation**
- [ ] T006 [P] Contract test POST /api/v1/users in src/test/java/contracts/UsersPostContractTest.java
- [ ] T007 [P] Contract test GET /api/v1/users/{id} in src/test/java/contracts/UsersGetContractTest.java
- [ ] T008 [P] Integration test user registration in src/test/java/integration/RegistrationIntegrationTest.java
- [ ] T009 [P] Integration test auth flow in src/test/java/integration/AuthFlowIntegrationTest.java

## Phase 3.3: Core Implementation (ONLY after tests are failing)
- [ ] T010 [P] User entity with Bean Validation in src/main/java/models/User.java
- [ ] T011 [P] UserRepository interface in src/main/java/repositories/UserRepository.java
- [ ] T012 [P] UserService with @Transactional in src/main/java/services/UserService.java
- [ ] T013 POST /api/v1/users @RestController endpoint
- [ ] T014 GET /api/v1/users/{id} @RestController endpoint
- [ ] T015 Input validation with @Valid annotations
- [ ] T016 Error handling with @ControllerAdvice and structured logging

## Phase 3.4: Integration
- [ ] T017 Configure Spring Data JPA with PostgreSQL
- [ ] T018 Configure Redis caching with @Cacheable
- [ ] T019 Implement Spring Security with OAuth 2.0 Resource Server
- [ ] T020 Add request/response correlation IDs and MDC logging
- [ ] T021 Configure CORS and security headers (Spring Security)

## Phase 3.5: Polish
- [ ] T022 [P] Unit tests for validation logic in src/test/java/unit/ValidationTest.java
- [ ] T023 Performance tests with Gatling (target <200ms p95)
- [ ] T024 [P] Update OpenAPI documentation via SpringDoc annotations
- [ ] T025 Refactor to remove duplication (SonarQube compliance)
- [ ] T026 Run manual-testing.md and verify all health checks

## Dependencies
- Setup (T001-T005) before tests
- Tests (T006-T009) before implementation (T010-T016)
- T010 blocks T011, T012
- T012 blocks T013, T014
- Implementation before integration (T017-T021)
- Integration before polish (T022-T026)

## Parallel Example
```
# Launch T006-T009 together:
Task: "Contract test POST /api/v1/users in src/test/java/contracts/UsersPostContractTest.java"
Task: "Contract test GET /api/v1/users/{id} in src/test/java/contracts/UsersGetContractTest.java"
Task: "Integration test registration in src/test/java/integration/RegistrationIntegrationTest.java"
Task: "Integration test auth in src/test/java/integration/AuthFlowIntegrationTest.java"
```

## Notes
- [P] tasks = different files, no dependencies
- Verify tests fail before implementing
- Commit after each task
- Avoid: vague tasks, same file conflicts

## Task Generation Rules
*Applied during main() execution*

1. **From Contracts**:
   - Each contract file → contract test task [P]
   - Each endpoint → implementation task
   
2. **From Data Model**:
   - Each entity → model creation task [P]
   - Relationships → service layer tasks
   
3. **From User Stories**:
   - Each story → integration test [P]
   - Quickstart scenarios → validation tasks

4. **Ordering**:
   - Setup → Tests → Models → Services → Endpoints → Polish
   - Dependencies block parallel execution

## Validation Checklist
*GATE: Checked by main() before returning*

- [ ] All contracts have corresponding tests
- [ ] All entities have model tasks
- [ ] All tests come before implementation
- [ ] Parallel tasks truly independent
- [ ] Each task specifies exact file path
- [ ] No task modifies same file as another [P] task