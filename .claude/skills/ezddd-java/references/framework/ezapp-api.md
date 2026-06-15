# ezapp-starter API 參考指南 🚀

## 關於 ezapp-starter

`ezapp-starter` 是一個整合框架，版本 2.0.0，包含了所有 EZDDD、CQRS、Event Sourcing 相關的功能。

**Maven 依賴：**
```xml
<dependency>
    <groupId>tw.teddysoft.ezapp</groupId>
    <artifactId>ezapp-starter</artifactId>
    <version>2.0.0</version>
</dependency>
```

## 🔥 重要：ezapp-starter 已包含以下所有框架

不需要單獨引入以下依賴，因為 ezapp-starter 已經包含：
- ezddd-core
- ezddd-gateway
- ezddd-postgres  
- ezcqrs
- ezspec
- ucontract

## 📦 核心套件結構與類別

### 1. Entity Layer (tw.teddysoft.ezddd.entity.*)

#### 基礎類別
```java
// Aggregate Root (Event Sourcing 版本)
import tw.teddysoft.ezddd.entity.EsAggregateRoot;
import tw.teddysoft.ezddd.entity.AggregateRoot;

// Domain Events
import tw.teddysoft.ezddd.entity.DomainEvent;
import tw.teddysoft.ezddd.entity.InternalDomainEvent;      // 內部領域事件
import tw.teddysoft.ezddd.entity.DomainEventTypeMapper;   // 事件類型映射

// Value Objects & Entity
import tw.teddysoft.ezddd.entity.ValueObject;
import tw.teddysoft.ezddd.entity.Entity;
```

#### 常用 Aggregate Root 方法
```java
// EsAggregateRoot 有兩個泛型參數：ID 類型和 Events 類型
public abstract class EsAggregateRoot<ID, Events extends InternalDomainEvent> {
    protected void apply(Events event);              // ✅ 發出 domain event → 觸發 when()
    protected abstract void when(Events event);      // ✅ 事件處理（狀態只在這裡設定）
    protected void clearDomainEvents();
    public List<DomainEvent> getDomainEvents();
    public long getVersion();
    public String getStreamName();
}
// ❌ WRONG — 以下方法不存在：addDomainEvent(), raiseEvent()
```

### 2. Use Case Layer (tw.teddysoft.ezddd.usecase.* & tw.teddysoft.ezddd.cqrs.*)

#### Command/Query 基礎
```java
// CQRS Command/Query Pattern
import tw.teddysoft.ezddd.cqrs.usecase.command.Command;
import tw.teddysoft.ezddd.cqrs.usecase.query.Query;
import tw.teddysoft.ezddd.cqrs.usecase.CqrsOutput;           // CQRS 輸出物件

// Input/Output
import tw.teddysoft.ezddd.usecase.port.in.interactor.Input;

// Use Case Exceptions & Exit Codes
import tw.teddysoft.ezddd.usecase.port.in.interactor.ExitCode;                  // 退出碼枚舉
import tw.teddysoft.ezddd.usecase.port.in.interactor.UseCaseFailureException;   // Use Case 失敗例外
```

#### Repository Pattern
```java
// 基礎 Repository
import tw.teddysoft.ezddd.usecase.port.out.repository.Repository;

// Outbox Pattern
import tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxRepository;
import tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxData;
import tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxMapper;
```

#### Projection Pattern
```java
// Projection 有兩個泛型參數：Input 和 Output
import tw.teddysoft.ezddd.cqrs.usecase.query.Projection;
import tw.teddysoft.ezddd.cqrs.usecase.query.ProjectionInput;

// 使用範例
public interface ProductsProjection extends Projection<ProductsProjectionInput, List<ProductData>> {
    // query 方法從 Projection 介面繼承
}
```

#### Inquiry Pattern (跨聚合查詢)
```java
// 注意：Inquiry 在實際專案中通常是自定義介面，不繼承框架類別
// 範例：FindPbisBySprintIdInquiry 是專案自定義的介面
// 位置：[rootPackage].[aggregate].usecase.port.out.inquiry
```

