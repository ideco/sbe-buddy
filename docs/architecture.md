# Architecture

The design decisions: modules, boundaries, the codec contract, how
generation and testing work, the build. These may change without sbe-buddy
changing what it is; `intent.md` and `type-mappings.md` outrank this file.

## Modules

```
sbe-buddy-api          annotations, PrimitiveType, Codec, TypeBinding, built-in types and bindings
                       dep: agrona
sbe-buddy-processor    net.concini.sbebuddy.generator   model → IR → flyweights + codecs; knows nothing of javac
                       net.concini.sbebuddy.processor   javac elements → model; Messager; Filer
                       deps: sbe-buddy-api, sbe-tool
sbe-buddy-example      the running example, its XML oracle, the end-to-end tests; not deployed
                       deps: sbe-buddy-api; the processor on annotationProcessorPaths only
reference/             sbe-tool's sources as a submodule, for reading
```

`api ← processor`, `api ← example`. The example reaches the processor only
through `annotationProcessorPaths`, so sbe-tool is never on a user's compile
or runtime classpath; the module graph guarantees it, no build rule needed.
Base package `net.concini.sbebuddy`; each published jar sets
`Automatic-Module-Name` to its base package; no `module-info.java`.

## The generator and the processor

The generator is a library that takes a schema model and writes sources. The
processor is one front-end that builds that model from javac elements. The
whole boundary is four things, all in the `generator` package:

```java
// the model: immutable records, JDK types only, one definition of the schema;
// every node carries sinceVersion, deprecated and an opaque origin
record SchemaDef(String pkg, int id, int version, ..., List<MessageDef> messages, List<TypeDef> types, ...)
record MessageDef(String name, int templateId, ..., List<FieldDef> fields, Object origin)
record FieldDef(String name, int id, JavaFace face, WireType wire, BindingDef binding, ..., Object origin)
sealed interface WireType permits Primitive, TypeRef, EnumRef, SetRef, CompositeRef

// a cross-node problem, pointing at the node's origin
record Problem(Object origin, String message)

// where sources go
interface Output { Writer source(String pkg, String simpleName) throws IOException; }

final class Generator {
  static List<Problem> validate(SchemaDef schema);
  static Ir ir(SchemaDef schema);                        // exposed for the oracle tests
  static void generate(SchemaDef schema, Output out);    // flyweights + codecs
}
```

- `origin` is opaque to the generator. The processor stores the javac
  `Element` (with annotation mirror and value where it has them) and casts
  it back to report a `Problem` through `Messager`. A test front-end stores
  whatever it likes.
- Rules decidable from one element (missing id, unsupported component type,
  `type` and `primitiveType` together) belong to the front-end that holds
  the element. Rules that compare nodes (duplicate ids, name clashes, the
  append-only version rule, two declarations with one wire name) belong to
  `Generator.validate`.
- `WireType` is sealed and switched without `default`, so a new wire kind
  one stage forgets fails to compile.
- The boundary is enforced by one ArchUnit rule: classes in `..generator..`
  depend on nothing in `javax.lang.model`, `javax.annotation.processing` or
  `..processor..`. It is the only guardrail test in the repository.
- The processor generates each `@SbeSchema` package once, in the round that
  first shows it, and never in the `processingOver` round; javac's `Filer`
  cannot recreate a file. It never touches an Agrona buffer, so javac needs
  no JVM flag.

## Generation

- `JavaGenerator` runs with one fixed configuration equal to `SbeTool`'s
  defaults: `MutableDirectBuffer` and `DirectBuffer`, no group-order
  annotation, no interfaces, no decoding of unknown enum values, no
  types-package override, precedence checks off. No `-A` option and no
  `sbe.*` system property is read.
- Flyweights go to `<schema package>.sbe`; IR `packageName` is that
  package, `namespaceName` is `null`.
- `<Msg>Codec` and `<Iface>Codec` go to the schema package: `public final`,
  public no-arg constructor, `@Generated("sbe-buddy")`, owning one header
  encoder and decoder and the message flyweights, plus one instance of each
  binding as a private final field. Emitted with a plain `StringBuilder`,
  fully qualified names, no imports, field code in component order.
- Nothing else is generated and none of it is configurable.

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

The oracle does the work; unit tests are for the few places where a wrong
answer is cheap to pin. Reflection is banned in main code and free in tests.

- The example module holds `trading.xml`, and `SbeTool` generates reference
  flyweights from it into `com.example.trading.xmlref` at
  `generate-sources`.
- Three checks per increment: the IR from the records equals the IR from the
  XML (`XmlSchemaParser` + `IrGenerator`), token by token; the codec round
  trips at a non-zero offset with `encodedLength` equal to the bytes written;
  the codec's bytes decode with the reference flyweights and the reference's
  bytes decode with the codec.
- Every past schema version stays as a frozen file (`trading-v0.xml`, ...)
  with its own reference package, so cross-version decoding is tested in
  both directions without old-version records.
- Negative compile tests, one per rule, assert the message only.
- No test-kit module until a second consumer exists.

## Build

- Java 21 is the compiler target, so the library and the processor run on
  every JDK from 21 up. Maven through the wrapper. `./mvnw verify` is the
  gate; `./mvnw spotless:apply` before every commit.
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
