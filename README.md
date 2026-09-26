# URL Shortener API

Spring Boot 3 / Java 21 REST API on DigitalOcean App Platform.

**Repo:** https://github.com/aaditagarwal/DigitalOcean_URL_Shortener  
**App:** `url-shortener-aadit`

## Run locally

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn test
mvn spring-boot:run
```

| | |
|---|---|
| Health | `GET /health` |
| API | `GET /api/v1` |
| Swagger | http://localhost:8080/swagger-ui.html |
| OpenAPI | `/v1/api-docs` |

## Config

| Variable | Purpose |
|----------|---------|
| `PORT` | HTTP port (default `8080`) |
| `SPRING_DATASOURCE_URL` | JDBC URL (App Platform Managed Postgres) |
| `SPRING_DATASOURCE_USERNAME` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `PUBLIC_BASE_URL` | Absolute origin for short links (optional) |
| `DIGITALOCEAN_ACCESS_TOKEN` | GitHub Actions CD secret |

## Docs

- [Basic design](docs/DESIGN.md) — endpoints, happy paths, component flow
- [Deep dive](docs/DESIGN-DEEP-DIVE.md) — validation, models, concurrency, edge cases
