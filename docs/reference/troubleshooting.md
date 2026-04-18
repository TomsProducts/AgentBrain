# Troubleshooting

This guide covers common issues, error messages, and how to fix them.

---

## Backend / Server Issues

### `Unsupported Database: PostgreSQL 16.x` at startup

**Cause:** Flyway 10+ no longer bundles database drivers. `flyway-core` alone is insufficient for PostgreSQL 16.

**Fix:** Add to `backend/pom.xml`:
```xml
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

---

### `Flyway checksum mismatch` at startup

**Cause:** An existing migration file was modified after it was already applied to the database.

**Fix (dev only — destroys data):**
```bash
docker compose down
rm -rf ./data/
docker compose up
```

**Fix (prod — safe):** Create a new migration that corrects the issue. Never modify `V1__`, `V2__`, etc. files.

---

### Backend container keeps restarting

```bash
docker compose logs backend | tail -50
```

Common causes:
- Database not ready yet → `depends_on: db: condition: service_healthy` should handle this — check if `db` container is healthy
- Port already in use → change `BACKEND_PORT` in `.env`
- Missing environment variable → check for `required` properties in `application-prod.yml`

---

### `Connection refused` on `/actuator/health`

```bash
# Is the container running?
docker compose ps

# Is it on the right port?
docker compose port backend 8080

# Check logs
docker compose logs backend | grep "Started\|ERROR"
```

---

## Frontend Issues

### Blank page / white screen

```bash
# Check nginx is serving
curl -v http://localhost:3000

# Check nginx logs
docker compose logs frontend
```

Common cause: React SPA loaded but API calls failing — open browser devtools → Network tab → look for red requests.

---

### `Graduate failed: {}` error

**Cause:** CORS blocking API calls. The backend only allows specific origins.

**Fix:** Already fixed in v1.0.1 — ensure `CorsConfig.java` uses `allowedOriginPatterns("*")` instead of `allowedOrigins("http://localhost:3000")`.

Verify:
```bash
curl -v -X POST http://your-server:3000/api/lessons/1/graduate \
  -H "Origin: http://your-server:3000" \
  -H "Content-Type: application/json" \
  -d '{"rationale": "test"}' 2>&1 | grep "HTTP/"
# Should be: HTTP/1.1 200 (not 403)
```

---

### API calls return `404` through nginx

**Cause:** nginx proxy config doesn't match Spring Boot's endpoint paths.

Check `nginx.conf`:
```nginx
location /api/ {
    proxy_pass http://backend:8080/api/;  # trailing slash required
}
```

Both `/api/` on the left and right are required. Without the trailing slash on the right, paths get doubled.

---

## Sync Agent Issues

### `JVM exits immediately after starting`

**Cause:** All threads (WatchService, HttpServer) are daemon threads. When `main()` returns, the JVM exits.

**Fix:** `main()` must block:
```java
// SyncAgentApplication.java — last line of main()
Thread.currentThread().join();
```

---

### `Files not syncing after restart`

**Cause:** `SyncState` is populated from `loadFromDisk()` before `fullSync()`, so `hasChanged()` returns false for all files (hashes already match).

**Fix:** Call `syncState.clear()` before `fullSync()`:
```java
syncState.clear();
loadFromDisk();
forcePush(all files);
```

---

### `401 Unauthorized` on push

**Cause:** Token mismatch between sync agent and server.

**Check:**
```bash
# Laptop
grep server.token ~/.agentbrain/application.properties

# Server
grep SYNC_TOKEN ~/agentbrain/.env
```

They must be identical. Update one to match the other and restart the sync agent.

---

### `MalformedInputException: Input length = 1`

**Cause:** Sync agent tried to read a binary file (e.g., a `.ttf` font or `.pack` git file) in `~/.claude/plugins/`.

**This is expected behavior** — the sync agent skips binary files and logs them at DEBUG level. No action needed.

If you want to suppress these log messages:
```properties
# ~/.agentbrain/application.properties
logging.level.io.agentbrain.sync=WARN
```

---

### Local API (`:7701`) not responding

```bash
# Check if sync agent is running
lsof -i :7701

# If not running, start it
cd ~/.agentbrain && java -jar agentbrain-sync.jar

# On macOS, check launchd
launchctl list | grep agentbrain
launchctl start io.agentbrain.sync
```

---

### Full sync takes forever

The `plugins/marketplaces/` directory in `~/.claude/` can contain thousands of binary files. These are scanned (and skipped) on every startup but still take time.

Add an exclude pattern to skip them:
```properties
# ~/.agentbrain/application.properties
sync.exclude-patterns=plugins/**
```

Restart the sync agent after changing this.

---

## Dream Cycle Issues

### Dream cycle ran but no lessons were staged

**Causes:**
1. **No episodic memories** — Dream Cycle needs entries to cluster. Add some via the UI or API.
2. **All memories already staged** — each entry is only processed once (`staged = false`). Add new memories.
3. **Only 1 cluster** — with 1-2 memories, there may only be 1 cluster → 1 candidate → possibly rejected as duplicate.

**Check:**
```bash
curl http://localhost:8080/api/memory/episodic?page=0&size=50 | \
  python3 -c "import json,sys; data=json.load(sys.stdin); print(f'Total: {data[\"totalElements\"]}, Unstaged: {sum(1 for e in data[\"content\"] if not e[\"staged\"])}')"
```

---

### `POST /api/dream/run` returns immediately with `stagedCount: 0`

**Normal** if there are no unstaged episodic memories from the last 24 hours.

To test the full cycle, add a few episodic memories first:
```bash
for i in 1 2 3 4 5; do
  curl -s -X POST http://localhost:8080/api/memory/episodic \
    -H "Content-Type: application/json" \
    -d "{\"content\": \"Test memory $i for dream cycle clustering\", \"tags\": \"test,dream\"}"
done

curl -X POST http://localhost:8080/api/dream/run
```

---

## Database Issues

### PostgreSQL container unhealthy

```bash
docker compose logs db | tail -20
```

Common causes:
- `DB_PASSWORD` not set in `.env`
- Volume permission issue → `chown 999:999` on the PostgreSQL data directory

```bash
# Reset PostgreSQL volume (destroys data)
docker compose down
docker volume rm agentbrain_pgdata
docker compose up -d
```

---

## Getting More Debug Info

### Enable debug logging on the backend

```bash
# Add to .env or docker-compose.yml environment section
LOGGING_LEVEL_IO_AGENTBRAIN=DEBUG
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB=DEBUG

docker compose up -d backend
docker compose logs -f backend
```

### Enable debug logging on the sync agent

```properties
# ~/.agentbrain/application.properties
logging.level.io.agentbrain=DEBUG
logging.level.org.springframework=WARN
```

### Check the sync log table

```bash
# Connect to PostgreSQL
docker compose exec db psql -U agentbrain agentbrain

# Recent sync activity
SELECT path, source, accepted, created_at 
FROM sync_log 
ORDER BY created_at DESC 
LIMIT 20;
```
