# UC Executor Workflow — JSON Spec to DDD Code

> **Version**: 1.0 | **Date**: 2026-02-09
> **Scope**: Generate production-grade DDD code from `.dev/specs/{aggregate}/usecase/*.json`

---

## Overview

This workflow generates Java DDD code from JSON UseCase specifications.
It enforces quality gates: Gate 1 (Dual-Profile Test) and Gate 2.5 (Deterministic Review).
Test scenarios come from the JSON spec's `testScenarios[]` field.

---

## Phase 0: Preparation

### Step 0.1 — Verify Project Initialization

Check critical infrastructure exists:
- `DomainEventMapperConfig.java` (ADR-047)
- `SharedInfrastructureConfig.java` (InMemory)
- `DateProvider.java`
- If missing → auto-trigger `/init-project`

### Step 0.2 — Read JSON Spec & Detect Type

```
JSON Spec Type Detection Rules:
───────────────────────────────
1. Has "useCase" + "domainEvent"   → COMMAND (create/delete/state-change)
2. Has "query" + "projections"     → QUERY
3. Has "reactor" + "events"        → REACTOR
4. Has "pattern" + "entity"        → PATTERN (ReadOnly Entity Proxy/SpecialCase)
5. Has adapter/ path prefix        → NOT SUPPORTED (controller generated from UC)
```

Read the spec file and extract:
- `specType`: one of COMMAND / QUERY / REACTOR
- `aggregateName`: from `spec.aggregate` or inferred from directory path
- `packageBase`: from `.dev/project-config.json` → `architecture.basePackage`

### Step 0.3 — Validate Required Fields (⛔ BLOCKING)

**Per spec type, verify mandatory fields exist:**

COMMAND has two sub-types based on the `method` field:

| Sub-Type | `method` Pattern | Example | Description |
|----------|-----------------|---------|-------------|
| **Constructor** | contains "constructor" | `"Sprint constructor"` | Creates new aggregate instance |
| **Method-call** | contains "." | `"Sprint.start(...)"` | Calls method on existing aggregate |

| Spec Type | Required Fields |
|-----------|----------------|
| COMMAND (Constructor) | `useCase`, `input`, `aggregate`, `domainEvent`, `aggregates`, `domainEvents` |
| COMMAND (Method-call) | `useCase`, `input`, `aggregate`, `domainEvent`, `domainEvents` |
| QUERY | `query`, `input`, `output`, `dependencies`, `projections` |
| REACTOR | `reactor`, `events`, `dependencies` |
| PATTERN | `pattern`, `entity`, `aggregate`, `packageInfo`, `methodRules`, `testScenarios` |

> **Note on `domainEvent` field format**: Accepts both string (`"SprintEvents.X"`)
> and array (`["SprintEvents.X"]`). Normalize to array before processing.

**If missing required fields → STOP. Report missing fields and ask user to fix the spec.**

### Step 0.4 — Read project-config.json

Extract:
- `basePackage` — Java package root
- `dualProfileSupport` — whether Gate 1 applies
- `architecture.outbox` — whether outbox infra is needed

---

## Phase 1: Learning — JIT Pattern Loading (DO NOT SKIP!)

### Step 1.1 — Read Critical Rules

```
LOAD: references/rules/critical-rules.md
```

These ~195 lines contain 27 FORBIDDEN + 16 ALWAYS REQUIRED rules that apply
regardless of spec format. They are the shared quality baseline.

### Step 1.2 — Read Field Mapping Reference

```
LOAD: references/uc-executor/json-to-pattern-mapping.md
```

This maps JSON spec fields to the pattern files and code generation targets.

---

## Phase 2: Code Generation (per Spec Type)

### ═══ COMMAND UseCase Path ═══

#### Step 4.1 — Generate Aggregate + Events

```
LOAD_PATTERNS:
  - references/patterns/domain/aggregate.md
  - references/patterns/domain/domain-event.md
  - references/patterns/domain/value-object.md (if spec has valueObjects[])
  - references/patterns/domain/entity.md (if spec has entities[])
```

**SOURCE**: `spec.aggregates[]`, `spec.domainEvents[]`, `spec.entities[]`, `spec.valueObjects[]`, `spec.enums[]`

