# Copilot CLI Integration

This guide explains how to connect GitHub Copilot CLI to AgentBrain for persistent memory across sessions.

---

## How It Works

Copilot CLI doesn't have a hooks system like Claude Code, so integration works through **shell aliases** that inject memory context before starting a session and **wrapper scripts** that can be called during a conversation.

```
You type: brain-context "fix auth bug"
    │
    ▼
~/.agentbrain/inject-copilot-memory.sh
    │
    ▼
curl → http://localhost:7701/context?q="fix auth bug"
    │
    ▼
Prints Markdown context to terminal
    │
    ▼
You copy/paste or describe it to Copilot CLI
```

---

## Prerequisites

1. AgentBrain server running (local or remote)
2. Sync agent running on your laptop (see [Sync Agent Setup](sync-agent.md))
3. Copilot CLI installed and authenticated

---

## Step 1 — Create the Inject Script

Create `~/.agentbrain/inject-copilot-memory.sh`:

```bash
#!/bin/bash
# AgentBrain memory inject for Copilot CLI
# Usage: inject-copilot-memory.sh [task description]

AGENTBRAIN_URL="${AGENTBRAIN_URL:-http://localhost:7701}"
TASK="${1:-current task}"

echo ""
echo "╔════════════════════════════════════════════════════════╗"
echo "║              🧠 AgentBrain Memory Context              ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

CONTEXT=$(curl -s "${AGENTBRAIN_URL}/context?q=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$TASK")" 2>/dev/null)

if [ -z "$CONTEXT" ] || [ "$CONTEXT" = "null" ]; then
  echo "⚠️  AgentBrain not reachable at $AGENTBRAIN_URL"
  echo "   Start the sync agent: java -jar ~/.agentbrain/agentbrain-sync.jar"
else
  echo "$CONTEXT"
fi

echo ""
echo "════════════════════════════════════════════════════════"
echo ""
```

Make it executable:

```bash
chmod +x ~/.agentbrain/inject-copilot-memory.sh
```

---

## Step 2 — Add Shell Aliases

Add the following to your `~/.zshrc` (or `~/.bashrc`):

```bash
# AgentBrain — memory shortcuts for Copilot CLI
export AGENTBRAIN_URL="http://localhost:7701"

# Fetch memory context before starting a Copilot CLI session
alias brain-context='~/.agentbrain/inject-copilot-memory.sh'

# Log a note to episodic memory
brain-note() {
  local content="$1"
  local tags="${2:-copilot,manual}"
  curl -s -X POST "${AGENTBRAIN_URL}/episodic" \
    -H "Content-Type: application/json" \
    -d "{\"content\": \"$content\", \"tags\": \"$tags\"}" > /dev/null
  echo "✅ Memory logged"
}

# Sync trigger (push ~/.claude changes to server)
alias brain-sync='curl -s -X POST ${AGENTBRAIN_URL}/sync-trigger > /dev/null && echo "✅ Sync triggered"'

# Check AgentBrain status
alias brain-status='curl -s http://192.168.68.111:8086/actuator/health | python3 -m json.tool'

# Open AgentBrain dashboard in browser
alias brain-open='open http://192.168.68.111:3010'
```

Reload your shell:

```bash
source ~/.zshrc
```

---

## Step 3 — Workflow

### Before a Copilot CLI Session

```bash
# Get context for your current task
brain-context "refactoring the authentication module"

# Output:
# ╔══════════════════════════════════════════════════════╗
# ║           🧠 AgentBrain Memory Context               ║
# ╚══════════════════════════════════════════════════════╝
#
# ## Working Memory
# - Session ended at 2026-04-18T14:00:00Z [session-end, auto]
#
# ## Relevant Episodes
# - UserService.getById() NPE — use orElseThrow() not get() [java,optional]
#
# ## Accepted Lessons
# - **Always use allowedOriginPatterns(*) for self-hosted Spring apps**
```

### During a Copilot CLI Session

Log discoveries as you work:

```bash
brain-note "Found that the JWT token is not being refreshed after role change — need to invalidate session on role update" "jwt,auth,bug"
```

### After a Copilot CLI Session

```bash
brain-note "Session 2026-04-18 afternoon: Refactored auth module. Moved role checks to @PreAuthorize annotations. JWT refresh now triggers on role change via SessionRegistry.expireNow(). Tests pass." "auth,refactor,session-summary"
```

---

## Advanced: Automatic Context on Terminal Open

Add this to `~/.zshrc` to automatically show the last few memories every time you open a new terminal (optional — disable if too noisy):

```bash
# Show recent AgentBrain working memory on terminal open
if curl -s --max-time 1 http://localhost:7701/ping > /dev/null 2>&1; then
  echo ""
  echo "🧠 Recent memory:"
  curl -s "http://localhost:7701/context?q=current" 2>/dev/null | head -20
  echo ""
fi
```

---

## Prompting Copilot CLI with Context

Since Copilot CLI reads from your conversation, paste the context output at the start of your first message:

```
## Context from AgentBrain:

### Accepted Lessons
- Always use allowedOriginPatterns(*) for self-hosted Spring apps
- Flyway 10+ requires flyway-database-postgresql for PostgreSQL 16

### Recent Episodes
- Fixed CORS blocking API calls when accessing via LAN IP

---

Now help me add a rate limiting feature to the REST API.
```

---

## Copilot CLI Space Integration

If you use Copilot Spaces, you can add your AgentBrain context as a space document:

```bash
# Export current context to a file that can be added to a Copilot Space
curl -s "http://localhost:7701/context?q=general" > ~/.agentbrain/context-snapshot.md
echo "Context exported to ~/.agentbrain/context-snapshot.md"
```

---

## Troubleshooting

**`brain-context` returns empty**  
→ Check sync agent: `curl http://localhost:7701/ping`  
→ Check server health: `brain-status`

**`brain-note` silently fails**  
→ Check the response: remove `> /dev/null` from the alias temporarily  
→ Verify server URL: `echo $AGENTBRAIN_URL`

**Context is stale / missing recent work**  
→ Trigger a sync: `brain-sync`  
→ Add episodic memories manually: `brain-note "what you worked on"`
