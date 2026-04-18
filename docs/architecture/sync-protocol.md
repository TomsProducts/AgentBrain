# Sync Protocol

The sync protocol defines how files in `~/.claude/` on your laptop are kept in sync with the `/claude-home` volume on the AgentBrain server.

---

## Overview

```
LAPTOP                                    SERVER
~/.claude/                                /claude-home/
    │                                          │
    │  1. WatchService detects change          │
    │  2. Compute SHA-256 hash                 │
    │  3. Compare with SyncState               │
    │  4. If changed → POST /api/sync/push ───▶│
    │                                          │  5. Write file to volume
    │                                          │  6. Log to sync_log table
    │◀─── 6. Poll /api/sync/pending ───────────│
    │  7. Pull changes made via WebUI           │
    │  8. Write to ~/.claude/                  │
```

The sync is **eventually consistent** and **unidirectional by default** (laptop → server). Changes made via the Claude Config Browser on the server are pulled by the sync agent on the next poll cycle (default: 30 seconds).

---

## Authentication

Every sync API call requires a Bearer token:

```
Authorization: Bearer <SYNC_TOKEN>
```

The token is configured in:
- Server: `SYNC_TOKEN` in `.env`
- Laptop: `server.token` in `~/.agentbrain/application.properties`

If the token is missing or wrong, the server returns `401 Unauthorized`.

---

## SyncState — Local Hash Map

The sync agent maintains an in-memory map of `relativePath → SHA-256 hash` for all files it has pushed to the server.

```java
// SyncState.java
private final Map<String, String> hashes = new ConcurrentHashMap<>();

boolean hasChanged(String path, String content) {
    String newHash = sha256(content);
    String oldHash = hashes.get(path);
    return !newHash.equals(oldHash);
}

void update(String path, String content) {
    hashes.put(path, sha256(content));
}

void clear() {
    hashes.clear();   // forces full re-sync on next startup
}
```

On startup, `fullSync()` calls `clear()` first so that all files are pushed to the server, regardless of whether they were pushed before.

---

## Push Protocol

### Endpoint

```
POST /api/sync/push
Authorization: Bearer <token>
Content-Type: application/json
```

### Request Body

```json
{
  "path": "CLAUDE.md",
  "content": "# My instructions...",
  "hash": "a3f1e2...",
  "source": "laptop",
  "timestamp": "2026-04-18T14:00:00Z"
}
```

### Response

```json
{
  "id": 42,
  "path": "CLAUDE.md",
  "accepted": true,
  "serverPath": "/claude-home/CLAUDE.md"
}
```

### Path Security

The server **always** validates that the resolved absolute path starts with the configured `claude-dir` mount:

```java
Path resolved = Paths.get(claudeDir).resolve(relativePath).normalize();
if (!resolved.startsWith(Paths.get(claudeDir).normalize())) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "Path traversal detected: " + relativePath);
}
```

Any path containing `..` or escaping the mount directory is rejected with `400 Bad Request`.

---

## Pull Protocol (Pending Changes)

The sync agent polls the server for changes made via the Claude Config Browser WebUI.

### Endpoint

```
GET /api/sync/pending?since=<ISO-8601-timestamp>
Authorization: Bearer <token>
```

### Response

```json
[
  {
    "path": "skills/my-skill/SKILL.md",
    "content": "# My Skill\n...",
    "hash": "b2c3d4...",
    "modifiedAt": "2026-04-18T13:45:00Z",
    "source": "webui"
  }
]
```

The sync agent writes each entry to `~/.claude/<path>` and updates its local `SyncState`.

### Poll Interval

Default: every 30 seconds. Configurable via `sync.pull-interval-seconds` in `application.properties`.

---

## Snapshot Endpoint

Used during `fullSync()` on startup to compare local state with server state without sending all content:

```
GET /api/sync/snapshot
Authorization: Bearer <token>
```

Response:

```json
{
  "files": {
    "CLAUDE.md": "a3f1e2...",
    "settings.json": "b4c5d6...",
    "skills/my-skill/SKILL.md": "e7f8a9..."
  },
  "count": 3,
  "generatedAt": "2026-04-18T14:00:00Z"
}
```

Files present on the server but not locally → pulled to laptop.  
Files present locally but not in snapshot → pushed to server.

---

## File Filtering

Not all files in `~/.claude/` are synced. The sync agent skips:

| Pattern | Reason |
|---------|--------|
| `*.ttf`, `*.woff` | Binary font files — not text |
| `*.tar.gz`, `*.zip` | Binary archives |
| `*.pack`, `*.idx` | Git pack files |
| `projects/**` | Session history — large, not useful to sync |
| `history.jsonl` | Prompt history — large, privacy-sensitive |
| Files > 1MB | Oversized for a text editor |
| Unreadable binary files | `MalformedInputException` on `Files.readString()` |

These files are skipped silently (logged at DEBUG level).

---

## Startup Sequence

```
main()
    │
    ├── 1. Start LocalApiServer (:7701) — FIRST, so hooks can connect immediately
    │
    ├── 2. Schedule fullSync() in background virtual thread (2 second delay)
    │       │
    │       ├── a. syncState.clear()
    │       ├── b. Walk ~/.claude/, skip binary/filtered files
    │       ├── c. forcePush() each file to server (bypasses hasChanged check)
    │       └── d. Mark SyncState for each pushed file
    │
    ├── 3. Start WatchService in virtual thread — watches ~/.claude/ for changes
    │
    └── 4. Thread.currentThread().join() — blocks main thread to keep JVM alive
         (all other threads are daemon threads; without this, JVM exits immediately)
```

---

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Server unreachable | Logged as WARN, retry on next change event |
| 401 Unauthorized | Logged as ERROR, sync disabled until restart |
| 400 Path traversal | Logged as ERROR, file skipped |
| Binary file read error | Logged as DEBUG, file skipped |
| Network timeout | Logged as WARN, retry |
| Disk full on server | Logged as ERROR with HTTP 500 response |

The sync agent never crashes on push failures — it continues watching for new changes.