**Field mapping**:
- `spec.aggregates[0].attributes[]` → Aggregate fields
- `spec.aggregates[0].attributes[].constraint` → field initialization rules
- `spec.domainEvents[].attributes[]` → Event record fields
- `spec.enums[]` → Enum definitions

**CRITICAL checks**:
- Aggregate MUST extend `EsAggregateRoot<AggregateEvents>`
- Events MUST be a `sealed interface` with `static mapper()` method (ADR-047)
- `MAPPING_TYPE_PREFIX` constant required
- `DateProvider.now()` for timestamps, NOT `Instant.now()`
- State set ONLY in `when()` method (Event Sourcing golden rule)

#### Step 4.1.5 — Generate Contract Tests (if applicable)

```
LOAD_PATTERNS:
  - references/patterns/testing/contract-test.md
```

**SOURCE**: `spec.constructorPreconditions[]`, `spec.constructorPostconditions[]`

**Generate if**: spec has either `constructorPreconditions` or `constructorPostconditions`.
Skip if neither field is present.

#### Step 4.2 — Generate UseCase Interface + Service

```
LOAD_PATTERNS:
  - references/patterns/usecase/command.md
```

**SOURCE**: `spec.useCase`, `spec.input[]`, `spec.output`, `spec.aggregate`, `spec.repository`

**Field mapping**:
- `spec.useCase` → class name (e.g., `CreateProductUseCase`)
- `spec.input[]` → `Input` inner class fields
- `spec.output` → return type description
- `spec.aggregate` + `spec.method` → service implementation logic
- `spec.repository` → injected dependency

**CRITICAL checks**:
- Input MUST be `class` (not record) with `create()` factory
- Output uses `CqrsOutput<?>` wildcard
- Service registered via `@Bean` in Config (NOT `@Component`)
- Command UseCase uses blanket catch → `UseCaseFailureException`

#### Step 4.3 — Generate UseCase Test

```
LOAD_PATTERNS:
  - references/patterns/testing/usecase-test.md
```

**SOURCE**: `spec.testScenarios[]` (if present) or auto-generate from pre/postconditions

**If `testScenarios[]` exists**: use scenario names and assertions directly.
**If no `testScenarios[]`**: generate basic happy-path + precondition-violation tests.

**CRITICAL checks**:
- `@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)`
- `setUpEventCapture()` in `@BeforeEach`, `tearDownEventCapture()` in `@AfterEach`
- NO `@ActiveProfiles` annotation
- Test uses `@Autowired` for repository (NOT hardcoded)
- ezSpec public void methods with `Consumer<ScenarioEnvironment>` signature

#### Step 4.4 — Generate Infrastructure

```
SCOPE CHECK (⛔ MUST evaluate before generating):
  If user specifies --only-inmemory / "只產生 in-memory" / "only inmemory":
    → SCOPE = inmemory-only
    → Skip: {Aggregate}OrmClient, {Aggregate}OutboxRepositoryConfig, SharedOutboxConfig
    → Skip: OutboxTestSuite (Phase 4)
  Else (default):
    → SCOPE = dual-profile
    → Generate all infrastructure files

LOAD_PATTERNS:
  - references/patterns/infrastructure/mapper.md
  - references/patterns/infrastructure/persistent-object.md
  - references/patterns/infrastructure/config.md
  - references/patterns/infrastructure/outbox.md        (skip if SCOPE = inmemory-only)
```

**SOURCE**: `spec.aggregates[0].attributes[]` for PO fields, `spec.domainEvents[]` for event handling

**Generate (dual-profile, default)**:
1. `{Aggregate}Data.java` — JPA persistent object (`jakarta.persistence`, NOT `javax`)
2. `{Aggregate}Mapper.java` — OutboxMapper with `toDomain()` and `toData()`
3. `{Aggregate}OrmClient.java` — extends `SpringJpaClient`
4. InMemory Config — `InMemoryOrmDb` with Map constructor, same bean name
5. Outbox Config — OutboxMapper, OrmClient, repository bean
6. UseCase Config — `@Configuration` without `@Profile`

