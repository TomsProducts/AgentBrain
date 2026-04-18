<div align="center">

<img src="https://raw.githubusercontent.com/TomsProducts/AgentBrain/main/.github/banner.svg" alt="AgentBrain" width="100%" />

# AgentBrain

**Persistent memory and configuration management for your AI coding agents.**  
Self-hosted · Spring Boot 3 · React 18 · Docker Compose

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB?style=flat-square&logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?style=flat-square&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/License-MIT-a855f7?style=flat-square)](LICENSE)

</div>

---

## What is AgentBrain?

AgentBrain is a self-hosted web application that gives **Claude Code** and **GitHub Copilot CLI** a persistent brain — memory that survives across sessions, a nightly dream cycle that distills lessons from your work, and a full file browser for your `~/.claude/` configuration directory.

Run it on a dedicated server or your local machine. Everything is in Docker Compose — no Kubernetes, no cloud dependencies.

---

## Features

### 🧠 Memory Layers

| Layer | Lifetime | Purpose |
|-------|----------|---------|
| **Working Memory** | 24 hours | Current task notes, auto-expire |
| **Episodic Memory** | 90 days | Session history with salience decay |
| **Lessons** | Permanent | Accepted patterns, reviewed by you |

### 🌙 Dream Cycle
Every night at 03:00, AgentBrain clusters your recent episodic memories using **Jaccard similarity**, extracts candidate lessons, and stages them for your review — no LLM calls, pure mechanical pattern extraction.

### 📋 Lesson Review Board
STAGED → ACCEPTED / REJECTED / REOPENED state machine. Graduate a lesson by providing a rationale; rejected lessons can be reopened. Accepted lessons are included in every context budget response.

### 📁 Claude Config Browser
Browse and edit your entire `~/.claude/` directory from a web UI — CLAUDE.md, settings.json, skills, agents, commands, hooks, and rules. Live save back to disk. Read-only guard on `projects/` and `history.jsonl`.

### 🔄 Sync Agent
A lightweight Java daemon that watches your local `~/.claude/` and syncs changes bidirectionally with the server — so your config is always backed up and available from any machine.

