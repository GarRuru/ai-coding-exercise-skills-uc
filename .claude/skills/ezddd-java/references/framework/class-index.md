# ezapp-starter 類別索引 (v2.0.0 / v2.1.0)

這是 ezapp-starter 框架的核心類別索引，用於快速查找和確認 import 路徑。

> **⚠️ v2.1.0 API 變更**: `EzOutboxClient`、`SpringJpaClient`、`InMemoryOrmDb`、`InMemoryOrmClient`
> 的套件路徑為 `tw.teddysoft.ezddd.data.io.ezoutbox.*`（**不是** `data.adapter.repository.outbox`）。
> `OutboxStore` 為 abstract，必須透過 `EzOutboxStoreAdapter.createOutboxStore()` 建立。
> `OutboxRepositoryPeer` 只有 2 個型別參數 `<Data, ID>`（不是 4 個）。
> `InMemoryOrmDb` 和 `InMemoryOrmClient` 只有 1 個型別參數 `<Data>`（不是 2 個）。

## Domain Layer 核心類別

```
tw.teddysoft.ezddd.entity.AggregateRoot
tw.teddysoft.ezddd.entity.DomainEvent
tw.teddysoft.ezddd.entity.InternalDomainEvent
tw.teddysoft.ezddd.entity.ValueObject
tw.teddysoft.ezddd.entity.Entity
tw.teddysoft.ezddd.entity.EsAggregateRoot
tw.teddysoft.ezddd.entity.DomainEventTypeMapper
```

## Use Case Layer 核心類別

```
tw.teddysoft.ezddd.usecase.port.in.interactor.Input
tw.teddysoft.ezddd.usecase.port.in.interactor.ExitCode
tw.teddysoft.ezddd.usecase.port.in.interactor.UseCaseFailureException
tw.teddysoft.ezddd.cqrs.usecase.command.Command
tw.teddysoft.ezddd.cqrs.usecase.CqrsOutput
```

## Repository Pattern 類別

```
tw.teddysoft.ezddd.usecase.port.out.repository.Repository
tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxRepository
tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxData
tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxMapper
tw.teddysoft.ezddd.usecase.port.out.repository.impl.RepositoryPeer
tw.teddysoft.ezddd.usecase.port.out.repository.impl.StoreData
```

## Query Pattern 類別

> **⚠️ 常見錯誤**: Projection 的 import 路徑是 `cqrs.usecase.query.Projection`，
> **不是** `usecase.port.out.repository.projection.Projection`（此路徑不存在）！
> `ProjectionInput` 是 `Projection` 的 inner class：`Projection.ProjectionInput`。

```
tw.teddysoft.ezddd.cqrs.usecase.query.Query
tw.teddysoft.ezddd.cqrs.usecase.query.Projection              ← ⚠️ 唯一正確路徑
tw.teddysoft.ezddd.cqrs.usecase.query.Projection.ProjectionInput  ← Inner class
tw.teddysoft.ezddd.cqrs.usecase.query.Archive
```

## Domain Event Data 類別

> **⚠️ DomainEventMapper.toDomain() 只接受 1 個參數**（ADR-047 auto-registration）：
> `DomainEventMapper.toDomain(eventData)` — 不要傳 mapper 作為第二參數！

```
tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventData
tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventMapper
tw.teddysoft.ezddd.usecase.port.inout.domainevent.ExternalDomainEvent
tw.teddysoft.ezddd.usecase.port.inout.domainevent.ExternalDomainEventDto
tw.teddysoft.ezddd.usecase.port.inout.domainevent.InternalDomainEventDto
```

## Messaging 類別

> ⛔ **Reactor 不在此 package！**
> `Reactor` 的正確 import 是 `tw.teddysoft.ezddd.usecase.port.in.interactor.Reactor`（見下方）。
> `tw.teddysoft.ezddd.usecase.port.inout.messaging` 只有 MessageProducer 和 PostEventFailureException。

```
tw.teddysoft.ezddd.usecase.port.inout.messaging.MessageProducer
tw.teddysoft.ezddd.usecase.port.inout.messaging.PostEventFailureException
```

## Reactor Pattern 類別

```
tw.teddysoft.ezddd.usecase.port.in.interactor.Reactor       ← ✅ 唯一正確路徑
tw.teddysoft.ezddd.usecase.port.in.interactor.GenericReactor
```

## Outbox Infrastructure 類別

