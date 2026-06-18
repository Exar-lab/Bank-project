# Banco Service

> Java 24 · Spring Boot 4.0.2 · MySQL · Kafka · Flyway · JWT

Banco Service is a banking backend built with Hexagonal Architecture, DDD, and a feature-first package structure under `com.banco.co.*`. This README focuses on the local setup path, runtime dependencies, environment variables, and verification commands.

## Quick Path

1. Install the prerequisites: JDK 24, Docker Desktop or Docker Engine with Compose, and Git.
2. Copy the environment file: `cp .env.example .env` or `Copy-Item .env.example .env`.
3. Start local infrastructure: `docker compose up -d mysql kafka`.
4. Run the application from the repo root:
   - Linux/macOS: `./banco-service/mvnw -f banco-service/pom.xml spring-boot:run`
   - Windows: `banco-service\mvnw.cmd -f banco-service/pom.xml spring-boot:run`
5. Check health: `curl http://localhost:8080/actuator/health`

## Prerequisites

- JDK 24 or newer
- Docker + Docker Compose
- Maven Wrapper is included in `banco-service/` so a system Maven installation is not required
- Optional: Postman and Python 3 for docs verification

## Local Runtime Dependencies

| Dependency | Purpose | Local endpoint | Start command |
|------------|---------|----------------|---------------|
| MySQL 8.0 | Primary database for JPA and Flyway | `localhost:3306` | `docker compose up -d mysql` |
| Kafka 3.7.0 | Event publishing and consumption | `localhost:9092` | `docker compose up -d kafka` |
| SMTP server | Email delivery for notification flows | not bundled | provide your own SMTP server or matching test double |

The Compose file defines MySQL and Kafka only. Email support is configured in the app, but no SMTP container is bundled here.

## Environment Variables

`application.yml` reads configuration from environment variables and the shared `.env` file via `springboot4-dotenv`.

| Variable | Required | Default | Purpose | Safety notes |
|----------|----------|---------|---------|--------------|
| `DB_URL` | Yes | none | JDBC URL for the MySQL database | Do not commit real credentials |
| `DB_USERNAME` | Yes | none | Database username | Keep local-only |
| `DB_PASSWORD` | Yes | none | Database password | Keep local-only |
| `JWT_SECRET_KEY` | Yes | none | Signing key for JWT tokens | Treat as a secret |
| `ISSUER_GENERATOR` | Yes | none | JWT issuer claim | Keep consistent across local clients |
| `JASYPT_ENCRYPTOR_PASSWORD` | Yes | none | Decrypts encrypted properties | Treat as a secret |
| `MAIL_HOST` | Yes | none | SMTP host for email sending | Required for email flows |
| `MAIL_USERNAME` | Yes | none | SMTP username | Required for email flows |
| `MAIL_PASSWORD` | Yes | none | SMTP password | Required for email flows |
| `KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9092` | Kafka bootstrap server list | Safe to keep at the default for local runs |
| `SPRINGDOTENV_DIRECTORY` | No | `..` | Directory containing `.env` when starting from `banco-service/` | Keep it pointed at the repo root |
| `MAIL_ENABLED` | No | `true` | Enables mail integration | Set to `false` only if you intentionally disable email flows |
| `MAIL_PORT` | No | `587` | SMTP port | Match your SMTP server |
| `MAIL_FROM` | No | `no-reply@banco.co` | Default sender address | Use a non-production value locally |
| `MAIL_RELAY_BATCH_SIZE` | No | `50` | Batch size for mail relay | Tuning knob |
| `MAIL_RELAY_POLL_DELAY_MS` | No | `10000` | Poll interval for mail relay | Tuning knob |
| `MAIL_RELAY_MAX_ATTEMPTS` | No | `5` | Retry budget for mail relay | Tuning knob |
| `MAIL_EXECUTOR_CORE_POOL_SIZE` | No | `2` | Core pool size for mail executor | Tuning knob |
| `MAIL_EXECUTOR_MAX_POOL_SIZE` | No | `8` | Max pool size for mail executor | Tuning knob |
| `MAIL_EXECUTOR_QUEUE_CAPACITY` | No | `500` | Queue capacity for mail executor | Tuning knob |

Compose-only variable:

- `MYSQL_ROOT_PASSWORD` is required by `docker-compose.yml` for the MySQL container.

## Profiles and Configuration Notes

- There is a single `banco-service/src/main/resources/application.yml`; no profile-specific YAML files are present.
- The app starts with the same config file for local runs, and `SPRINGDOTENV_DIRECTORY` defaults to the parent directory so `banco-service/` can load the repo-level `.env` file.
- `spring.jpa.hibernate.ddl-auto=validate`, so the schema must exist before the app starts. Flyway provides the schema migrations.
- Kafka defaults to `localhost:9092`.
- Fraud risk profiling is disabled by default in `application.yml`.

## Start MySQL and Kafka Locally

```bash
docker compose up -d mysql kafka
docker compose ps
docker compose logs -f mysql kafka
```

To reset local data:

```bash
docker compose down -v
```

## Run the Application

```bash
./banco-service/mvnw -f banco-service/pom.xml spring-boot:run
```

Windows PowerShell or CMD:

```bat
banco-service\mvnw.cmd -f banco-service/pom.xml spring-boot:run
```

Expected health check:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## Tests and Verification

### Application tests

```bash
./banco-service/mvnw -f banco-service/pom.xml test
./banco-service/mvnw -f banco-service/pom.xml verify
```

### API docs consistency check

This check is useful for detecting OpenAPI/Postman drift. It currently reports a known mismatch until the API documentation parity work is completed.

```bash
python -m unittest docs/verification/test_api_docs_consistency.py -v
```

### Markdown sanity check

```bash
git diff --check -- README.md
```

## Documentation Links

| Document | Purpose |
|----------|---------|
| [`docs/api/openapi.yaml`](docs/api/openapi.yaml) | OpenAPI specification for the REST API |
| [`docs/postman/banco-service.postman_collection.json`](docs/postman/banco-service.postman_collection.json) | Postman collection for the full API |
| [`docs/postman/environments/Local.postman_environment.json`](docs/postman/environments/Local.postman_environment.json) | Local Postman environment values |
| [`docs/api/coverage-report.md`](docs/api/coverage-report.md) | OpenAPI/Postman coverage summary |
| [`docs/OUTBOX.md`](docs/OUTBOX.md) | Transactional Outbox architecture |
| [`docs/email-notification-system.md`](docs/email-notification-system.md) | Email outbox and notification flow |
| [`docs/README-antifraud-risk-profile-gate.md`](docs/README-antifraud-risk-profile-gate.md) | Antifraud risk profile gate architecture |
| [`docs/adr-0001-security-authorities-scope-convergence.md`](docs/adr-0001-security-authorities-scope-convergence.md) | Security naming ADR |
| [`docs/diagrams/`](docs/diagrams/) | Architecture and flow diagrams |

## Checklist

- [ ] `.env.example` was copied to `.env`
- [ ] MySQL and Kafka are running locally
- [ ] The app starts successfully on port `8080`
- [ ] `/actuator/health` returns `UP`
- [ ] `python -m unittest docs/verification/test_api_docs_consistency.py -v` has been reviewed; it currently reports known OpenAPI/Postman drift
- [ ] `git diff --check -- README.md` passes
