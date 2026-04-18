# Local Development Setup

This guide walks through running AgentBrain on your local machine for development.

---

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Docker Desktop | 4.x+ | `docker --version` |
| Docker Compose | v2+ | `docker compose version` |
| Java | 21+ | `java -version` (for sync agent only) |
| Node.js | 20+ | `node --version` (for frontend dev only) |

---

## Quick Start (Docker Compose)

```bash
git clone https://github.com/TomsProducts/AgentBrain.git
cd AgentBrain

# Copy and edit environment file
cp .env.example .env
# Minimum required: set SYNC_TOKEN
echo "SYNC_TOKEN=$(openssl rand -hex 32)" >> .env

# Build and start everything
docker compose up --build
```

| Service | URL |
|---------|-----|
| Dashboard | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Health Check | http://localhost:8080/actuator/health |

---

## Environment File

`.env.example`:

```dotenv
# Claude home directory (mounted into backend container)
CLAUDE_HOME=~/.claude

# Sync token — must match between server .env and sync agent application.properties
SYNC_TOKEN=changeme_use_openssl_rand_hex_32

# PostgreSQL credentials (prod only — not used in dev)
DB_USER=agentbrain
DB_PASSWORD=changeme
```

For local dev, only `CLAUDE_HOME` and `SYNC_TOKEN` are needed.

---

## Dev Mode — Hot Reload

For frontend hot reload, run the frontend separately with Vite:

```bash
# Terminal 1: Backend only
docker compose up backend

# Terminal 2: Frontend dev server
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

Vite is configured to proxy `/api/` and `/ws` to `http://localhost:8080`.

```typescript
// vite.config.ts
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws':  { target: 'ws://localhost:8080', ws: true },
    },
  },
})
```

---

## Database

In dev mode, AgentBrain uses **H2** — an in-memory / file-based database embedded in the JVM. No separate database container needed.

Data is stored at `./data/agentbrain.mv.db` (relative to where the backend container mounts `./data`).

```yaml
# docker-compose.yml
volumes:
  - ./data:/app/data     # persists H2 data across restarts
```

To reset the database:
```bash
docker compose down
rm -rf ./data/
docker compose up
```

---

## Flyway Migrations

Database schema is managed by Flyway. Migrations run automatically on startup.

```
backend/src/main/resources/db/migration/
├── V1__memory.sql      — working_memory, episodic_memory tables
├── V2__lessons.sql     — lessons table + index
├── V3__skills.sql      — skill_meta table
└── V4__sync_log.sql    — sync_log table
```

To add a new migration:
1. Create `V5__description.sql` in `db/migration/`
2. Restart the backend — Flyway auto-applies it

> **Never modify existing migration files.** Flyway checksums each file. A mismatch causes startup failure.

---

## Running Tests

```bash
# Backend unit tests
cd backend
mvn test

# Frontend type check
cd frontend
npm run build   # TypeScript errors surface here
```

---

## Project Structure

```
AgentBrain/
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/io/agentbrain/
│       │   └── resources/
│       │       ├── application.yml          ← dev config (H2)
│       │       ├── application-prod.yml     ← prod config (PostgreSQL)
│       │       └── db/migration/
│       └── test/
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/
├── sync-agent/
│   ├── pom.xml
│   └── src/
├── docker-compose.yml          ← dev
├── docker-compose.prod.yml     ← prod (PostgreSQL)
└── .env.example
```
