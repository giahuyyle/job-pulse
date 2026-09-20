# JobPulse delivery roadmap

The operations dashboard is intentionally delivered before deployment. Administrative endpoints remain restricted to loopback clients and must not be exposed publicly until authentication and RBAC are complete.

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

11. Metrics, logs, Prometheus, and Grafana
12. Resilience and failure recovery
13. Authentication, RBAC, and API security
14. CI/CD and AWS deployment
15. Production verification and documentation

Do not make `/admin` or `/api/v1/admin/**` publicly reachable before Milestone 13.
