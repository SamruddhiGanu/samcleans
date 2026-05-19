# Storage Health Ranker - Implementation Plan

## Executive Overview
A fully local, AI-powered desktop storage analysis tool built with Java 21, Spring Boot, JavaFX, and SQLite. This plan breaks the project into 4 phases over an estimated 8-12 weeks with clear deliverables and checkpoints.

---

## Phase 1: Foundation & Core Scanning (Weeks 1-3)

### Objective
Establish project infrastructure, basic file scanning, and duplicate detection.

### 1.1 Project Setup & Infrastructure

#### Step 1.1.1: Initialize Maven Project Structure
```
storage-health-ranker/
├── pom.xml (root)
├── backend/
│   ├── pom.xml
│   ├── src/main/java/com/storagehealth/
│   │   ├── config/
│   │   ├── domain/
│   │   ├── infrastructure/
│   │   ├── application/
│   │   └── StorageHealthApplication.java
│   └── src/test/java/
├── desktop-ui/
│   ├── pom.xml
│   └── src/main/java/com/storagehealth/ui/
├── docs/
└── README.md
```

**Deliverables:**
- [ ] Root `pom.xml` with module structure
- [ ] Backend `pom.xml` with dependencies
- [ ] Desktop-ui `pom.xml` with JavaFX dependencies
- [ ] Git repository initialized

**Dependencies to Add:**
```xml
<!-- Core -->
<spring-boot-version>3.2.x</spring-boot-version>
<java.version>21</java.version>

<!-- Storage & Hashing -->
<sqlite-jdbc>3.45.x</sqlite-jdbc>
<hikaricp>5.1.x</hikaricp>
<apache-commons-codec>1.6.x</apache-commons-codec>

<!-- JavaFX -->
<javafx-version>21.x</javafx-version>

<!-- Database -->
<spring-data-jpa>3.2.x</spring-data-jpa>
<hibernate>6.4.x</hibernate>

<!-- Utilities -->
<lombok>1.18.x</lombok>
<log4j>2.22.x</log4j>

<!-- Testing -->
<junit-jupiter>5.10.x</junit-jupiter>
<mockito>5.7.x</mockito>
```

**Checkpoint:** `mvn clean install` builds successfully, all modules are recognized.

---

#### Step 1.1.2: Configure Spring Boot Application
Create `StorageHealthApplication.java`:
```java
@SpringBootApplication
public class StorageHealthApplication {
    public static void main(String[] args) {
        SpringApplication.run(StorageHealthApplication.class, args);
    }
}
```

Create `application.yml`:
```yaml
spring:
  application:
    name: storage-health-ranker
  datasource:
    url: jdbc:sqlite:${user.home}/.storage-health/database.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.community.dialect.SQLiteDialect
        jdbc:
          batch_size: 100
        order_inserts: true
        order_updates: true

logging:
  level:
    com.storagehealth: DEBUG
    org.springframework: WARN
  file:
    name: ${user.home}/.storage-health/logs/app.log
    max-size: 10MB
    max-history: 10
```

**Deliverables:**
- [ ] Spring Boot application starts without errors
- [ ] SQLite database is created in `~/.storage-health/`
- [ ] Logging is configured

**Checkpoint:** `StorageHealthApplication` runs and creates database file.

---

### 1.2 Database Schema & JPA Entities

#### Step 1.2.1: Design SQLite Schema
Create migration script: `src/main/resources/db/migration/V1__initial_schema.sql`

```sql
-- Files table
CREATE TABLE IF NOT EXISTS files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    extension TEXT,
    mime_type TEXT,
    size_bytes LONG NOT NULL,
    file_type VARCHAR(50),
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    accessed_at TIMESTAMP,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    scan_session_id INTEGER,
    FOREIGN KEY (scan_session_id) REFERENCES scan_sessions(id)
);

-- File hashes table
CREATE TABLE IF NOT EXISTS file_hashes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER NOT NULL,
    hash_type VARCHAR(20) NOT NULL, -- SHA256, DPHASH, PHASH
    hash_value TEXT NOT NULL,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (file_id) REFERENCES files(id),
    UNIQUE(file_id, hash_type)
);

-- Image embeddings table
CREATE TABLE IF NOT EXISTS image_embeddings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER NOT NULL,
    embedding BLOB,
    model_version VARCHAR(50),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (file_id) REFERENCES files(id)
);

-- Scan sessions table
CREATE TABLE IF NOT EXISTS scan_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_name TEXT,
    scan_path TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_files INTEGER,
    scanned_files INTEGER,
    total_size LONG,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Recommendations table
CREATE TABLE IF NOT EXISTS recommendations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER,
    recommendation_type VARCHAR(50) NOT NULL,
    confidence_score DECIMAL(3,2),
    explanation TEXT,
    recoverable_space LONG,
    is_acted_on BOOLEAN DEFAULT FALSE,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (file_id) REFERENCES files(id)
);

-- Cleanup sessions table
CREATE TABLE IF NOT EXISTS cleanup_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR(36) UNIQUE NOT NULL,
    creation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    files_count INTEGER,
    total_size LONG,
    status VARCHAR(20) NOT NULL
);

-- Cleanup session files table
CREATE TABLE IF NOT EXISTS cleanup_session_files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cleanup_session_id INTEGER NOT NULL,
    file_id INTEGER NOT NULL,
    original_path TEXT NOT NULL,
    archived_path TEXT,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (cleanup_session_id) REFERENCES cleanup_sessions(id),
    FOREIGN KEY (file_id) REFERENCES files(id)
);

-- User feedback table
CREATE TABLE IF NOT EXISTS user_feedback (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER,
    feedback_type VARCHAR(50), -- keep, delete, important
    importance_score INTEGER,
    user_notes TEXT,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (file_id) REFERENCES files(id)
);

-- Indexes for performance
CREATE INDEX idx_files_path ON files(path);
CREATE INDEX idx_files_extension ON files(extension);
CREATE INDEX idx_files_size ON files(size_bytes);
CREATE INDEX idx_file_hashes_hash_value ON file_hashes(hash_value);
CREATE INDEX idx_recommendations_type ON recommendations(recommendation_type);
CREATE INDEX idx_cleanup_session_status ON cleanup_sessions(status);
```

**Deliverables:**
- [ ] Schema SQL file created
- [ ] All tables with proper indexes
- [ ] Foreign key relationships defined

**Checkpoint:** Database schema validated; no conflicts or ambiguities.

---

#### Step 1.2.2: Create JPA Entity Classes
Create package: `com.storagehealth.domain.entity`

**FileEntity.java:**
```java
@Entity
@Table(name = "files", indexes = {
    @Index(name = "idx_path", columnList = "path"),
    @Index(name = "idx_extension", columnList = "extension"),
    @Index(name = "idx_size", columnList = "size_bytes")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String path;
    
    @Column(nullable = false)
    private String name;
    
    private String extension;
    private String mimeType;
    
    @Column(nullable = false)
    private Long sizeBytes;
    
    @Enumerated(EnumType.STRING)
    private FileType fileType;
    
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private LocalDateTime accessedAt;
    
    @CreationTimestamp
    private LocalDateTime createdDate;
    
    @UpdateTimestamp
    private LocalDateTime updatedDate;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_session_id")
    private ScanSessionEntity scanSession;
    
    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<FileHashEntity> hashes = new HashSet<>();
    
    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL)
    private Set<RecommendationEntity> recommendations = new HashSet<>();
}

enum FileType {
    IMAGE, VIDEO, DOCUMENT, ARCHIVE, EXECUTABLE, MEDIA, TEMPORARY, OTHER
}
```

**FileHashEntity.java:**
```java
@Entity
@Table(name = "file_hashes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileHashEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HashType hashType;
    
    @Column(nullable = false)
    private String hashValue;
    
    @CreationTimestamp
    private LocalDateTime createdDate;
}

enum HashType {
    SHA256, DPHASH, PHASH
}
```

**ScanSessionEntity.java:**
```java
@Entity
@Table(name = "scan_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String sessionName;
    
    @Column(nullable = false)
    private String scanPath;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStatus status;
    
    private Integer totalFiles;
    private Integer scannedFiles;
    private Long totalSize;
    
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    @CreationTimestamp
    private LocalDateTime createdDate;
    
    @OneToMany(mappedBy = "scanSession", cascade = CascadeType.ALL)
    private Set<FileEntity> files = new HashSet<>();
}

enum ScanStatus {
    INITIATED, IN_PROGRESS, COMPLETED, FAILED, PAUSED
}
```

**RecommendationEntity.java:**
```java
@Entity
@Table(name = "recommendations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private FileEntity file;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecommendationType type;
    
    @Column(columnDefinition = "DECIMAL(3,2)")
    private BigDecimal confidenceScore;
    
    private String explanation;
    private Long recoverableSpace;
    private Boolean isActedOn = false;
    
    @CreationTimestamp
    private LocalDateTime createdDate;
}

enum RecommendationType {
    DUPLICATE, NEAR_DUPLICATE, BLURRY_IMAGE, OLD_SCREENSHOT, 
    TEMP_FILE, UNUSED_LARGE_FILE, EMPTY_FOLDER, STALE_DOWNLOAD
}
```

