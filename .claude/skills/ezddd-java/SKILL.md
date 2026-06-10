---
name: ezddd-java
description: |
  Java + ezddd + Spring 的 AI Coding 完整工具包。
  Use when:
  - 執行 UC Executor 產生 DDD 程式碼
  - Code Review 審查程式碼
  - Mutation Testing 提升測試覆蓋率
  - 需要依據 JSON spec 產生任何 DDD 模式的程式碼
  - User asks for "multi-model review", "多模型審查", "cross-model review"
---

# Java EzDDD Spring Skill

這是一個**完全自包含**的 AI Coding 工具包，用於 Java + Spring Boot + ezddd 框架的開發。

## Required Project Conventions

> 本 Skill 的所有 pattern、template、workflow 都是自包含的，但需要宿主專案提供以下兩個介面：

| Convention | Path | 說明 |
|-----------|------|------|
| **Project Config** | `.dev/project-config.json` | 專案配置（rootPackage、database、architecture） |
| **JSON Specs** | `.dev/specs/{aggregate}/usecase/{use-case}.json` | UC 規格檔案 |

### Project Config Schema（必要欄位）

| JSON Path | 用途 | 範例 |
|-----------|------|------|
| `rootPackage` | Java package 前綴，所有程式碼產生的根 | `tw.teddysoft.aiscrum` |
| `database.environments.test.port` | Outbox 測試用 PostgreSQL port | `5800` |
| `database.environments.test.name` | Outbox 測試用資料庫名稱 | `board` |
| `database.environments.test.schema` | Outbox 測試用 schema | `message_store` |
| `architecture.commandDefaults.dualProfileSupport` | 是否啟用 InMemory + Outbox 雙 profile 測試 | `true` |
| `architecture.aggregates.{Name}.pattern` | 各 Aggregate 的架構模式 | `outbox` |

### JSON Specs Directory Structure

```
.dev/specs/
  {aggregate}/                       # e.g., product
    usecase/
      {use-case}.json               # e.g., create-product.json
    adapter/
      {controller}.json              # e.g., product-controller.json
```

### 移植到新專案

將此 Skill 用於新專案時，只需確保：
1. 在 `.dev/project-config.json` 提供上述必要欄位
2. 在 `.dev/specs/` 按上述結構放置 JSON 規格檔案
3. Skill 內所有 pattern 和 template 會自動讀取這兩個介面

## 核心原則

### SPEC-DRIVEN PRINCIPLE
> **Spec defines what to generate - no more, no less**

所有程式碼產生都必須：
1. 完全依照 JSON spec 定義
2. 不多產生 spec 沒要求的功能
3. 不少產生 spec 要求的功能

### Layered Validation Architecture
```
┌─────────────────────────────────────────┐
│  Use Case Layer (error_mapping)         │  ← 第一道防線：業務規則驗證
├─────────────────────────────────────────┤
│  Aggregate Layer (constraint)           │  ← 第二道防線：領域不變量
└─────────────────────────────────────────┘
```

### Event Sourcing Golden Rule
> **State can ONLY be set in `when()` method**

```java
// ✅ CORRECT - state in when()
private void when(SprintStarted event) {
    this.state = SprintState.STARTED;  // OK
}

// ❌ WRONG - state outside when()
public void start() {
    this.state = SprintState.STARTED;  // FORBIDDEN!
}
```

## 功能模組

### 1. Project Initialization (`/init-project`)
初始化新專案所需的基礎設施：
- **Dual Profile** 支援 (InMemory + Outbox)
- **Event Sourcing** with Domain Events
- **Clean Architecture** 結構
- **Test Infrastructure** with ProfileSetter pattern

**使用方式**：
```
/init-project                  # 使用 project-config.json 預設值
/init-project --dry-run        # 預覽產生的檔案
/init-project --force          # 強制覆蓋現有檔案
```

**產生檔案（15 個）**：
| Phase | 內容 | 檔案數 |
|-------|------|-------|
| Phase 1 | Main Application | 1 |
| Phase 2 | Shared Infrastructure | 6 |
| Phase 3 | Test Infrastructure | 3 |
| Phase 4 | Application Properties | 5 |

**工作流程文件**：`references/init-project/README.md`

**參考檔案**：
| 檔案 | 用途 |
|------|------|
| `init-project/workflow.md` | 執行流程（Step 0-5） |
| `init-project/templates.md` | 完整程式碼模板 |

### 2. Code Reviewer (`/code-review`)
依據 Clean Architecture 審查程式碼：
- Event Sourcing 合規性（Aggregate Root 必審）
- Clean Architecture 層次
- 編碼標準符合度

