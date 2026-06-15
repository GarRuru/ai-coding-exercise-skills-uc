---
name: outbox-skill
description: |
  Generate Outbox Pattern integration components and verify compliance.

  Triggered by:
  - code executor (final integration step)
  - Direct user request: "setup outbox for [aggregate]"

  Input: All generated infrastructure components
  Output:
    - Shared infrastructure configs (if not exist)
    - Connection frame configs
    - Test suite configurations

  This skill ensures all Outbox Pattern components work together correctly.

allowed-tools: Read, Write, Edit, Bash, Glob
---

# Outbox Pattern Integration Skill

## Overview

This skill integrates all Outbox Pattern components and generates shared infrastructure.
It ensures PostgreSQL-based event storage works correctly with dual-profile testing.

---

## INPUT

| Source | Path |
|--------|------|
| project-config.json | `.dev/project-config.json` (MUST read for ports) |
| Aggregate Configs | `{aggregate}/io/springboot/config/` |

---

## OUTPUT

| File | Location |
|------|----------|
| SharedInfrastructureConfig | `common/io/springboot/config/SharedInfrastructureConfig.java` |
| SharedOutboxConfig | `common/io/springboot/config/SharedOutboxConfig.java` |
| VolatileRelayConfig | `common/io/springboot/config/connectionframe/VolatileRelayConfig.java` |
| CatchupRelayConfig | `common/io/springboot/config/connectionframe/CatchupRelayConfig.java` |
| application-outbox.properties | `src/main/resources/application-outbox.properties` |
| application-test-outbox.properties | `src/test/resources/application-test-outbox.properties` |

---

## CRITICAL RULES (Embedded - Cannot Be Skipped)

### Rule 1: PostgreSQL is REQUIRED (NOT H2)

```properties
# ✅ CORRECT: PostgreSQL for Outbox
spring.datasource.url=jdbc:postgresql://localhost:${PORT}/ai_scrum
spring.datasource.driver-class-name=org.postgresql.Driver

# ❌ WRONG: H2 for Outbox tests
spring.datasource.url=jdbc:h2:mem:testdb  # INCOMPATIBLE!
```

**Rationale:** Outbox Pattern uses PostgreSQL-specific message_store schema.

### Rule 2: Read Ports from project-config.json

```java
// ⛔⛔⛔ ALWAYS READ FROM project-config.json ⛔⛔⛔
// DO NOT GUESS PORT NUMBERS!

// Production (outbox profile):
// .dev/project-config.json → database.environments.production.port
// Typical: 5500

// Test (test-outbox profile):
// .dev/project-config.json → database.environments.test.port
// Typical: 5800

// ❌ WRONG: Assuming default port
spring.datasource.url=jdbc:postgresql://localhost:5432/...  // WRONG!

// ✅ CORRECT: Read from config
spring.datasource.url=jdbc:postgresql://localhost:${dbPort}/...  // From project-config.json
```

### Rule 3: Bean Responsibility Separation

```java
// Clear separation to avoid BeanDefinitionOverrideException

// VolatileRelayConfig (inmemory/test-inmemory) provides:
// - InMemoryMessageBroker
// - InMemoryProducer
// - MessageProducer
// - EzesVolatileRelay

// CatchupRelayConfig (outbox/test-outbox) provides:
// - InMemoryMessageBroker
// - InMemoryProducer
// - MessageProducer
// - EzesCatchUpRelay

// SharedInfrastructureConfig (inmemory/test-inmemory) provides:
// - InMemoryMessageDb, InMemoryMessageDbClient
// - ExecutorService (relayExecutor)

// SharedOutboxConfig (outbox/test-outbox) provides:
// - PgMessageDbClient (via EntityManager + JpaRepositoryFactory)
// - @EnableJpaRepositories, @EntityScan

// ❌ WRONG: Duplicate beans in multiple configs
```

### Rule 4: EzesVolatileRelay for Test Event Verification

