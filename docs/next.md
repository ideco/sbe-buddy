# Increment 13: Codec: groups, nested

## Goal

A group reaches the record as the `List<E>` it is written on, `E` the record
whose components are the group's fields and groups, each mapped by the rules
a message's body already follows: the same shapes over the group's own
flyweight, a binding, an enum, a set, a composite, a constant, a string, an
array, an optional field and an unmapped one as they are on a message. A
nested group is a list inside an entry, however deep. `encodedLength` earns
its keep: it sums the dimensions and the entries of every group without
encoding, and `decodedLength` walks them without decoding. A group appended
above the baseline is `null` below its version, as a field is. After it, what
the emitter still refuses is var-data, a field added above the baseline
inside a group, a header of the schema's own and big-endian byte order.

## Settled before it started

- sbe-tool's message flyweights expose a group as a static nested class of
  the message's, `<Msg>Encoder.<Group>Encoder` and `<Msg>Decoder.<Group>Decoder`,
  a nested group's class nested in its parent group's. The encoder's
  `<group>Count(int)` writes the dimensions and returns the group encoder,
  whose `next()` opens the next entry; the decoder's `<group>()` reads the
  dimensions and returns the group decoder, with `count()`, `hasNext()` and
  `next()`, and below the acting version returns it with a count of zero
  without moving the limit. Both group classes have static `sbeHeaderSize()`
  and `sbeBlockLength()`, the parent decoder a static `<group>SinceVersion()`,
  and the group decoder `actingVersion()`. Inside a group the fields are
  generated as a message's, their static meta methods on the group class
  and their getters guarded by the message's acting version. The encoder's
  wrap refuses a count outside the dimension type's range with its own
  `IllegalArgumentException`. The message decoder's `sbeSkip()` walks every
  group and var-data, and `sbeDecodedLength()` does so and restores the
  limit. (`JavaGenerator.generateDecoderGroups`, `generateEncoderGroups`,
  `generateGroupDecoderProperty`, `generateGroupEncoderProperty`,
  `generateGroupDecoderClassHeader`, `generateGroupEncoderClassHeader`,
  `generateFieldNotPresentCondition` and `generateMessageLength`, read
  2026-09-22; to `notes.md`.)
- An entry's fields are addressed by offset from the entry's start, as a
  message's are from the block's, so a record's components may take any
  order; its groups are sequential, so the codec reads them in wire order
  into locals before it calls the constructor, and writes them after the
  fields in the order the layout gives.
- A group has no presence: it cannot be optional, and a group present with
  zero entries is an empty list. A `null` list on encode is an
  `IllegalArgumentException`, `legs is required`, from `encodedLength` and
  `encode` alike. A group appended above the baseline is absent below its
  version, decodes to `null` by the flyweight's `<group>SinceVersion()` as
  a field does, and its component is nullable; the flyweight's count of
  zero below the version is never read as an empty list. A list longer than
  the dimension type's `numInGroup` allows is refused by the flyweight.
- A field added above the baseline inside a group is increment 16's,
  although the flyweight guards it: the shape is a field's, and the proof
  is a frozen version with one, which the example does not have yet. So
  is var-data, in a message or a group, increment 14's.

## What gets built

- **The api.** Nothing: `@SbeGroup` has every member it needs.
- **`Annotated`.** `ListOfRecord` gains `qualifiedName`, the entry record's
  name as code names it, which the codec's helpers and locals are typed
  with; `Discovery` fills it from the `E` of `List<E>`, and the corpus DSL
  gains `listOfRecord(name)`.
- **The rules.** Nothing new in `Mapping`: a group must be a `List` of a
  record, its body follows the message's rules, and a group has no
  presence to check. The emitter refuses a field added above the baseline
  inside a group, `a field added above the baseline in a group`, beside
  var-data.
