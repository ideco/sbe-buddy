# Increment 26: Typed flyweights

## Goal

Every message of the schema gets a reader and a writer over sbe-tool's
flyweights: the message as a sequence of stages, in wire order by
construction, complete by construction on the writer, nothing allocated.
Where a record maps the message, each stage has a `bound()` view carrying
the record's types through its bindings, and the codec is rewritten over
those bound stages, so what a component is on the wire is decided in one
place and the corpus proves the flyweights. A union gets a reader that
dispatches by template id and shares what the interface declares.

The design is `flyweights.md`. This file is the build order. Read both
before starting a step; read `architecture.md` and the module `AGENTS.md`
files before touching a module; keep `.claude/rules/java-style.md`.

## Settled before it started

Decided in `flyweights.md`, not to be reopened here: the flat stage
sequence; the wire as the default and `bound()` the extra; `skip()` on
every stage; `default` safe; a stage answers while the reader is inside
it; the writer a typestate down to every required field, optional fields
on the block-complete stage, filled with their null values when the block
opens; no `Done` stage; the names (`<Message>Reader`, `<Message>Writer`,
`RootBlock`, `<Group>`, `<Group>Entry`, `<Data>`, `<Stage>Bound`);
commonality on a union declared by its interface; the codec over the bound
stages; the OTF decoder as an oracle and never the runtime.

Decided here, where the sketch left room:

- **Seven pull requests, in order,** each from a fresh `main`, each green
  on `./mvnw verify`, each one coherent change, as `.claude/skills/git`
  wants. Nothing in a later step is started before the earlier one is
  merged. A step's PR description says which step it is.
- **Generated for every message of the IR,** `ir.messages()` ordered by
  template id, never `annotated.messages()`: the readers and writers do
  not need a record. Only `bound()` does.
- **Liveness is read off the reader's position.** The reader keeps one
  `enum At` of its positions in wire order (`BEFORE_ROOT_BLOCK`,
  `ROOT_BLOCK`, each group's header and entry, an `AFTER_<Group>`
  where a stage can rest past nested content, `END`), and a stage is
  open while the position lies in its contiguous range: the root block
  from `ROOT_BLOCK` to `END`, a header from its own position to the last
  inside its group, an entry likewise, a var-data at its position alone.
  Every accessor, bound ones included, begins with that one range check,
  `IllegalStateException("<Stage> is not open")`. Nothing is set or
  cleared, the position is the single source of truth, and it accepts the
  same edge as a flag would: an entry kept across its group's `next()`
  reads the new entry. Step 0 found this simpler to write than a flag per
  stage; the generator emits the enum from the stage list.
- **`skip()` is for the current stage only,** `IllegalStateException("<Stage> is not the current stage")`
  on any other: pruning from inside a stage's nested content would have to
  finish that content first, and nobody needs it.
- **A var-data is passed over on arrival.** Reaching its stage records
  its start and length and calls `skip<Data>()`, so the limit is always
  past what was visited; reads set the limit back to the start, go
  through sbe-tool's `get<Data>`/`wrap<Data>`, and restore it. So
  `decodedLength()` is `ENCODED_LENGTH + decoder.encodedLength()` whenever
  `hasNext()` is false, and otherwise `sbeDecodedLength()` on a second
  decoder the reader keeps, because on the reader's own it would re-wrap
  the group decoders mid-walk (`notes.md`).
- **`index()` is counted by the reader,** since the group decoder keeps
  its index private; reset when the group opens.
- **`next()` is generated per stage, `hasNext()` is side-effect free.**
  Each stage kind has a fixed successor rule (below); `next()` performs
  the moves, `hasNext()` only asks the open group decoders' `hasNext()`
  and the static structure. No lookahead that reads the buffer: opening a
  group or skipping a var-data happens only in `next()`.
- **A group or var-data above the acting version is passed over** as if
  the schema did not have it, checked with the flyweight's static
  `<group>DecoderSinceVersion()` / `<data>SinceVersion()` against
  `decoder.actingVersion()`. Its stage never comes; the writer always
  writes the current version, so it has every stage.
