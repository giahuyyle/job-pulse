# Board discovery

Board discovery administration is available only from localhost.

Prepare a CSV with this header:

```csv
company_name,careers_url
Example Company,https://example.com/careers
```

Load the seeds:

```bash
curl --request POST http://localhost:8080/api/v1/admin/discovery/seeds \
  --header 'Content-Type: text/csv' \
  --data-binary @companies.csv
```

Run discovery immediately:

```bash
curl --request POST http://localhost:8080/api/v1/admin/discovery/runs
```

Inspect the persisted seed outcomes:

```bash
curl http://localhost:8080/api/v1/admin/discovery/seeds
```

The application also rediscovers all seeds weekly. Configure
`jobpulse.discovery.delay-ms` and `jobpulse.discovery.initial-delay-ms` to
override the default seven-day delay.
