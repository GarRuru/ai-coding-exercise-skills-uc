---
name: read-only-entity-skill
description: |
  Safely return Entities from an Aggregate to external clients using the
  Read-only Entities pattern.

  Triggered by:
  - Aggregate Root needs to hand an internal Entity (or a collection of
    Entities) to an external client (Use Case / Controller)
  - Direct user request: "return a read-only entity for [name]"

  Source: Hu, Chen, Cheng, Yang, "Supplemental Patterns for Domain-Driven
  Design: Read-only Entities, Internal Domain Events, and External Domain
  Events", JISE 42(4), 2026, §2.

  Two implementations are described: Special Case (inheritance) and
  Proxy (composition/delegation). **Proxy is recommended for this project.**
---

# Read-only Entities Pattern

## Overview

當 Aggregate Root 需要把內部 Entity 交給邊界外的 client（Use Case、Controller）時，
**Read-only Entities** 讓回傳的物件：

- 對 client 而言與原 Entity **同型別**（守住 Ubiquitous Language），
- 但任何**改狀態的 command 會立即拋例外**（fail-fast 告知誤用）。

> 📖 來源：JISE 42(4), 2026 §2「Read-only Entities」。

---

## ⚠️ 本專案適用判斷（先讀這段，避免過度設計）

論文的核心問題是「client 拿到回傳 entity 後**直接呼叫 public command 改狀態**，破壞 aggregate invariants」。

但本專案的 Entity 預設遵守 **Rule 2: Package-Private Mutation Methods**
（見 [`entity.md`](./entity.md) § Rule 2）：mutation 方法是 package-private，
跨 package 的外部 client **根本呼叫不到**。因此「直接改回傳 entity」這條主要攻擊面**已被擋掉**。

**結論：不要對每個 aggregate 無腦套用 Read-only Entities（違反 YAGNI）。**
只有出現下列**殘餘缺口**時才採用：

| 殘餘缺口 | 說明 | 對應解法 |
|---------|------|---------|
| 1. 回傳的 collection 可被結構性修改 | client 對回傳的 `List<Task>` 呼叫 `add`/`remove` | 回傳 `unmodifiableList`，內含 entity 也換成 read-only 版 |
| 2. 巢狀 entity getter | `getTask().subTask()` 又回傳可變 entity | getter 改回傳 read-only entity |
| 3. mutation 不得不 public | 因框架/序列化需求被迫公開 command 方法 | 回傳 read-only 版以攔截 command |
| 4. Defense-in-depth | 想要編譯期/執行期雙重保障，而非僅靠 package 慣例 | 視關鍵程度採用 |

**若 entity 全程只靠 package-private mutation + 不回傳可變 collection，就不需要本 pattern。**

---

## Context / Problem / Forces（摘自論文 §2.1–2.3）

**Context**：你遵守 Evans 的規則 —「邊界外只能持有 root 的參考；root 可短暫交出內部 entity 參考，但對方不得長期持有」。問題是除了開發者自律，沒有機制能阻止 client 留住參考並修改。

**Problem**：如何**安全地**從 aggregate 回傳 entity？

**Forces**：
- **Encapsulation**：client 不應修改回傳的 entity。
- **Ubiquitous Language**：回傳型別必須是 entity 的**原始 domain 型別**，而非 DTO / PO。
- **Informing misuses**：client 嘗試修改時必須被通知，否則產生**靜默分歧（silent divergence）**，日後爆成 bug。

---

## Solution

回傳 Read-only Entity。對 read-only entity 呼叫 query 照常運作；呼叫 command 則拋
`UnsupportedOperationException`。四種方法處理情境：

| 方法類型 | 處理方式 |
|---------|---------|
| Command（改狀態） | 覆寫 → 拋 `UnsupportedOperationException` |
| Query 回傳 immutable（VO / 基本型別） | 直接沿用 |
| Query 回傳 entity | 覆寫 → 回傳 read-only entity |
| Query 回傳 collection | 覆寫 → `unmodifiableList`；內含 entity 也換成 read-only 版 |

---

## 實作一：Proxy（組合 / 委派）— ⭐ 本專案建議

以 proxy 持有真實 entity 的參考，**不複製屬性**，逐一委派並做存取控制。
適合本專案的理由：不要求 entity 可被繼承、多型 entity 只需**一個** read-only 類別、
契合框架已提供的 `tw.teddysoft.ezddd.entity.Entity` 介面抽取慣例。

四步驟：
1. 從 entity 抽出介面（若還沒有）。
2. 建 read-only 類別實作該介面（而非繼承 concrete class）。
3. 在 read-only 類別內持有 real entity 參考（`real`）。
4. 實作所有介面方法：query 委派、command 拋例外、回傳 entity/collection 換成 read-only 版。

