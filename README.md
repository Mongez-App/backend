# SmartStudy — backend

Spring Boot microservice backend for SmartStudy, a study-planning assistant that
turns a student's course material into a schedule and an AI tutor grounded in
that material.

## Services

| Module | Port | Responsibility |
| --- | --- | --- |
| `eureka` | 8761 | Service discovery. |
| `api-gateway` | 8080 | Single entry point; routes to services via Eureka. |
| `identity-service` | 8081 | Authentication (Firebase), users, study preferences. |
| `planning-service` | 8082 | Courses, tasks, events, teams, the material-processing pipeline and the AI chat tutor. |
| `shared-lib` | — | Shared DTOs and utilities. |

External dependencies: PostgreSQL, Qdrant (vector store for retrieval), and the
Gemini API with OpenRouter as a fallback provider.

## Running locally

```bash
docker compose up --build
```

`docker-compose.yml` is the local stack; `docker-compose.prod.yml` mirrors the
production topology. Set `GEMINI_API_KEY` in your environment before starting,
or planning-service will refuse to start with
`GEMINI_API_KEY is not configured`.

Build and test a single service without Docker:

```bash
mvn -pl :planning-service -am test
```

## Deployment

Deployed on Railway. **See [docs/railway-deployment.md](docs/railway-deployment.md)** for
service settings, the full environment-variable list, Qdrant setup, and the
config-as-code trap that has already caused one production outage.

## Secrets

Never commit key values, including as YAML defaults. All credentials come from
environment variables — Railway service variables in production, your shell or
an ignored `*.env` file locally.