> ⛔ **`OutboxStore` 是 abstract class，不可直接 import 或用 explicit type 宣告！**
> 必須用 `var` 讓編譯器推斷：
> ```java
> // ✅ CORRECT
> var outboxStore = EzOutboxStoreAdapter.createOutboxStore(outboxClient);
> // ❌ WRONG — OutboxStore 是 abstract，無法直接 new 或用 explicit type
> OutboxStore<...> outboxStore = EzOutboxStoreAdapter.createOutboxStore(outboxClient);
> ```

```
tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxRepositoryPeer
tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxStore
tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxClient
tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxStoreAdapter
tw.teddysoft.ezddd.data.io.ezoutbox.SpringJpaClient
tw.teddysoft.ezddd.data.io.ezoutbox.OrmClient
```

## InMemory Infrastructure 類別 (v2.0.0 新增) ⭐

### Outbox InMemory 支援
```
tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmDb
tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmClient
```

### Event Store InMemory 支援
```
tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDb
tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDbClient
```

### Message Broker InMemory 支援

> ⛔ **`InMemoryMessageBroker` 沒有 `subscribe()` 方法！**
> 事件捕獲必須用 `InMemoryConsumer` + `InternalInMemoryMessageConsumer`：
> ```java
> // ✅ CORRECT — 測試事件捕獲正確模式
> var inMemoryConsumer = new InMemoryConsumer<>(inMemoryMessageBroker);
> var consumer = new InternalInMemoryMessageConsumer(reactorService, inMemoryConsumer);
> executorService.submit(consumer);
> // ❌ WRONG — subscribe() 方法不存在
> inMemoryMessageBroker.subscribe(reactorService);
> ```

```
tw.teddysoft.ezddd.message.broker.io.messagebroker.InMemoryMessageBroker
tw.teddysoft.ezddd.message.broker.adapter.out.producer.InMemoryMessageProducer
tw.teddysoft.ezddd.message.broker.adapter.out.producer.InMemoryProducer
tw.teddysoft.ezddd.message.broker.adapter.in.consumer.InMemoryConsumer
tw.teddysoft.ezddd.message.broker.adapter.in.consumer.internal.InternalInMemoryMessageConsumer
tw.teddysoft.ezddd.message.broker.adapter.in.consumer.external.ExternalInMemoryMessageConsumer
```

## Event Sourcing Infrastructure 類別

```
tw.teddysoft.ezddd.usecase.port.out.repository.impl.es.EsRepository
tw.teddysoft.ezddd.usecase.port.out.repository.impl.es.EventStoreData
tw.teddysoft.ezddd.usecase.port.out.repository.impl.es.EventStoreMapper
tw.teddysoft.ezddd.data.adapter.repository.es.EsRepositoryPeer
tw.teddysoft.ezddd.data.adapter.repository.es.EventStore
tw.teddysoft.ezddd.data.io.ezes.store.PgMessageDbClient
tw.teddysoft.ezddd.data.io.ezes.store.MessageDbClient
tw.teddysoft.ezddd.data.io.ezes.store.MessageData
tw.teddysoft.ezddd.data.EzesCatchUpRelay
tw.teddysoft.ezddd.data.EzesVolatileRelay
tw.teddysoft.ezddd.data.adapter.ezes.out.MessageDbToDomainEventDataConverter
```

## Testing Support 類別 (ezspec)

> **⚠️ 常見錯誤 1**: `@EzScenario` 的 import 必須是 `tw.teddysoft.ezspec.extension.junit5.EzScenario`。
> 禁止使用 `tw.teddysoft.ezspec.EzScenario`（不存在）或 `tw.teddysoft.ezspec.annotation.EzScenario`（不存在）。
>
> **⚠️ 常見錯誤 2**: Scenario 執行方法是 `.Execute()`（大寫 E），不是 `.run()` 或 `.execute()`。
>
> ⛔ **常見錯誤 3**: `EzSpecExtension` **不存在**於 ezspec library！
> 禁止在 `BaseSpringBootTest` 加 `@ExtendWith(EzSpecExtension.class)` — 會造成 test-compile 失敗。
> `@EzScenario` 只是包裝了 `@Test` 的 meta-annotation，不需要任何 JUnit Extension。

```
tw.teddysoft.ezspec.extension.junit5.EzScenario   ← ⚠️ 唯一正確的 import 路徑
tw.teddysoft.ezspec.EzFeature                      ← Feature 報告用
tw.teddysoft.ezspec.EzFeatureReport                ← Feature 報告產生
tw.teddysoft.ezspec.keyword.Feature
tw.teddysoft.ezspec.keyword.Scenario              ← API: .Given() .When() .Then() .And() .Execute()
tw.teddysoft.ezspec.visitor.PlainTextReport
```

## Design by Contract 類別 (ucontract)

