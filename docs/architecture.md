# Architecture

The design decisions: modules, the pipeline, the model, the rules, how
generation and testing work, the build. These may change without sbe-buddy
changing what it is; `intent.md` and `type-mappings.md` outrank this file.

## Modules

```
sbe-buddy-generator    net.concini.sbebuddy.generator   the model, SchemaXml, Generator, the codec emitter, the corpus
                       dep: sbe-tool
sbe-buddy-api          annotations, PrimitiveType, Codec, TypeBinding, built-in types and bindings
                       dep: agrona
sbe-buddy-processor    net.concini.sbebuddy.processor   javac elements → model; Messager; Filer
                       deps: sbe-buddy-api, sbe-buddy-generator
sbe-buddy-example      the running example, its XML oracle, the end-to-end tests; not deployed
                       deps: sbe-buddy-api; the processor on annotationProcessorPaths only
reference/             sbe-tool's sources as a submodule, for reading
```

`generator ← processor`, `api ← processor`, `api ← example`. The generator
knows nothing of javac and nothing of the api, and the example reaches the
processor only through `annotationProcessorPaths`, so sbe-tool is never on
a user's compile or runtime classpath; the module graph guarantees all of
it, no build rule needed. Base package `net.concini.sbebuddy`; each
published jar sets `Automatic-Module-Name` to its own package; no
`module-info.java`.

The generator is the core: a codec generator over sbe-tool's own toolchain,
usable from any front-end that can build the model. The annotations and the
processor are one such front-end.

## The pipeline

One compilation of an `@SbeSchema` package runs, in order:

```
1  javac elements ──► SchemaDef        ours      processor: annotations to the model, our rules
2  SchemaDef ──► schema.xml            ours      SchemaXml: one element per node, one attribute per member set
3  schema.xml ──► MessageSchema        sbe-tool  XmlSchemaParser.parse: the XSD's rules and the parser's
4  MessageSchema ──► Ir                sbe-tool  IrGenerator
5  Ir ──► flyweight sources            sbe-tool  JavaGenerator, through a Writer per file
6  Ir + SchemaDef ──► codec sources    ours      the codec emitter
7  schema.xml ──► a resource           ours      the schema ships in the jar
```

sbe-buddy writes the XML that goes in and consumes the IR that comes out.
Nothing with wire semantics, no offset, no null value, no flyweight method
name, is computed here.

## The model

- One file, `Schema`: the `messageSchema` record with a nested record per
  `sbe.xsd` element named after it, `Schema.Type`, `Schema.Composite`,
  `Schema.Ref`, `Schema.Enum`, `Schema.ValidValue`, `Schema.Set`,
  `Schema.Choice`, `Schema.Message`, `Schema.Field`, `Schema.Group`,
  `Schema.Data`, used qualified and never imported. One component per XSD
  attribute with the XSD's name, `value` for element text. An attribute
  the XSD makes optional is `@Nullable`; nothing carries a default, so the
  model holds what was written and `SchemaXml` writes exactly that.
  Children are `List`s in declaration order; the two sealed interfaces,
  `Declaration` for what `types` holds and `Member` for what a composite
  holds, need no `permits` because the file is the closed set.
- Enumerated attributes use sbe-tool's vocabulary, `PrimitiveType` and
  `Presence`, and `java.nio.ByteOrder`. The generator depends on sbe-tool
  anyway, and its names are the names.
- Two halves. The SBE half is the XSD and the only thing `SchemaXml`
  reads. The Java face, component types, boxing, bindings, family
  membership, is what the codec emitter reads and XML cannot hold; how it
  attaches to the records is decided by the codec increment, and the XML
  never sees it.
- No `origin`. The model is a value: equal when it says the same thing,
  built by a test without positions. The processor keeps its own map from
  node to javac `Element`, and `Problem(Object node, String message)`
  names the node for the processor to place.

## Rules

Three layers, in the order a mistake meets them.

- **The type system.** Annotation members are typed as the XSD types them:
  enumerations are enums, type references are classes, required attributes
  are required members. Two-thirds of sbe-tool's parser rules are name
  resolution over a text format and cannot fire from typed Java; javac
  fails the file first.