**CleanupSessionEntity.java:**
```java
@Entity
@Table(name = "cleanup_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String sessionId;
    
    @CreationTimestamp
    private LocalDateTime creationTime;
    
    private Integer filesCount;
    private Long totalSize;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CleanupStatus status;
    
    @OneToMany(mappedBy = "cleanupSession", cascade = CascadeType.ALL)
    private Set<CleanupSessionFileEntity> files = new HashSet<>();
}

enum CleanupStatus {
    ACTIVE, COMPLETED, RESTORED, ARCHIVED
}
```

**Deliverables:**
- [ ] All entity classes created with proper annotations
- [ ] Enums defined for all status/type fields
- [ ] Relationships (1:N, N:M) properly mapped
- [ ] Lombok annotations reduce boilerplate

**Checkpoint:** `mvn clean compile` builds all entities without errors.

---

#### Step 1.2.3: Create JPA Repositories
Create package: `com.storagehealth.infrastructure.repository`

```java
@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long> {
    Optional<FileEntity> findByPath(String path);
    List<FileEntity> findByExtension(String extension);
    List<FileEntity> findBySizeBytesGreaterThan(Long size);
    List<FileEntity> findByScanSession(ScanSessionEntity scanSession);
    Page<FileEntity> findAll(Pageable pageable);
    long countByScanSession(ScanSessionEntity session);
}

@Repository
public interface FileHashRepository extends JpaRepository<FileHashEntity, Long> {
    List<FileHashEntity> findByHashValueAndHashType(String hashValue, HashType hashType);
    List<FileHashEntity> findByFile(FileEntity file);
    Optional<FileHashEntity> findByFileAndHashType(FileEntity file, HashType hashType);
}

@Repository
public interface ScanSessionRepository extends JpaRepository<ScanSessionEntity, Long> {
    Optional<ScanSessionEntity> findLatestByOrderByCreatedDateDesc();
    List<ScanSessionEntity> findByStatus(ScanStatus status);
}

@Repository
public interface RecommendationRepository extends JpaRepository<RecommendationEntity, Long> {
    List<RecommendationEntity> findByType(RecommendationType type);
    List<RecommendationEntity> findByFile(FileEntity file);
    List<RecommendationEntity> findByIsActedOnFalse();
    Page<RecommendationEntity> findByIsActedOnFalse(Pageable pageable);
}

@Repository
public interface CleanupSessionRepository extends JpaRepository<CleanupSessionEntity, Long> {
    Optional<CleanupSessionEntity> findBySessionId(String sessionId);
    List<CleanupSessionEntity> findByStatus(CleanupStatus status);
}
```

**Deliverables:**
- [ ] All repository interfaces created
- [ ] Query methods defined for common operations
- [ ] Pagination support added

**Checkpoint:** All repositories compile and autowire successfully in tests.

---

### 1.3 File Scanner Module

#### Step 1.3.1: Create File Scanner Service
Create package: `com.storagehealth.application.service.scanner`

**FileScanner.java (Interface):**
```java
public interface FileScanner {
    void scanDirectory(String directoryPath, ScanSessionEntity session) throws IOException;
    void cancelScan();
    ScanProgress getProgress();
}

public class ScanProgress {
    private int totalFilesFound;
    private int filesProcessed;
    private long totalSize;
    private long currentSpeed; // files/second
    private LocalDateTime startTime;
    private LocalDateTime estimatedEndTime;
}
```

**FileScannerImpl.java:**
```java
@Service
@Slf4j
public class FileScannerImpl implements FileScanner {
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final Set<String> EXCLUDED_DIRS = Set.of(
        "$RECYCLE.BIN", "System Volume Information", ".git", "__pycache__",
        "node_modules", ".DS_Store", "Thumbs.db"
    );
    private static final Set<String> SYSTEM_PATHS = Set.of(
        "C:\\Windows", "C:\\Program Files", "C:\\ProgramData",
        "/System", "/Library", "/usr", "/etc", "/var"
    );
    
    private final FileRepository fileRepository;
    private final FileHashRepository hashRepository;
    private final ScanSessionRepository sessionRepository;
    private final ExecutorService executorService;
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final ScanProgress progress = new ScanProgress();
    
    @Autowired
    public FileScannerImpl(
        FileRepository fileRepository,
        FileHashRepository hashRepository,
        ScanSessionRepository sessionRepository
    ) {
        this.fileRepository = fileRepository;
        this.hashRepository = hashRepository;
        this.sessionRepository = sessionRepository;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }
    
    @Override
    @Transactional
    public void scanDirectory(String directoryPath, ScanSessionEntity session) throws IOException {
        log.info("Starting scan of directory: {}", directoryPath);
        
        session.setStatus(ScanStatus.IN_PROGRESS);
        session.setStartTime(LocalDateTime.now());
        sessionRepository.save(session);
        
        progress.setStartTime(LocalDateTime.now());
        cancelRequested.set(false);
        
        try {
            Path rootPath = Paths.get(directoryPath);
            List<Future<?>> futures = new ArrayList<>();
            
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (cancelRequested.get()) {
                        return FileVisitResult.TERMINATE;
                    }
                    
                    if (isExcludedDirectory(dir)) {
                        log.debug("Skipping excluded directory: {}", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (cancelRequested.get()) {
                        return FileVisitResult.TERMINATE;
                    }
                    
                    if (attrs.isRegularFile()) {
                        Future<?> future = executorService.submit(() -> {
                            try {
                                processFile(file, attrs, session);
                            } catch (Exception e) {
                                log.warn("Error processing file: {}", file, e);
                            }
                        });
                        futures.add(future);
                    }
                    
                    return FileVisitResult.CONTINUE;
                }
            });
            
            // Wait for all file processing to complete
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    log.warn("Task execution failed", e);
                }
            }
            
            session.setStatus(ScanStatus.COMPLETED);
            session.setEndTime(LocalDateTime.now());
            session.setScannedFiles(progress.totalFilesFound);
            session.setTotalSize(progress.totalSize);
            sessionRepository.save(session);
            
            log.info("Scan completed. Files: {}, Size: {} bytes", 
                progress.totalFilesFound, progress.totalSize);
            
        } catch (Exception e) {
            session.setStatus(ScanStatus.FAILED);
            sessionRepository.save(session);
            log.error("Scan failed", e);
            throw e;
        }
    }
    
    private void processFile(Path filePath, BasicFileAttributes attrs, ScanSessionEntity session) {
        try {
            // Check if file already exists in database
            Optional<FileEntity> existingFile = fileRepository.findByPath(filePath.toString());
            if (existingFile.isPresent()) {
                log.debug("File already indexed: {}", filePath);
                return;
            }
            
            FileEntity file = FileEntity.builder()
                .path(filePath.toString())
                .name(filePath.getFileName().toString())
                .extension(getExtension(filePath))
                .mimeType(getMimeType(filePath))
                .sizeBytes(attrs.size())
                .fileType(determineFileType(filePath))
                .createdAt(LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault()))
                .modifiedAt(LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()))
                .accessedAt(LocalDateTime.ofInstant(attrs.lastAccessTime().toInstant(), ZoneId.systemDefault()))
                .scanSession(session)
                .build();
            
            fileRepository.save(file);
            
            synchronized (progress) {
                progress.totalFilesFound++;
                progress.totalSize += attrs.size();
                updateProgressEstimate();
            }
            
        } catch (Exception e) {
            log.warn("Failed to process file: {}", filePath, e);
        }
    }
    
    private String getExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase() : "";
    }
    
    private String getMimeType(Path path) {
        try {
            return Files.probeContentType(path);
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
    
    private FileType determineFileType(Path path) {
        String extension = getExtension(path).toLowerCase();
        
        if (isImageExtension(extension)) return FileType.IMAGE;
        if (isVideoExtension(extension)) return FileType.VIDEO;
        if (isDocumentExtension(extension)) return FileType.DOCUMENT;
        if (isArchiveExtension(extension)) return FileType.ARCHIVE;
        if (isExecutableExtension(extension)) return FileType.EXECUTABLE;
        if (isMediaExtension(extension)) return FileType.MEDIA;
        if (isTemporaryExtension(extension)) return FileType.TEMPORARY;
        
        return FileType.OTHER;
    }
    
    private boolean isImageExtension(String ext) {
        return Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico").contains(ext);
    }
    
    private boolean isVideoExtension(String ext) {
        return Set.of("mp4", "mkv", "avi", "mov", "flv", "wmv", "webm").contains(ext);
    }
    
    private boolean isDocumentExtension(String ext) {
        return Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv").contains(ext);
    }
    
    private boolean isArchiveExtension(String ext) {
        return Set.of("zip", "rar", "7z", "tar", "gz", "bz2").contains(ext);
    }
    
    private boolean isExecutableExtension(String ext) {
        return Set.of("exe", "msi", "bat", "sh", "app", "deb", "rpm").contains(ext);
    }
    
    private boolean isMediaExtension(String ext) {
        return Set.of("mp3", "wav", "flac", "aac", "m4a", "wma").contains(ext);
    }
    
    private boolean isTemporaryExtension(String ext) {
        return Set.of("tmp", "temp", "cache", "log", "bak").contains(ext);
    }
    
    private boolean isExcludedDirectory(Path path) {
        String pathStr = path.toString().toLowerCase();
        String fileName = path.getFileName().toString().toLowerCase();
        
        if (EXCLUDED_DIRS.stream().anyMatch(fileName::equalsIgnoreCase)) {
            return true;
        }
        
        return SYSTEM_PATHS.stream().anyMatch(pathStr::startsWith);
    }
    
    private synchronized void updateProgressEstimate() {
        long elapsedSeconds = Duration.between(progress.startTime, LocalDateTime.now()).getSeconds();
        if (elapsedSeconds > 0) {
            progress.currentSpeed = progress.totalFilesFound / elapsedSeconds;
            if (progress.currentSpeed > 0) {
                long remainingTime = (progress.totalFilesFound / progress.currentSpeed) * 1000;
                progress.estimatedEndTime = LocalDateTime.now().plus(Duration.ofMillis(remainingTime));
            }
        }
    }
    
    @Override
    public void cancelScan() {
        log.info("Scan cancellation requested");
        cancelRequested.set(true);
    }
    
    @Override
    public ScanProgress getProgress() {
        return progress;
    }
    
    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }
}
```