**執行模式**：
| Mode | Command | Models | Accuracy |
|------|---------|--------|----------|
| **Default** | `/code-review Sprint.java` | Claude only | ⭐⭐⭐ |
| **Strict** | `/code-review Sprint.java --strict` | Claude (7-step workflow) | ⭐⭐⭐⭐⭐ |
| **IntelliJ** | `/code-review Sprint.java --intellij` | IntelliJ MCP (L0) + Claude (L1) | ⭐⭐⭐⭐⭐ |
| **Multi-Model** | `/code-review Sprint.java --multi` | 4 LLMs | ⭐⭐⭐⭐⭐ |

> **自動觸發 Strict**：Aggregate Root (`**/entity/{Name}.java`) 和 Domain Event (`**/entity/*Events.java`) 自動使用 Strict 模式。

#### IntelliJ 整合模式（L0 + L1 分層審查）

```
L0: 確定性層 (IntelliJ MCP)  ← 100% 準確，0 幻覺，毫秒級
    ├── ERROR: 編譯錯誤、型別錯誤
    ├── WARNING: 未使用變數、Null Safety
    └── WEAK WARNING: 程式碼重複、風格問題

L1: 語意層 (Claude)          ← 架構、業務邏輯、設計審查
```

**執行流程**: L0（`mcp__intellij-idea__get_file_problems`）→ L0 阻擋檢查（ERROR 則停止）→ L1 語意審查 → 合併報告（`Final Rating = min(L0, L1)`）

**L0 評分**: ERROR → ⭐ | WARNING > 10 → ⭐⭐ | WARNING 5-10 → ⭐⭐⭐ | WARNING 1-4 → ⭐⭐⭐⭐ | 無 WARNING → ⭐⭐⭐⭐⭐

**使用方式**：
```
/code-review Sprint.java              # 預設模式
/code-review Sprint.java --strict     # 嚴格模式（7 步驟 workflow）
/code-review Sprint.java --intellij   # IntelliJ L0 + Claude L1 分層審查
/code-review Sprint.java --multi      # 多模型審查（4 LLMs）
/code-review Product aggregate        # Aggregate 完整審查
```

**工作流程文件**：`references/code-reviewer/README.md`

**參考檔案**：
| 檔案 | 用途 |
|------|------|
| `code-reviewer/checklist.md` | 完整檢查清單（按檔案類型） |
| `code-reviewer/workflow.md` | 執行流程（Default + Multi-Model） |
| `code-reviewer/aggregate-review.md` | Aggregate 完整審查模式 |
| `code-reviewer/must-fail-conditions.md` | 必須失敗的條件清單 |
| `multi-model/dispatch.md` | 多模型 dispatch 引擎（4 LLMs） |

#### Multi-Model Infrastructure (4 LLMs)

Multi-Model 模式由共用 dispatch 引擎驅動，支援兩種 prompt 策略：

| Strategy | Prompt Template | 觸發方式 |
|----------|----------------|---------|
| **spec-compliance** | `multi-model/prompts/spec-compliance-review.md` | UC executor `--multi-model` |
| **code-quality** | `multi-model/prompts/code-quality-review.md` | Code reviewer `--multi` |

**4 Model Dispatch** (共用引擎：`references/multi-model/dispatch.md`)：

| # | Model | Method | File Access |
|---|-------|--------|-------------|
| 1 | Claude (Sonnet) | Sub-agent (Task tool) | Yes |
| 2 | Gemini | Local CLI (`cat \| gemini -y`) | Yes |
| 3 | ChatGPT (gpt-5.2) | Remote API (curl) | No |
| 4 | Codex (gpt-5-codex) | Local CLI (`cat \| codex exec -`) | Yes |

**相關參考檔案**：
| 檔案 | 用途 |
|------|------|
| `multi-model/dispatch.md` | 共用 dispatch 引擎（Phase 2-5） |
| `multi-model/model-config.md` | 4 LLM 配置、prerequisites |
| `multi-model/context-conservation.md` | 輸出限制政策 |
| `multi-model/false-positive-rules.md` | 3 條 FP 避免規則 |
| `multi-model/troubleshooting.md` | CLI 問題排除 |
| `multi-model/prompts/` | Prompt 策略模板 |
| `multi-model/schemas/` | JSON output schemas |
| `multi-model/aggregators/weighted-consensus.md` | 加權共識演算法 |

### 3. Mutation Tester (`/mutation-test`)
使用 PIT 提升 mutation coverage：
- 識別存活 mutants
- 產生補充測試
- 驗證 contract 完整性

