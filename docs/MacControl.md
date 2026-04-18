# 🍎 AgentBrain — macOS Service Management

The AgentBrain sync agent runs as a **macOS LaunchAgent** — it starts automatically on login and restarts if it crashes.

---

## ▶️ Start / Stop / Restart

```bash
# Stop the service
launchctl stop io.agentbrain.sync

# Start the service
launchctl start io.agentbrain.sync

# Restart
launchctl stop io.agentbrain.sync && sleep 2 && launchctl start io.agentbrain.sync
```

---

## 🔁 Enable / Disable (survives reboots)

```bash
# Disable auto-start (won't start on next login)
launchctl unload ~/Library/LaunchAgents/io.agentbrain.sync.plist

# Re-enable auto-start
launchctl load ~/Library/LaunchAgents/io.agentbrain.sync.plist
```

---

## 📊 Status & Logs

```bash
# Check if running — shows PID if alive, "-" if stopped
launchctl list | grep agentbrain

# Live log stream
tail -f ~/.agentbrain/sync.log

# Error log stream
tail -f ~/.agentbrain/sync-error.log

# Quick health check via local API
brain-ping
```

---

## 🔧 Manual Start (without launchd)

Useful for debugging or when launchd is not available:

```bash
cd ~/.agentbrain && \
/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home/bin/java \
  -jar agentbrain-sync.jar &
```

---

## 📁 Key Files

| File | Purpose |
|------|---------|
| `~/Library/LaunchAgents/io.agentbrain.sync.plist` | Service definition (launchd config) |
| `~/.agentbrain/agentbrain-sync.jar` | The sync agent jar |
| `~/.agentbrain/application.properties` | Config: server URL, token, local API port |
| `~/.agentbrain/sync.log` | Standard output logs |
| `~/.agentbrain/sync-error.log` | Error / stderr logs |

---

## 📄 Plist Reference

Located at `~/Library/LaunchAgents/io.agentbrain.sync.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>io.agentbrain.sync</string>
    <key>ProgramArguments</key>
    <array>
        <string>/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home/bin/java</string>
        <string>-jar</string>
        <string>/Users/admin/.agentbrain/agentbrain-sync.jar</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>WorkingDirectory</key>
    <string>/Users/admin/.agentbrain</string>
    <key>StandardOutPath</key>
    <string>/Users/admin/.agentbrain/sync.log</string>
    <key>StandardErrorPath</key>
    <string>/Users/admin/.agentbrain/sync-error.log</string>
</dict>
</plist>
```

> **`KeepAlive: true`** — launchd will automatically restart the agent if it crashes.  
> **`RunAtLoad: true`** — agent starts immediately when loaded (login or manual `launchctl load`).  
> **`WorkingDirectory`** — must point to `~/.agentbrain/` so Spring Boot loads `application.properties`.

---

## 🔄 Updating the Jar

When a new version of the sync agent is built:

```bash
# 1. Stop the service
launchctl stop io.agentbrain.sync

# 2. Install the new jar
cp /path/to/agentbrain-sync-0.0.1-SNAPSHOT.jar ~/.agentbrain/agentbrain-sync.jar

# 3. Start the service
launchctl start io.agentbrain.sync

# 4. Verify
brain-ping
```

---

## 🛠 Troubleshooting

| Problem | Fix |
|---------|-----|
| `brain-ping` returns connection refused | Run `launchctl start io.agentbrain.sync` |
| Service keeps stopping | Check `~/.agentbrain/sync-error.log` for Java errors |
| Port 7701 already in use | Find and kill the old process: `lsof -ti:7701 \| xargs kill` |
| Config not loading | Ensure `WorkingDirectory` in plist points to `~/.agentbrain/` |
| Java not found | Verify path: `/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home/bin/java` |
| Changes to plist not applied | Run `launchctl unload` then `launchctl load` to reload |
