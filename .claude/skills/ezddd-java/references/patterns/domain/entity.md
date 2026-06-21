---
name: entity-skill
description: |
  Generate Child Entity classes following DDD patterns.

  Triggered by:
  - code executor (Step 4.1.3)
  - Direct user request: "generate entity for [name]"

  Input: aggregate.yaml specification with entity definitions
  Output: {ChildEntity}.java, {ChildEntity}Id.java files

  Child Entities are objects within an Aggregate that have unique identity
  and lifecycle, but cannot exist independently of the Aggregate Root.

  This skill embeds critical Child Entity rules to ensure generated code
  is correct without relying on AI to read external files.

allowed-tools: Read, Write, Edit, Bash, Glob
---

# Child Entity Generation Skill

## Overview

This skill generates Child Entity classes following DDD patterns.
Child Entities are distinct from Aggregate Roots and Value Objects:

| Concept | Identity | Mutability | Lifecycle | Event Publishing |
|---------|----------|------------|-----------|------------------|
| **Aggregate Root** | Global unique ID | Mutable | Independent | Yes (`apply()`) |
| **Child Entity** | Unique within aggregate | Mutable or Immutable | Dependent on aggregate | No |
| **Value Object** | By all attributes | Immutable | None | No |

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
| Child Entity | `src/main/java/{rootPackage}/{aggregate}/entity/{ChildEntity}.java` |
| Entity ID | `src/main/java/{rootPackage}/{aggregate}/entity/{ChildEntity}Id.java` |

---

## ENTITY CATEGORIES

### Category 1: Simple Record Entity (Immutable)

**Use for:** Entities created once and never modified.

```java
package tw.teddysoft.aiscrum.board.entity;

import java.util.Objects;

/**
 * Represents a board member within the Board aggregate.
 * This is an immutable child entity.
 */
public record BoardMember(
    UserId userId,
    BoardId boardId,
    BoardMemberType memberType
) {
    public BoardMember {
        Objects.requireNonNull(userId, "UserId cannot be null");
        Objects.requireNonNull(boardId, "BoardId cannot be null");
        Objects.requireNonNull(memberType, "MemberType cannot be null");
    }
}
```

**Examples:**
- `BoardMember` (userId, boardId, memberType)
- `CommittedWorkflow` (workflowId, boardId, order)
- `SprintGoal` (goalId, sprintId, description)

### Category 2: Mutable Class Entity

**Use for:** Entities whose state changes over time.

```java
package tw.teddysoft.aiscrum.plan.entity;

import java.util.Objects;

/**
 * Represents a task within the Plan aggregate.
 * This is a mutable child entity.
 */
public class Task {
    private final TaskId id;
    private final PlanId planId;
    private String name;
    private TaskState state;

    public Task(TaskId id, PlanId planId, String name) {
        this.id = Objects.requireNonNull(id, "TaskId cannot be null");
        this.planId = Objects.requireNonNull(planId, "PlanId cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        this.state = TaskState.TODO;
    }

    // Public getters
    public TaskId getId() { return id; }
    public PlanId getPlanId() { return planId; }
    public String getName() { return name; }
    public TaskState getState() { return state; }

    // Package-private mutation methods (called by Aggregate's when())
    void rename(String newName) {
        this.name = Objects.requireNonNull(newName, "New name cannot be null");
    }

    void markComplete() {
        this.state = TaskState.DONE;
    }

    // Identity-based equality
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task that = (Task) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
```

**Examples:**
- `Task` (has TaskId, state changes TODO → IN_PROGRESS → DONE)
- `SprintBacklogItem` (state changes during sprint)
- `Stage` (can be renamed, reordered)

### Category 3: Collection Item Entity

**Use for:** Entities managed as collection items within aggregate.

