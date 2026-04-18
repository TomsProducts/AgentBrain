# Memory Layers

AgentBrain implements a three-layer memory system inspired by cognitive science models of human memory. Each layer has a distinct purpose, lifetime, and decay mechanism.

---

## Layer 1 — Working Memory

**Purpose:** Notes, reminders, and observations relevant to the *current task*. High priority, short-lived.

### Properties

| Property | Value |
|----------|-------|
| Table | `working_memory` |
| Default TTL | 24 hours |
| Configurable via | `agentbrain.memory.working-ttl-hours` |
| Auto-expire | Yes — `WorkingMemoryService` purges on read |
| Salience decay | No |

### Schema

```sql
CREATE TABLE working_memory (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content    TEXT NOT NULL,
    tags       VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP
);
```

### Lifecycle

```
POST /api/memory/working  →  stored with expires_at = NOW() + TTL
GET  /api/memory/working  →  purges expired rows, returns active
```

### When to write to Working Memory

- Task description at session start
- Active file paths / class names in scope
- Decisions made during the session ("using approach X because Y")
- Blockers or TODOs discovered mid-task
- Session summary at end (pushed by Stop hook)

---

## Layer 2 — Episodic Memory

**Purpose:** A running log of significant events, discoveries, and outcomes across sessions. Medium priority, medium lifetime with salience decay.

### Properties

| Property | Value |
|----------|-------|
| Table | `episodic_memory` |
| Default TTL | 90 days |
| Configurable via | `agentbrain.memory.episodic-ttl-days` |
| Auto-expire | Yes — entries past TTL are excluded from queries |
| Salience decay | Yes — sigmoid decay applied nightly by Dream Cycle |
| Staged flag | Entries marked `staged=true` after Dream Cycle processes them |

### Schema

```sql
CREATE TABLE episodic_memory (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content       TEXT NOT NULL,
    tags          VARCHAR(500),
    salience_score DOUBLE PRECISION DEFAULT 1.0,
    staged        BOOLEAN DEFAULT FALSE,
    occurred_at   TIMESTAMP DEFAULT NOW(),
    expires_at    TIMESTAMP
);
```

### Salience Score

New entries start with `salience_score = 1.0`. The Dream Cycle applies a sigmoid decay nightly to all entries (staged or not):

```
new_salience = old_salience * sigmoid_decay_factor
```

Where `sigmoid_decay_factor` approaches 0 over time. Entries with `salience_score < 0.05` are effectively invisible in context budget results.

### Lifecycle

```
POST /api/memory/episodic  →  stored with salience=1.0, staged=false
                                             │
                              (nightly Dream Cycle)
                                             │
                              salience decays + staged=true
                                             │
                              entry fades from context budget
                                             │
                              (after TTL) automatically excluded
```

### When to write to Episodic Memory

- When a bug is found and fixed
- When an approach is tried and fails (with reason)
- When a build/deployment succeeds or fails
- When a configuration change is made
- When an important decision is made
- End-of-session summaries

---

## Layer 3 — Lessons

**Purpose:** Distilled, validated patterns that should persist indefinitely and always be included in context. Lessons are the long-term knowledge base.

### Properties

| Property | Value |
|----------|-------|
| Table | `lessons` |
| Lifetime | Permanent (no TTL) |
| Created by | Dream Cycle (automatic) or manually via POST |
| Activated by | Human review — must Graduate with rationale |
| Salience | Decays slowly with sigmoid function, resets on re-acceptance |

### Schema

```sql
CREATE TABLE lessons (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim        TEXT NOT NULL,
    conditions   TEXT,
    status       VARCHAR(50) NOT NULL DEFAULT 'STAGED',
    pattern_id   VARCHAR(64),
    rationale    TEXT,
    salience     DOUBLE PRECISION DEFAULT 1.0,
    created_at   TIMESTAMP DEFAULT NOW(),
    graduated_at TIMESTAMP
);
```

### Status Machine

```
                  ┌─────────────────┐
                  │    STAGED       │◀──────────────┐
                  │  (candidate)    │               │
                  └────────┬────────┘               │
                           │                        │
          ┌────────────────┼────────────────┐       │
          │ graduate()     │                │       │
          │ + rationale    │ reject()       │       │
          ▼                ▼                │       │
   ┌─────────────┐  ┌─────────────┐        │       │
   │  ACCEPTED   │  │  REJECTED   │────────┘       │
   │ (active in  │  │ (archived)  │  reopen()       │
   │  context)   │  └─────────────┘                │
   └─────────────┘                                  │
          │                                         │
          └─── graduate() again ─────────────────--─┘
               (resets salience, updates rationale)
```

### Graduation Rules

1. `rationale` must be non-null and non-blank — 400 error otherwise
2. Only `STAGED` or `REOPENED` lessons can be graduated
3. Only `STAGED` or `REOPENED` lessons can be rejected
4. Only `REJECTED` lessons can be reopened

### Context Budget Inclusion

Only `ACCEPTED` lessons appear in `/api/context` responses. `STAGED`, `REJECTED`, and `REOPENED` lessons are never exposed to agents.

---

## Context Budget

The context budget is what Claude Code actually reads at session start. It is assembled by `ContextBudgetService`:

```
GET /api/context?q=<task description>

Response (Markdown):
---
## Working Memory
- [content] (expires in Xh)
...

## Relevant Episodes  (top 20 by salience × relevance)
- [content] — tags: [tags] — [date]
...

## Accepted Lessons
- **[claim]** — [conditions] — graduated [date]
...
---
```

### Ranking Algorithm

Episodes are ranked by: `salience_score × relevance_score`

Where `relevance_score` is a simple keyword overlap between the episode content/tags and the query string `q`. This is intentionally simple — no embeddings, no LLM calls.

```java
double relevance = tags.stream()
    .filter(queryTokens::contains)
    .count() / (double) Math.max(1, queryTokens.size());

double rank = episode.getSalienceScore() * (0.3 + 0.7 * relevance);
```

The top-N results (configurable, default 20) are returned.

---

## Memory Quality Guidelines

See [Memory Protocol](../reference/memory-protocol.md) for detailed guidelines on what to write and how to format memory entries for maximum usefulness.
