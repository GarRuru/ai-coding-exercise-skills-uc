# UseCase Layer Patterns (ezddd-java)

## Scope & Responsibility

This file defines **shared rules** applicable to ALL UseCase types (Command, Query, Reactor).
For type-specific generation guides and rules, see:

| UseCase Type | Pattern File |
|-------------|-------------|
| Command | `references/patterns/usecase/command.md` |
| Query | `references/patterns/usecase/query.md` |
| Reactor | `references/patterns/usecase/reactor.md` |

**JIT Loading**: This file is loaded alongside each pattern file during generation steps.

---

## ⚠️ MANDATORY Import Paths for UseCase Files ⭐⭐⭐ CRITICAL

> **BLOCKING REQUIREMENT**: Before generating any UseCase Interface or Service, you MUST use the exact import paths below.
> Do NOT guess import paths — incorrect paths cause compilation errors (see FC-1 in skill-improve).
> Full reference: `references/rules/framework-api.md`

<!-- @authority: cqrs_output_wildcard | source: patterns/usecase/command.md -->

### Command UseCase Interface — Required Imports

```java
import tw.teddysoft.ezddd.cqrs.usecase.CqrsOutput;           // ✅ CORRECT
import tw.teddysoft.ezddd.cqrs.usecase.command.Command;       // ✅ CORRECT
import tw.teddysoft.ezddd.usecase.port.in.interactor.Input;    // ✅ Input record implements this
import tw.teddysoft.ezddd.usecase.port.in.interactor.ExitCode; // ✅ SUCCESS / FAILURE enum
```

### ❌ WRONG Import Paths (Compilation Errors!)

```java
import tw.teddysoft.ezddd.usecase.port.in.CqrsOutput;                    // ❌ WRONG package!
import tw.teddysoft.ezddd.usecase.port.in.interactor.CommandUseCase;      // ❌ Class does not exist! Use Command
import tw.teddysoft.ezddd.usecase.port.in.interactor.CqrsOutput;         // ❌ WRONG package!
```

<!-- @authority: command_input_type | source: patterns/usecase/command.md -->

### Input Must Be Class with create() Factory

> **Authority**: `patterns/usecase/command.md` (type-specific pattern takes precedence over shared rules)

```java
// ✅ CORRECT: class implements Input with create() factory
public interface CreateProductUseCase extends Command<CreateProductUseCase.CreateProductInput, CqrsOutput<?>> {
    class CreateProductInput implements Input {
        public String id;
        public String name;
        public String userId;

        public static CreateProductInput create() {
            return new CreateProductInput();
        }
    }
}

// ❌ WRONG: record (no field assignment after construction)
record CreateProductInput(String productId, String name, String userId) implements Input {} // ❌ WRONG

// ❌ WRONG: class without create() factory
class CreateProductInput implements Input { private String productId; ... }
```

---

## Package Structure

```
[aggregate]/
├── entity/
├── usecase/
│   ├── port/
│   │   ├── in/
│   │   │   └── [UseCase]UseCase.java        # Command/Query Interface
│   │   └── out/
│   │       ├── [Aggregate]Data.java         # For Outbox pattern
│   │       ├── projection/
│   │       │   └── [Name]Projection.java    # Query Projection Interface
│   │       └── inquiry/
│   │           └── Find[X]By[Y]Inquiry.java # Reactor Inquiry Interface
│   ├── reactor/
│   │   └── [Action]When[Event]Reactor.java  # Reactor Interface
│   └── service/
│       ├── [UseCase]Service.java            # Command/Query Service
│       └── reactor/
│           └── [Action]When[Event]Service.java  # Reactor Service
├── adapter/
│   ├── in/
│   │   └── rest/springboot/
│   └── out/
│       ├── repository/
│       ├── database/springboot/projection/  # JPA Projection
│       └── persistence/inquiry/             # JPA Inquiry
└── io/
    └── springboot/
        └── config/
            ├── [Aggregate]UseCaseConfig.java
            ├── [Aggregate]InMemoryRepositoryConfig.java
            ├── [Aggregate]OutboxRepositoryConfig.java
            └── [Aggregate]ReactorConfig.java
```

