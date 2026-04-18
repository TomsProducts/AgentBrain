# Sync Agent Setup

The sync agent is a lightweight Java 21 daemon that:

1. **Watches** your `~/.claude/` directory for changes
2. **Pushes** changed files to the AgentBrain server
3. **Pulls** changes made via the Claude Config Browser back to your laptop
4. **Serves** a local HTTP API on port 7701 for Claude Code and Copilot CLI hooks

---

## Prerequisites

- Java 21 (any distribution — Temurin, Zulu, GraalVM)
- AgentBrain server running and accessible
- Maven (for building from source)

---

## Building

```bash
cd sync-agent
mvn package -DskipTests
ls target/agentbrain-sync-*.jar
```

The output JAR is self-contained (fat JAR) — no additional dependencies needed at runtime.

---

## Configuration

Create `~/.agentbrain/application.properties`:

```properties
# AgentBrain server URL (no trailing slash)
server.url=http://192.168.68.111:8086

# Must match SYNC_TOKEN in server's .env
server.token=your-sync-token-here

# Path to your Claude home directory
claude.home=/Users/youruser/.claude

# Local API port (Claude Code hooks connect here)
local.api.port=7701

# How often to poll server for changes made via WebUI (seconds)
sync.pull-interval-seconds=30

# Log level (DEBUG for troubleshooting, INFO for normal use)
logging.level.io.agentbrain=INFO
```

> **Important:** Place this file at exactly `~/.agentbrain/application.properties`.  
> Spring Boot loads config from the working directory — the launchd/systemd service sets the working directory to `~/.agentbrain/`.

---

## Running Manually

```bash
cd ~/.agentbrain
java -jar agentbrain-sync.jar
```

Expected startup output:

```
INFO  SyncAgentApplication - Local API server started on port 7701
INFO  SyncAgentApplication - Starting full sync of ~/.claude/...
INFO  SyncClient - Pushed 847 files to server
INFO  ClaudeDirWatcher - Watching /Users/youruser/.claude/ for changes
INFO  SyncAgentApplication - AgentBrain sync agent ready
```

Test the local API:

```bash
curl http://localhost:7701/ping
# {"status":"ok","serverUrl":"http://192.168.68.111:8086"}

curl "http://localhost:7701/context?q=test"
# Returns Markdown memory context
```

---

## macOS — Auto-Start with launchd

Create `~/Library/LaunchAgents/io.agentbrain.sync.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>io.agentbrain.sync</string>

  <key>ProgramArguments</key>
  <array>
    <string>/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home/bin/java</string>
    <string>-jar</string>
    <string>/Users/youruser/.agentbrain/agentbrain-sync.jar</string>
  </array>

  <key>WorkingDirectory</key>
  <string>/Users/youruser/.agentbrain</string>

  <key>RunAtLoad</key>
  <true/>

  <key>KeepAlive</key>
  <true/>

  <key>StandardOutPath</key>
  <string>/Users/youruser/.agentbrain/sync.log</string>

  <key>StandardErrorPath</key>
  <string>/Users/youruser/.agentbrain/sync-error.log</string>
</dict>
</plist>
```

> Replace `/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home/bin/java` with your actual Java 21 binary path.  
> Find it: `java -XshowSettings:all -version 2>&1 | grep java.home`

Load and start:

```bash
launchctl load ~/Library/LaunchAgents/io.agentbrain.sync.plist
launchctl start io.agentbrain.sync
```

Check status:

```bash
launchctl list | grep agentbrain
# Should show a PID (non-zero) if running
```

View logs:

```bash
tail -f ~/.agentbrain/sync.log
```

---

## Linux — Auto-Start with systemd

Create `~/.config/systemd/user/agentbrain-sync.service`:

```ini
[Unit]
Description=AgentBrain Sync Agent
After=network.target

[Service]
Type=simple
WorkingDirectory=%h/.agentbrain
ExecStart=/usr/lib/jvm/java-21-openjdk/bin/java -jar %h/.agentbrain/agentbrain-sync.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=default.target
```

Enable and start:

```bash
systemctl --user daemon-reload
systemctl --user enable agentbrain-sync
systemctl --user start agentbrain-sync
systemctl --user status agentbrain-sync
```

View logs:

```bash
journalctl --user -u agentbrain-sync -f
```

---

## Local API Endpoints

The sync agent exposes a local HTTP API on port 7701. This is what Claude Code hooks and Copilot CLI scripts call.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/ping` | Health check — returns `{"status":"ok"}` |
| `GET` | `/context?q=<task>` | Fetch context budget from server |
| `POST` | `/working` | Create working memory (proxied to server) |
| `POST` | `/episodic` | Create episodic memory (proxied to server) |
| `POST` | `/sync-trigger` | Trigger immediate sync of `~/.claude/` |
| `GET` | `/status` | Sync agent stats (files synced, last push, etc.) |

All POST endpoints accept the same JSON body as the corresponding server endpoints.

---

## Troubleshooting

### Sync agent won't start

```bash
# Check Java version
java -version   # Must be 21+

# Check the working directory has the config
ls ~/.agentbrain/application.properties

# Run manually with verbose output
cd ~/.agentbrain && java -jar agentbrain-sync.jar --logging.level.io.agentbrain=DEBUG
```

### Files not syncing

```bash
# Check server is reachable
curl http://192.168.68.111:8086/actuator/health

# Check token matches
grep server.token ~/.agentbrain/application.properties
# Compare with server .env SYNC_TOKEN
```

### Port 7701 already in use

```bash
lsof -i :7701
```

Change the port in `application.properties`:
```properties
local.api.port=7702
```

And update all Claude Code hooks and aliases accordingly.

### Large initial sync takes too long

The first `fullSync()` after install pushes everything in `~/.claude/`. The `plugins/` subdirectory can contain thousands of files. This is normal — it takes 10-60 seconds depending on connection speed. Subsequent starts are faster (only changed files are pushed).

To exclude the plugins directory, add to `application.properties`:
```properties
sync.exclude-patterns=plugins/**,projects/**,history.jsonl
```
