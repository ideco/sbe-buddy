# Architecture

The design decisions: modules, the pipeline, the models, the rules, how
generation and testing work, the build. These may change without sbe-buddy
changing what it is; `intent.md` and `type-mappings.md` outrank this file.

## Modules

```
sbe-buddy-api          annotations, PrimitiveType, Presence, ByteOrder, the built-in composites; Codec, TypeBinding, bindings
                       dep: agrona, from the first release
sbe-buddy-generator    net.concini.sbebuddy.generator   Schema, SchemaXml, Annotated, Mapping, Generator, the codec emitter, the corpus
                       deps: sbe-buddy-api, sbe-tool
sbe-buddy-processor    net.concini.sbebuddy.processor   javac elements → Annotated; Messager; Filer
                       dep: sbe-buddy-generator
sbe-buddy-example      a realistic annotated schema with its oracle; the integration proof; not deployed
                       deps: sbe-buddy-api; the processor on annotationProcessorPaths only
sbe-buddy-tests        the corpus compiled by the real build: a schema package, its oracle and its round trips per case, run against the generated code; not deployed
                       deps: sbe-buddy-api; the processor on annotationProcessorPaths only; the generator and its test jar in test scope, for the oracle check
reference/             sbe-tool's sources as a submodule, for reading
```

`api ← generator ← processor`, `api ← example`, `api ← tests`. The
generator knows nothing of javac, and the example and the tests reach the
processor only through `annotationProcessorPaths`, so sbe-tool is never on a
user's compile or runtime classpath; the module graph guarantees both, no
build rule needed.
Base package `net.concini.sbebuddy`; each published jar sets
`Automatic-Module-Name` to its own package; no `module-info.java`.

The generator is the core: everything from annotated Java as data to
generated sources, over sbe-tool's own toolchain, with no javac in it. The
processor is the javac front-end and nothing more.

## The pipeline

One compilation of an `@SbeSchema` package runs, in order:

```
1  javac elements ──► Annotated       ours      discovery, in the processor: annotations as data
2  Annotated ──► Schema               ours      Mapping: type-mappings.md, and the single-element rules
3  Schema ──► schema.xml              ours      SchemaXml: one element per node, one attribute per member set
4  schema.xml ──► MessageSchema       sbe-tool  XmlSchemaParser.parse: the XSD's rules and the parser's
5  MessageSchema ──► Ir               sbe-tool  IrGenerator
6  Ir ──► flyweight sources           sbe-tool  JavaGenerator, through a Writer per file
7  Ir + Annotated ──► codec sources   ours      the codec emitter
8  schema.xml ──► a resource          ours      the schema ships in the jar
```

sbe-buddy writes the XML that goes in and consumes the IR that comes out.
Nothing with wire semantics, no offset, no null value, no flyweight method
name, is computed here. With step 7 off (below), the pipeline is the first
release: annotated records in, sbe-tool's own flyweights out.

Discovery orders the package's top level itself, messages by `id` and
declarations by qualified Java name, never in the order javac entered the
types, which an incremental build does not keep. Mapping then writes the
header first and each declaration after the ones it names. The same
sources give the same `schema.xml` however they were compiled; sbe-tool
reads `types` by name and `message` by id, so the order carries no meaning
on the wire.

## The models

Two closed grammars, each one file of nested records, used qualified and
never imported.

* **`Schema`** is `sbe.xsd`: the `messageSchema` record with a nested
  record per XSD element named after it, `Schema.Type`, `Schema.Composite`,
  `Schema.Ref`, `Schema.Enum`, `Schema.ValidValue`, `Schema.Set`,
  `Schema.Choice`, `Schema.Message`, `Schema.Field`, `Schema.Group`,
  `Schema.Data`; one component per XSD attribute with the XSD's name,
  `value` for element text. An attribute the XSD makes optional is
  `@Nullable`; nothing carries a default, so the model holds what was
  written and `SchemaXml` writes exactly that. Children are `List`s in
  declaration order; the two sealed interfaces, `Declaration` for what
  `types` holds and `Member` for what a composite holds, need no `permits`
  because the file is the closed set. Enumerated attributes use sbe-tool's
  `PrimitiveType` and `Presence`, and `java.nio.ByteOrder`.
