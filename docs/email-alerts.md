# Daily job email digests

JobPulse optionally sends one combined digest per subscribed user at **9 AM
Eastern Time** (`America/New_York`, including EDT/EST changes). Each saved
search has an unchecked **Include in my daily email digest** setting. Inbox
alerts continue independently. Read alerts are included, overlapping jobs
appear once, and enabling email never backfills existing alerts.

The Alerts page shows the verified Google recipient, fixed schedule, next
slot, delivery availability, and bounce/complaint suspension. Opt-in starts at
the next future slot. Matches collected after its cutoff enter the next day.
Empty digests are omitted; closed jobs are excluded. Emails show up to 50 jobs,
the full count, and a link to all inbox matches.

## Free Resend setup

Use a dedicated Resend Free account with upgrades/overages disabled. Limits are
100 emails per UTC day and 3,000 per month. JobPulse reserves budget across
workers and honors provider quota errors. Retries crossing UTC midnight reserve the new day conservatively;
monthly counts deduplicate reservations for the same digest. Existing hosting and domain renewal
retain their existing costs.

1. Add `mail.YOUR_DOMAIN` to Resend. Add the supplied SPF/DKIM records at the
   authoritative DNS host (Name.com if it hosts your DNS). Preserve website
   and mailbox records. Follow the provider's DMARC guidance. Renew the domain.
2. Use `JobPulse <alerts@mail.YOUR_DOMAIN>` as the verified sender.
3. Create a sending API key and a webhook at
   `https://YOUR_DOMAIN/api/v1/webhooks/resend`. Subscribe to `email.sent`,
   `email.delivered`, `email.delivery_delayed`, `email.bounced`,
   `email.complained`, `email.failed`, and `email.suppressed`. Record the signing
   secret. Disable open and click tracking in the Resend dashboard.
4. Configure application environment variables:

```dotenv
JOBPULSE_EMAIL_ENABLED=false
RESEND_API_KEY=YOUR_SENDING_KEY
RESEND_WEBHOOK_SECRET=whsec_YOUR_SIGNING_SECRET
JOBPULSE_EMAIL_FROM=JobPulse <alerts@mail.YOUR_DOMAIN>
JOBPULSE_APP_URL=https://YOUR_DOMAIN
JOBPULSE_EMAIL_DAILY_LIMIT=100
JOBPULSE_EMAIL_MONTHLY_LIMIT=3000
JOBPULSE_EMAIL_ALLOWLIST=your-verified-address@example.com
```

Delivery defaults to off. Lower budgets are allowed; limits above the free
allowance are rejected. An empty allowlist permits all subscribed recipients.
Keep an internal allowlist during rollout, then remove it after a real digest
and unsubscribe test. Compose reads `.env`; host Spring does not automatically.

For GCP, rerun bootstrap to create the optional `jobpulse-resend-api-key` and
`jobpulse-resend-webhook-secret` Secret Manager containers and IAM bindings.
Add secret versions privately. Create root-owned `/opt/jobpulse/email.env`
(mode 0600) containing only the feature flag, sender, allowlist, and optional
lower budgets. Deployment reads that file and fetches Resend secrets only when
enabled. Never place API credentials in this settings file or Git.

Production Caddy and development Vite forward `/email/unsubscribe` to the
backend confirmation page. GET does not alter preferences; token-authenticated
POST unsubscribes all searches. Mail clients also use the one-click header.
Token hashes expire after 180 days. Stored exact outgoing payloads contain
recipient addresses and token URLs: restrict database and backup access.

## Delivery and recovery

Kafka processing inserts candidates beside new inbox alerts transactionally;
replay cannot create duplicates. A separate scheduler prepares digests once per
minute and sends leased claims outside database transactions. Catch-up uses the
latest elapsed Eastern cutoff and sends one digest, not one per missed day.

Quota rejection and budget exhaustion carry matches to a later 9 AM Eastern
slot for up to seven days. Inbox records remain. Pausing or disabling a search
cancels waiting candidates. Preference changes cancel unattempted digests;
remaining eligible matches are reconsidered next day. Already attempted
digests stay reserved when acceptance cannot be ruled out.

Exact JSON and `jobpulse-digest/DIGEST_UUID` keys remain fixed on retries.
Delays are 1, 5, 15, and 60 minutes, or longer when requested by the provider.
Five attempts are allowed; retries stop before 23 hours from the first attempt,
inside Resend's 24-hour idempotency window. Network errors and crashed sending
leases are treated as potentially accepted.

`ACCEPTED` means provider acceptance, not inbox delivery. Signed, deduplicated
webhooks record delivery, bounce, complaint, and failure. Digest tags reconcile
early webhooks and lost responses. Late success events cannot overwrite adverse
terminal outcomes. Updated verified addresses clear address-specific bounce
suppression. Explicit opt-in clears local complaint/unsubscribe suppression;
if Resend still suppresses the address, an administrator must review and remove
it there after explicit resubscription. A sending-only key cannot override that.

### Reconciliation

```sql
SELECT id, schedule_date, state, delivery_status, attempts, error_code,
       provider_id, first_attempt_at
FROM email_digests
WHERE state IN ('FAILED', 'UNCERTAIN')
ORDER BY first_attempt_at;
```

For uncertain messages, inspect Resend using the digest tag/provider ID and
allow signed webhooks to reconcile acceptance. **Never reset uncertain rows
or send with a new key without evidence of original non-acceptance.** Expired
leases inside the safety window retry the same payload/key automatically.
Failed/uncertain rows reserve their candidates to prevent duplicate daily mail.

For confirmed non-acceptance, fix the cause and release candidates in an
administrator-controlled transaction: lock settings then digest; set the digest
to `CANCELLED`, clear the lease, set its reserved candidates to `WAITING` with
`digest_id = NULL`, and mark the budget reservation released. The next slot
selects remaining eligible matches. Do not alter accepted digests.

Disabling delivery stops new scheduled sends; in-flight requests may complete.
Candidates still collect and are subject to seven-day retention on resumption.

## Observability and rollout

`jobpulse.email` counters report digest, attempt, and delivery outcomes. Gauges
report pending/failed/uncertain digests, suppression, oldest waiting-candidate
age, remaining budgets, quota blocking, and feature enablement. Prometheus
alerts cover exhaustion, backlog older than two days, and failed/uncertain
messages. Startup rejects enabled sending without credentials or sender.
Logs use digest IDs and classified outcomes, never recipients, tokens, payloads,
or credentials.

Tests use fixed clocks, PostgreSQL, a local HTTP stub, and `FakeEmailProvider`;
they send no real emails. Renderer tests write `target/email-preview.html` and
`target/email-preview.txt` for local inspection. Apply additive migrations with sending off. Inspect
HTML/plain text, verify DNS and sender, enable an internal allowlist, and test
a real scheduled digest plus confirmation/one-click unsubscribe before opening
rollout. No paid upgrade is needed within the free limits.

References: [quotas](https://resend.com/docs/knowledge-base/account-quotas-and-limits),
[idempotency](https://resend.com/docs/dashboard/emails/idempotency-keys),
[webhooks](https://resend.com/docs/webhooks/verify-webhooks-requests).
