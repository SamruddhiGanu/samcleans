# Storage Health Ranker

> A fully local, AI-powered desktop storage analysis tool built with **Java 21**, **Spring Boot 3.2**, **JavaFX 21**, and **SQLite**.

---

## Project Structure

```
storage-health-ranker/
├── pom.xml                    ← Root Maven multi-module POM
├── backend/                   ← Spring Boot REST + JPA + SQLite
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/storagehealth/
│       │   │   ├── StorageHealthApplication.java
│       │   │   ├── config/
│       │   │   │   └── DatabaseInitializer.java
│       │   │   ├── domain/entity/         ← JPA entities + enums
│       │   │   ├── infrastructure/repository/  ← Spring Data repositories
│       │   │   ├── application/service/
│       │   │   │   ├── scanner/           ← File-system scanning
│       │   │   │   ├── hashing/           ← SHA-256 + dPhash (stub)
│       │   │   │   └── duplicate/         ← Duplicate detection engine
│       │   │   └── presentation/api/      ← REST controllers + DTOs
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/V1__initial_schema.sql
│       └── test/java/                     ← JUnit 5 + Mockito tests
├── desktop-ui/                ← JavaFX UI (Phase 2)
│   └── pom.xml
├── docs/
└── README.md
```

---

## Prerequisites

| Tool        | Version  |
|-------------|----------|
| Java (JDK)  | 21+      |
| Maven       | 3.9+     |

---

## Quick Start

```bash
# 1. Clone / open the project
cd storage-health-ranker

# 2. Build all modules
mvn clean install -DskipTests

# 3. Run the backend
cd backend
mvn spring-boot:run
```

The first startup will:
1. Create `~/.storage-health/database.db` (SQLite)
2. Create `~/.storage-health/logs/app.log`
3. Run the V1 schema migration automatically

---

## REST API (Phase 1)

### Scan Endpoints

| Method | Path                          | Description                        |
|--------|-------------------------------|------------------------------------|
| POST   | `/api/scan/start`             | Start a new scan (async)           |
| GET    | `/api/scan/progress/{id}`     | Poll scan progress                 |
| POST   | `/api/scan/cancel/{id}`       | Request scan cancellation          |
| GET    | `/api/scan/list`              | Paginated list of all sessions     |
| GET    | `/api/scan/{id}`              | Get single session by ID           |

**Start scan request body:**
```json
{ "path": "C:\\Users\\YourName\\Documents", "name": "My first scan" }
```

### Duplicate Endpoints

| Method | Path                                | Description                        |
|--------|-------------------------------------|------------------------------------|
| POST   | `/api/duplicates/detect/{sessionId}`| Run duplicate detection            |
| GET    | `/api/duplicates/recommendations`   | List duplicate recommendations     |

---

## Running Tests

```bash
# From the project root
mvn test

# Or for the backend only
cd backend && mvn test
```

---

## Implementation Phases

| Phase | Focus                          | Status      |
|-------|--------------------------------|-------------|
| 1     | Foundation & Core Scanning     | ✅ Complete |
| 2     | Ranking Engine & Dashboard     | 🔲 Pending  |
| 3     | AI & Image Analysis            | 🔲 Pending  |
| 4     | Cleanup & Safety               | 🔲 Pending  |
