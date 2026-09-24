# sbe-buddy-tests

The corpus, compiled by the real build: each case is a schema package run
against the flyweights and codecs the processor generated for it.

## A case

- The records go in `src/main/java/corpus/<name>`, written as a user would,
  with a `package-info.java` carrying `@SbeSchema`.
- Beside them, in `src/test/java/corpus/<name>`, one
  `<Name>Test implements SchemaCase`: a description, the oracle as a text
  block, and the round trips.
- Nothing registers a case. `Cases` finds every one on the classpath, the only
  classpath scan in the repository, and `SchemaCasesTest` runs its checks.
- A case waiting on a later increment sets `codecs = false` and says which
  increment in its Javadoc.
- One case per XSD feature, and one per shape worth taking from sbe-tool's
  test schemas, written fresh and never copied.
- A schema's versions are sibling packages, `evolution.v0`, `evolution.v1`
  beside the current `evolution`, each a case with its own records, codecs
  and oracle, so the versions cross through our codecs with no reference
  build.
- A schema-first case keeps its XML at `src/main/resources/corpus/<name>/schema.xml`
  and names it `resource = "schema.xml"`, so the check that the schema in
  the jar is the oracle holds for it as for any case. `schemafirst` is
  `bigendian`'s records over `bigendian`'s schema and asserts the generated
  sources are the twin's; `partial` maps part of a schema of its own.

## The oracle

- Hand-written, as a person writes SBE: attributes left at the XSD's default
  are omitted.
- It grows only with the change that needs it. Never edit it to make a failing
  test pass; a mismatch means the model, the writer or the records are wrong.
- `XsdCoverageTest` asserts every element and attribute of `sbe.xsd` occurs
  in some oracle, apart from an explicit list sbe-tool ignores.

## Round trips and the case's own tests

- Every round trip is checked against the whole codec contract: lengths,
  bytes around the write left alone, equality after decoding, the same bytes
  after re-encoding.
- The values are the edge cases, listed by hand: the null value of an
  optional field, an empty group and an empty set, a full-length string, a
  primitive's bounds. Each description says what its value shows.
- What only one schema owes, a refusal with its message, an older version's
  bytes, a binding's exception, is a `@Test` of the case's own. It reaches a
  byte through the flyweight's `<field>EncodingOffset()`, never a literal.
