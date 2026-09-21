# Increment 8: the codec, enums and sets

## Goal

A field of an `@SbeEnum` decodes to the user's own enum constant and a field
of an `@SbeSet` to a `Set` of the user's enum, and back, under the
unknown-value contract of `type-mappings.md`: the wire value is read raw and
mapped by `@SbeEnumValue`, never through the flyweight's generated enum, so
a value the schema does not know is the codec's `IllegalArgumentException`,
or the constant an enum designates with `@UnknownValue`. Absence works for
both through the shapes increment 7 settled: an enum is `null` for the null
value or below the acting version, a set is `null` below the acting version
and has no null value. The mapping between a declared type and its wire
form lives in the codec as one pair of private methods per type, so the
message code stays one line per field.

## Settled before it started

* sbe-tool takes `char`, `int8`, `uint8`, `int16`, `uint16` and `int32` as
  an enum's encoding, and a named type of those, so the raw value is at
  most an `int` and a Java `switch` over it is always legal; a set's
  encoding is `uint8` to `uint64`. The flyweight enum's constants are the
  valid values' names through `JavaUtil.formatForJavaKeyword`, each with
  `value()`, plus `NULL_VAL`; the decoder's `<field>Raw()` returns the
  primitive and is guarded below the acting version like a primitive
  field, the encoder's `<field>(Enum)` takes the flyweight enum and has no
  raw form. A set field's `<field>()` on either flyweight returns the
  set's own flyweight wrapped at the field, `null` below the acting
  version on the decoder; the set decoder has `getRaw()` in the face type
  and `boolean <choice>()` per choice, the set encoder `clear()`,
  `setRaw()` and `<choice>(boolean)`. `JavaUtil.generateLiteral` renders
  a valid value's text as a Java literal of the encoding's face. All in
  `notes.md`.

## What gets built

* **`@UnknownValue` in the api**, on one constant of an `@SbeEnum` in place
  of `@SbeEnumValue`: the Java side, contributing no `validValue`.
  `Annotated.Enum` carries the constant's Java name, or null; `Discovery`
  reads it, and refuses a constant carrying both annotations, a second
  `@UnknownValue` in one enum, and one on a constant of an `@SbeSet`, each
  on the constant. `Mapping` and the schema never see it.
* **The set's face, `Set<E>`.** `Annotated.JavaType` gains `SetOf`, a
  `java.util.Set` of an `@SbeSet` enum; `Discovery` reads `Set<E>` to it
  when `E` carries `@SbeSet`, and to `Other` otherwise; `Mapping` maps a
  bare `SetOf` component to its set as it maps a bare enum, and `Fixtures`
  gains `setOf`. The `Sets` corpus case writes `Set<Permissions>` and
  `Set<Handling>` in its source and twin, which changes no oracle.
* **The face rules for declared types, in `Mapping`.** A field whose wire
  type is an `@SbeEnum`, by `type` or by its component, must have that
  enum as its component's type; one whose wire type is an `@SbeSet` must
  have a `Set` of that enum: the bare set enum is a `Problem`,
  `Permissions is a set; use Set<Permissions>`, and a `Set` of anything
  else, or the wrong enum, is one too. `presence = OPTIONAL` on a set
  field is a `Problem`, `a set has no null value`, because sbe-tool writes
  nothing for absence there and an empty set is a value. The boxing rule
  does not apply: a reference holds `null` as it is, and a required enum
  or set field refuses `null` on encode through the shape a boxed
  primitive already takes. One test each, in both directions where there
  are two.
