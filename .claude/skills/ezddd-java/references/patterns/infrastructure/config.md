---
name: config-skill
description: |
  Generate Spring Configuration classes for Aggregate-Specific Pattern.

  Triggered by:
  - code executor (Step 4.4, infrastructure sub-step)
  - Direct user request: "generate config for [aggregate]"

  Input: Aggregate specification
  Output:
    - {Aggregate}UseCaseConfig.java
    - {Aggregate}InMemoryRepositoryConfig.java
    - {Aggregate}OutboxRepositoryConfig.java
    - {Aggregate}OrmClient.java

  Each aggregate has its own config files to enable parallel sub-agent execution.

allowed-tools: Read, Write, Edit, Bash, Glob
---

# Spring Configuration Generation Skill

## Overview

This skill generates Aggregate-Specific Spring Configuration classes.
Each aggregate has its own configs to:
- Enable parallel sub-agent execution (no file conflicts)
- Support Dual-Profile testing (InMemory + Outbox)
- Follow Clean Architecture layering

---

## INPUT

| Source | Path |
|--------|------|
| Aggregate Spec | `JSON spec `aggregates[]`` |
| Data Class | `src/main/java/{rootPackage}/{aggregate}/usecase/port/out/{Aggregate}Data.java` |
| Mapper Class | `src/main/java/{rootPackage}/{aggregate}/usecase/port/{Aggregate}Mapper.java` |

---

## OUTPUT

| File | Location |
|------|----------|
| UseCaseConfig | `src/main/java/{rootPackage}/{aggregate}/io/springboot/config/{Aggregate}UseCaseConfig.java` |
| InMemoryRepositoryConfig | `src/main/java/{rootPackage}/{aggregate}/io/springboot/config/{Aggregate}InMemoryRepositoryConfig.java` |
| OutboxRepositoryConfig | `src/main/java/{rootPackage}/{aggregate}/io/springboot/config/{Aggregate}OutboxRepositoryConfig.java` |
| OrmClient | `src/main/java/{rootPackage}/{aggregate}/io/springboot/config/orm/{Aggregate}OrmClient.java` |

---

## CRITICAL RULES (Embedded - Cannot Be Skipped)

### Rule 1: Aggregate-Specific Configs (NOT Shared)

```java
// ✅ CORRECT: Each aggregate has its own config
package tw.teddysoft.aiscrum.product.io.springboot.config;

@Configuration
public class ProductUseCaseConfig { }

package tw.teddysoft.aiscrum.sprint.io.springboot.config;

@Configuration
public class SprintUseCaseConfig { }

// ❌ WRONG: Shared UseCaseConfiguration for all aggregates
package tw.teddysoft.aiscrum.common.config;

@Configuration
public class UseCaseConfiguration {
    // All use cases registered here - causes conflicts!
}
```

**Rationale:** Aggregate-specific configs enable parallel code generation without merge conflicts.

### Rule 2: Same Bean Name for InMemory and Outbox (Both use OutboxRepository)

Both InMemory and Outbox configs use the **same** `OutboxRepository` class.
The difference is in the OrmClient implementation:
- InMemory: `InMemoryOrmClient` wrapping `InMemoryOrmDb`
- Outbox: JPA `{Aggregate}OrmClient` (extends `SpringJpaClient`)

```java
// ✅ CORRECT: Both configs use same bean name and OutboxRepository
// InMemoryRepositoryConfig
@Bean("productRepository")
public Repository<Product, ProductId> productRepository(...) {
    // ... OutboxRepository with InMemoryOrmClient
    return new OutboxRepository<>(peer, ProductMapper.newMapper());
}

// OutboxRepositoryConfig
@Bean("productRepository")
public Repository<Product, ProductId> productRepository(...) {
    // ... OutboxRepository with JPA OrmClient
    return new OutboxRepository<>(peer, ProductMapper.newMapper());
}

// ❌ WRONG: Different bean names
@Bean("inMemoryProductRepository")  // Wrong!
@Bean("outboxProductRepository")    // Wrong!

// ❌ WRONG: Using GenericEsRepository or GenericOutboxEsRepository
return new GenericEsRepository<>(...);           // Wrong class!
return new GenericOutboxEsRepository<>(...);      // Wrong class!
```

