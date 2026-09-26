# Milestone 13 — authentication and API authorization

JobPulse uses Google OpenID Connect for browser sign-in. Spring Security maintains an HTTP session; the browser never receives a Google access token. Google `sub` owns saved searches and their alerts. Only an email with Google's `email_verified=true` claim can sign in. A verified email listed in `JOBPULSE_ADMIN_EMAILS` receives `ROLE_ADMIN` at sign-in; all other signed-in users receive `ROLE_USER`. Remove a former administrator's active sessions when changing the allowlist, because existing sessions retain their role until they expire or sign out.

## Local setup

1. In Google Cloud Console, create an OAuth client of type **Web application**. Add `http://localhost:5173/login/oauth2/code/google` as an authorized redirect URI. For a deployed same-origin site, add `https://YOUR_DOMAIN/login/oauth2/code/google` as a separate URI. The URI must match exactly.
2. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in your shell or an untracked local environment file. Never commit the client secret. Set `JOBPULSE_ADMIN_EMAILS` to a comma-separated list of verified Google addresses that may operate the system.
3. Set `JOBPULSE_ADMIN_ENABLED=true` if the local operations console is needed. It defaults to `false`. Run Spring and Vite as described in the README, then open `http://localhost:5173/jobs`. Vite proxies `/api`, `/oauth2`, `/login`, and `/logout` to Spring.
4. Sign in via **Sign in with Google**. `GET /api/v1/auth/session` returns the current identity and role. `GET /api/v1/auth/csrf` returns a session-bound CSRF token and header name; the frontend sends both with all state-changing requests, including sign-out.

For the production Spring profile, Google credentials and `JOBPULSE_MONITOR_PASSWORD` are required environment variables, and the session cookie is Secure, HttpOnly, and SameSite=Lax. TLS must terminate at a trusted proxy that forwards the original scheme and host. Serve frontend and API from the same origin. Do not expose Spring or the brokers directly to the internet.

## Access policy

| Route | Access |
| --- | --- |
| `GET /api/v1/jobs/**`, `GET /api/v1/analytics/jobs/**` | Public |
| `/api/v1/saved-searches/**`, `/api/v1/alerts/**` | Signed-in user, scoped to Google `sub` |
| `POST /api/v1/jobs`, `/api/v1/ingestions/**`, `/api/v1/ingestion-targets/**`, `/api/v1/ingestion-requests/**`, `/api/v1/admin/**` | Admin |
| `/actuator/health/**` | Public status only |
| `/actuator/prometheus` | Monitoring Basic account only |
| Other API routes | Denied |

The admin audit actor is taken from the authenticated subject. The `X-Admin-Actor` request header is ignored. The admin controller switch remains a second gate: when `JOBPULSE_ADMIN_ENABLED=false`, `/api/v1/admin/**` handlers are absent.

The local Compose Prometheus config uses the development-only `metrics` / `local-monitor-only` credential. Before a cloud deployment, set `JOBPULSE_MONITOR_PASSWORD` to a strong secret and provision a matching Prometheus password from a secret file. Keep Prometheus and its scrape traffic on a private network.

Migration V17 assigns pre-existing saved searches the owner `legacy-local` and disables them. They remain in the database but are not exposed to Google users or generating new alerts. After confirming the intended owner, an operator can update those rows to that account's `sub` shown by `/api/v1/auth/session` and re-enable them; do not assign them by email.

This milestone covers application authentication and authorization. The public release still requires Milestone 14's network controls, TLS, secrets, deployment automation, backups, and production verification.
