# JSON Spec → Pattern Mapping Reference

> **Version**: 2.0 | **Date**: 2026-02-10
> **Purpose**: Map JSON spec fields to code generation targets

---

## Spec Type Detection (from `uc-workflow.md` Step 0.2)

| Rule | Top-Level Keys | Detected Type |
|------|---------------|---------------|
| 1 | `"useCase"` + `"domainEvent"` | COMMAND |
| 2 | `"query"` + `"projections"` | QUERY |
| 3 | `"reactor"` + `"events"` | REACTOR |

COMMAND sub-types (based on `method` field):
- **Constructor**: `method` contains "constructor" → needs `aggregates[]`
- **Method-call**: `method` contains "." → does NOT need `aggregates[]`

> **`domainEvent` format**: accepts both string and array, normalize to array.

---

## Command UseCase Spec Fields

| JSON Field | Code Generation Target | Pattern File |
|------------|----------------------|-------------|
| `spec.useCase` | UseCase interface name | `patterns/usecase/command.md` |
| `spec.behavior` | Javadoc on UseCase | — |
| `spec.aggregate` | Aggregate class name | `patterns/domain/aggregate.md` |
| `spec.aggregateId` | AggregateId value object | `patterns/domain/value-object.md` |
| `spec.method` | Aggregate method to invoke | — |
| `spec.domainEvent` | Event record name | `patterns/domain/domain-event.md` |
| `spec.repository` | Repository dependency name | — |
| `spec.input[]` | `Input` inner class fields | `patterns/usecase/command.md` |
| `spec.output` | Return type description | — |
| `spec.aggregates[]` | Aggregate Root class | `patterns/domain/aggregate.md` |
| `spec.aggregates[].attributes[]` | Aggregate fields + PO columns | `patterns/infrastructure/persistent-object.md` |
| `spec.aggregates[].attributes[].constraint` | Field initialization logic | — |
| `spec.domainEvents[]` | Sealed interface + records | `patterns/domain/domain-event.md` |
| `spec.domainEvents[].attributes[]` | Event record parameters | — |
| `spec.entities[]` | Entity classes | `patterns/domain/entity.md` |
| `spec.valueObjects[]` | Record types | `patterns/domain/value-object.md` |
| `spec.enums[]` | Enum definitions | — |
| `spec.constructorPreconditions[]` | `requireNotNull()` checks | `patterns/testing/contract-test.md` |
| `spec.constructorPostconditions[]` | `ensure()` checks + event verification | `patterns/testing/contract-test.md` |
| `spec.domainModelNotes[]` | Design context (informational) | — |

---

## Query UseCase Spec Fields

| JSON Field | Code Generation Target | Pattern File |
|------------|----------------------|-------------|
| `spec.query` | Query UseCase interface name | `patterns/usecase/query.md` |
| `spec.behavior` | Javadoc on UseCase | — |
| `spec.input[]` | `Input` inner class fields | `patterns/usecase/query.md` |
| `spec.output` | Output class name | — |
| `spec.dependencies[]` | Injected service dependencies | — |
| `spec.projections[]` | Projection interface + impls | `patterns/usecase/query.md` |
| `spec.projections[].input[]` | Projection method signature | — |
| `spec.dataTransferObjects[]` | DTO record classes | — |
| `spec.dataTransferObjects[].fields[]` | Record parameters | — |
| `spec.mappers[]` | Mapper classes in `usecase.port` | — |
| `spec.mappers[].location` | **Package path** (CRITICAL) | — |
| `spec.mappers[].methods[]` | Mapper method signatures | — |
| `spec.testDataSetup` | Test setup steps | `patterns/testing/usecase-test.md` |
| `spec.testScenarios[]` | Test methods | `patterns/testing/usecase-test.md` |
| `spec.testScenarios[].given[]` | Test precondition setup | — |
| `spec.testScenarios[].when` | Test action | — |
| `spec.testScenarios[].then[]` | Assertions | — |

---

## Reactor Spec Fields