**Generate (inmemory-only)**:
1. `{Aggregate}Data.java` — still needed (OutboxRepository assembly chain uses it)
2. `{Aggregate}Mapper.java` — still needed (OutboxRepository assembly chain uses it)
3. ~~`{Aggregate}OrmClient.java`~~ — **SKIP** (Outbox JPA client)
4. InMemory Config — `InMemoryOrmDb` with Map constructor, same bean name
5. ~~Outbox Config~~ — **SKIP**
6. UseCase Config — `@Configuration` without `@Profile`
7. SharedInfrastructureConfig — still needed (InMemory beans)
8. ~~SharedOutboxConfig~~ — **SKIP**

#### Step 4.5 — Generate Controller (if `--controller` flag)

```
CONDITION: Only execute if --controller flag is present.
SKIP for REACTOR spec type (Reactors have no REST endpoint).

LOAD_PATTERNS:
  - references/patterns/adapter/controller.md
```

**SOURCE**: `spec.useCase` or `spec.query`, `spec.input[]`, `spec.output`, JSON spec `controller` field (optional)

**Pre-check**:
- UseCase interface exists (generated in Step 4.2)
- UseCaseConfig has `@Bean` for the UseCase

**Generate**:
1. `{UseCase}Controller.java` — in `{aggregate}/adapter/in/rest/springboot/`
2. `ApiError.java` — in `common/adapter/in/rest/springboot/` (shared, skip if exists)

**Field mapping** (from JSON spec or UseCase interface):
- `spec.useCase` / `spec.query` → Controller class name
- `spec.input[]` → Request DTO inner class fields
- `spec.output` → Response DTO inner class fields
- `spec.controller.endpoint` → `@RequestMapping` path (default: `/v1/api/{aggregatePlural}`)
- `spec.controller.httpMethod` → `@PostMapping` / `@GetMapping` etc. (default: infer from spec type)

**CRITICAL checks**:
- Constructor injection ONLY (no `@Autowired`)
- `/v1/api` prefix on `@RequestMapping`
- Request/Response as `static` inner classes
- `@Valid` on `@RequestBody`
- Thin controller: delegate to UseCase, no business logic

#### Step 4.6 — Generate Controller Test (if `--controller` flag)

```
CONDITION: Only execute if --controller flag is present AND Step 4.5 completed.

LOAD_PATTERNS:
  - references/patterns/testing/controller-test.md
```

**SOURCE**: Generated Controller from Step 4.5

**Generate**:
1. `{UseCase}ControllerTest.java` — MockMvc unit test (`@WebMvcTest`)
2. `{UseCase}ControllerIntegrationTest.java` — REST Assured integration test (`@SpringBootTest`)

**CRITICAL checks**:
- No `@ActiveProfiles` annotation
- `@MockBean` for UseCase dependencies
- `/v1/api` prefix in test request paths
- Minimum coverage: success (2xx) + validation (400) + error (500) cases
- BOTH test types must be generated and must pass

**Test execution**:
```bash
mvn test -Dtest={UseCase}ControllerTest -q
mvn test -Dtest={UseCase}ControllerIntegrationTest -q
```

---

### ═══ QUERY UseCase Path ═══

#### Step 4.1 — Generate Projection

```
LOAD_PATTERNS:
  - references/patterns/usecase/query.md
```

**SOURCE**: `spec.projections[]`, `spec.dataTransferObjects[]`

**Generate**:
1. Projection interface (if new) — `extends Projection<Input, Output>`
2. InMemory implementation
3. Outbox (JPA) implementation
4. DTO records (if new)

**CRITICAL checks**:
- Projection beans MUST be in profile-specific config (InMemory/Outbox), NOT in UseCaseConfig
- Query UseCase does NOT blanket catch (no `UseCaseFailureException`)

#### Step 4.2 — Generate Query UseCase + Service

```
LOAD_PATTERNS:
  - references/patterns/usecase/query.md
```

**SOURCE**: `spec.query`, `spec.input[]`, `spec.output`, `spec.dependencies[]`

**Field mapping**:
- `spec.query` → class name (e.g., `GetProductsUseCase`)
- `spec.dependencies[]` → injected dependencies