---

## Layered Validation Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Use Case Layer (Application Logic)                         │
│  ├── Source: use-case.yaml → error_mapping                  │
│  ├── Checks: null, empty, blank validation                  │
│  ├── Purpose: Friendly error messages, fail fast            │
│  └── Location: Service.execute()                            │
├─────────────────────────────────────────────────────────────┤
│  Aggregate Layer (Critical Business Rule)                   │
│  ├── Source: aggregate.yaml → constraint                    │
│  ├── Checks: Core business rules (e.g., non-null)           │
│  ├── Purpose: Last defense, ensure domain model integrity   │
│  └── Location: Value Object constructor / Aggregate method  │
└─────────────────────────────────────────────────────────────┘
```

**Defense in Depth - both layers are needed!**

---

## No @Service or @Component Annotation

**Applies to: Command, Query, Reactor**

All UseCase services are registered via `@Bean` in Configuration classes, NOT via Spring stereotype annotations.

```java
// ✅ CORRECT: Plain class, registered via @Bean in Config
public class CreateProductService implements CreateProductUseCase {
    // ...
}

// ✅ CORRECT: Query service, also no annotation
public class GetProductService implements GetProductUseCase {
    // ...
}

// ✅ CORRECT: Reactor service, also no annotation
public class CommitSprintWhenSprintCreatedService implements WhenSprintCreatedCommitItToProductReactor {
    // ...
}

// ❌ WRONG: Spring annotation on any UseCase service
@Service
public class CreateProductService implements CreateProductUseCase { }

@Component
public class GetProductService implements GetProductUseCase { }

@Service
public class CommitSprintWhenSprintCreatedService implements WhenSprintCreatedCommitItToProductReactor { }
```

**Rationale:**
- Explicit bean configuration in @Configuration class
- Better control over dependencies
- Easier to swap implementations for testing

---

## Package Location Rules

**Applies to: Command, Query, Reactor**

| Component | Package |
|-----------|---------|
| Command/Query UseCase Interface | `{aggregate}/usecase/port/in/` |
| Reactor Interface | `{aggregate}/usecase/reactor/` |
| Command/Query Service | `{aggregate}/usecase/service/` |
| Reactor Service | `{aggregate}/usecase/service/reactor/` |
| Projection Interface (Query) | `{aggregate}/usecase/port/out/projection/` |
| Inquiry Interface (Reactor) | `{aggregate}/usecase/port/out/inquiry/` |
| JPA Projection (Query) | `{aggregate}/adapter/out/database/springboot/projection/` |
| JPA Inquiry (Reactor) | `{aggregate}/adapter/out/persistence/inquiry/` |
| DTO (Query) | `{aggregate}/usecase/port/` |
| Config | `{aggregate}/io/springboot/config/` |

```java
// ❌ WRONG: Interface in service package
package tw.teddysoft.aiscrum.product.usecase.service;
public interface CreateProductUseCase { }  // Should be in port/in/

// ❌ WRONG: Service in port package
package tw.teddysoft.aiscrum.product.usecase.port.in;
public class CreateProductService { }  // Should be in service/
```

**Rationale:** Clean Architecture separates ports (interfaces) from implementations.

---

## Spring Configuration Pattern

### Aggregate-Specific UseCaseConfig (Parallel-Safe)

```java
// [Aggregate]UseCaseConfig.java
@Configuration
public class ProductUseCaseConfig {

    // Command bean
    @Bean
    public CreateProductUseCase createProductUseCase(
            Repository<Product, ProductId> productRepository) {
        return new CreateProductService(productRepository);
    }

