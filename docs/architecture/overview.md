# System Architecture Overview

AgentBrain is a self-hosted web application with three major components:

1. **Backend** — Spring Boot 3.3 REST API and memory brain
2. **Frontend** — React 18 dashboard
3. **Sync Agent** — local Java daemon that bridges the server with `~/.claude/`

---

## Component Diagram

```
╔══════════════════════════════════════════════════════════════════════╗
║                         REMOTE SERVER                               ║
║                                                                      ║
║  ┌─────────────┐    ┌──────────────────────────────────────────┐    ║
║  │             │    │            Spring Boot 3.3               │    ║
║  │   nginx     │    │                                          │    ║
║  │  :80/:3010  │───▶│  ┌──────────┐  ┌─────────┐  ┌───────┐  │    ║
║  │             │    │  │  Memory  │  │ Lessons │  │ Dream │  │    ║
║  │  /api/ ─────│───▶│  │  Brain   │  │ Review  │  │ Cycle │  │    ║
║  │  /ws ───────│───▶│  └──────────┘  └─────────┘  └───────┘  │    ║
║  │  static ────│    │  ┌──────────┐  ┌─────────┐  ┌───────┐  │    ║
║  └─────────────┘    │  │ Claude   │  │  Sync   │  │  WS   │  │    ║
║                     │  │ Dir API  │  │   API   │  │ Feed  │  │    ║
║                     │  └────┬─────┘  └────┬────┘  └───────┘  │    ║
║                     └───────│─────────────│──────────────────-┘    ║
║                             │             │                         ║
║                    ┌────────▼──┐    ┌─────▼──────────────┐         ║
║                    │ /claude-  │    │  PostgreSQL (prod)  │         ║
║                    │  home vol │    │  H2 file (dev)      │         ║
║                    └───────────┘    └────────────────────-┘         ║
╚══════════════════════════════════════════════════════════════════════╝
                          ▲                 ▲
               HTTP/WS    │                 │  HTTP API
               (browser)  │        ┌────────┴─────────────────┐
                          │        │     SYNC AGENT (laptop)  │
╔══════════════════════════════════╡         :7701             ├══════╗
║  YOUR LAPTOP                     │                           │      ║
║                                  │  ┌──────────────────┐    │      ║
║  Browser ──────────────────────▶ │  │  WatchService    │    │      ║
║                                  │  │  ~/.claude/ ──▶  │    │      ║
║  Claude Code ─── hooks ────────▶ │  │  push to server  │    │      ║
║                                  │  └──────────────────┘    │      ║
║  Copilot CLI ── aliases ───────▶ │  ┌──────────────────┐    │      ║
║                                  │  │  Local HTTP API  │    │      ║
║                                  │  │  /context        │    │      ║
║                                  │  │  /working        │    │      ║
║                                  │  │  /episodic       │    │      ║
║                                  └──┴──────────────────┘────┘      ║
╚══════════════════════════════════════════════════════════════════════╝
```

---

## Data Flow

### Memory Write (Claude Code → Server)

```
Claude Code hook fires
    │
    ▼
POST http://localhost:7701/episodic
    │
    ▼
SyncAgent LocalApiServer (:7701)
    │  proxies to →
    ▼
POST http://server:8080/api/memory/episodic
    │
    ▼
EpisodicMemoryService.add()
    │  persists to →
    ▼
episodic_memory table (PostgreSQL / H2)
    │  publishes →
    ▼
AgentEventPublisher → /topic/activity (WebSocket)
```

### Context Read (Claude Code → Server)

```
Claude Code hook fires (UserPromptSubmit)
    │
    ▼
GET http://localhost:7701/context?q=<task>
    │
    ▼
SyncAgent LocalApiServer
    │  proxies to →
    ▼
GET http://server:8080/api/context?q=<task>
    │
    ▼
ContextBudgetService.build(query)
    │  ├── WorkingMemoryService.getActive()
    │  ├── EpisodicMemoryService.getTopN(query, salience)
    │  └── LessonRepository.findByStatus(ACCEPTED)
    │
    ▼
Ranked Markdown snapshot (returned to Claude Code)
```

### File Sync (Laptop → Server)

```
User edits ~/.claude/CLAUDE.md
    │
    ▼
WatchService detects MODIFY event
    │
    ▼
SyncClient.push(relativePath, content)
    │  checks SyncState.hasChanged() →
    ▼
POST http://server:8080/api/sync/push
    Authorization: Bearer <token>
    { path, content, hash, timestamp }
    │
    ▼
SyncController → writes to /claude-home volume
    │
    ▼
SyncLog persisted to DB (for audit + pull tracking)
```

---

## Deployment Topology

### Single Machine (dev / local)

```
localhost:3000  → nginx → React SPA
localhost:8080  → Spring Boot
localhost:7701  → Sync Agent (separate process)
~/.claude/      → mounted as /claude-home (read-write)
```

### Dedicated Server (prod)

```
192.168.x.x:3010  → nginx → React SPA
192.168.x.x:8086  → Spring Boot
postgresql:5432   → internal Docker network only

Laptop:
localhost:7701    → Sync Agent → pushes to server:8086
```

---

## Technology Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Persistence | H2 (dev) / PostgreSQL (prod) | Zero-config dev, production-grade prod via same Flyway migrations |
| ORM | Spring Data JPA + Hibernate | Familiar, well-supported, standard |
| Migrations | Flyway | Versioned, irreversible, audit trail |
| Real-time | STOMP over WebSocket | Works through nginx proxy; broad client support |
| Frontend build | Vite | Fast HMR, small bundle, first-class TypeScript |
| Styling | Tailwind CSS | Utility-first, dark theme consistent |
| State | TanStack Query | Cache invalidation, loading states, background refetch |
| Clustering | Jaccard single-linkage | No ML dependencies, deterministic, explainable |
| File sync | NIO WatchService | JVM built-in, no native deps, virtual threads |
| Containerization | Docker Compose | Simple, single-file ops, no Kubernetes overhead |
