# Additional resume metrics

These harnesses measure the real application, not mocked Java methods. They do
not modify application behavior or weaken authentication. Keep local benchmark
claims distinct from deployed HTTPS measurements.

## Isolated integration suite

From the repository root:

```bash
docker compose -p jobpulse-metrics -f benchmarks/compose.metrics.yaml build app
python3 -m unittest discover -s benchmarks -p 'test_*.py'
python3 benchmarks/metrics.py --repeats 3 \
  --output benchmarks/results/metrics-local-YYYY-MM-DD.json
docker compose -p jobpulse-metrics -f benchmarks/compose.metrics.yaml stop
```

The harness uses only project `jobpulse-metrics` and database `jobpulse_metrics`.
The normal local stack and GCP deployment are not targets. It starts a synthetic
Greenhouse provider on port 18090; the isolated API listens on localhost:18082.
Docker Desktop's `host.docker.internal` is required for the provider. Infrastructure
has no host ports. Each run uses a unique company and board prefix. The database
persists between invocations; use a fresh suite volume for strict comparisons.
After stopping the stack, remove only its volumes if a fresh fixture is needed:

```bash
docker compose -p jobpulse-metrics -f benchmarks/compose.metrics.yaml down -v
```

Do not run two copies of the harness simultaneously. A failed run saves partial
results with `status: failed` and an error; partial results are not a passing suite.
The harness leaves its stack running for diagnosis. Stop it with the command above.

### Alert fan-out

One board supplies 100 new postings per trial. Compare 1, 10, and 100 enabled,
matching saved searches, representing distinct synthetic owners. Three trials
per condition follow a JVM/provider/DB warm-up. Each trial must produce exactly
100 creation events, 100 consumption records in each consumer group, and
`100 × searches` alert rows. All earlier searches are disabled before a trial.

Record p50/p95/p99 for event publication, consumer start, and last alert timestamp
per event. Percentiles use continuous interpolation over raw event samples.
`event_to_last_alert_timestamp_ms` starts at the posting observation timestamp
inside the ingestion transaction and ends at the last alert's application timestamp
before its transaction commits. It includes ingestion persistence, polling, outbox,
Kafka and matching work. It is **not** an HTTP inbox or transaction-commit latency.
The harness separately verifies that all expected alerts become database-visible.
`request_submission_to_database_visible_seconds` times the complete burst with
a monotonic client clock, including SQL submission, queue dispatch, ingestion,
publication, consumer commits and polling overhead. This is an upper bound for
the last alert becoming visible, not a per-alert percentile. Use `--scenario
fanout` to rerun only the fan-out cases; `workers` and `recovery` are also supported.

### Worker concurrency

Compare 1 and 4 RabbitMQ listeners with the same 2-CPU/2-GiB application limit,
using 8 boards × 100 new postings per trial and three trials per condition.
Saved searches are disabled. Throughput uses database request timestamps:
actual persisted postings divided by `last finished_at - first created_at`.
This includes queueing and dispatch. Per-request queue and service percentiles
are recorded separately. Kafka consumers must drain before the next trial.

The trial order is fixed (one worker first, then four). The application is
recreated and warmed before four-worker trials. JVM warm-up, growing database
size, scheduling phase, and other Docker workloads can influence the comparison;
report all trials and their range, rather than asserting a causal speedup from
the fastest pair. Larger repeated workloads and alternating trial order are
needed before claiming sustained scaling or a capacity limit.

### Kafka outage and duplicate replay

Stop only the isolated broker. Ingest 100 postings with 10 saved searches and
wait for a real failed publish attempt. Verify ingestion succeeds and all events
remain unpublished. Restart the broker and measure time from issuing the restart
to all 100 publications, both consumer groups' consumption records and all 1,000
alerts becoming visible. This includes broker startup and the application's real
retry delay, plus Docker/SQL polling overhead. Polling interval is 0.5 seconds;
this is one recovery drill, not a recovery SLA.

Republish those 100 event IDs through the actual outbox. Wait for zero Kafka lag
in both groups before checking that posting, alert, consumption and analytics
totals remain unchanged. This is controlled event replay, not a process crash.

### Environment and scope

The JSON includes the commit, dirty-file list, Docker CPU/memory allocation,
effective Compose configuration, PostgreSQL version, image identity, raw request
and event timestamps, per-trial counts, and failure state. CPU/memory limits are
declared in the Compose file. Other local services may compete for host resources.
The default application outbox publishes batches of up to 50 with a one-second
delay; no publish interval or batch tuning is performed for these measurements.
Tracing sampling is disabled; this differs from the earlier search benchmark.

Fixtures insert saved searches and durable ingestion requests using SQL, then
exercise the actual dispatcher, RabbitMQ workers, ingestion writer, outbox, Kafka
and consumer transactions. HTTP submission, OAuth/CSRF and frontend costs are
excluded. Disabled board scheduling prevents unrelated ingestion.

## Deployed HTTPS probe

```bash
python3 benchmarks/https_probe.py --base-url https://jobpulse.page --samples 20 \
  --output benchmarks/results/https-live-YYYY-MM-DD.json
```

This performs two warm-up GETs and 20 measured requests for each of newest jobs
and keyword search, interleaving the routes with a one-second pause. It records
status, payload validity, returned/total job counts, response size, and wall-clock
p50/p95/p99 including DNS, a fresh TLS connection, network travel and body download.
Errors are retained and cause a nonzero exit status; success percentiles exclude
errors explicitly. No writes, credentials or provider calls are issued.

Report client location and actual dataset size. A small sequential sample can
support an observed HTTPS response-time claim. It does not demonstrate concurrent
throughput, server-only latency, uptime, or performance across all of Canada.

## Results

See [the dated results](../benchmarks/results/) and the measured summary linked
from [performance.md](performance.md). Use only claims supported by passing runs.
The [2026-09-28 summary](evidence/performance-2026-09-28.md) contains the completed
alert, worker, recovery/replay, and public HTTPS measurements.