```java
// In Aggregate Root - storing child entities
// ⚠️ NOTE: EsAggregateRoot subclasses MUST NOT use field initializers (aggregate.md Rule 11).
//    Collections are initialized in when(ConstructionEvent), NOT at field declaration.
public class Plan extends EsAggregateRoot<PlanId, PlanEvents> {

    // No field initializers! Initialized in when(PlanCreated)
    private Map<TaskId, Task> tasks;       // initialized in when() as new HashMap<>()
    private List<Milestone> milestones;    // initialized in when() as new ArrayList<>()

    // Access methods
    public boolean hasTask(TaskId taskId) {
        return tasks.containsKey(taskId);
    }

    public Task getTask(TaskId taskId) {
        return tasks.get(taskId);
    }

    public List<Task> getAllTasks() {
        return List.copyOf(tasks.values());
    }
}
```

---

## CRITICAL RULES (Embedded - Cannot Be Skipped)

### Rule 1: No Direct Domain Event Publishing

**Child entities NEVER publish domain events.** Only Aggregate Root can call `apply()`.

```java
// ❌ WRONG: Child entity publishing events
public class Task {
    public void complete() {
        apply(new TaskCompleted(...));  // Task doesn't have apply()!
    }
}

// ✅ CORRECT: Aggregate Root publishes events
public class Plan extends EsAggregateRoot<PlanId, PlanEvents> {
    public void completeTask(TaskId taskId, String userId) {
        Task task = getTask(taskId);
        require("Task exists", () -> task != null);
        require("Task not already complete", () -> task.getState() != TaskState.DONE);

        apply(new PlanEvents.TaskCompleted(
            this.id, taskId, userId, DateProvider.now()
        ));
    }

    private void when(PlanEvents.TaskCompleted event) {
        Task task = tasks.get(event.taskId());
        task.markComplete();  // Mutation via package-private method
    }
}
```

**Rationale:** Event Sourcing requires all state changes to go through the Aggregate Root's event handler.

### Rule 2: Package-Private Mutation Methods

**Mutation methods should be package-private (default access), not public.**

```java
// ✅ CORRECT: Package-private mutation
public class Task {
    // Public: read-only access
    public String getName() { return name; }
    public TaskState getState() { return state; }

    // Package-private: only Aggregate can mutate
    void rename(String newName) {
        this.name = newName;
    }

    void markComplete() {
        this.state = TaskState.DONE;
    }
}

// ❌ WRONG: Public mutation methods
public class Task {
    public void rename(String newName) {  // WRONG! Should be package-private
        this.name = newName;
    }
}
```

**Rationale:** Encapsulation ensures all mutations go through Aggregate Root.

> **對外回傳 entity 時**：package-private mutation 已擋掉跨 package 的 client 直接改狀態。
> 但若需回傳 entity 給外部、回傳可變 collection，或 mutation 不得不 public，
> 請改用 **Read-only Entities** pattern → 見 [`read-only-entity.md`](./read-only-entity.md)。

### Rule 3: Identity-Based Equality

**Entities are equal if their IDs are equal, regardless of other attributes.**

```java
// ✅ CORRECT: Equality based on ID only
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Task that = (Task) o;
    return Objects.equals(id, that.id);  // Only compare ID
}

@Override
public int hashCode() {
    return Objects.hash(id);  // Only hash ID
}

// ❌ WRONG: Equality based on all fields
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Task that = (Task) o;
    return Objects.equals(id, that.id) &&
           Objects.equals(name, that.name) &&  // WRONG! Don't include
           Objects.equals(state, that.state);   // WRONG! Don't include
}
```

**Rationale:** Entity identity is defined by its ID, not its current state.

### Rule 4: Parent Aggregate Reference

**Child entities should reference their parent aggregate's ID.**

```java
// ✅ CORRECT: Include parent aggregate ID
public record BoardMember(
    UserId userId,          // This entity's identity
    BoardId boardId,        // Parent aggregate reference ← REQUIRED
    BoardMemberType type
) { }

// ❌ WRONG: Missing parent reference
public record BoardMember(
    UserId userId,
    BoardMemberType type    // Where does this belong? Unclear!
) { }
```

**Rationale:** Parent reference enables navigation and ensures data integrity.

### Rule 5: Constructor Validation with Objects.requireNonNull()