* **`Annotated`** is the api's annotations as data: a nested record per
  annotation, one component per member with the member's name, plus what
  an annotation cannot carry: the Java name of the thing annotated, its
  Java type as a small sealed descriptor (a primitive, `String`,
  `byte[]`, an array of another primitive, a `List` of a record, a `Set` of
  an `@SbeSet` enum, a declared
  type, or none for a field no component carries), for an enum or a set
  the qualified name code uses, for a message or a group its `layout` and
  its `unmapped` fields, for a field its `binding` as the class code names
  and the `W` it hands the flyweight, and references to other declarations
  by identity rather than by `Class`. It is the Java face: the
  codec emitter reads it beside the IR, related to `Schema` by name, which
  is unique per message.
* **Both are values.** Neither carries a position. `Discovery` returns its
  `Annotated` together with identity maps from each `Annotated` node to its
  javac `Element` and, where there is one, `AnnotationMirror`; `Mapping`
  returns its `Schema` together with an identity map from each `Schema`
  node to the `Annotated` node it came from. A `Problem(Object node, String
  message, Severity severity)` names a node of either model, or an
  `Element` when only javac can see the mistake, and the processor
  resolves it in at most two lookups; an `ERROR`, which the two-argument
  constructor means, stops generation, a `WARNING` is reported and
  generation goes on. Records' `equals` is content-based and the identity maps ignore
  it, so a test asserts a `Problem` by equality and the processor places it
  by identity.

## Rules

Three layers, in the order a mistake meets them.

* **The type system.** Annotation members are typed as the XSD types them:
  enumerations are enums, type references are classes, required attributes
  are required members. Two-thirds of sbe-tool's parser rules are name
  resolution over a text format and cannot fire from typed Java; javac
  fails the file first.
* **Ours, positioned.** The rules SBE has no model for, and the few of
  sbe-tool's that a user hits often enough to deserve an exact position and
  our wording. Rules only javac can see live in `Discovery` and name the
  `Element`: a schema annotation on something that is not a record, a
  component annotation outside a message or group record, `@SbeEnumValue`
  on anything but an enum constant, `@SbeChoice` outside a set, `@UnknownValue` beside
  `@SbeEnumValue`, twice in one enum or on a set's constant, a `Class`
  member naming a type that carries no declaration annotation, a `List<E>`
  whose `E` is not a record, a `binding` that is neither a `TypeBinding`
  nor one of its primitive specializations, is
  abstract, has no no-arg constructor the schema package can call, is a
  declaration or binds a
  `J` that is not the component's type, a declaration that implements
  `TypeBinding`, and a `binding` on an unmapped field. Rules decidable from one node live in
  `Mapping`: a component whose type maps to nothing (`char`, a class that
  is neither a declared type nor a default mapping), `type` and
  `primitiveType` together,
  `@SbeGroup` not on a `List` of a record, `@SbeData` not on `String` or
  `byte[]`, a field after a group or a group after data, an id outside
  `0..65535`, a name outside the XSD's pattern, a `baselineVersion` above
  the schema's version, a primitive component on a field that can be
  absent, and the one warning, a box on a field that never is
  (`type-mappings.md`, absence), each body judged against its own
  baseline, the schema's or the enclosing group's `sinceVersion` where
  that is higher, a field of an enum whose component is
  not that enum, a field of a set whose component is not a `Set` of that
  enum, `presence = OPTIONAL` on a set field, which has no null value, a
  component that is not the face of a type with a length, `String` for
  `char` and the element's array otherwise, `presence = OPTIONAL` on a
  field of such a type, a constant field with neither a `valueRef` nor a
  constant type, which sbe-tool's IR generator crashes on, a binding whose
  interface is not the face's, the primitive specialization for a
  primitive face and the generic interface for a reference one, or on a
  field of an enum or a set, a composite's member that is not the face of
  its type, a primitive component on an optional member and the box
  warning, a constant member without a value, a `ref` whose component is
  not the face of its target, a field of a composite whose component is
  not the record or that is optional, an
  unmapped field without a `name` or a type, unmapped fields without a
  `layout`, and a `layout` that misses a name, repeats one or names
  nothing, or a name that is both a component's and an unmapped field's.
  Rules that compare nodes
  live in `Generator.validate`: duplicate field ids and names in a message
  or group, duplicate message names and ids, two declarations with one
  wire name, `sinceVersion` above the schema version, `deprecated` below
  `sinceVersion`, and the append-only rule, a node with `sinceVersion = n`
  following every sibling with a lower one. One unit test each, asserting
  the `Problem` and the node it names.