**Rationale:** Profile isolation ensures only one bean is active at runtime. Both profiles use `OutboxRepository` with different ORM backends.

### Rule 3: Profile Annotations

```java
// ✅ CORRECT: Repository configs use profile-specific annotations
@Configuration
@Profile({"inmemory", "test-inmemory"})
public class ProductInMemoryRepositoryConfig { }

@Configuration
@Profile({"outbox", "test-outbox"})
public class ProductOutboxRepositoryConfig { }

// ✅ CORRECT: UseCaseConfig has NO @Profile (active in all profiles)
@Configuration
public class ProductUseCaseConfig { }

// ❌ WRONG: @Profile on UseCaseConfig (unnecessary)
@Configuration
@Profile({"inmemory", "test-inmemory", "outbox", "test-outbox"})
public class ProductUseCaseConfig { }

// ❌ WRONG: Only production profiles on repository config
@Profile({"inmemory"})  // Missing test-inmemory!
@Profile({"outbox"})    // Missing test-outbox!
```

### Rule 4: OrmClient extends SpringJpaClient Only

> **⛔ CRITICAL — COMMON FIRST-GEN FAILURE CAUSE ⛔**
> OrmClient **必須**繼承 `SpringJpaClient`，**禁止**使用 `CrudRepository` 或 `JpaRepository`。
> `SpringJpaClient` 整合了 `OrmClient + CrudRepository`，是 `EzOutboxClient` 所需的唯一介面。
> 使用 `CrudRepository` 會導致編譯錯誤：`cannot be converted to OrmClient<T,ID>`。

```java
// ✅ CORRECT: Extends SpringJpaClient only
public interface ProductOrmClient extends SpringJpaClient<ProductData, String> {
}

// ❌ WRONG: CrudRepository — EzOutboxClient 無法接受，編譯失敗！
public interface ProductOrmClient extends CrudRepository<ProductData, String> {
}

// ❌ WRONG: JpaRepository + OrmClient combination
public interface ProductOrmClient extends JpaRepository<ProductData, String>, OrmClient<ProductData, String> {
}

// ❌ WRONG: Adding @Repository
@Repository
public interface ProductOrmClient extends SpringJpaClient<ProductData, String> { }
```

**Rationale:** SpringJpaClient provides all necessary methods; @Repository is auto-applied.

### Rule 5: UseCaseConfig - Direct Repository Injection (No @Profile)

```java
// ✅ CORRECT: No @Profile, direct injection, no @Qualifier needed
@Configuration
public class ProductUseCaseConfig {

    @Bean
    public CreateProductUseCase createProductUseCase(
            Repository<Product, ProductId> productRepository) {  // Direct injection
        return new CreateProductService(productRepository);
    }

    @Bean
    public RenameProductUseCase renameProductUseCase(
            Repository<Product, ProductId> productRepository) {
        return new RenameProductService(productRepository);
    }
}

// ❌ WRONG: Using @Qualifier
public CreateProductUseCase createProductUseCase(
        @Qualifier("productRepository") Repository<...> repo) { }  // Unnecessary!
```

### Rule 6: InMemoryRepositoryConfig — OutboxRepository Assembly Chain

> **⛔ CRITICAL — COMMON FIRST-GEN FAILURE CAUSE ⛔**
> `InMemoryOrmDb` **必須**用 `Map<String, Data>` 參數構造，**禁止**使用無參構造函數！
> ```java
> // ✅ CORRECT: Map 參數構造
> new InMemoryOrmDb<>(productDataStore)
>
> // ❌ WRONG: 無參構造 — 編譯錯誤或資料無法共享！
> new InMemoryOrmDb<>()
> ```
> **每個 Aggregate 需要獨立的 `Map<String, Data>` bean 和 `InMemoryOrmDb` bean。**
> Projection beans 共享同一個 Map 以實現讀取一致性。

