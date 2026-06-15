# Base Test Classes Templates

這些是新專案必須產生的測試基礎類別，提供 Profile-based Testing 支援。

## 🚨 BaseSpringBootTest - Spring Boot 測試基礎類別
# ⚠️ 重要：必須放在 src/test/java 目錄
# 完整路徑：src/test/java/[rootPackage]/test/base/BaseSpringBootTest.java

> ⛔ **絕對禁止加 `@ExtendWith(EzSpecExtension.class)`**
> `EzSpecExtension` 在 ezspec library 中**不存在**，加了會導致 test-compile 失敗！
> `@EzScenario` 只是包裝了 `@Test` 的 meta-annotation，不需要任何 JUnit Extension。
> 這個類別只需要 `@SpringBootTest`，其他什麼都不加。

```java
package [rootPackage].test.base;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Base class for Use Case and Repository tests that require Spring context.
 * Contract tests do NOT extend this class (they don't need Spring).
 *
 * IMPORTANT: Do NOT add @ActiveProfiles here!
 * Profile switching is controlled by:
 * 1. Environment variable SPRING_PROFILES_ACTIVE
 * 2. Maven profile settings
 * 3. Test suite static initializers
 *
 * This design allows tests to run under different profiles without code changes.
 *
 * NOTE: @DirtiesContext is NOT set here — subclasses (BaseUseCaseTest) set their own
 * classMode (AFTER_EACH_TEST_METHOD) to avoid the EzesVolatileRelay Singleton Trap.
 */
@SpringBootTest
public abstract class BaseSpringBootTest {

    // Intentionally empty - provides Spring Boot test context
    // All profile-specific configurations are handled by Spring's profile mechanism

}
```

## 🚨 NotifyFakeHandleAllEventsService - 事件捕獲服務
# ⚠️ 重要：獨立類別（不是 BaseUseCaseTest 的 inner class）
# 完整路徑：src/test/java/[rootPackage]/common/NotifyFakeHandleAllEventsService.java

```java
package [rootPackage].common;

import tw.teddysoft.ezddd.entity.InternalDomainEvent;
import tw.teddysoft.ezddd.usecase.port.in.interactor.Reactor;
import tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventData;
import tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventMapper;

import java.util.ArrayList;
import java.util.List;

public class NotifyFakeHandleAllEventsService implements Reactor<DomainEventData> {
    private final List<InternalDomainEvent> handledDomainEvents = new ArrayList<>();

    @Override
    public void execute(DomainEventData message) {
        if (message != null) {
            this.handledDomainEvents.add(DomainEventMapper.toDomain(message));
        }
    }

    public long handledEventTimes(Class<?> clazz) {
        return handledDomainEvents.stream().filter(d -> d.getClass().isAssignableFrom(clazz)).count();
    }

    public int getHandledEventsSize() {
        return handledDomainEvents.size();
    }

    public InternalDomainEvent getLastHandledEvent() {
        return handledDomainEvents.getLast();
    }

    public List<InternalDomainEvent> getHandledEvents() {
        return handledDomainEvents;
    }

    public void clearHandledEvents() {
        handledDomainEvents.clear();
    }
}
```

## 🚨 BaseUseCaseTest - Use Case 測試基礎類別

<!-- @authority: dirtiescontext_after_each | source: rules/testing-patterns.md -->
<!-- @authority: setup_event_capture_manual | source: rules/testing-patterns.md -->

# ⚠️ 重要：必須放在 src/test/java 目錄
# 完整路徑：src/test/java/[rootPackage]/test/base/BaseUseCaseTest.java

