# AI Coding Skill 課程進度

> 最後更新：2026-06-14
> 專案：ai-scrum-course（Java 21 + Spring Boot 3.5.3 + ezddd）

---

## 完成項目

### 1. A/B 實驗：AI Coding Skill 對程式碼品質的影響

**任務**：比較有無 `/execute-uc` skill 對同一份 spec（create-product.json）產出品質的差異。

**方式**：建立兩個隔離的 git worktree，平行執行兩個 Agent：
- `.worktrees/experiment-A`（branch: experiment-A）：禁用 ezddd，純 DDD + Spring Boot
- `.worktrees/experiment-B`（branch: experiment-B）：使用 `/execute-uc --only-inmemory`

**結果**：

| | 方法 A（無 skill） | 方法 B（有 skill） |
|--|:-----------------:|:-----------------:|
| 合規項目數 | **10 / 16** | **16 / 16** |

**方法 A 6 個不合規原因**：
- #5 無 Event Sourcing（直接設 `this.state`，無 `when()` 機制）
- #7 Domain Event 缺少 metadata / `static mapper()`
- #10 UseCase Input 用 record（應用 plain class）
- #12 缺少 Mapper + `setVersion()` / `clearDomainEvents()`
- #15 測試缺少 `setUpEventCapture()` / `tearDownEventCapture()`
- #16 Aggregate 缺少 `getId()` override

**輸出**：[experiment-AB-result.md](experiment-AB-result.md)

---

### 2. 閱讀 proof_read2.pdf — Read-only Entities 設計模式

**內容**：論文中提出的 DDD 補充 Pattern，解決「Aggregate Root 把內部 Entity 暴露後，外部可直接繞過 AR 修改」的問題。

**兩種實作方式比較**：

| | Special Case（繼承） | Proxy（組合） |
|--|---------------------|--------------|
| 屬性複製 | 需要 shallow copy | 不需要 |
| 多型支援 | 每個子類別各需 ReadOnly 版本 | 一個 ReadOnly 對應所有實作 |
| 前置條件 | Entity 必須允許被繼承 | 必須有（或抽出）共同 Interface |

**四種方法處理規則**：
| 方法類型 | 處理方式 |
|---------|---------|
| Command（修改狀態） | 覆寫，拋 `UnsupportedOperationException` |
| Query → immutable（VO、primitive） | 直接繼承或 delegate，不覆寫 |
| Query → Entity | 覆寫，包成 ReadOnly 版本 |
| Query → Collection | 覆寫，包成 unmodifiable + 元素也包裝 |

**輸出**：[Ch2.md](Ch2.md)

---

### 3. 整個專案 Domain UML

根據 `.dev/specs/` 下全部規格，以 Mermaid 產生 9 張圖：
1. Bounded Context 總覽
2. Product Aggregate（含 ProductGoal、DefinitionOfDone、VOs、Events）
3. ProductBacklogItem Aggregate（含 Task、AcceptanceCriterion、VOs、Events）
4. Sprint Aggregate（含 ScrumBoardConfig、VOs、Events）
5. ScrumTeam Aggregate（含 ScrumTeamMember、Enum、Events）
6. Cross-Aggregate ID Reference 關係圖
7. Use Case 全覽 Mindmap（Commands / Queries / Reactors）
8. Clean Architecture 分層圖（以 Product 為例）
9. Event Sourcing + Outbox 流程 Sequence Diagram

**輸出**：[domain-uml.md](domain-uml.md)

---

### 4. Read-only Entities Spec 設計

#### 4.1 評估哪些 Entity 適合套用

| Entity | Aggregate | 優先級 | 理由 |
|--------|-----------|:------:|------|
| **Task** | ProductBacklogItem | 🔴 首選 | PBI auto-completion / work regression 兩個 critical invariant；6 個 UC 管理其狀態 |
| **ProductGoal** | Product | 🟡 次要 | 有 ProductGoalState 狀態機；`addGoalMetric` 修改內部 metrics |
| **ScrumTeamMember** | ScrumTeam | 🟢 低 | Entity 但缺乏複雜 invariant |
| **ScrumBoardConfig** | Sprint | ⚪ 略過 | 純設定型，無商業狀態機 |

#### 4.2 Read-only Project Spec（示範用，Team/Project 領域）

**路徑**：`.dev/specs/team/pattern/read-only-project.json`
- Pattern: ReadOnlyEntities / SpecialCase 實作
- 示範 Team → Project → Member 的三層 ReadOnly 包裝

#### 4.3 Read-only Task Spec（正式實作對象）

