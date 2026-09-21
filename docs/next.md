# Increment 7: the codec, absence

## Goal

A field that may be missing decodes to `null` and encodes from it. Two
constructs make a field absent, `presence = OPTIONAL` and a `sinceVersion`
above the acting version of the message being read, and both land here,
because they share one mechanism: a boxed component, the null value on the
wire, and the acting version taken from the header. A schema also says
which versions it still reads, `baselineVersion`, so a field appended long
ago goes back to being a plain primitive instead of a box forever. With
these the codec reads its first message from an older writer, so the
example gains what `architecture.md` has promised since increment 5:
reference flyweights that sbe-tool generates from the oracle, the first
frozen schema versions, and cross-version decoding tested in both
directions. Primitives still; every other construct keeps its refusal.

## Settled before it started

- Below the acting version, a flyweight's getter returns the encoding's
  null value; the decoder's `actingVersion()` is what the guard reads, and
  every field has a static `<field>SinceVersion()` and `<field>NullValue()`
  on its flyweight, so the codec names both without holding a number.
  Encoders have no version guard and `wrapAndApplyHeader` always writes
  `SCHEMA_VERSION`. The null value of `float` and `double` is `NaN`, which
  `==` never matches. `SbeTool.main` takes schema files as arguments and
  `sbe.output.dir`, `sbe.target.namespace` and the validation flags as
  system properties, and exits the JVM only on a usage error. All in
  `notes.md`.

## What gets built

- **`@SbeSchema(baselineVersion = n)`**, default `0`: the oldest version
  the schema's codecs still decode. It is the Java side, like `codecs`,
  contributes nothing to the schema and is read by nothing but the
  boxing rule and the codec; `Annotated` carries it and the processor
  reads it. `Mapping` refuses one above `version`, blamed on the schema.
  Raising it is how a schema retires its oldest readers and writers: the
  fields appended up to the baseline lose their box, and the compiler
  lists exactly which, because a box on a field that can no longer be
  absent is a problem.
- **The boxing rule, in `Mapping`.** A field can be absent when its
  `presence` is `OPTIONAL`, or its `sinceVersion` is above the
  `baselineVersion` and its `presence` is not `CONSTANT`. A component
  whose Java type is a primitive must then be its box: `int` on a field
  that can be absent is a `Problem`, `use Integer`, because `null` has
  nowhere to go. The other way round is a warning, not an error:
  `Integer` on a field that is never absent is a `Problem` of severity
  `WARNING`, `Integer is boxed although the field is never absent`,
  because a box may be there for reasons of the user's own; the codec
  never hands it `null`, and refuses `null` from it on encode like any
  required field. The rule is about the primitive faces only: a component
  of a reference type holds `null` as it is, so an enum, from increment 8
  on, needs no rule and takes the same optional and added shapes with a
  null test of its own, the encoding's null value that sbe-tool's enums
  carry as `NULL_VAL`. Decidable from one node once `Mapping` holds the
  baseline, blamed on the field, tested in both directions and at the
  baseline itself like the other rules. The face rule stays as it is and
  looks through the box, so `Long` on an optional `uint16` still says
  `long is not the face of uint16, which is int`. `type-mappings.md` says
  the same in its absence section, and that a constant is never absent.
- **`Problem` gains a severity**, `ERROR` and `WARNING`, and the
  two-argument constructor means `ERROR`, so nothing that exists changes;
  this is the first warning and there is no other machinery for it. The
  processor reports each `Problem` through `Messager` with its kind, on
  the element it names as before, and generation is all or nothing over
  the errors alone: a package whose only problems are warnings gets its
  output whole. In the generator `Mapping.Mapped.problems()` carries
  both, and the corpus stays clean of either.
