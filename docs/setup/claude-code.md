# Claude Code Integration

This guide explains how to connect Claude Code to AgentBrain so that your AI coding sessions have persistent memory.

---

## How It Works

```
Session starts
    │
    ▼
UserPromptSubmit hook fires
    │
    ▼
Curl → http://localhost:7701/context?q="<task>"
    │
    ▼
Sync agent proxies to server → ContextBudgetService
    │
    ▼
Returns ranked Markdown: working memory + episodes + lessons
    │
    ▼
Claude Code reads it → has full context of past work
```

---

## Prerequisites

1. AgentBrain server running (local or remote)
2. Sync agent running on your laptop (see [Sync Agent Setup](sync-agent.md))
3. Sync agent's local API accessible at `http://localhost:7701`

---

## Step 1 — Update CLAUDE.md

Add the AgentBrain memory protocol to your `~/.claude/CLAUDE.md`:

```markdown
---

## 🧠 AgentBrain Memory Protocol

You have persistent memory through AgentBrain. Follow this protocol every session.

### SESSION START
Before doing anything else, retrieve your memory context:
```bash
curl -s "http://localhost:7701/context?q=<brief description of current task>"
```
Read the output carefully. It contains:
- Working memory from recent sessions
- Relevant episodic memories ranked by salience
- All accepted lessons (permanent patterns)

### DURING SESSION
Log important discoveries and decisions as episodic memories:
```bash
curl -s -X POST http://localhost:7701/episodic \
  -H "Content-Type: application/json" \
  -d '{
    "content": "What happened and why it matters",
    "tags": "relevant,tags,here"
  }'
```

**Log when:**
- A bug is found and fixed
- An approach is tried and fails (with reason)
- A build, test, or deploy succeeds/fails
- An important architectural decision is made
- A configuration change has an unexpected effect
- You discover a pattern worth remembering

**Do NOT log:**
- Every file read or grep result
- Obvious or trivially known facts
- One-off outputs with no general value

### SESSION END
When the Stop hook fires, push a working memory summary:
```bash
curl -s -X POST http://localhost:7701/working \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Summary: [what was accomplished]. Files changed: [list]. Next: [what remains]",
    "tags": "session-summary,<project-tags>"
  }'
```

---
```

---

## Step 2 — Configure Hooks

Add the following hooks to `~/.claude/settings.json`:

```json
{
  "hooks": {
    "UserPromptSubmit": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "curl -s -o /dev/null 'http://localhost:7701/ping' 2>/dev/null || true"
          }
        ]
      }
    ],
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "curl -s -X POST http://localhost:7701/working -H 'Content-Type: application/json' -d '{\"content\": \"Session ended at $(date -u +%Y-%m-%dT%H:%M:%SZ). Review activity log for details.\", \"tags\": \"session-end,auto\"}' > /dev/null 2>&1 || true"
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": {
          "tool_name": "Write|Edit|MultiEdit"
        },
        "hooks": [
          {
            "type": "command",
            "command": "curl -s -X POST http://localhost:7701/sync-trigger > /dev/null 2>&1 || true"
          }
        ]
      }
    ]
  }
}
```

### Hook Explanations

| Hook | Trigger | Purpose |
|------|---------|---------|
| `UserPromptSubmit` | Every prompt | Ping the sync agent to verify it's alive |
| `Stop` | Session end | Auto-push a session summary to working memory |
| `PostToolUse` | After Write/Edit | Trigger file sync so edits reach the server fast |

> **Note:** All hooks use `|| true` so they never fail a session if AgentBrain is offline.

---

## Step 3 — Verify Integration

Start a new Claude Code session and manually run the context fetch:

```bash
curl -s "http://localhost:7701/context?q=test"
```

You should see a Markdown response. If you see a connection error, check that the sync agent is running:

```bash
curl -s http://localhost:7701/ping
# Expected: {"status":"ok"}
```

---

## Memory Workflow Examples

### Good episodic memory entries

```bash
# Bug fix
curl -s -X POST http://localhost:7701/episodic \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Fixed NPE in UserService.getById() — was calling .get() on Optional without checking isPresent(). Use .orElseThrow() instead.",
    "tags": "java,optional,npe,userservice"
  }'

# Deployment discovery
curl -s -X POST http://localhost:7701/episodic \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Flyway 10+ requires explicit flyway-database-postgresql dependency when using PostgreSQL 16. flyway-core alone throws Unsupported Database error at startup.",
    "tags": "flyway,postgresql,deployment,dependency"
  }'

# Architecture decision
curl -s -X POST http://localhost:7701/episodic \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Decided to use allowedOriginPatterns(*) in Spring CORS config instead of hardcoded localhost values. Self-hosted apps accessed by LAN IP send that IP as Origin header.",
    "tags": "spring,cors,architecture,decision"
  }'
```

### Good working memory entries (session end)

```bash
curl -s -X POST http://localhost:7701/working \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Session 2026-04-18: Fixed CORS blocking graduate button. Root cause: browser sends Origin header on same-origin POST. Fix: allowedOriginPatterns(*). Also improved error handling in lessonApi to use r.text() + manual JSON.parse. Deployed to 192.168.68.111:3010. Remaining: add toast notifications instead of alert().",
    "tags": "agentbrain,cors,deployment,session-summary"
  }'
```

---

## CLAUDE.md Structure Best Practices

The order of sections in your `CLAUDE.md` matters — Claude Code reads it top to bottom at session start.

**Recommended structure:**

```markdown
# Project Instructions

[Project-specific context and rules]

---

## 🧠 AgentBrain Memory Protocol

[The protocol above]

---

## Code Style

[Style and convention rules]

---

## Architecture Notes

[Key architectural decisions already made]
```

---

## Troubleshooting

**Context returns empty**  
→ Check if episodic memories exist: `curl http://localhost:8080/api/memory/episodic`  
→ Check if sync agent is running: `curl http://localhost:7701/ping`

**Hooks silently failing**  
→ Check Claude Code hook logs in the session output  
→ Verify curl is in PATH when hooks run (add full path `/usr/bin/curl` if needed)

**Session end hook not firing**  
→ The Stop hook only fires when Claude Code exits cleanly, not on crash  
→ You can manually push a summary: `curl -s -X POST http://localhost:7701/working ...`