**路徑**：`.dev/specs/pbi/pattern/read-only-task.json`
- Pattern: ReadOnlyEntities / **Proxy** 實作
- Aggregate: ProductBacklogItem / Entity: Task
- packageInfo: `tw.teddysoft.aiscrum.pbi.entity`
- 關鍵 invariant 保護：
  - **PBI Auto-Completion**：所有 Task → DONE 時 PBI 自動完成（發布 PbiCompleted 事件）
  - **PBI Work Regression**：DONE Task 退回時 PBI 退回 IN_PROGRESS（發布 PbiWorkRegressed 事件）
- 9 個 command methods → 全部 `throw UnsupportedOperationException`
- 8 個 query methods → 全部 `return real.xxx()`
- 2 個 collection queries → `Collections.unmodifiable*(real.xxx())`
- 7 個 testScenarios 涵蓋所有保護行為

---

### 5. /execute-uc skill 擴充：支援 PATTERN 類型

**問題**：原有 skill 只支援 COMMAND / QUERY / REACTOR 三種 spec 類型，PATTERN 類型無法被偵測與執行。

**修改內容**：

| 檔案 | 修改內容 |
|------|---------|
| `.claude/skills/ezddd-java/SKILL.md` | Spec Types 表格新增 PATTERN 行 |
| `.claude/skills/ezddd-java/references/uc-executor/uc-workflow.md` | Step 0.2 新增偵測規則；Step 0.3 新增必要欄位驗證；Phase 2 新增完整 PATTERN 生成流程（Step P.1–P.4） |
| `.claude/skills/ezddd-java/references/uc-executor/json-to-pattern-mapping.md` | 新增 PATTERN Spec Fields 欄位對照表（26 個欄位）與 Generation Order |

**PATTERN 偵測規則**：頂層有 `"pattern"` + `"entity"` 欄位 → 類型為 PATTERN

**PATTERN 生成流程**：
- P.1 → `{IEntity}.java`（interface）
- P.2 → `{ReadOnlyEntity}.java`（proxy）
- P.3 → AR 修改說明（若 AR 已存在則自動 patch）
- P.4 → `{ReadOnlyEntity}Test.java`（純 JUnit 5，無 Spring context）

---

### 6. 執行 /execute-uc 生成 ReadOnly Task 程式碼

**指令**：`/execute-uc .dev/specs/pbi/pattern/read-only-task.json`

**生成檔案（10 個）**：

```
src/main/java/tw/teddysoft/aiscrum/pbi/entity/
├── ITask.java          ← P.1 interface（10 query + 8 command methods）
├── ReadOnlyTask.java   ← P.2 Proxy（command 拋例外，query delegate，collection unmodifiable）
├── Task.java           ← implements ITask（原始 Entity）
├── TaskState.java      ← enum: TODO / IN_PROGRESS / DONE
├── TaskId.java         ← VO record
├── PbiId.java          ← VO record
├── Hours.java          ← VO record（BigDecimal）
├── EstimatedHours.java ← VO record
└── RemainingHours.java ← VO record

src/test/java/tw/teddysoft/aiscrum/pbi/entity/
└── ReadOnlyTaskTest.java ← 12 個 JUnit 5 測試
```

**驗證結果**：

```
╔═══════════════════════════════════════════════════════╗
║ Gate 1 (Tests):        ✅ PASS  12/12 tests passed   ║
║ Gate 2.5 (Coverage):   ✅ PASS  7/7 scenarios covered ║
║ Compilation:           ✅ PASS  9 classes compiled    ║
╚═══════════════════════════════════════════════════════╝
```

**待辦（AR 層修改，待 create-pbi UC 執行後套用）**：
- `ProductBacklogItem.getTasks()` → 回傳型別改為 `SequencedSet<ITask>`，每個 Task 包裝為 `new ReadOnlyTask(task)`

---

## 重要設計決策

| 決策 | 選擇 | 原因 |
|------|------|------|
| Task ReadOnly 實作方式 | Proxy（組合） | Task 可能有子類別；不需 shallow copy；ITask 介面提供型別安全 |
| PATTERN spec 識別方式 | `pattern` + `entity` 頂層欄位 | 與其他類型不衝突；語義清晰 |
| PATTERN 測試框架 | 純 JUnit 5（無 Spring） | PATTERN 不涉及 UseCase / Repository，不需要 Spring context |
| collection 回傳策略 | `Collections.unmodifiable*` | 元素（TaskId VO、String）本身不可變，不需二次包裝 |

---

## 重要釐清：Read-only Entities 不會自動套用

**問題**：執行 `/execute-uc create-product.json` 時，是否會自動套用 Read-only Entities？

**答案：不會。** 原因如下：

