# Railway deployment

How this monorepo is deployed, and the traps that have already cost us a
production outage. Read this before changing service settings.

## How services are built

Each service is its own Railway service, sourced from the **GitHub repo**
(`Mongez-App/backend`), not from a pre-built Docker image.

| Setting | Value | Why |
| --- | --- | --- |
| Root Directory | `/` (unset) | Every Dockerfile does `COPY . .` then `mvn -pl :<service> -am`, so the build context must contain the parent `pom.xml` and `shared-lib`. Pointing Root Directory at the service subdirectory breaks the Maven reactor build. |
| Builder | `Dockerfile` | Leaving this on auto-detect risks a Nixpacks build that silently differs from what we test locally. |
| Dockerfile Path | `/<service>/Dockerfile` | Repo-root-relative, matching the build context above. |

## Config as code

Railway reads `railway.json` from the service's **Root Directory**. Ours is `/`,
so by default every service reads the root [`railway.json`](../railway.json) —
the *same* file. Per-service settings therefore cannot live there: putting
planning-service's `dockerfilePath` at the root would make eureka, api-gateway
and identity-service all try to build planning-service.

Instead, each service keeps its own file and Railway is pointed at it:

> Service → Settings → Config as Code → path = `planning-service/railway.json`

**If that path is not set, the file is silently ignored.** It will look like the
service has a healthcheck and a required volume when it has neither. That is
not hypothetical — it is how a startup crash reached production unnoticed (see
below).

[`planning-service/railway.json`](../planning-service/railway.json) declares:

- `healthcheckPath: /actuator/health` — Railway withholds traffic until the app
  reports healthy and **fails the deploy** if it never does. Without it, Railway
  marks a deploy live as soon as the container starts, so an app that crashes
  during Spring startup still gets routed traffic and returns errors.
- `requiredMountPath: /data` — refuses to deploy unless a volume is attached.
  Uploaded PDFs live under `/data/materials`; without the volume they are
  written to the container filesystem and lost on every deploy.
- `restartPolicyType: ON_FAILURE`

Note that `/actuator/health` aggregates all Spring health indicators, including
the datasource and Eureka discovery. If a dependency is briefly down, the
deploy fails rather than going live degraded. That is usually what we want, but
if deploys become flaky this is the first thing to look at.

## planning-service variables

Set these on the Railway service. **Never commit key values** — a Gemini and an
OpenRouter key were once added as YAML defaults and are still recoverable from
git history.

| Variable | Notes |
| --- | --- |
| `GEMINI_API_KEY` | Read by the **chat tutor** (`GeminiChatClient`, own `RestClient`). |
| `SPRING_AI_GOOGLE_GEMINI_API_KEY` | Read by **task generation** (`StudyPlannerAgent`, Spring AI `ChatClient`). A different Gemini path with a different key — setting one does **not** cover the other, and the two features fail independently. |
| `OPENROUTER_API_KEY` | Without it the OpenRouter fallback throws `AI_AUTHENTICATION_FAILED` instead of answering. |
| `SPRING_PROFILES_ACTIVE` | Must be `prod`, or `application-prod.yml` never loads and Qdrant resolves to `localhost`. |
| `QDRANT_HOST` | `qdrant.railway.internal` |
| `QDRANT_PORT` | `6334` — gRPC. `6333` is the HTTP port; the client is a `QdrantGrpcClient`. |
| `MATERIAL_STORAGE_DIR` | `/data/materials`, on the mounted volume. |
| `POSTGRES_*`, `PLANNING_DB_NAME`, `EUREKA_*` | Standard wiring. |

Those first two variables are the outage referenced above: only
`SPRING_AI_GOOGLE_GEMINI_API_KEY` was set, `gemini.api-key` was bound to
`${GEMINI_API_KEY}` with no default, the placeholder could not resolve, and the
context failed to start. Every chat request errored at the gateway. The key now
carries an empty default so a missing value surfaces as an explicit
`GEMINI_API_KEY is not configured` message instead of a placeholder stack trace.

The two names are genuinely both needed. Gemini is reached through two unrelated
code paths — the chat tutor's hand-rolled `RestClient` and the planner agent's
Spring AI `ChatClient` — each with its own key property and its own model
setting (`gemini.chat.model` and `spring.ai.google.genai.chat.options.model`).
Chat can work perfectly while task generation is broken, and vice versa. Check
both before concluding "the AI is down".

## Qdrant

Qdrant is third-party, so unlike our services it is created from a **Docker
Image** source, not the GitHub repo.

1. New → Docker Image → `qdrant/qdrant:v1.13.2` (matching `docker-compose.prod.yml`).
2. **Name the service exactly `qdrant`** — private DNS is
   `<service-name>.railway.internal`, which is what `application-prod.yml`
   resolves.
3. Set `QDRANT__SERVICE__HOST=::` and `QDRANT__SERVICE__GRPC_PORT=6334`.
   Railway's private network is IPv6-only; Qdrant binds IPv4 `0.0.0.0` by
   default and will start healthy but stay unreachable.
4. Attach a volume at `/qdrant/storage`, or the vector index is wiped on every
   redeploy.
5. Do not generate a public domain. It holds indexed student material and has no
   authentication in front of it.

**Then redeploy planning-service.** `QdrantIndexingService.initCollection()` is
`@PostConstruct` — it runs once at startup and swallows its own failures. Qdrant
can be perfectly healthy and the `material_chunks` collection will still not
exist until planning-service restarts. Look for:

```
Creating Qdrant collection 'material_chunks' (dim=768, distance=Cosine)
```

Material uploaded before Qdrant existed was never indexed and, with
`processing.max-retries: 3`, may have exhausted its retries. Re-upload to
confirm the pipeline end to end.

## Verifying the AI pipeline

Startup log line confirming the key loaded:

```
Gemini REST client configured — API key: AIza...xxxx (length=39)
```

Then send a chat message. Assistant responses carry `used_context`,
`confidence` and `sources`. `used_context: true` with a populated `sources`
array means retrieval is working; `used_context: false` with no sources means
the answer came from the model's general knowledge, not the course material.
