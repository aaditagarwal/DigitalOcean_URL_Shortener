# URL Shortener API

Spring Boot 3 / Java 21 REST API scaffold for DigitalOcean App Platform.

**GitHub:** https://github.com/aaditagarwal/DigitalOcean_URL_Shortener  
**App name:** `url_shortener_aadit`

## Stack

- Java 21, Spring Boot 3.5, Maven
- Validation + Actuator
- OpenAPI / Swagger UI (`/swagger-ui.html`)
- GitHub Actions CI (Maven test) + CD (DigitalOcean App Platform)

## Local run

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # adjust for your machine
mvn test
mvn spring-boot:run
```

- Health: `GET http://localhost:8080/health`
- API root: `GET http://localhost:8080/api/v1`
- Swagger UI: http://localhost:8080/swagger-ui.html

## CI/CD

| Workflow | Trigger | Purpose |
|---|---|---|
| `.github/workflows/ci-cd.yml` | push / PR to `main` | `mvn test` + package; on `main` push, upsert/deploy App Platform app from `.do/app.yaml` |

Required GitHub secret: `DIGITALOCEAN_ACCESS_TOKEN`

## Status

Smoke-test scaffold only (`/health`, `/api/v1`, Swagger). Full URL-shortener design comes next.
