# URL Shortener — Service Design Plan

This document defines the application surface for the URL Shortener API (Spring Boot 3 / Java 21). It covers APIs, input validation, data models (DTOs + DAOs), and per-endpoint behavior. Persistence is implemented with Flyway + JPA against App Platform Postgres `url-shortener-aadit-storage`.

---

## 1. APIs required

Base path for management APIs: `/api/v1`. Redirect is root-level for short links.

| # | Method | Path | Purpose |
|---|--------|------|---------|
| 1 | `POST` | `/api/v1/urls` | Create a short URL for a long URL (optional `customCode`) |
| 2 | `GET` | `/api/v1/urls/{code}` | Fetch metadata for a short code (no redirect) |
| 3 | `DELETE` | `/api/v1/urls/{code}` | Deactivate / delete a short URL |
| 4 | `GET` | `/{code}` | Resolve short code and **HTTP 302** redirect to the original URL |
| 5 | `GET` | `/api/v1` | API root (existing scaffold) |
| 6 | `GET` | `/health` | Liveness / readiness (existing scaffold + Actuator) |

**Out of scope for v1 core (except custom codes — see §5):** auth/API keys, analytics dashboards, bulk create, QR codes, expiration updates after create, async create pipelines.

---

## 2. Input validation (per API)

Validation uses `spring-boot-starter-validation` (`@Valid` on request bodies, constraint annotations on DTO fields, path-variable checks in the controller/service).

### 2.1 `POST /api/v1/urls`

| Field | Location | Rules |
|-------|----------|-------|
| `url` | JSON body | Required (`@NotBlank`). Must be absolute HTTP/HTTPS URL (`@URL` or custom validator). Max length **2048**. Reject `javascript:`, `data:`, `file:`, and other non-http(s) schemes. Normalize trailing whitespace; do not accept blank-only. |
| `customCode` | JSON body (optional) | If present: same character/length rules as path `code` (`^[A-Za-z0-9_-]{4,16}$`). Reject reserved names (`health`, `api`, `swagger-ui`, `actuator`, `v3`, …). Must not already exist — see §5. |
| `expiresAt` | JSON body (optional) | If present: ISO-8601 instant (`OffsetDateTime` / `Instant`). Must be strictly in the **future**. |

**Error responses:** `400` for syntax/validation failures. Duplicate `customCode` → **`409 Conflict`** (see §5).

### 2.2 `GET /api/v1/urls/{code}`

| Field | Location | Rules |
|-------|----------|-------|
| `code` | Path | Required. Pattern: `^[A-Za-z0-9_-]{4,16}$` (alphanumeric + `_` / `-`, length 4–16). Reject anything else with `400`. |

**Not found:** valid format but unknown / inactive / expired → `404` / `410` per locked decisions.

### 2.3 `DELETE /api/v1/urls/{code}`

Same path validation as GET metadata (`code` pattern `^[A-Za-z0-9_-]{4,16}$`).

- Unknown code → `404`
- Already deleted / inactive → prefer **idempotent `204`**

### 2.4 `GET /{code}` (redirect)

| Field | Location | Rules |
|-------|----------|-------|
| `code` | Path | Same pattern as above. Invalid format → prefer **`404`** for public redirect. |

Business checks after format OK:

- Missing / inactive → `404`
- Past `expiresAt` → **`410 Gone`**

### 2.5 Meta / health

No request-body validation. Existing responses only.

---

## 3. Data models

### 3.1 DTO — API input & response

Package suggestion: `com.digitalocean.urlshortener.web.dto`

#### Request

```text
CreateUrlRequest
  - url: String              // required, validated
  - customCode: String?      // optional; client-chosen short code
  - expiresAt: Instant?      // optional
```

#### Response

```text
UrlResponse
  - code: String
  - shortUrl: String     // absolute, e.g. https://host/{code}
  - originalUrl: String
  - createdAt: Instant
  - expiresAt: Instant?  // null = never
  - active: boolean
  - clickCount: long

ErrorResponse
  - timestamp: Instant
  - status: int
  - error: String
  - message: String
  - path: String
  - fieldErrors: List<{ field, message }>?  // validation failures
```

`DELETE` returns **empty body** with `204 No Content`.

Redirect (`GET /{code}`) returns **no JSON body** — only `Location` header + `302 Found`.

---

### 3.2 DAO — DB schema, query objects, persistence responses

Package:

- Entity / table mapping: `...persistence.entity`
- Repository: Spring Data JPA

#### DB schema

Table: `short_urls` (Flyway `V1__create_short_urls.sql`)

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `BIGSERIAL` / `BIGINT` PK | Generated |
| `code` | `VARCHAR(16)` | `UNIQUE NOT NULL` |
| `original_url` | `VARCHAR(2048)` | `NOT NULL` |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL`, default `CURRENT_TIMESTAMP` |
| `expires_at` | `TIMESTAMP WITH TIME ZONE` | Nullable |
| `active` | `BOOLEAN` | `NOT NULL`, default `true` |
| `click_count` | `BIGINT` | `NOT NULL`, default `0` |

Indexes:

- Unique constraint on `code` (**authoritative for customCode races**)
- Index on `(active, expires_at)` for cleanup jobs later

#### Persistence / query objects

```text
ShortUrlEntity
ShortUrlRepository   // findByCode, existsByCode, deactivateByCode, incrementClickCountByCode