1. **Spec type 偵測不符**：`create-product.json` 頂層有 `useCase` + `domainEvent` → 偵測為 **COMMAND** 類型，走 COMMAND 生成路徑。PATTERN 路徑只有在偵測到 `pattern` + `entity` 頂層欄位時才觸發。

2. **領域不同**：`create-product.json` 目標是 **Product** aggregate（內部 Entity 為 `ProductGoal`），與 `read-only-task.json` 針對的 **ProductBacklogItem** aggregate（Task entity）完全無關。

**正確執行順序**：
```
# 1. 先建立 PBI aggregate（Task 才存在）
/execute-uc .dev/specs/pbi/usecase/create-pbi.json

# 2. 再套用 Read-only Pattern（包裝 Task）
/execute-uc .dev/specs/pbi/pattern/read-only-task.json
```

Read-only Entities 是**獨立的 PATTERN spec**，必須明確執行其 spec 檔案才會生效，不會被任何 COMMAND / QUERY / REACTOR spec 自動觸發。

---

---

### 7. Experiment D：B方案 Pending File 機制驗證

**目標**：驗證 `/execute-uc --only-inmemory create-pbi.json` 執行時，B方案 pending file 機制能否正確：
1. 產生完整 ProductBacklogItem Aggregate 基礎設施
2. 偵測 `.dev/.pattern-pending/ProductBacklogItem.json`，自動套用 ReadOnly 方法變更
3. 套用後刪除 pending 檔案
4. InMemory 測試全數通過

**Worktree**：`.worktrees/experiment-D`（branch: main）

#### 7.1 生成的基礎設施檔案

**共用基礎設施（init-project）**：
```
src/main/java/.../common/entity/DateProvider.java
src/main/java/.../common/io/springboot/config/SharedInfrastructureConfig.java
src/main/java/.../common/io/springboot/config/connectionframe/VolatileRelayConfig.java
src/main/java/.../common/io/springboot/config/DomainEventMapperConfig.java   ← ADR-047
src/main/resources/application.yml
src/main/resources/application-test-inmemory.yml    ← exclude DataSource/JPA/Flyway
src/test/java/.../common/NotifyFakeHandleAllEventsService.java
src/test/java/.../test/base/BaseSpringBootTest.java
src/test/java/.../test/base/BaseUseCaseTest.java
src/test/java/.../test/suite/InMemoryTestSuite.java
```

**ProductBacklogItem Aggregate 層**：
```
pbi/entity/PbiId.java, ProductId.java, SprintId.java, TagGroupId.java, TagId.java
pbi/entity/TagRef.java, Estimate.java, Importance.java, AcceptanceCriterion.java
pbi/entity/EstimateType.java, PbiState.java, Tag.java
pbi/entity/ProductBacklogItemEvents.java   ← sealed interface + static mapper()
pbi/entity/ProductBacklogItem.java         ← B方案 pending 套用後的最終版本
pbi/usecase/port/out/ProductBacklogItemData.java
pbi/usecase/port/ProductBacklogItemMapper.java
pbi/io/springboot/config/ProductBacklogItemInMemoryRepositoryConfig.java
pbi/io/springboot/config/ProductBacklogItemUseCaseConfig.java
pbi/usecase/port/in/CreateProductBacklogItemUseCase.java
pbi/usecase/service/CreateProductBacklogItemService.java
pbi/entity/ProductBacklogItemContractTest.java
pbi/usecase/service/CreateProductBacklogItemServiceTest.java
```

#### 7.2 B方案 Pending File 機制結果

| 驗證項目 | 結果 |
|---------|------|
| `ProductBacklogItem.json` pending 檔案被偵測 | ✅ |
| `getTasks()` 回傳型別改為 `SequencedSet<ITask>` | ✅ |
| `findTaskById()` 回傳型別改為 `Optional<ITask>` | ✅ |
| 兩方法都用 `ReadOnlyTask` 包裝 | ✅ |
| 套用後 pending 檔案刪除 | ✅ |
| `Team.json` 保留（Team AR 未產生） | ✅ |

#### 7.3 過程中發現並修正的 Bug

