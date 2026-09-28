"""Dependency-free, isolated integration benchmarks; not a production load tool.

Seeds durable commands and saved searches with SQL to exercise real workers,
outbox and Kafka consumers without weakening OAuth/CSRF. API/auth costs are
therefore excluded. Raw rows and configuration accompany every summary.
"""
import argparse
import json
import math
import os
import platform
import subprocess
import threading
import time
import uuid
import urllib.request
from datetime import datetime, timezone
from http.server import ThreadingHTTPServer
from pathlib import Path

from mock_greenhouse import Handler

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ['docker', 'compose', '-p', 'jobpulse-metrics', '-f',
           str(ROOT / 'benchmarks/compose.metrics.yaml')]


class MetricsHandler(Handler):
    def _write_json(self, payload):
        if 'name' in payload:
            # All boards in a run share the company filter used by its searches.
            payload = {'name': payload['name'].rsplit('-', 1)[0]}
        super()._write_json(payload)


def command(args, *, input=None, env=None):
    return subprocess.run(args, input=input, text=True, capture_output=True,
                          check=True, env=env, timeout=300).stdout.strip()


def compose(*args, workers=1):
    return command(COMPOSE + list(args),
                   env={**os.environ, 'METRICS_WORKERS': str(workers)})


def sql(statement):
    # Hard-coded isolated DB/project: no user-supplied target or credentials.
    return command(COMPOSE + ['exec', '-T', 'postgres', 'psql', '-X', '-qAt',
                              '-v', 'ON_ERROR_STOP=1', '-U', 'jobpulse',
                              '-d', 'jobpulse_metrics'], input=statement)


def query(statement):
    return json.loads(sql(f'SELECT coalesce(json_agg(t), \'[]\'::json) FROM ({statement}) t;'))


def wait_until(predicate, timeout=240):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(0.5)
    raise TimeoutError('Benchmark did not reach expected state before timeout')


def ready():
    try:
        with urllib.request.urlopen('http://127.0.0.1:18082/actuator/health/readiness', timeout=2) as response:
            return response.status == 200
    except Exception:
        return False


def quantiles(values):
    """PostgreSQL-compatible continuous percentiles, not sampled Prometheus bins."""
    if not values:
        raise ValueError('No latency samples')
    ordered = sorted(values)
    def percentile(p):
        at = (len(ordered) - 1) * p
        lo, hi = math.floor(at), math.ceil(at)
        return round(ordered[lo] + (ordered[hi] - ordered[lo]) * (at - lo), 3)
    return {'count': len(values), 'p50': percentile(.50), 'p95': percentile(.95),
            'p99': percentile(.99), 'max': round(max(values), 3)}


def seed(label, boards, searches):
    prefix = f'metrics-{label}-{uuid.uuid4().hex[:8]}'
    sql('UPDATE saved_searches SET enabled=false;')
    # Search ownership is explicit. Each search matches all postings in its run.
    sql(f"""INSERT INTO saved_searches
        (id,name,query,company,enabled,created_at,owner_subject)
        SELECT gen_random_uuid(), '{prefix}-' || n, 'backend engineer',
               'Benchmark {prefix}', true, clock_timestamp(), 'metrics-user-' || n
        FROM generate_series(1,{searches}) n;
        INSERT INTO ingestion_targets
        (id,source,source_account,company,enabled,interval_minutes,next_run_at)
        SELECT gen_random_uuid(),'GREENHOUSE','{prefix}-' || n,
               'Benchmark {prefix}',false,1440,clock_timestamp() + interval '1 day'
        FROM generate_series(1,{boards}) n;""")
    return prefix


def submit(prefix):
    sql(f"""INSERT INTO ingestion_requests
        (id,ingestion_target_id,status,created_at)
        SELECT gen_random_uuid(),id,'PENDING',clock_timestamp()
        FROM ingestion_targets WHERE source_account LIKE '{prefix}-%';""")


def ingestion_rows(prefix):
    return query(f"""SELECT r.id,r.status,r.attempt_count,
        extract(epoch FROM (r.started_at-r.created_at))*1000 AS queue_ms,
        extract(epoch FROM (r.finished_at-r.started_at))*1000 AS service_ms,
        extract(epoch FROM r.created_at) AS created_epoch,
        extract(epoch FROM r.finished_at) AS finished_epoch
        FROM ingestion_requests r JOIN ingestion_targets t ON t.id=r.ingestion_target_id
        WHERE t.source_account LIKE '{prefix}-%'""")


def wait_ingestion(prefix, boards):
    def complete():
        rows = ingestion_rows(prefix)
        return rows if len(rows) == boards and all(r['status'] in ('SUCCEEDED', 'FAILED') for r in rows) else None
    rows = wait_until(complete)
    if any(r['status'] != 'SUCCEEDED' for r in rows):
        raise AssertionError(f'Failed ingestion: {rows}')
    return rows