- **Ours, positioned.** The rules SBE has no model for, and the few of
  sbe-tool's that a user hits often enough to deserve an exact position and
  our wording: the append-only version rule, duplicate ids and names,
  `sinceVersion` above the schema version, field and type presence
  mismatch, the id range, the name pattern, field then group then data
  order. Rules decidable from one element live in the processor; rules
  that compare nodes live in `Generator.validate`, which returns
  `Problem`s for the processor to place. One negative compile test each.
- **sbe-tool, as backstop.** The document is validated against `sbe.xsd`
  from the sbe-tool jar, then parsed with `stopOnError` and an
  `errorPrintStream` we own. Whatever is reported lands on the
  `@SbeSchema` package element with sbe-tool's text verbatim, which
  already names the node (`at <message name="Order"> <field name="qty">`).
  Warnings become javac warnings. A rule that turns out to matter to users
  graduates to the layer above; nothing parses sbe-tool's strings.

## Generation

- `Generator.generate(schema, output)` runs steps 2 to 7. `Output` is where
  sources and the resource go: the processor's is over `Filer`, a test's
  over a map.
- `JavaGenerator` runs with one fixed configuration equal to `SbeTool`'s
  defaults: `MutableDirectBuffer` and `DirectBuffer`, no group-order
  annotation, no interfaces, no decoding of unknown enum values, no
  types-package override, precedence checks off. No `-A` option and no
  `sbe.*` system property is read. `setPackageName(ir.applicableNamespace())`
  is called before `generate()` (`notes.md`).
- Flyweights go to `<schema package>.sbe`; IR `packageName` is that
  package, `namespaceName` is `null`.
- The schema goes into the jar as `<schema package>/schema.xml`, so a jar
  of records carries its own schema and sbe-tool's other generators produce
  the other side of the wire from it. The IR itself is built in memory in
  every compilation and is what steps 5 and 6 consume; only its file form,
  `.sbeir`, is not written: `IrEncoder` serializes it with Agrona's
  `UnsafeBuffer`, which would make a user's javac need a JVM flag, and
  `-Dsbe.generate.ir=true` on the resource produces it.
- The codec emitter walks the IR the way `JavaGenerator` does, with
  `GenerationUtil.collectFields`, `collectGroups` and `collectVarData`,
  and names flyweight members through `JavaUtil`, so the codec calls what
  was generated, by construction. Each token's Java face comes from the
  model by name. Fields, then groups, recursing into each body, then
  var-data; decoding is the mirror. `encodedLength` takes its shape from
  the IR and its numbers from the flyweights' constants (`BLOCK_LENGTH`,
  `sbeHeaderSize()`, `sbeBlockLength()`, `ENCODED_LENGTH`), so generated
  code holds no magic numbers.
- `<Msg>Codec` and `<Iface>Codec` go to the schema package: `public final`,
  public no-arg constructor, `@Generated("sbe-buddy")`, owning one header
  encoder and decoder and the message flyweights, plus one instance of each
  binding as a private final field. Emitted with a plain `StringBuilder`,
  fully qualified names, no imports, field code in component order.
- Nothing else is generated and none of it is configurable.
- The processor generates each `@SbeSchema` package once, in the round that
  first shows it, and never in the `processingOver` round; javac's `Filer`
  cannot recreate a file. It loads no Agrona buffer class, so a user's
  javac needs no JVM flag; that `JavaGenerator` keeps this true is checked
  by the increment that first runs it.

## The codec contract

```java
public interface Codec<T> {
  int encodedLength(T value);                                    // exact, header included
  int encode(T value, MutableDirectBuffer buffer, int offset);   // writes exactly encodedLength(value) bytes, returns it
  T decode(DirectBuffer buffer, int offset);                     // acting version and block length from the header
  int lastDecodedLength();                                       // bytes consumed by the last decode
  int decodedLength(DirectBuffer buffer, int offset);            // bytes a decode would consume, without decoding
}
```

- `decode` checks `schemaId` and `templateId` and throws
  `IllegalArgumentException` on a mismatch; a family codec throws on a
  template id outside its `permits` set.
- Codecs are stateful instances: one per thread, no static methods, no
  static mutable state, nothing shared. Bindings are stateless.
- Runtime errors are `IllegalArgumentException` (header mismatch, unknown or
  null enum value, `null` in a required field, an array of the wrong length,
  a string that does not fit). No checked exceptions, no error codes, no
  logging anywhere; the processor speaks through `Messager`, the runtime
  through exceptions.