**Deliverables:**
- [ ] FileScanner interface and implementation
- [ ] Multi-threaded file walking
- [ ] Progress tracking
- [ ] File type detection
- [ ] Exclusion logic for system folders

**Checkpoint:** Scanner successfully walks directory and logs files to database.

---

#### Step 1.3.2: Create File Hashing Service
Create package: `com.storagehealth.application.service.hashing`

**HashingService.java:**
```java
public interface HashingService {
    String computeHash(Path filePath, HashType hashType) throws IOException;
    String sha256Hash(Path filePath) throws IOException;
    String dPhash(Path filePath) throws IOException;
}

@Service
@Slf4j
public class HashingServiceImpl implements HashingService {
    private static final int BUFFER_SIZE = 8192;
    private final FileHashRepository hashRepository;
    
    @Autowired
    public HashingServiceImpl(FileHashRepository hashRepository) {
        this.hashRepository = hashRepository;
    }
    
    @Override
    public String computeHash(Path filePath, HashType hashType) throws IOException {
        return switch (hashType) {
            case SHA256 -> sha256Hash(filePath);
            case DPHASH -> dPhash(filePath);
            case PHASH -> ""; // TODO: Implement in Phase 3
        };
    }
    
    @Override
    public String sha256Hash(Path filePath) throws IOException {
        try (InputStream fis = Files.newInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public String dPhash(Path filePath) throws IOException {
        // Difference Hash - requires image processing
        // To be implemented in Phase 3 with OpenCV
        throw new UnsupportedOperationException("dPhash requires image processing libraries");
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
```

**Deliverables:**
- [ ] HashingService interface and implementation
- [ ] SHA-256 hashing for files
- [ ] dPhash stub for Phase 3
- [ ] Efficient buffered reading

**Checkpoint:** Hashing service computes correct SHA-256 hashes.

---

### 1.4 Duplicate Detection Engine

#### Step 1.4.1: Create Duplicate Detection Service
Create package: `com.storagehealth.application.service.duplicate`

**DuplicateDetector.java:**
```java
public interface DuplicateDetector {
    List<DuplicateGroup> findExactDuplicates(ScanSessionEntity session);
    void markDuplicates(List<DuplicateGroup> groups);
}

@Data
@AllArgsConstructor
public class DuplicateGroup {
    private String hashValue;
    private List<FileEntity> files;
    private Long totalSize;
    private Long recoverableSpace; // (count - 1) * size
    
    public void calculate() {
        if (!files.isEmpty()) {
            totalSize = files.stream().mapToLong(FileEntity::getSizeBytes).sum();
            recoverableSpace = (long) (files.size() - 1) * files.get(0).getSizeBytes();
        }
    }
}

@Service
@Slf4j
@Transactional
public class DuplicateDetectorImpl implements DuplicateDetector {
    private final FileRepository fileRepository;
    private final FileHashRepository hashRepository;
    private final HashingService hashingService;
    private final RecommendationRepository recommendationRepository;
    
    @Autowired
    public DuplicateDetectorImpl(
        FileRepository fileRepository,
        FileHashRepository hashRepository,
        HashingService hashingService,
        RecommendationRepository recommendationRepository
    ) {
        this.fileRepository = fileRepository;
        this.hashRepository = hashRepository;
        this.hashingService = hashingService;
        this.recommendationRepository = recommendationRepository;
    }
    
    @Override
    public List<DuplicateGroup> findExactDuplicates(ScanSessionEntity session) {
        log.info("Starting duplicate detection for session: {}", session.getId());
        
        List<FileEntity> files = fileRepository.findByScanSession(session);
        Map<Long, List<FileEntity>> sizeGroups = groupBySize(files);
        
        List<DuplicateGroup> duplicates = new ArrayList<>();
        
        for (Map.Entry<Long, List<FileEntity>> entry : sizeGroups.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue; // No duplicates possible
            }
            
            Map<String, List<FileEntity>> hashGroups = computeHashesAndGroup(entry.getValue());
            
            for (Map.Entry<String, List<FileEntity>> hashEntry : hashGroups.entrySet()) {
                if (hashEntry.getValue().size() > 1) {
                    DuplicateGroup group = new DuplicateGroup(
                        hashEntry.getKey(),
                        hashEntry.getValue(),
                        0L,
                        0L
                    );
                    group.calculate();
                    duplicates.add(group);
                    
                    log.info("Found {} duplicate files with hash: {} (recoverable: {} bytes)",
                        group.getFiles().size(),
                        hashEntry.getKey().substring(0, 8),
                        group.getRecoverableSpace()
                    );
                }
            }
        }
        
        return duplicates;
    }
    
    private Map<Long, List<FileEntity>> groupBySize(List<FileEntity> files) {
        return files.stream()
            .collect(Collectors.groupingBy(FileEntity::getSizeBytes));
    }
    
    private Map<String, List<FileEntity>> computeHashesAndGroup(List<FileEntity> files) {
        Map<String, List<FileEntity>> hashGroups = new HashMap<>();
        
        for (FileEntity file : files) {
            try {
                Path filePath = Paths.get(file.getPath());
                String hash = hashingService.sha256Hash(filePath);
                
                // Store hash in database
                FileHashEntity hashEntity = FileHashEntity.builder()
                    .file(file)
                    .hashType(HashType.SHA256)
                    .hashValue(hash)
                    .build();
                hashRepository.save(hashEntity);
                
                hashGroups.computeIfAbsent(hash, k -> new ArrayList<>()).add(file);
                
            } catch (IOException e) {
                log.warn("Failed to hash file: {}", file.getPath(), e);
            }
        }
        
        return hashGroups;
    }
    
    @Override
    public void markDuplicates(List<DuplicateGroup> groups) {
        for (DuplicateGroup group : groups) {
            for (int i = 1; i < group.getFiles().size(); i++) {
                FileEntity duplicate = group.getFiles().get(i);
                
                RecommendationEntity recommendation = RecommendationEntity.builder()
                    .file(duplicate)
                    .type(RecommendationType.DUPLICATE)
                    .confidenceScore(BigDecimal.valueOf(1.0))
                    .explanation("Exact duplicate of: " + group.getFiles().get(0).getPath())
                    .recoverableSpace(duplicate.getSizeBytes())
                    .build();
                
                recommendationRepository.save(recommendation);
            }
        }
    }
}
```

**Deliverables:**
- [ ] DuplicateDetector interface and implementation
- [ ] Size-based grouping for efficiency
- [ ] Hash computation and grouping
- [ ] Recommendations created for duplicates

**Checkpoint:** Successfully identifies and logs exact duplicates.

---

### 1.5 Create REST API Controller

#### Step 1.5.1: Create Scan Controller
Create package: `com.storagehealth.presentation.api`

**ScanController.java:**
```java
@RestController
@RequestMapping("/api/scan")
@Slf4j
public class ScanController {
    private final FileScanner fileScanner;
    private final ScanSessionRepository scanSessionRepository;
    private final FileRepository fileRepository;
    
    @Autowired
    public ScanController(
        FileScanner fileScanner,
        ScanSessionRepository scanSessionRepository,
        FileRepository fileRepository
    ) {
        this.fileScanner = fileScanner;
        this.scanSessionRepository = scanSessionRepository;
        this.fileRepository = fileRepository;
    }
    
    @PostMapping("/start")
    public ResponseEntity<ScanSessionDTO> startScan(@RequestBody ScanRequest request) {
        log.info("Starting new scan for path: {}", request.getPath());
        
        ScanSessionEntity session = ScanSessionEntity.builder()
            .sessionName(request.getName() != null ? request.getName() : "Scan " + LocalDateTime.now())
            .scanPath(request.getPath())
            .status(ScanStatus.INITIATED)
            .build();
        
        ScanSessionEntity saved = scanSessionRepository.save(session);
        
        // Run scan async
        new Thread(() -> {
            try {
                fileScanner.scanDirectory(request.getPath(), saved);
            } catch (IOException e) {
                log.error("Scan failed", e);
            }
        }).start();
        
        return ResponseEntity.ok(mapToDTO(saved));
    }
    
    @GetMapping("/progress/{sessionId}")
    public ResponseEntity<ScanProgressDTO> getProgress(@PathVariable Long sessionId) {
        Optional<ScanSessionEntity> session = scanSessionRepository.findById(sessionId);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ScanProgress progress = fileScanner.getProgress();
        return ResponseEntity.ok(new ScanProgressDTO(
            progress.getTotalFilesFound(),
            progress.getFilesProcessed(),
            progress.getTotalSize(),
            progress.getCurrentSpeed(),
            progress.getEstimatedEndTime()
        ));
    }
    
    @PostMapping("/cancel/{sessionId}")
    public ResponseEntity<Void> cancelScan(@PathVariable Long sessionId) {
        fileScanner.cancelScan();
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/list")
    public ResponseEntity<Page<ScanSessionDTO>> listScans(Pageable pageable) {
        return ResponseEntity.ok(
            scanSessionRepository.findAll(pageable)
                .map(this::mapToDTO)
        );
    }
    
    private ScanSessionDTO mapToDTO(ScanSessionEntity entity) {
        return ScanSessionDTO.builder()
            .id(entity.getId())
            .sessionName(entity.getSessionName())
            .scanPath(entity.getScanPath())
            .status(entity.getStatus().name())
            .totalFiles(entity.getTotalFiles())
            .totalSize(entity.getTotalSize())
            .startTime(entity.getStartTime())
            .endTime(entity.getEndTime())
            .build();
    }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
class ScanSessionDTO {
    private Long id;
    private String sessionName;
    private String scanPath;
    private String status;
    private Integer totalFiles;
    private Long totalSize;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class ScanRequest {
    private String path;
    private String name;
}

@Data
@AllArgsConstructor
class ScanProgressDTO {
    private int totalFilesFound;
    private int filesProcessed;
    private long totalSize;
    private long currentSpeed;
    private LocalDateTime estimatedEndTime;
}
```