* **The codec emitter, declared types.** The refusals for an enum and a
  set go; each message's codec gains, after its last method, one pair of
  private static methods per enum and per set the message uses, in order
  of first use, from templates named after them:
  * `encodeEnum`, `encode<Enum>(<enum> value)` returning the flyweight
    enum: a `switch` expression over the user's constants, `enumToWire`
    per valid value naming the flyweight's constant, and `unknownToWire`
    for the `@UnknownValue` constant, which throws
    `IllegalArgumentException("<Enum>.<constant> has no wire form")`;
  * `decodeEnum`, `decode<Enum>(<face> raw)` returning the user's enum: a
    `switch` over the raw value, `wireToEnum` per valid value with the
    literal `JavaUtil.generateLiteral` gives for its text, and the default
    from `wireToUnknown`, the designated constant, or `wireToNothing`,
    which throws `IllegalArgumentException("<Enum> has no value " + raw)`;
    the null value on a required field is such a value;
  * `encodeSet`, `encode<Set>(Set<E> value, <Set>Encoder wire)`, which
    clears the wire and sets each choice from `value.contains`, one line
    per choice from `encodeChoice`;
  * `decodeSet`, `decode<Set>(<Set>Decoder wire)` returning a `Set<E>`,
    which first refuses a bit no choice names,
    `IllegalArgumentException("<Set> has a bit no choice names: " +
    wire.getRaw())`, against a mask of the declared bits written as one
    `1L << <bit>` per choice from `knownBit`, and then adds each choice
    the wire has to an `EnumSet`, one `if` per choice from `decodeChoice`.

  A field of an enum then takes the shapes of increment 7 with the pair
  in place of the flyweight call: `encodeField` becomes
  `encoder.<field>(encode<Enum>(value.<field>()))` and the optional shape
  writes `<Enum>.NULL_VAL` for `null`; `decodeField` becomes
  `decode<Enum>(decoder.<field>Raw())`, the optional shape tests
  `decoder.<field>Raw()` against `<Enum>.NULL_VAL.value()`, and the added
  shape tests the version as before. A field of a set encodes through
  `encode<Set>(value.<field>(), encoder.<field>())` and decodes through
  `decode<Set>(decoder.<field>())`, required or added, never optional.
  The checked encode shape, `null` refused before the call, now covers
  every reference component of a required field, boxed primitive, enum or
  set, and is renamed `encodeCheckedField`. Every flyweight name is still
  `JavaUtil`'s, including the enum constants through
  `formatForJavaKeyword`; the choice bits and the valid values' literals
  are the schema's own declarations pasted in, as the baseline is, and
  the mask is written as shifts of them, so no computed number appears.
  The pair's methods are named after the type's wire name through
  `formatClassName`, which is unique in a schema.
* **The corpus.** `Enums` and `Sets` turn codecs on and carry their
  codecs; `OrderStatus` in `Enums` gains an `@UnknownValue` constant, so
  the codec view shows both defaults side by side, and `Side` keeps
  none. `Versions`, which carries an enum and a set among everything
  else, stays refused for its group and var-data. Nothing else moves.
* **The example.** `com.example.quotes` goes to `version = 3`, appending
  what a feed says about where a quote comes from: `Venue venue`, an
  `@SbeEnum` over `uint8` with an `@UnknownValue` constant, because
  venues are added faster than readers update; `MarketState state`, an
  `@SbeEnum` over `char` without one, because a state the reader does
  not know is not a quote it can use; and `Set<QuoteFlag> flags`, an
  `@SbeSet` over `uint8`. The oracle grows, `quotes-v2.xml` is frozen
  with its reference package `xmlref.v2`, and the tests gain:
  * a round trip with every field, and one with empty flags;
  * the reference decoder reading the venue and state raw and each flag
    bit from what the codec wrote, and the codec decoding what the
    reference encoder wrote;
  * a version 2 message decoding with `venue`, `state` and `flags` `null`;
  * a raw venue no constant names, written through the reference
    encoder's flyweight enum in a later-versioned twin or straight into
    the buffer at `venueEncodingOffset()`, decoding to the unknown
    constant, and encoding that constant refused;
  * a raw state no constant names refused with the exception naming the
    enum and the value, and a flags bit no choice names, written through
    the reference set encoder's `setRaw`, refused naming the set.
* **The documents.** `architecture.md`'s generation section describes the
  pairs and the shapes over them, and its rules section the face rules
  for declared types; `type-mappings.md` names `@UnknownValue` beside the
  annotations it is a Java-side peer of, and says a set field cannot be
  optional; `notes.md` takes the facts above; `intent.md` ticks increment
  8; the README says enums and sets are covered.

## Criteria

* `Enums`' and `Sets`' emitted codecs equal their `codecs` view exactly;
  every other refused case still names its construct, and none names an
  enum or a set.
* The quotes example round trips its enums and set, agrees with the
  reference flyweights in both directions, decodes a version 2 message,
  maps an unknown venue to its constant and refuses to encode it, and
  refuses an unknown state and an unknown flag bit, all in the real build.
* The face rules and the optional-set rule each have a test that builds
  the mistake and asserts the `Problem` and the node; the `@UnknownValue`
  rules have one placement snippet each in the processor.
* Every new template is a text block filled through `Template`; the
  emitter reads the valid values, choices and the unknown constant from
  the IR and `Annotated` and decides nothing else; the only literals in
  generated code are the schema's own declarations.
* `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Named types, constants and `valueRef`, arrays and `char` strings, bindings,
composites and the enums and sets declared inline in them, groups,
var-data, byte order, header types: each keeps its refusal and its
increment. `sinceVersion` on a valid value or a choice changes nothing in a
codec: a newer writer's value is an unknown value, which is the contract.
Reference flyweights for the trading schema. Publishing.