The InMemory repository config creates a chain using **per-aggregate** data stores:

1. `Map<String, Data>` → `InMemoryOrmDb` (per-aggregate data store)
2. `InMemoryOrmDb` → `InMemoryOrmClient` (wraps OrmDb)
3. `InMemoryOrmClient` + `InMemoryMessageDbClient` → `EzOutboxClient`
4. `EzOutboxClient` → `EzOutboxStoreAdapter.createOutboxStore()` → `OutboxStore`
5. `OutboxStore` → `OutboxRepositoryPeer`
6. `OutboxRepositoryPeer` + `Mapper` → `OutboxRepository`

```java
@Configuration
@Profile({"inmemory", "test-inmemory"})
public class ProductInMemoryRepositoryConfig {

    // Step 1: Per-aggregate ConcurrentHashMap data store
    @Bean
    public Map<String, ProductData> productDataStore() {
        return new ConcurrentHashMap<>();
    }

    // Step 2: Per-aggregate OrmDb wrapping the data store
    @Bean
    public InMemoryOrmDb<ProductData> productOrmDb(
            Map<String, ProductData> productDataStore) {
        return new InMemoryOrmDb<>(productDataStore);
    }

    // Step 3: Repository bean (OutboxRepository assembly chain)
    @Bean("productRepository")
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

    // Step 4: Projection beans (add as needed per aggregate)
    // @Bean
    // public ProductsProjection productsProjection(Map<String, ProductData> productDataStore) {
    //     return new InMemoryProductsProjection(productDataStore);
    // }
}
```

> **Note:** `InMemoryMessageDbClient` is a shared bean from `SharedInfrastructureConfig`.
> Each aggregate creates its own `Map<String, Data>` data store and `InMemoryOrmDb`.
> Projection beans share the same `Map<String, Data>` for read-only access.

### Rule 7: OutboxRepositoryConfig — OutboxRepository Assembly Chain

```java
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

    // Projection beans for IDF queries (inject OrmClient, NOT Map<String, Data>!)
    // @Bean
    // public ProductsProjection productsProjection(ProductOrmClient ormClient) {
    //     return input -> StreamSupport.stream(ormClient.findAll().spliterator(), false)
    //             .filter(d -> ...)
    //             .map(ProductMapper::toDto)
    //             .collect(Collectors.toList());
    // }
}
```

> ⚠️ **WARNING**: Do NOT add `@EnableJpaRepositories`, `@EntityScan`, `@EnableTransactionManagement`,
> or `@PersistenceContext EntityManager` here — `SharedOutboxConfig` already handles JPA scanning globally.
>
> ⚠️ **PROJECTION WARNING**: Outbox profile 的 Projection bean 必須注入 `OrmClient`（JPA），
> 不可注入 `Map<String, Data>`（InMemory 專用）。參見 `query.md` Rule 12。

### Rule 8: No @Service or @Component on Services

```java
// ✅ CORRECT: Plain class, registered via @Bean
public class CreateProductService implements CreateProductUseCase {
    // No annotations!
}

// In UseCaseConfig:
@Bean
public CreateProductUseCase createProductUseCase(...) {
    return new CreateProductService(...);
}

// ❌ WRONG: @Service annotation
@Service
public class CreateProductService { }
```

### Rule 9: Shared Infrastructure Configs (Create Once)

```java
// SharedInfrastructureConfig - InMemory infrastructure (MessageDb + MessageDbClient + Executor)
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
    // ❌ NOT here: Broker, Producer, Relay → VolatileRelayConfig
}

// SharedOutboxConfig - Outbox infrastructure (EntityManager + JpaRepositoryFactory)
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

### Rule 10: Package Structure

```
{aggregate}/io/springboot/config/
├── {Aggregate}UseCaseConfig.java           # No @Profile (active in all profiles)
├── {Aggregate}InMemoryRepositoryConfig.java # @Profile inmemory, test-inmemory
├── {Aggregate}OutboxRepositoryConfig.java   # @Profile outbox, test-outbox
└── orm/
    └── {Aggregate}OrmClient.java            # Extends SpringJpaClient

