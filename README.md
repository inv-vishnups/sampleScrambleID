# SCIM learning stack

Full-stack sample: **Spring Boot** (JWT + SCIM Users API), **Angular** SPA, **PostgreSQL**, and **Docker Compose**. SCIM endpoints are intended for IdP provisioning (for example ScrambleID); the browser only talks to `/auth` and `/api`.

## Stack

| Layer | Tech |
|--------|------|
| API | Spring Boot 3.5, Spring Security, JPA, Flyway, JJWT |
| UI | Angular 19 (standalone, lazy routes) |
| DB | PostgreSQL 16 (Compose) or H2 (local `dev`) |
| Edge | nginx (Compose) proxies `/auth`, `/api`, `/scim` to the API |

## Prerequisites

- **Local (no Docker):** JDK 17+, Node 20+, optional Maven (or use `backend/mvnw`).
- **Docker:** Docker Engine + Compose v2.

## Run locally (H2 + ng serve)

1. **Backend** (profile `dev` — in-memory H2, seeded roles, optional admin user `admin` / `ChangeMe!123`):

   ```bash
   cd backend && ./mvnw spring-boot:run
   ```

2. **Frontend** (`http://localhost:4200` → API `http://localhost:8080`):

   ```bash
   cd frontend && npm ci && npx ng serve
   ```

## Run with Docker Compose

From the repository root:

```bash
cp .env.example .env   # optional; edit secrets
docker compose up -d --build
```

- **UI (nginx):** [http://localhost:8081](http://localhost:8081) — Angular uses same-origin API paths (`apiUrl` empty).
- **API (direct):** [http://localhost:8080](http://localhost:8080)

Compose sets `SPRING_PROFILES_ACTIVE=docker`, Postgres, and optional first admin when `APP_COMPOSE_BOOTSTRAP_ADMIN=true` (see `.env.example`).

## Environment variables

| Variable | Where | Purpose |
|----------|--------|---------|
| `JWT_SECRET` | Backend | HS256 signing key (use a long random value in production). |
| `SCIM_API_TOKEN` | Backend | `Authorization: Bearer …` for `/scim/v2/**`. |
| `APP_CORS_ALLOWED_ORIGINS` | Backend | Comma-separated browser origins. |
| `APP_COMPOSE_BOOTSTRAP_ADMIN` | Backend / Compose | `true` to seed `admin` when DB has no users (disable in real deployments). |

## API sketch

- `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`
- `GET /api/me`, `GET /api/admin/ping` (admin role)
- SCIM 2.0: `/scim/v2/Users` (Bearer = `SCIM_API_TOKEN`)

Example SCIM create (Compose; token must match `SCIM_API_TOKEN`):

```bash
curl -s -X POST http://localhost:8081/scim/v2/Users \
  -H "Authorization: Bearer scrambleid-docker-token-change-me" \
  -H "Content-Type: application/json" \
  -d '{"userName":"jdoe","emails":[{"value":"jdoe@example.com","primary":true}]}'
```

## Tests & CI

- Backend: `cd backend && ./mvnw test`
- Frontend build: `cd frontend && npx ng build --configuration=docker`
- GitHub Actions: `.github/workflows/ci.yml` (tests, Angular build, `docker compose build`)
- Dependabot: `.github/dependabot.yml`
- PR dependency review: `.github/workflows/dependency-review.yml`
- CodeQL: `.github/workflows/codeql.yml`

## Publish images (GHCR)

Workflow [`.github/workflows/publish-ghcr.yml`](.github/workflows/publish-ghcr.yml) builds and pushes:

- `ghcr.io/<owner>/<repo>/backend:<tag>`
- `ghcr.io/<owner>/<repo>/frontend:<tag>`

**Triggers**

1. **Git tag** matching `v*` (for example `v1.0.0`).
2. **Manual run:** GitHub → *Actions* → *Publish container images* → *Run workflow* (default tag `latest`).

Uses `GITHUB_TOKEN`; no extra registry secret is required for the same repository. For **private** packages, grant *read* access to packages where you deploy, or make the package public under *Package settings*.

**Pull and run (example)**

Replace `OWNER`, `REPO`, and `TAG` (paths must be **lowercase**):

```bash
docker pull ghcr.io/owner/repo/backend:TAG
docker pull ghcr.io/owner/repo/frontend:TAG
```

Point your own Compose or orchestrator at these images instead of `build:` blocks, and keep passing the same environment variables as in `.env.example`.

## Docs

See `helper.md` for the original architecture and implementation notes.