- **The writer fills a block with null values on open, field by field,**
  through the same null writers the codec uses for `unmapped` fields; the
  sketch's `byte[]` null image is an optimisation a JMH run may add later.
  Constants are not written; every other field of the block, mapped or
  not, gets its null value, then the chain overwrites what it sets.
- **One object per block, the root block's an inner class too:** javac
  refuses a class implementing its own nested interfaces (`notes.md`), so
  `wrap()` returns a private `RootBlockStage`, never the writer. The
  bound twin is a second object per block, `RootBlockBoundStage`, since
  the wire and bound setters of a `String` field share a signature.
- **A set is a required step with a sub-chain,** `<Set>Writer<N>` with the
  choices in any order and `end()` back to `N`; an empty set is
  `.execInst().end()`. Its choices are unordered, so unlike a composite it
  needs the `end()`.
- **A `char` array step takes the whole value:** `String`, `CharSequence`
  and `put<Field>(byte[], int)`, as sbe-tool's setters; the indexed
  setter is not a step, a field is written whole or not at all.
- **The wire var-data stage has no `String` accessor:** `length()`,
  `copyTo(MutableDirectBuffer, int)`, `copyTo(byte[], int)`,
  `wrap(DirectBuffer)`; the `String` is the record's, on `bound().value()`.
- **A composite in the writer is a chain of every member in wire order,**
  constants skipped, an optional member offering `<m>(value)` and
  `<m>Null()`, the last member returning the enclosing stage `N` through
  a phantom generic `<Composite>Writer<N>`. Composites are small and have
  no natural block-complete stage, so they are exhaustive outright.
- **The header's own members are the caller's**: `writer.wrap(buffer, offset)`
  writes the standard four through `wrapAndApplyHeader` and the rest as
  their null values; `writer.header()` returns sbe-tool's
  `<Header>Encoder` wrapped at the offset for whoever wants to set them;
  `reader.header()` returns the `<Header>Decoder`. The codec uses these
  for `encode(value, header, …)` and `decodeHeader`.
- **Helpers live in the reader and the writer,** private static methods
  as they are in the codec today: enum and set pairs, `ascii`, arrays,
  composites. After step 5 the codec has none.
- **Reserved names are an error on the node**: a group or var-data named
  `RootBlock`, `Stage`, `Member` (after `formatClassName`), or a group
  and a sibling named `<Group>Entry`, is
  `the <group|data> "<name>" clashes with the reader's <Name>; rename it`,
  on the component code-first, on the package schema-first. Step 2 found
  more a reader cannot compile with, and they are the same error: `At`,
  the reader's own name, the java.lang types it names (`String`,
  `Iterable`, `Override`, `Appendable`, `IllegalStateException`), any two
  stages or `At` constants alike anywhere in the message, and a field whose
  accessor would be `skip`, `bound`, an entry's `index` or a method of
  `Object`, `the field "index" clashes with the reader's LegsEntry.index();
  rename it`.

### The successor of each stage

The reader's whole control is this table, generated per message. "Level"
is a block: the root block or one group's entry. Each level has, in wire
order, its groups then its var-data.

| current stage | `next()` gives |
|---|---|
| `RootBlock` | the first present group's header at the root; else the first present var-data of the root; else nothing |
| a group header `G` | `G.hasNext()` → `G.next()`, the entry; else *after G* |
| an entry of `G` | the entry's first present nested group header; else its first present var-data; else `G.hasNext()` → the next entry; else *after G* |
| a var-data `D` of a level | skip `D` if unread (`skip<D>()`); then the level's next present var-data; else if the level is an entry of `G`: `G.hasNext()` → the next entry, else *after G*; else nothing |

*After G* is: the next present group at G's level; else the level's first
present var-data; else, if the level is an entry of `P`, `P.hasNext()` →
the next entry of `P`, else *after P*; else nothing. `skip()` on a header
runs `next()` + `sbeSkip()` for every remaining entry then continues with
*after G*; on an entry runs the group decoder's `sbeSkip()` then
continues as if its nested parts were passed; on the root block runs the
message decoder's `sbeSkip()` and ends iteration; on a var-data does
nothing. `hasNext()` is the same table with the moves replaced by "would
there be a stage", which needs only `hasNext()` on decoders and
`actingVersion()`.