common/io/springboot/config/
├── SharedInfrastructureConfig.java          # InMemory infrastructure (create once)
├── SharedOutboxConfig.java                  # Outbox infrastructure (create once)
└── connectionframe/
    ├── VolatileRelayConfig.java             # CF2: MessageDb → Broker
    └── CatchupRelayConfig.java              # CF2: Catchup relay
```

---

## VERIFICATION CHECKPOINTS

### Checkpoint 1: Pre-Generation

| Check | Verification |
|-------|--------------|
| Data class exists | {Aggregate}Data.java exists |
| Mapper exists | {Aggregate}Mapper.java exists |
| Domain entity exists | {Aggregate}.java exists |

### Checkpoint 2: Post-Generation

```bash
# Compile check
cd ${projectRoot} && mvn compile -q -pl :${module} 2>&1 | head -20

# Verify same bean name in both configs
grep '@Bean("productRepository")' ${inMemoryConfig}
grep '@Bean("productRepository")' ${outboxConfig}

# Verify no @Qualifier usage
grep "@Qualifier" ${useCaseConfig}
# Should return empty

# Verify OrmClient extends only SpringJpaClient
grep "extends SpringJpaClient" ${ormClientFile}

# Verify OutboxRepository is used (not GenericEsRepository)
grep "OutboxRepository" ${inMemoryConfig}
grep "OutboxRepository" ${outboxConfig}
# Both should return matches

# Verify UseCaseConfig has no @Profile
grep "@Profile" ${useCaseConfig}
# Should return empty
```

---

## GENERATION TEMPLATES

### UseCaseConfig

```java
package ${rootPackage}.${aggregateLowerCase}.io.springboot.config;

import ${rootPackage}.${aggregateLowerCase}.entity.${Aggregate};
import ${rootPackage}.${aggregateLowerCase}.entity.${Aggregate}Id;
import ${rootPackage}.${aggregateLowerCase}.usecase.port.in.*;
import ${rootPackage}.${aggregateLowerCase}.usecase.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tw.teddysoft.ezddd.usecase.port.out.repository.Repository;

@Configuration
public class ${Aggregate}UseCaseConfig {

    @Bean
    public Create${Aggregate}UseCase create${Aggregate}UseCase(
            Repository<${Aggregate}, ${Aggregate}Id> ${aggregateCamelCase}Repository) {
        return new Create${Aggregate}Service(${aggregateCamelCase}Repository);
    }

    // Add more use case beans as needed
}
```

### InMemoryRepositoryConfig

```java
package ${rootPackage}.${aggregateLowerCase}.io.springboot.config;

import ${rootPackage}.${aggregateLowerCase}.entity.${Aggregate};
import ${rootPackage}.${aggregateLowerCase}.entity.${Aggregate}Id;
import ${rootPackage}.${aggregateLowerCase}.usecase.port.${Aggregate}Mapper;
import ${rootPackage}.${aggregateLowerCase}.usecase.port.out.${Aggregate}Data;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxRepositoryPeer;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxStore;
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
public class ${Aggregate}InMemoryRepositoryConfig {

    @Bean
    public Map<String, ${Aggregate}Data> ${aggregateCamelCase}DataStore() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public InMemoryOrmDb<${Aggregate}Data> ${aggregateCamelCase}OrmDb(
            Map<String, ${Aggregate}Data> ${aggregateCamelCase}DataStore) {
        return new InMemoryOrmDb<>(${aggregateCamelCase}DataStore);
    }

