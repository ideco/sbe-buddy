# Increment 7: the codec, absence

## Goal

A field that may be missing decodes to `null` and encodes from it. Two
constructs make a field absent, `presence = OPTIONAL` and a `sinceVersion`
above the acting version of the message being read, and both land here,
because they share one mechanism: a boxed component, the null value on the
wire, and the acting version taken from the header. With them the codec
reads its first message from an older writer, so the example gains what
`architecture.md` has promised since increment 5: reference flyweights
that sbe-tool generates from the oracle, the first frozen schema version,
and cross-version decoding tested in both directions. Primitives still;
every other construct keeps its refusal.

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

- **The boxing rule, in `Mapping`.** A field can be absent when its
  `presence` is `OPTIONAL`, or its `sinceVersion` is above 0 and its
  `presence` is not `CONSTANT`. A component whose Java type is a primitive
  or its box is boxed exactly when the field can be absent: `Integer` on a
  field that is never absent is a `Problem`, `use int`, and `int` on one
  that can be is a `Problem`, `use Integer`. Decidable from one node,
  blamed on the field, tested in both directions like the other rules.
  The face rule stays as it is and looks through the box, so `Long` on an
  optional `uint16` still says `long is not the face of uint16, which is
  int`. `type-mappings.md` says the same in its absence section, and that
  a constant is never absent.
- **The codec emitter, absence.** The refusals for an optional field and
  for a field added in a later version go; a field is one of three shapes,
  decided in Java from the IR, the type token's presence and the field
  token's version, and each shape is a template beside the plain one:
  - *plain*, as increment 6 left it;
  - *optional*, whatever its version: `encodeOptionalField` writes the
    flyweight's `<field>NullValue()` for `null` and the value otherwise,
    one line; `decodeOptionalField` yields `null` when the wire holds the
    null value and the value otherwise, one expression in the constructor
    call. The null test is its own template, `isNull`, `==` against
    `<field>NullValue()` for the integer primitives and `char`, and
    `isNullFloating`, `Float.compare` or `Double.compare` against it
    `== 0`, because `NaN` is the null value of the floats and equals
    nothing. Below the acting version the getter returns the null value
    (`notes.md`), so an optional field added later needs no version test;
  - *added*, required with `sinceVersion` above 0: `encodeAddedField`
    throws `IllegalArgumentException("<component> is required")` for
    `null` before the plain call, the one block a primitive field needs;
    `decodeAddedField` yields `null` when `decoder.actingVersion()` is
    below `<field>SinceVersion()` and the value otherwise, on the version
    and never on the null value, which a required field may legitimately
    hold.

  Every name is still `JavaUtil`'s, every number still the flyweight's,
  and `encodedLength`, `decodedLength` and `lastDecodedLength` do not
  change: the block length written is the current schema's and the one
  read is the header's, which is what makes an older message shorter and
  a newer one longer without a line of ours. The box of a float, `Float`
  or `Double`, is the one thing the emitter decides from the primitive
  type itself; the emitter trusts the boxing rule for the rest.
- **The corpus.** `OptionalFields` turns codecs on, gains a `Float`
  beside its `Double` so both `compare` templates are in the expected
  source, and carries its codec. A new case, `AddedFields`, is a message
  grown over two schema versions, a field appended in each, one required
  and one optional, so the codec view shows `decodeAddedField` and
  `decodeOptionalField` side by side under the append-only rule. Both
  hold their codec as a text block; `Versions` keeps `codecs = false`,
  because it carries every node kind and the codec still lacks most of
  them. Nothing else in the work list moves.
- **The example, and the first frozen version.** `com.example.quotes`
  goes to `version = 1` and `Quote` appends two fields: a required
  `Long sequence` with `sinceVersion = 1`, and an optional `Double vwap`
  with `sinceVersion = 1`, absent until the instrument has traded. The
  oracle `quotes.xml` grows with them; `quotes-v0.xml` is the oracle as
  it was, frozen, with only its leading comment saying so, and is never
  edited again. At `generate-test-sources` the build runs `SbeTool` over
  both, through the exec plugin's `java` goal with sbe-tool as its
  dependency, into `target/generated-test-sources/sbe`, which the
  build-helper plugin adds as a test source root: `quotes.xml` under the
  namespace `com.example.quotes.xmlref`, `quotes-v0.xml` under
  `com.example.quotes.xmlref.v0`, both with `stopOnError` and
  `warningsFatal`. Both plugins get their versions in the root POM. The
  reference flyweights are test code only: the example's compile
  classpath still holds the api and Agrona and nothing else, and sbe-tool
  is already on its test classpath through the generator. `QuotesTest`
  keeps the oracle test and the template-id test and gains:
  - a round trip at a non-zero offset with every field present, and one
    with `vwap` absent, each with `encodedLength`, `decodedLength` and
    `lastDecodedLength` equal to the bytes written;
  - `null` in `sequence` refused on encode with `IllegalArgumentException`
    naming the component;
  - ours to the reference: a `Quote` encoded through `QuoteCodec` and read
    through `xmlref`'s `QuoteDecoder`, field by field, with `NaN` on the
    wire for an absent `vwap`; and the reverse, a message written through
    `xmlref`'s `QuoteEncoder` decoded to the equal record;
  - across versions: a message written through `xmlref.v0`'s
    `QuoteEncoder`, with a version 0 header, decodes through `QuoteCodec`
    to a `Quote` whose `sequence` and `vwap` are `null` and whose
    `lastDecodedLength` is the shorter block plus the header; and a
    `Quote` encoded through `QuoteCodec` reads through `xmlref.v0`'s
    `QuoteDecoder`, which consumes the whole longer block.
  `com.example.trading` stays as it is.
- **The documents.** `architecture.md`'s generation section describes the
  three shapes and the null test, its testing section the reference
  packages and the frozen file as they now exist, and its build section
  the two plugins; `type-mappings.md` sharpens absence to the boxing rule
  and says a constant is never absent; `notes.md` takes the facts above
  and whatever the build teaches about running `SbeTool` under Maven;
  `intent.md` ticks increment 7.

## Criteria

- `OptionalFields`' and `AddedFields`' emitted codecs equal their `codecs`
  view exactly; every other case still reports the construct the codec
  lacks, naming the message, and no case reports an optional field or a
  field added in a later version.
- The quotes example round trips with and without its optional field,
  refuses `null` in its required added field, agrees with sbe-tool's
  reference flyweights in both directions at the current version, and
  decodes a version 0 message and is decoded by a version 0 reader, all
  in the real build; `quotes-v0.xml` is byte-for-byte the previous oracle
  below its comment.
- The boxing rule has a test in each direction that builds the mistake
  and asserts the `Problem` and the node.
- Every new template is a text block filled through `Template`; the
  emitter's only decision outside the IR and `Annotated` is the float's
  box; no wire number appears in generated code.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Enums, sets, named types, constants, arrays, `char` strings, bindings,
composites, groups, var-data, byte order, header types: each keeps its
refusal and its increment. `sinceVersion` on groups, var-data and inside
composites, and a message's own `sinceVersion`, wait for increment 15.
`deprecated` changes nothing in a codec. Reference flyweights for the
trading schema, whose codecs are off. The `.sbeir` file. Publishing.