```java
// WHY: Event flow for test verification
// 1. Repository.save() → PostgreSQL (messages table)
// 2. EzesVolatileRelay reads from PostgreSQL
// 3. EzesVolatileRelay publishes to InMemoryMessageBroker
// 4. NotifyFakeHandleAllEventsService captures events
// 5. Tests verify events via awaitility

// WITHOUT EzesVolatileRelay, Outbox tests CANNOT verify domain events!

@Bean
public EzesVolatileRelay<DomainEventData> volatileRelay(
        MessageDbClient messageDbClient,
        MessageProducer<DomainEventData> messageProducer) {
    var config = EzesVolatileRelay.RelayConfiguration.of(
            messageDbClient,
            messageProducer,
            new MessageDbToDomainEventDataConverter());
    return new EzesVolatileRelay<>(config);
}
```

### Rule 5: @DirtiesContext Required for Outbox Tests

```java
// ✅ CORRECT: @DirtiesContext ensures fresh context per test
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CreateProductServiceTest extends BaseUseCaseTest {
}

// WHY: EzesVolatileRelay is stateful Runnable
// - shutdownNow() sets running = false
// - Cannot restart in same context
// - AFTER_EACH_TEST_METHOD creates new relay instance for next test
```

### Rule 6: Hibernate ddl-auto = update (NOT create-drop)

```properties
# ✅ CORRECT: Keep existing tables
spring.jpa.hibernate.ddl-auto=update

# ❌ WRONG: Drops messages table!
spring.jpa.hibernate.ddl-auto=create-drop  # DESTROYS EVENTS!
spring.jpa.hibernate.ddl-auto=create       # DESTROYS EVENTS!
```

**Rationale:** create-drop destroys the messages table between tests.

### Rule 7: SharedOutboxConfig with EntityManager + JpaRepositoryFactory

> **APPROACH**: Use `EntityManager` + `JpaRepositoryFactory` to programmatically create
> a `PgMessageDbClient` proxy. This avoids the need for a separate `MessageStoreRepository`
> sub-interface.

```java
@Configuration
@Profile({"outbox", "test-outbox"})
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = {
    "${rootPackage}",
    "tw.teddysoft.ezddd.data.io.ezes.store"
})
@EntityScan(basePackages = {
    "${rootPackage}",
    "tw.teddysoft.ezddd.data.io.ezes.store"
})
public class SharedOutboxConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public PgMessageDbClient pgMessageDbClient() {
        RepositoryFactorySupport factory = new JpaRepositoryFactory(entityManager);
        return factory.getRepository(PgMessageDbClient.class);
    }
}

// ❌ WRONG: Using a MessageStoreRepository sub-interface (unnecessary indirection)
// public interface MessageStoreRepository extends PgMessageDbClient {}
// public MessageDbClient messageDbClient(MessageStoreRepository repo) { return repo; }
```

### Rule 8: Connection Frame Configs in common Package

```java
// ✅ CORRECT: Connection frames in common package, different profiles
package tw.teddysoft.aiscrum.common.io.springboot.config.connectionframe;

@Configuration
@Profile({"inmemory", "test-inmemory"})    // VolatileRelay = InMemory profile
public class VolatileRelayConfig { }

@Configuration
@Profile({"outbox", "test-outbox"})        // CatchupRelay = Outbox profile
public class CatchupRelayConfig { }

// ❌ WRONG: In aggregate-specific package
package tw.teddysoft.aiscrum.product.io.springboot.config;  // Wrong!

// ❌ WRONG: VolatileRelayConfig on outbox profile
@Profile({"outbox", "test-outbox"})  // Wrong! Should be inmemory!
public class VolatileRelayConfig { }
```

### Rule 9: Dual-Profile Test Execution

```bash
# MUST run BOTH profiles!

# Step 1: InMemory Profile
SPRING_PROFILES_ACTIVE=test-inmemory mvn test -Dtest={TestClass}

# Step 2: Outbox Profile
SPRING_PROFILES_ACTIVE=test-outbox mvn test -Dtest={TestClass}

# BOTH must pass before code is considered complete!
```

### Rule 10: Application Properties Structure

```
src/main/resources/
├── application.properties          # Common settings
├── application-inmemory.properties # InMemory profile
└── application-outbox.properties   # Outbox profile

src/test/resources/
├── application-test-inmemory.properties  # Test InMemory
└── application-test-outbox.properties    # Test Outbox (different port!)
```

### Rule 11: OutboxMapper.toData() MUST Set domainEventDatas, streamName, version ⭐⭐⭐

> **ROOT CAUSE**: If `toData()` only maps business fields without setting event metadata,
> `EzOutboxClient.save()` will throw NPE on `data.getDomainEventDatas().isEmpty()`.