#### Archive Pattern (Query Model CRUD)
```java
import tw.teddysoft.ezddd.cqrs.usecase.query.Archive;
```

#### Reactor Pattern
```java
// Reactor 必須繼承 Reactor<DomainEventData>
import tw.teddysoft.ezddd.usecase.port.in.interactor.Reactor;
import tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventData;
```

### 3. Domain Event Support

```java
// Event Data & Mapper
import tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventData;
import tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventMapper;
```

#### DomainEventMapper 使用注意事項 (P2)

由於 Java 類型擦除，在 stream 中使用時需要明確轉型：

```java
// ✅ 正確：明確轉型
List<DomainEvent> events = eventDatas.stream()
    .map(data -> (DomainEvent) DomainEventMapper.toDomain(data))
    .collect(Collectors.toList());

// ❌ 錯誤：會導致 Type mismatch: List<Object> to List<DomainEvent>
List<DomainEvent> events = eventDatas.stream()
    .map(DomainEventMapper::toDomain)  // 返回 Object 類型
    .collect(Collectors.toList());
```

### 4. Message Support

```java
// Message Bus & Producer
import tw.teddysoft.ezddd.usecase.port.inout.messaging.MessageBus;
import tw.teddysoft.ezddd.usecase.port.inout.messaging.MessageProducer;

// ezapp 2.0.0: InMemory Message Broker (框架提供)
import tw.teddysoft.ezddd.message.broker.io.messagebroker.InMemoryMessageBroker;
import tw.teddysoft.ezddd.message.broker.adapter.out.producer.InMemoryMessageProducer;
import tw.teddysoft.ezddd.message.broker.adapter.in.consumer.InMemoryConsumer;  // 🆕 Polling Consumer
import tw.teddysoft.ezddd.usecase.port.inout.messaging.PostEventFailureException;
```

#### ⚠️ InMemoryMessageBroker 架構 (CRITICAL - P0)

**ezapp 2.0.0 採用 pub/sub + polling 模式：**

```
Producer 端                              Consumer 端
─────────────────                       ─────────────────
InMemoryProducer                        InMemoryConsumer
      ↓ publish                              ↓ subscribe + poll
InMemoryMessageBroker ═══════════════> InternalInMemoryMessageConsumer
      (pub/sub broker)                       ↓ execute
                                        Reactor<DomainEventData>
```

**生產環境配置（pub/sub 模式）：**
```java
// 1. 建立 Broker（共用）
@Bean
public InMemoryMessageBroker<DomainEventData> inMemoryMessageBroker() {
    return new InMemoryMessageBroker<>();
}

// 2. 建立 Consumer（訂閱 broker）
@Bean
public InMemoryConsumer<DomainEventData> pbiInMemoryConsumer(
        InMemoryMessageBroker<DomainEventData> broker) {
    return new InMemoryConsumer<>(broker);  // 內部封裝 polling 邏輯
}

// 3. 建立內部 Consumer（包裝 Reactor）
@Bean
public InternalInMemoryMessageConsumer notifyPbiConsumer(
        Reactor<DomainEventData> reactor,
        InMemoryConsumer<DomainEventData> consumer) {
    return new InternalInMemoryMessageConsumer(reactor, consumer);
}

// 4. 背景執行（在 CommandLineRunner 中）
executorService.submit(internalConsumer);  // Runnable，內部會 loop + poll
```

**⚠️ 重要：`poll(fromIndex, maxRecords)` 不是使用者直接呼叫的 API！**
- 封裝在 `InMemoryConsumer` 內部
- 由 `InternalInMemoryMessageConsumer` 的 `run()` 方法在背景 loop 中執行
- 使用者只需透過 `ExecutorService.submit()` 啟動 consumer

