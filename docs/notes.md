# Notes

Verified facts about sbe-tool, javac, Agrona and the build that the
implementation relies on, especially the surprising ones. Each entry says how it was
verified. Nothing else goes here: no design discussion, no to-dos, no
alternatives considered.

## sbe-tool 1.40.2

* `JavaGenerator` calls `setPackageName` on its output manager only for the
  types-package override. The caller must call
  `setPackageName(ir.applicableNamespace())` before `generate()`, or every
  file is named `null.<Class>` although its `package` line is right.
  (Spike experiment, 2026-09; re-check when increment 5 relies on it.)
* `JavaGenerator`'s output manager is Agrona's
  `org.agrona.generation.DynamicPackageOutputManager`: `OutputManager`,
  `Writer createOutput(String name)`, plus `setPackageName(String)`.
  Agrona ships `StringWriterOutputManager` implementing it. The seven-
  argument public constructor takes the IR, the two buffer class names,
  the three booleans for group-order annotation, interfaces and unknown
  enum values, and the output manager; it fixes types-package support to
  false and precedence checks to `PrecedenceChecks.newInstance(new
  Context())`, whose `shouldGeneratePrecedenceChecks` defaults to false.
  (`JavaGenerator.java`, `PrecedenceChecks.java` and the Agrona 2.6.1
  jar, read 2026-09-21.)
* `IrGenerator.generate(MessageSchema schema, String namespace)` sets the
  IR's `namespaceName`; `Ir.applicableNamespace()` returns it when set and
  `packageName` otherwise, and `JavaGenerator` names every package from
  `applicableNamespace()`. The one-argument `generate` passes null. This
  is what `sbe.target.namespace` sets in `SbeTool`. (`IrGenerator.java`,
  `Ir.java`, `SbeTool.java`, read 2026-09-21.)
* sbe-tool 1.40.2 builds against Agrona 2.6.1 (`gradle/libs.versions.toml`
  in the submodule, read 2026-09-21).
* `XmlSchemaParser.parse`, `IrGenerator` and `JavaGenerator` over a
  `StringWriterOutputManager` run to completion in a plain JVM with no
  `--add-opens`, so generation loads no Agrona buffer class;
  `getSources()` is keyed by the fully qualified class name. (Spike
  experiment, JDK 21, 2026-09-21.)
* A duplicate `validValue` is a warning, `validValue already exists for
  value: 66`; with `warningsFatal` the parse ends in an
  `IllegalArgumentException` whose message is the first line reported
  without its `WARNING: ` prefix, while the `errorPrintStream` carries
  every line with the prefix. (Spike experiment, 2026-09-21.)
* `JavaGenerator.generate()` opens `MessageHeaderEncoder` and
  `MessageHeaderDecoder` twice, once from `Ir.types()` and once as the
  header stub, with identical content. The generator stages every source in
  a `StringWriterOutputManager`, where the second open replaces the first
  with the same text, and hands each qualified name to the `Filer` once,
  because `Filer` cannot recreate a file. (Spike experiment, 2026-09, and
  `GeneratorTest`.)
* `XmlSchemaParser` with `stopOnError`, `warningsFatal` and
  `suppressOutput` throws `IllegalArgumentException` from a schema warning
  and prints nothing. (Spike experiment, 2026-09.)
* `XmlSchemaParser.parse` never validates against `sbe.xsd`; that is a
  separate `validate(xsdFilename, InputSource, options)`, which wants the
  XSD as a file path and sets no error handler, so the JDK's default prints
  each violation to stderr and carries on. Validate with
  `javax.xml.validation` and `fpl/sbe.xsd` from the jar instead.
  (`XmlSchemaParser.java` and `SbeTool.java`, read 2026-09-20.)
