# Increment 6: the codec, primitives

## Goal

A record becomes bytes and back through generated code. The api gains
`Codec<T>`; the generator gains the codec emitter, one `<Msg>Codec` per
message written from text-block templates over the IR and `Annotated`;
the processor writes it beside the flyweights; `@SbeSchema(codecs =
false)` turns it off. Primitives only, so the templates, the `Template`
type and the walk are settled on the smallest case, and every later
increment adds a construct without touching the mechanics. The emitter
will be the largest thing in the repository by the end, so its shape is
the point of this increment as much as its output.

## What gets built

- **`Codec<T>` in the api**, as `architecture.md` gives it: `encodedLength`,
  `encode`, `decode`, `lastDecodedLength` and `decodedLength`, over
  Agrona's `MutableDirectBuffer` and `DirectBuffer`. Implemented only by
  generated code.
- **`@SbeSchema(codecs = false)`**, a member of the annotation, default
  `true`; `Annotated` carries it and the processor reads it. Off, the
  processor does exactly what `0.1.0` did.
- **`Template`, in the generator.** A text block with named placeholders,
  `{name}`; `fill` takes the names and their values and fails on a name
  left unfilled or a value never used, because a silent gap in generated
  code is a bug nobody sees until it compiles. A value that spans lines is
  indented to the column of its placeholder, so a method body pasted into
  a class lands at the right depth. That is the whole type: no
  conditionals, no loops, no expression language; what varies is decided
  in Java and pasted in.
- **The codec emitter, in the generator**, its own file. Every construct
  it emits is a text block beside the method that fills it, named after
  the construct: `codecClass`, `encode`, `decode`, `encodedLength`,
  `decodedLength`, `encodeField`, `decodeField`. It walks the IR the way
  `JavaGenerator` does, `GenerationUtil.collectFields` over each message's
  tokens, and names flyweight members through `JavaUtil`, so what it
  calls was generated, by construction; each token's Java face comes from
  `Annotated` by name, the message by its wire name, the component by the
  field's wire name. Fully qualified names, no imports, one line per
  primitive field, real line breaks and indentation: the output is read
  while debugging and is not formatted afterwards. A construct it does not emit yet, a group, var-data, a
  composite, an enum, a set, a constant, an optional field, a bound type,
  is a `Problem` naming the message: `no codec for <construct> yet; set
  codecs = false`. Nothing is skipped silently.
- **What the codec does, for primitives.** `<Msg>Codec implements
  Codec<Msg>` in the schema package, `public final`, public no-arg
  constructor, owning one `MessageHeaderEncoder`, one
  `MessageHeaderDecoder` and the message's encoder and decoder. `encode`
  wraps with `wrapAndApplyHeader`, sets every field from the record's
  component in field order and returns the bytes written; `decode` wraps
  the header, checks `schemaId` and `templateId` and throws
  `IllegalArgumentException` on a mismatch, wraps the decoder with the
  acting block length and version, reads every field and calls the
  record's canonical constructor; `lastDecodedLength` is what the last
  decode consumed; `encodedLength` and `decodedLength` are the header's
  `ENCODED_LENGTH` plus the message's `BLOCK_LENGTH`, from the flyweights'
  constants, so the codec holds no number of its own.
- **One rule, in `Mapping`.** A field's component type must be the face of
  its wire type, `type-mappings.md`'s table: `int` for `uint16`, `long`
  for `uint32` and `uint64`, `short` for `uint8`, the same-width primitive
  for the signed ones, boxed only where a later increment allows it.
  Decidable from one node, blamed on the component, tested like the
  others.
- **`Generator.generate(Schema, Annotated, DynamicPackageOutputManager)`**,
  `Annotated` joining the signature as `architecture.md` said it would.
  After `JavaGenerator`, when `codecs` is on, the emitter runs over the IR
  and writes each codec through the same output under the schema package.
  An emitter problem is returned like sbe-tool's and generation writes
  nothing more.
- **The corpus's fifth view.** A case whose messages the codec covers
  gains `codecs`, the expected source of each codec by its qualified name
  as a text block, or a file under `src/test/resources` when it outgrows a
  screen; a case the codec does not cover yet carries an empty map and
  `codecs = false` in its source, so the processor's tests still compile
  it. `CorpusTest` asserts the emitted sources equal `codecs` exactly for
  the former, and for the latter that the emitter reports a problem naming
  the message, which is the work list for increments 7 to 15 and shrinks
  as they land. `Primitives` and `Messages` are the cases with a codec.
  Emitter tests need no javac.
- **The example.** The trading schema sets `codecs = false` with a comment
  saying which construct waits for which increment, so the flag is shown
  and the module builds. A second package, `com.example.quotes`, is a
  primitives-only schema a user would write, one message such as a quote
  with instrument, bid, ask and size, with codecs on and its oracle in
  `src/main/sbe`. Its tests: `schema.xml` against the oracle as before,
  and one round trip through the generated codec: encode a record at a
  non-zero offset, `encodedLength` equal to the bytes written, decode it
  back to an equal record, `lastDecodedLength` and `decodedLength` equal
  to the same number, and the wrong template id rejected.
- **The documents.** `architecture.md`'s generation section describes the
  emitter as text blocks over `Template`, and its testing section the
  fifth view and the second example package; `intent.md` ticks increment
  6.

## Criteria

- `Primitives`' and `Messages`' emitted codecs equal their `codecs` view
  exactly; every other case reports the construct the codec lacks, naming
  the message.
- The quotes example round trips through its codec in the real build; the
  trading example builds with codecs off.
- Every template in the emitter is a text block with named placeholders
  filled through `Template`; no code fragment is assembled by
  concatenation; the emitter names flyweight members through `JavaUtil`
  only.
- The face rule has a test that builds the mistake and asserts the
  `Problem` and the node.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Everything the emitter refuses above: groups, var-data, composites,
enums, sets, constants, optional presence, schema evolution in the codec,
`TypeBinding`, `@Bind`, the built-in bindings, message families. Reference
flyweights and frozen schema versions. Publishing.