```java
@Override
public ${Aggregate}Data toData(${Aggregate} entity) {
    ${Aggregate}Data data = new ${Aggregate}Data(
            entity.getId().value(),
            // ... business fields ...
    );

    // ⛔⛔⛔ CRITICAL: These 3 lines are MANDATORY ⛔⛔⛔
    // Without them, EzOutboxClient.save() throws NPE!
    data.setVersion(entity.getVersion());
    data.setStreamName(entity.getStreamName());

    // ✅ CORRECT: Individual .map(DomainEventMapper::toData) — no cast needed
    data.setDomainEventDatas(entity.getDomainEvents().stream()
            .map(DomainEventMapper::toData)
            .toList());

    return data;
}

// ❌ WRONG: Only mapping business fields
// data = new Data(entity.getId(), entity.getName());
// return data;  // NPE! domainEventDatas is null
```

### Rule 12: OutboxMapper.toDomain() — Business Constructor Reconstruction ⭐⭐⭐

> **ROOT CAUSE**: If `toDomain()` returns null, `OutboxRepository.findById()` wraps it with
> `Optional.of(null)` which throws NPE.
>
> **⚠️ CRITICAL**: Mapper 只負責從 data snapshot 重建 aggregate。
> **Mapper 不處理 Event Sourcing** — Event replay 是 `EsRepository` 的職責。
> 不需要 `hasCreationEvent` 判斷，不需要 `new Aggregate(domainEvents)` 分支。

**toDomain() Template (authority: mapper.md Rule 2):**

```java
@Override
public ${Aggregate} toDomain(${Aggregate}Data data) {
    Objects.requireNonNull(data, "${Aggregate}Data cannot be null");

    // ── Business Constructor + State Replay (see mapper.md Rule 9.5 Checklist) ──
    ${Aggregate} aggregate = new ${Aggregate}(
            ${Aggregate}Id.valueOf(data.getId()),
            // ... core construction fields from data ...
    );

    // ② Command methods 重建 post-creation 狀態
    // ✅ 使用 command methods 重建集合、欄位等（見 mapper.md Rule 2）
    // phantom events 由最後的 clearDomainEvents() 丟棄

    // Rebuild soft-delete flag
    if (data.isDeleted()) {
        aggregate.markAsDeleted();
    }

    aggregate.setVersion(data.getVersion());
    aggregate.clearDomainEvents();  // ⚠️ CRITICAL: clear reconstruction events
    return aggregate;
}

// ❌ WRONG: Returns null → NPE in Optional.of(null)
// return null;

// ❌ WRONG: Forgetting clearDomainEvents() → duplicate events on next save
// return aggregate;  // without clearDomainEvents()

// ❌ FORBIDDEN: Event Sourcing branch in Mapper — this is EsRepository's job!
// if (data.getDomainEventDatas() != null && !data.getDomainEventDatas().isEmpty()) {
//     var domainEvents = ...;
//     Aggregate aggregate = new Aggregate(domainEvents);  // WRONG in Mapper!
// }

// ✅ Command methods 重建 post-creation 狀態（phantom events 由 clearDomainEvents() 丟棄）
// sprint.changeNote(data.getNote());
// for (XxxData xxxData : data.getXxxDatas()) { sprint.addXxx(...); }
```

### Rule 13: Use Individual DomainEventMapper::toData Mapping ⭐⭐

> **PREFERRED APPROACH**: Use `stream().map(DomainEventMapper::toData)` to map each event
> individually. This avoids the Java generic invariance issue entirely — no cast needed.

```java
// ✅ CORRECT (PREFERRED): Individual mapping — no cast needed
data.setDomainEventDatas(entity.getDomainEvents().stream()
        .map(DomainEventMapper::toData)
        .toList());

// ✅ ALSO CORRECT: Explicit cast via stream (older approach)
List<InternalDomainEvent> events = entity.getDomainEvents().stream()
        .map(e -> (InternalDomainEvent) e)
        .toList();
data.setDomainEventDatas(DomainEventMapper.toData(events));

// ❌ WRONG: Direct list pass — compilation error (Java generic invariance)
List<${Aggregate}Events> events = entity.getDomainEvents();
data.setDomainEventDatas(DomainEventMapper.toData(events));  // COMPILE ERROR!
```

---

