# URL Shortener — Basic Design

High-level view of the service: endpoints, happy paths, and component flow.

For validation rules, error contracts, concurrency, and DTOs/DAOs see [DESIGN-DEEP-DIVE.md](DESIGN-DEEP-DIVE.md).

---

## API endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/urls` | Create short URL (auto code or optional `customCode`) |
| `GET` | `/api/v1/urls/{code}` | Metadata (no click increment) |
| `DELETE` | `/api/v1/urls/{code}` | Soft-delete (`active = false`) |
| `GET` | `/{code}` | **302** redirect to original URL (increments clicks) |
| `GET` | `/api/v1` | API root |
| `GET` | `/health` | Liveness |

---

## Happy paths

### Create (auto)

```http
POST /api/v1/urls
{"url":"https://example.com/long"}
→ 201
{
  "code": "Ab12Cd34",
  "shortUrl": "https://host/Ab12Cd34",
  "originalUrl": "https://example.com/long",
  "createdAt": "...",
  "active": true,
  "clickCount": 0
}
```

### Create (custom)

```http
POST /api/v1/urls
{"url":"https://example.com/long","customCode":"my-link"}
→ 201  (code / shortUrl use my-link)
```

### Metadata

```http
GET /api/v1/urls/Ab12Cd34
→ 200  (same shape as create; clickCount unchanged)
```

### Redirect

```http
GET /Ab12Cd34
→ 302  Location: https://example.com/long
(clickCount++)
```

### Delete

```http
DELETE /api/v1/urls/Ab12Cd34
→ 204
(later GET metadata / redirect → 404)
```

---

## Component flow

```text
Client
  │
  ▼
UrlController / RedirectController     (HTTP, validation, OpenAPI)
  │
  ▼
UrlShortenerService                    (create / get / delete / resolve)
  │                    │
  ├─ CodeGenerator     ├─ ReservedCodeChecker
  ▼                    ▼
ShortUrlRepository  ←→  short_urls (Postgres via Flyway + JPA)
```

| Step | Components |
|------|------------|
| Create | Controller → validate DTO → Service → generate or use `customCode` → Repository `save` → `UrlResponse` |
| Metadata | Controller → Service `getMetadata` → Repository `findByCode` → `UrlResponse` |
| Redirect | RedirectController → Service `resolveForRedirect` → increment clicks → `302` + `Location` |
| Delete | Controller → Service `delete` → Repository `deactivateByCode` → `204` |

Expiry on read: if past `expiresAt`, service soft-deletes then returns `410` (later reads `404`).
