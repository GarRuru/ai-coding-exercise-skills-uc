# Critical Rules (Always Active)

> **JIT Strategy**: These rules are ALWAYS active during code generation.
> Detailed patterns are loaded just-in-time at each Step 4.x via `LOAD_PATTERNS:` blocks.
> Full reference files are in `references/rules/` and `references/patterns/`.

<!-- @authority: dirtiescontext_after_each | source: rules/testing-patterns.md -->
<!-- @authority: dateprovider_not_instant | source: rules/common-rules.md -->
<!-- @authority: aggregate_isDeleted_no_declare | source: patterns/domain/aggregate.md -->
<!-- @authority: command_input_type | source: patterns/usecase/command.md -->
<!-- @authority: ormclient_extends_springjpaclient | source: patterns/infrastructure/config.md#Rule-4 -->
<!-- @authority: set_version_before_clear | source: patterns/infrastructure/mapper.md -->
<!-- @authority: inmemory_ormdb_map_constructor | source: patterns/infrastructure/config.md#Rule-6 -->
<!-- @authority: mapping_type_prefix_position | source: patterns/domain/domain-event.md -->
<!-- @authority: postcondition_event_verification | source: patterns/domain/aggregate.md -->

## ABSOLUTELY FORBIDDEN

| # | Rule | Symptom if Violated |
|---|------|---------------------|
| 1 | State changes in constructor | Event Sourcing inconsistency |
| 2 | `@Service`/`@Component` on Service classes | Wrong DI pattern |
| 3 | `@ActiveProfiles` in test classes | TestSuite cannot switch profiles |
| 4 | `Instant.now()` for events | Non-deterministic tests |
| 5 | `Map.of()` for metadata | UseCase layer 需要在 Aggregate 產生 event 後補入 userId 等 cross-cutting context，`Map.of()` 不可變會阻擋寫入 |
| 6 | Custom Repository methods | Violates Repository pattern |
| 7 | `javax.persistence.*` | Wrong JPA package (use `jakarta`) |
| 8 | Comments in generated code | Unless explicitly requested |
| 9 | `@EnableJpaRepositories` on OutboxRepositoryConfig | SharedOutboxConfig handles globally (FC-4) |
| 10 | `@EntityScan` on OutboxRepositoryConfig | SharedOutboxConfig handles globally (FC-4) |
| 11 | `new InMemoryMessageProducer<>(broker)` | No public constructor — use `InMemoryMessageProducer.internal(new InMemoryProducer<>(broker))` (FC-7) |
| 12 | `compositeMapper.putAll(eventMapper)` | `putAll()` does not exist — use `eventMapper.getMap().forEach(compositeMapper::put)` (FC-9) |
| 13 | `() -> {}` in ezSpec Given/When/Then lambdas (Gate 2.5: F-23) | Type must be `Consumer<ScenarioEnvironment>` — use `env -> {}` (FC-6) |
| 14 | Package-private ezSpec test methods (`void test()`) | Must be `public void test()` — `Class.getMethod()` only finds public (FC-11) |
| 15 | `ContractViolationException` | Class DOES NOT EXIST — use `PreconditionViolationException` (FC-10) |
| 16 | `EzesVolatileRelay` from `tw.teddysoft.ezddd.message.relay` | Wrong package — use `tw.teddysoft.ezddd.data.EzesVolatileRelay` (FC-8) |
| 17 | `CrudRepository` or `JpaRepository` in OrmClient | OrmClient must extend `SpringJpaClient` only — `EzOutboxClient` requires `OrmClient<T,ID>` interface |
| 18 | `tw.teddysoft.ezspec.testannotation.EzScenario` or `tw.teddysoft.ezspec.EzScenario` | Wrong import — use `tw.teddysoft.ezspec.extension.junit5.EzScenario` only |
| 19 | `@Enumerated` on `String` field in PO | `@Enumerated` is only for Java enum types — PO fields must use plain `String` for enum storage |
| 20 | `requireNotNull`/`require()` inside `try` block | Precondition violations are programming errors — must be OUTSIDE try block so they propagate as `PreconditionViolationException`, not caught as business exceptions. Also: `requireNotNull` returns void — `x = requireNotNull(...)` is compilation error (Gate 2.5: F-26) |
| 21 | Declaring `version`/`isDeleted` fields in Aggregate subclass (Gate 2.5: F-24) | Field shadowing — `EsAggregateRoot` already defines these. Subclass fields shadow parent, causing `isDeleted()` to always return `false` and `version` to stay `0`. Tests pass but Outbox fails silently |
| 22 | Using `record` for UseCase Input class | Input MUST be mutable `class` with `create()` factory — tests need to set fields individually (command.md Rule 1) |
| 23 | Empty `when(DestructionEvent)` handler body | `EsAggregateRoot` does NOT auto-handle — MUST set `this.isDeleted = true` in when() to access parent's `protected` field |
| 24 | ⚠️ RECURRING: `toDomain()` missing `setVersion()` + `clearDomainEvents()` | toDomain() uses Business Constructor + command methods to rebuild state, but MUST call `setVersion(data.getVersion())` to restore version, then `clearDomainEvents()` to discard phantom events. Order matters: `setVersion()` BEFORE `clearDomainEvents()` (see mapper.md Rule 9.5). Missing these causes phantom events in Outbox relay |
| 25 | `Thread.sleep()` in ezSpec lambdas | `Consumer<ScenarioEnvironment>` can't declare `throws` — use Awaitility `await().during()` instead |
| 26 | `@EzScenario(rule = "...")` | Rule parameter requires pre-loaded Feature rules (from spec file). Feature.New() does not pre-load rules → `IllegalArgumentException: Rule not found`. Always use bare `@EzScenario` |
| 27 | `CqrsOutput.setDto(...)` or `output.setDto(...)` | Method DOES NOT EXIST on CqrsOutput. Query use cases must define custom Output subclass with dto field (see API QUICK REFERENCE) |
| 28 | `new InMemoryOrmDb<>()` no-arg constructor | InMemoryOrmDb MUST use Map parameter constructor: `new InMemoryOrmDb<>(dataStore)`. No-arg constructor causes data isolation failure — each test gets separate empty store instead of shared store (config.md Rule 6) |
| 29 | `tw.teddysoft.ezddd.usecase.port.out.repository.projection.Projection` | Wrong package — Projection is in `tw.teddysoft.ezddd.cqrs.usecase.query.Projection`. Also: `ProjectionInput` is `Projection.ProjectionInput` (inner class) |
| 30 | `DomainEventMapper.toDomain(eventData, mapper)` 2-arg call | Method only accepts 1 arg — ADR-047 auto-registration uses global mapper. Use `DomainEventMapper.toDomain(eventData)` |
| 31 | `public void react(DomainEventData message)` in Reactor | Method name is `execute()` NOT `react()` — `Reactor<DomainEventData>` interface defines `execute()` |
| 32 | `@CrossOrigin` on controllers (Gate 2.5: F-30) | Must use centralized `CorsConfig` bean — per-controller `@CrossOrigin` bypasses centralized CORS policy (see `security-patterns.md` Rule 6) |
| 33 | `ALTER SEQUENCE message_store.messages_global_position_seq RESTART` in test cleanup | CatchUpRelay checkpoint 會超前 reset 後的 position，導致新 event 永遠被跳過。只 DELETE 資料，不 RESTART sequence (see `.dev/lessons/CATCHUP-RELAY-CHECKPOINT-VS-SEQUENCE-RESET.md`) |
| 34 | ID record without `@Override toString()` returning raw value (FC-17) | `OutboxRepository.findById()` uses `id.toString()` as the InMemory map lookup key. Java record default `toString()` = `PbiId[value=xxx]`, stored key = `xxx` → findById always returns empty. No compile error, no runtime exception — silently broken. Fix: every `*Id` record MUST override `toString() { return value; }` |