```java
// 1. 從 Task 抽出介面
public interface ITask {
    TaskId id();
    String getName();           // query, 回傳 immutable
    TaskState getState();       // query, 回傳 immutable
    void markComplete();        // command, 改狀態
    void rename(String name);   // command, 改狀態
}

// 真實 entity 實作介面（mutation 仍維持 package-private 給 aggregate 使用）
public class Task implements ITask, Entity {
    private TaskId id;
    private String name;
    private TaskState state;

    @Override public TaskId id() { return id; }
    @Override public String getName() { return name; }
    @Override public TaskState getState() { return state; }

    @Override public void markComplete() { this.state = TaskState.DONE; }
    @Override public void rename(String newName) { this.name = newName; }
}

// 2~4. Read-only proxy
public class ReadOnlyTask implements ITask {
    private final ITask real;                       // 持有真實 entity 參考

    public ReadOnlyTask(ITask real) { this.real = real; }

    // query → 委派
    @Override public TaskId id() { return real.id(); }
    @Override public String getName() { return real.getName(); }
    @Override public TaskState getState() { return real.getState(); }

    // command → 拋例外（fail-fast 告知誤用）
    @Override public void markComplete() {
        throw new UnsupportedOperationException("ReadOnlyTask is immutable");
    }
    @Override public void rename(String newName) {
        throw new UnsupportedOperationException("ReadOnlyTask is immutable");
    }
}
```

```java
// Aggregate Root 對外回傳 read-only 版
public class Plan extends EsAggregateRoot<PlanId, PlanEvents> {
    private final Map<TaskId, Task> tasks = new HashMap<>();

    // 單一 entity：回傳 read-only
    public ITask getTask(TaskId taskId) {
        return new ReadOnlyTask(tasks.get(taskId));
    }

    // collection：unmodifiable + 內含 entity 換成 read-only
    public List<ITask> getTasks() {
        return tasks.values().stream()
                .map(ReadOnlyTask::new)
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }
}
```

---

## 實作二：Special Case（繼承覆寫）

read-only entity **繼承**原 entity、shallow-copy 屬性，再覆寫方法。
與論文主範例一致，直觀但**每個 concrete entity 都要一個對應 read-only 子類**（重複），
且要求 entity 允許被繼承。

```java
public class ReadOnlyTask extends Task {
    public ReadOnlyTask(Task task) {
        super(task.id(), task.getName(), task.getState());  // shallow copy 屬性
    }

    // 覆寫 command → 拋例外
    @Override public void markComplete() {
        throw new UnsupportedOperationException("ReadOnlyTask is immutable");
    }
    @Override public void rename(String newName) {
        throw new UnsupportedOperationException("ReadOnlyTask is immutable");
    }

    // 回傳 immutable 的 query（getName / getState / id）直接繼承，無需覆寫
}
```

> **兩者取捨**：Proxy 消除重複、不需可繼承的 entity，但須先有共同介面（沒有就得重構抽取）並委派所有方法；
> Special Case 不需介面，但要求 entity 可被繼承、且每種 entity 各一份 read-only 類別。

---

## Resulting Context（採用後）

- ✅ **封裝保全**：client 無法繞過 root 修改 entity。
- ✅ **對齊 Ubiquitous Language**：回傳型別仍屬 domain model（非 DTO）。
- ✅ **誤用即時回饋**：嘗試修改立即拋例外（fail-fast）。
- ⚠️ **刻意違反 LSP**：read-only entity 保留型別介面但行為契約被改（不允許改狀態），
  嚴格解讀下破壞可替換性。這是**蓄意取捨** — 以 LSP 換取更強的封裝與完整性。
- ⚠️ **實作成本**：Special Case 有重複；Proxy 需共同介面 + 全方法委派。

---

## Related Patterns / 替代方案（為何不用）

| 替代方案 | 問題 |
|---------|------|
| 回傳 DTO | 改變方法簽章、暴露非 domain model 型別 → 違反 Ubiquitous Language。本專案 Query 結果用 DTO/Projection 是**另一條路**（read model），不要與「回傳 write-model entity」混用。 |
| Prototype（回傳 deep copy） | client 改 copy **不會失敗**，誤以為 aggregate 已更新 → 缺 fail-fast，產生隱藏 bug。 |
| 純靠 package-private mutation | 本專案預設防線，已擋掉大多數情況；但對「回傳 collection 結構修改」與「public mutation」無能為力 → 此時才補本 pattern。 |

---

## 相關文件

- [`entity.md`](./entity.md) § Rule 2 — Package-Private Mutation（本專案預設封裝防線）
- [`aggregate.md`](./aggregate.md) — Aggregate Root 對外介面
- [`../usecase/query.md`](../usecase/query.md) — Query / Projection（read model，與本 pattern 互補）
- 論文 §3–4：Internal / External Domain Events（同篇的另兩個 pattern）
