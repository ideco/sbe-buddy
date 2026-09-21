# Increment 4: discovery, the processor, and the schema in the jar

## Goal

Annotated records compile into their schema. The processor gains
discovery, javac elements to `Annotated`, and around it the few lines that
make it an annotation processor: the package as the unit of work, every
`Problem` placed on the element that carries the mistake, and `schema.xml`
written into the jar. The corpus gains a fourth view, the Java source, so
one corpus proves source to `Annotated` to `Schema` to XML, and the
processor is tested over it without Maven. The example module becomes the
integration proof: a realistic schema compiled by the real build, its
resource checked against a file oracle. sbe-tool in the pipeline, the
flyweights and the first release are increment 5.

## Settled before it started

- The spike, in `notes.md`: javac exposes `CLASS`-retained annotations on
  the record components of a type loaded from a class file, so the api's
  `MessageHeader` and a user's declared types in a library jar discover
  like source. Every member is read with its defaults filled.
- The generator publishes its tests as a test jar, and `Corpus` and
  `Case` are public, so the processor's tests read `Corpus.CASES`.

## What gets built

- **The corpus's fourth view.** Each case gains two text blocks,
  `PACKAGE_INFO` and `SOURCE`: the `package-info.java` carrying
  `@SbeSchema`, and one compilation unit holding the case's records,
  package-private and several to a file, nested where a user would nest.
  The package is the one the twin names (`corpus.primitives`), the Java
  names are the twin's, the annotations say what the twin holds and
  nothing more. `Corpus.Case` carries both. A case then reads top to
  bottom as source, annotated, schema, XML.
- **`Discovery`, in the processor.** `Discovered discover(PackageElement
  schemaPackage, Elements elements)`, where `Discovered` holds
  the `Annotated`, the `Problem`s, and identity maps from each `Annotated`
  node to its `Element` and, where there is one, its `AnnotationMirror`.
  The package annotation from the `PackageElement`; the declarations the
  package makes from its enclosed types in source order, nested types
  included; the messages likewise; each `Class`-typed member from the
  `AnnotationMirror`, resolved to the `TypeElement` and from there to the
  declaration it names, across packages and from jars, each declaration
  one instance however often it is reached. The Java type descriptor from
  the component's `TypeMirror`: a primitive or its box, `String`,
  `byte[]`, `List<E>` with `E` a record, a type carrying a declaration
  annotation, or `Other` with its name. Rules that only javac can see fire
  here and name the `Element`: a schema annotation on something that is
  not a record, `@SbeField`, `@SbeGroup` or `@SbeData` on a component
  outside a message or group record, `@SbeEnumValue` on anything but an
  enum constant, `@SbeChoice` outside a set, a `Class` member naming a
  type that carries no declaration annotation, and a `List<E>` whose `E`
  is not a record.
- **The processor.** `SbeProcessor`, registered through
  `META-INF/services`, supporting `net.concini.sbebuddy.*` and the latest
  source version. Triggered by any annotated element in a round, it takes
  that element's package as the unit of work and handles each package once,
  in the round that first shows it, never in the `processingOver` round.
  Per package: discover, map, validate; every `Problem` becomes a
  `Messager` error on the element it names, resolved through the identity
  maps in at most two lookups, with the `AnnotationMirror` where there is
  one; a package with any problem gets its errors and no output. Otherwise
  `schema.xml` goes through `Filer.createResource` into `CLASS_OUTPUT`
  under the package, written by `SchemaXml`. Nothing else is generated
  yet; the pipeline stops at step 3 and step 8.
- **The processor's tests.** One in-memory compilation helper over the
  Compiler API: sources as strings, the test classpath so the api is
  visible, `-proc:only`, diagnostics and written files collected. Over the
  corpus, two parameterized tests: a capturing processor hands the package
  element to `Discovery` and the discovered `Annotated` equals the case's
  `annotated()` by record equality with no problems and no diagnostics;
  the real processor runs and the `schema.xml` it wrote is equivalent to
  the case's oracle through `SchemaXmlAssert`. Beside them: one negative
  snippet per layer, a discovery rule, a `Mapping` rule and a
  `Generator.validate` rule, asserting the diagnostic's kind, element and
  line, which proves placement, since the rules themselves are tested in
  the generator; and one snippet resolving a declared type across a
  package boundary in the same compilation. Resolution from a jar is
  every corpus case, through the api's `MessageHeader`.
- **The example module.** One realistic schema, `com.example.trading`,
  said in annotated records a user would write: an order with an enum, a
  composite, a group and var-data, and a second message. The processor
  reaches it through `annotationProcessorPaths` only. Its oracle is
  `src/main/sbe/trading.xml`, where `SbeTool` will read it in the next
  increment, and one test asserts the `schema.xml` in the class output
  equivalent to it through `SchemaXmlAssert` from the generator's test
  jar. The example is the integration proof and the thing a user reads;
  coverage stays in the corpus.
- **The documents.** `notes.md` takes every javac fact the increment
  relies on, with the spike or file it came from.

## Criteria

- Every corpus case's source discovers to its `annotated()` by equality,
  and compiles through the real processor to a `schema.xml` equivalent to
  its oracle.
- Every rule of ours reaches the user as a javac error on the element
  that carries the mistake; the three placement snippets prove one per
  layer.
- `./mvnw verify` builds the example through the processor and its
  resource matches its oracle; the example's compile classpath holds the
  api and nothing of the generator, sbe-tool or the processor.
- Nothing in the generator's main sources names `javax.lang.model` or
  `javax.annotation.processing`; nothing in the processor loads an Agrona
  class, so a user's javac needs no JVM flag.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

sbe-tool in the pipeline: `XmlSchemaParser` as backstop, `IrGenerator`,
`JavaGenerator`, the flyweights, `Generator.generate` and `Output`. The
first release. `@SbeSchema(codecs = false)`, `Codec`, `TypeBinding`,
`@Bind`, the built-in bindings, message families, Agrona. Reference
flyweights and frozen schema versions in the example.