**DuplicateController.java:**
```java
@RestController
@RequestMapping("/api/duplicates")
@Slf4j
public class DuplicateController {
    private final DuplicateDetector duplicateDetector;
    private final ScanSessionRepository scanSessionRepository;
    private final RecommendationRepository recommendationRepository;
    
    @Autowired
    public DuplicateController(
        DuplicateDetector duplicateDetector,
        ScanSessionRepository scanSessionRepository,
        RecommendationRepository recommendationRepository
    ) {
        this.duplicateDetector = duplicateDetector;
        this.scanSessionRepository = scanSessionRepository;
        this.recommendationRepository = recommendationRepository;
    }
    
    @PostMapping("/detect/{sessionId}")
    public ResponseEntity<DuplicateAnalysisDTO> detectDuplicates(@PathVariable Long sessionId) {
        Optional<ScanSessionEntity> session = scanSessionRepository.findById(sessionId);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        List<DuplicateGroup> duplicates = duplicateDetector.findExactDuplicates(session.get());
        duplicateDetector.markDuplicates(duplicates);
        
        Long totalRecoverable = duplicates.stream()
            .mapToLong(DuplicateGroup::getRecoverableSpace)
            .sum();
        
        return ResponseEntity.ok(new DuplicateAnalysisDTO(
            duplicates.size(),
            totalRecoverable,
            duplicates.stream()
                .map(g -> new DuplicateGroupDTO(
                    g.getHashValue(),
                    g.getFiles().size(),
                    g.getTotalSize(),
                    g.getRecoverableSpace()
                ))
                .collect(Collectors.toList())
        ));
    }
    
    @GetMapping("/recommendations")
    public ResponseEntity<Page<RecommendationDTO>> getDuplicateRecommendations(Pageable pageable) {
        return ResponseEntity.ok(
            recommendationRepository.findByType(RecommendationType.DUPLICATE, pageable)
                .map(this::mapToDTO)
        );
    }
    
    private RecommendationDTO mapToDTO(RecommendationEntity entity) {
        return RecommendationDTO.builder()
            .id(entity.getId())
            .fileId(entity.getFile().getId())
            .fileName(entity.getFile().getName())
            .filePath(entity.getFile().getPath())
            .type(entity.getType().name())
            .confidenceScore(entity.getConfidenceScore())
            .explanation(entity.getExplanation())
            .recoverableSpace(entity.getRecoverableSpace())
            .build();
    }
}

@Data
@AllArgsConstructor
@Builder
class DuplicateAnalysisDTO {
    private int groupCount;
    private long totalRecoverableSpace;
    private List<DuplicateGroupDTO> groups;
}

@Data
@AllArgsConstructor
class DuplicateGroupDTO {
    private String hashValue;
    private int fileCount;
    private long totalSize;
    private long recoverableSpace;
}

@Data
@AllArgsConstructor
@Builder
class RecommendationDTO {
    private Long id;
    private Long fileId;
    private String fileName;
    private String filePath;
    private String type;
    private BigDecimal confidenceScore;
    private String explanation;
    private Long recoverableSpace;
}
```

**Deliverables:**
- [ ] Scan API endpoints (start, progress, cancel, list)
- [ ] Duplicate detection endpoint
- [ ] Recommendation endpoints
- [ ] DTO classes for API responses

**Checkpoint:** All endpoints compile and respond to requests.

---

### Phase 1 Summary Checkpoint

**Verify:**
- [ ] Maven build completes without errors
- [ ] Spring Boot application starts
- [ ] SQLite database created and schema initialized
- [ ] File scanner runs on test directory
- [ ] Duplicate detection identifies exact duplicates
- [ ] All REST endpoints respond
- [ ] Unit tests for scanner, hashing, duplicate detection

**Deliverables:**
- [ ] Complete project structure
- [ ] Database schema and JPA entities
- [ ] File scanner with progress tracking
- [ ] Duplicate detection engine
- [ ] REST APIs for scanning

**Estimated Time:** 3 weeks
**Lines of Code:** ~3,500

---

## Phase 2: Ranking Engine & Dashboard (Weeks 4-6)

### Objective
Implement file importance ranking, recommendations, and basic JavaFX dashboard.

### 2.1 File Importance Ranking Engine

#### Step 2.1.1: Create Ranking Service
Create package: `com.storagehealth.application.service.ranking`

**FileRankingService.java:**
```java
public interface FileRankingService {
    double computeImportanceScore(FileEntity file);
    void rankFiles(ScanSessionEntity session);
    List<FileEntity> getFilesByImportance(ScanSessionEntity session, Pageable pageable);
}

@Service
@Slf4j
@Transactional
public class FileRankingServiceImpl implements FileRankingService {
    // Weights for ranking formula
    private static final double RECENCY_WEIGHT = 0.2;
    private static final double FREQUENCY_WEIGHT = 0.2;
    private static final double SEMANTIC_WEIGHT = 0.3;
    private static final double UNIQUENESS_WEIGHT = 0.2;
    private static final double USER_FEEDBACK_WEIGHT = 0.1;
    
    private final FileRepository fileRepository;
    private final UserFeedbackRepository feedbackRepository;
    
    @Autowired
    public FileRankingServiceImpl(
        FileRepository fileRepository,
        UserFeedbackRepository feedbackRepository
    ) {
        this.fileRepository = fileRepository;
        this.feedbackRepository = feedbackRepository;
    }
    
    @Override
    public double computeImportanceScore(FileEntity file) {
        double recency = calculateRecency(file);
        double frequency = calculateAccessFrequency(file);
        double semantic = calculateSemanticValue(file);
        double uniqueness = calculateUniqueness(file);
        double userFeedback = calculateUserFeedback(file);
        
        return (recency * RECENCY_WEIGHT) +
               (frequency * FREQUENCY_WEIGHT) +
               (semantic * SEMANTIC_WEIGHT) +
               (uniqueness * UNIQUENESS_WEIGHT) +
               (userFeedback * USER_FEEDBACK_WEIGHT);
    }
    
    private double calculateRecency(FileEntity file) {
        LocalDateTime modified = file.getModifiedAt();
        if (modified == null) {
            return 0.0;
        }
        
        long daysSinceModification = ChronoUnit.DAYS.between(modified, LocalDateTime.now());
        
        if (daysSinceModification < 7) return 1.0;      // Recent
        if (daysSinceModification < 30) return 0.8;     // 1 month
        if (daysSinceModification < 180) return 0.6;    // 6 months
        if (daysSinceModification < 365) return 0.4;    // 1 year
        return 0.2;  // Older
    }
    
    private double calculateAccessFrequency(FileEntity file) {
        // In a full implementation, this would track access patterns
        // For now, use modified date as proxy
        LocalDateTime accessed = file.getAccessedAt();
        if (accessed == null) {
            return 0.5; // Unknown
        }
        
        long daysSinceAccess = ChronoUnit.DAYS.between(accessed, LocalDateTime.now());
        
        if (daysSinceAccess < 7) return 1.0;
        if (daysSinceAccess < 30) return 0.8;
        if (daysSinceAccess < 180) return 0.6;
        return 0.2;
    }
    
    private double calculateSemanticValue(FileEntity file) {
        // Higher value for documents, photos, projects
        return switch (file.getFileType()) {
            case DOCUMENT -> 0.9;
            case IMAGE -> 0.8;
            case VIDEO -> 0.7;
            case ARCHIVE -> 0.6;
            case EXECUTABLE -> 0.5;
            case MEDIA -> 0.7;
            case TEMPORARY -> 0.1;
            case OTHER -> 0.4;
        };
    }
    
    private double calculateUniqueness(FileEntity file) {
        // Files that are duplicates have lower uniqueness
        long duplicateCount = fileRepository.countDuplicatesForFile(file.getId());
        
        if (duplicateCount > 1) {
            return 1.0 / duplicateCount;
        }
        
        return 1.0; // Unique file
    }
    
    private double calculateUserFeedback(FileEntity file) {
        Optional<UserFeedbackEntity> feedback = feedbackRepository.findByFile(file);
        
        if (feedback.isEmpty()) {
            return 0.5; // Neutral
        }
        
        UserFeedbackEntity fb = feedback.get();
        
        return switch (fb.getFeedbackType()) {
            case KEEP -> 1.0;
            case IMPORTANT -> 1.0;
            case DELETE -> 0.0;
            case NEUTRAL -> 0.5;
        };
    }
    
    @Override
    public void rankFiles(ScanSessionEntity session) {
        log.info("Ranking files for session: {}", session.getId());
        
        List<FileEntity> files = fileRepository.findByScanSession(session);
        
        for (FileEntity file : files) {
            double score = computeImportanceScore(file);
            file.setImportanceScore(score);
            fileRepository.save(file);
        }
        
        log.info("Ranked {} files", files.size());
    }
    
    @Override
    public List<FileEntity> getFilesByImportance(ScanSessionEntity session, Pageable pageable) {
        return fileRepository.findByScanSessionOrderByImportanceScoreDesc(session, pageable).getContent();
    }
}
```

