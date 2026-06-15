# Aggregate Configuration Generation Templates

## 使用時機

當 code executor 或 sub-agent 產生新的 Aggregate 時，使用這些模板產生對應的配置類別。

## Template 1: [Aggregate]InMemoryRepositoryConfig.java

<!-- @authority: inmemory_ormdb_map_constructor | source: patterns/infrastructure/config.md#Rule-6 -->
<!-- @authority: repository_bean_naming | source: patterns/infrastructure/config.md#Rule-2 -->

**位置**: `[aggregate]/io/springboot/config/[Aggregate]InMemoryRepositoryConfig.java`

```java
package tw.teddysoft.aiscrum.[aggregate].io.springboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tw.teddysoft.aiscrum.[aggregate].entity.[Aggregate];
import tw.teddysoft.aiscrum.[aggregate].entity.[Aggregate]Id;
import tw.teddysoft.aiscrum.[aggregate].usecase.port.[Aggregate]Mapper;
import tw.teddysoft.aiscrum.[aggregate].usecase.port.out.[Aggregate]Data;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxRepositoryPeer;
import tw.teddysoft.ezddd.data.io.ezes.store.InMemoryMessageDbClient;
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxClient;
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxStoreAdapter;
import tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmClient;
import tw.teddysoft.ezddd.data.io.ezoutbox.InMemoryOrmDb;
import tw.teddysoft.ezddd.usecase.port.out.repository.Repository;
import tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Profile({"inmemory", "test-inmemory"})
public class [Aggregate]InMemoryRepositoryConfig {

    @Bean
    public Map<String, [Aggregate]Data> [aggregate]DataStore() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public InMemoryOrmDb<[Aggregate]Data> [aggregate]OrmDb(
            Map<String, [Aggregate]Data> [aggregate]DataStore) {
        return new InMemoryOrmDb<>([aggregate]DataStore);  // ⚠️ MUST use Map-arg (config.md Rule 6)
    }

    @Bean("[aggregate]Repository")
    public Repository<[Aggregate], [Aggregate]Id> [aggregate]Repository(
            InMemoryOrmDb<[Aggregate]Data> ormDb,
            InMemoryMessageDbClient messageDbClient) {

        InMemoryOrmClient<[Aggregate]Data> ormClient = new InMemoryOrmClient<>(ormDb);
        EzOutboxClient<[Aggregate]Data, String> outboxClient =
                new EzOutboxClient<>(ormClient, messageDbClient);
        var outboxStore = EzOutboxStoreAdapter.createOutboxStore(outboxClient);  // ⚠️ MUST use var — OutboxStore is abstract
        OutboxRepositoryPeer<[Aggregate]Data, String> peer =
                new OutboxRepositoryPeer<>(outboxStore);

        return new OutboxRepository<>(peer, [Aggregate]Mapper.newMapper());
    }

    // TODO: Add Projection beans if needed
    // @Bean
    // public [Aggregate]sProjection [aggregate]sProjection(Map<String, [Aggregate]Data> dataStore) {
    //     return new InMemory[Aggregate]sProjection(dataStore);
    // }
}
```

## Template 2: [Aggregate]OutboxRepositoryConfig.java

<!-- @authority: repository_bean_naming | source: patterns/infrastructure/config.md#Rule-2 -->

**位置**: `[aggregate]/io/springboot/config/[Aggregate]OutboxRepositoryConfig.java`

⚠️ **WARNING**: Do NOT add `@EnableJpaRepositories`, `@EntityScan`, `@EnableTransactionManagement`,
or `@PersistenceContext EntityManager` here — `SharedOutboxConfig` already handles JPA scanning globally.
Adding these causes `BeanDefinitionOverrideException` (duplicate bean registration).