## OrmClient Assembly Chain (Repository 組裝流程)

OrmClient 是 CRUD 資料存取的多型抽象層。在不同 Profile 下有不同實作，但從 `EzOutboxClient` 往下的組裝鏈完全相同。

### 組裝鏈概覽

```
OrmClient ──→ EzOutboxClient ──→ OutboxStore ──→ OutboxRepositoryPeer ──→ OutboxRepository
   ↑                                                                          ↑
 多型切換點                                                              UseCase 注入點
```

### Outbox Profile 組裝鏈

```
{Aggregate}OrmClient (extends SpringJpaClient, JPA 自動產生)
         │
         ▼
EzOutboxClient<>({Aggregate}OrmClient, PgMessageDbClient)
         │
         ▼
EzOutboxStoreAdapter.createOutboxStore(outboxClient)
         │
         ▼
OutboxRepository<>(new OutboxRepositoryPeer<>(outboxStore), {Aggregate}Mapper.newMapper())
         │
         ▼
Repository<{Aggregate}, {Aggregate}Id>  ← Bean: "{aggregate}Repository"
```

**對應程式碼** (`{Aggregate}OutboxRepositoryConfig.java`)：
```java
@Configuration
@Profile({"outbox", "test-outbox"})
public class ${Aggregate}OutboxRepositoryConfig {

    @Bean
    public EzOutboxClient<${Aggregate}Data, String> outboxClient(
            ${Aggregate}OrmClient ormClient,           // ← Spring Data JPA 自動產生
            PgMessageDbClient pgMessageDbClient) {    // ← SharedOutboxConfig 提供
        return new EzOutboxClient<>(ormClient, pgMessageDbClient);
    }

    @Bean
    public OutboxStore<${Aggregate}Data, String> outboxStore(
            EzOutboxClient<${Aggregate}Data, String> outboxClient) {
        return EzOutboxStoreAdapter.createOutboxStore(outboxClient);
    }

    @Bean("${aggregate}Repository")
    public Repository<${Aggregate}, ${Aggregate}Id> repository(
            OutboxStore<${Aggregate}Data, String> outboxStore) {
        return new OutboxRepository<>(
                new OutboxRepositoryPeer<>(outboxStore),
                ${Aggregate}Mapper.newMapper());
    }
}
```

### InMemory Profile 組裝鏈

```
InMemoryOrmDb<{Aggregate}Data> → InMemoryOrmClient<>(ormDb)
         │
         ▼
EzOutboxClient<>(InMemoryOrmClient, InMemoryMessageDbClient)
         │
         ▼
EzOutboxStoreAdapter.createOutboxStore(outboxClient)
         │
         ▼
OutboxRepository<>(new OutboxRepositoryPeer<>(outboxStore), {Aggregate}Mapper.newMapper())
         │
         ▼
Repository<{Aggregate}, {Aggregate}Id>  ← Bean: "{aggregate}Repository" (同名，Profile 隔離)
```

**對應程式碼** (`{Aggregate}InMemoryRepositoryConfig.java`)：
```java
@Configuration
@Profile({"inmemory", "test-inmemory"})
public class ${Aggregate}InMemoryRepositoryConfig {

    @Bean
    public Map<String, ${Aggregate}Data> ${aggregate}DataStore() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public InMemoryOrmDb<${Aggregate}Data> ormDb(
            Map<String, ${Aggregate}Data> ${aggregate}DataStore) {
        return new InMemoryOrmDb<>(${aggregate}DataStore);
    }

    @Bean("${aggregate}Repository")
    public Repository<${Aggregate}, ${Aggregate}Id> repository(
            InMemoryOrmDb<${Aggregate}Data> ormDb,
            InMemoryMessageDbClient messageDbClient) {  // ← SharedInfrastructureConfig 提供

        InMemoryOrmClient<${Aggregate}Data> ormClient = new InMemoryOrmClient<>(ormDb);
        EzOutboxClient<${Aggregate}Data, String> outboxClient =
                new EzOutboxClient<>(ormClient, messageDbClient);
        var outboxStore = EzOutboxStoreAdapter.createOutboxStore(outboxClient);  // ⚠️ MUST use var — OutboxStore is abstract

        return new OutboxRepository<>(
                new OutboxRepositoryPeer<>(outboxStore),
                ${Aggregate}Mapper.newMapper());
    }
}
```

