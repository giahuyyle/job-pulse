# Job search and saved alerts

JobPulse uses PostgreSQL full-text search over active job titles and
descriptions. Titles receive a higher search weight than descriptions, and a
GIN index supports nonblank keyword searches.

## Search jobs

```bash
curl 'http://localhost:8080/api/v1/jobs?query=software%20engineer&remotePolicy=REMOTE&sort=relevance&page=0&size=20'
```

Supported filters are `query`, `company`, `source`, `remotePolicy`, and
`location`. `sort` accepts `newest` or `relevance`; relevance requires a
nonblank query. Results include at most 100 compact job summaries and default
to active jobs ordered by when JobPulse first discovered them.

Use `GET /api/v1/jobs/{id}` for the full description and provider publication
time.

## Save a search

```bash
curl -X POST http://localhost:8080/api/v1/saved-searches \
  -H 'Content-Type: application/json' \
  --data '{
    "name":"Remote backend roles",
    "query":"backend engineer",
    "remotePolicy":"REMOTE"
  }'
```

List and edit saved searches with:

```text
GET   /api/v1/saved-searches
PATCH /api/v1/saved-searches/{id}
```

Setting `enabled` to `false` stops future matches. Saving a search does not
create alerts for jobs that already exist.

## Alert inbox

Newly ingested jobs create a durable `CREATED` event in the same transaction
as the posting. The Kafka alerts consumer compares those events with enabled
saved searches and inserts at most one alert per search and posting.

```text
GET   /api/v1/alerts
GET   /api/v1/alerts?unread=true
PATCH /api/v1/alerts/{id}/read
```

Alerts consume `jobpulse.job-events.v1` independently from analytics. See
`docs/kafka-job-events.md` for delivery and local runtime details.
