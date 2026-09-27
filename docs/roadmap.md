# JobPulse delivery roadmap

The operations dashboard and Google sign-in are implemented locally. A Google Cloud deployment path is prepared; the first live release requires a project, domain, credentials, and production verification.

## Milestone 10 — Admin operations dashboard

Status: implemented locally.

- Operational overview for boards, ingestion outcomes, RabbitMQ, DLQ, outbox, and Kafka publication failures.
- Board creation, enable/disable, polling interval changes, manual runs, and board run history.
- Run/request inspection, correlation IDs, failed-run retry, pending-request cancellation, and stored run log entries.
- DLQ inspection/replay and transactional outbox inspection/filter/retry through Spring Boot only.
- Posting inspection with source identity, fingerprint, raw snapshot, normalized data, event history, last modifying run, manual close, and reprocessing.
- Discovery candidate test, approval, and rejection with reviewer metadata.
- Immutable audit entries for administrative mutations.

## Remaining order

11. Metrics, logs, Prometheus, Grafana, and Tempo — implemented locally
12. Resilience and failure recovery — implemented locally
13. Authentication, RBAC, and API security — implemented locally
14. CI/CD and Google Cloud deployment — implementation prepared; live deployment pending account setup
15. Production verification and documentation

Before directing public traffic to JobPulse, verify TLS, proxy routing, private infrastructure, secrets, and backups using the [Google Cloud deployment guide](google-cloud-deployment.md).