### ⚡ Live Activity Feed
WebSocket-powered real-time event stream: lesson graduations, dream cycle completions, memory writes, errors — all with color-coded severity and type filtering.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Your Browser                         │
│              http://server-ip:3010                          │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP + WebSocket
┌──────────────────────────▼──────────────────────────────────┐
│                      nginx (port 80)                        │
│               Static files + /api/ proxy                    │
└──────────┬───────────────────────────────────────┬──────────┘
           │ /api/*                                │ /ws
┌──────────▼───────────────────────────────────────▼──────────┐
│              Spring Boot 3.3  (port 8080)                   │
│  ┌────────────┐  ┌────────────┐  ┌──────────┐  ┌────────┐  │
│  │  Memory    │  │   Lessons  │  │  Claude  │  │  Sync  │  │
│  │  Brain API │  │  Review    │  │  Dir API │  │  API   │  │
│  └─────┬──────┘  └─────┬──────┘  └────┬─────┘  └───┬────┘  │
│        └───────────────┴──────────────┘            │       │
│                   ┌────▼─────┐                     │       │
│                   │ H2 / PG  │          /claude-home volume │
│                   └──────────┘                     │       │
└────────────────────────────────────────────────────│───────┘
                                                     │
┌────────────────────────────────────────────────────▼───────┐
│                  Sync Agent (laptop)  :7701                 │
│          Watches ~/.claude/ → pushes to server              │
│          Serves context budget to Claude Code hooks         │
└────────────────────────────────────────────────────────────┘
```

---

## Quick Start

### Local Development (H2 database)

```bash
git clone https://github.com/TomsProducts/AgentBrain.git
cd AgentBrain

cp .env.example .env
# Edit .env — set SYNC_TOKEN to a random secret

docker compose up --build
```

| Service | URL |
|---------|-----|
| Dashboard | http://localhost:3000 |
| API | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |

### Production Server (PostgreSQL)

```bash
cp .env.example .env
# Edit .env:
#   CLAUDE_HOME=/home/youruser/.claude
#   DB_USER=agentbrain
#   DB_PASSWORD=$(openssl rand -hex 16)
#   SYNC_TOKEN=$(openssl rand -hex 32)

docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

---

## Sync Agent Setup

The sync agent is a small Java process that runs on your laptop alongside Claude Code.

```bash
# Build
cd sync-agent
mvn package -DskipTests

# Configure ~/.agentbrain/application.properties
server.url=http://your-server-ip:8080
server.token=your-sync-token
claude.home=/Users/youruser/.claude

# Run
java -jar target/agentbrain-sync-*.jar
```

**macOS auto-start** — copy the launchd plist from `DEPLOY.md` to `~/Library/LaunchAgents/`.

---

## Connecting Claude Code

Add to your `~/.claude/CLAUDE.md`:

```markdown
## Memory Protocol

**SESSION START** — Run this and read the output before anything else:
  curl -s "http://localhost:7701/context?q=<task description>"

**AFTER IMPORTANT EVENTS** — Log discoveries and decisions:
  curl -s -X POST http://localhost:7701/episodic \
    -H "Content-Type: application/json" \
    -d '{"content": "what happened and why", "tags": "tag1,tag2"}'

**SESSION END** — Push a working memory summary:
  curl -s -X POST http://localhost:7701/working \
    -H "Content-Type: application/json" \
    -d '{"content": "session summary"}'
```

Add hooks to `~/.claude/settings.json` so sync fires automatically on file writes — see [DEPLOY.md](DEPLOY.md#step-4--configure-claude-code-hooks) for the full hook config.

---

## Dashboard Pages

| Page | Route | Description |
|------|-------|-------------|
| **Dashboard** | `/` | Stats, live activity strip, quick links |
| **Memory** | `/memory` | Working · Episodic · Search tabs |
| **Lessons** | `/lessons` | Review queue + accepted lessons table |
| **Claude Config** | `/claude` | File tree editor for `~/.claude/` |
| **Activity Log** | `/activity` | Full real-time WebSocket event feed |

---

## API Reference

<details>
<summary>Expand full API reference</summary>

```
# Memory
GET    /api/memory/working              List working memories
POST   /api/memory/working              { content, tags }
DELETE /api/memory/working/{id}

GET    /api/memory/episodic             ?page=0&size=20
POST   /api/memory/episodic             { content, tags }

GET    /api/memory/search?q=            Search all layers
GET    /api/context?q=                  Context budget — top-N episodes + accepted lessons

# Lessons
GET    /api/lessons                     ?status=STAGED|ACCEPTED|REJECTED
POST   /api/lessons/{id}/graduate       { rationale }  — 400 if blank
POST   /api/lessons/{id}/reject         { reason }
POST   /api/lessons/{id}/reopen

# Dream Cycle
POST   /api/dream/run                   Trigger manually
GET    /api/dream/last                  Last run result + candidate count

# Claude Config Browser
GET    /api/claude/tree                 Full ~/.claude/ directory tree
GET    /api/claude/file?path=           Read file content
PUT    /api/claude/file?path=           Write file  { content }
POST   /api/claude/file?path=           Create file { content }
DELETE /api/claude/file?path=           Delete file

# Sync
POST   /api/sync/push                   Push file changes (Bearer token required)
GET    /api/sync/pending?since=         Poll pending server-side changes
GET    /api/sync/snapshot               Full file hash snapshot

# Meta
GET    /api/stats                       Dashboard counters
GET    /actuator/health                 Health check

# WebSocket (STOMP)
WS     /ws
       /topic/activity                 Live event feed
```

</details>

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.3, Spring Data JPA, Spring WebSocket |
| Database | H2 (dev) / PostgreSQL 16 (prod) via Flyway migrations |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, TanStack Query |
| UI Components | Radix UI, Lucide React |
| Real-time | STOMP over WebSocket |
| Infrastructure | Docker Compose (no Kubernetes) |
| Sync Agent | Java 21, NIO WatchService, virtual threads |

---

## Project Structure

```
AgentBrain/
├── backend/                    # Spring Boot 3 API
│   └── src/main/java/io/agentbrain/
│       ├── memory/             # Working · Episodic · Lessons · Dream cycle
│       ├── claudedir/          # ~/.claude/ file browser
│       ├── sync/               # Sync API (push/pull)
│       ├── events/             # WebSocket activity feed
│       └── config/             # CORS · WebSocket · App config
├── frontend/                   # React 18 dashboard
│   └── src/
│       ├── pages/              # Dashboard · Memory · Lessons · Claude · Activity
│       ├── api/                # Typed API client
│       └── hooks/              # useActivityLog (WebSocket)
├── sync-agent/                 # Local file watcher daemon
│   └── src/main/java/io/agentbrain/sync/
│       ├── watcher/            # NIO WatchService → push
│       ├── sync/               # SyncClient · SyncState
│       └── server/             # Local HTTP API for Claude Code hooks
├── docker-compose.yml          # Dev (H2)
├── docker-compose.prod.yml     # Prod (PostgreSQL)
├── .env.example
└── DEPLOY.md                   # Full deployment guide
```

---

## License

MIT © [TomsProducts](https://github.com/TomsProducts)

