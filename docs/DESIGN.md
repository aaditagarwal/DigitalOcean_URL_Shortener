# URL Shortener — Service Design Plan

This document defines the application surface for the URL Shortener API (Spring Boot 3 / Java 21). It covers APIs, input validation, data models (DTOs + DAOs), and per-endpoint behavior.

- **v1 (basic app):** server-generated short codes only.
- **v2 (this addendum):** optional client-supplied `customCode` on create, with uniqueness enforced by the DB.

---

## 1. APIs required

Base path for management APIs: `/api/v1`. Redirect is root-level for short links.

| # | Method | Path | Purpose |
|---|--------|------|---------|
| 1 | `POST` | `/api/v1/urls` | Create a short URL (auto-generated or optional `customCode`) |
| 2 | `GET` | `/api/v1/urls/{code}` | Fetch metadata for a short code (no redirect) |
| 3 | `DELETE` | `/api/v1/urls/{code}` | Soft-delete / deactivate a short URL |
| 4 | `GET` | `/{code}` | Resolve short code and **HTTP 302** redirect to the original URL |
| 5 | `GET` | `/api/v1` | API root (existing scaffold) |
| 6 | `GET` | `/health` | Liveness / readiness (existing scaffold + Actuator) |

**Still out of scope:** auth/API keys, analytics dashboards, bulk create, QR codes, expiration updates after create, hard delete, async/queued creates.

---

## 2. Input validation (per API)

Validation uses `spring-boot-starter-validation` (`@Valid` on request bodies, constraint annotations on DTO fields, path-variable checks in the controller/service).

### 2.1 `POST /api/v1/urls`

| Field | Location | Rules |
|-------|----------|-------|
| `url` | JSON body | Required (`@NotBlank`). Absolute HTTP/HTTPS only. Max length **2048**. Reject `javascript:`, `data:`, `file:`, etc. Trim whitespace. |
| `expiresAt` | JSON body (optional) | ISO-8601 instant. If present, must be strictly in the **future**. |
| `customCode` | JSON body (optional, **v2**) | If omitted / null → server generates a code (v1 behavior). If present: see §2.1.1. |

**Error responses:** `400` for invalid input; `409` when a valid `customCode` is already taken (see §5).

#### 2.1.1 `customCode` rules (v2)

| Check | Rule | Status on failure |
|-------|------|-------------------|
| Syntax | Pattern `^[A-Za-z0-9_-]{3,32}$` | `400` |
| Reserved | Must not collide with app routes / well-known segments: `api`, `health`, `actuator`, `swagger-ui`, `v3`, `error`, etc. (maintain an allowlist-deny set in config) | `400` |
| Existence | Must not already exist as a `code` | **`409 Conflict`** |

**Existence must not rely on SELECT-then-INSERT alone** — that races under concurrency. Source of truth: **`UNIQUE` constraint on `short_urls.code`**. Optional `EXISTS` pre-check is only a fast-path optimization; insert + catch unique violation remains mandatory.

### 2.2 `GET /api/v1/urls/{code}`

| Field | Location | Rules |
|-------|----------|-------|
| `code` | Path | Required. Pattern: `^[A-Za-z0-9_-]{3,32}$` (aligned with auto + custom). Invalid → `400` on management APIs. |

**Not found:** valid format but unknown / inactive → `404`. Expired → `410`.

### 2.3 `DELETE /api/v1/urls/{code}`

Same path validation as GET metadata.

- Unknown code → `404`
- Already inactive → idempotent **`204`**

### 2.4 `GET /{code}` (redirect)

| Field | Location | Rules |
|-------|----------|-------|
| `code` | Path | Same pattern. Invalid / unknown → **`404`** (public surface). |

- Inactive → `404`
- Expired → **`410 Gone`**

### 2.5 Meta / health

No request-body validation. Existing responses only.

---

## 3. Data models

### 3.1 DTO — API input & response

Package suggestion: `com.digitalocean.urlshortener.web.dto`

#### Request

```text
CreateUrlRequest
  - url: String            // required, validated
  - expiresAt: Instant?    // optional
  - customCode: String?    // optional (v2); omitted → auto-generate
```

Example:

```json
{
  "url": "https://example.com/long",
  "customCode": "my-link",
  "expiresAt": "2030-01-01T00:00:00Z"
}
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

`DELETE` → `204 No Content` (empty body).  
Redirect → no JSON; `Location` + `302`.

---

### 3.2 DAO — DB schema, query objects, persistence responses

#### DB schema

Table: `short_urls`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `BIGINT` identity PK | Generated |
| `code` | `VARCHAR(32)` | `UNIQUE NOT NULL` — holds auto-gen **and** custom codes |
| `original_url` | `VARCHAR(2048)` | `NOT NULL` |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL`, default `CURRENT_TIMESTAMP` |
| `expires_at` | `TIMESTAMP WITH TIME ZONE` | Nullable |
| `active` | `BOOLEAN` | `NOT NULL`, default `true` |
| `click_count` | `BIGINT` | `NOT NULL`, default `0` |

Indexes:

- Unique on `code` (**correctness for concurrent custom creates**)
- Optional: `(active, expires_at)` for cleanup later

**Migration note (v2):** if v1 shipped `VARCHAR(16)`, add a Flyway migration to widen to `VARCHAR(32)` before accepting longer custom codes. Pattern / path validation must match the column.

#### Persistence / query objects

```text
ShortUrlEntity
ShortUrlRepository      // findByCode, existsByCode, save, deactivateByCode, incrementClickCountByCode

CreateShortUrlCommand
  - originalUrl: String
  - code: String            // either customCode or generated
  - expiresAt: Instant?
  - custom: boolean         // true when client supplied customCode (affects collision handling)

ShortUrlRecord            // read model (may equal entity)
```