#### Step 4.3 — Generate Mapper (CRITICAL: usecase.port package)

**SOURCE**: `spec.mappers[]`

**CRITICAL**: Respect the `"location"` field in each mapper definition.
Mappers belong in `usecase.port` package, NOT in `adapter` package.

#### Step 4.4 — Generate Test

```
LOAD_PATTERNS:
  - references/patterns/testing/usecase-test.md
```

**SOURCE**: `spec.testScenarios[]`, `spec.testDataSetup`

Same critical checks as Command test generation.

---

### ═══ REACTOR Path ═══

#### Step 4.1 — Generate Reactor Interface + Service

```
LOAD_PATTERNS:
  - references/patterns/usecase/reactor.md (if exists)
  - references/patterns/usecase/command.md (for service pattern)
```

**SOURCE**: `spec.reactor`, `spec.service`, `spec.events[]`, `spec.dependencies[]`, `spec.actions[]`

**Field mapping**:
- `spec.reactor` → interface name
- `spec.service` → service class name
- `spec.interface_location` / `spec.service_location` → package paths
- `spec.events[]` → listened event type
- `spec.actions[]` → service implementation steps

#### Step 4.2 — Generate Inquiry (if cross-aggregate)

**SOURCE**: `spec.inquiries[]`

**Generate if**: spec has `inquiries[]` field with cross-aggregate queries.

#### Step 4.3 — Generate Test

**SOURCE**: `spec.testScenarios[]`, `spec.errorHandling[]`

Same critical checks as Command test generation.

---

### ═══ PATTERN: ReadOnly Entity Path ═══

> Triggered when spec has `"pattern"` + `"entity"` top-level fields.
> Generates a read-only proxy/wrapper for a child Entity inside an Aggregate Root.
> Does NOT generate UseCase, Repository, or Domain Events — only structural classes.

#### Step P.1 — Generate Interface

**SOURCE**: `spec.entity.interface`, `spec.interfaceDefinition`, `spec.packageInfo.entityPackage`

**Generate**:
- `{IEntity}.java` in `{entityPackage}` (e.g., `ITask.java` in `tw.teddysoft.aiscrum.pbi.entity`)

**Template**:
```java
package {spec.packageInfo.entityPackage};

// imports for all method return types and parameter types

public interface {spec.entity.interface} {

    // --- query methods (from spec.interfaceDefinition.queryMethods[]) ---
    {returnType} {methodName}();   // one per entry

    // --- command methods (from spec.interfaceDefinition.commandMethods[]) ---
    void {commandMethodName}({params});   // one per entry
}
```

**CRITICAL**:
- Include ALL methods from `spec.interfaceDefinition.queryMethods[]` AND `spec.interfaceDefinition.commandMethods[]`
- Imports: use `spec.valueObjects[]`, `spec.enums[]`, `spec.entities[]` to resolve types
- The real Entity class (`spec.entity.name`) must be updated to `implements {spec.entity.interface}`

#### Step P.2 — Generate ReadOnly Proxy Class

**SOURCE**: `spec.entity.readOnlyName`, `spec.methodRules`, `spec.constructorRule`, `spec.packageInfo.entityPackage`

**Generate**:
- `{ReadOnlyEntity}.java` in `{entityPackage}` (e.g., `ReadOnlyTask.java`)

**Template**:
```java
package {spec.packageInfo.entityPackage};

import java.util.Collections;
// other imports

public class {spec.entity.readOnlyName} implements {spec.entity.interface} {

    private final {spec.entity.interface} real;

    public {spec.entity.readOnlyName}({spec.entity.interface} real) {
        // from spec.constructorRule.implementation[]
        requireNotNull(real, "real must not be null");
        this.real = real;
    }

    // --- command methods → throw UnsupportedOperationException ---
    // (from spec.methodRules.commands[])
    @Override
    public void {commandMethod}({params}) {
        throw new UnsupportedOperationException("{spec.methodRules.commands[i].implementation message}");
    }

    // --- queries returning immutable (VO / Enum / String) → delegate ---
    // (from spec.methodRules.queriesReturningImmutable[])
    @Override
    public {returnType} {queryMethod}() {
        return real.{queryMethod}();
    }

    // --- queries returning collection → unmodifiable delegation ---
    // (from spec.methodRules.queriesReturningCollection[])
    @Override
    public {CollectionType}<{ElementType}> {queryMethod}() {
        return Collections.unmodifiable{CollectionVariant}(real.{queryMethod}());
    }
}
```

