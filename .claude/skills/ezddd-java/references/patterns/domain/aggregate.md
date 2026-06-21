# Aggregate Generation Skill

## ⚠️ IMPORT PATH REFERENCE (MUST READ FIRST!)

**正確的 Framework Import 路徑 (ezapp-starter 2.0.0):**

| 類別 | 正確路徑 | ❌ 錯誤路徑 |
|------|---------|-----------|
| `EsAggregateRoot` | `tw.teddysoft.ezddd.entity.EsAggregateRoot` | `tw.teddysoft.ezddd.core.aggregate.EsAggregateRoot` |
| `ValueObject` | `tw.teddysoft.ezddd.entity.ValueObject` | `tw.teddysoft.ezddd.core.entity.ValueObject` |
| `Entity` | `tw.teddysoft.ezddd.entity.Entity` | `tw.teddysoft.ezddd.core.entity.Entity` |
| `InternalDomainEvent` | `tw.teddysoft.ezddd.entity.InternalDomainEvent` | `tw.teddysoft.ezddd.core.entity.InternalDomainEvent` |
| `DomainEventTypeMapper` | `tw.teddysoft.ezddd.entity.DomainEventTypeMapper` | `tw.teddysoft.ezddd.core.entity.DomainEventTypeMapper` |
| `Contract` | `import static tw.teddysoft.ucontract.Contract.*` | ❌ `implements Contract` (Contract is a utility class, NOT an interface!) |
| `DateProvider` | `${rootPackage}.common.entity.DateProvider` | `${rootPackage}.common.DateProvider` |

> 📖 完整 API 參考: `.claude/skills/ezddd-java/references/framework/ezapp-api.md`

---

## Overview

This skill generates Aggregate Root classes following Event Sourcing and DDD patterns.
It embeds critical rules directly to ensure they cannot be skipped or forgotten.

> **當 Aggregate Root 需把內部 Entity 交給邊界外的 client（回傳 entity / 可變 collection）時**，
> 為守住封裝與 invariants，請套用 **Read-only Entities** pattern
> → 見 [`read-only-entity.md`](./read-only-entity.md)。

---

## PRE-FLIGHT CHECKLIST ⛔ BLOCKING

**執行此 skill 前必須完成以下檢查，任一失敗則 STOP：**

| # | 檢查項目 | 驗證方式 | 失敗處理 |
|---|---------|---------|---------|
| 1 | aggregate.yaml 存在 | `test -f ${aggregateYamlPath}` | STOP - 報告缺失 |
| 2 | 有 `aggregate:` 欄位 | `grep "^aggregate:" ${file}` | STOP - 無效規格 |
| 3 | 有 `domain_events:` 欄位 | `grep "^domain_events:" ${file}` | STOP - 無事件定義 |
| 4 | 檢查 aggregate.yaml 的 `semantics:` 欄位 | 理解語意標籤意義（參考 `rules/domain-patterns.md` § Semantics Vocabulary） | WARN - 可能遺漏語意約束 |

```bash
# Pre-flight 驗證腳本
test -f "${aggregateYamlPath}" || { echo "❌ aggregate.yaml not found"; exit 1; }
grep -q "^aggregate:" "${aggregateYamlPath}" || { echo "❌ Missing aggregate field"; exit 1; }
grep -q "^domain_events:" "${aggregateYamlPath}" || { echo "❌ Missing domain_events"; exit 1; }
```

---

## SEMANTICS TAG HANDLING ⭐⭐⭐

**產生 Aggregate 前，必須讀取並套用語意標籤！**

### Step 1: 查閱語意定義
```
參考: rules/domain-patterns.md § Semantics Vocabulary
常用標籤: collection_reference_immutable, value_immutable, identity, soft_delete_flag
```

### Step 2: 檢查 aggregate.yaml 的 semantics 欄位

```yaml
# aggregate.yaml 範例
attributes:
  - name: teamId
    type: String
    semantics:
      immutability: value_immutable  # ← 必須處理！
```

