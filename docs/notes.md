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
* `IrGenerator` gives a field's tokens, the `BEGIN_FIELD` and the type's
  under it, the later of the field's and its type's `sinceVersion` as their
  version, the field's `deprecated` on the field token and the type's on the
  type token, and the field's `presence` on the type token; a field's
  `semanticType` reaches the token only where its type has none; a group's
  `semanticType` is not read from the document at all. A constant `char`
  type's `length` stays 1 however long its value, which only `constValue`
  holds. (`IrGenerator.java`, `Message.java` and `EncodedDataType.java`,
  read 2026-09-24; each surfaced as a false disagreement when the
  annotations were compared with the document through the IR, which is why
  the comparison is between documents.)
* `PrimitiveValue.toString()` prints a `char` value as its code point, `66`
  for `B`; `PrimitiveValue.parse("B", CHAR)` is the value to compare with,
  and refuses more than one character. (`PrimitiveValue.java`, read
  2026-09-24, and `AnnotationMistakesTest`.)
* Below a field's `sinceVersion` the message decoder's getters return what
  stands for absence in each shape: `null` from a composite's and a set's
  getter, `""` from a `char` array's `String` getter, and the element's null
  value from an array's indexed getter; a scalar's returns its null value. A
  field whose face has no null value is therefore read behind its own
  `<field>SinceVersion()` guard, as a required field appended above the
  baseline is. (Generated flyweights of `corpus.addedfaces`, read 2026-09-25.)
* The XSD's `symbolicName_t`, the type of every `name`, is
  `([A-Z]|[a-z]|_)([0-9]|[A-Z]|[a-z]|_)*`: no name holds a `$`, which is why
  the codec joins the segments of a member's path with it. (`sbe.xsd`, read
  2026-09-25.)
* sbe-tool's group decoders have `sbeSkip()`, which passes over an entry's
  nested groups and var-data, and every var-data member has `skip<Name>()`,
  version-guarded, and a static `<name>HeaderLength()`; group encoders have
  a static `sbeHeaderSize()`. (Generated flyweights of the corpus, read
  2026-09-24.)
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
* A group is a static nested class of its message's flyweight,
  `<Msg>Encoder.<Group>Encoder` and `<Msg>Decoder.<Group>Decoder`, named
  by `JavaUtil.formatClassName` on the group's name, and a nested group's
  class is nested in its parent group's. The encoder's `<group>Count(int)`
  writes the dimensions at the limit and returns the group encoder, whose
  `wrap` refuses a count outside the dimension type's range with its own
  `IllegalArgumentException("count outside allowed range")` and whose
  `next()` opens the next entry, moving the limit by `sbeBlockLength()`.
  The decoder's `<group>()` reads the dimensions and returns the group
  decoder, with `count()`, `hasNext()` and `next()`, which moves the limit
  by the block length read from the wire; below the acting version it
  returns the decoder with count and index zero without touching the
  limit. Both group classes have static `sbeHeaderSize()` and
  `sbeBlockLength()`, the latter the declared `blockLength` where there is
  one; the parent decoder has static `<group>DecoderId()` and
  `<group>DecoderSinceVersion()`, named after the group's decoder class
  rather than the group, since `generateDecoderGroups` hands
  `generateGroupDecoderProperty` the class name where the encoder side
  gets the group's, so the encoder's is `<group>Id()`; the group decoder
  has `actingVersion()`, the message's. Inside a group the fields
  are generated as a message's, addressed from the entry's `offset`,
  which `next()` sets, with their static meta methods on the group class
  and their getters guarded by `parentMessage.actingVersion`, so a
  field's guard inside a group is never reached while the group's own
  guard holds. `count` has static `countMinValue()` and `countMaxValue()`.
  The message decoder's `sbeSkip()` rewinds to the block and walks every
  group, each entry's `sbeSkip()` walking its own, and every var-data;
  `sbeDecodedLength()` calls it and restores the limit, so it needs a
  wrapped decoder and disturbs the limit not at all; it does re-wrap every
  group decoder on its way, so a group decoder mid-iteration reads
  `hasNext()` false afterwards, and a walk that is inside a group must
  measure on a second decoder instance. A group decoder's `index` is a
  private field with no accessor. (`JavaGenerator.java`,
  `generateDecoderGroups`, `generateEncoderGroups`,
  `generateGroupDecoderProperty`, `generateGroupEncoderProperty`,
  `generateGroupDecoderClassHeader`, `generateGroupEncoderClassHeader`,
  `generateFieldNotPresentCondition`, `generateMessageLength` and the
  decoder's `sbeDecodedLength`, read 2026-09-22.)
* A group decoder's `next()` starts the entry at the message's current
  limit and moves the limit past the entry's block, by the block length
  read from the dimensions. Whatever an entry holds after its block, a
  nested group or var-data, moves the limit only when it is read, so a
  reader that does not know a group or var-data appended inside the entry
  starts the next entry where that appended part begins. A field appended
  to the entry's block is stepped over, the dimensions carrying the longer
  block. (`JavaGenerator.java`, the group decoder's `next`, read
  2026-09-23; the `evolution` corpus cases.)
* SBE's own rules for growing a schema: fields appended to the end of a
  message's or a group's block; a group after the existing groups, at the
  root or nested in a group; var-data after the existing var-data, at the
  root or in a group. "It is not possible to add fields to a composite type
  without creating a new message template and schema version", and the
  message header's encoding cannot change. (sbe-tool's wiki, Message
  Versioning, and the FIX SBE 1.0 standard, Schema Extension Mechanism,
  read 2026-09-23.)
