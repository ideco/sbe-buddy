# Increment 5: sbe-tool in the pipeline, and the first release

## Goal

Records in, sbe-tool's flyweights out. The generator gains
`Generator.generate`, which hands the document to sbe-tool's own toolchain
and writes the flyweights it produces; the processor calls it and puts the
sources through the `Filer`; the api depends on Agrona so a user's compile
classpath carries what the flyweights need. Sbe-tool is the backstop, not
the subject: the corpus already proves the document is the one a person
would write, so nothing here tests what sbe-tool makes of it. The example
compiles its flyweights and uses them once. Then `v0.1.0`.

## Settled before it started

- `IrGenerator.generate(schema, namespace)` sets the IR's `namespaceName`,
  which `applicableNamespace()` prefers over the package, so the
  flyweights go to `<schema package>.sbe` while the document keeps its
  package. `JavaGenerator` takes Agrona's `DynamicPackageOutputManager`,
  `OutputManager` with `setPackageName`, and Agrona ships
  `StringWriterOutputManager` for tests. Precedence checks are off by
  `PrecedenceChecks.newInstance(new Context())`. Sbe-tool 1.40.2 builds
  against Agrona 2.6.1. All in `notes.md`.

## What gets built

- **`Generator.generate(Schema schema, DynamicPackageOutputManager
  output)`**, returning the `Problem`s, in the generator. Steps 3 to 6 of
  the pipeline: `SchemaXml` writes the document; it is validated against
  `sbe.xsd` from the sbe-tool jar through `javax.xml.validation` with an
  error handler that fails; `XmlSchemaParser.parse` runs with
  `stopOnError`, `warningsFatal` and an `errorPrintStream` of ours; then
  `IrGenerator` with the namespace `<schema package>.sbe`, and
  `JavaGenerator` with `SbeTool`'s defaults, `MutableDirectBuffer` and
  `DirectBuffer`, no group-order annotation, no interfaces, no decoding of
  unknown enum values, no types-package support, precedence checks off,
  after `setPackageName(ir.applicableNamespace())` on the output. Every
  line sbe-tool reports, warning or error, becomes a `Problem` naming the
  `Schema` with sbe-tool's text verbatim, and nothing is generated; the
  corpus proves our documents raise none, so one that appears is ours to
  fix. The parameter is Agrona's interface, not one of ours: two
  implementations exist, the processor's and Agrona's own for tests, and
  a third would still fit. `Annotated` joins the signature with the codec
  emitter, not before.
- **The processor** calls it after `Generator.validate`, over a
  `DynamicPackageOutputManager` backed by `Filer.createSourceFile`, with
  the package element as the originating element, and a second open of
  the same name handed a writer that discards, as `notes.md` says the
  header flyweight needs. Problems land on the package element through
  the origins, like every other schema-level problem. The resource is
  written after the flyweights, so a package with any problem still gets
  nothing.
- **Agrona on the api**, compile scope, at the version sbe-tool builds
  against, with the version in the root POM. The generator already reaches
  it through sbe-tool.
- **The tests, ours only.** In the generator, one test builds a `Schema`
  our rules accept and sbe-tool rejects, a field whose `type` names no
  declaration, and asserts a `Problem` naming the schema with sbe-tool's
  text in it. In the processor, one placement snippet does the same from
  source, a mistake our rules let through such as two enum constants with
  one value, and asserts the diagnostic lands on the package element.
  The in-memory helper keeps `-proc:only`; compiling generated flyweights
  per corpus case would only test sbe-tool. No IR comparison, no
  reference flyweights, no `xmlref`.
- **The example.** The build compiles the flyweights the processor
  generates into `com.example.trading.sbe`, and one smoke test encodes an
  order through `NewOrderEncoder` and reads it back through the decoder.
  `SchemaResourceTest` stays. That is the product seen working once;
  coverage stays in the corpus.
- **The release.** The root POM's version becomes `0.1.0`; the README
  says what sbe-buddy is, shows the example, names the coordinates and
  what a build needs, and says what is generated and that codecs are not
  yet; after the merge, an annotated tag `v0.1.0` on `main`, and a
  follow-up commit takes the version to `0.2.0-SNAPSHOT`. No repository
  is published to.
- **The documents.** `notes.md` takes what the increment verifies about
  `JavaGenerator` inside javac; `architecture.md` and `intent.md` follow.

## Criteria

- `./mvnw verify` builds the example through the processor, compiles its
  flyweights and passes its smoke test; the example's compile classpath
  holds the api and Agrona and nothing else.
- Plain `javac` with no JVM flag and only the api and Agrona on the
  compile classpath compiles the example and writes its flyweights and
  its `schema.xml`; `JavaGenerator` loads no Agrona buffer class inside
  javac, and `notes.md` says so.
- Nothing in the generator's main sources names `javax.lang.model` or
  `javax.annotation.processing`.
- Every corpus oracle still parses through sbe-tool with no error and no
  warning, which is what makes the backstop silent for every document we
  write.
- `./mvnw verify` is green on a fresh clone, the CI job passes on this
  pull request, and `v0.1.0` is tagged on `main` after it merges.

## Out of scope

`Codec`, `TypeBinding`, `@Bind`, the built-in bindings, the codec emitter
and `@SbeSchema(codecs = false)`. Reference flyweights, frozen schema
versions, IR comparison, the `.sbeir` file. Message families. Publishing
to Maven Central or anywhere else.