### Step 3: 套用程式碼產生規則

| 語意標籤 | 程式碼影響 | 驗證 |
|---------|-----------|------|
| `value_immutable` | 不產生修改此欄位的 command/event | 檢查無 setter |
| `identity` | 欄位為聚合身分識別，搭配 `value_immutable` | 用於 ID 欄位 |
| `collection_reference_immutable` | 不產生替換集合的 setter，只能 add/remove | 檢查無 setXxx() |
| `soft_delete_flag` | 所有操作前檢查 `!isDeleted` | invariant 自動加入 |

### Step 4: 驗證產生的程式碼

```bash
# 如果 teamId 有 value_immutable，不應該有修改 teamId 的方法
grep -E "setTeamId|changeTeam|updateTeam" ${outputFile}
# 應該返回空！
```

---

## ⚠️ WHAT WILL GO WRONG (必讀!)

### 錯誤 1: 在建構子直接設定狀態（繞過 Event Sourcing）

```java
// ❌ 錯誤程式碼
public Product(ProductId productId, String name) {
    super();
    this.id = productId;      // 直接設定！
    this.name = name;         // 直接設定！
    apply(new ProductEvents.ProductCreated(...));
}
```

**症狀**:
- 事件重播時狀態不一致
- Aggregate 狀態與 Event Store 不同步

**修復**:
```java
// ✅ 正確：只在 when() 設定狀態
public Product(ProductId productId, String name) {
    super();
    apply(new ProductEvents.ProductCreated(productId, name, ...));
    // when() 會設定 this.id 和 this.name
}
```

### 錯誤 2: 使用 Instant.now() 而非 DateProvider.now()

**症狀**:
- 測試變得非決定性
- 無法用固定時間測試時間相關邏輯

**修復**: 全部改用 `DateProvider.now()`

### 錯誤 3: 在 apply() 前使用 this.xxx

```java
// ❌ 錯誤：this.id 此時是 null！
apply(new ProductEvents.ProductCreated(this.id, name, ...));
```

**症狀**: `NullPointerException` 在建構子

**修復**: 使用建構子參數，不用 `this.xxx`

### 錯誤 4: 缺少 Event Replay Constructor

```java
// ❌ 錯誤：只有業務建構子，沒有 event replay constructor
public class Product extends EsAggregateRoot<ProductId, ProductEvents> {
    public Product(ProductId productId, String name) { ... }
    // 缺少 Product(List<ProductEvents>) !
}
```

**症狀**:
- `EsRepository` 重建失敗：`constructor Product cannot be applied to given types`
- 無法從 Event Store 重建 Aggregate（repository.findById() 失敗）

**修復**:
```java
// ✅ 正確：必須同時有業務建構子和 event replay constructor
public Product(List<ProductEvents> domainEvents) {
    super(domainEvents);  // EsAggregateRoot 逐一呼叫 when(event) 重建狀態
}

public Product(ProductId productId, String name) {
    super();
    // 業務邏輯...
}
```

**Why**: Event Sourcing 的 Aggregate 有兩個生命週期：
- **新建**（業務建構子）：驗證 → apply(event) → when() 設定狀態
- **重建**（replay constructor）：從 DB 讀取事件列表 → 逐一 when() 還原狀態

### 錯誤 5: 忽略 semantics 標籤

**症狀**:
- 產生不該存在的 setter 或 command
- 違反領域不變量

**修復**: 執行 Pre-Flight Step 4，讀取並套用 `semantics/` 目錄下的對應語意定義

### 錯誤 6: 使用 Instance Field Initializer（覆蓋 Event Replay 狀態）⭐⭐⭐ CRITICAL

```java
// ❌ 錯誤程式碼
private boolean isDeleted = false;
private List<CommittedSprint> committedSprints = new ArrayList<>();
```