* **sbe-tool, as backstop.** The document is validated against `sbe.xsd`
  from the sbe-tool jar, then parsed with `stopOnError`, `warningsFatal`
  and an `errorPrintStream` we own. Whatever is reported, warning or
  error, lands on the `@SbeSchema` package element with sbe-tool's text
  verbatim, which already names the node (`at <message name="Order">
  <field name="qty">`); the corpus proves our documents raise nothing, so
  anything that appears is a mistake of ours. A rule that turns out to
  matter to users graduates to the layer above; nothing parses sbe-tool's
  strings.

## Generation

* `Generator.generate(schema, annotated, output)` runs steps 3 to 7 and
  returns the `Problem`s. Generation is all or nothing: the flyweights and
  the codecs are generated into Agrona's `StringWriterOutputManager` first,
  the problems of every step collected, and only when there is none does
  each source go through `output`, once per qualified name. `output` is
  Agrona's `DynamicPackageOutputManager`, the interface `JavaGenerator`
  takes: the processor's is over `Filer`, which cannot recreate a file and
  is never touched for a package with a problem; a test's is a second
  `StringWriterOutputManager`. The resource, step 8, is the processor's
  own, written after the sources.
* `JavaGenerator` runs with one fixed configuration equal to `SbeTool`'s
  defaults: `MutableDirectBuffer` and `DirectBuffer`, no group-order
  annotation, no interfaces, no decoding of unknown enum values, no
  types-package override, precedence checks off. No `-A` option and no
  `sbe.*` system property is read. `setPackageName(ir.applicableNamespace())`
  is called before `generate()` (`notes.md`).
* Flyweights go to `<schema package>.sbe`: it is the namespace handed to
  `IrGenerator.generate`, which `applicableNamespace()` prefers, while the
  IR's `packageName` and the document's `package` stay the schema package.
* The schema goes into the jar as `<schema package>/schema.xml`, so a jar
  of records carries its own schema and sbe-tool's other generators produce
  the other side of the wire from it. The IR itself is built in memory in
  every compilation and is what steps 6 and 7 consume; only its file form,
  `.sbeir`, is not written: `IrEncoder` serializes it with Agrona's
  `UnsafeBuffer`, which would make a user's javac need a JVM flag, and
  `-Dsbe.generate.ir=true` on the resource produces it.
* The codec emitter walks the IR the way `JavaGenerator` does, with
  `GenerationUtil.collectFields`, `collectGroups` and `collectVarData`,
  and names flyweight members through `JavaUtil`, so the codec calls what
  was generated, by construction. Each token's Java face comes from
  `Annotated` by name. Fields, then groups, recursing into each body, then
  var-data; decoding is the mirror. `encodedLength` takes its shape from
  the IR and its numbers from the flyweights' constants (`BLOCK_LENGTH`,
  `sbeHeaderSize()`, `sbeBlockLength()`, `ENCODED_LENGTH`), so generated
  code holds no wire number. A construct the emitter does not cover yet
  is a `Problem` naming the message, never a silent skip, wherever the
  walk meets it: the body walk throws out of any depth and the message's
  codec catches it once, so var-data inside a group is refused as var-data
  in a message is.
* Absence, per field, is decided on each side. The encode side follows
  the component: `encodeField` for a primitive, `encodeOptionalField`
  writing the flyweight's `<field>NullValue()` for `null`, and
  `encodeBoxedField` for any other box, which refuses `null` with
  `IllegalArgumentException` because a required field has no wire form for
  it. The decode side follows the wire: `decodeField`,
  `decodeOptionalField` yielding `null` for the null value, whatever the
  field's version, since below the acting version the getter returns it,
  and `decodeAddedField` for a required field appended above the
  baseline, yielding `null` when `decoder.actingVersion()` is below
  `<field>SinceVersion()`, on the version and never on the null value,
  which a required field may hold. The null test is `==` for the integer
  primitives and `Float.compare` or `Double.compare` for the floats,
  whose null value is `NaN`; the float's box is the one name the emitter
  decides from the primitive type itself.