**測試環境（直接使用 poll 驗證事件）：**
```java
// BaseUseCaseTest 中直接使用 poll() 進行同步驗證（不需啟動背景 loop）
private int lastPolledIndex = 0;

protected List<DomainEvent> getCapturedEvents() {
    // 測試時直接呼叫 poll()，這是測試專用的驗證方式
    List<DomainEventData> polled = consumer.poll(lastPolledIndex, 1000);
    return polled.stream()
            .map(data -> (DomainEvent) DomainEventMapper.toDomain(data))
            .collect(Collectors.toList());
}

protected void clearCapturedEvents() {
    lastPolledIndex = (int) inMemoryMessageBroker.size();
}
```

### 5. PostgreSQL/Outbox Support (tw.teddysoft.ezddd.data.*)

```java
// PgMessageStore (需透過 JpaRepositoryFactory 創建)
import tw.teddysoft.ezddd.data.io.ezes.store.PgMessageDbClient;

// Event Store
import tw.teddysoft.ezddd.data.EzesCatchUpRelay;
```

### 6. 專案自訂共用類別

⚠️ **ezapp 2.0.0 更新**：從 ezapp 2.0.0 開始，框架已提供完整的 InMemory 實作，專案只需自行實作 DateProvider：

```java
// DateProvider - 統一的日期時間管理（放在 [rootPackage].common.entity）
// 範例：tw.teddysoft.aiscrum.common.entity.DateProvider
// 這是唯一需要專案自行實作的共用類別
public class DateProvider {
    public static Instant now() { /* 實作 */ }
    public static void useFixedInstant(Instant instant) { /* 測試用 */ }
    public static void useSystemTime() { /* 恢復系統時間 */ }
}

// ⚠️ 以下類別已棄用，ezapp 2.0.0 使用框架提供的類別：
// ❌ GenericInMemoryRepository (已棄用) → 使用 OutboxRepository + InMemoryOrmClient
// ❌ MyInMemoryMessageBroker (已棄用) → 使用 tw.teddysoft.ezddd.message.broker.io.messagebroker.InMemoryMessageBroker
// ❌ MyInMemoryMessageProducer (已棄用) → 使用 tw.teddysoft.ezddd.message.broker.adapter.out.producer.InMemoryMessageProducer
```

詳細實作請參考：`references/templates/local-utils.md`

### 7. Testing Support (tw.teddysoft.ezspec.*)

```java
// BDD Testing - JUnit 5 Extension
import tw.teddysoft.ezspec.extension.junit5.EzScenario;

// Feature & Reporting
import tw.teddysoft.ezspec.EzFeature;
import tw.teddysoft.ezspec.EzFeatureReport;
import tw.teddysoft.ezspec.keyword.Feature;
import tw.teddysoft.ezspec.visitor.PlainTextReport;

// JUnit 5 BDD Annotations
import tw.teddysoft.ezspec.extension.junit5.EzScenario;
```

**PlainTextReport 使用方式 (Visitor Pattern)**：
```java
// ⚠️ PlainTextReport 使用 Visitor Pattern，沒有 generate() 方法！
PlainTextReport report = new PlainTextReport();
feature.accept(report);                      // ✅ Visitor: feature 接受 report
System.out.println(report.getOutput());      // ✅ 取得報告輸出

// ❌ WRONG: generate() 方法不存在
// System.out.println(report.generate(feature));  // 編譯失敗！
```

### 8. Design by Contract (tw.teddysoft.ucontract.*)

```java
// 🔴 重要：僅用於 EsAggregateRoot 及其子類別！
// ValueObject、Entity、Domain Events 使用 Objects.requireNonNull
import static tw.teddysoft.ucontract.Contract.*;

// 主要方法：
// - requireNotNull(String name, Object obj)  // 前置條件：檢查非空
// - require(String message, Supplier<Boolean> condition)  // 前置條件：檢查條件
// - ensureNotNull(String name, Object obj)  // 後置條件：檢查非空  
// - ensure(String message, Supplier<Boolean> condition)  // 後置條件：檢查條件
// - invariantNotNull(String name, Object obj)  // 不變式：檢查非空
// - invariant(String message, Supplier<Boolean> condition)  // 不變式：檢查條件
```

