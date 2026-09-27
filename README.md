# JobPulse

JobPulse collects jobs from company ATS boards, provides full-text search and saved-search alerts, and gives local operators a clear view of ingestion and event publication.

```text
React + Tailwind  →  Spring API  →  PostgreSQL
                           │             │
                           ├─ RabbitMQ workers
                           └─ Kafka outbox → alerts + analytics
```

## Run locally

Requirements: Java 21, Node.js 20+, Docker, and Docker Compose.

```bash
docker compose up -d postgres rabbitmq kafka
./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

Open [http://localhost:5173/jobs](http://localhost:5173/jobs). Vite proxies API and Google sign-in routes to Spring on port 8080. Job search is public; Google sign-in is required for saved searches and alerts.

To use the operations console, configure a Google OAuth web client and set `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `JOBPULSE_ADMIN_EMAILS`, and `JOBPULSE_ADMIN_ENABLED=true`. Admin controllers are disabled by default and require an authenticated admin role when enabled. See [docs/security.md](docs/security.md) for setup, route access, and the CSRF/session model.

The delivery sequence is tracked in [docs/roadmap.md](docs/roadmap.md). The prepared Google Cloud release path, account setup, secrets, costs, and verification steps are in [docs/google-cloud-deployment.md](docs/google-cloud-deployment.md).

Operational metrics, alerts, dashboards, structured logging, tracing, and controlled failure drills are documented in [docs/observability.md](docs/observability.md).
Provider, broker, worker, and outbox recovery procedures are in [docs/runbooks/failure-recovery.md](docs/runbooks/failure-recovery.md), with isolated drill results in [docs/evidence/milestone-12/](docs/evidence/milestone-12/).

## Demo path

1. Open `/admin`, approve a discovered board or enable an existing one, and choose **Run now**.
2. Watch its request move from pending to running to succeeded, then find the posting in `/jobs`.
3. Apply filters, save the search, and open `/alerts`.
4. Ingest a matching fixture and mark its new alert read.
5. Trigger a stubbed provider error, locate the board/run/error in `/admin`, and retry it. Disable the board to stop future scheduling.

## Verification and benchmarks

```bash
./mvnw test
cd frontend && npm run build
```

The repeatable fixture, k6 commands, metrics, and honest reporting rules are documented in [docs/performance.md](docs/performance.md). Existing subsystem notes live in `docs/`.