### 兩種 Profile 差異對照

| 層次 | Outbox Profile | InMemory Profile |
|------|---------------|-----------------|
| **OrmClient** | `{Aggregate}OrmClient extends SpringJpaClient` (宣告式，JPA 自動產生) | `new InMemoryOrmClient<>(ormDb)` (命令式，手動建立) |
| **MessageDbClient** | `PgMessageDbClient` (SharedOutboxConfig 提供) | `InMemoryMessageDbClient` (SharedInfrastructureConfig 提供) |
| **EzOutboxClient 以下** | 完全相同 | 完全相同 |
| **Bean name** | `"{aggregate}Repository"` | `"{aggregate}Repository"` (同名，Profile 隔離) |

### 為什麼 OrmClient 介面是空的？

```java
// OrmClient 只需宣告，不需任何方法
public interface ProductOrmClient extends SpringJpaClient<ProductData, String> {
}
```

- `SpringJpaClient` 繼承自 Spring Data `CrudRepository`，JPA 自動產生 `save()`、`findById()` 等實作
- `@EnableJpaRepositories`（SharedOutboxConfig）觸發自動產生機制
- OrmClient 的唯一職責是**提供型別資訊**讓 Spring 知道要管理哪個 Data class

---

## VERIFICATION CHECKPOINTS

### Checkpoint 1: Database Configuration

```bash
# Read port from project-config.json
cat .dev/project-config.json | jq '.database.environments.test.port'

# Verify application-test-outbox.properties uses correct port
grep "spring.datasource.url" src/test/resources/application-test-outbox.properties
```

### Checkpoint 2: Bean Configuration

```bash
# Verify no duplicate beans
grep -r "InMemoryMessageBroker" --include="*.java" src/main/java | grep "@Bean"
# Should only appear once (in CatchupRelayConfig or SharedInfrastructureConfig)

# Verify EzesVolatileRelay exists for Outbox
grep -r "EzesVolatileRelay" --include="*.java" src/main/java | grep "@Bean"
```

### Checkpoint 3: Dual-Profile Test

```bash
# Run InMemory tests
SPRING_PROFILES_ACTIVE=test-inmemory mvn test -Dtest={TestClass} -q

# Run Outbox tests
SPRING_PROFILES_ACTIVE=test-outbox mvn test -Dtest={TestClass} -q

# BOTH must pass!
```

---

## GENERATION TEMPLATES

### SharedInfrastructureConfig

```java
package ${rootPackage}.common.io.springboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDb;
import tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDbClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@Profile({"inmemory", "test-inmemory"})
public class SharedInfrastructureConfig {

    @Bean
    public InMemoryMessageDb inMemoryMessageDb() {
        return new InMemoryMessageDb();
    }

    @Bean
    public InMemoryMessageDbClient inMemoryMessageDbClient(InMemoryMessageDb messageDb) {
        return new InMemoryMessageDbClient(messageDb);
    }

    @Bean
    public ExecutorService relayExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    }
}
```

### SharedOutboxConfig

```java
package ${rootPackage}.common.io.springboot.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tw.teddysoft.ezddd.data.io.ezes.store.PgMessageDbClient;

@Configuration
@Profile({"outbox", "test-outbox"})
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = {
    "${rootPackage}",
    "tw.teddysoft.ezddd.data.io.ezes.store"
})
@EntityScan(basePackages = {
    "${rootPackage}",
    "tw.teddysoft.ezddd.data.io.ezes.store"
})
public class SharedOutboxConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public PgMessageDbClient pgMessageDbClient() {
        RepositoryFactorySupport factory = new JpaRepositoryFactory(entityManager);
        return factory.getRepository(PgMessageDbClient.class);
    }
}
```

### VolatileRelayConfig

> **⚠️ CRITICAL: Relay 必須 auto-start**
> `EzesVolatileRelay` 實作 `Runnable`，建立後必須透過 `relayExecutor.execute(relay)` 啟動。
> 如果只 `return new EzesVolatileRelay<>(configuration)` 而不執行，relay 不會輪詢 MessageDb，
> 事件永遠不會被轉發到 MessageBroker → 測試中的事件驗證會靜默失敗（超時等待）。
> `relayExecutor` 由 `SharedInfrastructureConfig` 提供。