**症狀**:
- Primitive 欄位：event replay 設定的值被覆蓋回預設值（如 `isDeleted` replay 為 `true` 卻變回 `false`）
- Collection 欄位：event replay 時集合為 `null`，直接 `NullPointerException`

**根本原因**: Java 物件初始化順序（JLS §12.5）：
1. JVM 分配記憶體，所有欄位設為預設值（`false`/`0`/`null`）
2. 執行 `super(domainEvents)` → 在其中 replay 所有事件 → `when()` 設定狀態
3. **執行 instance field initializer**（`= false`/`= new ArrayList<>()`）→ **覆蓋步驟 2 的結果！**

```
JVM default    super(events) replay    field initializer
┌──────────┐   ┌──────────────────┐   ┌──────────────────┐
│ false    │ → │ true (replay)    │ → │ false (= false)  │  ← 最終: false ❌
└──────────┘   └──────────────────┘   └──────────────────┘
```

**修復**: 所有欄位不使用 field initializer，在 `when(ConstructionEvent)` 中初始化：
```java
// ✅ 正確：不使用 field initializer（注意：isDeleted/version 由父類提供，不可宣告）
private String name;
private List<CommittedSprint> committedSprints;

private void when(ProductEvents.ProductCreated event) {
    this.id = event.productId();
    this.name = event.name();                  // 在 when() 中初始化
    this.committedSprints = new ArrayList<>(); // 在 when() 中初始化
}
```

**規則**: **所有繼承 `EsAggregateRoot` 的子類別，禁止使用 instance field initializer（`= value`）。所有欄位必須在 `when(ConstructionEvent)` 中初始化。**

### 錯誤 7: Shadow 父類欄位（version / isDeleted）⭐⭐⭐ CRITICAL

```java
// ❌ 錯誤：子類別重新宣告 version 和 isDeleted
public class ScrumTeam extends EsAggregateRoot<ScrumTeamId, ScrumTeamEvents> {
    private long version;       // SHADOWING! EsAggregateRoot 已有此欄位
    private boolean isDeleted;  // SHADOWING! EsAggregateRoot 已有此欄位
}
```

**症狀**:
- `isDeleted()` 永遠回傳 `false`（讀到子類別未初始化的欄位）
- `getVersion()` 永遠回傳 `0`
- InMemory 測試可能通過（直接操作物件），但 Outbox 測試 IDF query 失敗
- 軟刪除後的 query 仍會回傳已刪除的資料

**修復**:
```java
// ✅ 正確：不宣告 version 和 isDeleted，使用父類的
public class ScrumTeam extends EsAggregateRoot<ScrumTeamId, ScrumTeamEvents> {
    // 只宣告業務欄位
    private ScrumTeamId id;
    private String name;
    // version 和 isDeleted 由 EsAggregateRoot 管理
}
```

**Why**: Java field shadowing 讓子類別的欄位隱藏父類別同名欄位。
`EsAggregateRoot.isDeleted()` 讀的是父類別的 `isDeleted`，但 `when()` 中
`this.isDeleted = true` 寫的是子類別的 shadowed field → 讀寫不一致。

---

## INPUT

| Source | Path |
|--------|------|
| CBF Frame | `JSON spec `aggregates[]`` |
| SWF Frame | `JSON spec `aggregates[]`` |

---

## OUTPUT

| File | Location |
|------|----------|
| Aggregate Root | `src/main/java/{rootPackage}/{aggregate}/entity/{Aggregate}.java` |

**Note:** Domain Events and Value Objects are generated by separate skills:
- `domain-event-skill` → `{Aggregate}Events.java`
- `value-object-skill` → `{Aggregate}Id.java`, other VOs

---

## CRITICAL RULES (Embedded - Cannot Be Skipped)

### Rule 1: Base Class

```java
// CORRECT
public class ${Aggregate} extends EsAggregateRoot<${Aggregate}Id, ${Aggregate}Events> {

// WRONG - These classes DO NOT exist!
public class ${Aggregate} extends AggregateRoot { }
public class ${Aggregate} extends EventSourcedAggregateRoot { }
public class ${Aggregate} extends AbstractAggregateRoot { }
```