**Deliverables:**
- [ ] FileRankingService interface and implementation
- [ ] Scoring formula with 5 components
- [ ] Recency, frequency, semantic, uniqueness calculations
- [ ] User feedback integration

**Checkpoint:** Files are ranked with scores between 0.0 and 1.0.

---

#### Step 2.1.2: Create Health Score Calculator
Create package: `com.storagehealth.application.service.health`

**HealthScoreCalculator.java:**
```java
public interface HealthScoreCalculator {
    StorageHealthScore calculateHealthScore(ScanSessionEntity session);
}

@Data
@AllArgsConstructor
@Builder
public class StorageHealthScore {
    private Double overallScore; // 0-100
    private Double duplicateWasteScore;
    private Double clutterScore;
    private Double organizationScore;
    private Long totalSize;
    private Long duplicateWaste;
    private Long clutteredSize;
    private Long temporaryFileSize;
    
    public String getHealthStatus() {
        if (overallScore >= 80) return "EXCELLENT";
        if (overallScore >= 60) return "GOOD";
        if (overallScore >= 40) return "FAIR";
        return "POOR";
    }
}

@Service
@Slf4j
public class HealthScoreCalculatorImpl implements HealthScoreCalculator {
    private final FileRepository fileRepository;
    private final RecommendationRepository recommendationRepository;
    
    @Autowired
    public HealthScoreCalculatorImpl(
        FileRepository fileRepository,
        RecommendationRepository recommendationRepository
    ) {
        this.fileRepository = fileRepository;
        this.recommendationRepository = recommendationRepository;
    }
    
    @Override
    public StorageHealthScore calculateHealthScore(ScanSessionEntity session) {
        log.info("Calculating health score for session: {}", session.getId());
        
        List<FileEntity> files = fileRepository.findByScanSession(session);
        long totalSize = files.stream().mapToLong(FileEntity::getSizeBytes).sum();
        
        // Calculate duplicate waste
        long duplicateWaste = calculateDuplicateWaste(files);
        
        // Calculate clutter (temporary files, old downloads)
        long clutteredSize = calculateClutteredSize(files);
        
        // Organization score (based on folder structure)
        double organizationScore = calculateOrganizationScore(files);
        
        // Compose health score
        double duplicateWasteScore = 100.0 * (1.0 - (double) duplicateWaste / totalSize);
        double clutterScore = 100.0 * (1.0 - (double) clutteredSize / totalSize);
        
        double overallScore = (duplicateWasteScore * 0.4) +
                             (clutterScore * 0.3) +
                             (organizationScore * 0.3);
        
        return StorageHealthScore.builder()
            .overallScore(Math.min(100.0, overallScore))
            .duplicateWasteScore(duplicateWasteScore)
            .clutterScore(clutterScore)
            .organizationScore(organizationScore)
            .totalSize(totalSize)
            .duplicateWaste(duplicateWaste)
            .clutteredSize(clutteredSize)
            .temporaryFileSize(calculateTemporaryFileSize(files))
            .build();
    }
    
    private long calculateDuplicateWaste(List<FileEntity> files) {
        List<RecommendationEntity> duplicates = recommendationRepository
            .findByType(RecommendationType.DUPLICATE);
        
        return duplicates.stream()
            .mapToLong(r -> r.getFile().getSizeBytes())
            .sum();
    }
    
    private long calculateClutteredSize(List<FileEntity> files) {
        return files.stream()
            .filter(f -> f.getFileType() == FileType.TEMPORARY)
            .mapToLong(FileEntity::getSizeBytes)
            .sum();
    }
    
    private long calculateTemporaryFileSize(List<FileEntity> files) {
        return calculateClutteredSize(files);
    }
    
    private double calculateOrganizationScore(List<FileEntity> files) {
        // Check if files are well-organized (not dumped in root)
        Map<String, Long> folderStructure = files.stream()
            .collect(Collectors.groupingBy(
                f -> Paths.get(f.getPath()).getParent().toString(),
                Collectors.counting()
            ));
        
        // If majority of files are in root, lower score
        long filesInRoot = folderStructure.values().stream()
            .filter(count -> count > 50)
            .count();
        
        return Math.min(100.0, 50.0 + (folderStructure.size() * 5.0));
    }
}
```

**Deliverables:**
- [ ] StorageHealthScore class
- [ ] Health score calculation logic
- [ ] Component scores (duplicate, clutter, organization)
- [ ] Health status mapping

**Checkpoint:** Health scores calculated and returned correctly.

---

### 2.2 Create Recommendation Engine

#### Step 2.2.1: Advanced Recommendations Service
Create package: `com.storagehealth.application.service.recommendations`

**RecommendationEngine.java:**
```java
public interface RecommendationEngine {
    void generateRecommendations(ScanSessionEntity session);
    List<RecommendationDTO> getRecommendations(RecommendationType type, Pageable pageable);
}

@Service
@Slf4j
@Transactional
public class RecommendationEngineImpl implements RecommendationEngine {
    private final FileRepository fileRepository;
    private final RecommendationRepository recommendationRepository;
    private final FileRankingService rankingService;
    
    @Autowired
    public RecommendationEngineImpl(
        FileRepository fileRepository,
        RecommendationRepository recommendationRepository,
        FileRankingService rankingService
    ) {
        this.fileRepository = fileRepository;
        this.recommendationRepository = recommendationRepository;
        this.rankingService = rankingService;
    }
    
    @Override
    public void generateRecommendations(ScanSessionEntity session) {
        log.info("Generating recommendations for session: {}", session.getId());
        
        List<FileEntity> files = fileRepository.findByScanSession(session);
        
        // Old screenshots
        recommendOldScreenshots(files);
        
        // Temporary/cache files
        recommendTemporaryFiles(files);
        
        // Unused large files
        recommendUnusedLargeFiles(files);
        
        // Empty folders
        recommendEmptyFolders(files);
        
        // Stale downloads
        recommendStaleDownloads(files);
        
        log.info("Generated recommendations for {} files", files.size());
    }
    
    private void recommendOldScreenshots(List<FileEntity> files) {
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        
        files.stream()
            .filter(f -> isScreenshot(f))
            .filter(f -> f.getModifiedAt() != null && f.getModifiedAt().isBefore(sixMonthsAgo))
            .forEach(f -> {
                RecommendationEntity rec = RecommendationEntity.builder()
                    .file(f)
                    .type(RecommendationType.OLD_SCREENSHOT)
                    .confidenceScore(BigDecimal.valueOf(0.85))
                    .explanation("Old screenshot from " + f.getModifiedAt() + ". Can be safely deleted.")
                    .recoverableSpace(f.getSizeBytes())
                    .build();
                
                recommendationRepository.save(rec);
            });
    }
    
    private void recommendTemporaryFiles(List<FileEntity> files) {
        files.stream()
            .filter(f -> f.getFileType() == FileType.TEMPORARY)
            .forEach(f -> {
                RecommendationEntity rec = RecommendationEntity.builder()
                    .file(f)
                    .type(RecommendationType.TEMP_FILE)
                    .confidenceScore(BigDecimal.valueOf(0.95))
                    .explanation("Temporary file. Safe to delete.")
                    .recoverableSpace(f.getSizeBytes())
                    .build();
                
                recommendationRepository.save(rec);
            });
    }
    
    private void recommendUnusedLargeFiles(List<FileEntity> files) {
        long largeFileThreshold = 500 * 1024 * 1024; // 500 MB
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        
        files.stream()
            .filter(f -> f.getSizeBytes() > largeFileThreshold)
            .filter(f -> f.getAccessedAt() != null && f.getAccessedAt().isBefore(oneYearAgo))
            .forEach(f -> {
                RecommendationEntity rec = RecommendationEntity.builder()
                    .file(f)
                    .type(RecommendationType.UNUSED_LARGE_FILE)
                    .confidenceScore(BigDecimal.valueOf(0.7))
                    .explanation("Large file not accessed for 1 year. Size: " + formatSize(f.getSizeBytes()))
                    .recoverableSpace(f.getSizeBytes())
                    .build();
                
                recommendationRepository.save(rec);
            });
    }
    
    private void recommendEmptyFolders(List<FileEntity> files) {
        // Extract all folder paths from file paths
        Set<Path> allFolders = files.stream()
            .map(f -> Paths.get(f.getPath()).getParent())
            .collect(Collectors.toSet());
        
        // Find empty folders (no files directly in them)
        allFolders.stream()
            .filter(folder -> folder != null)
            .filter(folder -> !hasFilesDirectlyInFolder(files, folder))
            .forEach(folder -> {
                // Create virtual file entity for folder recommendation
                FileEntity folderFile = FileEntity.builder()
                    .name(folder.getFileName().toString())
                    .path(folder.toString())
                    .sizeBytes(0L)
                    .fileType(FileType.OTHER)
                    .build();
                
                RecommendationEntity rec = RecommendationEntity.builder()
                    .file(folderFile)
                    .type(RecommendationType.EMPTY_FOLDER)
                    .confidenceScore(BigDecimal.valueOf(1.0))
                    .explanation("Empty folder. Can be safely deleted.")
                    .recoverableSpace(0L)
                    .build();
                
                recommendationRepository.save(rec);
            });
    }
    
    private void recommendStaleDownloads(List<FileEntity> files) {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        
        files.stream()
            .filter(f -> isInDownloadsFolder(f))
            .filter(f -> f.getAccessedAt() != null && f.getAccessedAt().isBefore(threeMonthsAgo))
            .forEach(f -> {
                RecommendationEntity rec = RecommendationEntity.builder()
                    .file(f)
                    .type(RecommendationType.STALE_DOWNLOAD)
                    .confidenceScore(BigDecimal.valueOf(0.75))
                    .explanation("Stale download not accessed for 3 months. Size: " + formatSize(f.getSizeBytes()))
                    .recoverableSpace(f.getSizeBytes())
                    .build();
                
                recommendationRepository.save(rec);
            });
    }
    
    private boolean isScreenshot(FileEntity file) {
        String name = file.getName().toLowerCase();
        return (name.contains("screenshot") || name.contains("screen shot")) &&
               file.getFileType() == FileType.IMAGE;
    }
    
    private boolean isInDownloadsFolder(FileEntity file) {
        String path = file.getPath().toLowerCase();
        return path.contains("downloads") || path.contains("download");
    }
    
    private boolean hasFilesDirectlyInFolder(List<FileEntity> files, Path folder) {
        return files.stream()
            .anyMatch(f -> Paths.get(f.getPath()).getParent().equals(folder));
    }
    
    private String formatSize(Long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    @Override
    public List<RecommendationDTO> getRecommendations(RecommendationType type, Pageable pageable) {
        return recommendationRepository.findByType(type, pageable).stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }
    
    private RecommendationDTO mapToDTO(RecommendationEntity entity) {
        return RecommendationDTO.builder()
            .id(entity.getId())
            .fileId(entity.getFile().getId())
            .fileName(entity.getFile().getName())
            .filePath(entity.getFile().getPath())
            .type(entity.getType().name())
            .confidenceScore(entity.getConfidenceScore())
            .explanation(entity.getExplanation())
            .recoverableSpace(entity.getRecoverableSpace())
            .build();
    }
}
```

