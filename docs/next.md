# Increment 10: Codec: named types, constants, fixed-length arrays and `char` strings

## Goal

The codec covers every field of the fixed block that is not a composite. A
field of a named `@SbeType` reaches the record as the type's face: a scalar
with the presence and null value the type declares, a `char` string as a
`String`, a fixed-length array as the array of its element's face, and a
constant, which carries no bytes, as a component the schema fills. After it,
the corpus cases `NamedTypes`, `Constants` and `Arrays` have codecs, the
quotes example carries a symbol, a constant price exponent and a depth
ladder, and the only construct of the block the emitter still refuses is a
composite, increment 12.

## Settled before it started

- sbe-tool's flyweights for a `char` type of `length` N: the decoder has
  `String <field>()`, the bytes up to the first NUL in the type's charset,
  `byte <field>(int index)`, `get<Field>(byte[] dst, int dstOffset)` copying
  N bytes, and the static `<field>Length()` and `<field>CharacterEncoding()`;
  below the acting version they return `""`, the null value and `0`. The
  encoder has `<field>(String)`, which pads with NUL, takes `null` as empty
  and throws `IndexOutOfBoundsException` over N, and `put<Field>(byte[] src,
  int srcOffset)` copying exactly N. For an ASCII encoding the string form
  goes through Agrona's `putStringWithoutLengthAscii`, which writes `?` for
  a char above 127; for any other it goes through `String.getBytes`. A
  `char` type without `characterEncoding` is `US-ASCII`.
- For an array of any other primitive: `<face> <field>(int index)` on the
  decoder, `<field>(int index, <face> value)` on the encoder, both bounds
  checked, and the static `<field>Length()`; `uint8` alone adds
  `get<Field>(byte[] dst, int dstOffset, int length)` and `put<Field>(byte[]
  src, int srcOffset, int length)`, which zero-pads and throws
  `IllegalStateException` over N. Below the acting version the index getter
  returns the null value and the bulk getter copies nothing.
- A constant field's type token has size 0 and `constValue`, the type's own
  or the `valueRef`'s valid value; both flyweights carry the getter and the
  encoder no setter: `<face> <field>()` returning the literal, for `char`
  of length 1 a `byte`, for `char` longer a `String` beside the index
  getter and `<field>Length()`. A constant `char` value longer than one
  character without a `length` takes the value's length. A constant enum
  field has `<field>Raw()` returning `<Enum>.<Value>.value()` and
  `<field>()` returning the flyweight's constant. The field token of a
  `valueRef` constant carries the reference's text as a `char` value, and
  a constant field whose type is not constant and has no `valueRef` throws
  `IllegalStateException` inside `IrGenerator`, past every parser rule.
- All of it in `JavaGenerator`, `IrGenerator` and `EncodedDataType`, read
  2026-09-22, and Agrona 2.6.1's `AbstractMutableDirectBuffer` by `javap`;
  each goes into `notes.md`.

## What gets built

- **`Annotated`.** A Java type `Array(JavaPrimitive kind)` for `short[]`,
  `int[]`, `long[]`, `float[]` and `double[]`, and for `char[]` and
  `boolean[]`, which the face rule refuses; `byte[]` stays `Bytes`, which
  data and byte arrays share. `Discovery` classifies an array of a
  primitive so; the corpus DSL gains `array(kind)`.
- **The rules, in `Mapping`.** The face of a named type with `length`
  above 1, or of a constant `char` type whose `value` is longer than one
  character: `char` is `String`; `int8` and `uint8` are `byte[]`, the bytes
  as sbe-tool's bulk accessors have them; every other primitive is the
  array of its element's face, `short[]` for `int16`, `int[]` for `int32`
  and `uint16`, `long[]` for `int64`, `uint32` and `uint64`, `float[]` and
  `double[]`: `String is not the face of Rgb, which is byte[]`. A field of
  such a type cannot be optional, because sbe-tool gives an array no null
  value and its flyweight reads none: `Symbol has a length; a field of it
  cannot be optional`. A field with `presence = CONSTANT` names a
  `valueRef` or a constant type: `a constant field needs a valueRef or a
  constant type`, the mistake sbe-tool crashes on. A constant field is
  never absent, whatever its `sinceVersion`, which `canBeAbsent` already
  says; a box on it is the usual warning.