```java
package [rootPackage].test.base;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import [rootPackage].common.NotifyFakeHandleAllEventsService;
import tw.teddysoft.ezddd.data.io.ezes.store.PgMessageDbClient;
import tw.teddysoft.ezddd.message.broker.adapter.in.consumer.InMemoryConsumer;
import tw.teddysoft.ezddd.message.broker.adapter.in.consumer.internal.InternalInMemoryMessageConsumer;
import tw.teddysoft.ezddd.message.broker.io.messagebroker.InMemoryMessageBroker;
import tw.teddysoft.ezddd.usecase.port.inout.domainevent.DomainEventData;
import tw.teddysoft.ezddd.usecase.port.inout.messaging.MessageProducer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Base class for Use Case tests with event capture support.
 *
 * IMPORTANT: Relay lifecycle is managed by VolatileRelayConfig / CatchupRelayConfig.
 * Do NOT create additional relay instances here — VolatileRelayConfig already starts
 * a relay via relayExecutor.execute(relay) during Spring Context initialization.
 * Creating a second relay causes DUPLICATE EVENT DELIVERY (each event forwarded twice).
 *
 * With @DirtiesContext(AFTER_EACH_TEST_METHOD), each test gets a fresh Spring Context
 * including a new relay instance, so the Singleton Trap is avoided.
 *
 * Subclasses MUST call setUpEventCapture() in @BeforeEach
 * and tearDownEventCapture() in @AfterEach manually.
 *
 * @see EzesVolatileRelay Singleton Trap — explained in rules/testing-patterns.md §DirtiesContext
 * @see .claude/skills/ezddd-java/references/examples/dual-profile-test-infrastructure.md
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class BaseUseCaseTest extends BaseSpringBootTest {

    @Autowired
    protected InMemoryMessageBroker<DomainEventData> inMemoryMessageBroker;

    @Autowired
    protected MessageProducer<DomainEventData> messageProducer;

    // Do NOT create relay instances here! VolatileRelayConfig manages the relay lifecycle.
    // @DirtiesContext(AFTER_EACH_TEST_METHOD) ensures a fresh relay per test.

    @Autowired(required = false)
    protected PgMessageDbClient pgMessageDbClient;

    @Autowired(required = false)
    protected JdbcTemplate jdbcTemplate;

    @Value("${spring.profiles.active:test-inmemory}")
    protected String activeProfile;

    protected NotifyFakeHandleAllEventsService notifyFakeHandleAllEventsService;
    protected InternalInMemoryMessageConsumer notifyHandleAllEventsConsumer;
    protected InMemoryConsumer<DomainEventData> inMemoryConsumer;
    protected ExecutorService executorService;

    /**
     * Subclasses MUST call this in @BeforeEach: setUpEventCapture()
     * Example: @BeforeEach public void setUp() { setUpEventCapture(); }
     */
    protected void setUpEventCapture() {
        System.out.println("==> Running test with profile: " + activeProfile);

        if ("test-outbox".equals(activeProfile) && jdbcTemplate != null) {
            try {
                // Pre-flight Cleanup SQL: DELETE FROM messages (full path: message_store.messages)
                jdbcTemplate.execute("DELETE FROM message_store.messages");
                jdbcTemplate.execute("ALTER SEQUENCE message_store.messages_global_position_seq RESTART WITH 1");
                System.out.println("Cleaned up message_store.messages table and reset sequence");
            } catch (Exception e) {
                System.err.println("Could not clean messages table: " + e.getMessage());
            }
        }

        notifyFakeHandleAllEventsService = new NotifyFakeHandleAllEventsService();

        inMemoryConsumer = new InMemoryConsumer<>(inMemoryMessageBroker);
        notifyHandleAllEventsConsumer = new InternalInMemoryMessageConsumer(
                notifyFakeHandleAllEventsService, inMemoryConsumer);
        executorService = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        executorService.submit(notifyHandleAllEventsConsumer);
        // Relay is already running via VolatileRelayConfig/CatchupRelayConfig.
        // Do NOT start another relay here — it causes duplicate event delivery!

        // For outbox profile: the CatchUpRelay (with UUID checkpoint, starting from position 0)
        // may have already relayed leftover messages from the previous test before setUpEventCapture()
        // cleaned the messages table. Wait for those stale events to be consumed, then clear them.
        if ("test-outbox".equals(activeProfile)) {
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            notifyFakeHandleAllEventsService.clearHandledEvents();
        }
    }

    /**
     * Subclasses MUST call this in @AfterEach.
     *
     * CRITICAL: For outbox profile, messages MUST be cleaned in tearDown (not just setUp).
     * Reason: CatchUpRelay starts during Spring Context init (@Bean), which runs BEFORE
     * the @BeforeEach setUpEventCapture() call — so setUp cleanup is already too late.
     * If messages are only cleaned in setUp, the new relay has already read stale messages.
     *
     * Timeline: Test N ends → @DirtiesContext destroys Context
     *   → Test N+1 new Context → CatchUpRelay starts (reads stale msgs!) → then @BeforeEach setUpEventCapture() runs (too late!)
     *
     * @see testing-patterns.md Error Pattern #7
     */
    protected void tearDownEventCapture() {
        if (executorService != null) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    System.out.println("ExecutorService did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // CRITICAL: Clean outbox messages BEFORE Context is destroyed,
        // so the next test's CatchUpRelay starts with an empty message table.
        if ("test-outbox".equals(activeProfile) && jdbcTemplate != null) {
            try {
                jdbcTemplate.execute("DELETE FROM message_store.messages");
                jdbcTemplate.execute("ALTER SEQUENCE message_store.messages_global_position_seq RESTART WITH 1");
                System.out.println("tearDown: Cleaned up message_store.messages table");
            } catch (Exception e) {
                System.err.println("tearDown: Could not clean messages table: " + e.getMessage());
            }
        }
    }

    protected void clearCapturedEvents() {
        if (notifyFakeHandleAllEventsService != null) {
            notifyFakeHandleAllEventsService.clearHandledEvents();
        }
    }
}
```