* A `data` element's flyweight members sit on its message's or group's
  classes, named by `JavaUtil.formatPropertyName`, the bulk ones after
  `Generators.toUpperFirstChar` of it. The encoder has static
  `<data>HeaderLength()`; `put<Data>(byte[] src, int srcOffset, int
  length)` and the same over a `DirectBuffer`, each throwing
  `IllegalStateException("length > maxValue for type: " + length)` above
  the length type's `applicableMaxValue`; and, where the `varData` type
  has a `characterEncoding`, `<data>(String)`, taking `null` as empty,
  which for an ASCII encoding writes `value.length()` characters through
  Agrona's `putStringWithoutLengthAscii` and otherwise the bytes of
  `String.getBytes` in the charset, checking the same maximum. The decoder
  has static `<data>SinceVersion()` and `<data>HeaderLength()`;
  `<data>Length()`, which reads the length without moving the limit;
  `get<Data>(byte[] dst, int dstOffset, int length)`, which copies at most
  `length` bytes and moves the limit past the whole data; `skip<Data>()`,
  `wrap<Data>`, and with a `characterEncoding` `String <data>()` through
  `new String(bytes, charset)`. The same maximum is the static
  `lengthMaxValue()` of the encoding's own composite flyweight, both
  `Encoding.applicableMaxValue`: 254 for `uint8`, 65534 for `uint16`, the
  `maxValue` for `uint32`. A `char` type always has a `characterEncoding`,
  `US-ASCII` unless given; any other primitive has one only where given.
  `JavaUtil.isAsciiEncoding` and `isUtf8Encoding` recognise the charsets.
  (`JavaGenerator.java`, `generateDecoderVarData`, `generateEncoderVarData`,
  `generateDataDecodeMethods`, `generateDataEncodeMethods`,
  `generateCharArrayEncodeMethods`, `generatePrimitiveFieldMetaMethod`;
  `EncodedDataType.java`; `JavaUtil.java`, read 2026-09-23; the `vardata`
  corpus case.)
* The JDK's `String.getBytes(UTF_8)` writes a lone surrogate as `?`, and
  `new String(bytes, UTF_8)` reads a malformed sequence as U+FFFD, neither
  with an exception. (JDK 21, the `vardata` corpus case, 2026-09-23.)
