# Production-Grade SCIM + JWT Application — Complete Implementation Plan

This document is a **full implementation blueprint** for a system with **Spring Boot** (backend), **Angular** (frontend), **JWT** (access + refresh tokens), and **SCIM** provisioning integrated with **ScrambleID**. Explanations stay simple on purpose; depth is in structure and decisions, not jargon.

---

## Part 1 — Architecture (Start Here)

### 1.1 What You Are Building (One Paragraph)

Your **Angular app** talks to your **Spring Boot API** using **JWTs** after login. **Humans** authenticate to *your* app. **ScrambleID** (or another IdP) sends **SCIM** requests to *your* Spring Boot service to **create, update, and deactivate users and groups** in your database—this is **machine-to-machine provisioning**, not the same as a user clicking “Login.” You keep these concerns **separate**: user JWT auth for the SPA, and **SCIM bearer token / mTLS / API key** (whatever ScrambleID supports) for the `/scim/v2` endpoints.

### 1.2 High-Level System Architecture

```
┌─────────────┐     HTTPS + JWT        ┌──────────────────┐
│   Browser   │ ◄────────────────────► │  Spring Boot API │
│  (Angular)  │     REST (app API)     │                  │
└─────────────┘                        │  ┌────────────┐    │
       │                               │  │ App layer │   │
       │                               │  │ (JWT)     │   │
       │                               │  └────────────┘    │
       │                               │  ┌────────────┐    │
       │                               │  │ SCIM layer│   │
       └───────────────────────────────┼─►│ (ScrambleID)   │
                                       │  └────────────┘    │
                                       │         │          │
                                       │         ▼          │
                                       │  ┌────────────┐    │
                                       │  │ Database   │    │
                                       │  └────────────┘    │
                                       └─────────▲──────────┘
                                                 │
                    SCIM 2.0 (HTTPS)             │
                    Bearer / agreed auth         │
                                       ┌─────────┴──────────┐
                                       │    ScrambleID      │
                                       │  (Identity / HR    │
                                       │   source of truth) │
                                       └────────────────────┘
```

**Why split “app API” and “SCIM”?**

- **App API** — Optimized for your product (aggregates, business rules, user profile for logged-in users).
- **SCIM** — Standard contract for **provisioning**; ScrambleID expects **RFC 7644**-style resources and semantics.

Mixing them in one unguarded surface invites confusion and security mistakes (e.g. exposing SCIM with the same rules as public login).

### 1.3 Component Interaction (Textual Flow)

1. **End-user login**
   - Angular sends credentials (or authorization code if you later add OIDC) to Spring Boot `/auth/login`.
   - Backend validates user (database or external IdP), issues **short-lived access JWT** and **longer-lived refresh token** (stored server-side or as httpOnly cookie—see security section).
   - Angular attaches **access token** to API calls; on **401**, interceptor tries **refresh**, then retries or logs out.

2. **Provisioning from ScrambleID**
   - ScrambleID is configured with your **SCIM base URL** (e.g. `https://api.yourcompany.com/scim/v2`) and a **shared secret / OAuth client / certificate** per their docs.
   - On hire/role change/group change, ScrambleID sends **POST/PUT/PATCH/DELETE** to `/Users` or `/Groups`.
   - Your SCIM controller validates **SCIM auth**, maps payload to **local entities**, persists, returns **SCIM JSON** (201, 200, 404, etc. per RFC).

3. **Consistency**
   - Optionally emit **domain events** (user created, group membership changed) so other services stay in sync—important at scale.

### 1.4 Security Architecture (JWT + Refresh)

**Tokens**

| Token        | Typical lifetime | Storage (SPA) recommendation      | Storage (server)        |
|-------------|------------------|-------------------------------------|-------------------------|
| Access JWT  | 5–15 minutes     | Memory preferred; avoid localStorage if XSS is a concern | N/A (stateless verify) |
| Refresh     | Days–weeks       | **httpOnly, Secure, SameSite** cookie **or** secure server-side session | Hashed in DB or session store |

**Why access tokens should be short**

