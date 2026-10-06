# Orbit Maps API (Cloudflare Workers + D1 + R2)

> Placeholder. No code yet.

Serves the region-pack catalogue (from D1) and pack downloads (from R2) to the app.

## Privacy requirements

- Do not log, store or forward client IP addresses, user agents or request contents. No
  `console.log` of request data. Workers Logs and Logpush stay disabled.
- No analytics, no cookies, no user identifiers.
- Requests carry only which pack and version is wanted, never a location.

## Safety rules

- **Agents and CI never deploy.** No `wrangler deploy`/`publish`, no writes to production D1 or R2.
  Deployment is done by a maintainer, by hand.
- Never commit secrets: `.dev.vars`, `.env` and API tokens are gitignored and blocked by
  `scripts/check_forbidden_files.py`. Use `wrangler secret` for real secrets.
- `wrangler.toml` may contain resource bindings and names, but no tokens.
