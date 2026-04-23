# AgentBrain — Deployment & Integration Guide

> **Two setups are covered throughout this document:**
> - 🖥️ **Local** — AgentBrain runs on your laptop alongside Claude Code / Copilot CLI
> - 🌐 **Remote** — AgentBrain runs on a dedicated server; you access it from your laptop over LAN/VPN/internet

---

## Table of Contents

1. [Deploy AgentBrain](#1-deploy-agentbrain)
   - [1A. Local (laptop)](#1a-local-laptop)
   - [1B. Remote (dedicated server)](#1b-remote-dedicated-server)
2. [Install the Sync Agent on Your Laptop](#2-install-the-sync-agent-on-your-laptop)
   - [2A. Local setup — sync is optional](#2a-local-setup--sync-is-optional)
   - [2B. Remote setup — sync is required](#2b-remote-setup--sync-is-required)
3. [Claude Code Integration](#3-claude-code-integration)
   - [3A. Claude Code on your laptop (local AgentBrain)](#3a-claude-code-on-your-laptop--local-agentbrain)
   - [3B. Claude Code on your laptop (remote AgentBrain)](#3b-claude-code-on-your-laptop--remote-agentbrain)
   - [3C. Claude Code via SSH on the server](#3c-claude-code-via-ssh-on-the-server)
4. [GitHub Copilot CLI Integration](#4-github-copilot-cli-integration)
   - [4A. Copilot CLI on your laptop (local AgentBrain)](#4a-copilot-cli-on-your-laptop--local-agentbrain)
   - [4B. Copilot CLI on your laptop (remote AgentBrain)](#4b-copilot-cli-on-your-laptop--remote-agentbrain)
   - [4C. Copilot CLI via SSH on the server](#4c-copilot-cli-via-ssh-on-the-server)
5. [Quick Reference](#5-quick-reference)

---

## 1. Deploy AgentBrain

### 1A. Local (laptop)

Runs entirely on your machine. Uses H2 (embedded database — no Postgres needed). Good for solo use or trying it out.

```bash
git clone <this-repo>
cd agentbrain

cp .env.example .env
# Add a sync token (needed even locally if you run the sync agent):
echo "SYNC_TOKEN=$(openssl rand -hex 32)" >> .env

docker compose up --build -d
```

| Service | URL |
|---------|-----|
| Frontend dashboard | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Health check | http://localhost:8080/actuator/health |

The `claude-store` Docker volume starts empty. It gets populated automatically when the sync agent runs its first full sync (see [Section 2A](#2a-local-setup--sync-is-optional)).

**Stopping:**
```bash
docker compose down
```

**Updating:**
```bash
git pull && docker compose up --build -d
```

---

### 1B. Remote (dedicated server)

Runs on a server you access over the network. Uses PostgreSQL for durability. Multiple people or machines can connect to the same brain.

**On the server:**

```bash
git clone <this-repo>
cd agentbrain

cp .env.example .env
```

Edit `.env` on the server:

```env
DB_USER=agentbrain
DB_PASSWORD=your-secure-password
SYNC_TOKEN=<run: openssl rand -hex 32>
```

Start with PostgreSQL:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

Verify it is up:

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP","components":{"db":{"status":"UP"},...}}
```

**From your laptop**, open:

```
http://YOUR_SERVER_IP:3000
```

> **Firewall:** Open ports `3000` (frontend) and `8080` (API) on your server. If on a VPN or LAN, restrict them to your network only.

**Stopping / updating:**
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml down
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

---

## 2. Install the Sync Agent on Your Laptop

The sync agent is a lightweight Java 21 daemon (`~2 MB jar`) that runs on your laptop and:

- Watches `~/.claude/` for file changes → pushes them to the server
- Polls the server every 2 seconds for changes made in the WebUI → writes them locally
- Exposes `http://localhost:7701` so Claude Code hooks can trigger a sync

### Build the jar (one time)

```bash
cd agentbrain/sync-agent

# Requires Java 21+ and Maven
mvn package -DskipTests

# Copy to permanent location
mkdir -p ~/.agentbrain
cp target/agentbrain-sync-*.jar ~/.agentbrain/agentbrain-sync.jar
```

---

### 2A. Local setup — sync is optional

When AgentBrain runs on your laptop, the `claude-store` volume and your `~/.claude/` are both local. You can either:

**Option 1 — Skip the sync agent** and mount `~/.claude/` directly into the backend container. Edit `docker-compose.yml`:

```yaml
services:
  backend:
    volumes:
      - ./data:/app/data
      - ${HOME}/.claude:/claude-store   # direct mount — no sync agent needed
```

Then restart:
```bash
docker compose up -d
```

The WebUI now reads and writes your real `~/.claude/` directly.

**Option 2 — Run the sync agent anyway** (recommended if you also use a remote server later). Run the setup wizard pointing at localhost:

```bash
java -jar ~/.agentbrain/agentbrain-sync.jar setup
```

| Prompt | Value for local setup |
|--------|-----------------------|
| Server URL | `http://localhost:8080` |
| Sync token | Value of `SYNC_TOKEN` from your local `.env` |
| Claude home | Press Enter (defaults to `~/.claude`) |

Start the daemon:

```bash
java -jar ~/.agentbrain/agentbrain-sync.jar
```

Verify:
```bash
curl http://localhost:7701/health
# → {"ok":true}
```

---

### 2B. Remote setup — sync is required

When AgentBrain is on a remote server, the sync agent is the only way to keep your local `~/.claude/` in sync with the server's `claude-store` volume.

#### Setup wizard

```bash
java -jar ~/.agentbrain/agentbrain-sync.jar setup
```

| Prompt | Value for remote setup |
|--------|-----------------------|
| Server URL | `http://YOUR_SERVER_IP:8080` |
| Sync token | Value of `SYNC_TOKEN` from the server's `.env` file |
| Claude home | Press Enter (defaults to `~/.claude`) |

This writes `~/.agentbrain/sync-agent.properties`. To inspect or edit it:

```
~/.agentbrain/sync-agent.properties
```

```properties
server.url=http://YOUR_SERVER_IP:8080
sync.token=your-token-here
claude.home=/Users/you/.claude
watch.interval.ms=500
local.api.port=7701
```

#### Start the daemon

```bash
java -jar ~/.agentbrain/agentbrain-sync.jar
```

On first run it performs a **full sync**: pushes all your local `~/.claude/` files to the server and pulls anything on the server you don't have locally.

Verify:
```bash
curl http://localhost:7701/health
# → {"ok":true}

# Trigger a manual full sync:
curl http://localhost:7701/sync/trigger
# → {"ok":true,"synced":"triggered"}
```

#### Auto-start on macOS (launchd)

```bash
# Replace YOUR_USERNAME with: whoami
cat > ~/Library/LaunchAgents/io.agentbrain.sync.plist << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>io.agentbrain.sync</string>
    <key>ProgramArguments</key>
    <array>
        <string>/usr/bin/java</string>
        <string>-jar</string>
        <string>/Users/$(whoami)/.agentbrain/agentbrain-sync.jar</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>/Users/$(whoami)/.agentbrain/sync.log</string>
    <key>StandardErrorPath</key>
    <string>/Users/$(whoami)/.agentbrain/sync-error.log</string>
</dict>
</plist>
EOF

launchctl load ~/Library/LaunchAgents/io.agentbrain.sync.plist
launchctl start io.agentbrain.sync

# Verify
curl http://localhost:7701/health
```

To view logs: `tail -f ~/.agentbrain/sync.log`

To stop: `launchctl stop io.agentbrain.sync`

#### Auto-start on Linux (systemd)

```bash
mkdir -p ~/.config/systemd/user

cat > ~/.config/systemd/user/agentbrain-sync.service << EOF
[Unit]
Description=AgentBrain Sync Agent
After=network.target

[Service]
ExecStart=/usr/bin/java -jar /home/$(whoami)/.agentbrain/agentbrain-sync.jar
Restart=always
RestartSec=5
StandardOutput=append:/home/$(whoami)/.agentbrain/sync.log
StandardError=append:/home/$(whoami)/.agentbrain/sync-error.log

[Install]
WantedBy=default.target
EOF

systemctl --user daemon-reload
systemctl --user enable agentbrain-sync
systemctl --user start agentbrain-sync

# Verify
curl http://localhost:7701/health
```

To view logs: `journalctl --user -u agentbrain-sync -f`

---

## 3. Claude Code Integration

### How the memory flow works

```
Claude Code session starts
        │
        ▼ (UserPromptSubmit hook)
curl localhost:7701/sync/trigger   ← sync agent pulls latest from server
        │
        ▼
Claude reads ~/.claude/CLAUDE.md  ← includes instruction to call context API
        │
        ▼
curl /api/context?q=<task>        ← AgentBrain returns episodes + accepted lessons
        │
        ▼
Claude works with memory context
        │
        ▼ (Stop hook)
curl localhost:7701/sync/trigger   ← any files Claude wrote are pushed to server
```

---

### 3A. Claude Code on your laptop — local AgentBrain

AgentBrain is at `http://localhost:8080`. Everything is local.

#### Step 1 — Add memory instructions to `~/.claude/CLAUDE.md`

Open `~/.claude/CLAUDE.md` (create it if it does not exist) and add:

```markdown
## AgentBrain Memory

At the start of every session, fetch your memory context:

```bash
curl -s "http://localhost:8080/api/context?q=<brief description of current task>" | python3 -m json.tool
```

The response has two fields:
- `episodes` — recent session history ranked by salience (most relevant to your task first)
- `lessons` — accepted patterns you have learned and approved across all past sessions

Use this context before doing anything. Do not repeat mistakes listed in lessons.

After completing meaningful work, record it as an episodic memory:

```bash
curl -s -X POST http://localhost:8080/api/memory/episodic \
  -H "Content-Type: application/json" \
  -d '{"content": "<what you did and learned>", "tags": "<project,topic>"}'
```
```

#### Step 2 — Add hooks to `~/.claude/settings.json`

Create or edit `~/.claude/settings.json`:

```json
{
  "hooks": {
    "UserPromptSubmit": [
      {
        "matcher": "",
        "hooks": [
          {
            "type": "command",
            "command": "curl -s http://localhost:7701/sync/trigger || true",
            "timeout": 10
          }
        ]
      }
    ],
    "Stop": [
      {
        "matcher": "",
        "hooks": [
          {
            "type": "command",
            "command": "curl -s http://localhost:7701/sync/trigger || true",
            "timeout": 10
          }
        ]
      }
    ]
  }
}
```

> If the sync agent is not running, `|| true` makes the hook silently succeed — Claude continues normally.

#### Step 3 — Verify

```bash
claude
# Inside Claude Code session:
# "Fetch my AgentBrain context for the task: debugging auth issues"
```

Claude will call `http://localhost:8080/api/context?q=debugging+auth+issues` and present your memory.

---

### 3B. Claude Code on your laptop — remote AgentBrain

AgentBrain is at `http://YOUR_SERVER_IP:8080`. Your laptop runs Claude Code and the sync agent.

Same steps as [3A](#3a-claude-code-on-your-laptop--local-agentbrain) but change **every** `localhost:8080` to `YOUR_SERVER_IP:8080`:

**`~/.claude/CLAUDE.md`:**
```markdown
## AgentBrain Memory

At the start of every session, fetch your memory context:

```bash
curl -s "http://YOUR_SERVER_IP:8080/api/context?q=<task description>" | python3 -m json.tool
```

After completing meaningful work, record it:

```bash
curl -s -X POST http://YOUR_SERVER_IP:8080/api/memory/episodic \
  -H "Content-Type: application/json" \
  -d '{"content": "<what happened>", "tags": "<project,topic>"}'
```
```

**`~/.claude/settings.json`** — hooks stay as `localhost:7701` (the sync agent is always local):

```json
{
  "hooks": {
    "UserPromptSubmit": [
      {
        "matcher": "",
        "hooks": [
          {
            "type": "command",
            "command": "curl -s http://localhost:7701/sync/trigger || true",
            "timeout": 10
          }
        ]
      }
    ],
    "Stop": [
      {
        "matcher": "",
        "hooks": [
          {
            "type": "command",
            "command": "curl -s http://localhost:7701/sync/trigger || true",
            "timeout": 10
          }
        ]
      }
    ]
  }
}
```

The sync agent handles keeping your `~/.claude/` and the server's `claude-store` in sync automatically.

---

### 3C. Claude Code via SSH on the server

When you SSH into the server and run `claude` there, it reads from `/claude-store/` — the named Docker volume kept current by the sync agent on your laptop. **No extra setup needed on the server.**

```bash
ssh user@YOUR_SERVER_IP
cd /your-project
claude
```

Inside the Claude Code session on the server, use `localhost:8080` (AgentBrain runs on the same machine):

```markdown
## AgentBrain Memory
Fetch context: curl -s "http://localhost:8080/api/context?q=<task>"
```

The server does not need the sync agent or hooks because:
- It reads directly from `/claude-store/` (the Docker volume)
- Your laptop's sync agent keeps that volume up to date

> **Note:** Files that Claude writes on the server (e.g. edits to CLAUDE.md) will be picked up by your laptop's sync agent on its next poll cycle (within 2 seconds).

---

## 4. GitHub Copilot CLI Integration

Copilot CLI reads project-level instructions from `.github/copilot-instructions.md`. Memory is injected into that file or fetched on demand during a session.

---

### 4A. Copilot CLI on your laptop — local AgentBrain

AgentBrain is at `http://localhost:8080`.

#### Option 1 — Static memory block in `copilot-instructions.md`

Add this to your project's `.github/copilot-instructions.md`:

```markdown
## AgentBrain Memory

This project uses AgentBrain for persistent AI memory at http://localhost:8080.

At the start of any significant task, fetch context:
```bash
curl -s "http://localhost:8080/api/context?q=<task description>"
```

After completing work, record it:
```bash
curl -s -X POST http://localhost:8080/api/memory/episodic \
  -H "Content-Type: application/json" \
  -d '{"content": "<summary of what was done>", "tags": "<project,topic>"}'
```

Accepted lessons (reviewed patterns from past sessions) are included in the context response under the `lessons` key.
```

#### Option 2 — Auto-inject accepted lessons before each session

Create the inject script:

```bash
cat > ~/.agentbrain/inject-copilot-memory.sh << 'SCRIPT'
#!/bin/bash
# Usage: inject-copilot-memory.sh [project-dir] [agentbrain-url]
# Fetches accepted lessons from AgentBrain and injects them into copilot-instructions.md

PROJECT_DIR="${1:-$(pwd)}"
API="${2:-http://localhost:8080}"
INSTRUCTIONS="$PROJECT_DIR/.github/copilot-instructions.md"

if [ ! -f "$INSTRUCTIONS" ]; then
  echo "ERROR: $INSTRUCTIONS not found"
  exit 1
fi

LESSONS=$(curl -s "$API/api/lessons?status=ACCEPTED" 2>/dev/null)
if [ -z "$LESSONS" ] || [ "$LESSONS" = "[]" ]; then
  echo "No accepted lessons or AgentBrain not reachable at $API"
  exit 0
fi

LESSON_LIST=$(echo "$LESSONS" | python3 -c "
import json, sys
data = json.load(sys.stdin)
for l in data[:15]:
    print(f\"- {l['claim']}\")
" 2>/dev/null || echo "- (could not parse lessons)")

START="<!-- AGENTBRAIN:START -->"
END="<!-- AGENTBRAIN:END -->"

# Remove old injected block
TMP=$(mktemp)
awk "/$START/{found=1} !found{print} /$END/{found=0}" "$INSTRUCTIONS" > "$TMP"

# Append new block
cat >> "$TMP" << BLOCK

$START
## AgentBrain Lessons (auto-injected $(date '+%Y-%m-%d %H:%M'))

The following are reviewed and accepted patterns from past sessions:

$LESSON_LIST

> Manage lessons at $API (replace with your browser URL :3000)
$END
BLOCK

cp "$TMP" "$INSTRUCTIONS"
rm "$TMP"

COUNT=$(echo "$LESSONS" | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "?")
echo "Injected $COUNT lessons into $INSTRUCTIONS"
SCRIPT

chmod +x ~/.agentbrain/inject-copilot-memory.sh
```

Add a shell alias (add to `~/.zshrc` or `~/.bashrc`):

```bash
alias brain-sync='~/.agentbrain/inject-copilot-memory.sh "$(pwd)" "http://localhost:8080"'
```

**Workflow:**
```bash
cd your-project
brain-sync        # inject lessons into .github/copilot-instructions.md
gh copilot ...    # start Copilot CLI — it reads the updated instructions
```

#### Ask Copilot CLI for memory during a session

At any point in a Copilot CLI session:

```
Call http://localhost:8080/api/context?q="fixing the payment service" and use the returned memory context
```

```
Record in AgentBrain: "Fixed race condition in PaymentService by using synchronized block on the queue object" with tags "payments,concurrency"
```

---

### 4B. Copilot CLI on your laptop — remote AgentBrain

AgentBrain is at `http://YOUR_SERVER_IP:8080`. Everything is the same as [4A](#4a-copilot-cli-on-your-laptop--local-agentbrain) but use the server address.

**Static block in `copilot-instructions.md`:**

```markdown
## AgentBrain Memory

This project uses AgentBrain for persistent AI memory at http://YOUR_SERVER_IP:8080.

At the start of any significant task, fetch context:
```bash
curl -s "http://YOUR_SERVER_IP:8080/api/context?q=<task description>"
```

After completing work, record it:
```bash
curl -s -X POST http://YOUR_SERVER_IP:8080/api/memory/episodic \
  -H "Content-Type: application/json" \
  -d '{"content": "<summary>", "tags": "<project,topic>"}'
```
```

**Inject alias for remote:**

```bash
alias brain-sync='~/.agentbrain/inject-copilot-memory.sh "$(pwd)" "http://YOUR_SERVER_IP:8080"'
```

**During a Copilot CLI session:**

```
Call http://YOUR_SERVER_IP:8080/api/context?q="<task>" and use the returned memory context
```

The sync agent on your laptop keeps your local `~/.claude/` in sync with the server automatically. You do not need to do anything extra for file sync — only the API address changes.

---

### 4C. Copilot CLI via SSH on the server

When you SSH into the server and use Copilot CLI there, use `localhost:8080` since AgentBrain runs on the same machine:

```bash
ssh user@YOUR_SERVER_IP
cd /your-project

# Inject lessons into local copilot-instructions.md
curl -s http://localhost:8080/api/lessons?status=ACCEPTED | python3 -c "
import json, sys
lessons = json.load(sys.stdin)
print('Accepted lessons:')
for l in lessons:
    print(f'  - {l[\"claim\"]}')
"

# Then use Copilot CLI normally
# Ask it to call http://localhost:8080/api/context?q=<task>
```

Or run the inject script on the server (copy it there):

```bash
scp ~/.agentbrain/inject-copilot-memory.sh user@YOUR_SERVER_IP:~/.agentbrain/
ssh user@YOUR_SERVER_IP
~/.agentbrain/inject-copilot-memory.sh /your-project http://localhost:8080
```

---

## 5. Quick Reference

### Which URL to use

| Where you are | Where AgentBrain runs | Use this URL |
|---------------|----------------------|--------------|
| Laptop | Laptop (local) | `http://localhost:8080` |
| Laptop | Remote server | `http://YOUR_SERVER_IP:8080` |
| SSH on server | Same server | `http://localhost:8080` |
| Browser (dashboard) | Laptop (local) | `http://localhost:3000` |
| Browser (dashboard) | Remote server | `http://YOUR_SERVER_IP:3000` |

### Sync agent port

The sync agent's local API is always `http://localhost:7701` — it only runs on your laptop, never on the server. The Claude Code hooks always point to `localhost:7701` regardless of where AgentBrain itself is running.

### Day-to-day curl commands

```bash
# ── Fetch memory context for a task ──────────────────────────────────
curl -s "http://AGENTBRAIN_HOST:8080/api/context?q=<task description>" | python3 -m json.tool

# ── Add a working memory note (expires in 24h) ───────────────────────
curl -s -X POST http://AGENTBRAIN_HOST:8080/api/memory/working \
  -H "Content-Type: application/json" \
  -d '{"content": "Remember to check X before deploying", "tags": "reminder"}'

# ── Add an episodic memory (persists 90 days, feeds dream cycle) ──────
curl -s -X POST http://AGENTBRAIN_HOST:8080/api/memory/episodic \
  -H "Content-Type: application/json" \
  -d '{"content": "Fixed auth by clearing the token cache on logout", "tags": "auth,bugfix"}'

# ── Manually trigger the dream cycle (default: runs at 03:00 nightly) ─
curl -s -X POST http://AGENTBRAIN_HOST:8080/api/dream/run | python3 -m json.tool

# ── Dashboard stats ───────────────────────────────────────────────────
curl -s http://AGENTBRAIN_HOST:8080/api/stats | python3 -m json.tool

# ── Sync agent: trigger full sync ────────────────────────────────────
curl -s http://localhost:7701/sync/trigger

# ── Sync agent: health check ─────────────────────────────────────────
curl -s http://localhost:7701/health
```

Replace `AGENTBRAIN_HOST` with `localhost` (local setup) or `YOUR_SERVER_IP` (remote setup).

### Dashboard pages

| Page | URL | What you do there |
|------|-----|-------------------|
| Dashboard | `:3000` | Stats overview + live event feed |
| Memory | `:3000/memory` | View / add / search all memory layers |
| Lessons | `:3000/lessons` | Graduate or reject staged lessons |
| Claude Config | `:3000/claude` | Browse and edit `~/.claude/` files |
| Activity Log | `:3000/activity` | Real-time WebSocket event stream |

### Setup comparison

| | Local setup | Remote setup |
|---|---|---|
| AgentBrain URL | `localhost:8080` | `YOUR_SERVER_IP:8080` |
| Database | H2 (embedded) | PostgreSQL |
| Sync agent required? | Optional (can mount directly) | Required |
| Sync agent points to | `localhost:8080` | `YOUR_SERVER_IP:8080` |
| Claude Code hooks | `localhost:7701` | `localhost:7701` (same) |
| Copilot CLI API call | `localhost:8080` | `YOUR_SERVER_IP:8080` |
| SSH on server | N/A | Uses `localhost:8080` |

### Troubleshooting

| Problem | Likely cause | Fix |
|---------|-------------|-----|
| `Connection refused` on 8080 | Backend not running | `docker compose up -d` |
| `Connection refused` on 7701 | Sync agent not running | `java -jar ~/.agentbrain/agentbrain-sync.jar` |
| WebUI shows empty file tree | `claude-store` volume empty | Run sync agent — it will populate on first full sync |
| `401 Unauthorized` on `/api/sync/*` | Token mismatch | Check `SYNC_TOKEN` in server `.env` matches `sync.token` in `~/.agentbrain/sync-agent.properties` |
| Lesson not appearing in context | Lesson is not ACCEPTED | Go to `:3000/lessons`, graduate it with a rationale |
| Dream cycle produces no lessons | Not enough episodic memory | Add episodic memories via API or use Claude Code / Copilot CLI for a few sessions, then re-run the dream cycle |
| Hook fires but context is stale | Sync in progress | Wait 1–2 seconds; sync runs async and completes quickly |
| Files edited in WebUI not appearing locally | Sync agent not running | Start the sync agent; it polls every 2 seconds |