```java
// ✅ CORRECT: Validate in constructor
public Task(TaskId id, PlanId planId, String name) {
    this.id = Objects.requireNonNull(id, "TaskId cannot be null");
    this.planId = Objects.requireNonNull(planId, "PlanId cannot be null");
    this.name = Objects.requireNonNull(name, "Name cannot be null");
    if (name.isBlank()) {
        throw new IllegalArgumentException("Name cannot be blank");
    }
    this.state = TaskState.TODO;
}

// ❌ WRONG: Using Contract
public Task(TaskId id, PlanId planId, String name) {
    require("id not null", () -> id != null);  // WRONG! Use Objects.requireNonNull
    this.id = id;
}

// ❌ WRONG: No validation
public Task(TaskId id, PlanId planId, String name) {
    this.id = id;      // No null check!
    this.planId = planId;
    this.name = name;
}
```

**Rationale:** Child entities use standard Java validation, not Contract (which is for Aggregate Root).

### Rule 6: State Changes in Aggregate's when() Method

**Entity state mutations occur in response to events, in the Aggregate Root's `when()` method.**

```java
// ✅ CORRECT: State change in when()
@Override
protected void when(PlanEvents event) {
    switch (event) {
        // Create entity
        case PlanEvents.TaskCreated e -> {
            tasks.put(e.taskId(), new Task(e.taskId(), id, e.taskName()));
        }

        // Modify entity
        case PlanEvents.TaskRenamed e -> {
            Task task = tasks.get(e.taskId());
            task.rename(e.newName());
        }

        // Delete entity
        case PlanEvents.TaskDeleted e -> {
            tasks.remove(e.taskId());
        }
    }
}

// ❌ WRONG: Direct mutation outside when()
public void renameTask(TaskId taskId, String newName) {
    Task task = tasks.get(taskId);
    task.rename(newName);  // WRONG! Must go through event
}
```

**Rationale:** Event Sourcing requires state reconstruction from events.

### Rule 7: Final ID Field

**The ID field must be `final` (immutable after construction).**

```java
// ✅ CORRECT: Final ID
public class Task {
    private final TaskId id;        // FINAL - cannot change
    private final PlanId planId;    // FINAL - parent reference
    private String name;            // Can change
}

// ❌ WRONG: Non-final ID
public class Task {
    private TaskId id;              // WRONG! ID should be final
}
```

**Rationale:** Entity identity must not change during its lifecycle.

### Rule 7.1: Field Mutability Analysis (CRITICAL) ⭐⭐⭐

**Before generating Entity, MUST analyze all related Use Cases to determine field mutability.**

```
╔════════════════════════════════════════════════════════════════════╗
║  ⛔ STOP! Entity 不是 Value Object，不能全部 final！              ║
║                                                                    ║
║  產生 Entity 前必須執行：                                          ║
║  1. 找出所有會操作此 Entity 的 Use Cases                           ║
║  2. 分析每個操作會修改哪些欄位                                      ║
║  3. 被修改的欄位 → 不用 final                                       ║
║  4. 不會被修改的欄位 → 用 final                                     ║
╚════════════════════════════════════════════════════════════════════╝
```

**判斷規則表：**

| 欄位類型 | final? | 原因 |
|---------|--------|------|
| Identity (id) | ✅ final | 身份不變 |
| Parent reference (xxxId) | ✅ final | 所屬關係不變 |
| 建立時間 (createdAt, definedAt) | ✅ final | 歷史記錄不可改 |
| 集合欄位 (List, Map) | ✅ final (參考) | 參考不變，內容可變 |
| 狀態欄位 (state, status) | ❌ 不用 | 會被操作改變 |
| 業務欄位 (title, description) | ❌ 分析後決定 | 檢查是否有修改操作 |
| 時間戳 (updatedAt, revisedAt) | ❌ 不用 | 每次修改都更新 |

**範例分析流程：**

假設有 `ProductGoal` Entity，相關 Use Cases：