## The steps

### Step 0: the design, by hand

**Goal.** Prove the shape on real messages before any generator exists,
and leave behind the tests the generated classes must then pass.

**Build**, in `sbe-buddy-example/src/test/java/com/example/trading/hand/`,
test scope only:

- `NewOrderReader` and `NewOrderWriter` by hand over
  `com.example.trading.sbe.NewOrderEncoder/Decoder`: nested groups
  (`parties` → `partySubIds`), enums, a set, a constant, `char` arrays,
  composites, `layout` and explicit offsets, with `bound()` on every
  stage that carries data: the record's types through the enum pairs,
  the set, `UtcTimestampBinding`, `QtyBinding` over a face record the
  reader builds, `PriceBinding` deciding `null` from an optional
  composite's face, the `BindingContext` constants and the leaf helpers
  as the codec has them, on the writer a bound twin per stage with
  `bound()`/`wire()` hops.
- `CancelRejectReader` and `CancelRejectWriter`: var-data, `TextBound.value()`
  and the bound `text(String)` through a reporting `CharsetEncoder`.
- Exactly the API of `flyweights.md` and the decisions above: `Stage`
  sealed with `skip()`, `RootBlock`, `Parties`, `PartiesEntry`,
  `PartySubIds`, `PartySubIdsEntry`, `Text`; the writer's chain with
  `PartiesEntry` complete offering `partySubIds()`, `PartySubIds.end()`
  returning `Parties`, `Parties.end()` returning the stage after it.
- Until the api has `Stage`, a local `interface Stage { void skip(); }`
  in the same package; step 2 replaces it.

**Tests**, `HandReaderTest`, `HandWriterTest` and `HandBoundTest` in the
same package, over `Samples` values encoded with the codecs:

- the reader visits exactly the expected sequence of stage classes for
  `LIMIT_ORDER` (two parties, sub ids in one) and for `CANCEL_REJECT`;
- every field read through the stages equals the sample's;
- `skip()` on `Parties` makes no `PartiesEntry` come and the following
  stages still do; `skip()` on an entry passes its sub ids;
- a stage read after the reader left it throws `IllegalStateException`;
- an entry read inside its nested group answers (liveness);
- a var-data read twice reads the same bytes; `decodedLength()` equals
  the codec's;
- the writer's chain for `LIMIT_ORDER` produces bytes the codec decodes
  to `LIMIT_ORDER`; an empty nested group is `.partySubIds().end()`; an
  optional field left unset decodes as `null`; `length()` equals
  `encodedLength`.
- Compile-time properties are not tests: a `// does not compile:` comment
  block in `HandWriterTest` shows `length()` before `parties()`, and
  `text()` without `parties()`, for the reviewer.

**Docs.** None; the sketch is the design. Anything the hand-written classes
force you to change in the sketch is changed in this PR and named in its
description.

**Done when** both tests are green and the reviewer can read the chains.

- The bound stages read the sample component by component, unset
  optionals as `null`; the bound chain writes the codec's bytes; a chain
  hops between wire and bound; a bound setter refuses what the codec
  refuses with the codec's message, `null` on a required field, a string
  too long, a price with too many decimals, an unknown enum value, a
  character the charset lacks.

**Do not** generate anything or touch the generator.

### Step 1: split the generator, no behaviour change

**Goal.** Make the leaf layer (what a component is on the wire) a thing
of its own, used by the codec now and by the bound stages in step 3, and
make the IR walk reusable by a walk that has no records.

**Build**, in `sbe-buddy-generator`:

- `Join`: the walk of one message's IR tokens joined with its annotations
  where a record exists. It takes `Ir`, `Annotated`, the baseline and the
  message's tokens, and returns a tree: `Join.Block(String encoder, String decoder, String name, List<Join.Field> fields, List<Join.Group> groups, List<Join.Data> data)`,
  `Join.Group(Token token, String name, Join.Block entry, …)`,
  `Join.Data(Token token, String name, …)`, each field or member with its
  token, its property name (`JavaUtil.formatPropertyName`), and, where a
  record component maps it, the `Faces` shape, absence and binding.
  Everything that is today `CodecWalk.body/field/unmapped/group/data`,
  `shape/encoding/enumeration/set/composite/compositeBody/constant`,
  `absence/canBeAbsent/withoutNullValue`, `bound`, `content/charset`, the
  naming helpers and the `FaceRules` calls moves here. A message with no
  record (`Annotated.Message` absent) joins with no components: every
  node has its token and name and nothing else.
- `Faces`: the leaf model, moved out of `CodecModel`: `Shape`, `Helper`,
  `Content`, `Absence`, `Binding`, `Context`, and the null writers.
- `FaceTemplates` and `FaceWriter`: the leaf templates and the writer
  methods that render a read expression, a write statement, a null write
  and a helper for one shape — today's `CodecWriter.read(Member.Field, Shape)`,
  `write(Body, Member.Field, Shape, String)`, `writeNull`, `helper`,
  `enumPair`, `setPair`, and the templates they fill. `CodecTemplates`
  keeps the class, the header, the lengths, groups, var-data structure
  and the union.
- `CodecWalk` becomes: `Join` the message, then build `CodecModel` from
  the tree (bodies, wire and constructor order, helpers collected).
  `CodecModel` keeps `Header`, `Body`, `Member`; its `Member.Field` refers
  to `Faces` types.

**Tests.**

- Before the first change, copy `sbe-buddy-tests/target/generated-sources/annotations`
  and `sbe-buddy-example/target/generated-sources/annotations` aside;
  after the last, `diff -r` them against a fresh build: **no byte may
  differ**. Say so in the PR description with the command used.
- `GeneratorTest`, `SchemaRoundTripTest`, `SchemaFirstTest` and the
  corpus are unchanged and green.
- `JoinTest` in the generator: a message without a record joins with no
  components; a message with a record joins each component; the face
  rules still report through it (one existing `FaceRules` failure moved
  into a `JoinTest` case).

