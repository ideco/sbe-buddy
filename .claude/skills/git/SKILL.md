---
name: git
description: How work reaches main in this repository. Use when committing, pushing, opening or merging a pull request, or tagging a release.
---

# Git

`main` is linear: every pull request lands as one squashed commit, whose
subject is the pull request's title and whose body is its description.
There are no merge commits, ever.

## Branches

- Every unit of work, an increment or a fix, starts from a fresh `origin/main`
  on its own branch. Branch names do not matter; whatever the tool or the
  moment suggests is fine.
- Never commit to `main` directly.
- Before pushing, bring the branch up to date with
  `git fetch origin main && git rebase origin/main`. The branch has one
  author and is short-lived, so rewriting it is always safe.

## Commits

- Commits on a branch are working history: they are squashed away on merge.
  Split the work into commits as it helps review; nothing else about them
  reaches `main`.
- Subject in the imperative, under 72 characters, saying what changed:
  `Generate the message header tokens`. The body says why, when the diff
  does not. The same rules bind the pull request's title and description,
  because those become the commit on `main`.
- `./mvnw spotless:apply` before every commit; `./mvnw verify` before every
  push.
- No attribution trailers: no `Co-Authored-By`, no session link, no mention
  of the tool that wrote the change. The message says what changed and why,
  and nothing about who typed it.

## Pull requests

- One pull request per branch, one coherent change per pull request: it
  becomes one commit on `main`, so it must read as one.
- The title is the commit subject: imperative, under 72 characters, what
  changed. The description is the commit body: what the change does, why,
  and how it was verified; no attribution footer, generation notice or
  session link.
- Merge only when CI is green. Squash and merge. Delete the branch on merge.
- Repository settings that back this: merge commits and rebase merging
  disabled, squash merging only, the default squash message taken from the
  pull request's title and description, head branches deleted
  automatically.

## Releases

- A release is an annotated tag `vX.Y.Z` on `main`, whose root POM carries
  `X.Y.Z`. Pushing the tag runs `.github/workflows/release.yml`, which
  checks both, builds, and publishes the api, the generator and the
  processor to Maven Central.
- The version lands on `main` in a pull request of its own, or with the
  change it releases; the pull request after the release moves the root
  POM to the next `-SNAPSHOT`.