```java
package ${rootPackage}.common.io.springboot.config.connectionframe;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tw.teddysoft.ezddd.data.EzesVolatileRelay;
import tw.teddysoft.ezddd.data.adapter.ezes.out.MessageDbToDomainEventDataConverter;
import tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDbClient;
import tw.teddysoft.ezddd.message.broker.adapter.out.producer.InMemoryMessageProducer;
import tw.teddysoft.ezddd.message.broker.adapter.out.producer.InMemoryProducer;
import tw.teddysoft.ezddd.message.broker.io.messagebroker.InMemoryMessageBroker;
import tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventData;
import tw.teddysoft.ezddd.usecase.port.inout.messaging.MessageProducer;

import java.util.concurrent.ExecutorService;

@Configuration
@Profile({"inmemory", "test-inmemory"})
public class VolatileRelayConfig {

    @Bean
    public InMemoryMessageBroker<DomainEventData> inMemoryMessageBroker() {
        return new InMemoryMessageBroker<>();
    }

    @Bean
    public InMemoryProducer<DomainEventData> inMemoryProducer(
            InMemoryMessageBroker<DomainEventData> inMemoryMessageBroker) {
        return new InMemoryProducer<>(inMemoryMessageBroker);
    }

    @Bean
    public MessageProducer<DomainEventData> messageProducer(
            InMemoryProducer<DomainEventData> inMemoryProducer) {
        return InMemoryMessageProducer.internal(inMemoryProducer);
    }

    // ⚠️ MUST auto-start: EzesVolatileRelay is a Runnable — without execute(), it never polls MessageDb
    @Bean
    public EzesVolatileRelay<DomainEventData> ezesVolatileRelay(
            InMemoryMessageDbClient messageDbClient,
            MessageProducer<DomainEventData> messageProducer,
            ExecutorService relayExecutor) {
        EzesVolatileRelay.RelayConfiguration<DomainEventData> configuration =
                EzesVolatileRelay.RelayConfiguration.of(
                        messageDbClient,
                        messageProducer,
                        new MessageDbToDomainEventDataConverter());
        EzesVolatileRelay<DomainEventData> relay = new EzesVolatileRelay<>(configuration);
        relayExecutor.execute(relay);
        return relay;
    }
}
```

### CatchupRelayConfig

> **⚠️ CRITICAL: Checkpoint 路徑必須使用 UUID（Failure #6 → #7 連鎖修復）**
>
> **Failure #6（Stale Checkpoint）**：
> `@DirtiesContext(AFTER_EACH_TEST_METHOD)` 每次測試重建 Spring Context，但磁碟上的 checkpoint 檔案持久存在。
> 如果使用固定路徑（如 `relay-checkpoint`），新 relay 讀到舊 checkpoint 的 `global_position`，
> 而 `setUpEventCapture()` 已將 messages 表清空並重設 sequence → relay 永遠看不到新事件。
> **Fix**: 使用 `UUID.randomUUID()` 確保每個 Spring Context 的 relay 從 position 0 開始。
>
> **Failure #7（Stale Event Leak）— 由 #6 的 fix 引發**：
> UUID checkpoint 使 relay 從 position 0 開始，但如果前一個測試的 messages 尚未清除，
> relay 會在 `setUpEventCapture()` 之前就將舊事件轉發到 broker → consumer 收到不屬於本測試的事件。
> **Fix**: `setUpEventCapture()` 在 outbox profile 下先 `Thread.sleep(200)` 等待舊事件被消費完，
> 再呼叫 `clearHandledEvents()` 清除。
>
> **連鎖因果**: Failure #6 fix (UUID) → 觸發 Failure #7 (stale events) → fix (drain + clear)

