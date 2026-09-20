---
name: git
description: How work reaches main in this repository. Use when committing, pushing, opening or merging a pull request, or tagging a release.
---

# Git

`main` is linear: pull requests are rebased onto it, and their commits land
as they are. There are no merge commits, ever.

## Branches

- Every unit of work, an increment or a fix, starts from a fresh `origin/main`
  on its own branch. Branch names do not matter; whatever the tool or the
  moment suggests is fine.
- Never commit to `main` directly.
- Before pushing, bring the branch up to date with
  `git fetch origin main && git rebase origin/main`. The branch has one
  author and is short-lived, so rewriting it is always safe.

## Commits

- Subject in the imperative, under 72 characters, saying what changed:
  `Generate the message header tokens`. The body says why, when the diff
  does not.
- Every commit is one coherent step that builds. Commits land on `main` as
  they are, so before pushing fold fix-ups into the commit they fix
  (`git rebase -i origin/main`, or `git reset --soft origin/main` and
  commit again in steps).
- `./mvnw spotless:apply` before every commit; `./mvnw verify` before every
  push.
- No attribution trailers: no `Co-Authored-By`, no session link, no mention
  of the tool that wrote the change. The message says what changed and why,
  and nothing about who typed it.

## Pull requests

- One pull request per branch. The description states what the change does
  and how it was verified, with no attribution footer, generation notice or
  session link.
- Merge only when CI is green. Rebase and merge, so the branch's commits are
  replayed onto `main` and it stays linear. Delete the branch on merge.
- Repository settings that back this: merge commits and squash merging
  disabled, rebase merging only, head branches deleted automatically.

## Releases

- A release is an annotated tag `vX.Y.Z` on `main`. Nothing else.