## ALWAYS REQUIRED

| # | Rule | Where |
|---|------|-------|
| 1 | `@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)` + `setUpEventCapture()` in `@BeforeEach` (Gate 2.5: R-14) | All test classes extending BaseUseCaseTest |
| 2 | Input/Output as UseCase inner classes (Gate 2.5: R-13) | UseCase interfaces |
| 3 | TypeMapper key uses `MAPPING_TYPE_PREFIX + "EventName"` pattern (e.g. `"ProductEvents$ProductCreated"`) | Domain Events mapper() |
| 4 | `setDomainEventDatas()` + `setStreamName()` | `toData()` method |
| 5 | `DateProvider.now()` (returns `Instant` only — no `nowLocalDateTime()`) | Event creation |
| 6 | `new HashMap<>()` | Event metadata |
| 7 | `@Bean` registration | Service classes |
| 8 | `ignore()` for idempotency | uContract pattern |
| 9 | DomainEventMapperConfig must exist | Before any Repository with Outbox works (FC-2) |
| 10 | tearDown must clean outbox messages | In `tearDownEventCapture()` (FC-5) |
| 11 | `@EzScenario` + `public void` (no `@Test`) | All ezSpec test methods — `Class.getMethod()` only finds public (FC-11) |
| 12 | CBF state-setup: await → clear → next command | Every command execution in multi-step helpers: `await().atMost(10, SECONDS)` → `clearCapturedEvents()` → next command (race condition prevention) |
| 13 | `await().atMost(10, TimeUnit.SECONDS)` minimum timeout | All Awaitility assertions — 5s is insufficient for CI/Outbox environments |
| 14 | IDF list query tests MUST clean entity table in Outbox `@BeforeEach` | `@DirtiesContext` only refreshes Spring Context, NOT database. Outbox `queryAll()` returns stale data from prior tests. Add: `jdbcTemplate.execute("DELETE FROM {table}")` |
| 15 | Verify aggregate getter return type BEFORE writing assertions | e.g. `getCommittedSprints()` returns `List<CommittedSprint>`, NOT `List<SprintId>`. Use `getCommittedSprintIds()` for ID-only list. Always `javap` or read source first |
| 16 | Reactor `execute()` must null-check `message` parameter at entry | `if (message == null) return;` — relay may deliver null messages during shutdown or error recovery |
| 17 | Read entity source code before writing tests that reference enums/constants | Do NOT guess domain-specific enum values (e.g., `PbiState.COMMITTED` does not exist). Always read the actual enum definition first |
| 18 | UseCase Input class must have `public static XxxInput create()` factory method (Gate 2.5: R-12) | Tests use `XxxInput.create()` to construct input. Missing factory → compilation error in tests |
| 19 | `@SelectPackages` in both TestSuites must include current aggregate package | After creating tests for a NEW aggregate, check `InMemoryTestSuite` + `OutboxTestSuite` — if aggregate package is missing from `@SelectPackages`, add it. Template: `references/templates/test-suites.md` |
| 20 | Every command method MUST have `_xxxEventGenerated()` postcondition helper verifying all core business fields of the emitted event (see `Plan.java`) | Aggregate Root `ensure()` blocks |
| 21 | `@Valid` on all `@RequestBody` parameters (Gate 2.5: R-15) | All Controller classes — missing `@Valid` skips DTO validation entirely (see `security-patterns.md` Rule 4) |
| 22 | Every `*Id` record MUST override `toString()` to return raw value (FC-17) | All aggregate ID and cross-aggregate reference ID classes — `OutboxRepository.findById()` maps domain ID via `.toString()` as InMemory map key. See FORBIDDEN #34 |