- **The codec emitter.** Every shape takes its flyweight classes from its
  owner, the message's, a composite's or a group's, so a field inside a
  group is the same template over `<Msg>Encoder.<Group>Encoder`. A group
  is three private static methods keyed by its path, `write<Group>(List<E>
  entries, <Group>Encoder encoder)` looping the entries with `next()` and
  the body's encode statements, `List<E> read<Group>(<Group>Decoder decoder)`
  looping `hasNext()` and `next()` into an `ArrayList` sized by `count()`,
  and `int <group>Length(List<E> entries)`, the header plus the entries
  times the block length, plus each entry's nested groups where there are
  any, refusing `null`; a nested group's methods carry the parent's name,
  `writeLegsAllocations`, as an array pair does, in order of first use
  after the codec's own methods. A group encodes as
  `write<Group>(value.<group>(), encoder.<group>Count(value.<group>().size()))`
  under the checked shape, after the fields, and decodes into a local,
  `List<E> <group> = read<Group>(decoder.<group>())`, under the added shape
  above the baseline, before the constructor call, which takes the local
  where its component is. `encodedLength` adds a `<group>Length` term per
  group to the header and the block; `decodedLength` wraps the decoder and
  returns the header plus `sbeDecodedLength()` where the message has a
  group, and stays the block length where it has none, so every other
  case's view is untouched.
- **The corpus.** `Groups` goes to `version = 1`, turns codecs on and gains
  its `CODEC` view: a group with a `layout` and an unmapped field, holding
  a nested group with a `dimensionType` of its own and an optional field,
  and a second group appended in version 1 whose entry binds a price
  through `Cents`, so the nested pair, the shapes over a group's classes
  and the added shape on a group all show. Its var-data member moves to
  `VarData`, which gains a group holding var-data, so the shape stays in
  the corpus and `VarData` stays on `codecs = false`. `Versions` stays on
  `codecs = false` for its var-data.
- **The example.** `com.example.quotes` goes to `version = 7` and appends
  `contributors`, a group of `Contributor` records, each a venue's own
  quote, `Venue venue`, `bid` and `ask` through the `Price` binding and
  their sizes, `@Nullable List<Contributor>` since it is above the
  baseline; `quotes-v6.xml` is frozen with `xmlref.v6`. The tests: the
  round trip with two contributors and with none; `null` contributors
  refused on `encodedLength` and `encode`; the reference decoder reading
  the entries where the codec wrote them; the codec reading what the
  reference encoder writes; a version 6 message decoding with `null`
  contributors; a version 6 reader reading a current message's block and
  stopping before the group, which is SBE's limit. `com.example.trading`
  stays on `codecs = false` for its var-data.
- **The guide.** The reference page the index promises, `reference/
  groups.md`: `@SbeGroup` on a `List` of a record, the entry record,
  nested groups, `dimensionType`, `blockLength`, `layout` and `unmapped`
  on the body, what a group costs in `encodedLength`, absence and the
  empty list, what the compiler and the codec refuse.
- **The documents.** `type-mappings.md`'s absence section takes the rule on
  groups; `architecture.md`'s models, rules, generation, contract and
  testing sections follow; `notes.md` takes the facts above; `intent.md`
  ticks 13 and moves appended groups out of 16, which keeps the rest.

## Criteria

- `Groups`' emitted codec equals its `codecs` view exactly, its oracle
  parses, and every other case's view is untouched.
- The quotes example round trips with contributors against the reference
  flyweights, in the real build, with `encodedLength`, `lastDecodedLength`
  and `decodedLength` equal to the bytes written, and decodes a version 6
  message without them.
- Each refusal above has a test that builds the mistake and asserts the
  `Problem` and the node.
- The guide's reference page compiles as written: its snippets are the
  example's and the corpus's.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Var-data, increment 14, in a message or a group. A field added above the
baseline inside a group, increment 16, and `sinceVersion` inside a
composite with it. A header type of the schema's own, increment 15. A
group under `unmapped`, which `retire-a-field.md` still promises.