## 使用說明

### 1. 新專案設置時
當 AI 被要求「產生 BaseUseCaseTest」時，應該：
1. 將 `[rootPackage]` 替換為實際的 package 名稱（從 `.dev/project-config.json` 讀取）
2. 建立正確的目錄結構
3. 產生這三個類別（BaseSpringBootTest, NotifyFakeHandleAllEventsService, BaseUseCaseTest）

### 2. Profile 支援
這些基礎類別支援以下 profiles：
- `test-inmemory`: 使用記憶體內的 Repository 實作
- `test-outbox`: 使用 PostgreSQL + Outbox Pattern

### 3. 重要規範
- **絕對不要在這些類別上加 @ActiveProfiles**
- Profile 由外部控制（環境變數、Maven、Test Suite）
- **子類別必須手動呼叫 `setUpEventCapture()` 和 `tearDownEventCapture()`**
- `NotifyFakeHandleAllEventsService` 是獨立類別，不是 inner class

### 4. 依賴關係
BaseUseCaseTest 依賴以下類別：
- `NotifyFakeHandleAllEventsService` — 獨立類別 (`${rootPackage}.common`)
- ezddd 框架類別（透過 Maven 依賴取得）

## Concrete Test Class Example

> **完整範例** 請參考 `references/examples/dual-profile-test-infrastructure.md` Section 10。
>
> 子類別使用 BaseUseCaseTest 的關鍵要求：
>
> | 項目 | 必須 | 說明 |
> |------|------|------|
> | `@BeforeEach` → `setUpEventCapture()` | ✅ | 初始化事件捕獲 consumer |
> | `@AfterEach` → `tearDownEventCapture()` | ✅ | 關閉 consumer ExecutorService |
> | `@DirtiesContext(AFTER_EACH_TEST_METHOD)` | 已繼承 | 已在 BaseUseCaseTest 上宣告，concrete class **不需重複**（Spring 會繼承 class-level 註解） |
> | `Awaitility.await()` for event assertions | ✅ | 事件投遞是非同步的，統一使用 **10 秒** timeout（見 `testing-patterns.md` Rule 5） |
> | 建立 relay 實例 | ❌ 禁止 | relay 由 Config 管理（避免 Double Relay）。relay 類別路徑：`tw.teddysoft.ezddd.data.EzesVolatileRelay`（inmemory）、`tw.teddysoft.ezddd.data.EzesCatchUpRelay`（outbox） |

```java
// ✅ Minimal concrete test class — 完整骨架
import tw.teddysoft.ezspec.extension.junit5.EzScenario;
import tw.teddysoft.ezspec.keyword.Feature;
import tw.teddysoft.ezspec.keyword.ScenarioEnvironment;

class CreateProductBacklogItemServiceTest extends BaseUseCaseTest {

    @Autowired
    private CreateProductBacklogItemUseCase createProductBacklogItemUseCase;

    @BeforeEach public void setUp() { setUpEventCapture(); }    // MUST call setUpEventCapture()
    @AfterEach public void tearDown() { tearDownEventCapture(); }

    @EzScenario
    public void create_pbi_successfully() {
        Feature.New("CreateProductBacklogItem")
                .newScenario()
                .Given("A product exists", env -> { /* no setup needed */ })
                .When("The user creates a PBI", env -> {
                    var output = createProductBacklogItemUseCase.execute(buildInput());
                    env.put("output", output);
                })
                .Then("PBI is created and event is published", env -> assertPbiCreatedEvent(env))
                .Execute();
    }

    // ❌ WRONG: private/protected/void methodName(ScenarioEnvironment env) — invisible to Class.getMethod()
    // ✅ CORRECT: step helpers with ScenarioEnvironment MUST be public:
    public void assertPbiCreatedEvent(ScenarioEnvironment env) { await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(notifyFakeHandleAllEventsService.getHandledEvents()).hasSize(1)); }
}
```

> **relay 類別完整路徑** (供 Config 參考，禁止在測試中直接建立 relay 實例):
> - `tw.teddysoft.ezddd.data.EzesVolatileRelay` — inmemory/test-inmemory profile
> - `tw.teddysoft.ezddd.data.EzesCatchUpRelay` — outbox/test-outbox profile

## 與其他模板的關係
- 必須先產生 `local-utils.md` 中的共用類別
- 測試類別應該參考 `references/examples/` 的範例寫法
- 遵循 `references/patterns/testing/usecase-test.md` 的規範
- **必讀**：`examples/dual-profile-test-infrastructure.md` — 完整的 relay 生命週期、failure prevention 對照表、測試時序圖、concrete test example (Section 10)