**執行模式**：
| Mode | Command | Threshold | Use Case |
|------|---------|-----------|----------|
| **Default** | `/mutation-test Entity` | 75% | DBC design, accept postcondition survivors |
| **Spy Mode** | `/mutation-test Entity --spy` | 85% | Generate spy tests to kill all mutants |
| **Parallel Mode** | `/mutation-test --parallel E1,E2,...` | 75% | Test multiple aggregates simultaneously |

**使用方式**：
```
/mutation-test Sprint                           # 預設模式
/mutation-test Sprint --spy                     # 產生 spy tests
/mutation-test --parallel Product,Sprint        # 平行測試多個 aggregates
/mutation-test --parallel all                   # 自動發現並測試所有 aggregates
```

**工作流程文件**：`references/mutation-tester/README.md`

**參考檔案**：
| 檔案 | 用途 |
|------|------|
| `mutation-tester/workflow.md` | 執行流程（Phase 1-6） |
| `mutation-tester/rules.md` | 必遵守規則 + ignore() 模式 |
| `mutation-tester/strategies.md` | 策略指南 + 常見 mutant 類型 |

### 4. UseCase Spec Executor (`/execute-uc`)

從 JSON UseCase Specification 產生 DDD 程式碼。

**Supported Spec Types**：

| Spec Type | 辨識欄位 | 產生程式碼 |
|-----------|---------|-----------|
| **Command** | `useCase` + `domainEvent` | Aggregate + Events + UseCase + Service + Tests + Infra |
| **Query** | `query` + `projections` | Projection + Mapper + DTO + UseCase + Tests |
| **Reactor** | `reactor` + `events` | Reactor Interface + Service + Inquiry + Tests |
| **Pattern** | `pattern` + `entity` | Interface + ReadOnly Proxy + JUnit 5 Tests |

**使用方式**：
```bash
/execute-uc .dev/specs/product/usecase/create-product.json          # Command
/execute-uc .dev/specs/product/usecase/get-products.json             # Query
/execute-uc .dev/specs/pbi/usecase/reactor/notify-pbi-reactor.json   # Reactor
/execute-uc --dry-run .dev/specs/sprint/usecase/start-sprint.json    # Dry-run
```

**執行步驟總覽**：
1. **Spec 驗證** (Phase 0): 讀取 JSON、偵測 spec type、驗證必要欄位
2. **JIT 學習** (Phase 1): 載入 critical-rules + json-to-pattern-mapping
3. **程式碼產生** (Phase 2): 依據 spec type 選擇生成路徑（Command/Query/Reactor）
4. **編譯驗證** (Phase 3): `mvn compile -q`
5. **Dual-Profile 測試** (Phase 4): Gate 1 — InMemory + Outbox ⛔ BLOCKING
6. **確定性審查** (Phase 5): Gate 2.5 — 55 rules 靜態檢查 ⛔ BLOCKING
7. **完成報告** (Phase 7): 產生檔案清單、測試結果、Gate 狀態

**⛔ 強制阻擋閘門**：

| Gate | Step | 說明 | 共用/專用 |
|------|------|------|----------|
| JSON Validation | 0.3 | 必要欄位檢查 | 專用 |
| Dual-Profile | 6 | InMemory + Outbox 測試 | 共用 |
| Deterministic Review | 9.5 | 55 rules 靜態檢查 | 共用 |


**工作流程文件**：`references/uc-executor/uc-workflow.md`

**參考檔案**：
| 檔案 | 用途 |
|------|------|
| `uc-executor/uc-workflow.md` | 完整執行流程（Phase 0-7） |
| `uc-executor/json-to-pattern-mapping.md` | JSON → Pattern 欄位對照表 |
| `rules/critical-rules.md` | 共用 JIT 規則（27F + 16R） |

## Pattern 參考（按層次）

當產生特定類型的程式碼時，載入對應的 pattern 參考：

### Domain Layer
| Pattern | 參考檔案 | 使用時機 |
|---------|---------|---------|
| Aggregate Root | `references/patterns/domain/aggregate.md` | 建立或修改 Aggregate |
| Entity | `references/patterns/domain/entity.md` | 建立子 Entity |
| Value Object | `references/patterns/domain/value-object.md` | 建立 Value Object |
| Domain Event | `references/patterns/domain/domain-event.md` | 建立 Domain Events |

### UseCase Layer
| Pattern | 參考檔案 | 使用時機 |
|---------|---------|---------|
| Command | `references/patterns/usecase/command.md` | 建立 Command Use Case |
| Query | `references/patterns/usecase/query.md` | 建立 Query Use Case |
| Reactor | `references/patterns/usecase/reactor.md` | 建立 Reactor |