* `@SbeSchema(baselineVersion = n)` is the oldest version the codecs
  still decode: a required field appended at or below it is never absent
  and stays a plain primitive, and `decode` refuses a header below it
  with `IllegalArgumentException`, after the schema and template check,
  because reading on would put the null value of every field appended up
  to the baseline into a primitive component. The baseline is the one
  literal a codec holds; sbe-tool has no constant for it. `decodedLength`
  stays a length and refuses nothing.
* A declared type is mapped by one pair of private static methods per
  codec that uses it, after the codec's own methods, in order of first
  use. For an enum, `encode<Enum>` is a `switch` expression over the
  user's constants to the flyweight's, exhaustive, and throws for the
  `@UnknownValue` constant, which has no wire form; `decode<Enum>` is a
  `switch` over the raw value, `<field>Raw()`, with the literals
  `JavaUtil.generateLiteral` gives the valid values, defaulting to the
  designated constant or an `IllegalArgumentException` naming the enum
  and the value; the flyweight's own enum and its `get` are never on the
  path. For a set, `encode<Set>` clears the set flyweight and sets each
  choice from `Set.contains`, and `decode<Set>` refuses a bit no choice
  names, against a mask written as `1L << bit` per declared choice, and
  adds each choice the wire has to an `EnumSet`. The field shapes wrap
  the pair: an optional enum writes and tests `NULL_VAL`, an added enum
  or set tests the version, and the checked encode shape, `null` refused
  before the call, covers every component that may be null on a required
  field, boxed primitive, enum or set. The only literals in generated
  code are the schema's own declarations: the baseline, the valid values'
  text and the choices' bits.
* A named type of length 1 needs nothing of its own: the field shapes read
  the presence sbe-tool resolved from the type and the null value the type
  declares. A `char` string in an ASCII encoding goes through the
  flyweight's own `String` form, `<field>(String)` and `<field>()`, after
  `ascii`, a private static helper emitted once per codec that needs it,
  which refuses a string longer than `<field>Length()` or holding a char
  above 127, because Agrona writes `?` for those silently and throws its
  own exception over the length; a string in any other encoding is refused
  by name. An array is a pair of private static methods per field,
  `write<Field>` refusing a length other than `<field>Length()` and putting
  each element by index, `read<Field>` allocating that length and getting
  each, and for `uint8` the flyweight's bulk `put<Field>` and `get<Field>`
  over `byte[]`, so nothing is cast. A constant carries no bytes: encode
  compares the component with the flyweight's own constant getter, or for
  an enum with the record's constant the `valueRef` names, and refuses a
  mismatch; decode is the plain read, never the added shape, because a
  constant is never absent; an unmapped constant writes nothing. The field
  shapes wrap all of them: `null` refused on a required field, the version
  tested for an added string or array, since the flyweight answers `""` or
  the null value below it. The enum and set pairs, the array pairs and
  `ascii` follow the codec's own methods in order of first use.
* A field's binding is one private final field per binding class the codec
  uses, `priceBinding` for `Price`, after the flyweight fields in order of
  first use; two classes with one simple name in one codec are a problem
  naming the message. On encode, the value the flyweight is handed is
  `priceBinding.toWire(value.bid())` wherever a shape wrote
  `value.bid()` as the written or compared value, while the `null` tests
  stay on the component; on decode, the read is
  `priceBinding.fromWire(...)` around the getter or the array's
  `read<Field>`, inside the optional and added shapes, so the null value
  and the version are decided before the binding is called, which is how
  absence passes through without it.
* A composite is a pair of private static methods per composite type,
  `write<Composite>(<Record> value, <Composite>Encoder encoder)` and
  `read<Composite>(<Composite>Decoder decoder)`, keyed by wire name beside
  the enum and set pairs and declared after the pairs it nests, which
  register while it is built. Its parameters bear the message's names, so
  every field shape serves a member unchanged over the composite's
  flyweight, with the composite's class where a shape names the message's
  for a static meta method, and a member's array pair carrying the
  composite's name so a field's of the same name stays apart; a nested
  composite or a `ref` goes through its own pair. Inside a composite no
  shape tests the version, since the flyweight does not. A composite field
  encodes as `write<Composite>(value.<field>(), encoder.<field>())` under
  the checked shape and decodes as `read<Composite>(decoder.<field>())`
  under the added shape, and a binding wraps both.