```java
package tw.teddysoft.aiscrum.[aggregate].io.springboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tw.teddysoft.aiscrum.[aggregate].entity.[Aggregate];
import tw.teddysoft.aiscrum.[aggregate].entity.[Aggregate]Id;
import tw.teddysoft.aiscrum.[aggregate].usecase.port.[Aggregate]Mapper;
import tw.teddysoft.aiscrum.[aggregate].usecase.port.out.[Aggregate]Data;
import tw.teddysoft.aiscrum.[aggregate].io.springboot.config.orm.[Aggregate]OrmClient;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxRepositoryPeer;
import tw.teddysoft.ezddd.data.io.ezes.store.PgMessageDbClient;
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxClient;
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxStoreAdapter;
import tw.teddysoft.ezddd.usecase.port.out.repository.Repository;
import tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxRepository;

@Configuration
@Profile({"outbox", "test-outbox"})
public class [Aggregate]OutboxRepositoryConfig {

    @Bean("[aggregate]Repository")
    public Repository<[Aggregate], [Aggregate]Id> [aggregate]Repository(
            [Aggregate]OrmClient [aggregate]OrmClient,
            PgMessageDbClient pgMessageDbClient) {

        EzOutboxClient<[Aggregate]Data, String> outboxClient =
                new EzOutboxClient<>([aggregate]OrmClient, pgMessageDbClient);
        var outboxStore = EzOutboxStoreAdapter.createOutboxStore(outboxClient);  // ⚠️ MUST use var — OutboxStore is abstract
        OutboxRepositoryPeer<[Aggregate]Data, String> peer =
                new OutboxRepositoryPeer<>(outboxStore);

        return new OutboxRepository<>(peer, [Aggregate]Mapper.newMapper());
    }

    // TODO: Add Projection beans for IDF queries if needed
    // ⚠️ Outbox Projection 注入 OrmClient，不是 Map<String, Data>！
    // @Bean
    // public [Aggregate]sProjection [aggregate]sProjection([Aggregate]OrmClient ormClient) {
    //     return input -> StreamSupport.stream(ormClient.findAll().spliterator(), false)
    //             .filter(d -> ...)
    //             .map([Aggregate]Mapper::toDto)
    //             .collect(Collectors.toList());
    // }
}
```

## Template 3: [Aggregate]UseCaseConfig.java

<!-- @authority: usecaseconfig_no_profile | source: patterns/infrastructure/config.md#Rule-5 -->

**位置**: `[aggregate]/io/springboot/config/[Aggregate]UseCaseConfig.java`

```java
package tw.teddysoft.aiscrum.[aggregate].io.springboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tw.teddysoft.aiscrum.[aggregate].entity.[Aggregate];
import tw.teddysoft.aiscrum.[aggregate].entity.[Aggregate]Id;
import tw.teddysoft.aiscrum.[aggregate].usecase.port.in.Create[Aggregate]UseCase;
// import other use cases...
import tw.teddysoft.aiscrum.[aggregate].usecase.service.Create[Aggregate]Service;
// import other services...
import tw.teddysoft.ezddd.usecase.port.out.repository.Repository;

@Configuration
public class [Aggregate]UseCaseConfig {

    @Bean
    public Create[Aggregate]UseCase create[Aggregate]UseCase(
            Repository<[Aggregate], [Aggregate]Id> [aggregate]Repository) {
        return new Create[Aggregate]Service([aggregate]Repository);
    }

    // TODO: Add other use case beans as needed
}
```

## Template 4: [Aggregate]OrmClient.java

<!-- @authority: ormclient_extends_springjpaclient | source: patterns/infrastructure/config.md#Rule-4 -->

**位置**: `[aggregate]/io/springboot/config/orm/[Aggregate]OrmClient.java`

> **⛔ CRITICAL**: 必須繼承 `SpringJpaClient`，禁止使用 `CrudRepository` 或 `JpaRepository`。
> `SpringJpaClient` 整合了 `OrmClient + CrudRepository`，是 `EzOutboxClient` 組裝鏈所需的唯一介面。