def counts(prefix):
    return query(f"""SELECT
        (SELECT count(*) FROM job_postings WHERE source_account LIKE '{prefix}-%') AS postings,
        (SELECT count(*) FROM job_events WHERE source_account LIKE '{prefix}-%') AS events,
        (SELECT count(*) FROM job_events WHERE source_account LIKE '{prefix}-%'
             AND publish_status='PUBLISHED') AS published,
        (SELECT count(*) FROM job_event_consumptions c JOIN job_events e ON e.id=c.event_id
             WHERE e.source_account LIKE '{prefix}-%' AND c.consumer_name='jobpulse-alerts') AS alert_consumptions,
        (SELECT count(*) FROM job_event_consumptions c JOIN job_events e ON e.id=c.event_id
             WHERE e.source_account LIKE '{prefix}-%' AND c.consumer_name='jobpulse-analytics') AS analytics_consumptions,
        (SELECT count(*) FROM job_alerts a JOIN job_postings p ON p.id=a.job_posting_id
             WHERE p.source_account LIKE '{prefix}-%') AS alerts,
        (SELECT count(*) FROM (SELECT source,source_account,source_job_id FROM job_postings
             WHERE source_account LIKE '{prefix}-%' GROUP BY 1,2,3 HAVING count(*)>1) d) AS duplicate_posting_groups""")[0]


def drain(prefix, postings, searches):
    expected = {'postings': postings, 'events': postings, 'published': postings,
                'alert_consumptions': postings, 'analytics_consumptions': postings,
                'alerts': postings * searches, 'duplicate_posting_groups': 0}
    return wait_until(lambda: (c if (c := counts(prefix)) == expected else None))


def event_rows(prefix):
    # Alert timestamp is application time before commit. Consumer processed_at
    # is also before commit. The harness separately waits for DB-visible totals.
    return query(f"""SELECT e.id,
        extract(epoch FROM e.created_at) AS event_epoch,
        extract(epoch FROM e.published_at) AS published_epoch,
        extract(epoch FROM (e.published_at-e.created_at))*1000 AS publication_ms,
        extract(epoch FROM (c.processed_at-e.created_at))*1000 AS consumer_start_ms,
        (SELECT extract(epoch FROM max(a.created_at)) FROM job_alerts a
          WHERE a.job_posting_id=e.job_posting_id) AS last_alert_epoch
        FROM job_events e JOIN job_event_consumptions c ON c.event_id=e.id
        AND c.consumer_name='jobpulse-alerts' WHERE e.source_account LIKE '{prefix}-%'""")


def measure(label, boards, jobs, searches, workers):
    prefix = seed(label, boards, searches)
    started = time.monotonic()
    submit(prefix)
    requests = wait_ingestion(prefix, boards)
    totals = drain(prefix, boards * jobs, searches)
    visible_elapsed = time.monotonic() - started
    events = event_rows(prefix)
    elapsed = max(r['finished_epoch'] for r in requests) - min(r['created_epoch'] for r in requests)
    result = {'label': label, 'prefix': prefix, 'workers': workers,
              'boards': boards, 'jobs_per_board': jobs, 'saved_searches': searches,
              'counts': totals, 'ingestion_elapsed_seconds': elapsed,
              'request_submission_to_database_visible_seconds': visible_elapsed,
              'postings_per_second': totals['postings'] / elapsed,
              'queue_ms': quantiles([r['queue_ms'] for r in requests]),
              'service_ms': quantiles([r['service_ms'] for r in requests]),
              'event_publication_ms': quantiles([e['publication_ms'] for e in events]),
              'event_to_consumer_start_ms': quantiles([e['consumer_start_ms'] for e in events]),
              'raw_requests': requests, 'raw_events': events}
    if searches:
        result['event_to_last_alert_timestamp_ms'] = quantiles([
            (e['last_alert_epoch'] - e['event_epoch']) * 1000 for e in events])
    return result