**Code assignment:**

| Path | Behavior on unique collision |
|------|------------------------------|
| Auto-generate | Bounded regenerate + retry, then `500` / `503` if exhausted |
| `customCode` | **No retry as a different code** — client asked for that value → map unique violation to **`409`** |

---

### 3.3 Mapping overview

```text
CreateUrlRequest
  ├─ customCode present → validate syntax/reserved → CreateShortUrlCommand(custom=true)
  └─ customCode absent  → CodeGenerator → CreateShortUrlCommand(custom=false)
       → ShortUrlEntity insert
       → UrlResponse
```

---

## 4. What each API handles, and how

### 4.1 `POST /api/v1/urls` — Create

1. Bind JSON → `CreateUrlRequest`; Bean Validation (`@Valid`).
2. Normalize `url` (trim).
3. **Resolve code:**
   - If `customCode` present: syntax + reserved checks → use as `code`.
   - Else: generate opaque Base62 code (target length **7–8**, within 3–32).
4. Persist (`INSERT`).
5. Build `shortUrl` from public base URL + `/{code}`.
6. Return **`201 Created`** + `UrlResponse` + `Location: /api/v1/urls/{code}`.

**Collision handling:**

```text
Client A ──► validate OK ──► INSERT my-link ──► 201 Created
Client B ──► validate OK ──► INSERT my-link ──► unique violation ──► 409 Conflict
```

- Second request does **not** wait for the first.
- Second request does **not** “become the creator” if the first fails mid-flight.
- Validation passing only means input shape is OK; ownership of the code is decided at **persist** by the unique index.

### 4.2 `GET /api/v1/urls/{code}` — Metadata

Lookup only (no click increment) → `200` / `404` / `410`.

### 4.3 `DELETE /api/v1/urls/{code}` — Soft-delete

Set `active = false` → idempotent `204`.

### 4.4 `GET /{code}` — Redirect

Lookup → active/expiry checks → increment `click_count` → `302` + `Location`.

### 4.5 Meta endpoints

`GET /api/v1`, `GET /health` — unchanged.

---

## 5. Error contract (create + customCode)

| Case | Status | Example message |
|------|--------|-----------------|
| Missing / invalid `url`, bad `expiresAt` | `400` | field-level Bean Validation |
| `customCode` syntax invalid | `400` | `customCode` must match `^[A-Za-z0-9_-]{3,32}$` |
| `customCode` reserved | `400` | `customCode` is reserved |
| `customCode` already taken (incl. concurrent loser) | **`409`** | Alias already exists / `customCode` already exists |
| Auto-generate exhausted retries | `500` / `503` | Failed to allocate short code |

Rationale for **`409` vs `400` on duplicate:** `400` = malformed/unacceptable input; `409` = input is valid but conflicts with current resource state. Concurrent duplicate custom creates are a state conflict.

---

## 6. Concurrency & scalability

### 6.1 Same `customCode`, two requests (correctness)

Handled by Postgres **`UNIQUE (code)`**:

1. Both may pass syntax validation.
2. Both attempt `INSERT`.
3. Exactly one commits; the other gets a unique-constraint violation → **`409`**.

No app-level lock, no wait-queue, no “second becomes creator.”

### 6.2 Different codes in parallel (scalability)

**Sync REST is enough for this milestone** — no async / reactive / message queue.

- Each HTTP request uses one servlet worker thread.
- Independent codes → independent inserts; scale with Tomcat thread pool + Hikari pool.
- Same code → serialized by the unique index (correctness), not by application locking.
- One App Platform instance is fine for coding-round load; uniqueness still holds with N app instances later.

| Approach | Needed now? |
|----------|-------------|
| Sync REST + JPA/JDBC + unique constraint | **Yes** |
| `@Async` / WebFlux | No |
| Queue for creates | No |
| Distributed locks (Redis) | No — DB unique already solves the hard case |

Revisit async only for sustained high write QPS, slow side-effects, or multi-region — not this milestone.

---

## Suggested package layout

```text
com.digitalocean.urlshortener
  web/
    UrlController
    RedirectController
    dto/                       // CreateUrlRequest (+ customCode), UrlResponse, ErrorResponse
    GlobalExceptionHandler     // maps DataIntegrityViolation → 409 when custom
  service/
    UrlShortenerService
    CodeGenerator
    ReservedCodeChecker
  persistence/
    entity/ShortUrlEntity
    ShortUrlRepository
  config/
```

---

## Implementation sequence

### v1 (basic)

1. Schema + entity/repository (done / in flight).
2. DTOs + validation + exception handler.
3. Service + controllers (auto-generate only).
4. Tests: validation, create→redirect, 404/410, soft-delete.

### v2 (customCode)

1. Widen `code` to `VARCHAR(32)` if needed; align path regex to `{3,32}`.
2. Add optional `customCode` to `CreateUrlRequest` + reserved-list config.
3. Branch create path: custom → insert once / map unique → `409`; auto → retry generate.
4. Tests: syntax/reserved `400`, concurrent duplicate → one `201` + one `409`, auto path unchanged.

---

## Locked decisions

| Topic | Decision |
|-------|----------|
| Soft vs hard delete | Soft only (`active = false`); hard delete later |
| Expired status | `410 Gone` |
| Public invalid code | `404` |
| Click counting | Redirect only |
| Auth | Not required |
| Optional create field name | **`customCode`** (not `alias` / `shortCode`) |
| Duplicate custom code | **`409 Conflict`** (no wait / no steal) |
| Correctness under race | DB `UNIQUE(code)` + catch integrity violation |
| Parallel different creates | Sync request handling; **no async for v1/v2** |
| Auto-gen collision | Bounded internal retry, then 5xx |
| Custom collision | Fail to client; do not substitute another code |
