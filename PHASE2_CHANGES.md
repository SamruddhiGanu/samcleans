# Phase 2 — Changes Summary
**Storage Health Ranker** · Ranking Engine, Health Scores, Recommendations & JavaFX Dashboard

---

## 1. Schema Migration

| File | Type | Purpose |
|------|------|---------||
| `V2__add_importance_score.sql` | [NEW] | Adds `importance_score REAL` column + index to `files` table |

---

## 2. Entity & Repository Updates

| File | Type | Change |
|------|------|--------|
| `FileEntity.java` | [MODIFIED] | Added `importanceScore` field (`@Column`) |
| `FileRepository.java` | [MODIFIED] | Added `findByScanSessionOrderByImportanceScoreDesc` ranked query |

---

## 3. Application Services

| File | Type | Purpose |
|------|------|---------||
| `FileRankingService.java` | [NEW] | Interface: `computeImportanceScore`, `rankFiles`, `getFilesByImportance` |
| `FileRankingServiceImpl.java` | [NEW] | 5-component weighted scorer: recency×0.2 + frequency×0.2 + semantic×0.3 + uniqueness×0.2 + feedback×0.1 |
| `StorageHealthScore.java` | [NEW] | Value object for all health sub-scores + `getHealthStatus()` tier (EXCELLENT/GOOD/FAIR/POOR) |
| `HealthScoreCalculator.java` | [NEW] | Interface: `calculateHealthScore(session)` |
| `HealthScoreCalculatorImpl.java` | [NEW] | Composite score: duplicateWaste×40% + clutter×30% + organisation×30% |
| `RecommendationEngine.java` | [NEW] | Interface: `generateRecommendations`, `getRecommendations` |
| `RecommendationEngineImpl.java` | [NEW] | 4 rules: OLD_SCREENSHOT, TEMP_FILE, UNUSED_LARGE_FILE, STALE_DOWNLOAD — idempotent |

---

## 4. REST API Layer

### New DTOs

| File | Used By |
|------|---------|
| `HealthScoreDTO.java` | `GET /api/health/score/{id}` |
| `RankedFileDTO.java` | `GET /api/ranking/files/{id}` |

### New/Updated Controllers

| File | Endpoints |
|------|-----------|
| `HealthController.java` | `GET /api/health/score/{sessionId}` |
| `RankingController.java` | `POST /api/ranking/run/{id}` · `GET /api/ranking/files/{id}` |
| `RecommendationController.java` | `POST /generate/{id}` · `GET /list?type=` · `PATCH /{id}/acted` |

---

## 5. JavaFX Desktop UI

| File | Type | Purpose |
|------|------|---------||
| `StorageHealthUIApplication.java` | [NEW] | JavaFX `Application` entry point — loads dark CSS, 1280×820 window |
| `StorageHealthMainWindow.java` | [NEW] | `BorderPane` root: menu bar + 4-tab pane + status bar |
| `ScanDashboard.java` | [NEW] | Dashboard tab: path chooser, Start/Cancel, animated score cards, BarChart |
| `DuplicateExplorer.java` | [NEW] | Duplicates tab: session-driven detection, sortable `TableView` |
| `DuplicateGroupRow.java` | [NEW] | JavaFX table row model for duplicate groups |
| `RecommendationsPanel.java` | [NEW] | Recommendations tab: type-filter ComboBox, generate/load, table |
| `RecommendationRow.java` | [NEW] | JavaFX table row model for recommendations |
| `SettingsPanel.java` | [NEW] | Settings tab: API URL and detection threshold configuration |
| `ApiClientService.java` | [NEW] | Java 11 `HttpClient` wrapper covering all backend REST endpoints |
| `dark-theme.css` | [NEW] | 350-line dark theme (indigo/slate palette) for all JavaFX controls |

---

## 6. Infrastructure Updates

| File | Change |
|------|--------|
| `DatabaseInitializer.java` | [MODIFIED] — now runs V1 + V2; V2 wrapped in try-catch for idempotency |

---

## 7. Unit Tests (20 new tests)

| File | Tests | Coverage |
|------|-------|----------|
| `FileRankingServiceImplTest.java` | 6 | Score range, recency, feedback boost/penalty, duplicate uniqueness, `rankFiles` persistence |
| `HealthScoreCalculatorImplTest.java` | 6 | Empty session, score range, duplicate impact, clutter impact, health status tiers, null score |
| `RecommendationEngineImplTest.java` | 8 | All 4 rules + negative cases + idempotency guard |

---

## Phase 2 Deliverable Checklist

| Plan Step | Deliverable | Status |
|-----------|-------------|--------|
| 2.1.1 | `FileRankingService` + `FileRankingServiceImpl` | ✅ |
| 2.1.2 | `StorageHealthScore` + `HealthScoreCalculator` + Impl | ✅ |
| 2.2.1 | `RecommendationEngine` + `RecommendationEngineImpl` | ✅ |
| 2.3.1 | `StorageHealthUIApplication` + `StorageHealthMainWindow` | ✅ |
| 2.3.2 | `ScanDashboard` (cards + chart + scan control) | ✅ |
| 2.3.3 | `DuplicateExplorer` (table + detection trigger) | ✅ |
| 2.3.x | `RecommendationsPanel` + `SettingsPanel` | ✅ |
| 2.4.1 | `HealthController` + `RankingController` + `RecommendationController` | ✅ |
| DTOs | `HealthScoreDTO`, `RankedFileDTO` | ✅ |
| UI Service | `ApiClientService` (Java 11 HttpClient) | ✅ |
| Styling | `dark-theme.css` (350-line indigo/slate theme) | ✅ |
| Schema | `V2__add_importance_score.sql` + `DatabaseInitializer` updated | ✅ |
| Tests | 20 new unit tests across 3 test classes | ✅ |

**Phase 2 files: 27 new, 3 modified · Cumulative project total: ~69 files**
