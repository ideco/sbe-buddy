# Increment 1: the build

## Goal

A repository that builds green on a fresh clone and in CI, with the three
modules in place and empty, the formatter and the nullness check wired in,
and sbe-tool's sources checked out for reading. No SBE code yet; the
example, the oracle and every dependency they need come with increment 2.

## What gets built

- The parent pom and the three modules from `docs/architecture.md`, each
  with a `package-info.java` carrying `@NullMarked` and nothing else.
  Versions are declared in the parent pom only.
- Java 21 as the compiler target. The Maven wrapper.
- Spotless with the Eclipse JDT formatter and a profile of three
  settings: `join_wrapped_lines=false`, and
  `parentheses_positions_in_method_invocation`,
  `parentheses_positions_in_method_delcaration` (sic, Eclipse's spelling)
  and `parentheses_positions_in_record_declaration` set to
  `separate_lines_if_wrapped`. Everything else stays at the Eclipse
  defaults. `spotless:check` runs at `verify`.
- Error Prone as the carrier for NullAway and nothing else:
  `-XepDisableAllChecks -Xep:NullAway:ERROR`, `OnlyNullMarked`, JSpecify
  mode, generated sources excluded by path. The compiler-internals
  exports Error Prone needs go in `.mvn/jvm.config` with a one-line
  comment saying why. JSpecify is `optional` in the api module.
- `reference/simple-binary-encoding` as a git submodule at the sbe-tool
  1.40.2 tag, for reading.
- CI: one GitHub Actions workflow, one job, `./mvnw -B -ntp verify` on
  pull requests and pushes to `main`, submodule checked out.
- `README.md` with the first paragraph of `docs/intent.md` and the build
  command.

## Criteria

- `./mvnw verify` is green on a fresh clone with JDK 21 and nothing else
  installed, and the CI job passes on this pull request.
- A deliberate null dereference in a `@NullMarked` package fails the
  build, and a file with a misplaced closing parenthesis fails
  `spotless:check`; both tried once and not committed.
- No dependency or plugin beyond what the above needs.

## Out of scope

Annotations, `Codec`, the example schema, sbe-tool and Agrona as
dependencies, tests, any generated code.
