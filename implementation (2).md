# SamCleans — Implementation Plan

## 0. Priority Fix: Duplicate Waste Score Always 0

### Symptom
`duplicateWasteScore` returns 0 even after a scan finds duplicate files, causing the
dashboard to show "100% waste" (since the UI displays `100 - score`).

### Most likely root causes (check in this order)

**A. Score calculated before duplicate detection finishes**
If `submit-metadata` triggers `detectDuplicates()` in a background thread, and the
frontend calls `/api/health/score` immediately after the scan response returns, the
duplicates table is still empty at read time.

*Fix:* Don't return the scan response until duplicate detection completes, OR make the
health score endpoint block/poll until a `scanStatus = COMPLETE` flag is set for that
session.

```java
@PostMapping("/submit-metadata")
public ResponseEntity<ScanResult> submitMetadata(@RequestBody ScanRequest req) {
    List<FileMetadata> saved = fileRepository.saveAll(req.files());

    // run synchronously if the dataset is small enough for a demo,
    // or return a sessionId + status the frontend polls/subscribes to
    duplicateDetectionService.detectDuplicates(req.sessionId());
    recommendationService.generateRecommendations(req.sessionId());

    healthScoreService.recalculate(req.sessionId());
    return ResponseEntity.ok(new ScanResult(req.sessionId(), "COMPLETE"));
}
```

**B. Session ID mismatch between duplicate storage and score calculation**
Verify `DuplicateGroup` rows and the `HealthScoreCalculator` query use the *same*
`sessionId` field name and value. A silent `null` vs actual UUID mismatch is the single
most common cause of "it works in isolation but the API always returns 0."

```java
// Add a quick sanity check in the calculator itself:
public HealthScore calculate(String sessionId) {
    List<FileMetadata> files = fileRepository.findBySessionId(sessionId);
    log.info("Calculating health score for session={} fileCount={}", sessionId, files.size());
    if (files.isEmpty()) {
        log.warn("No files found for session={} — check sessionId consistency", sessionId);
    }
    ...
}
```

**C. Duplicate flag never written back to `FileMetadata`**
If duplicates are detected and stored in a separate `DuplicateGroup` table, but
`FileMetadata.isDuplicate` (or equivalent field used by the score formula) is never
updated, the score calculator sees zero duplicates even though the groups exist.

```java
public void detectDuplicates(String sessionId) {
    List<DuplicateGroup> groups = embeddingComparator.findGroups(sessionId);
    duplicateGroupRepository.saveAll(groups);

    // THIS STEP IS OFTEN MISSING:
    for (DuplicateGroup group : groups) {
        for (FileMetadata dup : group.getDuplicateFiles()) { // all but the "keeper"
            dup.setIsDuplicate(true);
            dup.setWastedBytes(dup.getSizeBytes());
        }
    }
    fileRepository.saveAll(group.getAllFiles());
}
```

**D. Integer division truncation**
```java
// WRONG — truncates to 0 if wastedBytes < totalBytes and both are long/int
int wasteScore = (int) (wastedBytes / totalBytes * 100);

// RIGHT — cast to double before dividing
int wasteScore = (int) ((double) wastedBytes / totalBytes * 100);
```

### Verification steps
1. Log `sessionId`, file count, and duplicate group count at each stage (scan → detect
   → score calculation).
2. Manually query the DB after a scan: confirm `DuplicateGroup` rows exist AND
   `FileMetadata.isDuplicate = true` for the expected files.
3. Hit `/api/health/breakdown` directly (skip the frontend) and confirm non-zero
   `duplicateWasteScore` before debugging the UI layer.
4. Only after backend returns correct non-zero values, fix frontend label/display math.

> **If this doesn't match your actual code:** paste your `HealthScoreCalculator`,
> `DuplicateDetectionService`, and the relevant entity classes and I'll pinpoint the
> exact broken line instead of listing candidates.

---

## 1. Phase 1 — Dashboard Bug Fixes

- [ ] Clear stale `localStorage` session on load if `sessionId` doesn't exist server-side
      (add `GET /api/session/{id}/exists`)