| Frame | 操作 | 影響欄位 |
|-------|------|---------|
| set-product-goal | 建立 | 全部 |
| revise-product-goal | 修改 | title, description, revisedAt |
| add-product-goal-metric | 新增 metric | metrics (集合內容) |
| complete-product-goal | 完成目標 | state |

**分析結果：**

```java
// ✅ 正確：根據分析結果決定 final
public class ProductGoal {
    private final ProductGoalId id;           // ✅ identity 不變
    private final ProductId productId;        // ✅ parent reference 不變
    private String title;                      // ❌ 會被 revise 修改
    private String description;                // ❌ 會被 revise 修改
    private final List<GoalMetric> metrics;   // ✅ 參考 final，內容可變
    private final Instant definedAt;          // ✅ 建立時間不變
    private Instant revisedAt;                 // ❌ 會被 revise 更新
    private ProductGoalState state;            // ❌ 會被 complete 改變
}

// ❌ 錯誤：全部 final（把 Entity 當 Value Object）
public class ProductGoal {
    private final ProductGoalId id;
    private final String title;           // WRONG! 會被修改
    private final String description;     // WRONG! 會被修改
    private final Instant revisedAt;      // WRONG! 會被修改
    private final ProductGoalState state; // WRONG! 會被修改
}
```

**Rationale:** Entity 狀態會隨時間變化；只有 identity 和建立時不變的欄位才用 final。

### Rule 8: Record for Immutable, Class for Mutable

```java
// ✅ CORRECT: Use record for immutable entities
public record BoardMember(
    UserId userId,
    BoardId boardId,
    BoardMemberType memberType
) { }

// ✅ CORRECT: Use class for mutable entities
public class Task {
    private final TaskId id;
    private String name;  // Mutable field needs class
}

// ❌ WRONG: Record for mutable entity
public record Task(TaskId id, String name, TaskState state) {
    // Cannot mutate state in record!
}
```

**Rationale:** Records are inherently immutable; use classes when state changes are needed.

### Rule 9: Package Location in entity Package

```java
// ✅ CORRECT: Same package as Aggregate
package tw.teddysoft.aiscrum.plan.entity;

public class Task { }
public record TaskId(String value) implements ValueObject { }

// ❌ WRONG: Separate child-entity package
package tw.teddysoft.aiscrum.plan.childentity;  // WRONG!

// ❌ WRONG: Common package
package tw.teddysoft.aiscrum.common.entity;    // WRONG!
```

**Rationale:** Child entities belong to their Aggregate's bounded context.

### Rule 10: No Orphan Entities

**Child entities must always have a valid parent reference.**

```java
// ✅ CORRECT: Entity created with parent reference
public Task(TaskId id, PlanId planId, String name) {
    this.planId = Objects.requireNonNull(planId, "PlanId cannot be null");
}

// ❌ WRONG: Entity without parent
public Task(TaskId id, String name) {
    // Where does this task belong? Orphan entity!
}
```

**Rationale:** Child entities cannot exist independently of their Aggregate.

---

## VERIFICATION CHECKPOINTS

### Checkpoint 0: Field Mutability Analysis (MANDATORY) ⭐⭐⭐

**BEFORE generating any Entity code, MUST complete this analysis:**

```
╔════════════════════════════════════════════════════════════════════╗
║  ENTITY FIELD MUTABILITY ANALYSIS CHECKLIST                        ║
╠════════════════════════════════════════════════════════════════════╣
║  □ 1. List all Use Cases that operate on this Entity               ║
║  □ 2. For each Use Case, identify which fields are modified        ║
║  □ 3. Fill in the analysis table below                             ║
║  □ 4. Mark fields as final/non-final based on analysis             ║
╚════════════════════════════════════════════════════════════════════╝

Analysis Table Template:
| Field | Type | Modified By (Use Cases) | final? |
|-------|------|------------------------------|--------|
| id | {Entity}Id | None | ✅ yes |
| {parent}Id | {Parent}Id | None | ✅ yes |
| title | String | revise-xxx | ❌ no |
| state | XxxState | complete-xxx, archive-xxx | ❌ no |
| createdAt | Instant | None | ✅ yes |
| updatedAt | Instant | All mutations | ❌ no |
| items | List<Item> | add-xxx, remove-xxx | ✅ ref only |
```

