# AgentBrain

A self-hosted web dashboard for your Claude Code agent brain.

## What it does

**Two things in one app:**

### 1. Agentic Memory Brain
Gives your Claude Code sessions persistent memory across sessions.

- **Working memory** — notes from the current task, auto-expire after 24h
- **Episodic memory** — recent session history, decays over 90 days
- **Lessons** — patterns Claude learned, reviewed and graduated by you
- **Dream cycle** — runs every night at 03:00, clusters recent memories into candidate lessons for your review
- **Context budget** — builds a ranked memory snapshot for Claude to read at session start

### 2. Claude Config Browser
View and edit your `~/.claude/` directory from a browser. See all your CLAUDE.md, settings.json, skills, agents, commands, and rules. Edit any file inline and save directly back to disk.

---

## Quick Start

```bash
git clone <this-repo>
cd agentbrain
cp .env.example .env
# Edit .env — add a SYNC_TOKEN
docker compose up --build
```

Open **http://localhost:3000**

Backend API: **http://localhost:8080**  
Health check: **http://localhost:8080/actuator/health**

---

## Production (dedicated server with PostgreSQL)

```bash
cp .env.example .env
# Edit .env:
#   DB_USER=agentbrain
#   DB_PASSWORD=your-secure-password
#   SYNC_TOKEN=$(openssl rand -hex 32)

docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

---

## Give Memory to Claude Code

At the start of a Claude Code session:

```bash
curl http://localhost:8080/api/context?q="<your current task description>"
```

Or add to your `~/.claude/CLAUDE.md`:

```markdown
## Memory
At session start, call http://localhost:8080/api/context?q="<task>" and read the result.
```

---

## Sync Agent

Keep your local `~/.claude/` in sync with the server:

```bash
cd sync-agent
# Build: mvn package -DskipTests
java -jar target/agentbrain-sync-*.jar setup   # one-time setup
java -jar target/agentbrain-sync-*.jar         # start daemon
```

The daemon watches `~/.claude/` locally and syncs changes bidirectionally with the server's `/claude-store/` volume. See [SYNC_ARCHITECTURE.md](../SYNC_ARCHITECTURE.md) for full details.

---

## Ports

| Service  | Port |
|----------|------|
| Frontend | 3000 |
| Backend  | 8080 |
| Sync API (local) | 7701 |
| Database | 5432 (prod only, not exposed) |

---

## Stack

- Java 21 + Spring Boot 3.3 + Maven
- React 18 + TypeScript + Vite + Tailwind CSS
- H2 (dev) / PostgreSQL (prod) via Flyway
- Docker Compose

---

## API Reference

```
GET    /api/memory/working              List working memories
POST   /api/memory/working              { content, tags }
DELETE /api/memory/working/{id}

GET    /api/memory/episodic             ?page=0&size=20
POST   /api/memory/episodic             { content, tags }

GET    /api/memory/search?q=            Search all layers
GET    /api/context?q=                  Context budget (top-N + accepted lessons)

GET    /api/lessons                     ?status=STAGED|ACCEPTED|REJECTED
POST   /api/lessons/{id}/graduate       { rationale } — required, 400 if blank
POST   /api/lessons/{id}/reject         { reason }
POST   /api/lessons/{id}/reopen

POST   /api/dream/run                   Trigger dream cycle manually
GET    /api/dream/last                  Last run result

GET    /api/claude/tree                 ~/.claude/ file tree
GET    /api/claude/file?path=           Read file
PUT    /api/claude/file?path=           Write file { content }
POST   /api/claude/file?path=           Create file { content }
DELETE /api/claude/file?path=           Delete file

POST   /api/sync/push                   Sync push (Bearer token)
GET    /api/sync/pending?since=         Pending WebUI changes
GET    /api/sync/snapshot               Full file list
GET    /api/stats                       Dashboard stats

WS     /ws → /topic/activity            Live event feed (STOMP)
```