### Rule 2: Event Sourcing Golden Rule

**State can ONLY be set in the `when()` method!**

```java
// CORRECT: Constructor uses apply() which triggers when()
public Product(ProductId productId, String name) {
    super();
    requireNotNull("productId", productId);
    requireNotNull("name", name);

    apply(new ProductEvents.ProductCreated(
        productId,
        name,
        new HashMap<>(),
        UUID.randomUUID(),
        DateProvider.now()
    ));

    ensure("ID matches", () -> this.id.equals(productId));
}

private void when(ProductEvents.ProductCreated event) {
    this.id = event.productId();      // State set HERE only
    this.name = event.name();
}

// WRONG: Direct state assignment in constructor
public Product(ProductId productId, String name) {
    super();
    this.id = productId;      // VIOLATION! Bypasses event sourcing
    this.name = name;         // VIOLATION!
    apply(new ProductEvents.ProductCreated(this.id, this.name, ...));
}
```

<!-- @authority: dateprovider_not_instant | source: rules/common-rules.md -->

### Rule 3: DateProvider Usage

```java
// CORRECT
apply(new ProductEvents.ProductCreated(
    productId,
    name,
    UUID.randomUUID(),
    DateProvider.now()      // Use DateProvider
));

// WRONG
apply(new ProductEvents.ProductCreated(
    productId,
    name,
    UUID.randomUUID(),
    Instant.now()           // NEVER use Instant.now() directly
));
```

### Rule 4: Constructor Parameters vs this.xxx

```java
// CORRECT: Use constructor parameters in apply()
public Product(ProductId productId, String name) {
    apply(new ProductEvents.ProductCreated(productId, name, ...));
    //                                     ^^^^^^^^^ ^^^^
    //                                     Use parameters, NOT this.xxx
}

// WRONG: Using this.xxx before state is set
public Product(ProductId productId, String name) {
    apply(new ProductEvents.ProductCreated(this.id, this.name, ...));
    //                                     ^^^^^^^ ^^^^^^^^^
    //                                     this.xxx is null here!
}
```

### Rule 5: Nullable Field Comparison

```java
// CORRECT: Use Objects.equals() for null-safe comparison
ensure("Description matches", () -> Objects.equals(description, this.description));

// WRONG: Verbose if-else
if (description != null) {
    ensure("Description matches", () -> this.description.equals(description));
} else {
    ensure("Description is null", () -> this.description == null);
}
```

### Rule 6: Contract Helper Methods (PIT Support)

```java
// CORRECT: Underscore-prefixed helpers excluded from mutation testing
private boolean _nameMatches(String expected) {
    return Objects.equals(this.name, expected);
}

public void rename(String newName, String userId) {
    require("Name not blank", () -> !newName.isBlank());
    apply(new ProductEvents.ProductRenamed(this.id, newName, new HashMap<>(), UUID.randomUUID(), DateProvider.now()));
    ensure("Name matches", () -> _nameMatches(newName));
}
```

> ⚠️ 此範例僅示範 contract helper 用法。完整的 update command 還需搭配
> Rule 7 (`ignore()` 冪等性) 和 Rule 8 (`require("Not deleted")` 軟刪除檢查)，
> 見 Example Output 區段的完整範例。

### Rule 7: Idempotency with ignore()

```java
// CORRECT: Use ignore() for semantic clarity
public void select(SprintId sprintId, String userId) {
    require("Must be in backlog", () -> this.state == PbiState.IN_BACKLOG);

    if (ignore("Already selected - idempotent no-op",
               () -> this.state == PbiState.SELECTED &&
                     Objects.equals(this.sprintId, sprintId))) {
        return;
    }

    apply(new PbiEvents.PbiSelected(this.id, sprintId, userId, DateProvider.now()));
}

// WRONG: Plain if-check without semantic meaning
if (this.state == PbiState.SELECTED) {
    return;  // Unclear why we're returning
}
```

