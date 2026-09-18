# Kafka job-change events

RabbitMQ carries ingestion commands; Kafka carries immutable facts about job
changes. PostgreSQL remains the source of truth through the `job_events`
outbox, so ingestion can continue while Kafka is unavailable.

## Run locally

```bash
docker compose up -d
./mvnw spring-boot:run
```

The host application connects to `localhost:9092`. A containerized application
uses `kafka:29092`; start it instead with
`docker compose --profile app up -d`. The app profile also uses the internal
PostgreSQL and RabbitMQ addresses. The Compose broker uses KRaft and declares
no ZooKeeper dependency.

JobPulse creates these three-partition local-development topics:

- `jobpulse.job-events.v1`
- `jobpulse.job-events.v1.DLT`

## Delivery model

An ingestion transaction writes the job and an event snapshot together. The
outbox publisher uses the job ID as the Kafka key, waits for broker
confirmation, and then records `published_at`. Kafka downtime therefore leaves
events pending for retry. A crash after the broker accepts a record but before
the database commit can publish it twice.

The `jobpulse-alerts` and `jobpulse-analytics` groups consume independently.
Each records `(consumer_name, event_id)` in the same database transaction as
its side effect, making replay safe. Invalid records receive two retries before
being recovered to the dead-letter topic.

The analytics projection counts `CREATED` and `CLOSED` events from the point
event publishing is enabled; no historical backfill is performed beyond
pending milestone 8 creation events. Inspect it with:

```bash
curl 'http://localhost:8080/api/v1/analytics/jobs/daily?from=2026-09-01&to=2026-09-30'
```