* Every parser error goes through `ErrorHandler.error(String)`, a public
  method of a public non-final class with a public `(PrintStream,
  ParserOptions)` constructor, and `ParserOptions` carries an
  `errorPrintStream`. The node never reaches the handler: `handleError`
  prefixes the message with `at <parent name="P"> <node name="N"> ` from a
  private `formatLocationInfo`. 57 `handleError` sites, about 40 distinct
  rules, in `Message`, `Field`, `CompositeType`, `EncodedDataType`,
  `EnumType` and `XmlSchemaParser`; `MessageSchema.validate` adds
  `sinceVersion` above the schema version. (Read 2026-09-20.)
* `sbe.xsd` declares `messageSchema` and `message` at top level, so both
  are namespace-qualified (`<sbe:message>`) and every other element is
  local and unqualified. The parser matches by local name and accepts an
  unqualified `message` that the XSD rejects, so validating against the
  XSD is a separate check. (Spike experiment, 2026-09-20.)
* `MessageSchema`'s constructors are package-private and `Message`,
  `CompositeType`, `EnumType` and `SetType` are built from DOM nodes, so
  `parse(InputSource)` is the only way to obtain a `MessageSchema`.
  (Read 2026-09-20.)
* `uk.co.real_logic.sbe.ir.GenerationUtil` (`collectFields`,
  `collectGroups`, `collectVarData`) and
  `uk.co.real_logic.sbe.generation.java.JavaUtil` (`formatPropertyName`,
  `formatClassName`, `formatGetterName`) are public; `JavaGenerator`
  derives every flyweight member name through them. (Read 2026-09-20.)
* `IrEncoder` and `IrDecoder` read and write the `.sbeir` file `SbeTool`
  accepts in place of XML and emits under `-Dsbe.generate.ir=true`.
  `IrEncoder` imports `org.agrona.concurrent.UnsafeBuffer`, so writing an
  IR file inside javac needs the Agrona JVM flag. `IrGenerator`, `Ir`,
  `Token` and `Encoding` import only `org.agrona.Verify`; `JavaGenerator`
  refers to the `DirectBuffer` and `MutableDirectBuffer` interfaces by
  class literal, and whether loading those touches `UnsafeApi` is
  unverified. (Read 2026-09-20.)
* The decoder's `wrap(buffer, offset, actingBlockLength, actingVersion)`
  sets `limit = offset + actingBlockLength`, so an older decoder skips
  fields a newer version appended to the block. (`JavaGenerator.java`,
  message `wrap`, read 2026-09-20.)
* Below the acting version, a guarded getter returns the encoding's
  applicable null value for a primitive, `NULL_VAL` for an enum, count zero
  for a group, and wraps var-data with length zero. Encoders have no
  version guard. (`JavaGenerator.java`, `generateFieldNotPresentCondition`
  and the group and var-data guards, read 2026-09-20.)
* Every primitive field has static meta methods on both flyweights, typed
  as the face: `<field>NullValue()`, `<field>MinValue()`,
  `<field>MaxValue()`, `<field>SinceVersion()`, `<field>Id()`,
  `<field>EncodingOffset()` and `<field>EncodingLength()`; the message
  decoder has `actingVersion()`. `JavaUtil.generateLiteral` writes the
  null value of `float` and `double` as `Float.NaN` and `Double.NaN`,
  which `==` never matches and `Float.compare` and `Double.compare` match
  with `0`. (`JavaGenerator.java`, `generatePrimitiveFieldMetaMethod` and
  `generateFieldSinceVersionMethod`, `JavaUtil.java`, `PrimitiveValue.java`,
  read 2026-09-21; the quotes example's
  `anAbsentVwapIsTheNullValueOnTheWire`.)
* A field without a `presence` attribute takes its type's presence
  (`Message.java`, `getPresence`). The IR's encoding token of a field
  carries the presence so resolved and a version of
  `max(field.sinceVersion, type.sinceVersion)`, while the field token's
  version is the field's own, which is what the flyweight's guard reads.
  (`IrGenerator.java`, `add(EncodedDataType, int, Field)` and `add(Field)`,
  read 2026-09-21.)