* sbe-tool's offset rule, `Message.computeAndValidateOffsets`, treats
  every position after a group or var-data as variable length, so an
  `offset` on a `data` element after a group is accepted whatever its
  value. (`Message.java`, read 2026-09-22.)
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
* `sbe.xsd` types `epoch` and `timeUnit` on `field` as free `xs:string`s,
  defaults `unix` and `nanosecond`, and annotates `timeUnit` "Deprecated -
  only for back compatibility with RC2". The parser reads both with
  `getAttributeValueOrNull`, so an absent one is null in the IR, never the
  XSD's default; nothing validates either value; `IrGenerator` copies them
  into the field's encoding, and `JavaGenerator` exposes them only as
  `MetaAttribute` strings. The SBE 1.0 standard's field attributes list
  neither. (`fpl/sbe.xsd`, `Message.java`, `IrGenerator.java`,
  `JavaGenerator.java`, read 2026-09-24; the standard's `04MessageSchema.md`.)
* A name that is a Java keyword, a valid value called `false` or `true`
  among them, makes `JavaUtil.formatForJavaKeyword` throw unless the system
  property `sbe.keyword.append.token` is set, so such a schema generates no
  Java flyweights as sbe-buddy runs sbe-tool. (`JavaUtil.java`, read
  2026-09-24.)
* A `char` array's `String` getter reads up to the first zero byte and
  decodes in the type's encoding, so an encoding that writes a zero byte
  inside a character, UTF-16 or UTF-32, does not come back from a char
  array. Its `String` setter, outside ASCII, and var-data's, in any
  encoding, go through `String.getBytes`, which writes a character the
  encoding cannot hold as `?`. For any encoding sbe-tool also generates
  `put<Name>(byte[], int)` on a char array and `put<Name>(byte[], int, int)`
  on var-data. (`JavaGenerator.java`, read 2026-09-24, and the generated
  flyweights.)
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
* `wrapAndApplyHeader` writes only the header members named
  `blockLength`, `templateId`, `schemaId` and `version`, from the message's
  constants; any other member of a header of the schema's own keeps the
  bytes the buffer held. The header flyweight is named after the header
  composite, `ApplicationHeaderEncoder` for `applicationHeader`, and the
  message flyweight takes it by that class. (`JavaGenerator.java`,
  `generateEncoderFlyweightCode`, read 2026-09-23, and the header corpus
  case's dirty-buffer test.)

* The message decoder's `limit()` and `limit(int)` are public, and every
  group and var-data accessor, a group's included, reads and moves that one
  limit: a group decoder has none of its own. `sbeRewind()` is `wrap(buffer,
  offset, actingBlockLength, actingVersion)` again, the limit back at the end
  of the root block. `wrapAndApplyHeader` throws `IllegalStateException(
  "Invalid TEMPLATE_ID: " + templateId)` for another template. The message
  decoder's `sbeSkip()` calls `sbeRewind()` first, then walks every group and
  var-data. A group accessor, `decoder.fills()`, returns the same group
  decoder object every time, a final field of its parent, after its public
  `wrap(DirectBuffer)`, which reads the dimensions at the limit, moves the
  limit past them and sets the index to 0. `skip<Data>()` moves the limit past
  the length and the data and returns the data's length; `get<Data>(dst,
  dstOffset, length)` over a `byte[]` or a `MutableDirectBuffer` and
  `wrap<Data>(DirectBuffer)` read at the limit. (`JavaGenerator.java`,
  `generateDecoderFlyweightCode`, `generateGroupDecoderClassHeader`,
  `generateDataDecodeMethods` and `generateDecoderGroups`, read 2026-09-26;
  the generated `NewOrderDecoder` of the example.)
* The decoder's accessors of a field, which a stage delegates, by its type
  token: a constant `ENCODING` its literal, a `char` one also indexed and in
  bulk, `get<Field>(byte[], int, int)`, and a `String` where the value is
  longer than one character, else a `byte`; otherwise by
  `Token.matchOnLength`, a length of 1 a scalar getter and more an array's,
  `<field>(int index)`, a `char` array adding `get<Field>(byte[], int)`,
  `String <field>()` and, in ASCII, `get<Field>(Appendable)`, a `uint8`
  array adding `get<Field>` over a `byte[]` and a `MutableDirectBuffer` and
  `wrap<Field>(DirectBuffer)`, and a length of 0 nothing; an enum
  `<field>()` and `<field>Raw()`, constant or not; a set and a composite the
  flyweight named after the type token's `name()`, where an enum's class is
  named after its `applicableTypeName()`. Only messages and types carry
  `@Deprecated`, never an accessor. (`JavaGenerator.java`,
  `generateDecoderFields`, `generatePrimitiveDecoder`,
  `generatePrimitiveArrayPropertyDecode`, `generateConstPropertyMethods`,
  `generateEnumDecoder`, `generateBitSetProperty`, `generateCompositeProperty`
  and `generateDeclaration`; `Token.java`, read 2026-09-26.)
* A group encoder's `wrap(buffer, count)`, which `<group>Count(int)` calls,
  writes the dimensions at the limit with the count given, remembering where
  as its initial limit, and refuses a count outside the dimension's
  `numInGroup` range; `next()` throws `NoSuchElementException` once `count`
  entries are opened; `resetCountToIndex()` sets the count to the entries
  opened so far and writes it over the dimensions at the initial limit. The
  static `countMinValue()` and `countMaxValue()` are the `numInGroup` range,
  typed as its face, which widens to the count's `int` for the `uint8` and
  `uint16` a dimension must be. So a group opened with `countMaxValue()` and settled by
  `resetCountToIndex()` writes the bytes a group opened with its count
  writes. (`JavaGenerator.java`, `generateGroupEncoderClassHeader`, read
  2026-09-26; `WriterAssert` over the corpus.)
* The encoder's setters of a field, which a writer's step takes whole: a
  primitive's `<field>(value)`; a `char` array's `<field>(String)`,
  `<field>(CharSequence)` only in ASCII, `put<Field>(byte[], int)` and the
  indexed `<field>(int, byte)`; an enum's `<field>(Enum)`. Var-data in ASCII
  has `<data>(CharSequence)` beside `<data>(String)`; in another encoding
  only the latter. (`JavaGenerator.java`, both
  `generateCharArrayEncodeMethods` and `generateDataEncodeMethods`, read
  2026-09-26.)
* `Ir.getType(name)` returns a type's tokens by its applicable name: the IR
  captures every composite, enum and set it meets in the messages' tokens,
  those nested in a composite included, keyed by the referenced name for a
  `ref` and by the name otherwise, keeping the lowest version where one name
  is met twice. `JavaGenerator` generates a composite's, an enum's and a
  set's flyweights from these. (`Ir.java`, `captureTypes` and
  `captureType`, read 2026-09-26.)
* `OtfMessageDecoder.decode` walks a message as the readers do:
  `onBeginMessage`, the root block's fields, then each group, `onGroupHeader`
  with its count and per entry `onBeginGroup`, the entry's fields, its own
  groups and var-data and `onEndGroup`, then each var-data, `onVarData` with
  its length. Entries are stepped by the block length read from the wire. A
  group or var-data whose token's version is above the acting version is
  still reported, with a count or a length of 0, where the readers never
  hand out its stage. (`OtfMessageDecoder.java`, read 2026-09-26, and
  `ReaderSequenceTest`.)

## javac

* The use of a deprecated annotation member is a
  `Diagnostic.Kind.MANDATORY_WARNING`, reported only when javac compiles,
  not under `-proc:only`, and once for each place javac copies a record
  component's annotation to. (The processor's snippet test, JDK 21.)
* `TypeMirror.toString()` of a record component's declared type writes a
  type-use annotation where source puts it on a qualified name,
  `java.math.@org.jspecify.annotations.Nullable BigDecimal`, and generics
  in full, `java.util.Map<java.lang.Integer,corpus.bindings.Leg>`: valid
  source, which the bound stages declare as the component's type as
  `Annotated.Other` holds it. (The corpus's `addedfaces` and `bindings`
  readers and writers, compiled by javac 21, 2026-09-26.)
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
  declaration order; `MessageHeader`, since renamed `DefaultMessageHeader`, from the api jar reads as
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
  work although `DefaultMessageHeader` carries `@SbeComposite`. (Every corpus
  case, which resolves `DefaultMessageHeader` from the api jar and compiles
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
* `Filer.getResource(CLASS_PATH, "", "com/example/schema.xml")`, an empty
  package and a slash-separated name, finds a file in a directory or a jar
  on the compile classpath; the `FileObject`'s URI is `file:` for the one
  and `jar:file:…!/com/example/schema.xml` for the other. On `CLASS_PATH`
  and `SOURCE_PATH` a missing file is a `FileNotFoundException` from
  `getResource`; on `CLASS_OUTPUT` and `SOURCE_OUTPUT` `getResource`
  returns a `FileObject` for any name and the read fails with
  `NoSuchFileException`. (Spike processor, javac 21, under Maven 4.0.0-rc-6
  and Gradle 8.14.3, 2026-09-24.)
* `Filer.createResource(CLASS_OUTPUT, …)` over a file that another tool
  put there before javac ran, a copied resource, overwrites it without a
  `FilerException`; the check is against files the `Filer` itself created
  in this compilation. (Spike processor under Maven, 2026-09-24.)
* An `InputSource` with no system id makes the XInclude-aware parser
  resolve `href` against the working directory; with `setSystemId` from
  `FileObject.toUri()` it resolves a relative `href` next to the document,
  in a directory and inside a jar alike. (Spike against
  `XmlSchemaParser.parse(InputSource, ParserOptions)`, 2026-09-24.)
* A class may not implement an interface nested in itself: `class W
  implements W.Stage` is "cyclic inheritance involving W", with the
  class's other members then failing to resolve. A typestate whose stage
  interfaces are nested in the writer is implemented by inner classes of
  it, never by the writer. (javac 21, the hand-written `NewOrderWriter`
  of `sbe-buddy-example`, 2026-09-26.)

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

* `src/main/resources` is copied to `target/classes` before javac runs,
  and `target/classes` is on the compile classpath, so a resource of the
  module is visible to a processor on `CLASS_OUTPUT` and on `CLASS_PATH`;
  `-sourcepath` is passed, so a file beside the sources in `src/main/java`
  is visible on `SOURCE_PATH`. A change to a resource alone copies the
  resource and compiles nothing, "Nothing to compile - all classes are up
  to date"; `fileExtensions` with `xml` changes nothing, because it watches
  dependencies, not the module's own output. (Spike processor,
  `maven-compiler-plugin` 3.14.0, 2026-09-24.)

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
* Install and deploy write two POMs per module: the POM as written, model
  4.1.0 with `root` and a parent without a version, attached under the
  classifier `build`, and a consumer POM as the main one, generated at
  install or deploy time into `target/consumer-*.pom`, at model 4.0.0.
  The consumer POM keeps the parent reference and leaves `${…}` versions
  for the parent to resolve, unless `maven.consumer.pom.flatten` is `true`:
  then the parent's content is inlined, every version resolved, test
  dependencies dropped and the parent reference gone. The switch is read
  from user properties only; as a property in the root POM, even in a
  profile, it changes nothing, and in `.mvn/maven.config` it applies.
  `name` is not inherited, so a module without one has none in the
  flattened POM. (Deploys into a file repository, 2026-09-25.)
* A deploy into a `file:` repository, `altDeploymentRepository` of
  `maven-deploy-plugin` 3.2.0, writes `.md5` and `.sha1` beside every file
  and the consumer POM, not the build POM, as the main one. A project
  resolving only from that directory, with an empty local repository,
  compiles against the flattened api and runs the processor.
  (`.github/central-publish.sh` dry run and a scratch consumer project,
  2026-09-25.)

## Gradle 8.14.3

* A module's own resources are visible to a processor on no location:
  `processResources` writes to `build/resources/main`, which is neither the
  class output nor on `compileJava`'s classpath, and `compileJava` passes
  an empty `-sourcepath`. `compileJava { classpath +=
  files(sourceSets.main.resources.srcDirs) }` puts the source directory on
  `CLASS_PATH`, where the resource is then found; a dependency's jar is on
  `CLASS_PATH` as it is. (Spike processor, 2026-09-24.)
* Compile-classpath normalization ignores anything but class files, so a
  resource on the classpath is not an input of `compileJava`: a change to
  it alone leaves the task `UP-TO-DATE`. `compileJava {
  inputs.files(fileTree('src/main/resources') { include '**/*.xml' }) }`
  makes it one, and the task re-runs on the change and is `UP-TO-DATE`
  otherwise. (Spike processor, 2026-09-24.)
* A resource the processor writes into the class output at a path the
  module's resources also hold fails `jar`: "Entry com/example/schema.xml
  is a duplicate but no duplicate handling strategy has been set". (Spike
  processor, 2026-09-24.)

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