- If stolen, the attacker’s window is small. Refresh is the “slow lane” with stricter rotation and reuse detection.

**Refresh strategy (production pattern)**

1. Issue refresh token on login; store **only a hash** in DB (`refresh_token` table: `user_id`, `token_hash`, `expires_at`, `revoked`, `replaced_by`, `device_id`).
2. On `/auth/refresh`, validate refresh token, check not revoked, issue **new access + new refresh**, **invalidate old refresh** (rotation).
3. If client presents a **revoked** refresh token again → **reuse attack** → revoke **all** refresh tokens for that user (or device family).

**JWT contents (access)**

- `sub` — user id  
- `roles` or `authorities` — for Spring Security  
- `exp`, `iat`, `jti` (optional, for denylist if needed)  
- **Do not** put PII or secrets in JWT (they are only signed, not encrypted by default).

**SCIM endpoint security (separate from user JWT)**

- Use **dedicated** Spring Security filter chain: e.g. path `/scim/v2/**` authenticated with **Bearer token** from ScrambleID, or **mTLS**, or **HMAC**—follow **ScrambleID’s integration guide** as source of truth.
- **Never** reuse end-user JWT secrets for SCIM; use separate credentials and rotation.

---

## Part 2 — Backend (Spring Boot)

### 2.1 Project Structure (Packages & Layers)

Use **layered + feature slices** for maintainability:

```
com.yourorg.app
├── AppApplication.java
├── config/                 # Security, OpenAPI, Jackson, SCIM beans
├── domain/                 # Core entities (User, Group, Role) — JPA
│   ├── user/
│   └── group/
├── repository/             # Spring Data JPA
├── security/
│   ├── jwt/                # JwtService, filters, entry points
│   └── scim/               # ScrambleID token validator (if separate)
├── api/                    # REST for SPA (NOT SCIM)
│   ├── auth/
│   ├── profile/
│   └── admin/
├── scim/                   # SCIM 2.0 — isolated package
│   ├── web/                # ScimUserController, ScimGroupController
│   ├── service/            # Mapping, business rules
│   ├── dto/                # ScimUserResource, ScimGroupResource, PatchOp
│   └── exception/          # ScimException → RFC error body
└── common/                 # Errors, auditing, mappers
```

**Why `scim` is its own package**

- Different DTOs, error format, and auth than your normal REST API. Isolation reduces accidental coupling.

### 2.2 JWT Implementation (Access + Refresh)

**Dependencies** (Maven example)

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<!-- Or jjwt / nimbus for custom JWT if not using resource-server JWT -->
```

**Minimal concepts**

- **Access token**: signed with HS256 or RS256; verified on each request via `OncePerRequestFilter` or `oauth2ResourceServer().jwt()`.
- **Refresh token**: opaque random string; persisted hashed; exchanged at `/auth/refresh`.

**Snippet — issuing tokens (conceptual)**

```java
// Pseudocode shape — adapt to your JWT library
public record TokenPair(String accessToken, Duration accessTtl, String refreshToken, Duration refreshTtl) {}