```java
package ${rootPackage}.common.io.springboot.config.connectionframe;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tw.teddysoft.ezddd.data.EzesCatchUpRelay;
import tw.teddysoft.ezddd.data.adapter.ezes.out.MessageDbToDomainEventDataConverter;
import tw.teddysoft.ezddd.data.io.ezes.store.PgMessageDbClient;
import tw.teddysoft.ezddd.message.broker.adapter.out.producer.InMemoryMessageProducer;
import tw.teddysoft.ezddd.message.broker.adapter.out.producer.InMemoryProducer;
import tw.teddysoft.ezddd.message.broker.io.messagebroker.InMemoryMessageBroker;
import tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventData;
import tw.teddysoft.ezddd.usecase.port.inout.messaging.MessageProducer;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@Profile({"outbox", "test-outbox"})
public class CatchupRelayConfig {

    @Bean
    public InMemoryMessageBroker<DomainEventData> inMemoryMessageBroker() {
        return new InMemoryMessageBroker<>();
    }

    @Bean
    public InMemoryProducer<DomainEventData> inMemoryProducer(
            InMemoryMessageBroker<DomainEventData> inMemoryMessageBroker) {
        return new InMemoryProducer<>(inMemoryMessageBroker);
    }

    @Bean
    public MessageProducer<DomainEventData> messageProducer(
            InMemoryProducer<DomainEventData> inMemoryProducer) {
        return InMemoryMessageProducer.internal(inMemoryProducer);
    }

    @Bean
    public EzesCatchUpRelay<DomainEventData> ezesCatchUpRelay(
            PgMessageDbClient pgMessageDbClient,
            MessageProducer<DomainEventData> messageProducer) {
        ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        // ⚠️ MUST use UUID to avoid stale checkpoint across @DirtiesContext test contexts
        Path checkpointPath = Path.of(System.getProperty("java.io.tmpdir"),
                "relay-checkpoint-" + UUID.randomUUID());
        EzesCatchUpRelay.RelayConfiguration<DomainEventData> configuration =
                EzesCatchUpRelay.RelayConfiguration.of(
                        pgMessageDbClient,
                        messageProducer,
                        checkpointPath,
                        new MessageDbToDomainEventDataConverter());
        EzesCatchUpRelay<DomainEventData> relay = new EzesCatchUpRelay<>(configuration);
        executor.execute(relay);
        return relay;
    }
}
```

### application-test-outbox.properties

```properties
# PostgreSQL Configuration (port from project-config.json!)
spring.datasource.url=jdbc:postgresql://localhost:${TEST_PORT}/ai_scrum_test
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Disable banner
spring.main.banner-mode=off
```

---

## EXAMPLE OUTPUT

### SharedInfrastructureConfig.java

```java
package tw.teddysoft.aiscrum.common.io.springboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDb;
import tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDbClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@Profile({"inmemory", "test-inmemory"})
public class SharedInfrastructureConfig {

    @Bean
    public InMemoryMessageDb inMemoryMessageDb() {
        return new InMemoryMessageDb();
    }

    @Bean
    public InMemoryMessageDbClient inMemoryMessageDbClient(InMemoryMessageDb messageDb) {
        return new InMemoryMessageDbClient(messageDb);
    }

    @Bean
    public ExecutorService relayExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    }
}
```

### VolatileRelayConfig.java

```java
package tw.teddysoft.aiscrum.common.io.springboot.config.connectionframe;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tw.teddysoft.ezddd.data.EzesVolatileRelay;
import tw.teddysoft.ezddd.data.adapter.ezes.out.MessageDbToDomainEventDataConverter;
import tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDbClient;
import tw.teddysoft.ezddd.message.broker.adapter.out.producer.InMemoryMessageProducer;
import tw.teddysoft.ezddd.message.broker.adapter.out.producer.InMemoryProducer;
import tw.teddysoft.ezddd.message.broker.io.messagebroker.InMemoryMessageBroker;
import tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventData;
import tw.teddysoft.ezddd.usecase.port.inout.messaging.MessageProducer;

import java.util.concurrent.ExecutorService;

@Configuration
@Profile({"inmemory", "test-inmemory"})
public class VolatileRelayConfig {

    @Bean
    public InMemoryMessageBroker<DomainEventData> inMemoryMessageBroker() {
        return new InMemoryMessageBroker<>();
    }

    @Bean
    public InMemoryProducer<DomainEventData> inMemoryProducer(
            InMemoryMessageBroker<DomainEventData> inMemoryMessageBroker) {
        return new InMemoryProducer<>(inMemoryMessageBroker);
    }

    @Bean
    public MessageProducer<DomainEventData> messageProducer(
            InMemoryProducer<DomainEventData> inMemoryProducer) {
        return InMemoryMessageProducer.internal(inMemoryProducer);
    }

    // ⚠️ MUST auto-start: EzesVolatileRelay is a Runnable — without execute(), it never polls MessageDb
    @Bean
    public EzesVolatileRelay<DomainEventData> ezesVolatileRelay(
            InMemoryMessageDbClient messageDbClient,
            MessageProducer<DomainEventData> messageProducer,
            ExecutorService relayExecutor) {
        EzesVolatileRelay.RelayConfiguration<DomainEventData> configuration =
                EzesVolatileRelay.RelayConfiguration.of(
                        messageDbClient,
                        messageProducer,
                        new MessageDbToDomainEventDataConverter());
        EzesVolatileRelay<DomainEventData> relay = new EzesVolatileRelay<>(configuration);
        relayExecutor.execute(relay);  // ← CRITICAL: auto-start relay
        return relay;
    }
}
```

