# JobPulse measured results — 2026-09-28

All local suite invariants passed. The initial integration suite completed 15
measured trials, then the database-visible timer was added and verified with 9
additional fan-out trials. Warm-ups are excluded from the tables. The latter
suite's timings cover durable request submission through database-visible alert
completion, including dispatch, ingestion, Kafka, commits and polling overhead.

## Alert fan-out: 100 new postings per burst

| Matching saved searches | Expected and observed alerts per trial | Completion seconds, three trials | Median seconds |
|---:|---:|---|---:|
| 1 | 100 | 4.660, 4.637, 4.050 | 4.637 |
| 10 | 1,000 | 5.419, 3.424, 4.013 | 4.013 |
| 100 | 10,000 | 3.435, 3.514, 3.421 | 3.435 |

At 100 searches, every trial produced exactly 10,000 alerts, 100 postings and
100 events, with both consumer groups processing every event. The longest
complete burst was 3.514 seconds. A conservative supported claim is “10,000
alerts for 100 saved searches in under 3.6 seconds across three local burst tests.”
This is a finite 100-posting burst, not sustained alert throughput or an API p95.

## RabbitMQ worker comparison: 8 boards × 100 postings

| Workers | Postings/second, three trials | Median postings/second |
|---:|---|---:|
| 1 | 347.23, 430.94, 263.40 | 347.23 |
| 4 | 273.10, 402.48, 496.84 | 402.48 |

All six trials persisted 800 postings, with no failed requests or duplicate
posting groups. The application had the same 2-CPU/2-GiB limit in both conditions.
The ranges overlap and trial order was fixed. The workload is short and includes
periodic dispatch delay, while JVM state and host contention vary. These results
do not support a reliable percentage speedup or a sustained capacity claim.
They are not comparable to the older native-JVM 50,000-posting benchmark.

## Kafka outage and replay

- Ingestion completed with Kafka stopped: 100 postings and 100 durable unpublished events.
- From issuing Kafka restart to database-visible completion: **11.904 seconds**,
  including broker startup, retries and 0.5-second polling plus command overhead.
- Recovery produced all 100 publications, 100 consumption records per group,
  and 1,000 alerts for 10 matching searches.
- Controlled replay of all 100 event IDs left postings, alerts and consumption
  totals unchanged. Both Kafka consumer groups reached zero lag before the final
  check. Analytics stayed at 6,000 before and after replay.
- This is one local failure drill and a controlled duplicate replay, not a
  recovery SLA or a simulated full VM crash.

## Live HTTPS: https://jobpulse.page

| Route | Total matching postings | p50 ms | p95 ms | p99 ms | Failures |
|---|---:|---:|---:|---:|---:|
| `/api/v1/jobs?size=20&sort=newest` | 3,006 | 549.97 | 568.93 | 576.48 | 0 / 20 |
| `/api/v1/jobs?query=backend%20engineer&size=20&sort=relevance` | 369 | 697.32 | 745.60 | 764.24 | 0 / 20 |

Two warm-ups and 40 measured GETs were issued sequentially from the user's
machine, interleaving routes with a one-second pause. Timing includes DNS,
fresh TLS, network travel and full response download. Payloads were validated.
This confirms observed public response times and a 3,006-posting live dataset;
it does not measure concurrent production throughput or uptime.

## Conditions and evidence

The app was rebuilt from commit `f4793661c63ae46d130bcbb16737b3f05c8e16a8` (application sources unchanged).
The benchmark scripts and documentation were new working-tree files. Java 21
runs in Docker with PostgreSQL 17.10, RabbitMQ 4 and Kafka 4.1.0. Docker Desktop
had 8 CPUs and 8,218,316,800 bytes of memory. Other local services remained running.
The isolated app was capped at 2 CPUs / 2 GiB, PostgreSQL 2 CPUs / 1 GiB, Kafka
2 CPUs / 1 GiB and RabbitMQ 1 CPU / 512 MiB. Tracing sampling was disabled. The
outbox retained its default 50-event batches and one-second scheduled delay.
Synthetic provider data and SQL-seeded durable requests exclude OAuth, CSRF,
HTTP submission and frontend rendering from local metrics.

Raw records, per-trial counts, timestamps and effective configuration:

- [Initial integration suite](../../benchmarks/results/metrics-local-2026-09-28.json)
- [Database-visible alert rerun](../../benchmarks/results/alert-visible-2026-09-28.json)
- [Live HTTPS samples](../../benchmarks/results/https-live-2026-09-28.json)
- [Harness instructions and limitations](../metrics-benchmarks.md)

## Resume wording supported by these results

- Implemented a transactional outbox and Kafka consumers for saved-search alerts,
  generating **10,000 alerts across 100 searches in under 3.6 seconds** in local
  burst tests; verified duplicate-safe replay of **100 events**.
- Deployed JobPulse to **GCP Compute Engine** with Docker and **GitHub Actions
  CI/CD**, serving **3,000+ live job postings** over HTTPS with Artifact Registry
  releases and scheduled PostgreSQL backups.

The cloud services/release implementation comes from the repository deployment
configuration and the owner's confirmation that the first release completed;
the HTTPS probe independently verifies the live route, job count and responses.
Keep the existing 50,000-posting ingestion and search metrics from their original
benchmark evidence; do not mix local search p95 with live HTTPS p95.