CreateShortUrlCommand
  - originalUrl: String
  - code: String            // generated OR from customCode
  - expiresAt: Instant?
```

**Code generation (when `customCode` omitted):**

- Generate opaque codes (e.g. Base62 from random bytes).
- Length target: **7–8** characters within the allowed 4–16 window.
- On unique-constraint collision, retry generate + insert (bounded retries).

---

### 3.3 Mapping overview

```text
CreateUrlRequest  →  CreateShortUrlCommand  →  ShortUrlEntity (insert)
ShortUrlEntity  →  UrlResponse
code (path)  →  repository.findByCode  →  redirect Location or UrlResponse
```

---

## 4. What each API handles, and how

### 4.1 `POST /api/v1/urls` — Create

1. Bind JSON → `CreateUrlRequest`; run Bean Validation (`@Valid`).
2. Normalize URL (trim; optionally reject userinfo / credentials in URL).
3. Resolve `code`:
   - If `customCode` present → use after syntax/reserved checks (§5).
   - Else → generate unique `code`.
4. Persist via repository (`CreateShortUrlCommand` → insert).
5. Build `shortUrl` from configured public base URL + `/{code}`.
6. Return **`201 Created`** with `UrlResponse` and `Location: /api/v1/urls/{code}`.

Failure modes: validation `400`; duplicate custom code `409`; auto-generate collision after retries → `503`/`500` (rare).

---

### 4.2 `GET /api/v1/urls/{code}` — Metadata

1. Validate `code` pattern.
2. `repository.findByCode(code)`.
3. If missing or inactive → `404`.
4. If expired → `410`.
5. Map entity → `UrlResponse`; return **`200 OK`**.

Does **not** increment `click_count`.

---

### 4.3 `DELETE /api/v1/urls/{code}` — Deactivate

1. Validate `code` pattern.
2. Soft-delete: set `active = false`.
3. If no row → `404`.
4. If already inactive → **`204`** (idempotent).
5. Success → **`204 No Content`**.

---

### 4.4 `GET /{code}` — Redirect (public)

1. Validate `code` (invalid → `404` for public surface).
2. Load by code; missing / inactive → `404`.
3. Expired → `410 Gone`.
4. Increment `click_count`.
5. Return **`302 Found`** with `Location: {original_url}`. Prefer `Cache-Control: no-cache`.

---

### 4.5 Existing meta endpoints

| Endpoint | Behavior |
|----------|----------|
| `GET /api/v1` | Confirms service identity / docs link. |
| `GET /health` | Process health for App Platform / probes. |
| `GET /api/v1/db-status` | Connectivity ping to Postgres. |

---

## 5. Custom short codes (`customCode`)

### 5.1 Behavior

- Optional field on create.
- Omitted → server auto-generates (existing path).
- Provided → client-chosen code after validation; **no substitution** if taken.

### 5.2 Validation order

1. **Syntax / reserved** → `400 Bad Request` (fail before persistence).
2. **Persist** with that exact `code`.
3. **Uniqueness** enforced by DB `UNIQUE (code)`.

An optional `existsByCode` pre-check is only a fast-path optimization. It is **not** sufficient under concurrency.

### 5.3 Concurrency (two clients, same `customCode`)

```text
Client A ──► syntax OK ──► INSERT code=X ──► 201 Created
Client B ──► syntax OK ──► INSERT code=X ──► unique violation ──► 409 Conflict
```

- Do **not** wait for A and “become the creator” if A fails.
- Do **not** retry custom inserts under a different code.
- Map `DataIntegrityViolationException` / unique constraint on `code` → **`409 Conflict`** with a clear message (e.g. `customCode already exists`).

Auto-generate path may retry on collision; custom path must not.

### 5.4 Parallel different creates (scalability)

Synchronous Spring MVC is enough for v1:

- Each request uses a Tomcat worker thread + Hikari connection.
- Different codes insert independently; Postgres handles parallelism.
- Same code serializes correctly via the unique index.

**Not required for v1:** WebFlux, `@Async` create queues, Redis distributed locks, or message brokers. Revisit only if measured write load demands it.

---

## Suggested package layout

```text
com.digitalocean.urlshortener
  web/
    UrlController
    RedirectController
    dto/
    GlobalExceptionHandler
  service/
    UrlShortenerService
    CodeGenerator
  persistence/
    entity/ShortUrlEntity
    ShortUrlRepository
    DatabasePingService
  config/
```

---

## Implementation sequence

1. ~~Agree DB schema~~ / Flyway `V1` + entity/repository (mirrored from Origin).
2. DTOs + validation + `GlobalExceptionHandler` (including `409` for `customCode`).
3. `UrlShortenerService` (create with optional `customCode` / get / delete / resolve).
4. Wire controllers; keep `/health`, `/api/v1`, `/api/v1/db-status`.
5. Tests: validation, create→redirect, 404/410, delete, **duplicate customCode → 409**.

---

## Open decisions (locked for v1)

| Topic | Decision |
|-------|----------|
| Soft vs hard delete | Soft only (`active = false`); hard delete is a later stage |
| Expired redirect/metadata status | `410 Gone` |
| Public invalid code | `404` |
| Click counting | On redirect only |
| Auth | Not required for v1 |
| Custom short codes | Supported via optional `customCode`; duplicates → **409**; sync request handling |
| Async create pipeline | **No** for v1 |