```
tw.teddysoft.ucontract.Contract
tw.teddysoft.ucontract.PreconditionViolationException
tw.teddysoft.ucontract.PostconditionViolationException
tw.teddysoft.ucontract.ClassInvariantViolationException
```

## Utility 類別

```
tw.teddysoft.ezddd.common.Converter
tw.teddysoft.ezddd.data.adapter.repository.OptimisticLockingFailureException
```

## 使用說明

1. **查找類別**: 使用 Ctrl+F 搜尋類別名稱
2. **確認 import**: 複製完整的類別路徑作為 import
3. **避免猜測**: 如果類別不在此索引中，請參考實際專案中的 import 或搜尋 ezapp-starter 原始碼

## 重要注意事項

### 依賴配置
- ✅ 所有這些類別都包含在 `ezapp-starter:2.0.0` 中
- ✅ 不需要單獨引入 ezddd-core、ezcqrs 等依賴
- ✅ 使用 jakarta.persistence 而非 javax.persistence

### v2.0.0 版本變更 ⚠️
- ✅ 新增 InMemory 系列類別，可取代專案自訂的 `GenericInMemoryRepository`
- ✅ `BlockingMessageBus` 已移除，改用 `InMemoryMessageBroker`
- ✅ `OutboxRepositoryPeerAdapter` 改名為 `OutboxRepositoryPeer`
- ✅ 新增 `ExternalDomainEvent` 和相關 DTO 類別

### 正確的套件路徑
| 類別 | 正確 Import |
|------|------------|
| Entity 類別 | `tw.teddysoft.ezddd.entity.*` |
| Reactor | `tw.teddysoft.ezddd.usecase.port.in.interactor.Reactor` |
| Message Broker | `tw.teddysoft.ezddd.message.broker.*` |
| EsAggregateRoot | `tw.teddysoft.ezddd.entity.EsAggregateRoot` |
| OutboxRepositoryPeer | `tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxRepositoryPeer` |

### Reactor 介面使用 (ADR-031)

> **⚠️ 方法名稱是 `execute()`，不是 `react()`！**

```java
// 正確：Reactor 必須繼承 Reactor<DomainEventData>，方法名是 execute()
public interface MyReactor extends Reactor<DomainEventData> {
    // void execute(DomainEventData message)  ← 正確方法名
    // void react(DomainEventData message)    ← ❌ 不存在！
}
```

### InMemory Repository 配置範例 (v2.0.0)

> ⚠️ **Aggregate-Specific 模式**：實際專案中應使用 `ProductInMemoryRepositoryConfig.java` 等
> Aggregate-Specific 命名，檔案位於 `[aggregate]/io/springboot/config/` 目錄。
> 參考：`references/templates/aggregate-config-template.md`

```java
// 使用框架提供的 InMemory 類別，不再需要專案自訂的 GenericInMemoryRepository
// 注意：實際使用時類別名稱應為 [Aggregate]InMemoryRepositoryConfig
@Configuration
@Profile({"inmemory", "test-inmemory"})
public class ProductInMemoryRepositoryConfig {  // Aggregate-Specific 命名

    // Step 1: Per-aggregate data store (共享給 Projection 讀取)
    @Bean
    public Map<String, ProductData> productDataStore() {
        return new ConcurrentHashMap<>();
    }

    // Step 2: ⚠️ 必須用 Map 參數建構，禁止無參建構子 new InMemoryOrmDb<>()
    @Bean
    public InMemoryOrmDb<ProductData> productOrmDb(
            Map<String, ProductData> productDataStore) {
        return new InMemoryOrmDb<>(productDataStore);
    }

    // 🔴 InMemoryMessageDb 和 InMemoryMessageDbClient 是 shared beans
    // 定義在 SharedInfrastructureConfig，此處透過參數注入

    @Bean("productRepository")  // 明確命名：InMemory 和 Outbox config 必須用相同 bean name
    public Repository<Product, ProductId> productRepository(
            InMemoryOrmDb<ProductData> ormDb,
            InMemoryMessageDbClient messageDbClient) {  // ← injected from SharedInfrastructureConfig

        InMemoryOrmClient<ProductData> ormClient = new InMemoryOrmClient<>(ormDb);
        EzOutboxClient<ProductData, String> outboxClient =
            new EzOutboxClient<>(ormClient, messageDbClient);
        OutboxStore<ProductData, String> outboxStore =
            EzOutboxStoreAdapter.createOutboxStore(outboxClient);
        OutboxRepositoryPeer<ProductData, String> peer =
            new OutboxRepositoryPeer<>(outboxStore);

        return new OutboxRepository<>(peer, ProductMapper.newMapper());
    }
}
```