### Infrastructure Layer
| Pattern | 參考檔案 | 使用時機 |
|---------|---------|---------|
| Config | `references/patterns/infrastructure/config.md` | 建立 Spring Config |
| Mapper | `references/patterns/infrastructure/mapper.md` | 建立 ORM Mapper |
| Outbox | `references/patterns/infrastructure/outbox.md` | 實作 Outbox Pattern |
| Persistent Object | `references/patterns/infrastructure/persistent-object.md` | 建立 JPA Entity |

### Adapter Layer
| Pattern | 參考檔案 | 使用時機 |
|---------|---------|---------|
| Controller | `references/patterns/adapter/controller.md` | 建立 REST Controller |

### Testing
| Pattern | 參考檔案 | 使用時機 |
|---------|---------|---------|
| UseCase Test | `references/patterns/testing/usecase-test.md` | 建立 UseCase 測試 |
| Contract Test | `references/patterns/testing/contract-test.md` | 建立 Contract 測試 |
| Controller Test | `references/patterns/testing/controller-test.md` | 建立 Controller 測試 |
| Repository Test | `references/patterns/testing/repository-test.md` | 建立 Repository 測試 |

## 共用規則

所有功能都必須遵守 `references/rules/` 下的規則：

### 1. Common Rules (`common-rules.md`)
- SPEC-DRIVEN 原則
- 測試規範
- 核心約束

### 2. Framework API (`framework-api.md`)
- 正確的 import 路徑
- ezapp-starter 2.0.0 API 用法
- 常見錯誤避免

### 3. Domain Patterns (`domain-patterns.md`)
- Aggregate 使用公開建構子
- Domain Event 必須包含 metadata
- 狀態變更必須發出事件
- Contract helpers 使用 `_` 前綴

### 4. UseCase Patterns (`usecase-patterns.md`)
- Input/Output 必須是 UseCase 的 inner class
- Service 使用 @Bean 註冊（不用 @Component）
- Repository 只用標準三方法

### 5. Testing Patterns (`testing-patterns.md`)
- 禁止 @ActiveProfiles
- 必須 @DirtiesContext(AFTER_EACH_TEST_METHOD)
- Dual-Profile 測試（InMemory + Outbox）

## 完整範例

`references/examples/` 包含完整的複雜 Aggregate 實作範例：

- **Plan.java** - 完整的 Aggregate Root 實作，包含：
  - 狀態機
  - Child Entity 管理
  - Reconstruction Constructor
  - Contract Helpers

- **PlanEvents.java** - 完整的 Domain Events 實作，包含：
  - Sealed interface 模式
  - TypeMapper 實作
  - Event Metadata

## Templates

`references/templates/` 包含可直接使用的模板：

- `aggregate-config-template.md` - Aggregate-specific Config 模板
- `base-test-classes.md` - BaseUseCaseTest 模板
- `test-suites.md` - TestSuite 模板
- `reactor-full.md` - Reactor 完整模板
- `local-utils.md` - DateProvider 等本地工具

## Framework API 詳細參考

`references/framework/` 包含詳細的框架 API 文件：

- `ezapp-api.md` - ezapp-starter 2.0.0 完整 API 參考
- `class-index.md` - 所有框架類別索引

## 檔案結構

