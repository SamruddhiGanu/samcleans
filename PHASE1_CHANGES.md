# Phase 1 — Changes Summary
**Storage Health Ranker** · Foundation & Core Scanning

> This document lists every file created during Phase 1 implementation, what it does, and how it maps to the implementation plan deliverables.

---

## 1. Build & Project Structure

| File | Type | Purpose |
|------|------|---------|
| `pom.xml` | [NEW] | Root Maven POM; declares `backend` and `desktop-ui` modules, shared dependency versions |
| `backend/pom.xml` | [NEW] | Backend module POM; adds Spring Boot Web, JPA, SQLite JDBC, HikariCP, Commons Codec, Lombok, Log4j2, JUnit 5, Mockito |
| `desktop-ui/pom.xml` | [NEW] | Desktop UI module POM; adds JavaFX 21 Controls/FXML/Web; depends on `backend` |
| `README.md` | [NEW] | Project overview, directory map, Quick-Start, REST API reference, phase status table |

---

## 2. Spring Boot Application Bootstrap

| File | Type | Purpose |
|------|------|---------|
| `StorageHealthApplication.java` | [NEW] | `@SpringBootApplication` entry point; boots the full Spring context |
| `application.yml` | [NEW] | SQLite datasource URL (`~/.storage-health/database.db`), HikariCP pool settings, JPA dialect, log file config, actuator endpoints |

---

## 3. Database Schema

| File | Type | Purpose |
|------|------|---------|
| `V1__initial_schema.sql` | [NEW] | Creates 7 tables + 10 performance indexes |
| `DatabaseInitializer.java` | [NEW] | `ApplicationStartedEvent` listener; ensures DB directory exists, enables FK pragma, runs V1 SQL idempotently on every startup |

---

## 4. Domain Enums

| File | Values |
|------|--------|
| `FileType.java` | IMAGE, VIDEO, DOCUMENT, ARCHIVE, EXECUTABLE, MEDIA, TEMPORARY, OTHER |
| `HashType.java` | SHA256, DPHASH, PHASH |
| `ScanStatus.java` | INITIATED, IN_PROGRESS, COMPLETED, FAILED, PAUSED |
| `RecommendationType.java` | DUPLICATE, NEAR_DUPLICATE, BLURRY_IMAGE, OLD_SCREENSHOT, TEMP_FILE, UNUSED_LARGE_FILE, EMPTY_FOLDER, STALE_DOWNLOAD |
| `CleanupStatus.java` | ACTIVE, COMPLETED, RESTORED, ARCHIVED |

---

## 5. JPA Entities

| File | Maps To | Key Relations |
|------|---------|---------------|
| `FileEntity.java` | `files` | → ScanSession (ManyToOne), → FileHash (OneToMany), → Recommendation (OneToMany) |
| `FileHashEntity.java` | `file_hashes` | → File (ManyToOne) |
| `ScanSessionEntity.java` | `scan_sessions` | → Files (OneToMany) |
| `RecommendationEntity.java` | `recommendations` | → File (ManyToOne) |
| `CleanupSessionEntity.java` | `cleanup_sessions` | → CleanupSessionFiles (OneToMany) |
| `CleanupSessionFileEntity.java` | `cleanup_session_files` | → CleanupSession, → File (ManyToOne each) |
| `UserFeedbackEntity.java` | `user_feedback` | → File (ManyToOne) |

---

## 6. JPA Repositories

| File | Notable Methods |
|------|----------------|
| `FileRepository.java` | `findByPath`, `findByScanSession`, `countDuplicatesForFile` (custom JPQL) |
| `FileHashRepository.java` | `findByHashValueAndHashType`, `findByFileAndHashType` |
| `ScanSessionRepository.java` | `findTopByOrderByCreatedDateDesc`, `findByStatus` |
| `RecommendationRepository.java` | `findByType(Pageable)`, `findByIsActedOnFalse(Pageable)` |
| `CleanupSessionRepository.java` | `findBySessionId`, `findByStatus` |
| `UserFeedbackRepository.java` | `findByFile` |

---

## 7. Application Services

| File | Pattern |
|------|---------|
| `FileScanner.java` (interface) | Contract + inner `ScanProgress` value class |
| `FileScannerImpl.java` | Multi-threaded `walkFileTree`; excludes system dirs; idempotent; cooperative cancel via `AtomicBoolean`; marks session COMPLETED/PAUSED/FAILED |
| `HashingService.java` (interface) | `computeHash`, `sha256Hash`, `dPhash` |
| `HashingServiceImpl.java` | SHA-256 with 8 KiB buffer; dPhash → `UnsupportedOperationException` (Phase 3 stub) |
| `DuplicateDetector.java` (interface) | `findExactDuplicates`, `markDuplicates`; inner `DuplicateGroup` with `calculate()` |
| `DuplicateDetectorImpl.java` | Two-pass (size grouping → SHA-256 hashing); reuses existing DB hash records; saves DUPLICATE recommendations |

---

## 8. REST API

### DTOs

| File | Used By |
|------|---------|
| `ScanRequest.java` | POST /api/scan/start body |
| `ScanSessionDTO.java` | Scan session response |
| `ScanProgressDTO.java` | Progress polling |
| `RecommendationDTO.java` | Recommendation list items |
| `DuplicateAnalysisDTO.java` | Detection aggregate response |
| `DuplicateGroupDTO.java` | Individual group summary |

### Controllers

| File | Endpoints |
|------|-----------|
| `ScanController.java` | `POST /api/scan/start` · `GET /progress/{id}` · `POST /cancel/{id}` · `GET /list` · `GET /{id}` |
| `DuplicateController.java` | `POST /api/duplicates/detect/{sessionId}` · `GET /recommendations` |

---

## 9. Unit Tests (20 total)

| File | Count | What's Covered |
|------|-------|----------------|
| `HashingServiceImplTest.java` | 7 | SHA-256 length, determinism, different content, empty file (known hash), `computeHash` delegation, dPhash stub, PHASH stub |
| `DuplicateDetectorImplTest.java` | 7 | Empty session, two identical files detected, different sizes skipped, hash reuse, recommendation creation, primary not marked, `calculate()` arithmetic |
| `FileScannerImplTest.java` | 6 | File persistence, COMPLETED status, idempotency, unknown session error, progress tracking, cancel flag |

---

## Phase 1 Deliverable Checklist

| Plan Step | Deliverable | Status |
|-----------|-------------|--------|
| 1.1.1 | Root + module POMs | ✅ |
| 1.1.2 | `StorageHealthApplication.java` + `application.yml` | ✅ |
| 1.2.1 | `V1__initial_schema.sql` + `DatabaseInitializer` | ✅ |
| 1.2.2 | All 7 JPA entity classes + 5 enums | ✅ |
| 1.2.3 | All 6 repository interfaces | ✅ |
| 1.3.1 | `FileScanner` interface + `FileScannerImpl` | ✅ |
| 1.3.2 | `HashingService` interface + `HashingServiceImpl` | ✅ |
| 1.4.1 | `DuplicateDetector` interface + `DuplicateDetectorImpl` | ✅ |
| 1.5.1 | `ScanController` + `DuplicateController` + 6 DTOs | ✅ |
| Tests | 20 unit tests across 3 test classes | ✅ |

**Total files created: 42**