```java
package tw.teddysoft.aiscrum.[aggregate].io.springboot.config.orm;

import tw.teddysoft.aiscrum.[aggregate].usecase.port.out.[Aggregate]Data;
import tw.teddysoft.ezddd.data.io.ezoutbox.SpringJpaClient;

// ⛔ 必須 extends SpringJpaClient，不可用 CrudRepository/JpaRepository
public interface [Aggregate]OrmClient extends SpringJpaClient<[Aggregate]Data, String> {
}
```

## Placeholder Replacements

| Placeholder | Description | Example |
|-------------|-------------|---------|
| `[Aggregate]` | Aggregate 名稱（PascalCase） | `Product`, `Sprint`, `Workflow` |
| `[aggregate]` | Aggregate 名稱（camelCase） | `product`, `sprint`, `workflow` |

## Generation Checklist

當產生新 Aggregate 的配置時：

### 必須產生的檔案

- [ ] `[aggregate]/io/springboot/config/[Aggregate]InMemoryRepositoryConfig.java`
- [ ] `[aggregate]/io/springboot/config/[Aggregate]UseCaseConfig.java`

### 如果使用 Outbox Pattern

- [ ] `[aggregate]/io/springboot/config/[Aggregate]OutboxRepositoryConfig.java`
- [ ] `[aggregate]/io/springboot/config/orm/[Aggregate]OrmClient.java`
- [ ] `[aggregate]/usecase/port/out/[Aggregate]Data.java` — ⚠️ **MUST follow `persistent-object.md` rules!** Data class must `implements OutboxData<String>` (interface, NOT extends class). See: `references/patterns/infrastructure/persistent-object.md`
- [ ] `[aggregate]/usecase/port/[Aggregate]Mapper.java` — ⚠️ **MUST follow `mapper.md` rules!** OutboxMapper has exactly 2 type params: `OutboxMapper<Product, ProductData>` (example). `toDomain()` 必須以 `setVersion()` → `clearDomainEvents()` 收尾（順序不可反）。See: `references/patterns/infrastructure/mapper.md` <!-- @authority: outbox_mapper_two_params | source: patterns/infrastructure/mapper.md -->

### 共用檔案（必須存在，不需修改）⭐⭐⭐

以下檔案**不需要修改**，但**必須存在**。如果不存在，Repository 無法正常運作：

- ✅ `common/io/springboot/config/SharedInfrastructureConfig.java` — 提供 InMemory 基礎設施（必須存在）
- ✅ `common/io/springboot/config/SharedOutboxConfig.java` — 提供 JPA 掃描和 PgMessageDbClient（必須存在）
- ✅ `common/io/springboot/config/DomainEventMapperConfig.java` — ADR-047 事件自動註冊（**必須存在**，否則 `DomainEventMapper.toData()` 會拋出 `"Require [Please call setMapper to config DomainEventMapper first] cannot be null"`）

> **⚠️ 如果這些檔案不存在**：先執行 `/init-project` 產生，或手動從 `references/init-project/templates.md` 取得模板。
> 執行 Use Case 時若尚未 `/init-project`，Phase 2 必須確認這些檔案存在，否則會 Runtime Error。

## Notes

1. **SharedInfrastructureConfig** 必須已存在於 `common/io/springboot/config/`，提供 `InMemoryMessageDb` 和 `InMemoryMessageDbClient`。⚠️ 注意：此 Config 需要注入 `relayExecutor` bean（由 `VolatileRelayConfig` 提供），請確認 `connectionframe/VolatileRelayConfig.java` 已建立（見 `patterns/infrastructure/config.md` Rule 6）
2. **Bean 名稱** 使用 `[aggregate]Repository` 格式（如 `productRepository`）
3. **Projection beans** 必須 profile-aware：InMemory 放 InMemoryRepositoryConfig（注入 Map），Outbox 放 OutboxRepositoryConfig 或獨立 OutboxProjectionConfig（注入 OrmClient）。**絕對不可**放在 profile-neutral 的 UseCaseConfig（見 query.md Rule 12）
4. **OrmClient** 放在 `[aggregate]/io/springboot/config/orm/` 目錄
