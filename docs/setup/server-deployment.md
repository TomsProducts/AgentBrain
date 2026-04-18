# Server Deployment

This guide covers deploying AgentBrain to a dedicated server (Linux, VPS, LAN server) with PostgreSQL for production use.

---

## Server Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| CPU | 1 core | 2 cores |
| RAM | 512 MB | 1 GB |
| Disk | 2 GB | 10 GB |
| OS | Ubuntu 22.04+ | Ubuntu 24.04 LTS |
| Docker | 24+ | Latest |
| Docker Compose | v2+ | Latest |

---

## Step 1 — Install Docker on Server

```bash
ssh user@your-server

# Install Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker

# Verify
docker --version
docker compose version
```

---

## Step 2 — Clone and Configure

```bash
git clone https://github.com/TomsProducts/AgentBrain.git
cd AgentBrain

cp .env.example .env
```

Edit `.env`:

```dotenv
# Path to Claude config on this server (or a dedicated directory)
CLAUDE_HOME=/home/youruser/.claude

# Secure credentials — generate with openssl
DB_USER=agentbrain
DB_PASSWORD=$(openssl rand -hex 16)
SYNC_TOKEN=$(openssl rand -hex 32)

# Custom ports if defaults are taken
BACKEND_PORT=8080
FRONTEND_PORT=3000
```

> **Save the SYNC_TOKEN** — you'll need it in the sync agent config on your laptop.

---

## Step 3 — Check Available Ports

```bash
# Check if default ports are free
ss -tlnp | grep -E ':3000|:8080'

# If occupied, choose different ports in .env:
# BACKEND_PORT=8086
# FRONTEND_PORT=3010
```

---

## Step 4 — Deploy

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

Check status:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps

# Expected:
# NAME                    STATUS
# agentbrain-db-1         healthy
# agentbrain-backend-1    running
# agentbrain-frontend-1   running
```

---

## Step 5 — Verify

```bash
# Health check
curl http://localhost:8080/actuator/health
# {"status":"UP"}

# Frontend
curl -o /dev/null -w "%{http_code}" http://localhost:3000
# 200
```

Open a browser and navigate to `http://your-server-ip:3000`.

---

## Updating

```bash
cd AgentBrain

# Pull latest changes
git pull

# Rebuild and restart
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

Docker Compose only rebuilds containers that changed. The database is untouched.

---

## Backup

### Database backup

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec db \
  pg_dump -U agentbrain agentbrain > backup-$(date +%Y%m%d).sql
```

### Restore

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T db \
  psql -U agentbrain agentbrain < backup-20260418.sql
```

---

## Ports Reference

After deployment, the following ports are exposed on the host:

| Port | Service | URL |
|------|---------|-----|
| `${FRONTEND_PORT}` (3000) | nginx + React SPA | http://server:3000 |
| `${BACKEND_PORT}` (8080) | Spring Boot API | http://server:8080 |

PostgreSQL is **not** exposed to the host — it only listens on the internal Docker network.

---

## Security Recommendations

For a LAN-only deployment, the defaults are acceptable. For internet-facing deployments:

### 1. Put AgentBrain behind a reverse proxy (nginx/Caddy) with HTTPS

```nginx
server {
    listen 443 ssl;
    server_name agentbrain.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/agentbrain.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/agentbrain.yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:3000;
    }
}
```

### 2. Use a strong SYNC_TOKEN

```bash
openssl rand -hex 32
```

### 3. Firewall — block direct backend port

```bash
# Only allow frontend port externally; backend via Docker network only
ufw allow 3000/tcp
ufw deny 8080/tcp
```

### 4. Read-only Claude home mount in prod

Add `:ro` to the volume mount in `docker-compose.prod.yml` if you don't need to write files from the server:
```yaml
volumes:
  - ${CLAUDE_HOME}:/claude-home:ro
```

Remove `:ro` only if you want the Claude Config Browser to be able to write files on the server.

---

## Logs

```bash
# All services
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f

# Backend only
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f backend

# Last 100 lines
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs --tail=100 backend
```