#### 使用規則：
- **EsAggregateRoot 及其子類別**：使用 `Contract.requireNotNull()`
- **ValueObject (record)**：使用 `Objects.requireNonNull()`  
- **Entity**：使用 `Objects.requireNonNull()`
- **Domain Events (record)**：使用 `Objects.requireNonNull()`
- **UseCase Service 建構子**：使用 `Objects.requireNonNull()`（依賴注入 null check，非 DBC）
- **Controller**：使用 `Objects.requireNonNull()`

## 🎯 實作範例

### 1. Aggregate Root 實作
```java
import tw.teddysoft.ezddd.entity.EsAggregateRoot;
import tw.teddysoft.ezddd.entity.InternalDomainEvent;
import static tw.teddysoft.ucontract.Contract.*;

// EsAggregateRoot 需要兩個泛型參數
public class Product extends EsAggregateRoot<ProductId, ProductEvents> {
    private ProductId id;
    private ProductName name;
    
    // 🔴 Event Sourcing Golden Rule: State can ONLY be set in when() method!
    // 建構子不可直接賦值 this.id = id，必須透過 apply() → when() 設定狀態
    public Product(ProductId id, ProductName name) {
        requireNotNull("id", id);
        requireNotNull("name", name);

        apply(new ProductEvents.ProductCreated(
            id,
            name,
            new HashMap<>(),    // 🔴 必須使用 mutable map（Use Case 層需要添加 userId 等 metadata）
            UUID.randomUUID(),
            DateProvider.now()  // 🔴 重要：使用 DateProvider.now()，不要用 Instant.now()
        ));
    }
    
    @Override
    public ProductId getId() {
        return id;
    }
    
    // Event Sourcing: 處理事件
    @Override
    protected void when(ProductEvents event) {
        switch (event) {
            case ProductEvents.ProductCreated e -> {
                this.id = e.productId();
                this.name = e.name();
            }
            default -> {}
        }
    }
}

// Domain Events 使用 sealed interface（🔴 重要：所有 events 在同一個檔案中）
// 🔴 不需要 permits clause（同一檔案中的子類型 Java 17+ 自動推斷）
public sealed interface ProductEvents extends InternalDomainEvent {

    // 共用方法：獲取 aggregate ID
    ProductId productId();

    // 🔴 重要：source() 回傳 aggregate instance ID，DRY：interface 層級定義一次
    @Override
    default String source() {
        return productId().value();
    }

    // 🔴 [Aggregate]Created 必須額外實作 ConstructionEvent
    record ProductCreated(
        ProductId productId,
        ProductName name,
        Map<String, String> metadata,
        UUID id,
        Instant occurredOn
    ) implements ProductEvents, InternalDomainEvent.ConstructionEvent {

        // 建構子可以加入驗證（Domain Events 也使用 Objects.requireNonNull）
        public ProductCreated {
            Objects.requireNonNull(productId, "productId cannot be null");
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(metadata, "metadata cannot be null");
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(occurredOn, "occurredOn cannot be null");
        }

        // 🔴 不需要 per-record source() override — 繼承自 interface default method
    }

    // 🔴 [Aggregate]Deleted 必須額外實作 DestructionEvent
    record ProductDeleted(
        ProductId productId,
        String reason,
        Map<String, String> metadata,
        UUID id,
        Instant occurredOn
    ) implements ProductEvents, InternalDomainEvent.DestructionEvent {
        // 不需要 per-record source() override — 繼承自 interface default method
    }
}
```

### 2. Value Object 實作
```java
import tw.teddysoft.ezddd.entity.ValueObject;
import java.util.Objects;

// 使用 record 並 implements ValueObject（不是 extends）
// 🔴 重要：ValueObject 使用 Objects.requireNonNull，不用 Contract
// 🔴 重要：ValueObject 使用 Objects.requireNonNull，不用 Contract
// 🔴 SPEC-DRIVEN：只加 spec 明確要求的驗證，不要「預防性」加 blank check
public record ProductId(String value) implements ValueObject {

    public ProductId {
        Objects.requireNonNull(value, "ProductId value cannot be null");
    }
    
    // 🔴 重要：必須覆寫 toString() 返回純值（對 Outbox Pattern 的 stream name 生成很重要）
    @Override
    public String toString() {
        return value;
    }
    
    // 必須提供 valueOf 方法（框架序列化需要）
    public static ProductId valueOf(String value) {
        return new ProductId(value);
    }

    public static ProductId valueOf(UUID uuid) {
        return new ProductId(uuid.toString());
    }

    public static ProductId create() {
        return new ProductId(UUID.randomUUID().toString());
    }
}
```