### application-test-outbox.properties

```properties
# PostgreSQL Configuration (port from project-config.json)
spring.datasource.url=jdbc:postgresql://localhost:${dbPort}/${dbName}
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Disable banner
spring.main.banner-mode=off
```

---

## INTEGRATION WITH ORCHESTRATOR

```
code executor
    ↓
    Final Step: Invoke outbox-skill
    ├─ Check: SharedInfrastructureConfig exists? If not, create
    ├─ Check: SharedOutboxConfig exists? If not, create
    ├─ Check: Connection frame configs exist? If not, create
    ├─ Check: application properties configured?
    └─ Verify: Dual-profile tests pass
```

---

### Rule 14: Outbox Test Pre-flight Cleanup ⭐⭐

> **ROOT CAUSE (Workflow CBF Failures F2 + F5)**: 首次 Outbox 測試或 schema 變更後，
> PostgreSQL 中可能殘留前一次執行的 messages 和 aggregate 資料，導致：
> - CatchupRelay checkpoint race condition (F2)
> - NOT NULL column addition failure on existing rows (F5)

**Pre-flight Cleanup SQL（首次或 schema 變更後執行）**:

```sql
DELETE FROM message_store.messages;
ALTER SEQUENCE message_store.messages_global_position_seq RESTART WITH 1;
DROP TABLE IF EXISTS message_store.<aggregate_table> CASCADE;
```

**何時需要 Pre-flight Cleanup**:

| 情境 | 需要 Cleanup？ | 原因 |
|------|---------------|------|
| 首次執行 Outbox 測試 | ✅ | Messages table 可能有殘留資料 |
| Aggregate schema 新增欄位 | ✅ | `ddl-auto=update` 無法在有資料的表上加 NOT NULL 欄位 |
| 連續執行同一 aggregate 測試 | ❌ | `BaseUseCaseTest` 的 `setUpEventCapture()` + `tearDownEventCapture()` 處理 |
| 切換 aggregate 測試 | ❌ | 不同 aggregate 使用不同 table |

---

## ERROR HANDLING

| Error | Action |
|-------|--------|
| H2 database in Outbox | Replace with PostgreSQL |
| Wrong port number | Read from project-config.json |
| BeanDefinitionOverrideException | Check for duplicate beans |
| messages table dropped | Change ddl-auto to update |
| Events not captured in test | Verify EzesVolatileRelay bean exists |
| Test passes in InMemory but fails in Outbox | Check toDomain() correctly reconstructs all state from data snapshot |
| NPE in `EzOutboxClient.save()` on `getDomainEventDatas()` | toData() must set domainEventDatas, streamName, version (Rule 11) |
| NPE in `Optional.of(null)` from `findById()` | toDomain() must reconstruct via Business Constructor, not return null (Rule 12) |
| Compilation error: `List<E>` incompatible with `List<InternalDomainEvent>` | Use `.stream().map(e -> (InternalDomainEvent) e).toList()` (Rule 13) |
| `NoQualifyingBean` for `PgMessageDbClient` | Use `EntityManager` + `JpaRepositoryFactory` in SharedOutboxConfig (Rule 7) |
| `ConditionTimeoutException` on first Outbox test | Execute Pre-flight Cleanup SQL (Rule 14) — CatchupRelay checkpoint race |
| `column "xxx" contains null values` on Outbox startup | DROP aggregate table and let Hibernate recreate (Rule 14) — Schema change with existing rows |
| `UnrecognizedPropertyException` in relay stdout/stderr | Add `@JsonIgnore` on VO record `is*()`/`get*()`/`has*()` methods (see value-object.md Rule 11) |

---