**Docs.** `architecture.md`: the models section gains `Join` and
`Faces`, step 7's line names them; `sbe-buddy-generator/AGENTS.md`: the
file table and the "Codecs" section ("A new construct is a shape in
`Faces`, its templates in `FaceTemplates`, and a case in `FaceWriter`;
the codec and the flyweights both read it").

**Done when** the diff is empty and the PR touches no template text, only
where templates live and who fills them. Moving a template is allowed;
rewording one is not.

**Do not** change any generated line, add any flyweight code, or rename
anything the user sees.

### Step 2: the wire reader

**Goal.** `<Message>Reader` for every message of the IR, wire stages only,
generated into `<package>.sbe` beside sbe-tool's flyweights.

**Build.**

- api: `net.concini.sbebuddy.Stage` — `void skip();` with Javadoc from the
  sketch's "Every stage can `skip()`".
- generator: `FlyweightWalk` (from `Join`'s tree to a `FlyweightModel`),
  `FlyweightModel` (the reader's stages: for each stage its kind, name,
  the flyweight members it delegates to, its successor per the table, its
  nesting), `ReaderTemplates`, `ReaderWriter`, and `FlyweightEmitter`
  called by `Generator.generate` after the codec emitter, once it reported
  no error, for every message of `ir.messages()`, into `staged` under
  `ir.applicableNamespace()`; the reader's join is the codec's, whose
  problems the codec emitter reports.
  The reserved-name check lives in `FlyweightWalk` and reports a `Problem`
  on the component, or on the package where no record exists.
- The generated reader exactly as `flyweights.md`'s shape: `Iterable` and
  `Iterator` of its sealed `Stage`, `wrap(DirectBuffer, int)`, `rewind()`,
  `decodedLength()` (`ENCODED_LENGTH + decoder.encodedLength()` once
  `hasNext()` is false; else `sbeDecodedLength()` on the second decoder),
  `header()`, `actingVersion()`; the `At` enum and the range check on
  every accessor, `skip()`, `index()` on entries, `count()` on headers,
  var-data with `length()`, `copyTo(MutableDirectBuffer, int)`,
  `copyTo(byte[], int)`, `wrap(DirectBuffer)`, reading through saved and
  restored `limit()`. No `bound()` yet.
- Fully qualified names and no imports, as the codecs; a Javadoc on the
  class naming the message and on each stage naming its block.

**Tests.**

- `sbe-buddy-tests`, `ReaderSequenceTest` in `net.concini.sbebuddy.tests`:
  for every `SchemaCase` and every `RoundTrip`, encode the value with its
  codec at `OFFSET`, take the template id from `codec.decodeHeader`, find
  the reader class `<package>.sbe.<formatClassName(messageName)>Reader`
  by name, `wrap` it, and record `(stage class simple name, count() or
  length() or nothing)` per stage. Parse the case's `schema.xml` resource
  with sbe-tool's `XmlSchemaParser` and `IrGenerator` (on the test
  classpath already), decode the same buffer with `OtfHeaderDecoder` and
  `OtfMessageDecoder` and a `TokenListener` that records
  `RootBlock` on `onBeginMessage`, `formatClassName(name)` with
  `numInGroup` on `onGroupHeader`, `formatClassName(name) + "Entry"` on
  `onBeginGroup`, `formatClassName(name)` with `length` on `onVarData`.
  The two lists are equal. Also: `decodedLength()` equals the codec's.
- The same test, second pass: `skip()` on every group header the first
  time it is met gives the OTF sequence with that group's entries and
  everything under them removed. A third pass skips every entry, which
  removes what each entry holds past its block.
- `HandReaderTest` from step 0 re-pointed at the generated
  `com.example.trading.sbe.NewOrderReader` and `CancelRejectReader`. The
  hand-written readers stay until step 4, since `HandBoundTest` reads
  through their bound stages; step 4 deletes them.
- `GoldenSourcesTest` in `sbe-buddy-tests`: `target/generated-sources/annotations/corpus/groups/sbe/GroupsReader.java`
  equals `src/test/resources/golden/corpus/groups/sbe/GroupsReader.java`;
  running with `-Dgolden.update=true` rewrites the resource instead.
  The golden is reviewed in the PR like any source.
- Processor, `AnnotationMistakesTest`: a group named `stage` and a group
  named `fills` beside a var-data `fillsEntry` are errors with the text
  above, on the component; the schema-first twin lands on the package.
- `SchemaRoundTripTest` and `SchemaFirstTest` cover the new files by
  construction; check they are green.
- `FlyweightsOnlyTest` and `PartialMappingTest`: the unmapped messages
  have readers; read one.

**Docs.** `type-mappings.md`: a new normative section "Typed flyweights:
the reader", the class, the stage kinds, their methods, `skip()`,
liveness, versions; `architecture.md`: step 7 gains the flyweights,
Generation gains a bullet; `notes.md`: the facts read for this step,
`sbeRewind()`, `limit()`/`limit(int)`, group `wrap(DirectBuffer)`,
`OtfMessageDecoder`'s walk, with the files they were read from; the
guide: `docs/guide/reference/flyweights.md`, reading only, and a line in
`getting-started.md`; `glossary.md`: stage, reader; README: one
paragraph; `sbe-buddy-generator/AGENTS.md`: the new files;
`sbe-buddy-api/AGENTS.md`: `Stage`.

**Done when** `ReaderSequenceTest` is green over the whole corpus, the
golden is reviewed, and `HandReaderTest` passes unchanged against the
generated class.

**Do not** generate the writer, add `bound()`, or touch the codec.

### Step 3: the wire writer

**Goal.** `<Message>Writer` for every message of the IR.

**Build.**

- generator: `WriterTemplates`, `WriterWriter`; `FlyweightModel` gains the
  writer's typestate as a list per block: for each required field a stage
  interface `<Block><Field>` whose one method returns the next; the
  block-complete stage `<Block>` with the optional setters and the first
  mandatory follower; per group a stage `<Group>` with `entry()` and
  `end()`; the stage after the last mandatory step carrying `length()`.
  One object per block implements every interface of its block.
- Composites: `<Composite>Writer<N>` generated once per composite type of
  the schema, in `<package>.sbe`, the chain of every member.
- Null values on open through `FaceWriter`'s null writers.
- `header()`, and the run-time check: a kept stage calling a method after
  the writer moved past it is `IllegalStateException`, the writer's own
  `At` position, one value per stage.

**Tests.**

- `sbe-buddy-tests`, `WriterRoundTripTest`: for every corpus case with a
  codec and every round trip, the codec's bytes decoded by the reader and
  re-written through the writer's *wire* chain, driven by the reader's
  stages (a generic loop cannot type the chain, so this test is per case:
  each `<Name>Test` that has a writer worth exercising gains one `@Test`
  writing its first round-trip value by hand and asserting the codec's
  bytes; `groups`, `vardata`, `composites`, `optionalfields`, `evolution`,
  `bigendian`, `header` at least).
- `HandWriterTest` from step 0 re-pointed at the generated writers, the
  hand-written ones deleted.
- Golden: `GroupsWriter.java`.
- Unset optionals decode as `null` through the codec; a group with no
  `entry()` is written with count 0; a nested group must be opened per
  entry (a `// does not compile:` block).

**Docs.** `type-mappings.md`: "Typed flyweights: the writer"; the guide
page gains writing; `glossary.md`.

**Done when** every listed case writes its bytes and the goldens are
reviewed.

**Do not** add `bound()` or touch the codec.

### Step 4: bound stages

**Goal.** `bound()` on every reader and writer stage of a message a
record maps, carrying the record's components through `Faces`.

**Build.**

- `FlyweightWalk` reads the joined components off `Join`'s tree; the model
  gains, per stage, its bound members: for each component of the block a
  read expression and a write statement from `FaceWriter`, absence to
  `null` (boxed for primitives, `null` for enums, composites, bound
  types, both for the null value and above the acting version, exactly
  the codec's `DECODE_OPTIONAL_FIELD`/`DECODE_ADDED_FIELD` logic), a
  `BindingContext` constant per bound component, helpers collected into
  the reader and writer classes.
- Reader: `<Stage>Bound` classes, singletons, checking their stage's
  range, every accessor applying the binding on each call, never cached.
  A var-data's bound stage has `value()`.
- Writer: `<Stage>Bound` interfaces mirroring the wire chain with the
  record's types, `bound()` on every wire stage that takes a value and
  `wire()` on every bound one, implemented by a second object per block
  (a `String` field's wire and bound setters share a signature and differ
  in return type, so one class cannot carry both). A composite with a
  binding is one step taking the bound value; without one, the step also
  takes the face record. Group stages and a block-complete stage with no
  optional fields carry no data and have no twin. The shape is step 0's
  `NewOrderWriter`, to match, not to invent.
- A message without a record: no `bound()` anywhere, nothing else changes.

**Tests.**

- `sbe-buddy-example`, `BoundStagesTest`: `Samples.FILL`, `LIMIT_ORDER`,
  `CANCEL_REJECT`, `ACKNOWLEDGEMENT` encoded with their codecs, read
  through the reader's bound stages, each component asserted equal to the
  sample's; the same values written through the writer's bound chain,
  decoded by the codec, equal.
- Corpus: `GroupsTest`, `BindingsTest`, `VarDataTest`, `CompositesTest`,
  `OptionalFieldsTest`, `AddedFieldsTest`, `EvolutionTest` each gain one
  `@Test` reading their first round-trip value's bound components
  through the reader and asserting equality, and `AddedFieldsTest`
  asserts a field above the acting version is `null` on the bound stage.
- `HandBoundTest`'s reading half re-pointed at the generated readers, the
  hand-written readers deleted.
- Goldens updated (`GroupsReader.java`, `GroupsWriter.java`) and
  reviewed: the diff is the bound classes and nothing else.

**Docs.** `type-mappings.md`: "bound stages", absence on them, boxing;
the guide page; `docs/guide/reference/bindings.md`: one paragraph that a
binding applies on the flyweights too.

**Done when** the listed tests are green and the goldens' diff is only
additions.

**Do not** touch the codec.

### Step 5: the codec over the bound stages

**Goal.** One implementation of every leaf conversion. The codec keeps
the record.

**Build.**

- `CodecModel` loses every reference to `Faces.Shape` and `Faces.Helper`;
  `Body` keeps wire order, constructor order and the length terms.
- `CodecWriter`/`CodecTemplates`: `decode` is the cast-driven walk of
  `flyweights.md`'s "The codec over them", `count()` driving each group
  loop, `bound()` on each stage giving the constructor arguments;
  `encode` and `encode(value, header, …)` the writer's bound chain, a loop
  of `.entry().bound()…` per group, `header()` for the header's own
  members, the length from the chain's last step; `decodedLength(buffer,
  offset)` is `reader.wrap(…).decodedLength()`; `canDecode`,
  `decodeHeader`, `lastDecodedLength` unchanged; `encodedLength(value)`
  unchanged, without writing.
- The codec holds one reader and one writer as fields, as it held the
  flyweights.
- `FaceWriter`'s remaining codec-only callers go; if a template in
  `FaceTemplates` or `FaceHelperTemplates` has no caller left, delete it.
- A new module `sbe-buddy-benchmarks`, not deployed, JMH (`jmh-core` and
  `jmh-generator-annprocess` on its processor path, versions in the root
  pom), depending on `sbe-buddy-example`: one benchmark class,
  `TradingBenchmark`, with `decode`/`encode` of `Samples.FILL` and
  `LIMIT_ORDER` through their codecs, run by hand with
  `./mvnw -pl sbe-buddy-benchmarks -am package && java -jar sbe-buddy-benchmarks/target/benchmarks.jar`.
  Not part of `verify`.

**Tests.** The corpus, unchanged. `CodecsTest`, `ReferenceFlyweightsTest`,
`UnionsTest` in the example, unchanged. The processor's
`SchemaRoundTripTest`, unchanged. If any of them needs a change, the
codec's behaviour changed, which this step must not do; find the
difference.

**Measure.** Run `TradingBenchmark` on `main` and on the branch; the
numbers go in the PR description. If decode or encode is more than 10%
slower, gate the range check behind a `static final boolean` read once
from the system property `sbebuddy.checks` (default `true`), as
sbe-tool's bounds checks are, and measure again with it off; both numbers
in the description. Do not remove the check.

**Docs.** `architecture.md`: Generation ("Codecs are written over the
readers' and writers' bound stages and hold no leaf conversion"), the
codec contract unchanged; `sbe-buddy-generator/AGENTS.md`: the "Codecs"
section rewritten; `sbe-buddy-benchmarks/AGENTS.md`; README's module
table; `intent.md`: nothing.

**Done when** the corpus is green with no test changed, the generated
codec for `NewOrder` is under a hundred lines, and the benchmark numbers
are in the PR.

**Do not** change `Codec`, its contract, or any oracle.

### Step 6: union readers

**Goal.** `<Union>Reader` per `@SbeUnion`, dispatching by template id,
sharing what the interface declares.

**Build.**

- processor, `Discovery`: the abstract, parameterless methods of the
  union's interface, name and return type, into a new
  `Annotated.Union.Method(String name, String returnType)` list, in
  declaration order.
- generator: `UnionReaderTemplates`, `UnionReaderWriter`; the walk
  matches each declared method to a component of the same name on every
  member's record and classifies it: a root-block field, a group, a
  var-data, or nothing (not the same kind on every member, or a field
  outside the root block). Wire methods are shared only where every
  member's face type is the same string.
- The generated union reader: `Member` sealed over the member readers
  (and over nested annotated unions' `Member`, flattening unannotated
  ones, as `CodecEmitter.union` does); `Stage` sealed over the members'
  `Stage`s, each member's `Stage` extending it; a `RootBlock` interface
  with the header's `templateId()`, `version()`, `blockLength()`, the
  shared wire fields, and `bound()` to a `RootBlockBound` with the
  declared fields; a `<Group>`/`<Group>Entry`/`<Data>` interface per
  shared group and var-data; `wrap(buffer, offset)` returning `Member`,
  `IllegalArgumentException` outside the union, `canDecode`; `Iterable`
  delegating to the current member.
- Member readers implement the union interfaces they belong to; a message
  in several unions implements each.

**Tests.**

- `sbe-buddy-example`, `UnionReadersTest`: `OrderEntryReader` over each
  of `ENTRIES`: the `Member` switch reaches the right reader;
  `root().bound().clOrdId()` equals the sample's without a switch; the
  flat loop over `TradingMessageReader` handles `OrderEntryReader.Member`
  as one case; a template id outside the union throws.
- Corpus `UnionsTest`: the same over `corpus.unions`, including a nested
  union and a shared group if the case has one; if not, add one shared
  group to the case's records and oracle in this step.
- Golden: `corpus/unions/sbe/<Union>Reader.java`.
- Processor: a declared method that is a field on one member and a
  var-data on another compiles clean and the union reader lacks it
  (asserted on the output text in `AnnotationMistakesTest`'s style).

**Docs.** `type-mappings.md`: "Typed flyweights: unions"; the guide's
`unions.md` gains a section; `glossary.md`.

**Done when** the example and corpus tests are green.

### Closing the increment

In step 6's PR, or a small one after: `intent.md` ticks 26;
`flyweights.md` is deleted and its line in `AGENTS.md` with it, its
content now in `type-mappings.md`, `architecture.md` and the guide (the
"Superseded" section is history, kept by git); `next.md` becomes
increment 27's.

## Rules for every step

- **Byte-identical stays byte-identical.** No step changes the schema
  written, the IR, sbe-tool's flyweights or the codec's bytes. The oracles
  are never edited except in step 6 for a shared group, and that change
  is its own commit.
- **Generated code holds no wire numbers.** Offsets, null values, lengths
  and names come from sbe-tool's flyweights and `JavaUtil`, never
  computed here. A null value is written by calling the flyweight's
  `<field>NullValue()`.
- **Templates hold no conditionals and no loops.** A variant is a
  template; the choice is made in Java. If a templates file passes three
  hundred lines, it is two files by concern.
- **Everything in the model, nothing in the writer.** A writer method
  that decides something from a string is a model component that should
  exist. `Template.fill` refuses unfilled and unused names; keep it so.
- **Determinism.** The same package gives the same sources however it was
  compiled and in either mode; `SchemaRoundTripTest` and `SchemaFirstTest`
  check it, so iterate maps in the model's order, never a `HashMap`'s.
- **Every fact about sbe-tool goes into `notes.md`** with the file it was
  read from, before the code relies on it. Read `reference/`, never copy
  from it.
- **A step's PR description** names the step, what it built, what it
  proved and how, and what the goldens' diff shows.
- `./mvnw spotless:apply` before every commit, `./mvnw verify` before
  every push, no attribution trailers.

## Criteria

- Every message of every corpus case and the example has a reader and a
  writer in `<package>.sbe`; every mapped message's stages have `bound()`.
- `ReaderSequenceTest` agrees with `OtfMessageDecoder` over the whole
  corpus, every frozen version included.
- The corpus round trips are unchanged and green with the codecs written
  over the bound stages.
- A message left incomplete on the writer does not compile; an ignored
  stage on the reader is skipped correctly.
- `TradingBenchmark`'s numbers are recorded, and the codec is not more
  than 10% slower than before, checks on.
- `./mvnw verify` is green after every step.

## Out of scope

`hasFoo()` presence accessors; a `VarData` kind interface; `reader.at(offset)`;
the `byte[]` null image; a generic runtime for `next()` in the api; the
API pass (increment 27). Views over part of a message are gone, not
deferred.