* An enum's `encodingType` is `char`, `int8`, `uint8`, `int16`, `uint16`
  or `int32`, or a named type of those with length 1; anything else is
  `illegal encodingType for enum`. So an enum's raw value is at most an
  `int` and a Java `switch` over it is legal. A set's is `uint8` to
  `uint64`. (`EnumType.java` and `SetType.java`, read 2026-09-21.)
* The flyweight enum's constants are the valid values' names through
  `JavaUtil.formatForJavaKeyword`, each with `value()` in the face type,
  plus `NULL_VAL` with the encoding's null value and a static `get` that
  throws on anything else. An enum field's decoder has `<field>Raw()` in
  the face type, guarded below the acting version like a primitive, and
  `<field>()` through `get`; its encoder has `<field>(Enum)` writing
  `value()` and no raw form; the meta methods `<field>Id()`,
  `<field>SinceVersion()`, `<field>EncodingOffset()` and
  `<field>EncodingLength()` exist for it as for a primitive.
  (`JavaGenerator.java`, `generateEnumDecoder`, `generateEnumEncoder`,
  `generateEnumValues` and `generateEnumLookupMethod`, read 2026-09-21;
  the quotes example's `aVenueNoConstantNamesDecodesToTheUnknownValue`.)
* A set is its own flyweight pair, `<Set>Decoder` with `getRaw()` in the
  face type, `isEmpty()` and `boolean <choice>()` per choice, and
  `<Set>Encoder` with `clear()`, `setRaw()` and `<choice>(boolean)`; a set
  field's `<field>()` on either message flyweight returns it wrapped at
  the field, and the decoder's returns `null` below the acting version.
  The choice methods are named through `JavaUtil.formatPropertyName`,
  which lowercases the first character, so a choice named `INDICATIVE`
  becomes `iNDICATIVE()`; a wire `name` in Java's own case avoids it.
  (`JavaGenerator.java`, `generateBitSet`, `generateChoiceDecoders`,
  `generateChoiceEncoders` and `generateBitSetProperty`, `JavaUtil.java`,
  read 2026-09-21; the quotes example.)
* `JavaUtil.generateLiteral(primitiveType, text)` renders a value's text
  as a Java literal of the face, `(byte)66` for a `char` `B`, `(short)1`
  for `uint8`, a bare number for `uint16` and `int32`, which is legal as a
  `case` label. A valid value's text reaches the IR as
  `encoding().constValue().toString()`, the number for `char` too.
  (`JavaUtil.java` and `PrimitiveValue.java`, read 2026-09-21; the Enums
  corpus case.)
* The flyweights address a block's fields by offset, `buffer.getInt(offset
  + 24, BYTE_ORDER)` in the generated quotes decoder, so a codec may read
  and write the fields of a block in any order; only groups and var-data
  are sequential. (The generated `QuoteDecoder` and the `Layout` corpus
  case, 2026-09-22.)
* A `char` type without `characterEncoding` is `US-ASCII`. For a `char`
  type of length N the decoder has `String <field>()`, the bytes up to the
  first NUL in the type's charset, `byte <field>(int index)`,
  `get<Field>(byte[] dst, int dstOffset)` copying N bytes, and the static
  `<field>Length()` and `<field>CharacterEncoding()`; below the acting
  version they return `""`, the null value and `0`. The encoder has
  `<field>(String)`, which pads with NUL, takes `null` as empty and throws
  `IndexOutOfBoundsException` over N, and `put<Field>(byte[] src, int
  srcOffset)` copying exactly N. For an ASCII encoding,
  `JavaUtil.isAsciiEncoding`, the string form goes through Agrona's
  `putStringWithoutLengthAscii`; for any other through `String.getBytes`.
  (`EncodedDataType.java` and `JavaGenerator.java`,
  `generatePrimitiveArrayPropertyDecode` and
  `generateCharArrayEncodeMethods`, read 2026-09-22.)
* Agrona's `putStringWithoutLengthAscii(int, String)` writes `?` for a char
  above 127. (`javap -c` of `AbstractMutableDirectBuffer` in the 2.6.1 jar,
  which compares each char with 127 and stores 63 otherwise, 2026-09-22.)
* For an array of any other primitive the decoder has `<face> <field>(int
  index)` and the encoder `<field>(int index, <face> value)`, both bounds
  checked, with the static `<field>Length()`; `uint8` alone adds
  `get<Field>(byte[] dst, int dstOffset, int length)`, copying at most N,
  and `put<Field>(byte[] src, int srcOffset, int length)`, zero-padding and
  throwing `IllegalStateException` over N; lengths 2 to 4 add
  `put<Field>(v0, v1, ...)`. Below the acting version the index getter
  returns the null value and the bulk getter copies nothing.
  (`JavaGenerator.java`, `generatePrimitiveArrayPropertyEncode` and
  `generateByteArrayEncodeMethods`, read 2026-09-22.)
* A constant field's type token has size 0, so it is outside
  `BLOCK_LENGTH`, and `constValue`, the type's own or the `valueRef`'s
  valid value. Both flyweights carry the getter and the encoder no setter:
  `<face> <field>()` returning the literal; for `char` of length 1 a
  `byte`; for `char` longer, `constValue().byteArrayValue(CHAR).length > 1`,
  a `String` beside the index getter, `get<Field>(byte[], int, int)` and
  `<field>Length()`. A constant `char` value longer than one character
  without a `length` takes the value's length. A constant enum field has
  `<field>Raw()` returning `<Enum>.<Value>.value()` and `<field>()`
  returning the flyweight's constant, and no encoder method. The field
  token of a `valueRef` constant carries the reference's text, `Side.Sell`,
  as a `char` value. (`JavaGenerator.java`, `generateConstPropertyMethods`,
  `generateEnumDecoder` and `generateEnumEncoder`, `IrGenerator.java`,
  `addFieldSignal`, and `EncodedDataType.java`, `processConstantChar`, read
  2026-09-22.)
* A field with `presence="constant"` whose type is not constant and that
  has no `valueRef` passes every parser rule and throws
  `IllegalStateException("type is not of constant presence")` from
  `EncodedDataType.constVal()` inside `IrGenerator`. (`IrGenerator.java`,
  `add(EncodedDataType, int, Field)`, read 2026-09-22.)
* A field named `range` draws `name is not valid for Golang: range`, a
  warning, which `warningsFatal` makes an error. (The `Arrays` corpus case,
  2026-09-22.)
* `SbeTool.main` takes schema files as arguments, reads `sbe.output.dir`,
  `sbe.target.namespace`, `sbe.validation.stop.on.error` and
  `sbe.validation.warnings.fatal` from system properties, writes Java
  through `JavaOutputManager`, which creates the package directories, and
  calls `System.exit` only for no arguments or an unknown file extension,
  so it runs inside Maven's JVM. (`SbeTool.java`, read 2026-09-21; the
  example's build.)
* The parser checks `sinceVersion <= messageSchema.version` on messages,
  types and valid values (`MessageSchema.java`) but does not check that
  later-version fields follow earlier ones; offsets are computed in
  declaration order (`Message.computeAndValidateOffsets`). (Read
  2026-09-20.)
* `semanticVersion` is a free string emitted as the `SEMANTIC_VERSION`
  constant; `deprecated` marks the generated member `@Deprecated`.
  (`JavaGenerator.java`, read 2026-09-20.)
* A composite named as a group's `dimensionType` must hold `blockLength` and
  `numInGroup`, and a composite named as a `data` field's type must hold
  `length` and `varData`. Each is warned unless its primitive is what SBE
  expects, `uint8` or `uint16` for the dimensions and `uint8`, `uint16` or
  `uint32` for the length, and a `uint32` length errors unless it carries a
  `maxValue` no greater than `Integer.MAX_VALUE`. The message header needs
  `blockLength`, `templateId`, `schemaId` and `version`, all warned unless
  `uint16`, and ignores any further member.
  (`CompositeType.java`, read 2026-09-20, and the corpus.)
* A `ref` resolves against `/messageSchema/types/*[@name=...]`, so its target
  must be declared at the top level; a ref to a member of another composite
  does not resolve. `ref` carries only `name`, `type`, `offset`,
  `sinceVersion` and `deprecated`, and the parser reads the last two onto the
  copied type. (`CompositeType.java`, read 2026-09-20, and the corpus.)
* `nullValue` on a type whose presence is not `optional` is a warning, which
  `warningsFatal` makes fatal; so is an enum valid value equal to the
  encoding's null value. (`EncodedDataType.java` and `EnumType.java`, read
  2026-09-20, and the corpus.)
* `sbe.xsd` types every `name` attribute as `symbolicName_t`, whose pattern
  is `([A-Z]|[a-z]|_)([0-9]|[A-Z]|[a-z]|_)*`, and a `valueRef` as
  `qualifiedName_t`, two of those joined by a dot. A `field`'s and a
  `message`'s `id` is `xs:unsignedShort`, so 0 to 65535, and `offset` is
  `xs:unsignedInt` with no default, which is why an absent one is not the
  same as `offset="0"`. (`fpl/sbe.xsd` in the sbe-tool jar, read
  2026-09-21.)
* The XSD gives `data` the same attribute groups as `field`, so `presence`,
  `valueRef`, `epoch` and `timeUnit` are declared for it. `parseDataField`
  reads presence, epoch and timeUnit, and `Field.validate` checks a
  `valueRef` resolves to an enum valid value, but nothing downstream uses
  them for a data field. (`Message.java` and `Field.java`, read 2026-09-20,
  and the corpus.)
* The Java face of each primitive, `JavaUtil.javaTypeName`: `char` and
  `int8` are `byte`, `int16` is `short`, `int32` is `int`, `int64` is
  `long`, `uint8` is `short`, `uint16` is `int`, `uint32` and `uint64` are
  `long`, `float` and `double` are themselves. A field's accessor and
  mutator are named by `JavaUtil.formatPropertyName`, the flyweight
  classes by `JavaUtil.formatClassName`. (`JavaUtil.java`, read
  2026-09-21.)
* A message's tokens, `Ir.getMessage(id)`, open with `BEGIN_MESSAGE`; from
  index 1, `GenerationUtil.collectFields`, `collectGroups` and
  `collectVarData` split them into the three lists in that order, each
  returning the index the next starts at. A field's tokens run from
  `BEGIN_FIELD` for `componentTokenCount()` tokens with its type token
  second: `ENCODING` for a primitive, whose `arrayLength()` and
  `encoding().presence()` tell an array and a constant or optional
  apart, or `BEGIN_ENUM`, `BEGIN_SET` or `BEGIN_COMPOSITE`. A field added
  after version 0 has `version()` above 0. The header composite's name is
  `ir.headerStructure().tokens().get(0).name()` and the schema's byte
  order `ir.byteOrder()`. (`GenerationUtil.java`, `Token.java` and `Ir.java`,
  read 2026-09-21.)
* An enum field's decoder has two getters: `<field>Raw()` returns the
  encoding's primitive, and `<field>()` passes it to the generated enum's
  static `get`, which returns `NULL_VAL` for the null value and otherwise
  throws `IllegalArgumentException("Unknown value: " + value)`; only with
  `shouldDecodeUnknownEnumValues` does the enum gain `SBE_UNKNOWN`, whose
  `value()` is the null value, and `get` return it instead. The C++
  generator does the same under the same option; C# and Go cast the raw
  value into their enum types. (`JavaGenerator.java`, `generateEnumLookupMethod`,
  `generateEnumValues` and the enum field getters, and `CppGenerator.java`,
  read 2026-09-21.)
* The generated flyweights: `<Msg>Encoder.BLOCK_LENGTH`, `TEMPLATE_ID` and
  `SCHEMA_ID` are `int` constants, `MessageHeaderEncoder.ENCODED_LENGTH`
  the header's size; `encoder.wrapAndApplyHeader(buffer, offset,
  headerEncoder)` writes the header at the offset and wraps the body after
  it, and `encodedLength()` is the bytes from the body's offset to the
  limit, so header plus body is `ENCODED_LENGTH + encodedLength()`;
  `decoder.wrap(buffer, offset, actingBlockLength, actingVersion)` takes
  the header's `blockLength()` and `version()`. (`JavaGenerator.java`,
  read 2026-09-21, and the quotes example's round trip.)

## javac

* No annotation processor runs after an error: a negative compile test sees
  only the diagnostics of the round that failed. (Spike, 2026-09.)
* `Filer.createSourceFile` throws `FilerException` for a name already
  created in the same compilation. (Spike, 2026-09.)
* `Messager` cannot position an annotation *value* on a record component;
  the diagnostic lands on the annotation. (Spike, 2026-09.)
* A `Class`-typed member read through an annotation instance from
  `Element.getAnnotation` throws `MirroredTypeException` carrying the
  `TypeMirror`; the same member read from the `AnnotationMirror`'s element
  values is a `ClassType`. Discovery reads every `Class` member from the
  mirror. (Spike experiment, javac 21, 2026-09-20.)
* A type loaded from a class file, from a jar or a classes directory,
  exposes its `CLASS`-retained annotations through `getAnnotationMirrors()`
  on the type and on each `RecordComponentElement`, with the components in
  declaration order; `MessageHeader` from the api jar reads as
  `@SbeComposite(name="messageHeader")` over four `@SbeType(primitiveType=
  UINT16)` components. (Spike experiment, javac 21, 2026-09-21.)
* An annotation may carry an array of annotations as a member,
  `SbeField[] unmapped() default {}`; only an annotation type containing
  itself, directly or through another, is refused. In the mirror the
  array is a `List` of `AnnotationValue`s whose values are
  `AnnotationMirror`s, read like the annotation on an element.
  (`Discovery.Members.annotations`, javac 21, 2026-09-22.)
* `AnnotationMirror.getElementValues()` holds only the members written explicitly; `Elements.getElementValuesWithDefaults` fills the rest.
  Discovery reads every member through the latter, so a member left at its
  default is the default's value. (`javax.lang.model` Javadoc, and the
  spike above.)
* `PackageElement.getEnclosedElements()` returns the package's types in the
  order javac entered them: compilation units in the order given, and
  declaration order within a unit; `TypeElement.getEnclosedElements()`
  returns record components in declaration order, and
  `ElementFilter.recordComponentsIn` keeps it. (Spike experiment, javac
  21, 2026-09-21.)
* In an incremental build that order is not source order: the types being
  compiled come first, the package's others follow as javac lists them from
  the class output, in the file system's order. Recompiling `Trade.java`
  alone against classes holding `Quote` gave `Trade, Quote` on macOS and
  `Quote, Trade` on Linux, so Discovery sorts the package's top level
  itself. (`IncrementalCompilationTest`, JDK 25, 2026-09-21.)
* sbe-tool reads `types` with an XPath per kind into a map by name and
  messages into a map by id (`XmlSchemaParser.findTypes`,
  `findMessages`), so the order of the declarations and of the messages in
  the XML carries no meaning. (Read 2026-09-21.)
* `RoundEnvironment.getElementsAnnotatedWith` returns only elements of the
  types being compiled; a type read from a jar is never in a round, however
  many of our annotations it carries. So the packages a round offers are
  the compilation's own, and the api's package never becomes a unit of
  work although `MessageHeader` carries `@SbeComposite`. (Every corpus
  case, which resolves `MessageHeader` from the api jar and compiles
  without a diagnostic.)
* A message flyweight exposes a composite field as a flyweight of its own:
  the decoder's `<field>()` wraps a `<Composite>Decoder` at the field's
  offset and returns it, `null` below the acting version; the encoder's
  `<field>()` wraps a `<Composite>Encoder` and never guards. Inside a
  composite flyweight the members are generated as a message's fields are,
  primitives with their static meta methods, arrays and strings, enums with
  `<member>Raw()`, sets and nested composites, but with no version guard of
  any kind, `inComposite` being true for every one: a member's
  `sinceVersion` changes nothing in the flyweight. A `ref` is a nested
  composite property wrapping the referenced composite's flyweight, whose
  class `applicableTypeName()` names. (`JavaGenerator.java`,
  `generateCompositeProperty`, `generateComposite` and
  `generatePropertyNotPresentCondition`, read 2026-09-22; the `Composites`
  corpus case.)
* `Types.asMemberOf(declaredType, method)` on an interface's method, with
  the class implementing the interface as the type, returns the
  `ExecutableType` with the class's type arguments substituted: for
  `CentsBinding implements TypeBinding<BigDecimal, Long>` and
  `TypeBinding.toWire`, a parameter type of `java.math.BigDecimal` and a
  return type of `java.lang.Long`. `Types.isSubtype` over the erasures
  tells whether the class implements the interface at all.
  (`Discovery.toWire` and `PlacementTest`, javac 21, 2026-09-22.)
* `Messager.printMessage(kind, message, element, mirror)` positions the
  diagnostic on the annotation rather than on the element's own
  declaration; without a mirror, on the element's declaration, which for
  an enum constant with annotations on the lines above it is the first
  annotation's line. (`PlacementTest`, on a message whose `@SbeMessage`
  is written on the line above the record, and on the `@UnknownValue`
  snippets, 2026-09-21.)
* `Filer.createResource(CLASS_OUTPUT, "a.b", "schema.xml", origin)` writes
  `a/b/schema.xml` under the class output directory, so the resource ships
  in the jar beside the package's classes and is on the test classpath of
  the module that declared it. (`javax.annotation.processing` Javadoc, and
  the example's `SchemaResourceTest`.)
* Plain `javac` on JDK 21 with no JVM flag, the api, Agrona and JSpecify on
  the compile classpath and the processor, the generator, the api,
  sbe-tool and Agrona on the processor path compiles the example, writes
  every flyweight under `com.example.trading.sbe` into `-s` and
  `schema.xml` into `-d`; generation inside javac loads no Agrona buffer
  class. (Run by hand against the increment 5 build, 2026-09-21.)
* `Filer.createSourceFile` for a type that exists on the classpath only as
  a class file, from an earlier compilation, succeeds; javac warns only
  under `-Xlint:processing`. And `PackageElement.getEnclosedElements()`
  lists the package's types from the classpath beside the ones in the
  sources being compiled, with their `CLASS`-retained annotations. So
  recompiling one record against the previous build's class output
  rediscovers the whole package and regenerates every file of it, over the
  old classes. (`IncrementalCompilationTest`, javac 21 and 25, 2026-09-21.)
* From JDK 23 javac performs no annotation processing unless `-processor`,
  `--processor-path` or `--processor-module-path` is set, or `-proc` is
  `only` or `full`; discovery from the compile classpath is gone. Reaching
  the processor through `annotationProcessorPaths`, as the example does,
  sets `--processor-path` and is unaffected.
  (`maven-compiler-plugin:4.0.0-beta-5` plugin descriptor, `proc` and
  `annotationProcessorPaths`, read 2026-09-20.)

## JDK 25

* The whole build, `./mvnw verify` with `maven.compiler.release` 21, Error
  Prone 2.50.0 with NullAway 0.14.1, the processor's in-memory javac tests
  and the example's annotation processing, runs green on OpenJDK 25.0.4
  exactly as on 21, with the same `.mvn/jvm.config` exports and the same
  surefire `--add-opens`. (Run by hand on Ubuntu's `openjdk-25-jdk-headless`
  beside the JDK 21 run, 2026-09-21; CI repeats it on Temurin 25.)

## Error Prone 2.50.0

* On JDK 21 Error Prone refuses to run without
  `-XDaddTypeAnnotationsToSymbol=true` and fails the compilation with that
  sentence as the whole message. (Spike experiment, 2026-09-20.)

## Agrona 2.6.1

* `UnsafeApi` in the sources jar is a stub; the shipped bytecode calls
  `jdk.internal.misc.Unsafe` directly, so any JVM that loads an Agrona
  buffer class needs `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED`.
  javac needs it only if the processor touches a buffer. (Sources jar,
  read 2026-09-20.) Confirmed on JDK 21: the generated flyweights' first
  `wrap` of an `UnsafeBuffer` fails with `IllegalAccessError` from
  `UnsafeApi` without the flag and runs with it, so a user's tests and
  runtime carry it while their javac does not. (Spike experiment,
  2026-09-21.)
* `StringWriterOutputManager.getSources()` keys sources `<package>.<Name>`
  with the package current at `createOutput`. Its writer resets the package
  to the first one ever set when it closes: after `setPackageName("p")` on
  a manager first given `p.sbe`, the first codec lands in `p` and the
  second in `p.sbe`. So the package is set before every file, never once
  for a run. (javap of the 2.6.1 jar, and `IncrementalCompilationTest`,
  which caught it, 2026-09-21.)

## Maven 4.0.0-rc-6

* `.mvn/jvm.config` is parsed by `bin/JvmConfigParser.java`, which strips
  everything from a `#` to the end of the line and expands
  `${MAVEN_PROJECTBASEDIR}`. The `only-script` wrapper execs
  `$MAVEN_HOME/bin/mvn`, so `./mvnw` inherits that; the classic wrapper
  joins the file with `tr` and passes the comment to the JVM, which fails
  with `Could not find or load main class #`. (`bin/mvn`,
  `bin/JvmConfigParser.java` and both `mvnw` variants, read 2026-09-20.)
* `bin/mvn` captures that parser's stderr into `MAVEN_OPTS` and `eval`s it,
  so a JVM that prints a banner, as any JDK does when `JAVA_TOOL_OPTIONS` is
  set, breaks the launcher before Maven starts. CI and a plain workstation
  are unaffected; a container that sets it must pass those flags in
  `MAVEN_OPTS` instead. (Spike experiment, 2026-09-20.)

## exec-maven-plugin 3.6.4 and build-helper-maven-plugin 3.6.2

* The exec plugin's `java` goal runs under Maven 4.0.0-rc-6 in the build's
  JVM: `SbeTool` with `includePluginDependencies` and sbe-tool as the
  plugin's dependency, three executions in one phase each with its own
  `sbe.target.namespace` system property, write three reference packages
  under `target/generated-test-sources/sbe`; the build-helper plugin's
  `add-test-source` adds the directory and the compiler plugin compiles
  it with the tests. Neither reaches the example's compile classpath.
  (The example's `verify`, JDK 21, 2026-09-21.)

## Surefire 3.6.0

* The plugin's own descriptor names junit-platform-launcher 1.14.4, but it
  resolves the launcher to the JUnit Platform version it finds on the test
  classpath, 6.1.3 here, and runs that. Declaring the launcher through the
  JUnit BOM takes the alignment out of surefire's hands and puts the version
  in the root POM. (Spike experiment with `-X`, 2026-09-20.)

## Spotless 3.10.2

* `removeUnusedImports` and `importOrder` work on this code. The CleanThat
  step does not: with any mutator configured it throws
  `InvocationTargetException` on `Corpus.java`, which `spotless:apply`
  reports as a lint and otherwise ignores under `sourceJdk` 21, so the
  step silently changes nothing. (Spike experiment, 2026-09-20.)
