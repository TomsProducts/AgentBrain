# Environment Variables

All configuration is done through environment variables. In Docker Compose, these are read from the `.env` file in the project root.

---

## `.env` File

Copy `.env.example` to `.env` and fill in values:

```bash
cp .env.example .env
```

---

## Variables Reference

### Required

| Variable | Description | Example |
|----------|-------------|---------|
| `SYNC_TOKEN` | Bearer token for sync API. Must match between server and all sync agents. Generate with `openssl rand -hex 32`. | `a3f1e2b4c5d6...` |

### Optional — Ports

| Variable | Default | Description |
|----------|---------|-------------|
| `BACKEND_PORT` | `8080` | Host port for the Spring Boot backend |
| `FRONTEND_PORT` | `3000` | Host port for the nginx frontend |

Change these if the defaults are occupied:

```dotenv
BACKEND_PORT=8086
FRONTEND_PORT=3010
```

### Optional — Claude Home

| Variable | Default | Description |
|----------|---------|-------------|
| `CLAUDE_HOME` | `~/.claude` | Path to the `~/.claude` directory on the host, mounted into the backend container as `/claude-home` |

```dotenv
# Local machine
CLAUDE_HOME=/Users/youruser/.claude

# Dedicated server (another user's home)
CLAUDE_HOME=/home/thomas/.claude
```

### Production Only — Database

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_USER` | — | PostgreSQL username |
| `DB_PASSWORD` | — | PostgreSQL password |

These are only used when running with `docker-compose.prod.yml`.

```dotenv
DB_USER=agentbrain
DB_PASSWORD=your-secure-password
```

---

## Docker Compose Variable Expansion

The compose files use `${VAR:-default}` syntax:

```yaml
# docker-compose.yml
ports:
  - "${BACKEND_PORT:-8080}:8080"
  - "${FRONTEND_PORT:-3000}:80"

volumes:
  - ${CLAUDE_HOME:-~/.claude}:/claude-home
```

If a variable is not set in `.env`, the default after `:-` is used.

---

## Spring Boot Environment Variables

Spring Boot also reads environment variables directly. These can be set in the `environment:` section of `docker-compose.prod.yml` or passed on the command line:

| Spring Property | Env Variable | Default |
|-----------------|--------------|---------|
| `agentbrain.claude-dir` | `AGENTBRAIN_CLAUDE_DIR` | `/claude-home` |
| `agentbrain.dream.cron` | `AGENTBRAIN_DREAM_CRON` | `0 0 3 * * *` |
| `agentbrain.memory.working-ttl-hours` | `AGENTBRAIN_MEMORY_WORKING_TTL_HOURS` | `24` |
| `agentbrain.memory.episodic-ttl-days` | `AGENTBRAIN_MEMORY_EPISODIC_TTL_DAYS` | `90` |
| `agentbrain.memory.context-budget-limit` | `AGENTBRAIN_MEMORY_CONTEXT_BUDGET_LIMIT` | `20` |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | H2 file URL |
| `DB_USER` | `DB_USER` | — |
| `DB_PASSWORD` | `DB_PASSWORD` | — |

---

## Sync Agent Variables (`~/.agentbrain/application.properties`)

The sync agent uses Spring Boot's property file loading, not `.env`.

| Property | Description | Example |
|----------|-------------|---------|
| `server.url` | AgentBrain server base URL (no trailing slash) | `http://192.168.68.111:8086` |
| `server.token` | Must match server's `SYNC_TOKEN` | `a3f1e2b4c5d6...` |
| `claude.home` | Absolute path to `~/.claude/` on this machine | `/Users/youruser/.claude` |
| `local.api.port` | Port for Claude Code hooks to connect to | `7701` |
| `sync.pull-interval-seconds` | How often to poll server for WebUI changes | `30` |
| `logging.level.io.agentbrain` | Log verbosity | `INFO` |