**Deliverables:**
- [ ] RecommendationEngine interface and implementation
- [ ] Multiple recommendation types
- [ ] Confidence scores for each type
- [ ] Explanations for user clarity

**Checkpoint:** Recommendations generated for all file types.

---

### 2.3 JavaFX Desktop UI Foundation

#### Step 2.3.1: Create JavaFX Application Main Class
Create package: `com.storagehealth.ui`

**StorageHealthUIApplication.java:**
```java
public class StorageHealthUIApplication extends Application {
    private Stage primaryStage;
    private Scene scene;
    private StorageHealthMainWindow mainWindow;
    
    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        
        try {
            // Initialize main window
            mainWindow = new StorageHealthMainWindow();
            scene = new Scene(mainWindow.getRoot(), 1200, 800);
            
            // Apply CSS styling
            String css = getClass().getResource("/styles/dark-theme.css").toExternalForm();
            scene.getStylesheets().add(css);
            
            primaryStage.setTitle("Storage Health Ranker");
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(e -> handleApplicationClose());
            
            primaryStage.show();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleApplicationClose() {
        // Save state, cleanup resources
        System.exit(0);
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
```

**StorageHealthMainWindow.java:**
```java
@Component
public class StorageHealthMainWindow {
    private BorderPane root;
    private ScanDashboard scanDashboard;
    private DuplicateExplorer duplicateExplorer;
    private RecommendationsPanel recommendationsPanel;
    private SettingsPanel settingsPanel;
    private TabPane mainTabs;
    
    @Autowired
    public StorageHealthMainWindow(
        ScanDashboard scanDashboard,
        DuplicateExplorer duplicateExplorer,
        RecommendationsPanel recommendationsPanel,
        SettingsPanel settingsPanel
    ) {
        this.scanDashboard = scanDashboard;
        this.duplicateExplorer = duplicateExplorer;
        this.recommendationsPanel = recommendationsPanel;
        this.settingsPanel = settingsPanel;
        
        initializeUI();
    }
    
    private void initializeUI() {
        root = new BorderPane();
        
        // Top: Menu Bar
        MenuBar menuBar = createMenuBar();
        root.setTop(menuBar);
        
        // Center: Tab Pane
        mainTabs = new TabPane();
        mainTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Dashboard Tab
        Tab dashboardTab = new Tab("Dashboard", scanDashboard.getRoot());
        dashboardTab.setStyle("-fx-text-base-color: #ffffff;");
        
        // Duplicates Tab
        Tab duplicatesTab = new Tab("Duplicates", duplicateExplorer.getRoot());
        
        // Recommendations Tab
        Tab recommendationsTab = new Tab("Recommendations", recommendationsPanel.getRoot());
        
        // Settings Tab
        Tab settingsTab = new Tab("Settings", settingsPanel.getRoot());
        
        mainTabs.getTabs().addAll(dashboardTab, duplicatesTab, recommendationsTab, settingsTab);
        root.setCenter(mainTabs);
        
        // Bottom: Status Bar
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);
    }
    
    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        
        Menu fileMenu = new Menu("File");
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> System.exit(0));
        fileMenu.getItems().add(exitItem);
        
        Menu scanMenu = new Menu("Scan");
        MenuItem startScanItem = new MenuItem("Start New Scan");
        scanMenu.getItems().add(startScanItem);
        
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        helpMenu.getItems().add(aboutItem);
        
        menuBar.getMenus().addAll(fileMenu, scanMenu, helpMenu);
        return menuBar;
    }
    
    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-border-color: #333333; -fx-border-width: 1 0 0 0;");
        
        Label statusLabel = new Label("Ready");
        statusBar.getChildren().add(statusLabel);
        
        return statusBar;
    }
    
    public BorderPane getRoot() {
        return root;
    }
}
```

**Deliverables:**
- [ ] JavaFX Application structure
- [ ] Main window with tabs
- [ ] Menu bar
- [ ] Status bar
- [ ] Component integration

**Checkpoint:** JavaFX application launches with empty tabs.

---

#### Step 2.3.2: Create Dashboard UI Component
Create package: `com.storagehealth.ui.dashboard`

**ScanDashboard.java:**
```java
@Component
public class ScanDashboard {
    private VBox root;
    private HBox statsContainer;
    private LineChart<String, Number> storageChart;
    private ProgressBar scanProgress;
    private Label statusLabel;
    private Button startScanButton;
    
    private HealthScoreCalculator healthScoreCalculator;
    private final ScanService scanService;
    
    @Autowired
    public ScanDashboard(ScanService scanService, HealthScoreCalculator healthScoreCalculator) {
        this.scanService = scanService;
        this.healthScoreCalculator = healthScoreCalculator;
        initializeUI();
    }
    
    private void initializeUI() {
        root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #1a1a1a;");
        
        // Top: Control Panel
        HBox controlPanel = createControlPanel();
        root.getChildren().add(controlPanel);
        
        // Middle: Health Score Cards
        statsContainer = createHealthScoreCards();
        root.getChildren().add(statsContainer);
        
        // Bottom: Chart
        storageChart = createStorageChart();
        ScrollPane scrollPane = new ScrollPane(storageChart);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        root.getChildren().add(scrollPane);
    }
    
    private HBox createControlPanel() {
        HBox panel = new HBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-border-color: #333333; -fx-border-width: 1;");
        
        startScanButton = new Button("Start Scan");
        startScanButton.setStyle("-fx-font-size: 14; -fx-padding: 10;");
        startScanButton.setOnAction(e -> handleStartScan());
        
        Button pauseButton = new Button("Pause");
        pauseButton.setDisable(true);
        
        Button cancelButton = new Button("Cancel");
        cancelButton.setDisable(true);
        
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #00ff00;");
        
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        panel.getChildren().addAll(startScanButton, pauseButton, cancelButton, statusLabel);
        
        return panel;
    }
    
    private HBox createHealthScoreCards() {
        HBox cardsContainer = new HBox(15);
        cardsContainer.setPadding(new Insets(10));
        
        // Overall Score Card
        VBox overallCard = createScoreCard("Overall Health", "85", "#4CAF50");
        
        // Duplicate Waste Card
        VBox duplicateCard = createScoreCard("Duplicate Waste", "12%", "#FF9800");
        
        // Clutter Score Card
        VBox clutterCard = createScoreCard("Clutter", "8%", "#2196F3");
        
        // Organization Card
        VBox organizationCard = createScoreCard("Organization", "92%", "#9C27B0");
        
        cardsContainer.getChildren().addAll(overallCard, duplicateCard, clutterCard, organizationCard);
        
        return cardsContainer;
    }
    
    private VBox createScoreCard(String title, String value, String color) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-border-color: " + color + "; -fx-border-width: 2; -fx-background-color: #222222;");
        card.setPrefWidth(250);
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12;");
        
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 32; -fx-font-weight: bold;");
        
        card.getChildren().addAll(titleLabel, valueLabel);
        
        return card;
    }
    
    private LineChart<String, Number> createStorageChart() {
        final CategoryAxis xAxis = new CategoryAxis();
        final NumberAxis yAxis = new NumberAxis();
        
        xAxis.setLabel("File Type");
        yAxis.setLabel("Size (GB)");
        
        final LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Storage by File Type");
        lineChart.setStyle("-fx-background-color: #1a1a1a; -fx-text-fill: #ffffff;");
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Size");
        series.getData().addAll(
            new XYChart.Data<>("Images", 45),
            new XYChart.Data<>("Videos", 120),
            new XYChart.Data<>("Documents", 25),
            new XYChart.Data<>("Archives", 15),
            new XYChart.Data<>("Other", 30)
        );
        
        lineChart.getData().add(series);
        
        return lineChart;
    }
    
    private void handleStartScan() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Directory to Scan");
        
        File selectedDir = chooser.showDialog(null);
        if (selectedDir != null) {
            statusLabel.setText("Scanning: " + selectedDir.getAbsolutePath());
            statusLabel.setStyle("-fx-text-fill: #ffaa00;");
            
            // Start scan async
            new Thread(() -> {
                try {
                    // Call API to start scan
                    scanService.startScan(selectedDir.getAbsolutePath());
                } catch (Exception e) {
                    statusLabel.setText("Scan failed: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #ff0000;");
                }
            }).start();
        }
    }
    
    public VBox getRoot() {
        return root;
    }
}
```