- **The codec emitter, absence.** The refusals for an optional field and
  for a field added in a later version go. Each side of a field is
  decided in Java, the encode side from the component's Java type in
  `Annotated` and the type token's presence, the decode side from the type
  token's presence and the field token's version against the baseline,
  and each choice is a template beside the plain one:
  - encoding: `encodeField`, the plain call, for a primitive component;
    `encodeOptionalField` for an optional field, writing the flyweight's
    `<field>NullValue()` for `null` and the value otherwise, one line;
    `encodeBoxedField` for a boxed component of a required field, whether
    added above the baseline or merely boxed, throwing
    `IllegalArgumentException("<component> is required")` for `null`
    before the plain call, the one block a primitive field needs;
  - decoding: `decodeField`, the plain call, for a field that is never
    absent, which the constructor boxes by itself where the component is;
    `decodeOptionalField` for an optional field, whatever its version,
    yielding `null` when the wire holds the null value and the value
    otherwise, one expression in the constructor call; `decodeAddedField`
    for a required field with `sinceVersion` above the baseline, yielding
    `null` when `decoder.actingVersion()` is below `<field>SinceVersion()`
    and the value otherwise, on the version and never on the null value,
    which a required field may legitimately hold.

  The null test is its own template, `isNull`, `==` against
  `<field>NullValue()` for the integer primitives and `char`, and
  `isNullFloating`, `Float.compare` or `Double.compare` against it `== 0`,
  because `NaN` is the null value of the floats and equals nothing. Below
  the acting version the getter returns the null value (`notes.md`), so an
  optional field added later needs no version test.

  **The baseline in `decode`.** After the schema and template check, a
  header whose `version` is below the baseline is an
  `IllegalArgumentException` naming the message, the version and the
  baseline, from a template `refuseBelowBaseline` that the emitter pastes
  only when the baseline is above 0. Reading on would put the null value
  of every field appended up to the baseline into a primitive component,
  a silent wrong number, which is the one thing the codec never does; a
  schema that still needs those messages keeps its baseline where they
  are. The baseline is the one literal a codec holds: it is the schema's
  own declaration, and sbe-tool has no constant for it. `decodedLength`
  stays a length and refuses nothing.

  **`Template`** gains one rule: a placeholder that stands alone on its
  line, filled with an empty value, takes the line with it, so a block
  the emitter leaves out leaves no blank line behind; tested.

  Every name is still `JavaUtil`'s, every wire number still the
  flyweight's, and `encodedLength`, `decodedLength` and
  `lastDecodedLength` do not change: the block length written is the
  current schema's and the one read is the header's, which is what makes
  an older message shorter and a newer one longer without a line of ours.
  The box of a float, `Float` or `Double`, is the one thing the emitter
  decides from the primitive type itself; whether a component is boxed it
  reads from `Annotated`, and the boxing rule guarantees a box wherever
  absence needs one.
- **The corpus.** `OptionalFields` turns codecs on and carries its codec:
  the optional shape at a baseline of 0, with no version check. A new
  case, `AddedFields`, is a message at `version = 2` with
  `baselineVersion = 1`, grown by a required field in version 1, plain
  again because the baseline covers it, and by a required and an optional
  `Float` field in version 2, so its codec view shows the plain, added and
  optional shapes side by side under the append-only rule, the
  `Float.compare` test, and the baseline refusal. Both hold their codec as
  a text block; `Versions` keeps `codecs = false`, because it carries
  every node kind and the codec still lacks most of them. Nothing else in
  the work list moves.