### Rule 8: Soft Delete Support (When Spec Requires Deletion)

> **SPEC-DRIVEN**: 只在 spec 定義了刪除操作（如 `delete` command 或 `soft_delete_flag` 語意標籤）時才產生 soft delete。
> 不可因此規則而無條件為所有 aggregate 加上刪除功能。

```java
// When spec includes delete: use inherited isDeleted from EsAggregateRoot
// ⚠️ 不需宣告 isDeleted — AggregateRoot 已有 protected boolean isDeleted
// ⚠️ 不需 override isDeleted() — AggregateRoot 的 getter 讀取同一個欄位
// 子類別只需在 when() handler 中設定 this.isDeleted = true

public void delete(String userId) {
    require("Not already deleted", () -> !this.isDeleted);
    apply(new ProductEvents.ProductDeleted(this.id, userId, DateProvider.now()));
}

private void when(ProductEvents.ProductDeleted event) {
    this.isDeleted = true;  // accesses AggregateRoot.protected isDeleted
}

// Invariant: Most operations should check isDeleted
public void rename(String newName, String userId) {
    require("Not deleted", () -> !this.isDeleted);
    // ...
}
```

#### ❌ Anti-Pattern 1: Declaring private isDeleted (Field Shadowing)

```java
// ❌ WRONG — SHADOWS AggregateRoot.isDeleted
private boolean isDeleted;

// parent's isDeleted() reads PARENT's field → always false
// unless you also @Override isDeleted() → unnecessary complexity
```

#### ❌ Anti-Pattern 2: Empty when(DestructionEvent) Handler

```java
// ❌ WRONG — EsAggregateRoot does NOT auto-set isDeleted
private void when(ProductEvents.ProductDeleted event) {
    // empty body — isDeleted stays false forever!
}
// Result: postcondition ensure("is deleted") always throws
```

### Rule 9: Event Handler Switch Pattern

**推薦**：delegated overload 模式（每個 event 一個 private when() 方法），提升可讀性。

```java
// ✅ PREFERRED: Delegated overload pattern
@Override
protected void when(ProductEvents event) {
    switch (event) {
        case ProductEvents.ProductCreated e -> when(e);
        case ProductEvents.ProductRenamed e -> when(e);
        case ProductEvents.ProductDeleted e -> when(e);
    }
}

// Individual handlers
private void when(ProductEvents.ProductCreated event) { ... }
private void when(ProductEvents.ProductRenamed event) { ... }
private void when(ProductEvents.ProductDeleted event) { ... }
```

> **備註**：inline block 模式（`case ... e -> { this.x = e.x(); }`）也完全可接受，
> `references/examples/Plan.java` 即採用此風格。兩種皆為 valid patterns，選擇取決於 event handler 數量和複雜度。
> "Delegated overload pattern" 標記為 PREFERRED 僅為可讀性建議，而非強制要求。兩種皆不加 `default` 分支（sealed interface 已窮舉）。

### Rule 10: Public Constructor (Not Factory)

```java
// CORRECT: Public constructor
public Product(ProductId productId, String name) {
    // ...
}

// WRONG: Static factory method
public static Product create(ProductId productId, String name) {
    return new Product(productId, name);
}
```

### Rule 11: No Instance Field Initializer (MANDATORY) ⭐⭐⭐

**所有繼承 `EsAggregateRoot` 的子類別，禁止使用 instance field initializer。**

Java 的 instance field initializer 在 `super()` 返回之後才執行，會覆蓋 event replay 建構子（`super(domainEvents)`）中 `when()` 設定的狀態。