    @Bean("${aggregateCamelCase}Repository")
    public Repository<${Aggregate}, ${Aggregate}Id> ${aggregateCamelCase}Repository(
            InMemoryOrmDb<${Aggregate}Data> ormDb,
            InMemoryMessageDbClient messageDbClient) {

        InMemoryOrmClient<${Aggregate}Data> ormClient = new InMemoryOrmClient<>(ormDb);
        EzOutboxClient<${Aggregate}Data, String> outboxClient =
                new EzOutboxClient<>(ormClient, messageDbClient);
        var outboxStore = EzOutboxStoreAdapter.createOutboxStore(outboxClient);  // ⚠️ MUST use var — OutboxStore is abstract
        OutboxRepositoryPeer<${Aggregate}Data, String> peer =
                new OutboxRepositoryPeer<>(outboxStore);

        return new OutboxRepository<>(peer, ${Aggregate}Mapper.newMapper());
    }

    // TODO: Add Projection beans if needed
    // @Bean
    // public ${Aggregate}sProjection ${aggregateCamelCase}sProjection(
    //         Map<String, ${Aggregate}Data> ${aggregateCamelCase}DataStore) {
    //     return new InMemory${Aggregate}sProjection(${aggregateCamelCase}DataStore);
    // }
}
```

### OutboxRepositoryConfig

```java
package ${rootPackage}.${aggregateLowerCase}.io.springboot.config;

import ${rootPackage}.${aggregateLowerCase}.entity.${Aggregate};
import ${rootPackage}.${aggregateLowerCase}.entity.${Aggregate}Id;
import ${rootPackage}.${aggregateLowerCase}.io.springboot.config.orm.${Aggregate}OrmClient;
import ${rootPackage}.${aggregateLowerCase}.usecase.port.${Aggregate}Mapper;
import ${rootPackage}.${aggregateLowerCase}.usecase.port.out.${Aggregate}Data;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxRepositoryPeer;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxStore;
import tw.teddysoft.ezddd.data.io.ezes.store.PgMessageDbClient;
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxClient;
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxStoreAdapter;
import tw.teddysoft.ezddd.usecase.port.out.repository.Repository;
import tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxRepository;

@Configuration
@Profile({"outbox", "test-outbox"})
public class ${Aggregate}OutboxRepositoryConfig {

    @Bean("${aggregateCamelCase}Repository")
    public Repository<${Aggregate}, ${Aggregate}Id> ${aggregateCamelCase}Repository(
            ${Aggregate}OrmClient ${aggregateCamelCase}OrmClient,
            PgMessageDbClient pgMessageDbClient) {

        EzOutboxClient<${Aggregate}Data, String> outboxClient =
                new EzOutboxClient<>(${aggregateCamelCase}OrmClient, pgMessageDbClient);
        var outboxStore = EzOutboxStoreAdapter.createOutboxStore(outboxClient);  // ⚠️ MUST use var — OutboxStore is abstract
        OutboxRepositoryPeer<${Aggregate}Data, String> peer =
                new OutboxRepositoryPeer<>(outboxStore);

        return new OutboxRepository<>(peer, ${Aggregate}Mapper.newMapper());
    }
}
```

### OrmClient

> **⛔ CRITICAL**: 必須繼承 `SpringJpaClient`，禁止使用 `CrudRepository` 或 `JpaRepository`。

```java
package ${rootPackage}.${aggregateLowerCase}.io.springboot.config.orm;

import ${rootPackage}.${aggregateLowerCase}.usecase.port.out.${Aggregate}Data;
import tw.teddysoft.ezddd.data.io.ezoutbox.SpringJpaClient;

// ⛔ 必須 extends SpringJpaClient，不可用 CrudRepository/JpaRepository
public interface ${Aggregate}OrmClient extends SpringJpaClient<${Aggregate}Data, String> {
}
```

---

## EXAMPLE OUTPUT

### ProductUseCaseConfig.java

```java
package tw.teddysoft.aiscrum.product.io.springboot.config;