- **The example, and the first frozen versions.** `com.example.quotes`
  goes to `version = 2` with `baselineVersion = 1`, and `Quote` shows a
  feed's history: version 1 appended a required `long sequence`, plain
  because version 0 readers are retired; version 2 appended the session's
  trade statistics, a required `Long tradeCount` and an optional `Double
  vwap`, absent until the instrument has traded. The oracle `quotes.xml`
  grows with them; `quotes-v0.xml` is the oracle as it was, and
  `quotes-v1.xml` the oracle with `sequence` alone, each frozen with only
  its leading comment saying so, and never edited again. At
  `generate-test-sources` the build runs `SbeTool` over all three, through
  the exec plugin's `java` goal with sbe-tool as its dependency, into
  `target/generated-test-sources/sbe`, which the build-helper plugin adds
  as a test source root: `quotes.xml` under the namespace
  `com.example.quotes.xmlref`, and each frozen file under `xmlref.v0` and
  `xmlref.v1`, all with `stopOnError` and `warningsFatal`. Both plugins
  get their versions in the root POM. The reference flyweights are test
  code only: the example's compile classpath still holds the api and
  Agrona and nothing else, and sbe-tool is already on its test classpath
  through the generator. `QuotesTest` keeps the oracle test and the
  template-id test and gains:
  - a round trip at a non-zero offset with every field present, and one
    with `vwap` absent, each with `encodedLength`, `decodedLength` and
    `lastDecodedLength` equal to the bytes written;
  - `null` in `tradeCount` refused on encode with `IllegalArgumentException`
    naming the component;
  - ours to the reference: a `Quote` encoded through `QuoteCodec` and read
    through `xmlref`'s `QuoteDecoder`, field by field, with `NaN` on the
    wire for an absent `vwap`; and the reverse, a message written through
    `xmlref`'s `QuoteEncoder` decoded to the equal record;
  - across versions: a message written through `xmlref.v1`'s
    `QuoteEncoder`, with a version 1 header, decodes through `QuoteCodec`
    to a `Quote` with its `sequence` and `tradeCount` and `vwap` `null`,
    and a `lastDecodedLength` of the shorter block plus the header; a
    `Quote` encoded through `QuoteCodec` reads through `xmlref.v1`'s
    `QuoteDecoder`, which consumes the whole longer block; and a message
    written through `xmlref.v0`'s `QuoteEncoder` is refused by
    `QuoteCodec` with `IllegalArgumentException` naming version 0 and the
    baseline.
  `com.example.trading` stays as it is.
- **The documents.** `architecture.md`'s models and rules sections take
  `Problem`'s severity and the first warning, its generation section
  describes the templates of each side, the null test and the baseline
  refusal and that warnings stop nothing, its testing section the
  reference packages and the frozen files as they now exist, and its
  build section the two plugins; `type-mappings.md` gains
  `baselineVersion` in the `messageSchema` row, sharpens absence to the
  boxing rule, and says a constant is never absent; `notes.md` takes the
  facts above and whatever the build teaches about running `SbeTool`
  under Maven; `intent.md` ticks increment 7 and names the baseline where
  it lists what is written by hand.

## Criteria

- `OptionalFields`' and `AddedFields`' emitted codecs equal their `codecs`
  view exactly; every other case still reports the construct the codec
  lacks, naming the message, and no case reports an optional field or a
  field added in a later version.
- The quotes example round trips with and without its optional field,
  refuses `null` in its required added field, agrees with sbe-tool's
  reference flyweights in both directions at the current version, decodes
  a version 1 message and is decoded by a version 1 reader, and refuses a
  version 0 message, all in the real build; each frozen oracle is
  byte-for-byte the oracle it replaced below its comment.
- The boxing rule has a test in each direction and one at the baseline
  that build the mistake and assert the `Problem`, its severity and the
  node; a `baselineVersion` above `version` has one too. In the
  processor, one snippet with a box on a never-absent field asserts a
  warning on that component and every output written, the mirror of the
  error snippets that assert nothing was.
- Every new template is a text block filled through `Template`; the
  emitter's only decisions outside the IR and `Annotated` are the float's
  box and whether to paste the baseline check; the baseline is the only
  literal in generated code and no wire number is.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Enums, sets, named types, constants, arrays, `char` strings, bindings,
composites, groups, var-data, byte order, header types: each keeps its
refusal and its increment. `sinceVersion` on groups, var-data and inside
composites, and a message's own `sinceVersion`, wait for increment 15,
and so does whatever the baseline means for a `deprecated` node.
`deprecated` changes nothing in a codec. Reference flyweights for the
trading schema, whose codecs are off. The `.sbeir` file. Publishing.
