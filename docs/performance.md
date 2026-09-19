# Performance benchmarks

These benchmarks use a dedicated `jobpulse_benchmark` database. Never point the
commands below at the normal development database. Record the machine, Docker
resource limits, PostgreSQL version, application commit, and worker count with
every result.

## Start the benchmark stack

Start infrastructure and create the isolated database (the repository exposes
PostgreSQL on host port `5433`):

```bash
docker compose up -d postgres rabbitmq kafka
docker compose exec postgres createdb -U jobpulse jobpulse_benchmark
```

Start the application locally so Flyway creates the benchmark schema:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5433/jobpulse_benchmark \
DATABASE_USER=jobpulse \
DATABASE_PASSWORD=jobpulse \
./mvnw spring-boot:run
```

## Search latency across 50,000 postings

Seed exactly 50,000 deterministic postings:

```bash
docker compose exec -T postgres psql \
  -U jobpulse -d jobpulse_benchmark -v rows=50000 \
  < scripts/seed-benchmark.sql
```

Verify the fixture:

```bash
docker compose exec postgres psql -U jobpulse -d jobpulse_benchmark \
  -c "SELECT count(*) FROM job_postings WHERE source_account LIKE 'benchmark-board-%';"
```

Warm the JVM, connection pool, and PostgreSQL cache. Discard this result:

```bash
BASE_URL=http://localhost:8080 RATE=10 DURATION=30s \
  k6 run benchmarks/search.js
```

Run controlled arrival-rate tests and retain the summaries:

```bash
mkdir -p benchmarks/results
BASE_URL=http://localhost:8080 RATE=25 DURATION=5m k6 run \
  --summary-export=benchmarks/results/search-50k-25rps.json \
  benchmarks/search.js
BASE_URL=http://localhost:8080 RATE=50 DURATION=5m k6 run \
  --summary-export=benchmarks/results/search-50k-50rps.json \
  benchmarks/search.js
BASE_URL=http://localhost:8080 RATE=100 DURATION=5m k6 run \
  --summary-export=benchmarks/results/search-50k-100rps.json \
  benchmarks/search.js
```

The run fails its thresholds if p95 is at least 250 ms, the error rate reaches
1%, or any iterations are dropped. A rate with dropped iterations is not
sustained throughput.

Inspect the actual full-text query plan:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, title, company, location
FROM job_postings
WHERE status = 'ACTIVE'
  AND search_vector @@ websearch_to_tsquery('english', 'backend engineer')
ORDER BY
  ts_rank(search_vector, websearch_to_tsquery('english', 'backend engineer')) DESC,
  first_seen_at DESC,
  id DESC
LIMIT 20;
```

Look for use of `idx_job_postings_search_vector`, actual execution time, sort
cost, rows scanned, shared-buffer hits, and disk reads.

## RabbitMQ ingestion throughput

The local server emulates both Greenhouse endpoints used by the real adapter:
board metadata and board jobs. Each board returns 1,000 postings.

```bash
python3 benchmarks/mock_greenhouse.py
```

In another terminal, verify it:

```bash
curl 'http://localhost:8090/v1/boards/benchmark-0/jobs?content=true'
```

Restart the application against the benchmark database and mock provider:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5433/jobpulse_benchmark \
DATABASE_USER=jobpulse \
DATABASE_PASSWORD=jobpulse \
GREENHOUSE_BASE_URL=http://localhost:8090 \
./mvnw spring-boot:run
```

Create or reuse 50 targets through the target API, submit all of them through
the normal admin run-now endpoint, and wait for terminal request states:

```bash
mkdir -p benchmarks/results
python3 benchmarks/ingestion.py --boards 50 \
  > benchmarks/results/ingestion-50-boards.json
```

The harness reports end-to-end wall-clock time. Use database timestamps for the
authoritative result. Immediately before launching the harness, capture:

```sql
SELECT clock_timestamp();
```

Substitute that value below:

```sql
SELECT status, count(*)
FROM ingestion_requests
WHERE created_at >= TIMESTAMPTZ 'YOUR_START_TIME'
GROUP BY status;

SELECT
  count(*) AS completed_requests,
  EXTRACT(EPOCH FROM max(finished_at) - min(created_at)) AS elapsed_seconds
FROM ingestion_requests
WHERE created_at >= TIMESTAMPTZ 'YOUR_START_TIME'
  AND status = 'SUCCEEDED';

SELECT
  sum(discovered) AS postings_processed,
  sum(created) AS postings_created,
  sum(updated) AS postings_updated,
  sum(unchanged) AS postings_unchanged,
  sum(closed) AS postings_closed
FROM ingestion_runs
WHERE started_at >= TIMESTAMPTZ 'YOUR_START_TIME'
  AND status = 'SUCCEEDED';
```

Calculate `postings_processed / elapsed_seconds`. Report only the measured rate
and duration; do not extrapolate it to an hour.

### Duplicate safety

Run `benchmarks/ingestion.py` a second time, then verify that the posting count
remains 50,000 and the duplicate query returns no rows:

```sql
SELECT source, source_account, source_job_id, count(*)
FROM job_postings
WHERE source_account LIKE 'benchmark-%'
GROUP BY source, source_account, source_job_id
HAVING count(*) > 1;

SELECT count(*)
FROM job_postings
WHERE source_account LIKE 'benchmark-%';
```

## Result checklist

Record: posting count, board count, elapsed ingestion time, successful and
failed requests, duplicate rows, search p50/p95/p99, requested rate, achieved
request rate, error rate, and dropped iterations.

## Measured local result (2026-09-19)

Environment: macOS, PostgreSQL 17.10 in Docker, one local Spring Boot process,
one RabbitMQ consumer, and a 50,000-row dataset. Search values are milliseconds
from five-minute constant-arrival-rate runs after a 30-second warm-up.

| Requested rate | Achieved rate | p50 | p95 | p99 | HTTP failures | Dropped |
|---:|---:|---:|---:|---:|---:|---:|
| 25 RPS | 25.002 RPS | 17.12 | 31.68 | 36.37 | 0 / 7,501 | 0 |
| 50 RPS | 48.364 RPS | 12.90 | 24.36 | 27.07 | 3 / 15,001 | 0 |
| 100 RPS | 99.997 RPS | 12.71 | 24.39 | 27.22 | 0 / 30,001 | 0 |

The 50 RPS run completed every scheduled iteration but three requests hit the
30-second client timeout, extending its wall-clock summary and lowering the
reported achieved rate. The 100 RPS run is the strongest clean result: no
errors, no dropped iterations, and 24.39 ms p95 across 50,000 postings.

For `backend engineer`, PostgreSQL returned 20 rows in 15.813 ms with all 4,555
buffers served from cache. It chose a sequential scan over the GIN index because
the fixture intentionally makes 10,000 of 50,000 rows match; the low selectivity
makes a sequential scan cheaper. The sort was an in-memory top-N heapsort using
29 kB.

The clean ingestion pass completed all 50 RabbitMQ requests successfully and
processed/created 50,000 postings in 77.827 seconds: **642.45 postings/second**.
There were zero failed requests and zero duplicate groups. An identical second
pass processed 50,000 postings as unchanged, created zero rows, left the table
at exactly 50,000 rows, and again found zero duplicate groups.