| # | 問題 | 原因 | 修正方式 |
|---|------|------|---------|
| 1 | `getCategory()` 抽象方法未覆寫 | API 文件未明確列出此抽象方法 | 新增 `CATEGORY` 常數 + `@Override getCategory()` |
| 2 | `when()` 簽名錯誤 | 誤用重載式 `when(PbiCreated e)` | 改為 `@Override protected void when(ProductBacklogItemEvents event)` + switch pattern |
| 3 | `CqrsOutput.success(String)` 不存在 | API 誤解 | 改為 `CqrsOutput.create().setId(input.pbiId)` |
| 4 | `UseCaseFailureException` 建構子錯誤 | 誤以為有 3 參數版本 | 改為 `(String, Throwable)` 兩參數版 |
| 5 | `private PbiId id` 欄位被誤刪 | 誤以為 `EsAggregateRoot` 有 `protected ID id` | 重新加回 `private PbiId id;` |
| 6 | InMemory 測試載入 DataSource / JPA / Flyway | 直接跑 `-Dtest=` 不經 TestSuite ProfileSetter | 建立 `application-test-inmemory.yml` 排除三個 autoconfigure |
| 7 | `@EzScenario` `NoSuchMethodException` | `Class.getMethod()` 只找 public method | 測試方法改為 `public void` |
| 8 | `DomainEventMapper` 未初始化 | 缺少 `DomainEventMapperConfig` Spring bean | 建立 ADR-047 classpath scan config |
| 9 | `findById` 回傳 null | ID record 的 `toString()` 回傳 `PbiId[value=xxx]` 而非 `xxx` | 所有 ID record 新增 `@Override toString() { return value; }` |

#### 7.4 最終測試結果

```
Tests run: 7, Failures: 0, Errors: 0 -- ProductBacklogItemContractTest
Tests run: 1, Failures: 0, Errors: 0 -- CreateProductBacklogItemServiceTest
Tests run: 8, Failures: 0, Errors: 0 ← BUILD SUCCESS
```

#### 7.5 關鍵學習

**ID record 必須覆寫 `toString()`**：框架（`OutboxRepository.findById`）用 `id.toString()` 當 map key。Java record 預設 `toString()` = `PbiId[value=xxx]`，而存入時用 `data.getId()` = `xxx`，導致找不到。所有 ID class 都必須加：
```java
@Override
public String toString() { return value; }
```

**`DomainEventMapperConfig` 是必要 bean**：不建就會有 `"Please call setMapper to config DomainEventMapper first"` 錯誤，Event Sourcing 的 `toData()` 無法序列化 domain event。

---

### 8. Skill Templates 固化（FC-17 + value-object.md 無條件載入）

**目標**：將 experiment-D 發現的兩個 silent bug 固化進 skill system，確保未來產生程式碼時不會重蹈覆轍。

#### 8.1 FC-17：ID record 缺 `toString()` 導致 `findById` 靜默失敗

**根本原因**：`OutboxRepository.findById(PbiId id)` 在 InMemory 模式下用 `id.toString()` 當 map 查找 key。Java record 預設 `toString()` 格式為 `PbiId[value=xxx]`，但存入時 key 是 `data.getId()` = `xxx`。兩者不符 → findById 永遠回傳 empty，**無任何 exception、無任何 log**，極難發現。

**固化位置與內容**：

| 檔案 | 修改內容 |
|------|---------|
| `references/rules/critical-rules.md` | FORBIDDEN #34 加入 FC-17 說明（silent failure 路徑） |
| `references/rules/critical-rules.md` | ALWAYS REQUIRED #22 加入「每個 `*Id` record 必須覆寫 `toString()`」 |
| `references/uc-executor/uc-workflow.md` | Step 4.1 CRITICAL checks 加入 FC-17 檢查點 |
| `references/AUTHORITY-REGISTRY.yaml` | 新增 `id_record_tostring_findbyid` 規則（severity: critical） |

**FC-17 快速記憶法**：

> `id.toString()` ≠ `id.value()` → findById 啞巴失敗

```java
// 所有 *Id record 必須加這三行
@Override
public String toString() { return value; }

public static XxxId valueOf(String value) { return new XxxId(value); }
```

#### 8.2 `value-object.md` 改為無條件載入

**問題**：原本 `uc-workflow.md` Step 4.1 的 `LOAD_PATTERNS` 只在「spec 有 `valueObjects[]`」時才載入 `value-object.md`。但 aggregate 本身的 `*Id` class（如 `PbiId`）也需要按此 template 生成，否則會缺 `toString()`。

**修正**：`uc-workflow.md` Step 4.1 改為：
```
- references/patterns/domain/value-object.md  ← ALWAYS（不再有條件）
```

#### 8.3 AUTHORITY-REGISTRY.yaml 新增規則

```yaml
id_record_tostring_findbyid:
  description: "Every *Id record MUST override toString() returning raw value — ..."
  severity: critical
  group: correctness
  authority: "patterns/domain/value-object.md"
  consumers:
    - uc-executor/uc-workflow.md § Step 4.1 CRITICAL checks
  jit_consumers:
    - rules/critical-rules.md rule #34
```

---

## 待執行項目

- [ ] 評估是否為 ProductGoal 建立 Read-only Entities spec
- [ ] 執行其他 PBI 相關 UC spec（create-task、move-task、set-task-status 等）