- [ ] Fix `HealthScoreCards.tsx` label: show "Space Wasted by Duplicates" =
      `100 - duplicateWasteScore`, not the raw score, so 0 duplicates reads as "0% wasted"
- [ ] Add a "Re-scan" button that clears session state and re-triggers scan
- [ ] Show session name + scanned path in the UI header
- [ ] After scan, auto-call `detectDuplicates` (background thread) before returning
      "scan complete" to the frontend — see fix A above

---

## 2. Phase 2 — Real-Time Pipeline via Kafka + WebSocket

### 2.1 Kafka topics
| Topic | Producer | Payload |
|---|---|---|
| `file-scanned` | Scanner service, per file during scan | `{sessionId, fileMetadata}` |
| `duplicate-found` | Duplicate detection service | `{sessionId, duplicateGroup}` |

### 2.2 Publish incrementally, not in one batch
```java
@Service
public class FileScanService {
    private final KafkaTemplate<String, FileScannedEvent> kafkaTemplate;

    public void scanFolder(String path, String sessionId) {
        Files.walk(Paths.get(path)).forEach(file -> {
            FileMetadata metadata = extractMetadata(file);
            kafkaTemplate.send("file-scanned", new FileScannedEvent(sessionId, metadata));
        });
    }
}
```

### 2.3 Consumer updates DB + recalculates score per event
```java
@KafkaListener(topics = "file-scanned", groupId = "dashboard-group")
public void onFileScanned(FileScannedEvent event) {
    fileRepository.save(event.toEntity());
    HealthScore updated = healthScoreCalculator.recalculate(event.sessionId());
    messagingTemplate.convertAndSend(
        "/topic/dashboard-updates/" + event.sessionId(), updated);
}
```

### 2.4 WebSocket config (Spring STOMP)
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();
    }
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
    }
}
```

### 2.5 Frontend subscribes instead of polling
```javascript
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);
stompClient.connect({}, () => {
  stompClient.subscribe(`/topic/dashboard-updates/${sessionId}`, (msg) => {
    const updatedScore = JSON.parse(msg.body);
    setHealthScore(updatedScore);
  });
});
```

---

## 3. Phase 3 — Ollama Integration for Live Insights

### 3.1 Batch events before calling the LLM (don't call per-file)
```java
@KafkaListener(topics = "duplicate-found", groupId = "insight-group")
public void onDuplicateFound(DuplicateFoundEvent event) {
    batchBuffer.add(event);
    if (batchBuffer.size() >= 20 || timeSinceLastFlush() > 5000) {
        String prompt = buildSummaryPrompt(batchBuffer);
        String insight = ollamaClient.generate(prompt);
        messagingTemplate.convertAndSend(
            "/topic/dashboard-updates/" + sessionId, new InsightEvent(insight));
        batchBuffer.clear();
    }
}
```

### 3.2 Docker Compose — verify network hostnames
```yaml
services:
  ollama:
    image: ollama/ollama
    ports: ["11434:11434"]
    volumes: ["ollama_data:/root/.ollama"]

  backend:
    build: ./backend
    environment:
      - OLLAMA_BASE_URL=http://ollama:11434   # NOT localhost — common bug inside Docker
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
    depends_on: [kafka, ollama]

  kafka:
    image: confluentinc/cp-kafka:latest
```

---

## 4. Full Verification Checklist

1. [ ] Scan a folder with 2+ known duplicate files
2. [ ] Backend logs show non-empty file count and duplicate group count for the session
3. [ ] `GET /api/health/breakdown` returns non-zero `duplicateWasteScore` directly
       (before checking the frontend)
4. [ ] Dashboard updates incrementally during scan (not just once at the end) —
       confirm via browser DevTools → Network → WS tab showing incoming frames
5. [ ] `docker exec -it kafka kafka-console-consumer --topic file-scanned
       --bootstrap-server localhost:9092` shows live messages during a scan
6. [ ] Ollama insight text appears within ~5 seconds of duplicates being found
7. [ ] Re-scan button clears old state and produces fresh, correct scores