## IMPORT PATH QUICK REFERENCE

```java
// Core Domain
import tw.teddysoft.ezddd.entity.EsAggregateRoot;
import tw.teddysoft.ezddd.entity.InternalDomainEvent;
import tw.teddysoft.ezddd.entity.ValueObject;
import tw.teddysoft.ezddd.entity.DomainEventTypeMapper;

// CQRS — UseCase Interface (FC-1: MUST use these exact paths!)
import tw.teddysoft.ezddd.cqrs.usecase.CqrsOutput;
import tw.teddysoft.ezddd.cqrs.usecase.command.Command;
import tw.teddysoft.ezddd.cqrs.usecase.query.Query;
import tw.teddysoft.ezddd.cqrs.usecase.query.Projection;              // ⚠️ NOT in usecase.port.out.repository.projection!
import tw.teddysoft.ezddd.cqrs.usecase.query.Projection.ProjectionInput; // ⚠️ Inner class of Projection
import tw.teddysoft.ezddd.usecase.port.in.interactor.Input;
import tw.teddysoft.ezddd.usecase.port.in.interactor.ExitCode;
import tw.teddysoft.ezddd.usecase.port.in.interactor.UseCaseFailureException;

// Repository
import tw.teddysoft.ezddd.usecase.port.out.repository.Repository;

// Outbox Pattern — OutboxMapper (⚠️ NOT in ezddd.data.adapter!)
import tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxMapper;
import tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxRepository;

// Outbox Infrastructure — Peer + Store (adapter layer)
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxRepositoryPeer; // 2 type params: <Data, ID>
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxStore;          // abstract! use factory below

// Outbox Infrastructure — ORM + Client (io layer, ⚠️ NOT in data.adapter!)
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxClient;
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxStoreAdapter;   // factory: EzOutboxStoreAdapter.createOutboxStore(outboxClient)
import tw.teddysoft.ezddd.data.io.ezoutbox.SpringJpaClient;        // OrmClient extends this (NOT CrudRepository!)
import tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmDb;          // 1 type param: <Data>
import tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmClient;      // 1 type param: <Data>

// Contract
import static tw.teddysoft.ucontract.Contract.*;

// Reactor
import tw.teddysoft.ezddd.usecase.port.in.interactor.Reactor;
import tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventData;

// Testing — ezSpec BDD
import tw.teddysoft.ezspec.keyword.Feature;

// Relay / Connection Frame (FC-8, FC-12)
import tw.teddysoft.ezddd.data.EzesVolatileRelay;
import tw.teddysoft.ezddd.data.EzesCatchUpRelay;
import tw.teddysoft.ezddd.data.adapter.ezes.out.MessageDbToDomainEventDataConverter;

// uContract Exceptions (FC-10)
import tw.teddysoft.ucontract.PreconditionViolationException;
import tw.teddysoft.ucontract.PostconditionViolationException;
import tw.teddysoft.ucontract.ClassInvariantViolationException;
```