    // Query bean
    @Bean
    public GetProductsUseCase getProductsUseCase(ProductsProjection projection) {
        return new GetProductsService(projection);
    }
}
```

### Repository Config (Profile-Based)

Both InMemory and Outbox configs use the **same** `OutboxRepository` class.
The difference is in the ORM backend: `InMemoryOrmClient` vs JPA `OrmClient`.

```java
// [Aggregate]InMemoryRepositoryConfig.java
@Configuration
@Profile({"inmemory", "test-inmemory"})
public class ProductInMemoryRepositoryConfig {

    @Bean("productRepository")  // Same bean name as Outbox!
    public Repository<Product, ProductId> productRepository(
            InMemoryOrmDb<ProductData> ormDb,
            InMemoryMessageDbClient messageDbClient) {

        InMemoryOrmClient<ProductData> ormClient = new InMemoryOrmClient<>(ormDb);
        EzOutboxClient<ProductData, String> outboxClient =
                new EzOutboxClient<>(ormClient, messageDbClient);
        var outboxStore = EzOutboxStoreAdapter.createOutboxStore(outboxClient);  // ⚠️ MUST use var — OutboxStore is abstract
        OutboxRepositoryPeer<ProductData, String> peer =
                new OutboxRepositoryPeer<>(outboxStore);

        return new OutboxRepository<>(peer, ProductMapper.newMapper());
    }
}

// [Aggregate]OutboxRepositoryConfig.java
@Configuration
@Profile({"outbox", "test-outbox"})
public class ProductOutboxRepositoryConfig {

    @Bean("productRepository")  // Same bean name as InMemory!
    public Repository<Product, ProductId> productRepository(
            ProductOrmClient productOrmClient,
            PgMessageDbClient pgMessageDbClient) {

        EzOutboxClient<ProductData, String> outboxClient =
                new EzOutboxClient<>(productOrmClient, pgMessageDbClient);
        var outboxStore = EzOutboxStoreAdapter.createOutboxStore(outboxClient);  // ⚠️ MUST use var — OutboxStore is abstract
        OutboxRepositoryPeer<ProductData, String> peer =
                new OutboxRepositoryPeer<>(outboxStore);

        return new OutboxRepository<>(peer, ProductMapper.newMapper());
    }
}
```

### ReactorConfig (Aggregate-Specific)

```java
// [TargetAggregate]ReactorConfig.java
@Configuration
@Profile({"inmemory", "test-inmemory", "outbox", "test-outbox"})
public class ProductReactorConfig {

    @Bean
    public Reactor<DomainEventData> whenSprintCreatedReactor(
            Repository<Product, ProductId> productRepository) {
        return new CommitSprintWhenSprintCreatedService(productRepository);
    }
}
```

**Key Rules:**
- InMemory and Outbox use SAME bean name
- Profile isolation ensures only one exists at runtime
- UseCaseConfig injects Repository directly (no @Qualifier needed)
- ReactorConfig goes in **TARGET** aggregate's package

### Projection Profile-Awareness ⭐⭐⭐ CRITICAL

> **⚠️ CRITICAL**: Projection beans 必須註冊在 **profile-specific** config 中（InMemoryRepositoryConfig / OutboxRepositoryConfig）。
> **絕對不可**放在 profile-neutral 的 `UseCaseConfig`！
>
> - InMemory profile 的 Projection 注入 `Map<String, Data>` data store
> - Outbox profile 的 Projection 注入 `OrmClient`（JPA CrudRepository）
> - 放在 `UseCaseConfig` 會導致 Outbox profile 找不到 Map bean → 啟動失敗
>
> 詳見 `patterns/usecase/query.md` Rule 12。

---

## CqrsOutput API 兩種等價寫法

> `.setExitCode(ExitCode.SUCCESS)` 等同於 `.succeed()`；`.setExitCode(ExitCode.FAILURE)` 等同於 `.fail()`。
> 兩種風格都合法，專案內建議統一。Command 和 Query 適用相同 API。
> 完整 API 參見 `rules/framework-api.md` § CqrsOutput API，canonical 定義見 `patterns/usecase/command.md` Rule 8。

---

## OrmClient Pattern

```java
// CORRECT
public interface ProductOrmClient extends SpringJpaClient<ProductData, String> {
}