- **The codec emitter.**
  - A named type of `length` 1 needs nothing new: the field shapes read
    the presence sbe-tool resolved from the type and the null value the
    type declares, through `<field>NullValue()`.
  - A `char` string in an ASCII encoding encodes as
    `encoder.<field>(ascii(value.<field>(), <Msg>Encoder.<field>Length(),
    "<field>"))`, where `ascii` is a private static helper emitted once per
    codec that needs it: `IllegalArgumentException` for a string longer
    than the field or holding a char above 127, which the flyweight would
    silently write as `?`; it decodes as `decoder.<field>()`. The checked
    shape refuses `null`, and the added shape tests the version, because
    the flyweight answers `""` below it. A string in any other encoding is
    refused by name, `no codec for a string in UTF-8 yet`, until a schema
    needs one.
  - An array is a pair of private static methods per field, after the
    codec's own: `write<Field>(<face>[] value, <Msg>Encoder encoder)`
    refuses a length other than `<field>Length()` with
    `IllegalArgumentException` and puts each element by index, and
    `read<Field>(<Msg>Decoder decoder)` allocates `<field>Length()` and
    gets each; a `uint8` array goes through the bulk pair instead, so the
    generated code holds no cast. The field shapes wrap the pair as they
    wrap an enum's: `null` refused, the version tested for an added array,
    never optional.
  - A constant is checked on encode and read on decode. Encode compares
    the component with the flyweight's own getter, `value.<field>() !=
    encoder.<field>()` for a primitive, `equals` for a `String`, and for an
    enum with the record's constant, `value.<field>() !=
    <JavaEnum>.<Constant>`, the constant the `valueRef` names; a mismatch
    is `IllegalArgumentException("<field> is the constant " + ...)`, and
    the checked shape wraps it where the component can be `null`. Decode
    is the plain read, `decoder.<field>()` or
    `decode<Enum>(decoder.<field>Raw())`, and never the added shape. An
    unmapped constant writes nothing.
  - The `{declaredTypes}` placeholder becomes `{helpers}`: the enum and
    set pairs, the array pairs and `ascii`, in order of first use.
- **The corpus.** `NamedTypes`, `Constants` and `Arrays` turn codecs on
  and gain their `CODEC` views, leaving `casesTheCodecLacks`: the symbol
  through `ascii`, the optional quantity through its type's null value,
  the currency compared by `equals`, the buy side by `==`, the side
  against `Side.Sell`, the colour through the bulk pair and the samples by
  index. `Arrays` gains an `int16` field, `short[]`, so an element face
  that is not its own array type is proved. One emitter test builds a
  `char` type in `UTF-8` and asserts the refusal names it.
- **The example.** `com.example.quotes` goes to `version = 5` and gains
  three fields: `symbol`, a `Symbol` type of eight `US-ASCII` characters,
  `@Nullable String` since it is appended above the baseline;
  `priceExponent`, a constant `int8` type holding `-4`, a plain `byte` that
  a message of any version decodes to; and `bidDepth`, a `Depth` type of
  five `uint32`, `long @Nullable []`. `quotes-v4.xml` is frozen with
  `xmlref.v4`. The tests: a round trip with all three; the reference
  decoder reading the symbol NUL-padded, the exponent as the constant and
  the depth by index; a symbol of nine characters, a symbol with a char
  above 127, a depth of four and an exponent other than `-4` each refused
  with the codec's exception; a version 4 message decoding with the
  constant exponent and `null` for the symbol and the depth; a version 4
  reader reading a current message whole.
- **The guide.** A reference page, `docs/guide/reference/named-types.md`:
  `@SbeType` on a class, a field of it, the presence and null value it
  gives its fields, `char` strings, arrays, constants, and what the codec
  refuses; the index links it, and the primitives page's coverage section
  points to it instead of promising it.
- **The documents.** `type-mappings.md`'s faces table takes the `uint8`
  array row and the constant `char` length; `architecture.md`'s rules
  section takes the three rules and its generation section the shapes and
  the helpers; `notes.md` takes the facts above; `intent.md` ticks 10.

## Criteria

- The three cases' emitted codecs equal their `codecs` views exactly, and
  every other case's view is untouched.
- The quotes example round trips with a symbol, the constant exponent and
  a depth, refuses what the contract says it refuses, and decodes a
  version 4 message, all in the real build against the reference
  flyweights.
- Each rule above has a test that builds the mistake and asserts the
  `Problem` and the node.
- The guide's reference page compiles as written: its snippets are the
  example's.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Composites and `@SbeType` on a composite's component, increment 12. A
`char` string in an encoding that is not ASCII, refused by name until a
schema needs it. Bindings over any of these faces, increment 11. Range
checks against `minValue` and `maxValue`, which the guide already says the
codec never adds. A string face other than `String`, or an array face
other than the array, which the flyweights serve.