### 3. Entity 實作（Aggregate 內的 Entity）
```java
import tw.teddysoft.ezddd.entity.Entity;
import java.util.Objects;

// Entity 有泛型參數，使用 implements
// 🔴 重要：Entity 也使用 Objects.requireNonNull，不用 Contract
public class Task implements Entity<TaskId> {
    private final TaskId id;
    private String name;
    private TaskStatus status;
    
    public Task(TaskId id, String name) {
        this.id = Objects.requireNonNull(id, "Task id cannot be null");
        this.name = Objects.requireNonNull(name, "Task name cannot be null");
        this.status = TaskStatus.TODO;
    }
    
    @Override
    public TaskId getId() {
        return id;
    }
    
    public void moveToInProgress() {
        if (status != TaskStatus.TODO) {
            throw new IllegalStateException("Task must be TODO to move to IN_PROGRESS");
        }
        this.status = TaskStatus.IN_PROGRESS;
    }
}
```

### 4. Use Case 實作
```java
// UseCase 介面定義（extends Command 或 Query）
import tw.teddysoft.ezddd.cqrs.usecase.command.Command;
import tw.teddysoft.ezddd.cqrs.usecase.CqrsOutput;
import tw.teddysoft.ezddd.usecase.port.in.interactor.Input;

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

// Service 實作
import tw.teddysoft.ezddd.usecase.port.out.repository.Repository;

public class CreateProductService implements CreateProductUseCase {
    private final Repository<Product, ProductId> repository;
    
    public CreateProductService(Repository<Product, ProductId> repository) {
        this.repository = Objects.requireNonNull(repository);
    }
    
    @Override
    public CqrsOutput<?> execute(CreateProductInput input) {
        Product product = new Product(
            ProductId.valueOf(input.id),
            new ProductName(input.name)
        );
        repository.save(product);
        return CqrsOutput.create().setId(product.getId().value());
    }
}
```

### 5. Repository 配置

#### InMemory Repository (ezapp 2.0.0)
```java
// ezapp 2.0.0: InMemory 和 Outbox profile 都使用 OutboxRepository
// 差異在於底層的 OrmClient 和 MessageDbClient 實作
@Configuration
@Profile({"inmemory", "test-inmemory"})
public class InMemoryRepositoryConfig {

    @Bean
    public InMemoryOrmDb<ProductData> productOrmDb(
            Map<String, ProductData> productDataStore) {
        return new InMemoryOrmDb<>(productDataStore);
    }

    @Bean("productRepository")
    public Repository<Product, ProductId> productRepository(
            InMemoryOrmDb<ProductData> productOrmDb,
            InMemoryMessageDbClient messageDbClient) {
        InMemoryOrmClient<ProductData> ormClient = new InMemoryOrmClient<>(productOrmDb);
        EzOutboxClient<ProductData, String> outboxClient =
            new EzOutboxClient<>(ormClient, messageDbClient);
        var outboxStore = EzOutboxStoreAdapter.createOutboxStore(outboxClient);  // ⚠️ MUST use var — OutboxStore is abstract
        OutboxRepositoryPeer<ProductData, String> peer =
            new OutboxRepositoryPeer<>(outboxStore);
        return new OutboxRepository<>(peer, ProductMapper.newMapper());
    }
}
```

#### Outbox Repository
```java
// ✅ Profile Isolation: 與 InMemoryConfig 使用相同 bean name
@Configuration
@Profile({"outbox", "test-outbox"})
public class ProductOutboxRepositoryConfig {

    @Bean("productRepository")
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

### 6. Reactor 實作
```java
import tw.teddysoft.ezddd.usecase.port.in.interactor.Reactor;
import tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventData;
import tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventMapper;
import tw.teddysoft.ezddd.entity.InternalDomainEvent;

