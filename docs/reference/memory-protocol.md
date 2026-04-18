# Memory Protocol

This guide explains **what to log, when, how to format it**, and quality guidelines for getting the most out of AgentBrain's memory system.

---

## The Core Principle

> **Log decisions and discoveries, not actions.**

Bad: `"Read the UserService.java file"`  
Good: `"UserService.getById() throws NPE when user is deleted mid-request — fixed by using orElseThrow()"`

The context budget has a fixed size limit. Every entry competes for space. Low-signal entries crowd out high-signal ones.

---

## What to Log as Episodic Memory

### ✅ Always log these

| Event | Example |
|-------|---------|
| **Bug found + fixed** | `"Fixed NPE in PaymentService — null check on card.getExpiry() was missing when card is expired"` |
| **Approach tried and failed** | `"Tried using @Cacheable on UserRepository.findAll() — causes stale data issues when roles change. Don't cache this."` |
| **Configuration that worked** | `"docker compose down && docker volume prune -f fixes Flyway checksum mismatch after migration edit"` |
| **Dependency issue resolved** | `"Flyway 10+ with PostgreSQL 16 requires flyway-database-postgresql dependency in addition to flyway-core"` |
| **Architecture decision** | `"Chose STOMP over WebSocket instead of SSE — better nginx proxy support and browser reconnect handling"` |
| **Deployment gotcha** | `"Port 8080 is occupied on 192.168.68.111 by another service — use 8086"` |
| **Test that consistently passes/fails** | `"Integration tests need a 2s delay after Spring context start — otherwise JPA not ready"` |

### ❌ Don't log these

| Event | Why |
|-------|-----|
| `"Read file X"` | Not actionable — no discovery |
| `"Ran grep for Y"` | Process, not outcome |
| `"Created a new class"` | Too granular |
| `"Installed npm packages"` | Recoverable from package.json |
| `"The build succeeded"` | Non-informative — expected state |

---

## What to Log as Working Memory

Working memory is cleared after 24 hours. Use it for session context — the equivalent of sticky notes on your desk.

```bash
curl -X POST http://localhost:7701/working \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Session 2026-04-18 afternoon: Working on auth refactor. Current file: src/auth/JwtService.java. Next: add role invalidation on token refresh. Blocked on: need to understand how SessionRegistry tracks expiry.",
    "tags": "auth,jwt,session-summary,in-progress"
  }'
```

### Working memory format

```
Session [date]: [what you worked on]. 
Current: [active file/component]. 
Completed: [what was finished]. 
Next: [what remains]. 
Blocked: [any blockers].
```

---

## Tagging Strategy

Tags are comma-separated strings stored with each memory. They are used for:
1. Keyword matching in context budget ranking
2. Filtering and searching in the UI

### Recommended tag structure

```
<project>, <component>, <type>, <language/framework>, <extra>
```

| Tag category | Examples |
|-------------|---------|
| Project | `agentbrain`, `myapp`, `auth-service` |
| Component | `userservice`, `frontend`, `nginx`, `database` |
| Type | `bugfix`, `deployment`, `architecture`, `decision`, `gotcha`, `session-summary` |
| Framework | `spring`, `react`, `flyway`, `docker`, `jpa` |
| Language | `java`, `typescript`, `sql`, `bash` |

**Example:** `"agentbrain,cors,spring,bugfix,deployment"`

### Why tags matter

The context budget ranking uses tag overlap with the query:

```
curl "http://localhost:7701/context?q=spring deployment"
```

Memories tagged `spring,deployment` rank much higher than memories with no tags or irrelevant tags.

---

## Lesson Graduation Guidelines

When reviewing staged lessons in the UI, use these guidelines to decide whether to graduate or reject:

### Graduate when

- [ ] The lesson generalizes beyond one specific situation
- [ ] You have seen this pattern more than once (or it's high-value enough once)
- [ ] The rationale explains *why* this lesson is important
- [ ] The claim is specific enough to be actionable
- [ ] The claim is general enough to apply in future sessions

### Reject when

- [ ] The lesson is too specific to one project/file (no general value)
- [ ] The lesson is already obvious or covered by documentation
- [ ] The claim is vague and not actionable
- [ ] The lesson is factually wrong or outdated

### Rationale quality

The rationale is required (min 10 chars, but aim for more). A good rationale:

```
"Confirmed in two separate deployments. The JVM exits immediately when
main() returns because WatchService and HttpServer threads are both daemon
threads. Thread.currentThread().join() is the standard fix."
```

A weak rationale:
```
"this is true"
```

---

## Context Budget Format

The output of `/api/context?q=<task>` is Markdown formatted for Claude Code / Copilot CLI to read:

```markdown
## Working Memory
- Session 2026-04-18: Fixed CORS in AgentBrain. Next: add toast notifications. [cors,session-summary] (expires in 22h)

## Relevant Episodes  
*(ranked by salience × relevance to "deploy spring app")*
- Flyway 10+ requires flyway-database-postgresql dependency for PostgreSQL 16 [flyway,postgresql,deployment] (salience: 0.97)
- Docker compose port vars: BACKEND_PORT and FRONTEND_PORT override defaults [docker,deployment] (salience: 0.94)

## Accepted Lessons
- **allowedOriginPatterns("*") for self-hosted Spring** — Browser sends Origin header on fetch() POST even for same-origin requests. Hardcoded localhost breaks LAN IP access. — graduated 2026-04-18
- **Thread.currentThread().join() to keep sync agent alive** — All WatchService/HttpServer threads are daemon threads; JVM exits immediately without this. — graduated 2026-04-18
```

---

## Memory Hygiene

Over time, episodic memories accumulate and become less relevant. The Dream Cycle's salience decay handles this automatically, but you can also:

**Manually purge old working memory:**  
Working memory auto-expires after 24h — nothing needed.

**Delete irrelevant episodic memories:**  
Not yet in the UI — use the REST API:  
```bash
# List recent episodic memories to find IDs
curl http://localhost:8080/api/memory/episodic?page=0&size=50

# Delete a specific one
curl -X DELETE http://localhost:8080/api/memory/episodic/42
```

**Reject low-quality staged lessons:**  
Use the Review Queue in the UI — this keeps the accepted lessons list clean and relevant.
