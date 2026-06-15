# 🚨 共用類別（必須產生）🚨

# [rootPackage] 內容參考 .dev/project-config.json#rootPackage

## ezapp 2.0.0 版本說明

從 ezapp 2.0.0 開始，框架提供了完整的 InMemory 實作類別，**不再需要專案自訂的 GenericInMemoryRepository**。

### 框架提供的 InMemory 類別：
- `tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmDb` - 資料儲存
- `tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmClient` - ORM 客戶端
- `tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDb` - 事件儲存
- `tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDbClient` - 事件儲存客戶端
- `tw.teddysoft.ezddd.message.broker.io.messagebroker.InMemoryMessageBroker` - 訊息代理
- `tw.teddysoft.ezddd.message.broker.adapter.out.producer.InMemoryMessageProducer` - 訊息生產者

---

# 🚨 Code for DateProvider in the [rootPackage].common.entity package 🚨
# ⚠️ 重要：必須放在 src/main/java 目錄，不是 test 目錄
# 完整路徑：src/main/java/[rootPackage]/common/entity/DateProvider.java
```java
package [rootPackage].common.entity;

import java.time.Instant;

public class DateProvider {

    private static Instant fixedInstant = null;

    public static Instant now() {
        if (fixedInstant != null) {
            return fixedInstant;
        }
        return Instant.now();
    }

    public static void useFixedInstant(Instant instant) {
        fixedInstant = instant;
    }

    public static void useSystemTime() {
        fixedInstant = null;
    }
}
```

---

# 📌 ezapp 2.0.0 InMemory Repository 配置範例

> **⚠️ DEPRECATED PATTERN**: 以下展示的是**集中式** `InMemoryRepositoryConfig`，已被
> **Aggregate-Specific Configuration Pattern** 取代。每個 Aggregate 應有獨立的
> `[Aggregate]InMemoryRepositoryConfig.java`（在 `[aggregate]/io/springboot/config/` 下），
> 且 `@Bean` 必須指定明確名稱（如 `@Bean("productRepository")`）以確保 Profile isolation。
>
> 正確範例請參見：`references/templates/aggregate-config-template.md`

```java
// ⚠️ DEPRECATED — 僅供參考，不要在新專案使用此集中式模式
package [rootPackage].io.springboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxRepositoryPeer;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxStore;
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxClient;
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxStoreAdapter;
import tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmClient;
import tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmDb;
import tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDb;
import tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDbClient;
import tw.teddysoft.ezddd.usecase.port.out.repository.Repository;
import tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxRepository;

@Configuration
@Profile({"inmemory", "test-inmemory"})
public class InMemoryRepositoryConfig {

    // ========== 共用的 InMemory 基礎設施 ==========

    @Bean
    public InMemoryMessageDb inMemoryMessageDb() {
        return new InMemoryMessageDb();
    }

    @Bean
    public InMemoryMessageDbClient inMemoryMessageDbClient(InMemoryMessageDb messageDb) {
        return new InMemoryMessageDbClient(messageDb);
    }

    // ========== Product Aggregate ==========
    // ⚠️ 注意：正式 Aggregate-Specific 配置中 @Bean 必須指定名稱，如 @Bean("productRepository")

    @Bean
    public InMemoryOrmDb<ProductData> productOrmDb(
            Map<String, ProductData> productDataStore) {
        return new InMemoryOrmDb<>(productDataStore);
    }

    @Bean("productRepository") // ⚠️ 必須指定 bean name 以確保 Profile isolation
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

    // ========== Sprint Aggregate ==========

    @Bean
    public InMemoryOrmDb<SprintData> sprintOrmDb(
            Map<String, SprintData> sprintDataStore) {
        return new InMemoryOrmDb<>(sprintDataStore);
    }

    @Bean("sprintRepository") // ⚠️ 必須指定 bean name 以確保 Profile isolation
    public Repository<Sprint, SprintId> sprintRepository(
            InMemoryOrmDb<SprintData> sprintOrmDb,
            InMemoryMessageDbClient messageDbClient) {

        InMemoryOrmClient<SprintData> ormClient = new InMemoryOrmClient<>(sprintOrmDb);
        EzOutboxClient<SprintData, String> outboxClient =
            new EzOutboxClient<>(ormClient, messageDbClient);
        var outboxStore = EzOutboxStoreAdapter.createOutboxStore(outboxClient);  // ⚠️ MUST use var — OutboxStore is abstract
        OutboxRepositoryPeer<SprintData, String> peer =
            new OutboxRepositoryPeer<>(outboxStore);

        return new OutboxRepository<>(peer, SprintMapper.newMapper());
    }

    // 依此類推，為其他 Aggregate 配置 Repository...
}
```

## 優點

1. **API 一致性** - InMemory 和 Outbox (PostgreSQL) 使用相同的 Repository 介面
2. **無縫切換** - 只需切換 Profile 即可從 InMemory 切換到真實資料庫
3. **測試友好** - InMemory 模式不需要資料庫，測試更快速
4. **框架支援** - 由框架提供，維護成本低

## 測試中清理資料

```java
@BeforeEach
void setUp() {
    // 清理 OrmDb
    if (productOrmDb != null) {
        // InMemoryOrmDb.clear() 清理儲存的資料
    }

    // 清理 MessageDb - 可透過重建 InMemoryMessageDb 來清理
}
```

## 注意事項

- `InMemoryOrmDb` 提供 `clear()` 方法用於測試前清理資料（搭配 `@DirtiesContext(AFTER_EACH_TEST_METHOD)` 使用）
- `InMemoryMessageDb` 可透過重新實例化來清理
- 建議每個測試類別使用獨立的 Spring Context 以避免資料污染
