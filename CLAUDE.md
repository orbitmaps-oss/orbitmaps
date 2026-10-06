# CLAUDE.md

The project rules live in AGENTS.md and apply in full:

@AGENTS.md

## Notes for Claude Code
- Explain the planned change before editing files; keep each commit small and signed off (`git commit -s`).
- Never run `git push`, `wrangler deploy`/`publish`, or anything that touches live servers — ask the user to do it.
- Before adding any dependency, check its licence and stop if it is not open source or is GPL-only; update `docs/THIRD_PARTY.md` and `config/dependency-allowlist.txt` in the same commit.
- Never read, print or commit secrets, keystores, `google-services.json`, `.env` or `.dev.vars`. Don't commit `.idea/` or `local.properties`.