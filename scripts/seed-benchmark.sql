\if :{?rows}
\else
\set rows 50000
\endif

SELECT setseed(0.4242);

INSERT INTO job_postings (
    id, source, source_account, source_job_id, company, title, location,
    description, employment_type, remote_policy, apply_url, posted_at,
    first_seen_at, last_seen_at, status, content_hash, version
)
SELECT
    md5('benchmark-' || n)::uuid,
    CASE n % 3
        WHEN 0 THEN 'GREENHOUSE'
        WHEN 1 THEN 'LEVER'
        ELSE 'ASHBY'
    END,
    'benchmark-board-' || (n % 50),
    'benchmark-job-' || n,
    'Benchmark Company ' || (n % 100),
    CASE n % 5
        WHEN 0 THEN 'Backend Software Engineer'
        WHEN 1 THEN 'Frontend Engineer'
        WHEN 2 THEN 'Data Engineer'
        WHEN 3 THEN 'Machine Learning Engineer'
        ELSE 'Site Reliability Engineer'
    END,
    CASE n % 4
        WHEN 0 THEN 'Toronto, Canada'
        WHEN 1 THEN 'Vancouver, Canada'
        WHEN 2 THEN 'Edmonton, Canada'
        ELSE 'Remote, Canada'
    END,
    'Build scalable distributed services using Java, Spring Boot, PostgreSQL, RabbitMQ, Kafka, Docker, and cloud infrastructure.',
    (ARRAY['FullTime','Intern','Contract'])[1 + (n % 3)],
    (ARRAY['REMOTE','HYBRID','ONSITE','UNSPECIFIED'])[1 + (n % 4)],
    'https://example.com/jobs/' || n,
    now() - ((n % 365) || ' days')::interval,
    now() - ((n % 30) || ' minutes')::interval,
    now(), 'ACTIVE', repeat(substr(md5('content-' || n), 1, 32), 2), 0
FROM generate_series(1, :rows) AS n
ON CONFLICT (source, source_account, source_job_id) DO NOTHING;

ANALYZE job_postings;
