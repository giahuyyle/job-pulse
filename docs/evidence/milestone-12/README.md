# Milestone 12 recovery drill evidence

The drill ran on 2026-09-26 UTC in a separate `jobpulse-m12` Compose project, using app port 18080, Prometheus 19090, Grafana 13000, and a controlled fake provider on 18081. The existing `jobpulse` Compose project was not stopped. The isolated app was built from the working tree and passed readiness checks. Prometheus reported its target UP. `promtool check rules` reported 12 valid rules; Grafana loaded the 16-panel JobPulse Operations dashboard.

The text files record the observed IDs, states, counters, and recovery results. The screenshot shows the dashboard after recovery. The fake provider and isolated database were used for every injected failure. The worker-crash and Kafka post-acknowledgement simulations advanced only isolated lease fields to avoid waiting for the production lease duration.

After the final lease-owner and circuit-state changes, the isolated stack was rebuilt and checked again: readiness returned HTTP 200, Flyway migration 16 applied, Prometheus exposed `closed`, `half_open`, and `open` circuit series for each provider, and RabbitMQ declared the primary, 5-second retry, 30-second retry, and DLQ queues. Request `11672bef-d58f-4177-a71e-c2a3f016f57d` reached `SUCCEEDED` on attempt 1; its lease owner was cleared, one posting existed, and its outbox event was `PUBLISHED`.