## WRONG IMPORT PATHS (Compilation Errors!)

```java
// FC-1: These paths DO NOT EXIST — never use them!
import tw.teddysoft.ezddd.usecase.port.in.CqrsOutput;                    // WRONG package!
import tw.teddysoft.ezddd.usecase.port.in.interactor.CommandUseCase;      // Class does not exist! Use Command
import tw.teddysoft.ezddd.usecase.port.in.interactor.CqrsOutput;         // WRONG package!

// Projection wrong paths — MOST COMMON ERROR! (FAIL-001)
import tw.teddysoft.ezddd.usecase.port.out.repository.projection.Projection;      // WRONG! Does not exist!
import tw.teddysoft.ezddd.usecase.port.out.repository.projection.ProjectionInput;  // WRONG! Does not exist!
import tw.teddysoft.ezddd.usecase.port.out.Projection;                             // WRONG! Does not exist!

// DomainEventMapper wrong usage — SECOND MOST COMMON ERROR! (FAIL-002)
// DomainEventMapper.toDomain(eventData, mapper)   // WRONG! Only 1 arg: DomainEventMapper.toDomain(eventData)

// OutboxMapper wrong path — it's in `usecase`, NOT `data.adapter`!
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxMapper;     // WRONG! Does not exist!

// FC-8: Relay wrong paths — package is `data`, NOT `message.relay`!
import tw.teddysoft.ezddd.message.relay.adapter.out.EzesVolatileRelay;   // WRONG!
import tw.teddysoft.ezddd.message.relay.adapter.out.EzesCatchUpRelay;    // WRONG!
import tw.teddysoft.ezddd.message.relay.EzesVolatileRelay;               // WRONG!

// FC-10: This exception class DOES NOT EXIST in uContract!
import tw.teddysoft.ucontract.ContractViolationException;                // WRONG! Use PreconditionViolationException

// FC-13: Outbox infrastructure wrong paths — package is `data.io.ezoutbox`, NOT `data.adapter.repository.outbox`!
import tw.teddysoft.ezddd.data.adapter.repository.outbox.EzOutboxClient;     // WRONG! → data.io.ezoutbox
import tw.teddysoft.ezddd.data.adapter.repository.outbox.SpringJpaClient;    // WRONG! → data.io.ezoutbox
import tw.teddysoft.ezddd.data.adapter.repository.outbox.InMemoryOrmDb;      // WRONG! → data.io.ezoutbox
import tw.teddysoft.ezddd.data.adapter.repository.outbox.InMemoryOrmClient;  // WRONG! → data.io.ezoutbox

// FC-14: OutboxStore is ABSTRACT — cannot instantiate directly!
new OutboxStore<>(outboxClient);   // WRONG! Use EzOutboxStoreAdapter.createOutboxStore(outboxClient)

// FC-15: Wrong type parameter count — common when following v2.0.0 patterns!
OutboxRepositoryPeer<Product, ProductId, ProductData, String> // WRONG! Only 2 params: <ProductData, String>
InMemoryOrmDb<ProductData, String>                            // WRONG! Only 1 param: <ProductData>
InMemoryOrmClient<ProductData, String>                        // WRONG! Only 1 param: <ProductData>
```

