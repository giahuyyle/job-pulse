#!/usr/bin/env bash
set -euo pipefail

if [[ "$(id -u)" != 0 || $# != 1 ]]; then
  echo 'Usage: sudo deploy-host.sh RELEASE_DIRECTORY' >&2
  exit 2
fi

release_dir=$1
manifest="$release_dir/release.json"
[[ -f "$manifest" ]] || { echo 'Missing release.json' >&2; exit 2; }

project=$(jq -er '.project | select(test("^[a-z][a-z0-9-]{4,28}[a-z0-9]$"))' "$manifest")
domain=$(jq -er '.domain | select(test("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"))' "$manifest")
commit=$(jq -er '.commit | select(test("^[a-f0-9]{40}$"))' "$manifest")
app_image=$(jq -er '.appImage | select(test("^[a-z0-9-]+-docker\\.pkg\\.dev/[a-z0-9-]+/jobpulse/jobpulse-app@sha256:[a-f0-9]{64}$"))' "$manifest")
frontend_image=$(jq -er '.frontendImage | select(test("^[a-z0-9-]+-docker\\.pkg\\.dev/[a-z0-9-]+/jobpulse/jobpulse-frontend@sha256:[a-f0-9]{64}$"))' "$manifest")

[[ "$app_image" == */"$project"/jobpulse/* ]] || { echo 'App image project mismatch' >&2; exit 2; }
[[ "$frontend_image" == */"$project"/jobpulse/* ]] || { echo 'Frontend image project mismatch' >&2; exit 2; }

if [[ -x /opt/jobpulse/backup-postgres.sh && -f /opt/jobpulse/release.json ]]; then
  /opt/jobpulse/backup-postgres.sh
  cp -p /opt/jobpulse/release.json /opt/jobpulse/release.previous.json
fi

install -d -m 0750 /opt/jobpulse /opt/jobpulse/observability \
  /opt/jobpulse/observability/grafana /opt/jobpulse/secrets
install -m 0644 "$release_dir/compose.prod.yaml" /opt/jobpulse/compose.prod.yaml
install -m 0644 "$release_dir/prometheus.prod.yml" /opt/jobpulse/observability/prometheus.prod.yml
install -m 0644 "$release_dir/alerts.yml" /opt/jobpulse/observability/alerts.yml
install -m 0644 "$release_dir/tempo.yml" /opt/jobpulse/observability/tempo.yml
cp -a "$release_dir/grafana/." /opt/jobpulse/observability/grafana/
install -m 0640 "$manifest" /opt/jobpulse/release.json

secret() {
  gcloud secrets versions access latest --project "$project" --secret "$1"
}

export APP_IMAGE="$app_image" FRONTEND_IMAGE="$frontend_image" JOBPULSE_DOMAIN="$domain"
export POSTGRES_PASSWORD="$(secret jobpulse-postgres-password)"
export RABBITMQ_PASSWORD="$(secret jobpulse-rabbitmq-password)"
export GOOGLE_CLIENT_ID="$(secret jobpulse-google-client-id)"
export GOOGLE_CLIENT_SECRET="$(secret jobpulse-google-client-secret)"
export JOBPULSE_MONITOR_PASSWORD="$(secret jobpulse-monitor-password)"
export GRAFANA_ADMIN_PASSWORD="$(secret jobpulse-grafana-password)"
export JOBPULSE_ADMIN_EMAILS="$(secret jobpulse-admin-emails)"
export JOBPULSE_ADMIN_ENABLED=true

for value in POSTGRES_PASSWORD RABBITMQ_PASSWORD GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET \
  JOBPULSE_MONITOR_PASSWORD GRAFANA_ADMIN_PASSWORD JOBPULSE_ADMIN_EMAILS; do
  [[ -n "${!value}" ]] || { echo "Missing secret value: $value" >&2; exit 2; }
done

printf '%s' "$JOBPULSE_MONITOR_PASSWORD" > /opt/jobpulse/secrets/monitor-password
chown 0:65534 /opt/jobpulse/secrets/monitor-password
chmod 0640 /opt/jobpulse/secrets/monitor-password

registry_host=${app_image%%/*}
gcloud auth configure-docker "$registry_host" --quiet >/dev/null
cd /opt/jobpulse
docker compose -f compose.prod.yaml config --quiet
docker compose -f compose.prod.yaml pull app frontend
docker compose -f compose.prod.yaml up -d --remove-orphans

healthy=false
for _ in $(seq 1 36); do
  if docker compose -f compose.prod.yaml exec -T frontend \
      wget -qO- http://app:8080/actuator/health/readiness 2>/dev/null |
      grep -q '"status":"UP"'; then
    healthy=true
    break
  fi
  sleep 5
done

if [[ "$healthy" != true ]]; then
  docker compose -f compose.prod.yaml ps >&2
  echo 'Application readiness check failed' >&2
  exit 1
fi

install -m 0750 "$release_dir/backup-postgres.sh" /opt/jobpulse/backup-postgres.sh
install -m 0644 "$release_dir/jobpulse-backup.service" /etc/systemd/system/jobpulse-backup.service
install -m 0644 "$release_dir/jobpulse-backup.timer" /etc/systemd/system/jobpulse-backup.timer
systemctl daemon-reload
systemctl enable --now jobpulse-backup.timer
/opt/jobpulse/backup-postgres.sh
echo "Deployed commit $commit to $domain"