**CRITICAL**:
- `spec.methodRules.commands[].implementation` contains the exact exception message string — use it verbatim
- `spec.methodRules.queriesReturningImmutable[].implementation` is always `return real.{method}()`
- `spec.methodRules.queriesReturningCollection[].implementation` contains the `Collections.unmodifiable*` call
- `spec.methodRules.queriesReturningEntity[]` — if non-empty, wrap returned entity with its ReadOnly version

#### Step P.3 — Report Aggregate Root Changes (⚠️ MANUAL ACTION REQUIRED)

**SOURCE**: `spec.aggregateRootChanges`

After generating the files, print the following notice:

```
╔══════════════════════════════════════════════════════════════╗
║   ⚠️  AGGREGATE ROOT MANUAL CHANGES REQUIRED                ║
╠══════════════════════════════════════════════════════════════╣
║  The following changes to {spec.aggregate}.java are needed:  ║
║                                                              ║
║  (for each entry in spec.aggregateRootChanges.methods[]):    ║
║    • Change: {original signature}                            ║
║    • To:     {modified signature}                            ║
║    • Body:   {body lines}                                     ║
║    • Note:   {note}                                          ║
╠══════════════════════════════════════════════════════════════╣
║  Also update: {spec.entity.name}.java                        ║
║    → Add "implements {spec.entity.interface}" to class decl  ║
╚══════════════════════════════════════════════════════════════╝
```

If `{spec.aggregate}.java` already exists in the project, **apply the changes automatically** using Edit tool.
If not yet generated, record the required changes for when the file is created.

#### Step P.4 — Generate Test

**SOURCE**: `spec.testScenarios[]`, `spec.packageInfo.testPackage`

**Generate**:
- `{ReadOnlyEntity}Test.java` in `src/test/java/{testPackage}/`

**Template**:
```java
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test-inmemory")   // ← ONLY for PATTERN tests; no UseCase infra needed
class {ReadOnlyEntity}Test {

    // For each spec.testScenarios[]:
    @Test
    void {scenario.name}() {
        // Given: build real entity instance directly (new Task(...))
        // When: wrap in ReadOnlyTask (new ReadOnlyTask(task))
        // Then: assertions per scenario.then[]
    }
}
```

**CRITICAL**:
- PATTERN tests do NOT use ezSpec BDD format (no UseCase infrastructure)
- PATTERN tests use plain JUnit 5 `@Test` with `assertThrows` and `assertEquals`
- No `@Autowired` repository — entity instances are constructed directly in test
- `assertThrows(UnsupportedOperationException.class, () -> readOnly.{commandMethod}(...))` for each command
- `assertEquals(expected, readOnly.{queryMethod}())` for each query
- `assertThrows(UnsupportedOperationException.class, () -> readOnly.{collectionMethod}().add(...))` for collection immutability

**SCOPE**: `inmemory-only` (PATTERN type always uses inmemory-only scope — no Outbox, no dual-profile)

---

---

## Phase 3: Compilation

### Step 5 — Compile Verification

```bash
mvn compile -q
```

If compilation fails → fix and re-compile before proceeding.

---

## Phase 4: Testing (Gate 1 — ⛔ BLOCKING)

### Step 6 — Profile Test

**Pre-check**: Determine SCOPE from Step 4.4.

#### If SCOPE = inmemory-only:

```bash
# InMemory Profile only
SPRING_PROFILES_ACTIVE=test-inmemory mvn test -Dtest={TestClass} -q
```