## API QUICK REFERENCE

```java
// CqrsOutput (Command — no DTO return)
CqrsOutput<?> output = CqrsOutput.create().setId(id.value()).succeed();
CqrsOutput<?> output = CqrsOutput.create().setMessage(msg).fail();
assertEquals(ExitCode.SUCCESS, output.getExitCode());

// Query Output (IDF — returns DTO, MUST use custom subclass!)
// ⚠️ CqrsOutput has NO setDto()! Define custom Output in UseCase interface:
//   class GetXxxOutput extends CqrsOutput<GetXxxOutput> {
//       private XxxDto dto;
//       public XxxDto getDto() { return dto; }
//       public GetXxxOutput setDto(XxxDto dto) { this.dto = dto; return this; }
//   }
GetXxxOutput output = new GetXxxOutput();
output.setDto(dto);
return output.succeed();

// EsAggregateRoot (ONLY way to raise events)
apply(new MyEvent(...));

// Repository (ONLY 3 methods)
repository.findById(id);
repository.save(aggregate);
repository.delete(aggregate);

// Contract — requireNotNull returns void!
requireNotNull("name", name);   // correct
this.x = requireNotNull("x", x); // WRONG — void return!

// ezSpec env — geti() requires String values! (FC-3)
env.put("count", "5");                    // correct: String
env.put("count", 5);                      // WRONG: Integer → ClassCastException in geti()
env.put("result", String.valueOf(num));   // correct: convert to String
```

## INFRASTRUCTURE PREREQUISITES (FC-2)

Before any Aggregate with Outbox pattern can work, these files **MUST EXIST**:

| File | Purpose | If Missing |
|------|---------|------------|
| `DomainEventMapperConfig.java` | ADR-047 event auto-registration | `"Require [Please call setMapper] cannot be null"` |
| `SharedInfrastructureConfig.java` | InMemory beans | InMemory profile fails |
| `SharedOutboxConfig.java` | JPA scanning + PgMessageDbClient | Outbox profile fails |

If not present → **auto-trigger** `/init-project`（Step 0.1 會自動偵測並執行，不需詢問用戶）.

## COMMON MISTAKES QUICK FIX

| Mistake | Fix |
|---------|-----|
| `raiseEvent(e)` (Gate 2.5: F-25) | `apply(e)` |
| `new CqrsOutput(...)` | `CqrsOutput.create()` |
| `output.exitCode()` | `output.getExitCode()` |
| `repository.findByName(n)` | Use Projection instead |
| `repository.findAll()` | Repository is Write-Only (3 methods: findById, save, delete) — use Projection for queries, accept individual IDs for batch |
| `Instant.now()` | `DateProvider.now()` |
| `@EnableJpaRepositories` on OutboxConfig | Remove — SharedOutboxConfig handles it (FC-4) |
| `env.put("key", 5)` with `env.geti()` (Gate 2.5: F-27) | `env.put("key", "5")` — must be String (FC-3) |
| tearDown without message cleanup | Add `DELETE FROM message_store.messages` in tearDown (FC-5) |
| `toDomain()` missing `setVersion()` + `clearDomainEvents()` | toDomain() uses Business Constructor + command methods to rebuild state — MUST call `setVersion(data.getVersion())` to restore version, then `clearDomainEvents()` to discard phantom events. Order: `setVersion()` BEFORE `clearDomainEvents()` (see mapper.md Rule 9.5) |
| `Thread.sleep(500)` in ezSpec lambda | Use `await().during(500, MILLISECONDS).atMost(1, SECONDS)` — lambdas can't declare `throws` |

