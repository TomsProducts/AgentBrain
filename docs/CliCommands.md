# 🧠 AgentBrain CLI Command Reference

All commands are shell functions / aliases defined in `~/.zshrc`.  
They communicate with the **local sync agent** on `http://localhost:7701`, which proxies requests to the AgentBrain server.

> **Prerequisite:** The sync agent must be running.  
> Check with `brain-ping`. If it's not running, start it:
> ```bash
> cd ~/.agentbrain && java -jar agentbrain-sync.jar &
> ```

---

## 📋 Quick Reference

| Command | Description |
|---------|-------------|
| [`brain-context <query>`](#brain-context) | Fetch ranked memory + accepted lessons for a task |
| [`brain-note <text>`](#brain-note) | Log an episodic memory entry |
| [`brain-working <text>`](#brain-working) | Log a short-lived working memory (24h TTL) |
| [`brain-sync`](#brain-sync) | Trigger immediate file sync to the server |
| [`brain-ping`](#brain-ping) | Check sync agent health and server connectivity |
| [`brain-open`](#brain-open) | Open AgentBrain dashboard in the browser |
| [`brain-help`](#brain-help) | Print all available commands |

---

## Command Details

### `brain-context`

Fetch memory context ranked by relevance to your query. Returns episodic memories and accepted lessons formatted as Markdown — ready to paste into a prompt or read before starting a task.

```bash
brain-context <query>
```

**Examples:**
```bash
brain-context "fixing flyway migration"
brain-context "docker compose deployment"
brain-context "spring boot startup"
brain-context                          # fetch all context (no filter)
```

**Output format:**
```
# 🧠 AgentBrain Context — fixing flyway migration

## 📼 Relevant Episodes
- **Fixed Flyway migration error by adding flyway-database-postgresql...**  `java,flyway`

## 🎓 Accepted Lessons
- Flyway 10+ requires explicit flyway-database-postgresql dependency
  > Always add flyway-database-postgresql when using Flyway 10+ with PostgreSQL
```

**Underlying API:** `GET http://localhost:7701/context?q=<encoded-query>`  
**Server API:** `GET http://<server>:8086/api/context?q=<query>`

---

### `brain-note`

Log a permanent episodic memory entry. Tagged as `copilot,manual`. Episodic memories are visible in the AgentBrain dashboard and can be graduated into lessons via the Review Queue.

```bash
brain-note <text>
```

**Examples:**
```bash
brain-note "Fixed CORS issue by setting allowedOriginPatterns to wildcard in Spring Boot"
brain-note "Nginx proxy requires proxy_http_version 1.1 for WebSocket upgrade"
brain-note "Remember: docker compose v2 uses 'docker compose' not 'docker-compose'"
```

**Output:**
```
✅ Logged to AgentBrain: id=12
```

**Underlying API:** `POST http://localhost:7701/episodic`  
**Payload:** `{ "content": "<text>", "tags": "copilot,manual" }`

---

### `brain-working`

Log a short-lived working memory entry. Automatically expires after **24 hours**. Use this for temporary context like the current task, current branch, or active problem being solved.

```bash
brain-working <text>
```

**Examples:**
```bash
brain-working "Currently fixing WebSocket reconnect bug on /activity page"
brain-working "Working on PR #42 — refactor ContextBudgetService salience scoring"
brain-working "Deployed to staging at 192.168.68.111, testing CORS fix"
```

**Output:**
```
✅ Working memory id=5
```

**Underlying API:** `POST http://localhost:7701/working`  
**Payload:** `{ "content": "<text>", "tags": "copilot" }`

---

### `brain-sync`

Trigger an immediate full sync of your local `~/.claude/` directory to the AgentBrain server. Normally sync happens automatically on file changes and via the Claude Code hooks — use this to force it manually.

```bash
brain-sync
```

**Output:**
```
✅ sync triggered
```

**Underlying API:** `POST http://localhost:7701/sync-trigger`

---

### `brain-ping`

Check if the local sync agent is alive and connected to the server.

```bash
brain-ping
```

**Output:**
```json
{
    "serverUrl": "http://192.168.68.111:8086",
    "status": "ok"
}
```

**Underlying API:** `GET http://localhost:7701/ping`

---

### `brain-open`

Open the AgentBrain web dashboard in your default browser.

```bash
brain-open
```

Opens: `http://192.168.68.111:3010`

---

### `brain-help`

Print a summary of all available commands.

```bash
brain-help
```

**Output:**
```
🧠 AgentBrain CLI Commands
  brain-context <query>  Fetch ranked memory + lessons for a task
  brain-note <text>      Log an episodic memory entry
  brain-working <text>   Log a short-lived working memory (24h TTL)
  brain-sync             Trigger immediate file sync to server
  brain-ping             Check local sync agent status
  brain-open             Open AgentBrain dashboard in browser
```

---

## 🔌 Direct REST API (curl)

You can also call the local sync agent or server directly via `curl`:

### Local Sync Agent (`localhost:7701`)

```bash
# Health check
curl http://localhost:7701/ping

# Fetch context (returns Markdown)
curl "http://localhost:7701/context?q=your+query"

# Add episodic memory
curl -X POST http://localhost:7701/episodic \
  -H "Content-Type: application/json" \
  -d '{"content": "your memory", "tags": "tag1,tag2"}'

# Add working memory
curl -X POST http://localhost:7701/working \
  -H "Content-Type: application/json" \
  -d '{"content": "temporary note", "tags": "working"}'

# Trigger sync
curl -X POST http://localhost:7701/sync-trigger
```

### Server REST API (`192.168.68.111:8086`)

```bash
# --- Memory ---
GET  /api/memory/working              # list working memory
POST /api/memory/working              # { content, tags }
DELETE /api/memory/working/{id}

GET  /api/memory/episodic?page=0      # paginated episodic memory
POST /api/memory/episodic             # { content, tags }

GET  /api/memory/search?q=<text>      # full-text search all layers

GET  /api/context?q=<text>            # ranked context budget (JSON)

# --- Lessons ---
GET  /api/lessons?status=STAGED       # list by status: STAGED|ACCEPTED|REJECTED
POST /api/lessons/{id}/graduate       # { rationale }  (required, min 10 chars)
POST /api/lessons/{id}/reject         # { reason }
POST /api/lessons/{id}/reopen

# --- Dream Cycle ---
POST /api/dream/run                   # manual trigger
GET  /api/dream/last                  # last run result

# --- Claude Dir Browser ---
GET  /api/claude/tree                 # full ~/.claude/ file tree
GET  /api/claude/file?path=           # read file content
PUT  /api/claude/file?path=           # { content } — write file
DELETE /api/claude/file?path=         # delete file
POST /api/claude/file?path=           # { content } — create new file

# --- Health ---
GET  /actuator/health
```

---

## ⚙️ Configuration

Shell variables set in `~/.zshrc`:

| Variable | Value | Description |
|----------|-------|-------------|
| `BRAIN_LOCAL` | `http://localhost:7701` | Local sync agent address |
| `BRAIN_UI` | `http://192.168.68.111:3010` | AgentBrain web dashboard |

Sync agent config in `~/.agentbrain/application.properties`:

| Property | Description |
|----------|-------------|
| `server.url` | AgentBrain server base URL |
| `sync.token` | Authentication token |
| `sync.local-api-port` | Local API port (default: 7701) |
| `sync.claude-home` | Path to `~/.claude/` to watch |
| `sync.watch-interval-seconds` | File watch polling interval |

---

## 🔄 Automatic Hooks (Claude Code)

These fire automatically — no manual commands needed:

| Hook | Trigger | Action |
|------|---------|--------|
| `UserPromptSubmit` | Every prompt | Fetches context from AgentBrain and injects it |
| `Stop` | Session end | Logs episodic memory with session summary |
| `PostToolUse` | Write/Edit/MultiEdit | Triggers file sync to server |

Hooks are defined in `~/.claude/settings.json`.

---

## 🚀 Typical Workflow

```bash
# 1. Before starting a task — load relevant memory
brain-context "implement OAuth2 login"

# 2. During work — log important discoveries
brain-note "Spring Security 6 requires SecurityFilterChain bean, not WebSecurityConfigurerAdapter"

# 3. Note current task in working memory
brain-working "Implementing OAuth2 login on feature/oauth branch"

# 4. After a major file change — sync to server
brain-sync

# 5. Open dashboard to review and graduate staged lessons
brain-open
```

---

*Sync agent auto-starts on login via macOS LaunchAgent: `~/Library/LaunchAgents/io.agentbrain.sync.plist`*
