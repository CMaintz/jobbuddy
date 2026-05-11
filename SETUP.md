# AutoApplicant — Setup & Deployment Guide

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Docker + Docker Compose | 24+ | https://docs.docker.com/get-docker/ |
| Java JDK | 21+ | https://adoptium.net/ (only needed for local backend dev) |
| Node.js | 20+ | https://nodejs.org/ (only needed for local frontend dev) |
| Git | any | https://git-scm.com/ |

An **OpenAI API key** with access to `gpt-4o` and `text-embedding-3-small` is required for AI features.

---

## 1 — Clone the repository

```bash
git clone <repo-url> AutoApplicant
cd AutoApplicant
```

---

## 2 — Configure environment variables

Copy the example file and fill in your values:

```bash
cp .env.example .env
```

Edit `.env`:

```env
# Required — get yours at https://platform.openai.com/api-keys
OPENAI_API_KEY=sk-...

# Required — generate a random secret, minimum 32 characters
# Example: openssl rand -base64 32
JWT_SECRET=your-very-long-random-secret-here

# Optional — override only if running services outside Docker
POSTGRES_PASSWORD=localdevpassword
TYPESENSE_API_KEY=local-dev-key
```

> **Security note:** Never commit `.env` to version control. It is already listed in `.gitignore`.

---

## 3A — Full stack with Docker Compose (recommended)

This starts PostgreSQL (with pgvector), Typesense, the Spring Boot backend, and the Angular frontend behind nginx.

```bash
cd infra
docker compose up --build
```

First startup takes 3–5 minutes (Gradle build + npm install inside Docker). Subsequent starts are fast.

Once all four services are healthy:

- **App**: http://localhost
- **API**: http://localhost:8080/api/v1
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Typesense**: http://localhost:8108

To run in the background:

```bash
docker compose up -d --build
```

To stop and remove containers (data volumes are preserved):

```bash
docker compose down
```

To wipe all data and start fresh:

```bash
docker compose down -v
```

---

## 3B — Local development (backend + frontend separately)

Use this when you are actively developing and want hot-reload.

### Start infrastructure only

```bash
cd infra
docker compose up postgres typesense -d
```

### Run the backend

```bash
# From the repo root
cp .env.example .env  # fill in values as above
export $(grep -v '^#' .env | xargs)   # load env vars into shell

cd backend
../gradlew bootRun
```

The backend starts on **http://localhost:8080**. Flyway migrations run automatically on startup.

### Run the frontend

```bash
cd frontend
npm install
npm start
```

The Angular dev server starts on **http://localhost:4200** and proxies `/api` calls to `localhost:8080`.

---

## 4 — First-run verification

1. Open http://localhost (Docker) or http://localhost:4200 (local dev)
2. Register a new account
3. Log in — you should land on the Dashboard
4. Go to **CV** and upload or paste your master CV
5. Go to **Generate** — select your CV, choose "Angled CV" or "Cover Letter", click Generate
6. Verify AI-generated content appears in the editor

If AI generation returns an error, double-check `OPENAI_API_KEY` is set correctly.

---

## 5 — Create an admin account

The job crawler admin endpoint requires the `ADMIN` role. To promote a user:

```sql
-- Connect to the database
docker exec -it infra-postgres-1 psql -U autoapplicant -d autoapplicant

-- Find the user
SELECT id, email, role FROM users;

-- Promote to admin
UPDATE users SET role = 'ADMIN' WHERE email = 'your@email.com';
```

Then trigger a manual crawl:

```bash
curl -X POST http://localhost:8080/api/v1/admin/crawler/trigger \
  -H "Authorization: Bearer <your-jwt-token>"
```

---

## 6 — Production deployment

### Option A — Single server (VPS / bare metal)

1. Provision a server with at least **2 GB RAM** and Docker installed.
2. Copy the repo and your `.env` file to the server.
3. Run `docker compose up -d --build` from `infra/`.
4. Put a reverse proxy (nginx, Caddy, Traefik) in front of port 80 and terminate TLS there.

Caddy example (`Caddyfile`):

```
autoapplicant.yourdomain.com {
    reverse_proxy localhost:80
}
```

### Option B — Managed cloud (Railway / Render / Fly.io)

Each service (postgres, typesense, backend, frontend) can be deployed separately on a managed platform. Pass environment variables through the platform's secrets UI.

Recommended split:
- **Postgres**: use the platform's managed PostgreSQL add-on (must support the `pgvector` extension — Railway and Supabase both do)
- **Typesense**: use Typesense Cloud (https://cloud.typesense.org/) or self-host on a separate container
- **Backend**: deploy as a Docker container, set all env vars as secrets
- **Frontend**: deploy the built static files to Vercel / Netlify, set `VITE_API_URL` / proxy config to point at the backend URL

### Option C — Kubernetes

Build images and push to a registry:

```bash
docker build -t your-registry/autoapplicant-backend:latest -f backend/Dockerfile .
docker build -t your-registry/autoapplicant-frontend:latest frontend/
docker push your-registry/autoapplicant-backend:latest
docker push your-registry/autoapplicant-frontend:latest
```

Use the `docker-compose.yml` as a reference for pod specs, environment variables, and health checks.

---

## 7 — Environment variable reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `OPENAI_API_KEY` | **Yes** | — | OpenAI secret key |
| `JWT_SECRET` | **Yes** | — | HS256 signing secret (min 32 chars) |
| `POSTGRES_PASSWORD` | No | `localdevpassword` | Database password |
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/autoapplicant` | JDBC URL (local dev only) |
| `DB_USER` | No | `autoapplicant` | DB username (local dev only) |
| `DB_PASS` | No | `autoapplicant` | DB password (local dev only) |
| `TYPESENSE_API_KEY` | No | `local-dev-key` | Typesense admin API key |
| `TYPESENSE_HOST` | No | `localhost` | Typesense hostname (local dev only) |
| `TYPESENSE_PORT` | No | `8108` | Typesense port (local dev only) |

---

## 8 — Troubleshooting

**`relation "users" does not exist`**  
Flyway migrations haven't run. Ensure `SPRING_DATASOURCE_URL` points at the correct host. In Docker Compose, the backend waits for postgres to be healthy before starting.

**`Cannot connect to Typesense`**  
Check `TYPESENSE_HOST` and `TYPESENSE_API_KEY`. In Docker Compose the host is `typesense` (the service name), not `localhost`.

**`OpenAI: 401 Unauthorized`**  
Your `OPENAI_API_KEY` is invalid or not set. Verify with:  
`curl https://api.openai.com/v1/models -H "Authorization: Bearer $OPENAI_API_KEY"`

**`JWT secret too short`**  
The backend validates the secret is at least 32 characters at startup. Use `openssl rand -base64 32` to generate one.

**Frontend shows blank page after login**  
Open browser DevTools → Network. If `/api/v1/dashboard` returns 404, the nginx proxy config may be misconfigured. In Docker Compose this is handled automatically.