// ⚠️ 不用 @Component，透過 @Bean 在 ReactorConfig 中註冊
public class CommitSprintToProductWhenSprintCreatedService
        implements WhenSprintCreatedCommitItToProductReactor {

    @Override
    public void execute(DomainEventData message) {
        if (message == null) return;

        InternalDomainEvent domainEvent = DomainEventMapper.toDomain(message);

        if (domainEvent instanceof SprintEvents.SprintCreated event) {
            // 處理事件
        }
    }
}
```

## 🔧 Spring Configuration

### 必要的 Configuration 類別
```java
// 位置: common/io/springboot/config/SharedOutboxConfig.java
@Configuration
@Profile({"outbox", "test-outbox"})
@EnableJpaRepositories(basePackages = {
    "tw.teddysoft.aiscrum",  // 掃描所有 aggregate 下的 ORM clients 和 Projections
    "tw.teddysoft.ezddd.data.io.ezes.store"  // PgMessageDbClient
})
@EntityScan(basePackages = {
    "tw.teddysoft.aiscrum",  // 掃描所有 aggregate 下的 Data 類別
    "tw.teddysoft.ezddd.data.io.ezes.store"
})
public class SharedOutboxConfig {
}
```

## 🎯 專案自定義類別（不是框架提供）

以下是專案需要自行實作的類別：

### 1. DateProvider
```java
package [rootPackage].common.entity;

import java.time.Instant;

/**
 * 提供統一的時間管理機制
 * 🔴 重要：Domain Events 必須使用 DateProvider.now()，不要用 Instant.now()
 */
public class DateProvider {
    private static Instant fixedInstant;
    
    public static Instant now() {
        return fixedInstant != null ? fixedInstant : Instant.now();
    }
    
    // 測試用：固定時間
    public static void useFixedInstant(Instant instant) {
        fixedInstant = instant;
    }

    // 測試用：重置為系統時間
    public static void useSystemTime() {
        fixedInstant = null;
    }
}
```

### 2. InMemory Repository (ezapp 2.0.0 架構)

> **⚠️ ezapp 2.0.0 重大變更**：不再需要自訂 `GenericInMemoryRepository`。
> 框架提供完整的 InMemory 實作，InMemory 和 Outbox profile 都使用相同的 `OutboxRepository` 介面。

```java
// ezapp 2.0.0 InMemory 架構
//
// OutboxRepository
//   └── OutboxRepositoryPeer
//       └── OutboxStore
//           └── EzOutboxStoreAdapter.createOutboxStore()
//               └── EzOutboxClient
//                   ├── InMemoryOrmClient ←── InMemoryOrmDb (Data 層)
//                   └── InMemoryMessageDbClient ←── InMemoryMessageDb (Event 層)

// 必要的 imports
import tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmDb;
import tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmClient;
import tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDb;
import tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDbClient;
import tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxRepository;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxRepositoryPeer;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxStore;
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxStoreAdapter;
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxClient;
```

### 3. Inquiry Pattern（查詢模式）
```java
// Inquiry 介面（專案自定義，不是框架提供）
package [rootPackage].[aggregate].usecase.port.out.inquiry;

public interface Find[Entity]By[Criteria]Inquiry {
    List<[Entity]Data> findBy[Criteria](String criteria);
}

// JPA 實作
package [rootPackage].[aggregate].adapter.out.persistence.inquiry;

