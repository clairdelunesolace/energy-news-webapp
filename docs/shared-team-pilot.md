# Shared team pilot

## Account and data model

The pilot uses one shared account configured through `ADMIN_USERNAME` and
`ADMIN_PASSWORD`. Spring Security BCrypt-encodes the password at startup and
authenticates separate browser sessions against that account. The application
refuses to start with a missing or blank password. The existing development
username default is `admin`; it is not displayed as a login suggestion.

Every authenticated session has the same permissions, without roles. Watchlists,
Keywords, Articles, ArticleKeywordMatches, DailyBriefs, and AI analyses remain
global data. A keyword added, edited, disabled, or deleted in one session is seen
by another session after refreshing. No user table or ownership columns exist.
Concurrent edits use the existing behavior; this milestone adds no collaboration
or conflict-resolution mechanism.

Login and `/api/auth/me` return only authentication state and the signed-in
username for the account header, never passwords, password hashes, or provider
keys. The credential configuration's string representation redacts both values.
The login form has no registration, reset, remember-me, or default-credential UI.

Logout invalidates only that browser session. It does not erase shared data or
log out other team sessions. A shared account provides no individual attribution
or individual access revocation. To rotate the shared credential, change the
external environment and restart the backend; its in-memory sessions are lost.

## Pilot deployment prerequisites

- Supply a strong, unique `ADMIN_PASSWORD` and the chosen `ADMIN_USERNAME` to the
  backend process externally. Never commit real credentials or use example
  placeholders. Keep local `.env` files private and ignored by Git.
- Serve the frontend and `/api` through the same HTTPS origin. Set
  `SESSION_COOKIE_SECURE=true` for HTTPS. The existing session cookie settings
  already require HttpOnly and SameSite=Lax. CSRF remains required for login,
  logout, and all other mutations; the frontend obtains a token from
  `/api/auth/csrf` and sends it in the indicated header.
- If TLS terminates at a trusted reverse proxy, verify that Spring sees requests
  as HTTPS so the CSRF cookie also receives Secure. Configure forwarded-header
  handling as appropriate for that proxy (for example,
  `SERVER_FORWARD_HEADERS_STRATEGY=framework`), have the proxy replace untrusted
  incoming forwarding headers, and do not expose the backend directly.
- Verify actual session and CSRF cookie flags and login/logout through the pilot
  HTTPS URL. Local HTTP acceptance deliberately uses non-Secure cookies and is
  not evidence that production TLS/proxy configuration is complete.
- Keep PostgreSQL and provider keys backend-only. Do not pass credentials or API
  keys through `VITE_*`, frontend build arguments, logs, or browser storage.
  Avoid request/response-body and authentication-header logging.
- Review the existing deployment work separately: `.env.example` still contains
  placeholders and `SESSION_COOKIE_SECURE=false`; the Caddy/Compose setup exposes
  HTTP port 80. It does not yet pass every discovery/AI environment setting to the
  backend. Configure needed backend-only provider settings and verify persistent
  PostgreSQL storage/backups before inviting the team. Those deployment files
  are intentionally unchanged by this milestone.

## Acceptance checklist

Use two independent browser cookie jars, not merely two tabs on the same host.
For local testing, distinct loopback hosts may proxy to the same backend to give
independent sessions without changing the application architecture.

1. Check that a protected page redirects to login and a protected API returns
   401 without authentication; a wrong password must fail.
2. Sign in twice with the same shared account. In session A, add a uniquely named
   temporary keyword through the Watchlist UI.
3. Refresh session B, verify the new keyword, and disable it through the UI.
4. Refresh A and verify the disabled state. Delete only the temporary keyword
   through the normal UI, then refresh B and verify it is gone.
5. Log out A and verify protected access is blocked while B remains signed in.
   Sign in A again and check the feed and an existing Daily Brief.
6. Confirm missing/invalid CSRF tokens cannot perform mutations, no secrets
   appear in frontend bundles, and no unexpected console/network errors occur.

This verification must not trigger Discovery, RSS, AI provider calls, or scheduled
jobs, and must not manually edit database rows to manufacture shared state.