- `Codec<T>` is implemented only by generated code, so it may grow; a new
  member gets a `default` if hand-written implementations ever exist.

## Testing

Three levels; the bulk sits at the first. Reflection is banned in main
code and free in tests.

- **The corpus, in the generator.** Each case pairs a hand-built
  `SchemaDef` with a hand-written oracle XML: one case per XSD feature,
  and one per shape worth taking from sbe-tool's own test schemas, written
  fresh and never copied. Per case: `SchemaXml.of(schema)` is equivalent
  to the oracle, and the oracle parses through `XmlSchemaParser` with no
  error and no warning, so a wrong oracle cannot agree with a wrong
  writer. Once over the corpus: every element and attribute `sbe.xsd`
  declares occurs in some oracle, so completeness is a test, not a claim.
  No javac, no annotations, no IR under test.
- **Equivalence** is XMLUnit's, held in `SchemaXmlAssert` in the
  generator's tests: whitespace and comments ignored; both documents
  parsed with `sbe.xsd` attached, so the XSD's defaults are filled and an
  absent attribute equals its default; `type`, `composite`, `enum`, `set`
  and `message` matched by `name` regardless of order; everything else in
  sequence, because offsets follow declaration order and a moved field is
  a different schema. A test is one line through it, or an XPath probe for
  a single attribute.
- **The processor.** Fixture records compiled through the Compiler API
  with the processor attached; the model it built equals the hand-built
  one. Negative compile tests, one per rule of ours and one per backstop
  channel, assert the message only.
- **The example, end to end.** `trading.xml` beside the records is the
  oracle, and the schema resource the processor wrote is equivalent to it,
  through `SchemaXmlAssert` from the generator's test jar. `SbeTool`
  generates reference flyweights from the oracle into
  `com.example.trading.xmlref` at `generate-sources`; the codec round trips
  at a non-zero offset with `encodedLength` equal to the bytes written, the
  codec's bytes decode with the reference flyweights, and the reference's
  bytes decode with the codec.
- Every past schema version stays as a frozen file (`trading-v0.xml`, ...)
  with its own reference package, so cross-version decoding is tested in
  both directions without old-version records.
- Test libraries: JUnit, AssertJ, XMLUnit with its AssertJ module. No
  test-kit module until a second consumer exists.

## Build

- Java 21 is the compiler target, so the library and the processor run on
  every JDK from 21 up. Maven through the wrapper. `./mvnw verify` is the
  gate; `./mvnw spotless:apply` before every commit.
- Maven 4 with `4.1.0` POMs, pinned by the wrapper at `4.0.0-rc-6`: the root
  POM carries `root="true"`, a subproject declares neither its own version
  nor its parent's, and a dependency inside the reactor omits its version.
  Maven installs a `4.0.0` consumer POM beside each artifact, so none of this
  reaches a consumer. The wrapper is generated `only-script`, so `./mvnw`
  execs the distribution's own `bin/mvn`; the classic wrapper parses
  `.mvn/jvm.config` itself and would reject the comment in it.
- javac runs with its defaults: no `-Xlint`, no `-Werror`. Generated code
  is compiled by the same javac and cannot be fixed, and lint categories
  change with each JDK.
- Formatting: Spotless with the Eclipse JDT formatter and a profile that
  keeps hand-written line breaks and puts a closing parenthesis on its own
  line when the arguments wrap; otherwise Eclipse's defaults (tabs, 120
  columns).
- Nullness: JSpecify annotations, `@NullMarked` on every hand-written main
  package, checked by NullAway at error level. NullAway runs as an Error
  Prone plugin with every other Error Prone check disabled and generated
  sources excluded by path; the compiler-internals exports it needs live
  in `.mvn/jvm.config`. JSpecify is `optional` in the api so users do not
  inherit it. Test code is not checked.
- Test JVMs carry `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED`,
  which Agrona needs in any JVM that loads a buffer class. Users' javac
  needs no flag, because the processor never touches an Agrona buffer.
- Dependencies and plugins are added by the increment that first needs
  them, with their versions in the root pom and nowhere else.
- `reference/simple-binary-encoding` is a git submodule at the sbe-tool
  tag the build depends on. It is read to confirm behaviour and never
  copied from or edited.
- CI is one GitHub Actions job running `./mvnw -B -ntp verify`. `main`
  takes pull requests only.