```java
// ❌ WRONG: field initializer 會覆蓋 event replay 狀態
private boolean isDeleted = false;
private List<Item> items = new ArrayList<>();
private String note = "";
private int count = 0;

// ✅ CORRECT: 不使用 field initializer，在 when(ConstructionEvent) 初始化
// ⚠️ 注意：isDeleted/version 由父類 EsAggregateRoot 提供（protected），子類別不宣告也不在 ConstructionEvent 中設定
//    父類的 protected field 預設為 false（JVM default）；只在 DestructionEvent handler 中設定 this.isDeleted = true
private List<Item> items;
private String note;
private int count;

private void when(ProductEvents.ProductCreated event) {
    this.id = event.productId();
    this.items = new ArrayList<>();
    this.note = null;
    this.count = 0;
}
```

**適用範圍**: 此規則適用於 `EsAggregateRoot` 的所有子類別中的所有欄位，包括：
- primitive types（`boolean`、`int`、`long`）
- reference types（`String`、`List`、`Map`）
- 即使初始值與 JVM 預設值相同（如 `= false`、`= 0`、`= null`），也禁止寫出

### Rule 12: Event Replay Constructor (MANDATORY) ⭐⭐⭐

**Every `EsAggregateRoot` MUST have an event replay constructor in addition to the business constructor.**

This constructor is required by `EsRepository` to reconstruct the aggregate from persisted events.
**Note**: Mapper does NOT use this constructor — Mapper uses **Business Constructor + command methods** to rebuild state from `Data` snapshot, then calls `setVersion(data.getVersion())` + `clearDomainEvents()` to restore version and discard reconstruction events (order matters: setVersion BEFORE clearDomainEvents, see mapper.md Rule 9.5).

```java
// CORRECT: Both constructors present
public class Product extends EsAggregateRoot<ProductId, ProductEvents> {

    // Event replay constructor — used by EsRepository to rebuild from Event Store
    public Product(List<ProductEvents> domainEvents) {
        super(domainEvents);
    }

    // Business constructor — used by Service.execute() to create new aggregate
    public Product(ProductId productId, String name, ...) {
        super();
        requireNotNull("productId", productId);
        apply(new ProductEvents.ProductCreated(...));
        ensure(...);
    }
}

// WRONG: Only business constructor — EsRepository will fail to compile!
public class Product extends EsAggregateRoot<ProductId, ProductEvents> {
    public Product(ProductId productId, String name, ...) { ... }
    // ❌ Missing: Product(List<ProductEvents>)
}
```

**Key differences:**

| | Business Constructor | Event Replay Constructor |
|---|---|---|
| Caller | `CreateProductService` | `EsRepository` (event store replay) |
| Preconditions | `requireNotNull(...)` | None (events already validated) |
| Events | `apply()` creates NEW events | `super(domainEvents)` replays existing |
| Postconditions | `ensure(...)` | None |

---

## VERIFICATION CHECKPOINTS

### Checkpoint 1: Input Validation

Before generating code, verify:

| Check | Command | Expected |
|-------|---------|----------|
| aggregate.yaml exists | `test -f ${aggregateYamlPath}` | File exists |
| Has `name` field | `grep "^name:" ${aggregateYamlPath}` | Found |
| Has `attributes` field | `grep "^attributes:" ${aggregateYamlPath}` | Found |

```
IF ANY CHECK FAILS:
  Report error: "Invalid aggregate.yaml: missing required field"
  STOP - do not proceed
```

### Checkpoint 2: Pre-Generation Verification

Before writing any file:

| Check | Verification |
|-------|--------------|
| Base class | Must be `EsAggregateRoot<${Id}, ${Events}>` |
| Constructor | Uses `apply()` not direct assignment |
| Events | Uses `DateProvider.now()` |
| State changes | Only in `when()` methods |

```
IF ANY CHECK FAILS:
  Fix the generated code
  Re-verify before writing
```

### Checkpoint 3: Post-Generation Verification

After writing the file:

```bash
# Compile check
cd ${projectRoot} && mvn compile -q -pl :${module} 2>&1 | head -20

# Verify no direct state assignment in constructor
grep -n "this\.[a-z]* = " ${outputFile} | grep -v "when("
# Should return empty (all assignments should be in when() methods)
```

