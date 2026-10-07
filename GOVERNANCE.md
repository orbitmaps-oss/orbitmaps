# Governance

## Current model: benevolent lead

Orbit Maps is led by **[@Ravikant97](https://github.com/Ravikant97)** (project lead). The lead:

- sets the roadmap and makes final decisions when consensus is not reached;
- reviews and merges pull requests, and manages releases;
- enforces the [Code of Conduct](CODE_OF_CONDUCT.md);
- grants and revokes repository permissions.

## How decisions are made

1. Most changes are decided in the pull request: a maintainer review and green CI are enough.
2. Larger changes (new features, architecture, new data sources, new dependencies with notable
   impact) start as a GitHub issue or discussion labelled `rfc`, open for at least 7 days.
3. **Changes to the licence, the App Store additional permission, or the privacy principles in
   [AGENTS.md](AGENTS.md) and [PRIVACY.md](PRIVACY.md) always need a public RFC** open for at least
   14 days, and an explanation from the lead of the final decision.

## Becoming a maintainer

Contributors with a record of sustained, high-quality contributions and reviews may be invited by
the lead to become maintainers (triage, review and merge rights).

## Moving to a maintainer team

When the project has **3 to 5 regular contributors**, the lead will propose, in a public RFC, a
maintainer team that makes decisions by lazy consensus, with a vote of maintainers as a fallback,
and will update this document accordingly.

## Repository

<https://github.com/orbitmaps-oss/orbitmaps>