* Every shape takes its flyweight classes from its owner, the message's,
  a composite's or a group's, so a field inside a group is the same
  template over `<Msg>Encoder.<Group>Encoder`, and a group's own baseline,
  the higher of its owner's and its `sinceVersion`, decides which of its
  fields would need the added shape. A group is three private methods
  keyed by its path, the parent's name before its own as an array pair's
  is, `writeLegsAllocations` for `allocations` inside `legs`:
  `write<Group>(List<E> entries, <Group>Encoder encoder)` looping the
  entries with `next()` and the body's encode statements, `List<E>
  read<Group>(<Group>Decoder decoder)` looping `hasNext()` and `next()`
  into an `ArrayList` sized by `count()`, both the codec's own because an
  entry's bound field reaches its binding, and the static `int
  <group>Length(List<E> entries)`, `sbeHeaderSize()` plus the entries
  times `sbeBlockLength()`, plus each entry's nested groups where there
  are any, refusing `null` with `<group> is required`. A group's three
  precede the helpers its body registers, in order of first use after the
  codec's own methods. A group encodes as
  `write<Group>(value.<group>(), encoder.<group>Count(value.<group>().size()))`
  under the checked shape, after the fields, and decodes into a local
  before the constructor call, `List<E> <group> = read<Group>(decoder.<group>())`,
  under the added shape where its `sinceVersion` is above the baseline,
  which tests `decoder.actingVersion()` against
  `<group>DecoderSinceVersion()`, the name sbe-tool gives it, and never
  reads the flyweight's count of zero as an empty list; the
  constructor takes the local where the component is. Groups are
  sequential on the wire, so the locals are read in wire order, while an
  entry's fields are addressed from the entry's start and read in
  component order as a message's are. `encodedLength` adds a
  `<group>Length` term per group to the header and the block, without
  encoding; `decodedLength` wraps the decoder and returns the header plus
  `sbeDecodedLength()` where the message has a group, and stays the
  header's block length where it has none. What the emitter still refuses
  inside a group is a field or a group added above the group's baseline,
  whose shape is a field's but whose proof is a frozen version the example
  does not have, and var-data anywhere.
- A body's wire order is its `layout` when it gives one, declaration order
  otherwise; `Mapping` orders the fields, groups and data before every
  rule that reads the order, so `SchemaXml` and sbe-tool see one document
  whichever way the record was written. A field no component carries is
  written as its null value, `<field>NullValue()`, `NULL_VAL` or `clear()`,
  and never read. The codec's encode statements follow the wire order it
  walks; the constructor call's arguments follow the record's component
  order, which the canonical constructor takes, and fixed-block addressing
  makes the two independent.
* The emitter is templates. Every construct it emits is a Java text block
  with named placeholders, beside the method that fills it and named after
  the construct, filled through `Template`: names and values in, a failure
  for a name left unfilled or a value never used, a multi-line value
  indented to its placeholder's column with its blank lines left blank,
  and a placeholder alone on its line indenting its value's first line
  the same way, so a value may open with a blank line, and filled empty
  taking the line with it, so a block left out leaves no blank line. What
  varies is decided in Java and pasted in; the templates hold no conditionals and no loops, and no code
  fragment is assembled by concatenation. The emitter is the file that
  grows with every increment, and this is what keeps it readable: the
  generated shape is read in the emitter the way it is read in the output.
* `<Msg>Codec` and `<Iface>Codec` go to the schema package: `public final`,
  public no-arg constructor, `@Generated("net.concini.sbebuddy")`, owning one header
  encoder and decoder and the message flyweights, plus one instance of each
  binding as a private final field. Fully qualified names, no imports,
  field code in component order, one line per primitive field and a
  comment naming the field above a block that needs more, real line breaks
  and indentation; the output is read while debugging and is not formatted
  afterwards.
* Nothing else is generated, and one thing is configurable:
  `@SbeSchema(codecs = false)` turns step 7 off for that schema, and the
  processor then does exactly what the first release did. It is a member
  of the annotation, not a processor option, because it belongs beside the
  schema it applies to and contributes nothing to it.
* The processor is triggered by any annotated element in a round and takes
  that element's package as the unit of work, so an incremental build that
  recompiles a record without its `package-info.java` still regenerates
  the package. It generates each package once, in the round that first
  shows it, and never in the `processingOver` round; javac's `Filer` cannot
  recreate a file. A package without `@SbeSchema` declares types for a
  schema elsewhere and produces nothing; an `@SbeMessage` in one is the
  single mistake that names, and so is reported on, the message. A package
  with any `Problem` gets each through `Messager` with its severity, on
  the element it names with the `AnnotationMirror` where there is one;
  one with an error gets no output, whichever step found it, and one with
  warnings alone gets its output whole. It reads `Class`-typed members from the
  `AnnotationMirror`, never through an annotation instance (`notes.md`),
  and the annotations are retained at `CLASS`, so a declared type in a
  library jar still resolves and nothing exists at runtime to reflect
  over. It loads no Agrona buffer class, so a user's javac needs no JVM
  flag; `JavaGenerator` keeps this true, checked by hand with a plain
  javac at the first release (`notes.md`).

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

* `decode` checks `schemaId` and `templateId` and throws
  `IllegalArgumentException` on a mismatch; a family codec throws on a
  template id outside its `permits` set.
* Codecs are stateful instances: one per thread, no static methods, no
  static mutable state, nothing shared. Bindings are stateless.
* One exception is the codec's own, `IllegalArgumentException`, for what
  it is handed and cannot represent: on encode a value with no wire form,
  `null` in a required field or a `null` group, from `encodedLength` and
  `encode` alike, an array of the wrong length, a string that
  does not fit, an enum's unknown-value constant; on decode a header of
  another schema or template, or a wire value the schema does not know
  (`type-mappings.md`, unknown values). Everything else passes through
  unwrapped and is never caught: a buffer too small is Agrona's
  `IndexOutOfBoundsException`, a binding's exception is the binding's, and
  a state generated code cannot reach is an `IllegalStateException`, a bug
  of ours. No checked exceptions, no error codes, no logging anywhere; the
  processor speaks through `Messager`, the runtime through exceptions.
* Enum fields are read raw, `<field>Raw()`, and mapped to the domain
  constant by its `@SbeEnumValue`; the flyweight's generated enum type,
  whose `get` throws on a value it does not know, is not on the codec's
  path, which is why sbe-tool's unknown-enum option stays at its default.
* `Codec<T>` is implemented only by generated code, so it may grow; a new
  member gets a `default` if hand-written implementations ever exist.

## Testing

The corpus does the work at every layer; javac appears only where it must.
Reflection is banned in main code and free in tests.

* **The corpus, in the tests module.** One package per schema under
  `sbe-buddy-tests/src/main/java`, the records as a user would write them,
  compiled by the real build with the processor on
  `annotationProcessorPaths`, so the flyweights and the codecs the tests
  call are the generated classes themselves. Beside each package, in the
  test sources, one `<Name>Test implements SchemaCase`: its
  `description()`, its `oracle()` as a text block, and its `roundTrips()`,
  each a value and the codec that carries it under a description of what
  the value shows. `SchemaCasesTest` finds every case by walking the
  module's test classes, the one classpath scan in the repository, and runs
  what every schema owes as one dynamic test per check, named by its
  description: the `schema.xml` the processor wrote is the oracle, through
  `SchemaXmlAssert`; and every round trip holds the codec contract whole,
  `encodedLength` being what `encode` writes, at an offset and touching
  nothing around it, `decodedLength` and `lastDecodedLength` agreeing with
  it, the decoded value equal to the original by recursive comparison and
  encoding to the same bytes. What only one schema owes, a refusal with its
  message, an older version's bytes, a binding's exception, is a `@Test`
  of the case's own, reaching a byte through the flyweights' own
  `<field>EncodingOffset()` and never a literal offset. The values are the
  edge cases, listed by hand: the null value on an optional field, an
  empty group and an empty set, a full-length string, the bounds of a
  primitive. `XsdCoverageTest` lives here too, over every oracle. Surefire
  reports each check by its phrase, the case's description, "checks", the
  check's.
* **The corpus, in the generator, until every case has moved.** Each case
  still there is one class holding four views of one schema: the Java source as text blocks, `PACKAGE_INFO` and
  `SOURCE`; `annotated()` and `schema()`, built through the `Fixtures` DSL
  so the case reads like the oracle; and the hand-written oracle `XML`. In
  the generator, per case: `Mapping.map(annotated())` equals `schema()` by
  record equality; `SchemaXml.of(schema())` is equivalent to the oracle;
  and the oracle parses through `XmlSchemaParser` with no error and no
  warning, so a wrong oracle cannot agree with a wrong writer. In the
  processor, which takes the corpus from the generator's test jar: the
  source discovers to `annotated()` by record equality, and compiles
  through the real processor to a `schema.xml` equivalent to the oracle.
  One corpus, source to XML. A
  model says what an annotation can say: a String member is absent only
  when empty, so `epoch="unix"` written is `epoch="unix"` emitted, while an
  enum or int member left at the XSD's default, `presence`, `length`,
  `byteOrder`, reaches the model as absent and the XML omits it, the same
  document to any XSD-aware reader and the same IR from sbe-tool. Once
  over the corpus: every element and attribute `sbe.xsd` declares occurs
  in some oracle, except an explicit list of attributes the XSD declares
  and sbe-tool ignores, so completeness is a test, not a claim. One case
  per XSD feature and one per shape worth taking from sbe-tool's own test
  schemas, written fresh and never copied. No javac. A case moves to the
  tests module as `docs/next.md` says, and its twins and its codec view go
  with it: the round trips prove as behaviour what the view proved as
  text, and the real build proves what the twins proved by record
  equality.
* **The rules** are unit tests over `Annotated` or `Schema` inputs: build
  the mistake, assert the `Problem` and the node it names.
* **Equivalence** is XMLUnit's, held in `SchemaXmlAssert` in the
  generator's tests: whitespace and comments ignored; both documents
  parsed with `sbe.xsd` attached, so the XSD's defaults are filled and an
  absent attribute equals its default; `type`, `composite`, `enum`, `set`
  and `message` matched by `name` regardless of order; everything else in
  sequence, because offsets follow declaration order and a moved field is
  a different schema. A test is one line through it, or an XPath probe for
  a single attribute.
* **The codec's view of the corpus.** A case the codec covers holds the
  expected source of each of its codecs by qualified name, as a text block
  or a file in the test resources once it outgrows a screen; the emitter's
  output must equal it exactly, so a change to generated code shows as a
  diff of Java. A case the codec does not cover yet holds none and sets
  `codecs = false` in its source, and the test asserts the emitter names
  the construct it lacks, which is the work list the codec increments
  shrink. No javac. Retires with the case's move.
* **The example, as integration.** Realistic schemas a user would write,
  `com.example.trading` and `com.example.quotes`, which grows a construct
  per increment and carries a group since version 7,
  compiled by the real build with the processor on
  `annotationProcessorPaths`, each with its oracle a file in
  `src/main/sbe`. One test per package asserts the `schema.xml` in the class output
  equivalent to the oracle, through `SchemaXmlAssert` from the generator's
  test jar. A package whose constructs the codec covers keeps codecs on
  and round trips a record through its codec at a non-zero offset, with
  `encodedLength`, `lastDecodedLength` and `decodedLength` equal to the
  bytes written; one that waits for a later increment sets `codecs =
  false` and says which increment. It proves the wiring and shows the
  product; coverage stays in the tests module.
  From the first release on it compiles the flyweights the processor
  generates and one smoke test encodes and decodes through them; what
  sbe-tool generates is not tested, because the corpus proves the document
  it gets. Where our own code writes bytes, `SbeTool` generates reference
  flyweights from the oracle at `generate-test-sources`, test code only,
  into an `xmlref` package under the schema package, and the tests encode
  with ours and decode with the reference, and the reverse. Every past
  schema version stays as a frozen file beside the oracle, `quotes-v0.xml`,
  `quotes-v1.xml` and so on, the oracle as it was with only its leading
  comment saying so and never edited again, each with its own reference
  package, `xmlref.v0`, `xmlref.v1` and so on, so cross-version decoding is tested in both
  directions without old-version records: a message from an older writer
  decodes with its later fields and its later group `null`, an older
  reader consumes a newer message's block whole and stops before a group
  it does not know, which is SBE's limit, and a message below the baseline
  is refused. The reference tests read a group the codec wrote entry by
  entry and write one the codec reads, so the group's bytes are
  sbe-tool's on both sides.
* **The processor** runs javac in memory through the Compiler API, with
  one helper: sources as strings, `-proc:only`, diagnostics and written
  files collected. `-proc:only` compiles nothing the processor writes, so
  a generated codec that would not compile passes here; the tests module
  is where generated code meets javac. The corpus tests above; one negative snippet per rule
  layer, discovery, `Mapping`, `Generator.validate`, sbe-tool and the codec
  emitter, asserting the diagnostic's element and line and that nothing
  was written, which proves placement and the all-or-nothing rule while
  the rules themselves are tested in the generator, and one warning
  snippet asserting the same placement with everything written; and one
  snippet
  resolving a declared type across a package boundary. Resolution from a
  jar is every corpus case, through the api's `MessageHeader`. One test
  runs javac against real directories: a package of two records compiled
  whole, then one record alone with the first compilation's class output
  on the classpath, asserting the same `schema.xml`, flyweights and codecs
  come out and compile, which is the incremental build the package-as-unit
  rule promises.
* Test libraries: JUnit, AssertJ, XMLUnit with its AssertJ module. No
  test-kit module until a second consumer exists.

## Build

* Java 21 is the compiler target, so the library and the processor run on
  every JDK from 21 up. Maven through the wrapper. `./mvnw verify` is the
  gate; `./mvnw spotless:apply` before every commit.
* Maven 4 with `4.1.0` POMs, pinned by the wrapper at `4.0.0-rc-6`: the root
  POM carries `root="true"`, a subproject declares neither its own version
  nor its parent's, and a dependency inside the reactor omits its version.
  Maven installs a `4.0.0` consumer POM beside each artifact, so none of this
  reaches a consumer. The wrapper is generated `only-script`, so `./mvnw`
  execs the distribution's own `bin/mvn`; the classic wrapper parses
  `.mvn/jvm.config` itself and would reject the comment in it.
* javac runs with its defaults: no `-Xlint`, no `-Werror`. Generated code
  is compiled by the same javac and cannot be fixed, and lint categories
  change with each JDK.
* Formatting: Spotless with the Eclipse JDT formatter and a profile that
  keeps hand-written line breaks and puts a closing parenthesis on its own
  line when the arguments wrap; otherwise Eclipse's defaults (tabs, 120
  columns). Spotless also removes unused imports and orders them.
* Nullness: JSpecify annotations, `@NullMarked` on every hand-written main
  package, checked by NullAway at error level. NullAway runs as an Error
  Prone plugin with every other Error Prone check disabled and generated
  sources excluded by path; the compiler-internals exports it needs live
  in `.mvn/jvm.config`. JSpecify is `optional` in the api so users do not
  inherit it. Test code is not checked.
* Test JVMs carry `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED`,
  which Agrona needs in any JVM that loads a buffer class. Users' javac
  needs no flag, because the processor never touches an Agrona buffer.
* Dependencies and plugins are added by the increment that first needs
  them, with their versions in the root pom and nowhere else. The api
  depends on Agrona from the first release, because generated flyweights
  compile against it and the api is all a user depends on. The example
  runs `SbeTool` through the exec plugin's `java` goal, in Maven's own
  JVM with sbe-tool as the plugin's dependency, once per oracle file with
  its namespace as a system property, and the build-helper plugin adds
  the output directory as a test source root; nothing of it reaches the
  example's compile classpath.
* `reference/simple-binary-encoding` is a git submodule at the sbe-tool
  tag the build depends on. It is read to confirm behaviour and never
  copied from or edited.
* CI is one GitHub Actions job running `./mvnw -B -ntp verify` on a
  matrix of JDK 21 and JDK 25, both compiling with `--release 21`: the
  oldest JDK the library runs on and the newest, because javac, annotation
  processing and Error Prone move between compiler versions. `main` takes
  pull requests only.