**Deliverables:**
- [ ] Dashboard UI with tabs
- [ ] Health score cards
- [ ] Storage chart
- [ ] Scan control buttons
- [ ] Real-time status updates

**Checkpoint:** Dashboard renders with placeholder data.

---

#### Step 2.3.3: Create Duplicate Explorer UI
Create package: `com.storagehealth.ui.explorer`

**DuplicateExplorer.java:**
```java
@Component
public class DuplicateExplorer {
    private VBox root;
    private TableView<DuplicateGroupRow> duplicateTable;
    private Label summaryLabel;
    private ProgressBar loadingProgress;
    
    private final FileRepository fileRepository;
    
    @Autowired
    public DuplicateExplorer(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
        initializeUI();
    }
    
    private void initializeUI() {
        root = new VBox(10);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #1a1a1a;");
        
        // Summary
        summaryLabel = new Label("Total duplicates: 0 files, 0 GB recoverable");
        summaryLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14;");
        root.getChildren().add(summaryLabel);
        
        // Table
        duplicateTable = createDuplicateTable();
        VBox.setVgrow(duplicateTable, Priority.ALWAYS);
        root.getChildren().add(duplicateTable);
        
        // Load data
        loadDuplicates();
    }
    
    private TableView<DuplicateGroupRow> createDuplicateTable() {
        TableView<DuplicateGroupRow> table = new TableView<>();
        table.setStyle("-fx-control-inner-background: #222222; -fx-text-fill: #ffffff;");
        
        TableColumn<DuplicateGroupRow, String> hashCol = new TableColumn<>("Hash");
        hashCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getHashValue().substring(0, 16)));
        
        TableColumn<DuplicateGroupRow, Integer> countCol = new TableColumn<>("Count");
        countCol.setCellValueFactory(cellData -> 
            new SimpleIntegerProperty(cellData.getValue().getFileCount()).asObject());
        
        TableColumn<DuplicateGroupRow, String> sizeCol = new TableColumn<>("Total Size");
        sizeCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(formatSize(cellData.getValue().getTotalSize())));
        
        TableColumn<DuplicateGroupRow, String> recoverableCol = new TableColumn<>("Recoverable");
        recoverableCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(formatSize(cellData.getValue().getRecoverableSpace())));
        
        TableColumn<DuplicateGroupRow, Void> actionCol = new TableColumn<>("Action");
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button viewBtn = new Button("View");
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    viewBtn.setOnAction(e -> {
                        DuplicateGroupRow row = getTableView().getItems().get(getIndex());
                        handleViewDuplicate(row);
                    });
                    setGraphic(viewBtn);
                }
            }
        });
        
        table.getColumns().addAll(hashCol, countCol, sizeCol, recoverableCol, actionCol);
        
        return table;
    }
    
    private void loadDuplicates() {
        new Thread(() -> {
            try {
                // Load from database
                // duplicateTable.setItems(FXCollections.observableArrayList(duplicates));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void handleViewDuplicate(DuplicateGroupRow row) {
        // Show detailed view of duplicate group
    }
    
    private String formatSize(Long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    public VBox getRoot() {
        return root;
    }
}

@Data
@AllArgsConstructor
class DuplicateGroupRow {
    private String hashValue;
    private int fileCount;
    private long totalSize;
    private long recoverableSpace;
}
```

**Deliverables:**
- [ ] Duplicate table with columns
- [ ] Summary statistics
- [ ] View action for each group
- [ ] Data loading from database

**Checkpoint:** Duplicate explorer displays data in table.

---

### 2.4 Update REST Controllers for Dashboard

#### Step 2.4.1: Add Health & Recommendations Endpoints
```java
@RestController
@RequestMapping("/api/health")
@Slf4j
public class HealthController {
    private final HealthScoreCalculator healthCalculator;
    private final ScanSessionRepository sessionRepository;
    
    @Autowired
    public HealthController(
        HealthScoreCalculator healthCalculator,
        ScanSessionRepository sessionRepository
    ) {
        this.healthCalculator = healthCalculator;
        this.sessionRepository = sessionRepository;
    }
    
    @GetMapping("/score/{sessionId}")
    public ResponseEntity<HealthScoreDTO> getHealthScore(@PathVariable Long sessionId) {
        Optional<ScanSessionEntity> session = sessionRepository.findById(sessionId);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        StorageHealthScore score = healthCalculator.calculateHealthScore(session.get());
        return ResponseEntity.ok(mapToDTO(score));
    }
    
    private HealthScoreDTO mapToDTO(StorageHealthScore score) {
        return HealthScoreDTO.builder()
            .overallScore(score.getOverallScore())
            .duplicateWasteScore(score.getDuplicateWasteScore())
            .clutterScore(score.getClutterScore())
            .organizationScore(score.getOrganizationScore())
            .status(score.getHealthStatus())
            .build();
    }
}

@RestController
@RequestMapping("/api/recommendations")
@Slf4j
public class RecommendationController {
    private final RecommendationEngine recommendationEngine;
    private final RecommendationRepository recommendationRepository;
    
    @Autowired
    public RecommendationController(
        RecommendationEngine recommendationEngine,
        RecommendationRepository recommendationRepository
    ) {
        this.recommendationEngine = recommendationEngine;
        this.recommendationRepository = recommendationRepository;
    }
    
    @PostMapping("/generate/{sessionId}")
    public ResponseEntity<Void> generateRecommendations(@PathVariable Long sessionId) {
        // Implementation
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/list")
    public ResponseEntity<Page<RecommendationDTO>> listRecommendations(
        @RequestParam(required = false) String type,
        Pageable pageable
    ) {
        // Implementation
        return ResponseEntity.ok(Page.empty());
    }
}
```

**Deliverables:**
- [ ] Health score endpoint
- [ ] Recommendation generation endpoint
- [ ] Recommendation list endpoint

**Checkpoint:** All endpoints respond with correct data.

---

### Phase 2 Summary Checkpoint

**Verify:**
- [ ] File ranking works correctly
- [ ] Health scores calculated
- [ ] Recommendations generated
- [ ] Dashboard displays data
- [ ] Duplicate explorer works
- [ ] All UI tabs functional

**Deliverables:**
- [ ] File ranking engine with scoring formula
- [ ] Health score calculator
- [ ] Recommendation engine
- [ ] JavaFX dashboard
- [ ] Duplicate explorer UI
- [ ] REST endpoints for UI

**Estimated Time:** 3 weeks
**Lines of Code:** ~4,500 (total ~8,000)

---

## Phase 3: AI Image Analysis & Advanced Features (Weeks 7-9)

### Objective
Integrate OpenCV for image analysis and ONNX for embeddings.

### 3.1 Image Processing Services

