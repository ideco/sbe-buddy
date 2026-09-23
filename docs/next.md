# Increment 17: Byte order and header types

## Goal

A schema can put its messages on the wire big-endian, and can frame them in
a header of its own. The mapping, the XML and sbe-tool's flyweights already
carry both; the codec refuses both. This increment lifts the two refusals
and gives the header a Java form: every codec reads the header on its own,
and encodes a message with the header's extra members supplied by the
caller.

## What the spike showed

- **Big-endian already works.** The codec reaches the buffer only through
  the flyweights, which apply the byte order themselves. With the refusal
  lifted, a `long` round-tripped and read back big-endian at
  `orderIdEncodingOffset()`. What is missing is the proof.
- **A custom header almost works.** The templates already take the header's
  class from the IR, so a codec compiled against `ApplicationHeaderEncoder`
  and round-tripped. One real bug: sbe-tool's `wrapAndApplyHeader` writes
  only `blockLength`, `templateId`, `schemaId` and `version`, and an extra
  member keeps whatever the buffer held. `encode` then claims bytes it never
  wrote.
- **The standard four are always `uint16`.** sbe-tool only warns about any
  other type, and our pipeline makes its warnings fatal, so their Java type
  is always `int`.

## Settled before it started

- **A header is named in two ways, as sbe-tool reads it.** The schema's
  `headerType` attribute names the header composite, and without it the
  composite named `messageHeader` is the header; nothing else marks it.
  `@SbeSchema(headerType = …)` keeps doing both: a header record whose wire
  name is `messageHeader` writes no attribute, any other name writes
  `headerType`. A hand-written schema in a later partial mode can use
  either.
- **The api gains `interface MessageHeader`**, with `int blockLength()`,
  `int templateId()`, `int schemaId()` and `int version()`. Not `Header`:
  Aeron's `io.aeron.logbuffer.Header` sits beside a codec in every fragment
  handler. The built-in record is renamed `DefaultMessageHeader`, keeps its
  wire name `messageHeader`, and implements the interface. A custom header
  is a record that implements it too, so a header declaring the four
  standard components already has the accessors.
- **`@SbeSchema.headerType` is typed `Class<? extends MessageHeader>`,**
  default `DefaultMessageHeader.class`. A class that does not implement the
  interface is then javac's error on the annotation, the first rule layer,
  with no rule of ours.
- **`Codec<T, H extends MessageHeader>`.** `H` is the schema's header
  record. Code that holds the concrete class, or `var`, never writes it;
  generic code writes `Codec<Order, ?>`.
- **Two methods join the contract.**
  - `H decodeHeader(DirectBuffer buffer, int offset)` reads the whole header,
    the standard four and every extra member the record carries. It checks
    nothing, so it can peek at any message of any template before choosing
    a codec, which is what increment 19's dispatch needs.
  - `int encode(T value, H header, MutableDirectBuffer buffer, int offset)`
    writes the message with the header's extra members taken from `header`.
    It returns what `encode` returns; a `null` header is an
    `IllegalArgumentException`, `header is required`.
- **The standard four are always the codec's own.** Both `encode`s write the
  message's `blockLength`, `templateId`, `schemaId` and `version` through
  `wrapAndApplyHeader`, and ignore the header's values for them. Checking
  them as constants are checked would break relaying: a header decoded from
  an older message carries another version. Plain `encode(value, …)` writes
  every extra member as its null value, so no byte is left as the buffer
  held it.
- **The standard four keep their wire names.** A header record's component
  `blockLength`, `templateId`, `schemaId` or `version` renamed on the wire to
  something else is refused by `Mapping`, since the interface's accessor
  would then read another member.

## What gets built

- **The api.** `MessageHeader`, `DefaultMessageHeader`, `headerType`'s new
  type, and `Codec`'s second type parameter and two methods, each
  documented as a contract.
- **`CodecModel`.** The header becomes a node of its own: the header
  record, its flyweight class and a body over the header flyweight, built
  as a composite's is. Its extra members are fields; the standard four are
  read and never written; members under the record's `unmapped` are
  written as their null value.
- **`CodecWalk`.** The header body comes from `ir.headerStructure()` and the
  schema's `headerType` composite, with the composite's shapes, so an extra
  member may be anything a composite member may be. The refusals of a
  header of its own and of big-endian go.
- **`CodecWriter`.** Plain `encode` and `encode` with a header share one
  body; the header's extras are written after `wrapAndApplyHeader`, from the
  header or as null. `decodeHeader` builds the record through its canonical
  constructor. `encodedLength` is unchanged: the header's length is fixed.
- **`Mapping`.** The rule on the standard four's wire names.
- **The corpus.**
  - `header` gets its codecs. Round trips through both `encode`s; a test
    that plain `encode` into a dirty buffer leaves every extra member at
    its null value; a test that `encode` with a header writes its sequence
    number and ignores its standard four; a test that `decode` ignores the
    extras.
  - A new case, `leadingheader`, whose header puts an extra member before
    the standard four, so every standard offset moves.
  - `bigendian` grows to everything whose bytes depend on the order: every
    multi-byte width, `float` and `double`, a `short[]`, an enum on
    `uint16`, a set on `uint32`, a composite, a group's dimension and
    var-data with a `uint16` length. Its own test reads values big-endian
    at the flyweights' `<field>EncodingOffset()`.
  - `SchemaCase.RoundTrip` takes a `Codec<T, ?>`, and `SchemaCasesTest`
    checks `decodeHeader` after every round trip: the standard four as the
    flyweights declare them.
- **The example.** The rename and the new type parameter only. Neither
  schema changes its byte order or header: `quotes` cannot without breaking
  every frozen version, and `trading` would prove nothing the corpus does
  not.
- **The documents.** `type-mappings.md`'s `messageSchema` row and header
  section; `architecture.md`'s codec contract; a guide page for the
  schema's own attributes, `byteOrder` and `headerType` with what the codec
  writes into a header, and the rename across the guide; `notes.md` on
  `wrapAndApplyHeader`; `intent.md` ticks 17.

## Criteria

- Every round trip in the corpus passes the whole codec contract, and its
  header reads back through `decodeHeader`.
- No `encode` leaves a header byte as the buffer held it.
- The `bigendian` case reads every value big-endian at its offset.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Partial mode, and with it the check that a header record matches a
hand-written schema's header. Bindings on header members. Members appended
to a header in a later version, increment 18. Family dispatch on
`decodeHeader`, increment 19.