// WRONG - causes "No property 'save' found" error
public interface ProductOrmClient extends JpaRepository<ProductData, String>, OrmClient<ProductData, String> {
}
```

---

## Mapper Location (ADR-019)

```java
// CORRECT: Mapper in usecase/port/
package {rootPackage}.{aggregate}.usecase.port;

public class ProductMapper implements OutboxMapper<Product, ProductData> {
    // ...
}

// WRONG: Mapper in adapter layer
package {rootPackage}.{aggregate}.adapter.out.repository;
```

---

## Repository is Write-Only (CQRS Boundary) ⭐⭐⭐ CRITICAL

> **⚠️ CRITICAL**: ezddd `Repository<T, ID>` 介面是 **Write Model 專用**。
> 它只提供 3 個方法：`findById(ID)`, `save(T)`, `delete(T)`。
> **沒有 `findAll()`、沒有 `findBy*()`** — 這是刻意的 CQRS 分離。

```java
// ✅ CORRECT: Repository 只用於 Write Model（Command Use Case）
Sprint sprint = sprintRepository.findById(SprintId.valueOf(input.sprintId)).orElse(null);
if (null == sprint) {
    output.setId(input.sprintId)
            .setExitCode(ExitCode.FAILURE)
            .setMessage("Rename sprint failed: sprint not found, sprint id = " + input.sprintId);
    return output;
}
sprint.rename(input.newName, input.userId);
sprintRepository.save(sprint);

// ❌ WRONG: 用 Repository 做查詢操作（batch/filter）
List<Sprint> allSprints = sprintRepository.findAll();           // 方法不存在！
List<Sprint> expired = sprintRepository.findByState("STARTED"); // 方法不存在！
```

**批次操作的正確做法**：

| 場景 | 錯誤做法 | 正確做法 |
|------|---------|---------|
| 查詢某 Product 的所有 Sprint | `sprintRepository.findAll()` + filter | IDF Projection |
| 定時結束到期 Sprint | `findAll()` + filter expired | RBF 接受單個 `sprintId`，Scheduler 透過 Projection 查候選者 |
| 批次更新 | `findAll()` + loop + save | 每個 aggregate 獨立 Command |

```java
// ✅ CORRECT: RBF 接受單個 aggregate ID（Scheduler 提供 ID 列表）
public class AutoEndExpiredSprintsService implements AutoEndExpiredSprintsUseCase {
    @Override
    public CqrsOutput<?> execute(AutoEndExpiredSprintsInput input) {
        Sprint sprint = sprintRepository.findById(SprintId.valueOf(input.sprintId)).orElse(null);
        if (null == sprint) { /* return failure output */ }
        // ... single-sprint operation
    }
}

// ❌ WRONG: RBF 自己做批次查詢
public class AutoEndExpiredSprintsService {
    @Override
    public CqrsOutput<?> execute(Input input) {
        List<Sprint> all = sprintRepository.findAll();  // 編譯錯誤！
        all.stream().filter(s -> s.isExpired()).forEach(s -> { ... });
    }
}
```

---

## Common Configuration Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `No property 'save' found` | Wrong OrmClient interface | Use `SpringJpaClient` |
| `No qualifying bean 'InMemoryMessageBroker'` | Missing broker config | Check VolatileRelayConfig (inmemory) or CatchupRelayConfig (outbox) |
| `Not a managed type: MessageData` | Missing entity scan | Add to `@EntityScan` |
| `@Service` on UseCase service | Wrong registration method | Remove annotation, use `@Bean` in Config |
| UseCase interface in wrong package | Port/Service confusion | Interface → `port/in/`, Service → `service/` |
| `repository.findAll()` compilation error | Repository is Write-Only | Use Projection for queries; redesign to accept individual IDs |