#### Step 3.1.1: Create Blur Detection Service
```java
public interface ImageAnalysisService {
    ImageAnalysisResult analyzeImage(Path imagePath);
    double calculateBlurScore(Path imagePath);
}

@Data
@AllArgsConstructor
@Builder
public class ImageAnalysisResult {
    private double blurScore;
    private double brightnessScore;
    private double colorfulnessScore;
    private boolean isBlurry;
}

@Service
@Slf4j
public class ImageAnalysisServiceImpl implements ImageAnalysisService {
    private static final double BLUR_THRESHOLD = 100.0; // Laplacian variance
    
    @Override
    public ImageAnalysisResult analyzeImage(Path imagePath) {
        try {
            Mat image = Imgcodecs.imread(imagePath.toString());
            
            if (image.empty()) {
                log.warn("Failed to load image: {}", imagePath);
                return null;
            }
            
            double blurScore = calculateBlurScore(image);
            double brightnessScore = calculateBrightnessScore(image);
            double colorfulnessScore = calculateColorfulnessScore(image);
            
            image.release();
            
            return ImageAnalysisResult.builder()
                .blurScore(blurScore)
                .brightnessScore(brightnessScore)
                .colorfulnessScore(colorfulnessScore)
                .isBlurry(blurScore < BLUR_THRESHOLD)
                .build();
            
        } catch (Exception e) {
            log.error("Error analyzing image: {}", imagePath, e);
            return null;
        }
    }
    
    @Override
    public double calculateBlurScore(Path imagePath) {
        Mat image = Imgcodecs.imread(imagePath.toString(), Imgcodecs.IMREAD_GRAYSCALE);
        double blurScore = calculateBlurScore(image);
        image.release();
        return blurScore;
    }
    
    private double calculateBlurScore(Mat image) {
        Mat laplacian = new Mat();
        Imgproc.Laplacian(image, laplacian, CvType.CV_64F);
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(laplacian, mean, stddev);
        laplacian.release();
        return stddev.toArray()[0] * stddev.toArray()[0];
    }
    
    private double calculateBrightnessScore(Mat image) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);
        MatOfDouble mean = new MatOfDouble();
        Core.mean(hsv, mean);
        hsv.release();
        return mean.toArray()[2];
    }
    
    private double calculateColorfulnessScore(Mat image) {
        Mat rgb = new Mat();
        Imgproc.cvtColor(image, rgb, Imgproc.COLOR_BGR2RGB);
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(rgb, mean, stddev);
        rgb.release();
        return stddev.toArray()[0] + stddev.toArray()[1] + stddev.toArray()[2];
    }
}
```

---

## Phase 4: Cleanup System & Polish (Weeks 10-12)

### Objective
Implement safe cleanup, undo, and production optimization.

### 4.1 Cleanup Session Manager

#### Step 4.1.1: Create Cleanup Service
```java
public interface CleanupService {
    CleanupSession initiateCleanup(List<Long> fileIds);
    void executeCleanup(String sessionId);
    void undoCleanup(String sessionId);
}

@Service
@Slf4j
@Transactional
public class CleanupServiceImpl implements CleanupService {
    private static final String CLEANUP_BASE_PATH = System.getProperty("user.home") + 
                                                    "/.storage-health/cleanup_sessions/";
    
    private final CleanupSessionRepository cleanupSessionRepository;
    private final FileRepository fileRepository;
    
    @Autowired
    public CleanupServiceImpl(
        CleanupSessionRepository cleanupSessionRepository,
        FileRepository fileRepository
    ) {
        this.cleanupSessionRepository = cleanupSessionRepository;
        this.fileRepository = fileRepository;
    }
    
    @Override
    public CleanupSession initiateCleanup(List<Long> fileIds) {
        String sessionId = UUID.randomUUID().toString();
        
        List<FileEntity> files = fileRepository.findAllById(fileIds);
        long totalSize = files.stream().mapToLong(FileEntity::getSizeBytes).sum();
        
        CleanupSessionEntity session = CleanupSessionEntity.builder()
            .sessionId(sessionId)
            .filesCount(files.size())
            .totalSize(totalSize)
            .status(CleanupStatus.ACTIVE)
            .build();
        
        cleanupSessionRepository.save(session);
        
        // Create session directory
        Path sessionPath = Paths.get(CLEANUP_BASE_PATH, sessionId);
        try {
            Files.createDirectories(sessionPath);
            Files.createDirectory(sessionPath.resolve("files"));
        } catch (IOException e) {
            log.error("Failed to create cleanup session directory", e);
        }
        
        return mapToDTO(session);
    }
    
    @Override
    public void executeCleanup(String sessionId) {
        Optional<CleanupSessionEntity> session = cleanupSessionRepository.findBySessionId(sessionId);
        if (session.isEmpty()) {
            throw new IllegalArgumentException("Cleanup session not found: " + sessionId);
        }
        
        CleanupSessionEntity entity = session.get();
        Path sessionPath = Paths.get(CLEANUP_BASE_PATH, sessionId);
        Path filesPath = sessionPath.resolve("files");
        
        try {
            for (CleanupSessionFileEntity file : entity.getFiles()) {
                Path originalPath = Paths.get(file.getOriginalPath());
                Path archivedPath = filesPath.resolve(originalPath.getFileName().toString());
                
                // Move file to cleanup session
                Files.move(originalPath, archivedPath, StandardCopyOption.REPLACE_EXISTING);
                
                // Update metadata
                file.setArchivedPath(archivedPath.toString());
            }
            
            entity.setStatus(CleanupStatus.COMPLETED);
            cleanupSessionRepository.save(entity);
            
            log.info("Cleanup session {} completed", sessionId);
            
        } catch (IOException e) {
            log.error("Cleanup failed for session: {}", sessionId, e);
            entity.setStatus(CleanupStatus.ACTIVE);
            cleanupSessionRepository.save(entity);
            throw new RuntimeException("Cleanup execution failed", e);
        }
    }
    
    @Override
    public void undoCleanup(String sessionId) {
        Optional<CleanupSessionEntity> session = cleanupSessionRepository.findBySessionId(sessionId);
        if (session.isEmpty()) {
            throw new IllegalArgumentException("Cleanup session not found: " + sessionId);
        }
        
        CleanupSessionEntity entity = session.get();
        
        try {
            for (CleanupSessionFileEntity file : entity.getFiles()) {
                Path archivedPath = Paths.get(file.getArchivedPath());
                Path originalPath = Paths.get(file.getOriginalPath());
                
                // Restore to original location
                Files.createDirectories(originalPath.getParent());
                Files.move(archivedPath, originalPath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            entity.setStatus(CleanupStatus.RESTORED);
            cleanupSessionRepository.save(entity);
            
            log.info("Cleanup session {} restored", sessionId);
            
        } catch (IOException e) {
            log.error("Undo failed for session: {}", sessionId, e);
            throw new RuntimeException("Undo execution failed", e);
        }
    }
    
    private CleanupSession mapToDTO(CleanupSessionEntity entity) {
        return CleanupSession.builder()
            .sessionId(entity.getSessionId())
            .filesCount(entity.getFilesCount())
            .totalSize(entity.getTotalSize())
            .status(entity.getStatus().name())
            .build();
    }
}
```

---

## Implementation Checklist

### Phase 1: Foundation (Weeks 1-3)
- [ ] Maven project structure initialized
- [ ] Spring Boot application configured
- [ ] SQLite database schema created
- [ ] JPA entities and repositories
- [ ] File scanner with multi-threading
- [ ] Exact duplicate detection
- [ ] REST API endpoints
- [ ] Unit tests (80% coverage)
- [ ] Documentation

### Phase 2: Ranking & UI (Weeks 4-6)
- [ ] File ranking engine
- [ ] Health score calculator
- [ ] Recommendation engine
- [ ] JavaFX dashboard
- [ ] Duplicate explorer
- [ ] Charts and visualizations
- [ ] API integration
- [ ] Unit tests
- [ ] Documentation

### Phase 3: AI & Advanced (Weeks 7-9)
- [ ] OpenCV integration
- [ ] Blur detection
- [ ] Image analysis
- [ ] ONNX embeddings (optional)
- [ ] Near-duplicate detection
- [ ] Advanced recommendations
- [ ] Unit tests

### Phase 4: Cleanup & Polish (Weeks 10-12)
- [ ] Cleanup session manager
- [ ] Safe file moving
- [ ] Undo restore functionality
- [ ] Cleanup UI
- [ ] Performance optimization
- [ ] Security hardening
- [ ] Integration tests
- [ ] User documentation
- [ ] Release build

---

## Testing Strategy

### Unit Tests (Phase 1-4)
- FileScanner: 15-20 tests
- HashingService: 8-10 tests
- DuplicateDetector: 12-15 tests
- FileRankingService: 10-12 tests
- HealthScoreCalculator: 8-10 tests
- RecommendationEngine: 15-20 tests
- CleanupService: 10-12 tests
- ImageAnalysis: 8-10 tests

**Target Coverage:** 80%+

### Integration Tests
- Database operations
- API endpoints
- Scanner + Database flow
- Duplicate detection flow
- Cleanup + Undo flow

### Performance Tests
- Scanner on 1M files
- Duplicate detection performance
- Memory usage
- UI responsiveness

---

## Deployment Checklist

- [ ] Build executable JAR
- [ ] Create native installers (NSIS for Windows, DMG for macOS, DEB/RPM for Linux)
- [ ] Version numbering (semantic versioning)
- [ ] Release notes
- [ ] User manual
- [ ] FAQ/Troubleshooting
- [ ] Update mechanism
- [ ] Error reporting/analytics (local only)

---

## Success Metrics

1. **Performance:** Scan 1M files in < 5 minutes
2. **Accuracy:** 99%+ duplicate detection accuracy
3. **Safety:** Zero accidental deletions
4. **UI:** <100ms response for all interactions
5. **Reliability:** 99.9% uptime
6. **User Satisfaction:** Clear explanations, confidence scores