## FORBIDDEN API (Does NOT Exist — Commonly Hallucinated)

| Hallucinated Method | Correct Alternative |
|---|---|
| `requireNotBlank(name, value)` | `requireNotNull(name, value)` then `require("must not be blank", () -> !value.isBlank())` |
| `requireNotEmpty(name, collection)` | `requireNotNull(name, collection)` then `require("must not be empty", () -> !collection.isEmpty())` |
| `requirePositive(name, value)` | `require("must be positive", () -> value > 0)` |
| `ContractViolationException` | `PreconditionViolationException` (FC-10, also Rule 15) |
| `DateProvider.nowLocalDateTime()` | Method does NOT exist — `DateProvider.now()` returns `Instant`. Use `LocalDateTime.ofInstant(DateProvider.now(), ZoneId.systemDefault())` if needed |
| `CqrsOutput.setDto(dto)` | Method does NOT exist on CqrsOutput. For Query use cases, create custom Output subclass: `class MyOutput extends CqrsOutput<MyOutput> { private MyDto dto; ... }` |
| `CqrsOutput.exitWithFailure(msg, code)` | Static method does NOT exist. Use `CqrsOutput.create().setMessage(msg).fail()` or custom Output's `new MyOutput().setMessage(msg).fail()` |
| `@EzScenario(rule = "AC1 - ...")` | Rule parameter needs pre-loaded Feature rules. Use bare `@EzScenario` with `Feature.New()` |

## EZSPEC CRITICAL RULES (FC-6, FC-11)

| Aspect | ✅ Correct | ❌ Wrong | FC |
|--------|-----------|----------|-----|
| **Lambda type** | `env -> {}` (`Consumer<ScenarioEnvironment>`) | `() -> {}` (`Runnable`) (Gate 2.5: F-23) | FC-6 |
| **Method annotation** | `@EzScenario` only (no `@Test`) | `@EzScenario` + `@Test` (redundant, may cause double execution) | FC-11 |
| **Method visibility** | `public void test_name()` | `void test_name()` (package-private) | FC-11 |
| **Feature creation** | `Feature.New("name")` | `feature("name")`, `EzScenario.feature()` | — |
| **Step methods** | `.Given()`, `.When()`, `.Then()` (uppercase) | `.given()`, `.when()`, `.then()` (lowercase) | — |
| **Execution** | `.Execute()` | `.perform()`, `.run()` | — |
| **env.geti()** | `env.put("k", "5")` (String!) | `env.put("k", 5)` (Integer → ClassCastException) (Gate 2.5: F-27) | FC-3 |
| **env.get()** | `env.get("k", String.class)` (2 params!) | `env.get("k")` (1 param — compilation error: cannot infer type T) | FC-16 |
| **@EzScenario params** | `@EzScenario` (no params) | `@EzScenario(rule = "AC1 - ...")` (Rule not found!) | Rule 26 |

## RELAY CONSTRUCTOR QUICK REFERENCE (FC-8, FC-12)

```java
// VolatileRelay — 3 params (FC-8: correct import is tw.teddysoft.ezddd.data.EzesVolatileRelay)
// ⚠️ RelayConfiguration is INNER CLASS of each Relay, not a standalone class!
EzesVolatileRelay relay = new EzesVolatileRelay(
    EzesVolatileRelay.RelayConfiguration.of(messageDbClient, producer, converter)   // 3 args
);

// CatchUpRelay — 4 params (FC-12: extra checkpointPath parameter)
EzesCatchUpRelay relay = new EzesCatchUpRelay(
    EzesCatchUpRelay.RelayConfiguration.of(messageDbClient, producer, checkpointPath, converter)  // 4 args
);

// ⚠️ RelayConfiguration is inner class:
//   EzesVolatileRelay.RelayConfiguration.of(MessageDbClient, MessageProducer, Converter)
//   EzesCatchUpRelay.RelayConfiguration.of(MessageDbClient, MessageProducer, Path, Converter)
```
