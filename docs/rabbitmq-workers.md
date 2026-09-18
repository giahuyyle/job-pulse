# RabbitMQ ingestion workers

JobPulse records each board ingestion as a PostgreSQL request before it sends
anything to RabbitMQ. The outbox dispatcher publishes only the request UUID,
and the worker loads the registered board before invoking the existing provider
adapter.

## Run locally

Start PostgreSQL and RabbitMQ:

```bash
docker compose up -d
```

Run JobPulse from IntelliJ or with:

```bash
./mvnw spring-boot:run
```

The defaults are intended only for local development:

- PostgreSQL: `localhost:5433`
- RabbitMQ AMQP: `localhost:5672`
- RabbitMQ management UI: `http://localhost:15672`
- RabbitMQ username/password: `jobpulse` / `jobpulse`

Override `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, and
`RABBITMQ_PASSWORD` outside local development. A containerized API should use
`RABBITMQ_HOST=rabbitmq` on the Compose network.

## Request an ingestion

The board must already exist in the ingestion target registry.

```bash
curl -i -X POST http://localhost:8080/api/v1/ingestions \
  -H 'Content-Type: application/json' \
  --data '{"source":"ASHBY","sourceAccount":"openai"}'
```

The endpoint returns `202 Accepted` and an ingestion request ID. Check it with:

```bash
curl http://localhost:8080/api/v1/ingestion-requests/{request-id}
```

Requests move through `PENDING`, `RUNNING`, and either `SUCCEEDED` or `FAILED`.
The worker retries failures up to three total attempts. Exhausted messages are
rejected to `jobpulse.ingestion.dlq`. A periodic recovery task resets expired
worker leases for safe outbox republishing.

The scheduler and manual endpoint both deduplicate outstanding work per board.
Publisher confirms are required before `published_at` is stored, so a broker
outage leaves the request visible and eligible for a later dispatch.
