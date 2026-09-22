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

The decisions that shaped the first code, so later code matches it.

- A closed grammar is one file with a nested record per node: `Schema`
  for the XSD's elements, `Annotated` for the api's annotations and
  `CodecModel` for what a codec is made of. Nesting is for those three
  only, not a habit: `SchemaXml`, `Mapping`, `Generator`, `CodecWalk`,
  `CodecWriter` and `CodecTemplates` are their own files. Nested model
  types are used qualified, `Schema.Field`, `Annotated.Field`, and never
  imported: `Schema.Enum` and `Schema.Set` would shadow `java.lang` and
  `java.util` in any file that imported them. `CodecModel`'s nodes sit
  under its interfaces, `Shape.Enum`, `Member.Field`, so the walk and the
  writer import `Body`, `Member`, `Shape`, `Helper` and `Absence`, and
  every leaf is still read qualified by its interface.
- Records are pure: canonical constructor, no builder, no wither, no
  setter. A record's compact constructor copies its lists and does nothing
  else; every schema rule lives in `Generator.validate`, positioned. A
  Javadoc on a record names the XSD element it mirrors and stops; the XSD
  is the documentation.
- Components are the XSD's attributes with the XSD's names, required ones
  first, then children, then optional ones in XSD order. Optional is
  `@Nullable`, boxed where the attribute is numeric; nothing has a default,
  because a default in our code is a second copy of the XSD. Where the XML
  holds a name that sbe-tool resolves (`type`, `encodingType`,
  `dimensionType`, `valueRef`, `headerType`) the component is a `String`;
  where the XSD enumerates, it is an enum (`PrimitiveType`, `Presence`,
  `ByteOrder`); numbers the XSD types as strings stay `String`.
- Structure encodes the order rules: a message holds `fields`, `groups`
  and `data` as three lists because the XSD orders them; a composite holds
  one list of a sealed `Member` because the XSD does not. Sealed types are
  switched without `default`.
- Checked exceptions do not leave the generator except `IOException` from
  a method that takes a `Writer`. `IllegalStateException` for cannot-happen,
  `IllegalArgumentException` for a caller's mistake.
- Tests: JUnit, AssertJ, XMLUnit. Every assertion is AssertJ's, exceptions
  included (`assertThatThrownBy`); JUnit's `Assertions` are not used. Test
  classes and methods package-private, helpers shared across test packages
  public;
  parameterized tests over an explicit list, never classpath scanning,
  except the tests module, which finds its schema cases on its own
  classpath so a new schema needs no registration;
  method names are sentences in camelCase. `Fixtures` holds plain
  factories for the few models the generator's tests build by hand, the
  fixed defaults inside them; a test builds a node once and passes it to
  its parent. A rule a user can break is tested as the source they write.
- Imports, never fully qualified names in code. Spotless removes unused
  imports and orders them; nothing adds an import for you. The one exception is a
  type that collides with one of ours in the same file, such as
  `javax.xml.validation.Schema` beside `Schema`; it is qualified and the
  line says why.
- Not used anywhere: Lombok, `Utils` classes, an interface with one
  implementation, an abstraction for a front-end that does not exist yet.
