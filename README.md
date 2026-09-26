# URL Shortener API

Spring Boot 3 / Java 21 REST API scaffold for DigitalOcean App Platform.

**GitHub:** https://github.com/aaditagarwal/DigitalOcean_URL_Shortener  
**App name:** `url-shortener-aadit` (App Platform requires hyphens; alias for `url_shortener_aadit`)

## Stack

- Java 21, Spring Boot 3.5, Maven
- **Persistence:** Spring Data JPA + HikariCP + PostgreSQL JDBC driver (`org.postgresql:postgresql`)
- **Database:** DigitalOcean App Platform Postgres `url-shortener-aadit-storage`
- Validation + Actuator (includes DB health)
- OpenAPI / Swagger UI (`/swagger-ui.html`)
- Dockerfile-based build on DigitalOcean App Platform
- GitHub Actions CI (Maven test) + CD (upsert/deploy App Platform app)
- Unit tests mock DB calls (no local database required)

## Local run

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # adjust for your machine
mvn test
# App runtime requires Managed Postgres env vars (set by App Platform in deploy):
#   SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD
mvn spring-boot:run
```

- Health (liveness): `GET /health`
- API root: `GET /api/v1`
- DB connectivity: `GET /api/v1/db-status`
- Actuator: `GET /actuator/health`
- Swagger UI: `/swagger-ui.html`
## CI/CD

| Workflow | Trigger | Purpose |
|---|---|---|
| `.github/workflows/ci-cd.yml` | push / PR to `main` | `mvn test` + package; on `main` push, upsert/deploy App Platform app from `.do/app.yaml` |

Required GitHub secret: `DIGITALOCEAN_ACCESS_TOKEN`

## Status

DB connectivity milestone in progress: Managed PostgreSQL attached; domain URL-shortener APIs next.