```
IF COMPILATION FAILS:
  Analyze error
  Fix and regenerate

IF DIRECT ASSIGNMENT FOUND OUTSIDE when():
  This is a CRITICAL violation of Event Sourcing
  Fix immediately
```

---

## GENERATION TEMPLATES

### Step 1: Parse aggregate.yaml

Extract:
- `name`: Aggregate name (e.g., "Product")
- `attributes`: Field definitions with types and semantics
- `commands`: Command methods to generate
- `domain_events`: Events to reference (generated by domain-event-skill)
- `invariants`: Class-level invariants
- `contracts`: Method-level pre/post conditions

### Step 2: Generate Package Declaration

```java
package ${rootPackage}.${aggregateLowerCase}.entity;
```

### Step 3: Generate Imports

```java
// ⚠️ CRITICAL: 使用正確的 import 路徑！
// 參考: .claude/skills/ezddd-java/references/framework/ezapp-api.md

// Framework imports (ezapp-starter 2.0.0)
import tw.teddysoft.ezddd.entity.EsAggregateRoot;      // NOT ezddd.core.aggregate!
import static tw.teddysoft.ucontract.Contract.*;       // static import! Contract is a utility class, NOT an interface

// Project imports
import ${rootPackage}.common.entity.DateProvider;      // DateProvider 在 common.entity 套件

// Java imports
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
```

### Step 4: Generate Class Declaration

```java
// ⚠️ CRITICAL: Contract is a UTILITY CLASS, NOT an interface!
// Use: import static tw.teddysoft.ucontract.Contract.*;
// Do NOT use: implements Contract (COMPILATION ERROR!)
public class ${Aggregate} extends EsAggregateRoot<${Aggregate}Id, ${Aggregate}Events> {
```

### Step 5: Generate Fields

For each attribute in aggregate.yaml:
```java
// ⚠️ 禁止 field initializer — 所有欄位在 when(ConstructionEvent) 中初始化（見錯誤 6）
private ${Type} ${fieldName};
```

⚠️ **不要宣告** `isDeleted` 或 `version` — 這些欄位由 `EsAggregateRoot` 提供（`protected` 存取），
子類別只需在 `when(DestructionEvent)` 中設定 `this.isDeleted = true`。宣告會造成 field shadowing（見 critical-rules Rule 21）。

### Step 6: Generate Event Replay Constructor (Rule 12)

**This MUST be generated BEFORE the business constructor.**

```java
public ${Aggregate}(List<${Aggregate}Events> domainEvents) {
    super(domainEvents);
}
```

> ⚠️ Requires `import java.util.List;` — ensure it is included in Step 3.

### Step 6.5: Generate Business Constructor

Following Rule 2 (Event Sourcing Golden Rule):
```java
public ${Aggregate}(${ConstructorParams}) {
    super();
    // require() validations

    apply(new ${Aggregate}Events.${Aggregate}Created(..., DateProvider.now()));

    // ensure() postconditions
}
```

### Step 7: Generate Command Methods

For each command in aggregate.yaml:
```java
public void ${commandName}(${Params}) {
    // require() preconditions
    // ignore() for idempotency (if applicable)

    apply(new ${Aggregate}Events.${EventName}(..., DateProvider.now()));

    // ensure() postconditions
}
```

### Step 8: Generate Event Handlers

```java
@Override
protected void when(${Aggregate}Events event) {
    switch (event) {
        case ${Aggregate}Events.${Event1} e -> when(e);
        case ${Aggregate}Events.${Event2} e -> when(e);
        // ... all events
    }
}

private void when(${Aggregate}Events.${Event1} event) {
    // State assignment ONLY here
}
```

### Step 9: Generate Contract Helpers

For postcondition checks:
```java
private boolean _${fieldName}Matches(${Type} expected) {
    return Objects.equals(this.${fieldName}, expected);
}
```

### Step 9.5: Generate getId() Override (MANDATORY)

