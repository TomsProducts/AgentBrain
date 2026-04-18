# Sync API Reference

The Sync API is used by the sync agent to push and pull files between the laptop's `~/.claude/` and the server's `/claude-home` volume.

All sync endpoints require Bearer token authentication.

---

## Authentication

```
Authorization: Bearer <SYNC_TOKEN>
```

Missing or invalid token → `401 Unauthorized`

---

## Push Files

```
POST /api/sync/push
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body**
```json
{
  "path": "CLAUDE.md",
  "content": "# My instructions...",
  "hash": "sha256-hex-of-content",
  "source": "laptop",
  "timestamp": "2026-04-18T14:00:00Z"
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `path` | ✅ | Relative path from `~/.claude/` root |
| `content` | ✅ | Full file content as UTF-8 string |
| `hash` | ✅ | SHA-256 hex of the content |
| `source` | ❌ | `"laptop"` or `"webui"` — for audit log |
| `timestamp` | ❌ | ISO-8601 timestamp of the change |

**Success Response** `200 OK`
```json
{
  "id": 42,
  "path": "CLAUDE.md",
  "accepted": true,
  "serverPath": "/claude-home/CLAUDE.md"
}
```

**Error Responses**

| Status | Cause |
|--------|-------|
| `400` | Path traversal attempt |
| `401` | Invalid or missing Bearer token |
| `413` | Content too large (> 1MB) |

---

## Get Pending Changes

Poll for files modified on the server via the Claude Config Browser that haven't been pulled yet.

```
GET /api/sync/pending?since=<ISO-8601>
Authorization: Bearer <token>
```

**Parameters**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `since` | ✅ | ISO-8601 timestamp — only return changes after this time |

**Response** `200 OK`
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

Empty array `[]` means no pending changes.

---

## Snapshot

Get a hash map of all files currently on the server. Used by `fullSync()` on startup to avoid re-pushing unchanged files.

```
GET /api/sync/snapshot
Authorization: Bearer <token>
```

**Response** `200 OK`
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

---

## Sync Log

The server records every push/pull to the `sync_log` table for audit and debugging:

```sql
CREATE TABLE sync_log (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    path         VARCHAR(1000),
    hash         VARCHAR(64),
    source       VARCHAR(50),    -- 'laptop' | 'webui'
    accepted     BOOLEAN,
    reject_reason VARCHAR(500),
    created_at   TIMESTAMP DEFAULT NOW()
);
```

Query recent activity:
```bash
docker compose exec db psql -U agentbrain agentbrain -c \
  "SELECT path, source, accepted, created_at FROM sync_log ORDER BY created_at DESC LIMIT 20;"
```