```
IF ANALYSIS NOT COMPLETED:
  STOP - do not proceed with generation
  Report: "Must analyze field mutability before generating Entity"

IF ALL FIELDS MARKED AS FINAL:
  WARNING - this is likely a Value Object, not an Entity
  Re-verify: Does this object have identity? Does state change?
```

### Checkpoint 1: Input Validation

Before generating code, verify:

| Check | Command | Expected |
|-------|---------|----------|
| aggregate.yaml exists | `test -f ${aggregateYamlPath}` | File exists |
| Has entities defined | `grep -E "entities:|childEntities:" ${aggregateYamlPath}` | Found |

```
IF NO ENTITIES DEFINED:
  Skip entity generation (not all aggregates have child entities)
  Report: "No child entities defined in aggregate.yaml"
```

### Checkpoint 2: Pre-Generation Verification

Before writing any file:

| Check | Verification |
|-------|--------------|
| No apply() calls | Entity does not publish events |
| Package-private mutations | Mutation methods are not public |
| Final ID field | ID field is declared final |
| Parent reference | Aggregate ID is included |
| Constructor validation | Uses Objects.requireNonNull() |

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

# Verify no apply() calls in entity
grep "apply(" ${entityFile}
# Should return empty

# Verify mutation methods are not public
grep -E "public void (rename|update|change|set|mark)" ${entityFile}
# Should return empty (mutations should be package-private)
```

```
IF COMPILATION FAILS:
  Analyze error
  Fix and regenerate

IF PUBLIC MUTATION FOUND:
  Change to package-private (remove public modifier)
```

---

## GENERATION TEMPLATES

### Step 1: Identify Child Entities from aggregate.yaml

Look for:
- `entities:` or `childEntities:` section
- Collections with `type: List<{EntityName}>` or `type: Map<{Id}, {Entity}>`
- References with lifecycle (created, modified, deleted)

### Step 2: Determine Entity Category

| Indicator | Category |
|-----------|----------|
| No state changes | Category 1: Simple Record |
| Has mutable fields | Category 2: Mutable Class |
| Stored in collection | Category 3: Collection Item |

### Step 3: Generate Entity ID (if needed)

```java
package ${rootPackage}.${aggregateLowerCase}.entity;

import tw.teddysoft.ezddd.entity.ValueObject;
import java.util.Objects;
import java.util.UUID;

public record ${ChildEntity}Id(String value) implements ValueObject {
    public ${ChildEntity}Id {
        Objects.requireNonNull(value, "${ChildEntity}Id cannot be null");
    }

    public static ${ChildEntity}Id create() {
        return new ${ChildEntity}Id(UUID.randomUUID().toString());
    }

    public static ${ChildEntity}Id valueOf(String value) {
        return new ${ChildEntity}Id(value);
    }