import tw.teddysoft.aiscrum.product.entity.Product;
import tw.teddysoft.aiscrum.product.entity.ProductId;
import tw.teddysoft.aiscrum.product.usecase.port.in.*;
import tw.teddysoft.aiscrum.product.usecase.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tw.teddysoft.ezddd.usecase.port.out.repository.Repository;

@Configuration
public class ProductUseCaseConfig {

    @Bean
    public CreateProductUseCase createProductUseCase(
            Repository<Product, ProductId> productRepository) {
        return new CreateProductService(productRepository);
    }

    @Bean
    public RenameProductUseCase renameProductUseCase(
            Repository<Product, ProductId> productRepository) {
        return new RenameProductService(productRepository);
    }
}
```

### ProductInMemoryRepositoryConfig.java

```java
package tw.teddysoft.aiscrum.product.io.springboot.config;

import tw.teddysoft.aiscrum.product.entity.Product;
import tw.teddysoft.aiscrum.product.entity.ProductId;
import tw.teddysoft.aiscrum.product.usecase.port.ProductMapper;
import tw.teddysoft.aiscrum.product.usecase.port.out.ProductData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxRepositoryPeer;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxStore;
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
public class ProductInMemoryRepositoryConfig {

    @Bean
    public Map<String, ProductData> productDataStore() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public InMemoryOrmDb<ProductData> productOrmDb(
            Map<String, ProductData> productDataStore) {
        return new InMemoryOrmDb<>(productDataStore);
    }

    @Bean("productRepository")
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
```

### ProductOutboxRepositoryConfig.java

```java
package tw.teddysoft.aiscrum.product.io.springboot.config;

import tw.teddysoft.aiscrum.product.entity.Product;
import tw.teddysoft.aiscrum.product.entity.ProductId;
import tw.teddysoft.aiscrum.product.io.springboot.config.orm.ProductOrmClient;
import tw.teddysoft.aiscrum.product.usecase.port.ProductMapper;
import tw.teddysoft.aiscrum.product.usecase.port.out.ProductData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxRepositoryPeer;
import tw.teddysoft.ezddd.data.adapter.repository.outbox.OutboxStore;
import tw.teddysoft.ezddd.data.io.ezes.store.PgMessageDbClient;
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxClient;
import tw.teddysoft.ezddd.data.io.ezoutbox.EzOutboxStoreAdapter;
import tw.teddysoft.ezddd.usecase.port.out.repository.Repository;
import tw.teddysoft.ezddd.usecase.port.out.repository.impl.outbox.OutboxRepository;

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

### ProductOrmClient.java

```java
package tw.teddysoft.aiscrum.product.io.springboot.config.orm;

import tw.teddysoft.aiscrum.product.usecase.port.out.ProductData;
import tw.teddysoft.ezddd.data.io.ezoutbox.SpringJpaClient;

public interface ProductOrmClient extends SpringJpaClient<ProductData, String> {
}
```

---

## INTEGRATION WITH ORCHESTRATOR

```
code executor
    ↓
    Step 4.6: Invoke config-skill
    ├─ Input: {Aggregate}Data.java, {Aggregate}Mapper.java
    ├─ Output: UseCaseConfig, InMemoryConfig, OutboxConfig, OrmClient
    └─ Next: usecase-test-skill (Step 4.7)
```

---

## ERROR HANDLING

| Error | Action |
|-------|--------|
| Different bean names | Use same name for both InMemory and Outbox |
| @Qualifier usage | Remove - profile isolation handles this |
| JpaRepository in OrmClient | Replace with SpringJpaClient only |
| @Service on service class | Remove - use @Bean in config |
| Missing test profiles | Add test-inmemory and test-outbox |
| GenericEsRepository used | Replace with OutboxRepository assembly chain |
| @Profile on UseCaseConfig | Remove - UseCaseConfig should have no @Profile |
| Missing dataStore/ormDb beans | Add per-aggregate ConcurrentHashMap + InMemoryOrmDb beans |

---