| JSON Field | Code Generation Target | Pattern File |
|------------|----------------------|-------------|
| `spec.reactor` | Reactor interface name | `patterns/usecase/reactor.md` |
| `spec.service` | Service class name | — |
| `spec.interface_location` | Interface package path | — |
| `spec.service_location` | Service package path | — |
| `spec.events[]` | Subscribed event types | — |
| `spec.events[].source` | Full event class path | — |
| `spec.events[].fields[]` | Event payload access | — |
| `spec.dependencies[]` | Injected dependencies | — |
| `spec.inquiries[]` | Inquiry interface + impls | `patterns/usecase/query.md` |
| `spec.inquiries[].queryLogic` | JPA query implementation | — |
| `spec.actions[]` | Service method body | — |
| `spec.errorHandling[]` | Error handling strategy | — |
| `spec.testScenarios[]` | Test methods | `patterns/testing/usecase-test.md` |

---

## PATTERN Spec Fields (ReadOnly Entity)

> Detected by top-level `"pattern"` + `"entity"` fields.

| JSON Field | Code Generation Target | Notes |
|------------|----------------------|-------|
| `spec.pattern` | Type discriminator (`"ReadOnlyEntities"`) | Detection key |
| `spec.implementation` | Generation strategy (`"Proxy"` or `"SpecialCase"`) | Proxy = composition; SpecialCase = inheritance |
| `spec.packageInfo.entityPackage` | Java package for interface + proxy class | e.g., `tw.teddysoft.aiscrum.pbi.entity` |
| `spec.packageInfo.testPackage` | Java package for test class | Same as entityPackage |
| `spec.aggregate` | Aggregate Root class name | Used for AR changes notice |
| `spec.entity.name` | Original Entity class name | Must add `implements {interface}` |
| `spec.entity.interface` | Interface to extract | `ITask`, `IProjectGoal`, etc. |
| `spec.entity.readOnlyName` | Proxy class name | `ReadOnlyTask`, etc. |
| `spec.interfaceDefinition.queryMethods[]` | Interface query method signatures | All readable methods |
| `spec.interfaceDefinition.commandMethods[]` | Interface command method signatures | Methods to block in proxy |
| `spec.methodRules.commands[].signature` | Override method signature in proxy | Throws `UnsupportedOperationException` |
| `spec.methodRules.commands[].implementation` | Exception message string | Used verbatim in `throw new UOE(...)` |
| `spec.methodRules.queriesReturningImmutable[].signature` | Delegate-only query | `return real.{method}()` |
| `spec.methodRules.queriesReturningCollection[].signature` | Unmodifiable collection query | `Collections.unmodifiable*(real.{method}())` |
| `spec.methodRules.queriesReturningCollection[].implementation` | Full delegation expression | Copy verbatim |
| `spec.methodRules.queriesReturningEntity[].implementation` | Nested entity wrapping | `return new ReadOnly{Entity}(real.{method}())` |
| `spec.constructorRule.implementation[]` | Proxy constructor body lines | Copy in order |
| `spec.aggregateRootChanges.methods[]` | AR getter method changes | `.original`, `.modified`, `.body[]`, `.note` |
| `spec.testScenarios[]` | JUnit 5 `@Test` methods | Plain JUnit (NOT ezSpec) |
| `spec.testScenarios[].given` | Test setup | Construct real entity directly |
| `spec.testScenarios[].when` | Test action | Wrap in ReadOnly + call method |
| `spec.testScenarios[].then[]` | Assertions | `assertThrows` or `assertEquals` |
| `spec.entities[]` | Import resolution for entity types | Used to resolve `import` statements |
| `spec.valueObjects[]` | Import resolution for VO types | Used to resolve `import` statements |
| `spec.enums[]` | Import resolution for enum types | Used to resolve `import` statements |

### Generation Order for PATTERN

```
Step P.1 → {IEntity}.java         (interface, in entityPackage)
Step P.2 → {ReadOnlyEntity}.java  (proxy, in entityPackage)
Step P.3 → AR changes notice      (or auto-apply if file exists)
Step P.4 → {ReadOnlyEntity}Test.java (JUnit 5, in testPackage)
```

### PATTERN Scope

PATTERN always uses **inmemory-only** scope (no Outbox, no domain events, no Repository).
Gate 1 test command: `mvn test -Dtest={ReadOnlyEntity}Test -q`

---

## Important Notes

### Mapper Location (QUERY — CRITICAL)
- **Must respect `spec.mappers[].location` field** — typically `usecase.port`
- Wrong package placement = Gate 2.5 violation
