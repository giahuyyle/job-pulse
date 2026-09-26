# JobPulse failure recovery

Start with Grafana **JobPulse Operations**, Prometheus `/alerts`, and the `/admin` Overview, Runs, Queues, and Events views. Use `requestId`, `runId`, and `eventId` from structured logs to follow one item. Check `/actuator/health/readiness` before and after recovery. Admin actions are local-only and audited.

## Provider API outage

Inspect `jobpulse_provider_requests_total`, provider retries, timeouts, and circuit state by provider. A 5xx, connection failure, or timeout is transient; a 400/404 board error, 401/403, or malformed payload needs operator correction. Check the run's failure message and the provider's status before changing the board. Let the bounded retry and 30-second circuit recovery work; correct a bad account before using **Run now**. Do not repeatedly queue the same broken board. Verify a later run succeeds and creates no duplicate postings.

## Provider rate limiting

Look for 429 failures and `rate_limit` retries. JobPulse honors `Retry-After` up to five seconds; a longer requested wait fails the run so a later scheduled poll can retry. Reduce the board polling frequency or contact the provider if limits persist. Do not shorten retry delays or create multiple boards for one account. Verify the provider retry rate falls and a later run succeeds.

## PostgreSQL unavailable

Check readiness, datasource errors in structured logs, and the database container. Restore PostgreSQL before attempting admin actions. Uncommitted work rolls back; database failures during message handling route the delivery to the DLQ instead of spinning. Expired request and outbox leases are reclaimed, and PENDING requests whose confirmed delivery is older than five minutes become eligible for republishing. Do not edit request or event rows by hand. Verify pending requests complete, the outbox drains, and unique posting counts remain stable.

## RabbitMQ unavailable

Check `/admin` Queues and pending requests with no `published_at`, plus dispatcher warnings. Requests remain durable in PostgreSQL until RabbitMQ confirms publication. Restore RabbitMQ and let the dispatcher republish due requests. Do not mark requests published manually. Verify requests leave PENDING and no board has two active requests.

## Ingestion DLQ contains messages

Check `jobpulse_messages_dead_letter_count`, the Queues tab, the failed request, run error, and board settings. Correct permanent board errors first. Inspect the exact dead-letter entry before replaying it. Do not replay the whole queue or repeatedly replay an unchanged permanent failure. Verify one new run for the original request ID and one logical posting per source identity.

## Kafka unavailable

Check `jobpulse_outbox_unpublished`, `jobpulse_outbox_failed`, the oldest event age, and outbox publication errors. Job ingestion and posting commits can continue. Restore Kafka and allow due outbox events to publish. Do not delete unpublished rows or create replacement events. Verify backlog falls, published timestamps appear, and alerts/analytics do not double count event IDs.

## Outbox backlog increasing

Compare unpublished count, oldest age, failed count, Kafka health, and publication attempts. Temporary broker errors receive exponential delay up to 15 minutes; ten failed attempts require an audited event retry. Do not lower delays during an outage. Verify the oldest age and pending count return to normal.

## Stuck ingestion leases

Check RUNNING requests, `lease_until`, and `jobpulse_ingestion_leases_reclaimed_total`. Recovery touches only expired leases, returns attempts below three to PENDING, and fails exhausted requests. A worker still holding a valid lease should be left alone. Do not reset every RUNNING row during an app restart. Verify a reclaimed request gets a later successful run without duplicate postings.

## Safe DLQ replay

Select one dead-letter row in `/admin` Queues after fixing its cause. The replay reopens the failed request with its original ID, resets attempts, stores actor/time/reason, and leaves the dead-letter record inspectable. Audit log records the action. Verify the request progresses and the dead-letter row shows its replay timestamp. Do not publish the payload directly from RabbitMQ management.

## Safe outbox retry

Select one failed event in `/admin` Events and choose **Retry**. The action resets its attempt budget and makes it due for the scheduled publisher; it does not send to Kafka synchronously. Verify the event becomes PUBLISHED and consumers have one `(consumer_name, event_id)` record each. Do not modify the immutable payload or delete its original row.

## Verification after recovery

Confirm readiness is healthy, the affected alert clears, pending ingestion and outbox gauges fall, and the audit log captures operator actions. Compare job counts by source identity and event IDs before and after. A transport may deliver twice; one durable job and one consumer side effect per event are the expected result.

## Alert response map

| Alert | First inspection | Recovery check |
| --- | --- | --- |
| `JobPulseDown` | Readiness, app logs | Target returns to UP |
| `JobPulseSearchLatencyHigh` | Search p95 and database load | p95 falls below threshold |
| `JobPulseIngestionFailure`, `JobPulseIngestionFailureRatioHigh` | Runs and provider error class | Later runs succeed |
| `JobPulseProviderCircuitOpen`, `JobPulseProviderRetriesHigh`, `JobPulseProviderTimeoutsHigh` | Provider metrics and board account | Circuit closes, retries fall |
| `JobPulseOutboxBacklog`, `JobPulseOutboxOldestPending`, `JobPulseOutboxFailed` | Events, Kafka, oldest age | Pending and failed counts clear |
| `JobPulseDeadLettersPresent` | Queues and failed request | Each selected entry is resolved |
| `JobPulseLeasesReclaimedRepeatedly` | Worker logs and expired requests | Reclamation rate returns to zero |