```
╔═══════════════════════════════════════════════════════╗
║      IN-MEMORY PROFILE TEST VERIFICATION TABLE        ║
╠═══════════════════════════════════════════════════════╣
║ Profile       │ Tests │ Passed │ Failed │ Status      ║
╠═══════════════════════════════════════════════════════╣
║ test-inmemory │   N   │   N    │   0    │ ✅ PASS     ║
╠═══════════════════════════════════════════════════════╣
║ OVERALL: ✅ IN-MEMORY PROFILE PASSED                  ║
╚═══════════════════════════════════════════════════════╝
```

#### If SCOPE = dual-profile (default):

```bash
# Step 6.1: InMemory Profile
SPRING_PROFILES_ACTIVE=test-inmemory mvn test -Dtest={TestClass} -q

# Step 6.2: Outbox Profile
SPRING_PROFILES_ACTIVE=test-outbox mvn test -Dtest={TestClass} -q
```

```
╔═══════════════════════════════════════════════════════╗
║         DUAL-PROFILE TEST VERIFICATION TABLE          ║
╠═══════════════════════════════════════════════════════╣
║ Profile       │ Tests │ Passed │ Failed │ Status      ║
╠═══════════════════════════════════════════════════════╣
║ test-inmemory │   N   │   N    │   0    │ ✅ PASS     ║
║ test-outbox   │   N   │   N    │   0    │ ✅ PASS     ║
╠═══════════════════════════════════════════════════════╣
║ OVERALL: ✅ BOTH PROFILES PASSED                      ║
╚═══════════════════════════════════════════════════════╝
```

**⛔ If any profile fails → fix → re-run applicable profiles → repeat until pass.**

---

## Phase 5: Deterministic Review (Gate 2.5 — ⛔ BLOCKING)

### Step 9.5 — Validate Generated Code

```bash
bash .claude/skills/ezddd-java/scripts/validate-generated-code.sh --aggregate {aggregate_name}
```

**⛔ CRITICAL violations → must fix → re-run Steps 5-6 → Step 9.5 → repeat.**

0 CRITICAL violations required to proceed.

---

## Phase 6: Spec Compliance (Adapted)

### Step 9 — Verify testScenarios Coverage

**If spec has `testScenarios[]`**:
- Each scenario has at least 1 test method
- Each `"then"` condition in the scenario has at least 1 assertion

**If spec does NOT have `testScenarios[]`**:
- Verify basic coverage: happy-path + each precondition violation
- ⚠️ WARNING: Coverage verification is best-effort without explicit scenarios

**Note**: Traceability verification is not part of this workflow.

---

## Phase 7: Report

### Step 12 — Completion Report

```
╔═══════════════════════════════════════════════════════╗
║            UC EXECUTOR COMPLETION REPORT               ║
╠═══════════════════════════════════════════════════════╣
║ Spec:         {spec-path}                             ║
║ Spec Type:    COMMAND / QUERY / REACTOR               ║
║ Aggregate:    {aggregate-name}                        ║
║ Start Time:   {ISO-8601}                              ║
║ End Time:     {ISO-8601}                              ║
║ Duration:     {mm:ss}                                 ║
╠═══════════════════════════════════════════════════════╣
║ Generated Files:                                      ║
║   - {file1.java}                                      ║
║   - {file2.java}                                      ║
║   - ...                                               ║
╠═══════════════════════════════════════════════════════╣
║ Gate 1 (Dual-Profile): ✅ PASS                        ║
║ Gate 2.5 (Deterministic): ✅ PASS (0 CRITICAL)        ║
║ Spec Coverage: ✅ {N}/{N} scenarios covered            ║
╚═══════════════════════════════════════════════════════╝
```

---

## Failure Conditions Summary

| Condition | Phase | Blocking? |
|-----------|-------|-----------|
| Missing required JSON fields | 0 | ⛔ YES |
| Compilation failure (unresolved) | 3 | ⛔ YES |
| Dual-Profile test failure (unresolved) | 4 | ⛔ YES |
| Gate 2.5 CRITICAL violation (unresolved) | 5 | ⛔ YES |
| testScenarios coverage < 100% | 6 | ⚠️ WARNING (no explicit scenarios = best-effort) |

---

## Appendix: Optional Steps (Not Part of This Workflow)

1. **Code Review (Steps 10-11)** — User can run `/code-review` separately
2. **Multi-Model Review** — Available via `/code-review --multi` if needed
