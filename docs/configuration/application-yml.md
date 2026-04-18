# Application Configuration Reference

Full reference for `backend/src/main/resources/application.yml` and `application-prod.yml`.

---

## `application.yml` (dev — H2)

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/agentbrain;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: validate          # Flyway manages schema — never use create/update
    show-sql: false               # Set to true for query debugging
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8080

agentbrain:
  claude-dir: /claude-home          # Docker volume mount point

  dream:
    cron: "0 0 3 * * *"             # 03:00 daily (Spring cron: sec min hour day month weekday)

  memory:
    working-ttl-hours: 24           # Working memory lifetime
    episodic-ttl-days: 90           # Episodic memory lifetime
    context-budget-limit: 20        # Max episodic entries in /api/context response

  sync:
    token: ${SYNC_TOKEN:changeme}   # Bearer token for /api/sync/* endpoints

logging:
  level:
    io.agentbrain: DEBUG
    org.springframework.web: INFO
    org.hibernate.SQL: WARN
```

---

## `application-prod.yml` (prod — PostgreSQL)

This file overrides `application.yml` when `SPRING_PROFILES_ACTIVE=prod`.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db:5432/agentbrain
    driver-class-name: org.postgresql.Driver
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true

logging:
  level:
    io.agentbrain: INFO             # Less verbose in prod
```

---

## Configuration Properties Reference

### `agentbrain.claude-dir`

**Type:** `String`  
**Default:** `/claude-home`  
**Env Override:** `AGENTBRAIN_CLAUDE_DIR`

The absolute path inside the container where `~/.claude/` is mounted. All Claude Config Browser operations resolve paths relative to this directory.

**Security:** Any file operation that resolves to a path outside this directory is rejected with `400 Bad Request`.

---

### `agentbrain.dream.cron`

**Type:** `String` (Spring cron expression)  
**Default:** `0 0 3 * * *`  
**Env Override:** `AGENTBRAIN_DREAM_CRON`

Spring cron format: `second minute hour day-of-month month day-of-week`

Examples:

| Cron | Schedule |
|------|----------|
| `0 0 3 * * *` | Every day at 03:00 (default) |
| `0 30 2 * * *` | Every day at 02:30 |
| `0 0 3 * * MON-FRI` | Weekdays only at 03:00 |
| `0 */30 * * * *` | Every 30 minutes (for testing) |

---

### `agentbrain.memory.working-ttl-hours`

**Type:** `int`  
**Default:** `24`  
**Env Override:** `AGENTBRAIN_MEMORY_WORKING_TTL_HOURS`

Working memory entries older than this are excluded from `GET /api/memory/working` responses.

---

### `agentbrain.memory.episodic-ttl-days`

**Type:** `int`  
**Default:** `90`  
**Env Override:** `AGENTBRAIN_MEMORY_EPISODIC_TTL_DAYS`

Episodic memory entries are excluded from context budget results after this many days. They are not deleted — just filtered.

---

### `agentbrain.memory.context-budget-limit`

**Type:** `int`  
**Default:** `20`  
**Env Override:** `AGENTBRAIN_MEMORY_CONTEXT_BUDGET_LIMIT`

Maximum number of episodic memory entries returned by `/api/context`. All accepted lessons are always included (no limit).

---

### `agentbrain.sync.token`

**Type:** `String`  
**Default:** value of `${SYNC_TOKEN}` env var  
**Env Override:** `AGENTBRAIN_SYNC_TOKEN`

The Bearer token that sync agents must present. Set `SYNC_TOKEN` in `.env`:

```bash
SYNC_TOKEN=$(openssl rand -hex 32)
```

---

## H2 Console (dev only)

H2 has a web console for inspecting the database directly. To enable it during development, add to `application.yml`:

```yaml
spring:
  h2:
    console:
      enabled: true
      path: /h2-console
```

Access at: http://localhost:8080/h2-console  
JDBC URL: `jdbc:h2:file:./data/agentbrain`  
Username: `sa`  
Password: *(empty)*

> **Never enable H2 console in production.**

---

## Scheduling

The `@EnableScheduling` annotation is on `AgentBrainApplication`. To disable the Dream Cycle entirely (e.g., for testing):

```yaml
spring:
  task:
    scheduling:
      enabled: false
```

Or override the cron to a far-future date:
```yaml
agentbrain:
  dream:
    cron: "0 0 3 1 1 *"   # Jan 1 only
```