@Repository  // Inquiry 可以加 @Repository
public class JpaFind[Entity]By[Criteria]Inquiry implements Find[Entity]By[Criteria]Inquiry {
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public List<[Entity]Data> findBy[Criteria](String criteria) {
        // JPQL 查詢實作
    }
}
```

## ⚠️ 重要提醒

1. **不要嘗試重新實作框架類別** - 這些都由 ezapp-starter 提供
2. **Import 路徑必須正確** - 使用上述指定的 import 路徑
3. **PgMessageDbClient 必須透過 JpaRepositoryFactory 創建** - 不能用 new
4. **OutboxMapper 必須是內部類別** - 參考 FRAMEWORK-API-INTEGRATION-GUIDE.md
5. **使用 jakarta.persistence 而非 javax.persistence**

## 🔍 如何讓 AI 使用這份文件

當需要使用 ezapp-starter 的類別時，請參考本文件的 import 路徑，不要嘗試猜測或創建框架類別。

## 📊 快速查詢表

| 功能 | Import 路徑 | 用途 |
|-----|-----------|------|
| **Entity Layer** | | |
| EsAggregateRoot | tw.teddysoft.ezddd.entity.EsAggregateRoot | Event Sourcing 聚合根 |
| DomainEvent | tw.teddysoft.ezddd.entity.DomainEvent | 領域事件 |
| InternalDomainEvent | tw.teddysoft.ezddd.entity.InternalDomainEvent | 內部領域事件 |
| DomainEventTypeMapper | tw.teddysoft.ezddd.entity.DomainEventTypeMapper | 事件類型映射 |
| ValueObject | tw.teddysoft.ezddd.entity.ValueObject | 值物件基礎類別 |
| Entity | tw.teddysoft.ezddd.entity.Entity | 實體基礎類別 |
| **CQRS Layer** | | |
| Command | tw.teddysoft.ezddd.cqrs.usecase.command.Command | Command 註解 |
| Query | tw.teddysoft.ezddd.cqrs.usecase.query.Query | Query 註解 |
| CqrsOutput | tw.teddysoft.ezddd.cqrs.usecase.CqrsOutput | CQRS 輸出物件 |
| Projection | tw.teddysoft.ezddd.cqrs.usecase.query.Projection | 查詢投影 |
| Archive | tw.teddysoft.ezddd.cqrs.usecase.query.Archive | Query Model CRUD |
| **Use Case Layer** | | |
| Repository | tw.teddysoft.ezddd.usecase.port.out.repository.Repository | Repository 介面 |
| Reactor | tw.teddysoft.ezddd.usecase.port.in.interactor.Reactor | 事件處理器 |
| Input | tw.teddysoft.ezddd.usecase.port.in.interactor.Input | Use Case 輸入介面 |
| ExitCode | tw.teddysoft.ezddd.usecase.port.in.interactor.ExitCode | 退出碼枚舉 |
| UseCaseFailureException | tw.teddysoft.ezddd.usecase.port.in.interactor.UseCaseFailureException | Use Case 失敗例外 |
| DomainEventData | tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventData | 事件資料 |
| DomainEventMapper | tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventMapper | 事件映射器 |
| MessageBus | tw.teddysoft.ezddd.usecase.port.inout.messaging.MessageBus | 訊息匯流排 |
| InMemoryMessageBroker | tw.teddysoft.ezddd.message.broker.io.messagebroker.InMemoryMessageBroker | InMemory 訊息代理 (ezapp 2.0.0) |
| InMemoryMessageProducer | tw.teddysoft.ezddd.message.broker.adapter.out.producer.InMemoryMessageProducer | InMemory 訊息生產者 (ezapp 2.0.0) |
| InMemoryOrmDb | tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmDb | InMemory ORM 資料庫 (ezapp 2.0.0) |
| InMemoryOrmClient | tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmClient | InMemory ORM 客戶端 (ezapp 2.0.0) |
| InMemoryMessageDb | tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDb | InMemory 訊息資料庫 (ezapp 2.0.0) |
| InMemoryMessageDbClient | tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDbClient | InMemory 訊息客戶端 (ezapp 2.0.0) |
| **Testing** | | |
| EzScenario | tw.teddysoft.ezspec.extension.junit5.EzScenario | JUnit 5 BDD 測試 |
| **專案自訂** | | |
| DateProvider | [rootPackage].common.entity.DateProvider | 日期時間管理（需自行實作） |

## 🚀 使用建議

1. **建立專案時**：此文件已內建於 skill 的 `references/framework/` 目錄
2. **定期更新**：當 ezapp-starter 版本更新時，同步更新此文件