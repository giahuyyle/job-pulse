# Google Cloud deployment

This is the first production deployment path for JobPulse. It runs one `e2-standard-4` Compute Engine VM with Docker Compose. Caddy serves the React build over HTTPS and proxies API and Google sign-in requests to Spring. PostgreSQL, RabbitMQ, Kafka, Prometheus, Tempo, and Grafana have no public listener. SSH uses Identity-Aware Proxy (IAP) and OS Login. GitHub Actions tests both apps on every pull request and `main` push. A manual release run builds images, pushes them to Artifact Registry with Workload Identity Federation, and deploys image digests tied to a commit.

This is a single-VM deployment. It tolerates container restarts and has backups, but it is not highly available: a VM or zone failure requires recovery. Keep the production environment approval gate and do not point DNS at the VM until the secrets and Google OAuth client are ready.

## 1. Create the account resources

Create a Google Cloud project, attach a billing account, and own a domain or subdomain for JobPulse. [Install the Google Cloud CLI](https://docs.cloud.google.com/sdk/docs/install-sdk), run `gcloud auth login`, and verify that the intended project is selected. Set a Cloud Billing budget before provisioning; for your US$400 credit balance, alerts around US$100, US$200, US$300, and US$380 leave time to react. The default region below is `us-central1` to conserve credits; change both region and zone together if your users need a closer location. Check the [current Compute Engine prices](https://cloud.google.com/products/compute/pricing/general-purpose) and [pricing calculator](https://cloud.google.com/products/calculator) before choosing a VM size. The VM, disk, static IP, storage, network egress, and Artifact Registry all consume credits. Google Cloud trial credit can also expire by date, independently of spending.

Run from the repository root:

```bash
export GCP_PROJECT_ID='YOUR_PROJECT_ID'
export GITHUB_REPOSITORY='YOUR_GITHUB_OWNER/YOUR_REPO'
export GCP_REGION='us-central1'
export GCP_ZONE='us-central1-a'
bash deploy/gcp/bootstrap.sh
```

The script creates a dedicated VPC, a VM with an 80 GB disk and static IP, Artifact Registry, a private backup bucket, Secret Manager secret containers, a daily disk snapshot policy, and IAM for GitHub deployment. The custom VPC permits public traffic only on 80/443; SSH is limited to [IAP's TCP forwarding range](https://docs.cloud.google.com/iap/docs/using-tcp-forwarding). It prints the IP and GitHub variable values. Provisioning starts billable resources. The script can be rerun after partial setup, but review any existing resource settings before changing them.

Add a DNS A record for the chosen domain pointing to the printed static IP. Wait for DNS resolution. Create a Google OAuth **Web application** client with exactly `https://YOUR_DOMAIN/login/oauth2/code/google` as an authorized redirect URI. Record the client ID and client secret. The [Google redirect URI rules](https://developers.google.com/identity/protocols/oauth2/web-server) require an exact match.

## 2. Add runtime secrets

Bootstrap creates empty Secret Manager secrets. Add one version to each before the first release. The four generated passwords below are random and do not enter shell history:

```bash
for name in postgres-password rabbitmq-password monitor-password grafana-password; do
  openssl rand -hex 32 | tr -d '\n' | gcloud secrets versions add "jobpulse-$name" \
    --project "$GCP_PROJECT_ID" --data-file=-
done
```

Enter the Google client ID, Google client secret, and comma-separated verified admin email addresses without echoing them in the terminal:

```bash
for name in google-client-id google-client-secret admin-emails; do
  read -rsp "jobpulse-$name: " secret_value
  printf '\n'
  printf '%s' "$secret_value" | gcloud secrets versions add "jobpulse-$name" \
    --project "$GCP_PROJECT_ID" --data-file=-
  unset secret_value
done
```

The VM service account can read only these seven secrets. The deploy script refuses empty values and reads new versions during each deployment. PostgreSQL, RabbitMQ, and Grafana also store credentials in their persistent data; changing those Secret Manager versions alone does not rotate the stored credentials. Coordinate an in-service password change before updating their secrets. The admin allowlist takes effect for new sign-ins, so revoke existing sessions if an admin is removed.

## 3. Configure GitHub and release

In the repository's **Settings → Secrets and variables → Actions → Variables**, set the values printed by bootstrap: `GCP_PROJECT_ID`, `GCP_REGION`, `GCP_ZONE`, `GCP_VM_NAME`, `GCP_WORKLOAD_IDENTITY_PROVIDER`, and `GCP_DEPLOY_SERVICE_ACCOUNT`. Also set `GCP_DOMAIN` to your domain, without `https://`. No Google service account key is needed. The Workload Identity provider accepts only this repository on `refs/heads/main`.

Protect `main` with the backend and frontend CI checks. Create the GitHub Actions environment **production** and add a deployment branch rule for `main`. Configure required reviewers if available for your repository plan. A push to `main` runs CI only; to release, run **CI and deploy** manually from `main`. The workflow tests Java and frontend, builds two images tagged with the commit SHA, pins their immutable digests in the release manifest, uploads the runtime files through IAP, takes a pre-release database backup when upgrading an existing stack, starts the stack, checks Spring readiness, takes a post-release PostgreSQL backup, and verifies `https://YOUR_DOMAIN/api/v1/auth/session`. If any check fails, the workflow fails; inspect the deployment job and VM logs before retrying. A release with a database migration may not safely roll back to an older application image.

The first VM startup installs Docker, the Compose plugin, `jq`, and the Google Cloud CLI. Wait for its startup script to finish before the first deployment; new Workload Identity bindings can also take a few minutes to propagate. You can inspect its log through IAP:

```bash
gcloud compute ssh jobpulse-prod --project "$GCP_PROJECT_ID" \
  --zone "$GCP_ZONE" --tunnel-through-iap \
  --command='sudo journalctl -u google-startup-scripts.service -n 100 --no-pager'
```

## 4. Verify and operate

After the workflow succeeds:

```bash
curl -fsS "https://YOUR_DOMAIN/api/v1/auth/session"
curl -fsS "https://YOUR_DOMAIN/api/v1/jobs"
```

The first response should report `authenticated:false`. Open `/jobs`, sign in with Google, save a search, and verify `/alerts`. An admin email should see `/admin`; a regular user must not. Check that `/actuator/prometheus`, port 5432, port 5672, and port 9092 are not publicly reachable. Spring's health endpoint is available only within the Docker network.

For Grafana, use an IAP SSH tunnel rather than opening a public firewall rule:

```bash
gcloud compute ssh jobpulse-prod --project "$GCP_PROJECT_ID" \
  --zone "$GCP_ZONE" --tunnel-through-iap \
  -- -L 3000:localhost:3000
```

Then open `http://localhost:3000` and use the Secret Manager `jobpulse-grafana-password` value. Prometheus can be tunneled similarly on port 9090. On the VM, `sudo docker compose -f /opt/jobpulse/compose.prod.yaml ps` shows service status; `sudo journalctl -u jobpulse-backup.service` shows backup runs. Docker containers restart automatically after a VM reboot.

The daily PostgreSQL backup uploads a custom-format dump to `gs://PROJECT_ID-jobpulse-backups/postgres/`; the bucket deletes those objects after 30 days. The VM's boot disk also has daily snapshots retained for 14 days. Snapshots are crash-consistent, while `pg_dump` is the logical database recovery source. Test restoration in an isolated database before relying on these backups. Never restore over live production without first stopping writes and taking a fresh backup.

For an isolated restore drill, copy a dump to the VM through IAP, then create a separate database in the existing PostgreSQL container. Do not target the live `jobpulse` database:

```bash
gcloud storage cp 'gs://PROJECT_ID-jobpulse-backups/postgres/TIMESTAMP.dump' ./jobpulse-restore.dump
gcloud compute scp ./jobpulse-restore.dump jobpulse-prod:~/ \
  --project "$GCP_PROJECT_ID" --zone "$GCP_ZONE" --tunnel-through-iap
gcloud compute ssh jobpulse-prod --project "$GCP_PROJECT_ID" \
  --zone "$GCP_ZONE" --tunnel-through-iap
```

On the VM, restore into a separate database and check that Flyway and jobs are present:

```bash
container=$(sudo docker ps --filter label=com.docker.compose.project=jobpulse \
  --filter label=com.docker.compose.service=postgres --format '{{.ID}}' | head -1)
test -n "$container"
sudo docker cp ~/jobpulse-restore.dump "$container:/tmp/jobpulse-restore.dump"
sudo docker exec "$container" sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" createdb -h localhost -U jobpulse jobpulse_restore_check'
sudo docker exec "$container" sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore -h localhost -U jobpulse --no-owner --no-privileges -d jobpulse_restore_check /tmp/jobpulse-restore.dump'
sudo docker exec "$container" sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h localhost -U jobpulse -d jobpulse_restore_check -Atc "SELECT count(*) FROM flyway_schema_history"'
sudo docker exec "$container" sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h localhost -U jobpulse -d jobpulse_restore_check -Atc "SELECT count(*) FROM job_postings"'
sudo docker exec "$container" sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" dropdb -h localhost -U jobpulse jobpulse_restore_check'
sudo docker exec "$container" rm -f /tmp/jobpulse-restore.dump
rm -f ~/jobpulse-restore.dump
```

The row counts should match expectations for the backup timestamp. This drill, plus a full VM recovery from snapshot, is part of live production verification.

Apply VM OS and Docker security updates regularly, reboot when required, and repeat the HTTPS, readiness, and backup checks after each maintenance window.

## Cost and release limits

This design keeps all stateful services on one VM to avoid multiple always-on managed-service charges. As a rough planning figure, an `e2-standard-4` VM alone can be around US$100/month in lower-cost regions before disk, external IP, storage, network egress, and taxes; use the calculator for your region and actual traffic. A US$400 credit balance therefore is not an indefinite hosting plan. Set budget alerts and review Billing after the first 24 hours.

Milestone 14's repository work supplies the release pipeline and infrastructure recipe. The first live deployment still needs your project ID, domain, OAuth client, and credentials. Milestone 15 covers live restore drills, real Google sign-in verification, operational thresholds, and final production documentation.