public TokenPair issueForUser(User user) {
    String access = jwtEncoder.encode(Map.of(
        "sub", user.getId().toString(),
        "roles", user.getRoles()
    ), Duration.ofMinutes(15));

    String rawRefresh = secureRandom(32);
    refreshTokenRepository.save(hash(rawRefresh), user.getId(), Instant.now().plus(7, ChronoUnit.DAYS));

    return new TokenPair(access, Duration.ofMinutes(15), rawRefresh, Duration.ofDays(7));
}
```

**Password handling**

- Use **BCrypt** or **Argon2** for password hashes; never store plaintext.

### 2.3 Spring Security Configuration

**Two security filter chains** (Spring Security 6 style)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain scimChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/scim/v2/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(a -> a.anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults())) // or custom ScrambleID filter
            .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable()) // if SPA + cookie, use cookie CSRF instead
            .authorizeHttpRequests(a -> a
                .requestMatchers("/auth/**", "/actuator/health").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

**Notes**

- If refresh is **httpOnly cookie**, protect against **CSRF** for cookie-using endpoints (double-submit token or SameSite=Lax/Strict + careful CORS).
- **CORS**: allow only your Angular origin in production.

### 2.4 SCIM Endpoints (RFC 7644)

**Base path**: `/scim/v2`

| Method | Path                    | Purpose                    |
|--------|-------------------------|----------------------------|
| GET    | `/Users`                | List/filter users          |
| GET    | `/Users/{id}`           | Get user                   |
| POST   | `/Users`                | Create user                |
| PUT    | `/Users/{id}`           | Replace user               |
| PATCH  | `/Users/{id}`           | Partial update (ops)       |
| DELETE | `/Users/{id}`           | Deactivate/delete          |
| GET    | `/Groups`               | List groups                |
| GET    | `/Groups/{id}`          | Get group                  |
| POST   | `/Groups`               | Create group               |
| PUT    | `/Groups/{id}`          | Replace group              |
| PATCH  | `/Groups/{id}`          | Patch members, displayName |
| DELETE | `/Groups/{id}`          | Delete group               |

**ServiceProviderConfig** (recommended)

- `GET /ServiceProviderConfig` — features, auth schemes, max results.

**PATCH**

- Body is **`PATCH` with JSON Patch** or **SCIM `patch` + `Operations`** per spec; implement what ScrambleID sends (verify in their docs).

**Error responses**

- Return **SCIM `urn:ietf:params:scim:api:messages:2.0:Error`** JSON with `status`, `scimType`, `detail`.

**Snippet — error body**

```java
public record ScimError(String detail, String status, String scimType) {
    public static final String URN = "urn:ietf:params:scim:api:messages:2.0:Error";
}
```

### 2.5 ScrambleID Integration Flow

1. **Onboarding** — In ScrambleID admin UI, create a **SCIM connector** pointing to your `/scim/v2` URL.
2. **Authentication** — Configure the method ScrambleID uses (Bearer token, OAuth2 client credentials, etc.).
3. **Attribute mapping** — Map ScrambleID fields → SCIM attributes you support (`userName`, `name`, `emails`, `active`, `externalId`, enterprise extensions).
4. **Test** — Run a test user create/update; watch your logs and DB.
5. **Production** — TLS 1.2+, IP allowlist if offered, rotate secrets on a schedule.

**Your responsibility**

- Implement **idempotent** handling where possible (same `externalId` → update, not duplicate).
- Return correct **HTTP status** and **Location** header on create.

### 2.6 Database Design (Entities & Relationships)

**Core tables (relational)**

**`users`**

| Column          | Type        | Notes                          |
|----------------|-------------|--------------------------------|
| id             | UUID PK     | Internal id                    |
| external_id    | VARCHAR     | SCIM `externalId` — unique     |
| user_name      | VARCHAR     | SCIM `userName` — unique       |
| email          | VARCHAR     | Primary email                  |
| given_name     | VARCHAR     |                                |
| family_name    | VARCHAR     |                                |
| active         | BOOLEAN     | SCIM `active`                  |
| password_hash  | VARCHAR     | Nullable if SSO-only app users |
| created_at     | TIMESTAMPTZ |                                |
| updated_at     | TIMESTAMPTZ |                                |

**`groups`**

| Column      | Type        | Notes                    |
|------------|-------------|--------------------------|
| id         | UUID PK     |                          |
| external_id| VARCHAR     | Unique                   |
| display_name | VARCHAR   | SCIM `displayName`       |

**`user_groups`** (many-to-many)

| user_id  | FK → users.id   |
|----------|-----------------|
| group_id | FK → groups.id  |
| PK       | (user_id, group_id) |

**`roles`** + **`user_roles`** (optional, if not mapping groups 1:1 to roles)

**`refresh_tokens`**

| id          | UUID PK     |
|-------------|-------------|
| user_id     | FK          |
| token_hash  | VARCHAR     |
| expires_at  | TIMESTAMPTZ |
| revoked     | BOOLEAN     |
| created_at  | TIMESTAMPTZ |

**Indexes**

- Unique on `users.user_name`, `users.external_id`
- Index on `refresh_tokens(user_id)`, `refresh_tokens(token_hash)`

**Auditing**

- `scim_audit` (optional): payload summary, source, correlation id — helps debug ScrambleID issues without storing full PII forever.

---

## Part 3 — Frontend (Angular)

### 3.1 Folder Structure

```
src/app/
├── core/                    # Singleton services, guards, interceptors
│   ├── auth/
│   │   ├── auth.service.ts
│   │   ├── token-storage.service.ts
│   │   └── auth.guard.ts
│   ├── interceptors/
│   │   └── jwt.interceptor.ts
│   └── models/
├── shared/                  # Reusable UI, pipes, directives
├── features/
│   ├── login/
│   ├── dashboard/
│   └── admin/
├── layout/
└── app.routes.ts
```

**Why `core` vs `shared`**

- **Core** — Imported once; app-wide security and HTTP behavior.
- **Shared** — Dumb components used everywhere.

### 3.2 Authentication Flow

1. **Login** — `POST /auth/login` → receive access + refresh (or access + Set-Cookie for refresh).
2. **Storage**
   - **Best**: refresh in **httpOnly cookie** (backend sets it); access in **memory** (service variable).
   - **Acceptable dev**: both in memory; **avoid localStorage** for refresh in production.
3. **Logout** — Call `/auth/logout` to revoke refresh; clear client state; redirect to login.
4. **Refresh** — On 401 from API, single-flight refresh: if another request already refreshes, queue and retry.

**Snippet — interceptor outline**

```typescript
// jwt.interceptor.ts (conceptual)
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getAccessToken();
  const authReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(authReq).pipe(
    catchError((err) => {
      if (err.status === 401 && !req.url.includes('/auth/refresh')) {
        return auth.refresh().pipe(switchMap(() => next(auth.cloneWithToken())));
      }
      return throwError(() => err);
    })
  );
};
```

Implement **request queuing** during refresh to avoid token stampedes.

### 3.3 Role-Based UI

- Decode **roles** from JWT in `AuthService` (or fetch `/me` from backend—**backend remains source of truth** for sensitive authorization).
- Use **route guards** (`canActivate`) and `*ngIf`/`@if` with a `hasRole('ADMIN')` helper.
- **Never** rely on hiding buttons alone; backend must **enforce** roles on every endpoint.

---

## Part 4 — SCIM + ScrambleID (Deep Dive)

### 4.1 Provisioning Flow (Typical)

1. HR creates user in upstream system → ScrambleID receives event.
2. ScrambleID **POST /scim/v2/Users** with JSON body (`schemas`, `userName`, `emails`, `name`, `active`, `externalId`).
3. Your service creates `users` row, returns **201** + resource.
4. Group assignment → **PATCH /Groups/{id}`** or **PATCH /Users/{id}`** depending on connector config.
5. Termination → **PATCH** `active: false` or **DELETE** per your policy.

### 4.2 Attribute Mapping

| SCIM attribute   | Your column / field   | Notes                          |
|------------------|------------------------|--------------------------------|
| `id`             | Internal UUID          | You generate; return in SCIM   |
| `externalId`     | `external_id`          | Stable id from IdP             |
| `userName`       | `user_name`            | Often email                    |
| `name.givenName` | `given_name`           |                                |
| `name.familyName`| `family_name`          |                                |
| `emails[value]`  | `email`                | Primary email                  |
| `active`         | `active`               |                                |
| `groups`         | `user_groups`          | Via PATCH operations           |

Use **extension schema** `urn:ietf:params:scim:schemas:extension:enterprise:2.0:User` only if ScrambleID requires `department`, `manager`, etc.

### 4.3 Token Validation (SCIM)

- Validate **signature** (if JWT), **issuer**, **audience**, **expiry**.
- If static Bearer: compare using **constant-time** equals against stored secret (from env/KMS).
- Log **correlation id** from header if ScrambleID sends one.

### 4.4 Common Failures & Handling

| Symptom              | Likely cause              | Mitigation                          |
|----------------------|---------------------------|-------------------------------------|
| 401 on SCIM          | Wrong/expired token       | Rotate creds; sync clocks (NTP)     |
| 409 / duplicate      | Same `userName` exists    | Return SCIM error; define upsert    |
| 400 on PATCH         | Unsupported path          | Implement op or return `invalidValue` |
| Partial updates lost | PUT vs PATCH confusion   | Document; implement PATCH fully     |
| Timeout on ScrambleID| Slow DB                   | Indexes, async processing + 202*  |
| Charset issues       | Non-UTF-8                 | Force UTF-8 everywhere              |

\* Async SCIM is advanced; prefer fast synchronous responses if possible.

---

## Part 5 — DevOps & Deployment

### 5.1 Docker (Suggested Layout)

- **`Dockerfile` (backend)** — Multi-stage: Maven/Gradle build → JRE slim image; non-root user.
- **`Dockerfile` (frontend)** — Build Angular with `ng build` → **nginx** serving static files.
- **`docker-compose.yml`** — `api`, `web`, `db` (Postgres), optional `redis` for rate limiting / sessions.

**Environment variables (backend)**

- `JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_PRIVATE_KEY` / `JWT_SECRET` (prefer asymmetric keys in prod)
- `SCIM_BEARER_TOKEN` or JWKS URL for ScrambleID validation
- `SPRING_DATASOURCE_*`
- `CORS_ALLOWED_ORIGINS`

**Frontend**

- `API_BASE_URL` injected at build time (`environment.prod.ts`) or runtime via `config.json` fetched on load (more flexible).

### 5.2 CI/CD Suggestions

1. **On PR** — Unit tests, integration tests (Testcontainers for Postgres), SpotBugs/checkstyle, `npm ci && ng test --no-watch`.
2. **On main** — Build images, tag with git SHA, push to registry.
3. **Deploy** — Helm/Kubernetes or managed service; **migrate DB** with Flyway/Liquibase before rolling pods.
4. **Secrets** — Vault, AWS Secrets Manager, or GitHub OIDC → cloud roles; **never** commit secrets.

---

## Part 6 — Best Practices

### 6.1 Security

- **Least privilege** DB user for the app.
- **Rate limit** `/auth/login` and SCIM endpoints.
- **Dependency scanning** (OWASP, Snyk).
- **Security headers** (CSP, HSTS) on frontend nginx.
- **Regular key rotation** for JWT signing keys and SCIM credentials.

### 6.2 Scalability

- Stateless API nodes behind a load balancer; JWT fits this model.
- DB connection pooling; read replicas if read-heavy.
- Consider **outbox pattern** for downstream notifications from SCIM events.

### 6.3 Maintainability

- **OpenAPI** for app REST; **separate** SCIM doc or reference RFC 7644.
- **Contract tests** for SCIM (golden files from ScrambleID samples).
- **Feature flags** for risky SCIM attribute mappings.

---

## Implementation Order (Practical Roadmap)

1. **Database + entities + Flyway**
2. **Spring Security** with JWT access + refresh + user registration/login (if local auth)
3. **App REST** + Angular login + interceptor + refresh queue
4. **SCIM read-only** (`GET Users/Groups`) — verify ScrambleID connectivity
5. **SCIM write + PATCH** + full error model
6. **Hardening** — audit logs, rate limits, integration tests, Docker, CI/CD

---

## Summary

You run **two authenticated surfaces** on the same Spring Boot app: **JWT for people** using the Angular app, and **SCIM credentials for ScrambleID** hitting `/scim/v2`. Keep packages, tokens, and config **separate**, implement **refresh rotation**, map **SCIM attributes** deliberately to your schema, and treat **SCIM errors and idempotency** as first-class. This plan is intentionally complete so you can turn sections into tickets without re-deriving architecture.

---

*Generated as `helper.md` for the SCIM learning project. Adjust URLs, token lifetimes, and ScrambleID auth mechanism per vendor documentation.*