    public static ${ChildEntity}Id valueOf(UUID uuid) {
        return new ${ChildEntity}Id(uuid.toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
```

### Step 4: Generate Entity Class

**For Immutable (Record):**
```java
package ${rootPackage}.${aggregateLowerCase}.entity;

import java.util.Objects;

public record ${ChildEntity}(
    ${ChildEntity}Id id,
    ${Aggregate}Id ${aggregateCamelCase}Id,
    ${AttributeType} ${attributeName}
) {
    public ${ChildEntity} {
        Objects.requireNonNull(id, "${ChildEntity}Id cannot be null");
        Objects.requireNonNull(${aggregateCamelCase}Id, "${Aggregate}Id cannot be null");
        Objects.requireNonNull(${attributeName}, "${attributeName} cannot be null");
    }
}
```

**For Mutable (Class):**
```java
package ${rootPackage}.${aggregateLowerCase}.entity;

import java.util.Objects;

public class ${ChildEntity} {
    private final ${ChildEntity}Id id;
    private final ${Aggregate}Id ${aggregateCamelCase}Id;
    private ${MutableType} ${mutableField};
    private ${State} state;

    public ${ChildEntity}(${ChildEntity}Id id, ${Aggregate}Id ${aggregateCamelCase}Id, ${MutableType} ${mutableField}) {
        this.id = Objects.requireNonNull(id, "${ChildEntity}Id cannot be null");
        this.${aggregateCamelCase}Id = Objects.requireNonNull(${aggregateCamelCase}Id, "${Aggregate}Id cannot be null");
        this.${mutableField} = Objects.requireNonNull(${mutableField}, "${mutableField} cannot be null");
        this.state = ${State}.INITIAL;
    }

    // Getters
    public ${ChildEntity}Id getId() { return id; }
    public ${Aggregate}Id get${Aggregate}Id() { return ${aggregateCamelCase}Id; }
    public ${MutableType} get${MutableFieldPascal}() { return ${mutableField}; }
    public ${State} getState() { return state; }

    // Package-private mutations
    void update${MutableFieldPascal}(${MutableType} new${MutableFieldPascal}) {
        this.${mutableField} = Objects.requireNonNull(new${MutableFieldPascal});
    }

    void changeState(${State} newState) {
        this.state = Objects.requireNonNull(newState);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ${ChildEntity} that = (${ChildEntity}) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
```

### Step 5: Update Aggregate Root (if needed)

Ensure Aggregate has:
- Collection field for entities
- Access methods (has, get, getAll)
- Event handlers in when() for entity lifecycle

---

## EXAMPLE OUTPUT

For aggregate.yaml with:
```yaml
name: Plan
entities:
  - name: Task
    mutable: true
    attributes:
      - name: id
        type: TaskId
      - name: name
        type: String
        mutable: true
      - name: state
        type: TaskState
        mutable: true
```

Generated files:

**TaskId.java:**
```java
package tw.teddysoft.aiscrum.plan.entity;

import tw.teddysoft.ezddd.entity.ValueObject;
import java.util.Objects;
import java.util.UUID;

public record TaskId(String value) implements ValueObject {
    public TaskId {
        Objects.requireNonNull(value, "TaskId cannot be null");
    }

    public static TaskId create() {
        return new TaskId(UUID.randomUUID().toString());
    }

    public static TaskId valueOf(String value) {
        return new TaskId(value);
    }

    public static TaskId valueOf(UUID uuid) {
        return new TaskId(uuid.toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
```

**Task.java:**
```java
package tw.teddysoft.aiscrum.plan.entity;

import java.util.Objects;

public class Task {
    private final TaskId id;
    private final PlanId planId;
    private String name;
    private TaskState state;

    public Task(TaskId id, PlanId planId, String name) {
        this.id = Objects.requireNonNull(id, "TaskId cannot be null");
        this.planId = Objects.requireNonNull(planId, "PlanId cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        this.state = TaskState.TODO;
    }

    public TaskId getId() { return id; }
    public PlanId getPlanId() { return planId; }
    public String getName() { return name; }
    public TaskState getState() { return state; }

    void rename(String newName) {
        this.name = Objects.requireNonNull(newName, "New name cannot be null");
    }

    void markComplete() {
        this.state = TaskState.DONE;
    }

    void changeState(TaskState newState) {
        this.state = Objects.requireNonNull(newState, "State cannot be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task that = (Task) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
```

---

## INTEGRATION WITH ORCHESTRATOR

When called by `code executor`:

```
code executor
    ↓
    Step 4.1.3: Invoke entity-skill (if entities defined)
    ├─ Input: ${problemFramePath}/controlled-domain/aggregate.yaml
    ├─ Output: ${ChildEntity}.java, ${ChildEntity}Id.java
    └─ Next: Step 4.2 (use-case-skill)
```

---

## ERROR HANDLING

| Error | Action |
|-------|--------|
| aggregate.yaml not found | Report error, STOP |
| No entities defined | Skip (OK - not all aggregates have child entities) |
| Compilation error | Analyze, fix, retry (max 3 attempts) |
| Public mutation detected | Change to package-private |
| apply() call detected | Remove - only Aggregate Root publishes events |

---
