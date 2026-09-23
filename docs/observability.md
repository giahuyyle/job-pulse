# JobPulse observability runbook

Milestone 11 adds Prometheus metrics, provisioned Grafana dashboards, ECS JSON logs, and OTLP traces through Tempo. Metric labels are deliberately bounded to provider, outcome, event type, and queue. Identifiers such as `runId`, `boardId`, `requestId`, `eventId`, and `jobId` appear only in logs and traces.

## Start the stack

Copy `.env.example` to `.env` if you want to override the pinned image versions, then run:

```bash
docker compose up -d postgres rabbitmq kafka prometheus tempo grafana
./mvnw spring-boot:run -Dspring-boot.run.profiles=observability
```

Alternatively, run the application with the Compose `app` profile:

```bash
docker compose --profile app up -d
```

Verify:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/prometheus
```

- Prometheus targets and alerts: `http://localhost:9090/targets` and `http://localhost:9090/alerts`
- Grafana: `http://localhost:3000` (`admin` / `admin` by default for local use)
- Tempo API: `http://localhost:3200/ready`

Grafana automatically provisions the Prometheus and Tempo data sources and the **JobPulse Operations** dashboard.

## Controlled failure drills

| Drill | Expected evidence | Recovery evidence |
| --- | --- | --- |
| Stop JobPulse | `up{job="jobpulse"}` becomes `0`; `JobPulseDown` fires after one minute | Target returns to `UP` after restart |
| Configure an invalid board account | `jobpulse_ingestion_runs_total{outcome="failed"}` increases; ECS error includes `runId`, `boardId`, provider, and attempt | A corrected board produces a successful run |
| Stop Kafka | `jobpulse_outbox_publications_total{outcome="failed"}` and `jobpulse_outbox_unpublished` increase | Restart Kafka; unpublished gauge returns to zero |
| Publish a poison ingestion message | `jobpulse_messages_dead_lettered_total{queue="ingestion"}` and the DLQ gauge increase; error log identifies request and board | Replay from the admin dashboard |
| Run `benchmarks/search.js` | Search request rate and p95 panels respond | p95 returns toward the ~0.024-second baseline after load |
| Run `benchmarks/ingestion.py` | Ingestion throughput and outcome panels respond | Queued/running gauges return to zero |

For each drill, record the UTC timestamp, affected component, run/request/event identifier from the structured log, alert state, and recovery time. Payload bodies, credentials, connection strings, authentication tokens, résumés, and full job descriptions must not be logged.

## Local verification record

Verified on 2026-09-23 UTC with the Compose `app` profile:

- Health, liveness, readiness, and Prometheus endpoints returned successfully; the unexposed `/actuator/metrics` endpoint returned `404`.
- Prometheus reported `up{job="jobpulse"} = 1` and loaded all five JobPulse alert rules.
- Grafana provisioned the Prometheus and Tempo data sources and the **JobPulse Operations** dashboard.
- A Greenhouse ingestion request completed successfully with request/correlation ID `67378603-d841-44ff-a97f-d4d8a1b14287` and run ID `6fd37001-0224-4d4d-8eee-31b063e564ad`. Its ECS log included the board, provider, trace, run, request, and outcome counts.
- Tempo trace `80552d67c0222c0f1273df13f2ef167b` followed one Ashby request across the initiating HTTP call, persisted dispatcher continuation, RabbitMQ publish and receive, transactional outbox, 43 Kafka publications, and both Kafka consumers. The request/correlation ID was `69609ea6-fb2d-4480-afa5-aeb9a49c412b` and the ingestion run ID was `2232d171-5cbe-409e-9442-8fcd28cb5421`.
- Stopping JobPulse changed the scrape target to `0`, moved `JobPulseDown` from pending to firing after one minute, and recreating the app from the final image returned the target to `1` and cleared the alert.
