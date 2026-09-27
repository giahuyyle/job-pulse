#!/usr/bin/env bash
set -euo pipefail

manifest=/opt/jobpulse/release.json
project=$(jq -er '.project' "$manifest")
bucket="${project}-jobpulse-backups"
container=$(docker ps --filter 'label=com.docker.compose.project=jobpulse' \
  --filter 'label=com.docker.compose.service=postgres' --format '{{.ID}}' | head -1)
[[ -n "$container" ]] || { echo 'PostgreSQL container is not running' >&2; exit 1; }

backup=$(mktemp /tmp/jobpulse-postgres.XXXXXXXX.dump)
trap 'rm -f "$backup"' EXIT
docker exec "$container" sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -h localhost -U jobpulse -d jobpulse -Fc' > "$backup"
[[ -s "$backup" ]] || { echo 'PostgreSQL backup is empty' >&2; exit 1; }

name="postgres/$(date -u +%Y%m%dT%H%M%SZ).dump"
gcloud storage cp "$backup" "gs://${bucket}/${name}" --project "$project"
gcloud storage objects describe "gs://${bucket}/${name}" --project "$project" \
  --format='value(size)' | grep -Eq '^[1-9][0-9]*$'
echo "PostgreSQL backup uploaded: gs://${bucket}/${name}"
