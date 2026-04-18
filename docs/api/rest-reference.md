# REST API Reference

All endpoints are prefixed with `/api`. The server runs on port `8080` internally (or whatever `BACKEND_PORT` is set to on the host).

When accessing through the frontend nginx proxy, use `/api/...` relative to the frontend URL.

---

## Authentication

Most endpoints are unauthenticated (designed for LAN use). The sync endpoints require a Bearer token.

```
Authorization: Bearer <SYNC_TOKEN>
```

---

## Memory — Working

### List Working Memories

```
GET /api/memory/working
```

Returns all non-expired working memories.

**Response** `200 OK`
```json
[
  {
    "id": 1,
    "content": "Working on fixing the CORS config in AgentBrain",
    "tags": "agentbrain,cors,bugfix",
    "createdAt": "2026-04-18T13:00:00",
    "expiresAt": "2026-04-19T13:00:00"
  }
]
```

---

### Create Working Memory

```
POST /api/memory/working
Content-Type: application/json
```

**Request Body**
```json
{
  "content": "Fixing the graduate button CORS issue",
  "tags": "agentbrain,cors,lessons"
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `content` | ✅ | The memory content |
| `tags` | ❌ | Comma-separated tags |

**Response** `200 OK` — created WorkingMemory object

---

### Delete Working Memory

```
DELETE /api/memory/working/{id}
```

**Response** `200 OK`

---

## Memory — Episodic

### List Episodic Memories

```
GET /api/memory/episodic?page=0&size=20
```

Paginated. Sorted by `occurred_at DESC`.

**Response** `200 OK`
```json
{
  "content": [
    {
      "id": 12,
      "content": "Fixed sync agent startup by calling localApi.start() before fullSync()",
      "tags": "sync-agent,startup,bugfix",
      "salienceScore": 0.97,
      "staged": false,
      "occurredAt": "2026-04-18T12:00:00",
      "expiresAt": "2026-07-17T12:00:00"
    }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "number": 0
}
```

---

### Create Episodic Memory

```
POST /api/memory/episodic
Content-Type: application/json
```

**Request Body**
```json
{
  "content": "Discovered that Flyway 10+ requires flyway-database-postgresql dependency for PostgreSQL 16",
  "tags": "flyway,postgresql,deployment"
}
```

**Response** `200 OK` — created EpisodicMemory object

---

## Memory — Search

### Search All Layers

```
GET /api/memory/search?q=<query>
```

Searches across working memory, episodic memory, and lessons using case-insensitive LIKE matching against `content` and `tags`.

**Response** `200 OK`
```json
{
  "working": [...],
  "episodic": [...],
  "lessons": [...]
}
```

---

## Context Budget

### Get Context Snapshot

```
GET /api/context?q=<task description>
```

Returns a ranked Markdown snapshot of the most relevant memories for the given task. This is what Claude Code reads at session start.

**Response** `200 OK` — `text/markdown`
```markdown
## Working Memory
- Fixing the graduate button CORS issue (expires in 23h) [cors, lessons]

## Relevant Episodes
- Fixed sync agent startup by calling localApi.start() before fullSync() [sync-agent] (salience: 0.97)
- Flyway 10+ requires flyway-database-postgresql for PostgreSQL 16 [flyway] (salience: 0.94)

## Accepted Lessons
- **Always validate resolved paths start with the mount dir** — prevents path traversal in file APIs — graduated 2026-04-18
- **Thread.currentThread().join() is required** — prevents JVM exit when all threads are daemon — graduated 2026-04-18
```

Results are ranked by `salience × relevance`. Only `ACCEPTED` lessons appear.

---

## Lessons

### List Lessons

```
GET /api/lessons?status=STAGED
```

| Parameter | Values | Default |
|-----------|--------|---------|
| `status` | `STAGED`, `ACCEPTED`, `REJECTED`, `REOPENED` | all |

**Response** `200 OK`
```json
[
  {
    "id": 5,
    "claim": "Sync agent clear() must be called before fullSync()",
    "conditions": "Java sync agent startup sequence",
    "status": "STAGED",
    "patternId": "f93ede78",
    "rationale": null,
    "salience": 1.0,
    "createdAt": "2026-04-18T03:00:00",
    "graduatedAt": null
  }
]
```

---

### Graduate a Lesson

Move a lesson from `STAGED`/`REOPENED` → `ACCEPTED`.

```
POST /api/lessons/{id}/graduate
Content-Type: application/json
```

**Request Body**
```json
{
  "rationale": "Confirmed this pattern in two separate deployments. The hasChanged() check short-circuits the push after loadFromDisk() unless we clear state first."
}
```

| Rule | Details |
|------|---------|
| `rationale` required | Returns `400 Bad Request` if null or blank |
| Only `STAGED` or `REOPENED` | Returns `400 Bad Request` for other statuses |

**Response** `200 OK` — updated Lesson object with `status: "ACCEPTED"`

**Error Response** `400 Bad Request`
```json
{
  "status": 400,
  "error": "Bad Request",
  "path": "/api/lessons/5/graduate"
}
```

---

### Reject a Lesson

Move a lesson from `STAGED`/`REOPENED` → `REJECTED`.

```
POST /api/lessons/{id}/reject
Content-Type: application/json
```

**Request Body** (optional)
```json
{
  "reason": "Too specific to one deployment, not a general pattern"
}
```

**Response** `200 OK` — updated Lesson object

---

### Reopen a Lesson

Move a lesson from `REJECTED` → `STAGED` (for re-evaluation).

```
POST /api/lessons/{id}/reopen
```

No request body required.

**Response** `200 OK` — updated Lesson object with `status: "STAGED"`

---

## Dream Cycle

### Trigger Dream Cycle Manually

```
POST /api/dream/run
```

Runs the full dream cycle synchronously. May take a few seconds for large episode sets.

**Response** `200 OK`
```json
{
  "stagedCount": 3,
  "processedEpisodes": 8,
  "clustersFound": 4,
  "skippedDuplicates": 1,
  "runAt": "2026-04-18T14:00:00"
}
```

---

### Get Last Run Result

```
GET /api/dream/last
```

**Response** `200 OK` — same shape as trigger response, or `null` if never run.

---

## Claude Config Browser

All paths are **relative to the `~/.claude/` mount**. Do not include a leading `/`.

### Get File Tree

```
GET /api/claude/tree
```

**Response** `200 OK`
```json
[
  {
    "name": ".claude",
    "path": "",
    "type": "directory",
    "children": [
      { "name": "CLAUDE.md", "path": "CLAUDE.md", "type": "file", "size": 4821, "modified": "2026-04-18T13:00:00" },
      { "name": "settings.json", "path": "settings.json", "type": "file", "size": 1024, "modified": "2026-04-18T12:00:00" },
      {
        "name": "skills",
        "path": "skills",
        "type": "directory",
        "children": [
          { "name": "my-skill", "path": "skills/my-skill", "type": "directory", "children": [...] }
        ]
      }
    ]
  }
]
```

---

### Read File

```
GET /api/claude/file?path=CLAUDE.md
```

**Response** `200 OK` — `text/plain` — raw file content

**Error** `400 Bad Request` — path traversal attempt  
**Error** `404 Not Found` — file does not exist

---

### Write File

```
PUT /api/claude/file?path=CLAUDE.md
Content-Type: application/json
```

**Request Body**
```json
{
  "content": "# My instructions\n\n..."
}
```

**Response** `200 OK`

---

### Create File

```
POST /api/claude/file?path=rules/my-rule.md
Content-Type: application/json
```

**Request Body**
```json
{
  "content": "# My Rule\n\nAlways..."
}
```

**Response** `200 OK`  
**Error** `409 Conflict` — file already exists

---

### Delete File

```
DELETE /api/claude/file?path=rules/old-rule.md
```

**Response** `200 OK`  
**Error** `404 Not Found` — file does not exist

---

## Stats

```
GET /api/stats
```

**Response** `200 OK`
```json
{
  "workingMemoryCount": 3,
  "episodicMemoryCount": 42,
  "stagedLessonsCount": 5,
  "acceptedLessonsCount": 12,
  "rejectedLessonsCount": 2,
  "lastDreamRun": "2026-04-18T03:00:00"
}
```

---

## Health Check

```
GET /actuator/health
```

**Response** `200 OK`
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```
