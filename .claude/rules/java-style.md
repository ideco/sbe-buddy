---
paths:
  - "**/*.java"
---

# Java style

Write pragmatic, modern Java with low ceremony. Optimize for correctness and local readability. Prefer code whose behavior can be understood without chasing unnecessary indirection.

Where this guide conflicts with project-specific architecture or constraints, the project documentation wins.

## Structure

- Keep related logic together and execution flow visible.
- Extract helpers when they clarify meaning, isolate real complexity, or remove meaningful duplication. Avoid chains of trivial pass-through methods.
- Do not split coherent methods to satisfy arbitrary size or abstraction rules.
- Prefer concrete classes. Introduce interfaces for meaningful boundaries or interchangeable behavior.
- Build for current requirements. Avoid speculative factories, strategy hierarchies, and generic frameworks.
- Record components that carry annotations go one per line.

## Control flow

- Prefer guard clauses and early returns over nested if/else.
- Combine conditions with the same outcome when readability and short-circuit behavior remain clear.
- Prefer streams for transformation and collection; loops for side effects, mutable state, or complex control flow.
- Inline trivial expressions. Name intermediate values when they clarify intent or avoid repeated work.
- Keep obvious single-use strings inline. Use constants for domain concepts and shared policy, not merely because a value appears more than once.
- Always use braces.

## Types and state

- Use explicit types; never `var`.
- Use clear, descriptive names without unnecessary abbreviations.
- Prefer immutable data and records for simple data carriers.
- Use `final` for fields that do not change after construction. Do not add `final` to locals or parameters merely for style.
- Use modern Java features when they simplify code without compromising behavior, performance, or concurrency guarantees.

## Nullability and errors

- Treat unannotated references in code we control as non-null. Mark legitimate absence with the project's `@Nullable` annotation.
- Do not introduce `Optional` into APIs we control unless there is a compelling reason. Accommodate it where an external API requires it.
- Validate external input and enforce meaningful domain constraints where invariants are established. Trust established invariants internally.
- Avoid redundant null checks, especially in private/internal code whose callers establish the contract.
- Respect nullable or unspecified contracts in external APIs.
- Do not silently ignore invalid values, invent fallback defaults, or catch exceptions merely to continue. Recovery must have defined semantics.

## Documentation

- Class Javadoc describes the class as a whole: its purpose, contract, and important constraints.
- Keep class Javadoc short. Prefer 1–3 sentences and normally do not exceed 4.
- Do not use Javadoc to explain individual fixes, incidents, bug analysis, implementation history, or why a particular change was made. That belongs in version control.
- Document interfaces and interface methods as contracts for callers and implementers.
- Omit Javadoc on self-explanatory implementation methods.
- Let implementations inherit interface documentation unless they add relevant behavior.
- Document surprising behavioral or concurrency constraints when callers need to know them.
- Avoid redundant `@param` and `@return` descriptions.
- Use inline comments for non-obvious reasons or constraints, not to narrate the code or preserve debugging history.

## Scope

Use the repository formatter and supported Java version.

Apply these rules to the code being changed. Do not perform unrelated cleanup or refactoring.

## sbe-buddy specifics

- Imports, never fully qualified names. The one exception is a type that
  collides with one of ours in the same file, such as
  `javax.xml.validation.Schema` beside `Schema`; it is qualified and the
  line says why. Nested model types whose names shadow `java.lang` or
  `java.util`, such as `Schema.Enum` and `Schema.Set`, are used qualified
  by their outer type and not imported.
- Records are pure: canonical constructor, no builder, no wither, no
  setter. A compact constructor copies lists and does nothing else.
- Sealed types are switched without `default`.
- `IllegalStateException` for cannot-happen, `IllegalArgumentException`
  for a caller's mistake. Checked exceptions do not leak out of the
  generator, except `IOException` from a method that takes a `Writer`.
- Tests: JUnit and AssertJ. Every assertion is AssertJ's,
  exceptions included (`assertThatThrownBy`). Test classes and methods
  are package-private; method names are sentences in camelCase.
- Not used anywhere: Lombok, `Utils` classes, an interface with one
  implementation.
