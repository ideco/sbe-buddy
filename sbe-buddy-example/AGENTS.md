# sbe-buddy-example

Realistic schemas a user would write, `com.example.trading` and
`com.example.quotes`, and a client of the first, `com.example.client`,
compiled with the processor. It proves the wiring and interop with sbe-tool;
coverage belongs in sbe-buddy-tests.

- Each package's oracle is a file in `src/main/sbe`. One test per package
  asserts the `schema.xml` in the class output equals it.
- An oracle grows only with the change that needs it. Never edit it to make a
  failing test pass; a mismatch means the model, the writer or the records
  are wrong.
- `trading` is the showcase: order entry in FIX's shapes, without being
  FIX or taken from its standard. It stays at version 0; every construct a
  user would reach for appears where such a schema would use it, beside the
  bindings a user would write. Its schema is written fresh, and sbe-tool's
  flyweights are the only byte reference.
- `quotes` grows a construct per increment and bumps its schema version. The
  previous oracle is then frozen as `quotes-vN.xml`, with only its leading
  comment saying so, and never edited again.
- The pom runs `SbeTool` over each oracle and each frozen version at
  `generate-test-sources`, into `xmlref` and `xmlref.vN`, test code only. A
  new frozen version needs its own execution.
- The reference tests encode with our codecs and decode with sbe-tool's
  flyweights, and the reverse, across versions in both directions.
- `client` is schema-first: it maps part of `trading.xml`, read from the
  class path, as a client maps a venue's published schema, reusing the
  venue's types and bindings across the package boundary. `src/main/sbe`
  is a resource directory for that, so the oracles are on the class path.
  Its test crosses with the venue's codecs and sbe-tool's flyweights.
