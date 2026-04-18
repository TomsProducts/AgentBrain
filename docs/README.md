# AgentBrain Documentation

Welcome to the AgentBrain documentation. Use the index below to navigate.

---

## 📐 Architecture

| Document | Description |
|----------|-------------|
| [System Overview](architecture/overview.md) | High-level architecture, component diagram, data flow |
| [Memory Layers](architecture/memory-layers.md) | Working, Episodic, and Lesson memory design and lifecycle |
| [Dream Cycle](architecture/dream-cycle.md) | Nightly clustering algorithm — how memories become lessons |
| [Sync Protocol](architecture/sync-protocol.md) | How the local sync agent communicates with the server |

---

## 📡 API Reference

| Document | Description |
|----------|-------------|
| [REST API](api/rest-reference.md) | Full REST endpoint reference with request/response examples |
| [WebSocket Events](api/websocket.md) | STOMP event types, payloads, and subscription patterns |
| [Sync API](api/sync-api.md) | File push/pull protocol used by the sync agent |

---

## 🚀 Setup & Deployment

| Document | Description |
|----------|-------------|
| [Local Development](setup/local-development.md) | Run AgentBrain on your laptop with H2 |
| [Server Deployment](setup/server-deployment.md) | Deploy to a dedicated server with PostgreSQL |
| [Sync Agent](setup/sync-agent.md) | Install and configure the local file watcher daemon |
| [Claude Code Integration](setup/claude-code.md) | Hooks, CLAUDE.md protocol, session memory workflow |
| [Copilot CLI Integration](setup/copilot-cli.md) | Shell aliases, inject script, memory workflow |

---

## ⚙️ Configuration

| Document | Description |
|----------|-------------|
| [Environment Variables](configuration/environment-variables.md) | All `.env` and Docker Compose variables |
| [Application Config](configuration/application-yml.md) | Spring Boot `application.yml` full reference |

---

## 📖 Reference

| Document | Description |
|----------|-------------|
| [Memory Protocol](reference/memory-protocol.md) | What to log, when, format, and quality guidelines |
| [Troubleshooting](reference/troubleshooting.md) | Common issues, error messages, and fixes |