`EsAggregateRoot` 繼承的 `Entity` 介面要求實作 `getId()`。**缺少此方法會導致編譯錯誤**：
`${Aggregate} is not abstract and does not override abstract method getId()`

```java
@Override
public ${Aggregate}Id getId() {
    return id;
}
```

### Step 10: Generate CATEGORY constant and getCategory()

```java
public final static String CATEGORY = "${Aggregate}";

@Override
public String getCategory() {
    return CATEGORY;
}
```

---

## EXAMPLE OUTPUT

For input `aggregate.yaml`:
```yaml
name: Product
attributes:
  - name: id
    type: ProductId
  - name: name
    type: String
  - name: description
    type: String
    nullable: true
commands:
  - name: rename
    params:
      - name: newName
        type: String
      - name: userId
        type: String
```

Generated `Product.java`:
```java
package tw.teddysoft.aiscrum.product.entity;

// ⚠️ CRITICAL: 正確的 import 路徑
import tw.teddysoft.ezddd.entity.EsAggregateRoot;          // NOT ezddd.core.aggregate!
import static tw.teddysoft.ucontract.Contract.*;           // static import! NOT implements Contract
import tw.teddysoft.aiscrum.common.entity.DateProvider;   // DateProvider 在 common.entity 套件
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Product extends EsAggregateRoot<ProductId, ProductEvents> {
    public final static String CATEGORY = "Product";

    private ProductId id;
    private String name;
    private String description;
    // ⚠️ isDeleted 和 version 由 EsAggregateRoot 提供（見 Rule 7、Rule 8）

    // Event replay constructor (Rule 12) — required by EsRepository
    public Product(List<ProductEvents> domainEvents) {
        super(domainEvents);
    }

    // Business constructor
    public Product(ProductId productId, String name) {
        super();
        requireNotNull("productId", productId);
        requireNotNull("name", name);

        apply(new ProductEvents.ProductCreated(
                productId,
                name,
                new HashMap<>(),
                UUID.randomUUID(),
                DateProvider.now()
        ));

        ensure("ID matches", () -> this.id.equals(productId));
        ensure("Name matches", () -> _nameMatches(name));
    }

    public void rename(String newName, String userId) {
        require("Not deleted", () -> !this.isDeleted);
        require("Name not blank", () -> !newName.isBlank());

        if (ignore("Name unchanged", () -> _nameMatches(newName))) return;

        apply(new ProductEvents.ProductRenamed(
                this.id,
                newName,
                new HashMap<>(),
                UUID.randomUUID(),
                DateProvider.now()
        ));

        ensure("Name matches", () -> _nameMatches(newName));
    }

    @Override
    protected void when(ProductEvents event) {
        switch (event) {
            case ProductEvents.ProductCreated e -> when(e);
            case ProductEvents.ProductRenamed e -> when(e);
        }
    }

    private void when(ProductEvents.ProductCreated event) {
        this.id = event.productId();
        this.name = event.name();
    }

    private void when(ProductEvents.ProductRenamed event) {
        this.name = event.newName();
    }

    // getId() — Entity interface requires this (Step 9.5)
    @Override
    public ProductId getId() {
        return id;
    }

    // Contract helpers
    private boolean _nameMatches(String expected) {
        return Objects.equals(this.name, expected);
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }
}
```

---

## INTEGRATION WITH ORCHESTRATOR

When called by `code executor`:

```
code executor
    ↓
    Step 4.1: Invoke aggregate-skill
    ├─ Input: ${problemFramePath}/controlled-domain/aggregate.yaml
    ├─ Output: ${Aggregate}.java
    └─ Next: domain-event-skill (Step 4.1.1)
```

---

## ERROR HANDLING

| Error | Action |
|-------|--------|
| aggregate.yaml not found | Report error, STOP |
| Missing required fields | Report specific field, STOP |
| Compilation error | Analyze, fix, retry (max 3 attempts) |
| Event Sourcing violation detected | CRITICAL - fix immediately |

---