def recovery(jobs):
    prefix = seed('kafka-outage', 1, 10)
    compose('stop', 'kafka')
    try:
        submit(prefix)
        wait_ingestion(prefix, 1)
        # Allow a real failed send (10s timeout), rather than merely a pause
        # shorter than the publisher schedule.
        wait_until(lambda: int(sql(f"SELECT coalesce(max(publish_attempts),0) FROM job_events WHERE source_account LIKE '{prefix}-%';")) > 0)
        during = counts(prefix)
        if during['postings'] != jobs or during['published'] != 0:
            raise AssertionError(f'Unexpected outage counts: {during}')
    finally:
        restarted = time.monotonic()
        compose('start', 'kafka')
    recovered = drain(prefix, jobs, 10)
    recovery_seconds = time.monotonic() - restarted
    analytics_before = sql('SELECT coalesce(sum(count),0) FROM daily_job_stats;')
    before = counts(prefix)
    # Controlled duplicate delivery via the actual outbox, not direct consumer calls.
    # Keep event IDs stable so deduplication is exercised for both consumer groups.
    sql(f"""UPDATE job_events SET published_at=NULL,publish_status='PENDING',
        next_attempt_at=clock_timestamp(),lease_owner=NULL,lease_expires_at=NULL
        WHERE source_account LIKE '{prefix}-%';""")
    replayed = drain(prefix, jobs, 10)
    # Published does not imply consumers have replayed: verify offsets per group.
    wait_consumer_lag_zero()
    analytics_after = sql('SELECT coalesce(sum(count),0) FROM daily_job_stats;')
    if replayed != before or analytics_after != analytics_before:
        raise AssertionError('Replay changed alerts, postings, consumer records or analytics')
    return {'jobs': jobs, 'saved_searches': 10, 'during_outage': during,
            'after_recovery': recovered, 'restart_to_database_visible_completion_seconds': recovery_seconds,
            'poll_interval_seconds': .5, 'replayed_events': jobs,
            'after_replay': counts(prefix), 'analytics_before': int(analytics_before),
            'analytics_after': int(analytics_after)}


def wait_consumer_lag_zero():
    def consumed():
        for group in ('jobpulse-alerts', 'jobpulse-analytics'):
            output = compose('exec', '-T', 'kafka', '/opt/kafka/bin/kafka-consumer-groups.sh',
                             '--bootstrap-server', 'localhost:29092', '--describe', '--group', group)
            rows = [line.split() for line in output.splitlines()
                    if line.startswith(group + ' ')]
            if len(rows) != 3 or any(row[5] != '0' for row in rows):
                return False
        return True
    wait_until(consumed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repeats', type=int, default=3)
    parser.add_argument('--scenario', choices=['all', 'fanout', 'workers', 'recovery'], default='all')
    parser.add_argument('--output', type=Path, default=ROOT / 'benchmarks/results/metrics-local.json')
    args = parser.parse_args()
    if args.repeats < 1:
        parser.error('--repeats must be positive')
    import mock_greenhouse
    mock_greenhouse.JOBS_PER_BOARD = 100
    server = ThreadingHTTPServer(('0.0.0.0', 18090), MetricsHandler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    result = {'started_at': datetime.now(timezone.utc).isoformat(),
              'commit': command(['git', 'rev-parse', 'HEAD']),
              'working_tree': command(['git', 'status', '--short']),
              'host': platform.platform(),
              'docker': command(['docker', 'info', '--format',
                  '{"cpus":{{.NCPU}},"memory_bytes":{{.MemTotal}},"server_version":"{{.ServerVersion}}","architecture":"{{.Architecture}}"}']),
              'configuration': compose('config', '--format', 'json'),
              'scenario': args.scenario, 'runs': [], 'status': 'running'}
    def save():
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    try:
        compose('up', '-d', '--wait', 'postgres', 'rabbitmq', 'kafka')
        compose('up', '-d', 'app')
        wait_until(ready)
        if sql('SELECT current_database();') != 'jobpulse_metrics':
            raise RuntimeError('Wrong database')
        result['postgres_version'] = sql('SHOW server_version;')
        result['app_image'] = compose('images', '--format', 'json')
        # Warm JVM/provider/DB paths; retain but exclude from reported medians.
        result['warmup'] = measure('warmup', 1, 100, 1, 1)
        save()
        for searches in ((1, 10, 100) if args.scenario in ('all', 'fanout') else ()):
            for repeat in range(args.repeats):
                print(f'Alert fan-out: {searches} searches, run {repeat + 1}', flush=True)
                run = measure(f'fanout-{searches}-{repeat}', 1, 100, searches, 1)
                run['scenario'] = 'alert_fanout'
                result['runs'].append(run)
                save()
        for workers in ((1, 4) if args.scenario in ('all', 'workers') else ()):
            if workers != 1:
                compose('up', '-d', '--force-recreate', 'app', workers=workers)
                wait_until(ready)
                measure('worker-warmup', 1, 100, 0, workers)
            for repeat in range(args.repeats):
                print(f'Ingestion: {workers} workers, run {repeat + 1}', flush=True)
                run = measure(f'workers-{workers}-{repeat}', 8, 100, 0, workers)
                run['scenario'] = 'worker_concurrency'
                result['runs'].append(run)
                save()
        if args.scenario in ('all', 'recovery'):
            print('Kafka outage and duplicate replay', flush=True)
            result['recovery'] = recovery(100)
        result['status'] = 'passed'
    except Exception as error:
        result['status'] = 'failed'
        result['error'] = repr(error)
        raise
    finally:
        result['finished_at'] = datetime.now(timezone.utc).isoformat()
        save()
        server.shutdown()
        print(f'Results: {args.output}', flush=True)


if __name__ == '__main__':
    main()
