#!/usr/bin/env bash
set -euo pipefail

: "${GCP_PROJECT_ID:?Set GCP_PROJECT_ID}"
: "${GITHUB_REPOSITORY:?Set GITHUB_REPOSITORY, for example owner/repo}"
GCP_REGION=${GCP_REGION:-us-central1}
GCP_ZONE=${GCP_ZONE:-us-central1-a}
VM_NAME=${VM_NAME:-jobpulse-prod}

[[ "$GCP_PROJECT_ID" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]] || exit 2
[[ "$GITHUB_REPOSITORY" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || exit 2
[[ "$GCP_ZONE" == "$GCP_REGION"-* ]] || { echo 'Zone is outside region' >&2; exit 2; }

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project=$GCP_PROJECT_ID
repo=$GITHUB_REPOSITORY
bucket="${project}-jobpulse-backups"
vm_sa="jobpulse-vm@${project}.iam.gserviceaccount.com"
deploy_sa="jobpulse-deploy@${project}.iam.gserviceaccount.com"

gcloud services enable compute.googleapis.com artifactregistry.googleapis.com \
  secretmanager.googleapis.com iam.googleapis.com iamcredentials.googleapis.com \
  iap.googleapis.com oslogin.googleapis.com storage.googleapis.com \
  --project "$project"

if ! gcloud artifacts repositories describe jobpulse --location "$GCP_REGION" \
    --project "$project" >/dev/null 2>&1; then
  gcloud artifacts repositories create jobpulse --repository-format=docker \
    --location "$GCP_REGION" --project "$project"
fi

if ! gcloud storage buckets describe "gs://$bucket" --project "$project" >/dev/null 2>&1; then
  gcloud storage buckets create "gs://$bucket" --location "$GCP_REGION" \
    --uniform-bucket-level-access --public-access-prevention \
    --project "$project"
fi
gcloud storage buckets update "gs://$bucket" \
  --lifecycle-file "$here/bucket-lifecycle.json" --public-access-prevention \
  --project "$project"

for account in jobpulse-vm jobpulse-deploy; do
  if ! gcloud iam service-accounts describe \
      "$account@${project}.iam.gserviceaccount.com" --project "$project" \
      >/dev/null 2>&1; then
    gcloud iam service-accounts create "$account" --project "$project"
  fi
done

gcloud artifacts repositories add-iam-policy-binding jobpulse \
  --location "$GCP_REGION" --project "$project" \
  --member "serviceAccount:$vm_sa" --role roles/artifactregistry.reader
gcloud artifacts repositories add-iam-policy-binding jobpulse \
  --location "$GCP_REGION" --project "$project" \
  --member "serviceAccount:$deploy_sa" --role roles/artifactregistry.writer
gcloud storage buckets add-iam-policy-binding "gs://$bucket" \
  --member "serviceAccount:$vm_sa" --role roles/storage.objectAdmin \
  --project "$project"

for secret in postgres-password rabbitmq-password google-client-id \
  google-client-secret monitor-password grafana-password admin-emails resend-api-key resend-webhook-secret; do
  name="jobpulse-$secret"
  if ! gcloud secrets describe "$name" --project "$project" >/dev/null 2>&1; then
    gcloud secrets create "$name" --replication-policy=automatic \
      --project "$project"
  fi
  gcloud secrets add-iam-policy-binding "$name" --project "$project" \
    --member "serviceAccount:$vm_sa" --role roles/secretmanager.secretAccessor
done

if ! gcloud compute networks describe jobpulse-vpc --project "$project" \
    >/dev/null 2>&1; then
  gcloud compute networks create jobpulse-vpc --subnet-mode=custom \
    --project "$project"
fi
if ! gcloud compute networks subnets describe jobpulse-subnet \
    --region "$GCP_REGION" --project "$project" >/dev/null 2>&1; then
  gcloud compute networks subnets create jobpulse-subnet \
    --network=jobpulse-vpc --region "$GCP_REGION" --range=10.42.0.0/24 \
    --project "$project"
fi
if ! gcloud compute firewall-rules describe jobpulse-web --project "$project" \
    >/dev/null 2>&1; then
  gcloud compute firewall-rules create jobpulse-web --network=jobpulse-vpc \
    --allow=tcp:80,tcp:443 --source-ranges=0.0.0.0/0 \
    --target-tags=jobpulse-web --project "$project"
fi
if ! gcloud compute firewall-rules describe jobpulse-iap-ssh \
    --project "$project" >/dev/null 2>&1; then
  gcloud compute firewall-rules create jobpulse-iap-ssh \
    --network=jobpulse-vpc --allow=tcp:22 \
    --source-ranges=35.235.240.0/20 --target-tags=jobpulse-iap \
    --project "$project"
fi

if ! gcloud compute addresses describe jobpulse-ip --region "$GCP_REGION" \
    --project "$project" >/dev/null 2>&1; then
  gcloud compute addresses create jobpulse-ip --region "$GCP_REGION" \
    --project "$project"
fi
ip=$(gcloud compute addresses describe jobpulse-ip --region "$GCP_REGION" \
  --project "$project" --format='value(address)')

if ! gcloud compute instances describe "$VM_NAME" --zone "$GCP_ZONE" \
    --project "$project" >/dev/null 2>&1; then
  gcloud compute instances create "$VM_NAME" --zone "$GCP_ZONE" \
    --project "$project" --machine-type=e2-standard-4 \
    --network=jobpulse-vpc --subnet=jobpulse-subnet --address="$ip" \
    --image-family=ubuntu-2404-lts-amd64 --image-project=ubuntu-os-cloud \
    --boot-disk-size=80GB --boot-disk-type=pd-balanced \
    --service-account="$vm_sa" \
    --scopes=https://www.googleapis.com/auth/cloud-platform \
    --metadata=enable-oslogin=TRUE \
    --metadata-from-file="startup-script=$here/vm-startup.sh" \
    --tags=jobpulse-web,jobpulse-iap
fi

if ! gcloud compute resource-policies describe jobpulse-daily \
    --region "$GCP_REGION" --project "$project" >/dev/null 2>&1; then
  gcloud compute resource-policies create snapshot-schedule jobpulse-daily \
    --region "$GCP_REGION" --project "$project" --daily-schedule \
    --start-time=04:00 --max-retention-days=14 \
    --on-source-disk-delete=apply-retention-policy
fi
if ! gcloud compute disks describe "$VM_NAME" --zone "$GCP_ZONE" \
    --project "$project" --format='value(resourcePolicies)' | \
    grep -q jobpulse-daily; then
  gcloud compute disks add-resource-policies "$VM_NAME" \
    --resource-policies=jobpulse-daily --zone "$GCP_ZONE" \
    --project "$project"
fi

if ! gcloud iam workload-identity-pools describe jobpulse-github \
    --location=global --project "$project" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools create jobpulse-github \
    --location=global --project "$project" \
    --display-name='JobPulse GitHub Actions'
fi
if ! gcloud iam workload-identity-pools providers describe jobpulse-repo \
    --workload-identity-pool=jobpulse-github --location=global \
    --project "$project" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers create-oidc jobpulse-repo \
    --workload-identity-pool=jobpulse-github --location=global \
    --project "$project" --issuer-uri=https://token.actions.githubusercontent.com \
    --attribute-mapping='google.subject=assertion.sub,attribute.repository=assertion.repository' \
    --attribute-condition="assertion.repository=='$repo' && assertion.ref=='refs/heads/main'"
fi
number=$(gcloud projects describe "$project" --format='value(projectNumber)')
principal="principalSet://iam.googleapis.com/projects/$number/locations/global/workloadIdentityPools/jobpulse-github/attribute.repository/$repo"
gcloud iam service-accounts add-iam-policy-binding "$deploy_sa" \
  --project "$project" --role roles/iam.workloadIdentityUser \
  --member "$principal"
gcloud iam service-accounts add-iam-policy-binding "$vm_sa" \
  --project "$project" --role roles/iam.serviceAccountUser \
  --member "serviceAccount:$deploy_sa"
for role in roles/compute.instanceAdmin.v1 roles/compute.osAdminLogin \
  roles/iap.tunnelResourceAccessor; do
  gcloud projects add-iam-policy-binding "$project" \
    --member "serviceAccount:$deploy_sa" --role "$role" --quiet
done

echo "VM public IP: $ip"
echo "GitHub GCP_WORKLOAD_IDENTITY_PROVIDER: projects/$number/locations/global/workloadIdentityPools/jobpulse-github/providers/jobpulse-repo"
echo "GitHub GCP_DEPLOY_SERVICE_ACCOUNT: $deploy_sa"
echo "GitHub GCP_PROJECT_ID: $project"
echo "GitHub GCP_REGION: $GCP_REGION"
echo "GitHub GCP_ZONE: $GCP_ZONE"
echo "GitHub GCP_VM_NAME: $VM_NAME"
echo "Create DNS A record for your domain pointing to $ip, then add all seven secret versions."