```
ezddd-java/
├── SKILL.md                              # 本檔案
└── references/
    ├── AUTHORITY-REGISTRY.yaml           # Pattern 權威來源註冊表
    ├── assertions/                       # Pattern 自動化斷言 (3)
    │   ├── command-input.yaml
    │   ├── domain-event-mapper.yaml
    │   └── test-input-construction.yaml
    ├── rules/                            # 共用規則（必讀）
    │   ├── common-rules.md               # 核心規則
    │   ├── framework-api.md              # 框架 API 快速參考
    │   ├── domain-patterns.md            # Domain 層模式
    │   ├── usecase-patterns.md           # UseCase 層模式
    │   └── testing-patterns.md           # 測試模式
    ├── patterns/                         # 詳細 Pattern 參考
    │   ├── domain/                       # Domain 層 (4)
    │   │   ├── aggregate.md
    │   │   ├── entity.md
    │   │   ├── value-object.md
    │   │   └── domain-event.md
    │   ├── usecase/                      # UseCase 層 (3)
    │   │   ├── command.md
    │   │   ├── query.md
    │   │   └── reactor.md
    │   ├── infrastructure/               # Infrastructure 層 (4)
    │   │   ├── config.md
    │   │   ├── mapper.md
    │   │   ├── outbox.md
    │   │   └── persistent-object.md
    │   ├── testing/                      # Testing (4)
    │   │   ├── usecase-test.md
    │   │   ├── contract-test.md
    │   │   ├── controller-test.md
    │   │   └── repository-test.md
    │   └── adapter/                      # Adapter 層 (1)
    │       └── controller.md
    ├── templates/                        # 程式碼模板 (8)
    │   ├── README.md                     # 模板總覽
    │   ├── aggregate-config-template.md  # Aggregate-Specific Config
    │   ├── base-test-classes.md          # 測試基底類別
    │   ├── complex-aggregate-spec.md     # 複雜 Aggregate 規格範例
    │   ├── local-utils.md               # DateProvider 等共用類別
    │   ├── reactor-full.md              # Reactor 完整模板
    │   ├── test-case-full.md            # 測試案例完整模板
    │   └── test-suites.md               # TestSuite 配置模板
    ├── examples/                         # 完整範例
    │   ├── Plan.java                     # 複雜 Aggregate 範例
    │   ├── PlanEvents.java               # Domain Events 範例
    │   └── dual-profile-test-infrastructure.md  # 雙 Profile 測試基礎設施
    ├── framework/                        # Framework 詳細文件
    │   ├── ezapp-api.md
    │   └── class-index.md
    │       ├── preparation-steps.md      # Steps 0.x 準備階段
    │       ├── learning-steps.md         # Steps 1-2 學習階段
    │       ├── context-checkpoint.md     # Context 檢查點
    │       ├── testing-steps.md          # Step 4.3 測試產生
    │       ├── validation-steps.md       # Steps 5-9 驗證階段
    │       └── completion-steps.md       # Steps 10-12 完成階段
    ├── code-reviewer/                    # Code Review (完整版)
    │   ├── README.md                     # 總覽
    │   ├── checklist.md                  # 完整檢查清單
    │   ├── workflow.md                   # 執行流程
    │   ├── aggregate-review.md           # Aggregate 完整審查
    │   └── must-fail-conditions.md       # 必須失敗條件
    ├── init-project/                     # 專案初始化 (完整版)
    │   ├── README.md                     # 總覽
    │   ├── workflow.md                   # 執行流程
    │   └── templates.md                  # 程式碼模板
    ├── mutation-tester/                  # Mutation Testing (完整版)
    │   ├── README.md                     # 總覽
    │   ├── workflow.md                   # 執行流程 (Phase 1-6)
    │   ├── rules.md                      # 必遵守規則
    │   └── strategies.md                 # 策略指南
    ├── multi-model/                      # Multi-Model Review 引擎（4 LLMs）
    │   ├── dispatch.md                   # 共用 dispatch 引擎
    │   ├── model-config.md               # 4 LLM 配置、prerequisites
    │   ├── context-conservation.md       # 輸出限制政策
    │   ├── false-positive-rules.md       # FP 避免規則
    │   ├── troubleshooting.md            # CLI 問題排除
    │   ├── prompts/                      # Prompt 策略模板
    │   │   ├── spec-compliance-review.md # Spec compliance 審查
    │   │   └── code-quality-review.md    # Code quality 審查
    │   ├── schemas/                      # JSON output schemas
    │   │   ├── review-result.schema.json
    │   │   └── aggregated-report.schema.json
    │   └── aggregators/
    │       └── weighted-consensus.md     # 加權共識演算法
scripts/
├── check-pattern-consistency.sh          # Pattern 一致性檢查
├── run-pattern-assertions.sh             # Pattern 斷言執行
├── validate-generated-code.sh            # Gate 2.5 確定性審查（支援 --json）
```

## 總行數統計

| 類別 | 檔案數 | 行數 |
|------|-------|------|
| Patterns | 16 | ~13,000 |
| Rules | 5 | ~1,900 |
| Templates | 8 | ~2,000 |
| Examples | 3 | ~2,100 |
| Framework | 2 | ~960 |
| Code-Reviewer | 5 | ~1,060 |
| Init-Project | 3 | ~1,040 |
| Mutation-Tester | 4 | ~978 |
| Multi-Model | 9 | ~600 |
| Assertions + Registry | 4 | ~200 |
| Scripts | 3 | ~400 |
| SKILL.md | 1 | ~540 |
| **總計** | **62** | **~24,400** |

## 設計原則

- **自包含**：所有 pattern 定義集中在 `references/`，不依賴外部知識庫
- **Skill 自動載入**：不需手動讀取 prompt
- **單一來源**：每個概念只在一處定義（見 `AUTHORITY-REGISTRY.yaml`）
- **獨立可複製**：可移植到其他專案（執行時讀取 `.dev/` 專案資料為輸入）
