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

Open [http://localhost:5173/jobs](http://localhost:5173/jobs). Vite proxies `/api` and `/actuator` to Spring on port 8080. The operations console at `/admin` and every admin mutation are restricted by the backend to loopback clients; this local restriction is not a substitute for production authentication.

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
