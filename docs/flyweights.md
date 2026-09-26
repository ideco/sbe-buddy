# Typed flyweights: the sketch

Increment 26 in `intent.md`: this is the design, `next.md` the build
order. Read both before working on the increment; `next.md` decides where
this file leaves room.

## The problem

sbe-tool's flyweights are a pain to work with, and people get them wrong
all the time. Decoding: groups and var-data must be read in wire order, or
what follows reads garbage; a var-data accessor consumes, so reading it
twice reads what follows it; a group not walked leaves everything after it
misplaced; an optional field is its null value unless compared; a field
above the acting version reads as its null value, silently. Encoding: a
group's count comes before its entries and must match them; every entry
needs its `next()`; an empty group must still be written; groups and
var-data go in schema order; a required field not set leaves whatever the
buffer held; the length is the header plus `encodedLength()`, added by
hand.

sbe-tool's precedence checks catch the order at run time, opt-in. Its OTF
decoder walks any message in wire order from the IR, at run time, untyped
and per field. The goal here is that the wrong order, and a message left
incomplete, do not compile.

## The shape

The wire is the default. Every message of the schema gets a reader and a
writer over sbe-tool's own flyweights, records or not, generated beside
them in `<package>.sbe`, schema-shaped, with sbe-tool's names and faces, so
anyone who knows SBE recognises every method. The reader takes the message
as a flat sequence of stages, as a pull parser reads a document: the root
block, each group's header, each of its entries, each var-data. Nesting is
the order the stages come in. The stages are a sealed interface, so a read
is one loop and one flat `switch`.

- **A block is trivial.** A stage's methods are the block's fixed fields,
  each a one-line delegation to sbe-tool's decoder.
- **A group wraps sbe-tool's group decoder.** The header stage holds the
  decoder `decoder.fills()` hands over and answers `count()`; each entry is
  a stage over the same decoder after its `next()`. Order, versions and
  positions stay inside sbe-tool's flyweights; the reader only decides
  which stage comes next.
- **Every stage can `skip()`.** The api has one `Stage` interface with
  `skip()`, which each message's sealed `Stage` extends. `skip()` prunes
  what the stage contains, so its stages never come: on the root block the
  rest of the message, on a group header its entries, on an entry its
  nested groups and var-data, on a var-data nothing, a no-op.
- **Each stage offers a bound stage where a record maps the message.**
  `bound()` is the record's view of the block: every component of the
  record, under its Java name, with its binding applied, lazily, when the
  method is called, never cached; an unbound component is the face itself.
  Absence is `null`, as on the record: a bound accessor returns `null` for
  the null value and for a field above the acting version, boxed for
  primitives, so a bound optional primitive boxes and the wire face is the
  garbage-free one. A binding needs the whole record; there is no other
  way to declare one. A message without a record has no `bound()`.

For a root block, a group `fills` whose entries carry a nested group
`allocations`, and var-data `note`:

```
RootBlock, Fills, FillsEntry, Allocations, AllocationsEntry, AllocationsEntry, FillsEntry, Allocations, Note
```

```java
// generated beside sbe-tool's flyweights, delegating to them
public final class OrderReader implements Iterable<OrderReader.Stage>, Iterator<OrderReader.Stage> {

    public sealed interface Stage extends net.concini.sbebuddy.Stage
            permits RootBlock, Fills, FillsEntry, Allocations, AllocationsEntry, Note {}

    private final OrderDecoder decoder = new OrderDecoder();          // sbe-tool's, one per reader

    public OrderReader wrap(DirectBuffer buffer, int offset) { ... }  // wrapAndApplyHeader, done once
    public Iterator<Stage> iterator() { return this; }                // as sbe-tool's group decoders do
    public boolean hasNext() { ... }
    public Stage next() { ... }                                       // forward; what was left unread is skipped
    public OrderReader rewind() { ... }                               // sbeRewind(): back to the root block
    public int decodedLength() { ... }                                // sbeDecodedLength(): at any point

    /** The root block: sbe-tool's fixed fields, delegated one to one. */
    public static final class RootBlock implements Stage {
        public long orderId() { return decoder.orderId(); }
        public Side side() { return decoder.side(); }
        public PriceDecoder price() { return decoder.price(); }
        public RootBlockBound bound() { ... }
        public void skip() { ... }                                    // sbeSkip(): iteration ends
    }

    /** A group's header, around sbe-tool's group decoder. */
    public static final class Fills implements Stage {
        private OrderDecoder.FillsDecoder fills;                      // from decoder.fills(), as sbe-tool hands it
        public int count() { return fills.count(); }
        public void skip() { ... }                                    // next() and sbeSkip() per entry: no FillsEntry comes
    }

    /** One entry: the same group decoder, after its next(). */
    public static final class FillsEntry implements Stage {
        public int index() { ... }
        public long fillId() { return fills.fillId(); }
        public int quantity() { return fills.quantity(); }
        public PriceDecoder price() { return fills.price(); }
        public FillsEntryBound bound() { ... }
        public void skip() { ... }                                    // fills.sbeSkip(): its allocations never come
    }

    public static final class Note implements Stage {
        public int length() { ... }                                   // read without consuming
        public int copyTo(MutableDirectBuffer dst, int dstOffset) { ... }
        public void wrap(DirectBuffer window) { ... }                 // zero-copy over the data
        public NoteBound bound() { ... }                              // value(): the record's String or byte[]
        public void skip() {}                                         // nothing to prune
    }
}

/** The record's view of a fill, every component, bindings run on each call. */
public static final class FillsEntryBound {
    public long fillId() { return fill.fillId(); }
    public BigDecimal price() { return PRICE_BINDING.fromWire(fill.price(), FILLS$PRICE); }
    public int quantity() { return fill.quantity(); }
}
```

```java
OrderReader reader = new OrderReader();            // once; reused for every message

for (OrderReader.Stage stage : reader.wrap(buffer, offset)) {
    switch (stage) {
        case OrderReader.RootBlock block -> orderId = block.orderId();                 // the wire, the default
        case OrderReader.FillsEntry fill -> notional = notional.add(fill.bound().price());  // bound where wanted
        case OrderReader.Allocations allocations -> allocations.skip();                // no AllocationsEntry comes
        case OrderReader.Note note -> note.copyTo(audit, position);
        default -> {}                                                                  // safe: unread is skipped
    }
}
int length = reader.decodedLength();
```

## Names

Everything is named from the schema, since a record may not exist:
sbe-tool's `formatClassName` of the schema name, as its flyweights do. The
reader is `<Message>Reader`, the writer `<Message>Writer`, a union's
reader `<Union>Reader`; sbe-tool's `Decoder` and `Encoder` are taken, and
they sit in the same package. The root block is `RootBlock`, sbe-tool's
own term (`HeaderStructure`, `OtfHeaderDecoder`, `sbe.xsd`'s "root level
of message"); a group's header is the group's name, `Fills`, and its entry
`FillsEntry`, since the wire has no singular and one borrowed from a
record would give two regimes. A var-data is its name, `Note`. Bound
stages take the suffix, `RootBlockBound`, `FillsEntryBound`. `Stage`,
`Member`, `RootBlock` and `Entry` as a suffix are reserved: a group or
var-data that would clash is an error on its node, and so is any other name
the reader could not compile with, which `type-mappings.md` lists.

## Settled

- **Order by construction.** The reader hands out one stage at a time, and
  its iteration is the only way on. Nested groups need nothing of their
  own: their entries come after the entry holding them.
- **Structure is the counts.** A reader that wants nesting drives `next()`
  itself: after `Fills` come `count()` entries, each followed by its own
  nested headers and their counts, and the casts are correct by
  construction because the grammar is the schema's. That is how the codec
  reads (below), and how anyone else can. One that does not want it
  switches flat; `index()` on an entry says where it is. No end stage.
- **Unread is skipped, `skip()` prunes.** Moving on passes over whatever
  the current stage holds and was not read, through sbe-tool's
  `skip<Data>()` and `sbeSkip()`; a var-data is passed over the moment its
  stage arrives, one addition, its start and length kept for reading.
  That is not optional: the reader must move past it to reach the next
  stage. `skip()` is the reader's choice on top: what the stage contains
  never comes, and only the current stage can be asked.
  `default -> stage.skip()` is a mistake the guide names: the
  group header lands in `default`, is pruned, and the `case` for its
  entries never runs. `default` stays empty.
- **`default` is safe.** With sbe-tool, ignoring a group is how a reader
  reads garbage; here an ignored stage is skipped correctly because the
  reader owns the order. A reader after one group writes its cases and a
  `default`, and loses nothing.
- **Evolution at compile time is the reader's option.** A group appended
  later adds a permit. A `switch` that lists every stage stops compiling
  until it is handled; one with a `default` does not, and reads the new
  message correctly. The reader is right either way.
- **An empty group is a header and no entries**, as it is on the wire; an
  empty var-data a stage with a length of 0.
- **A group or var-data absent from an older version never comes.** Its
  case simply does not run.
- **A var-data reads again and again.** The stage knows where it starts
  from its arrival; a read sets the limit there, goes through sbe-tool's
  `get<Data>` or `wrap<Data>` and restores the limit. No prefix is
  decoded by the reader, and nothing is cached but the start and the
  length.
- **The reader owns the control.** It is `Iterable` and its own iterator,
  as sbe-tool's group decoders are; `rewind()` and `decodedLength()`
  delegate to sbe-tool's `sbeRewind()` and `sbeDecodedLength()`. A stage
  carries data only.
- **A stage answers while the reader is inside it.** Blocks are addressed
  by offset, so a block stays readable as long as its decoder has not
  moved on: the root block for the whole message, an entry while the
  reader is at it or inside its nested groups and var-data, a group header
  while the reader is inside the group, a var-data while it is current.
  The reader keeps its position as one enum in wire order, and a stage is
  open while the position lies in its range, which every accessor, the
  bound stage's included, checks first. So a nested entry may read the fields of
  the entry holding it, and a union's common fields answer after the fills
  were read. An entry is one object advanced and answers for the entry its
  group is on, so a reference kept across its group's `next()` reads the
  new entry, not garbage; that is the rule's edge and it is accepted. On
  by default; a JMH run decides whether it needs a `static final` switch,
  as sbe-tool's checks have.
- **Garbage-free.** Stages and bound stages are preallocated fields of the
  reader; bindings allocate only where they build an object themselves,
  and bound optionals box, as the record does.
- **Bound stages carry every component of the record,** so a record's user
  never switches between two objects for one block. A group binding over
  the whole collection, `FillsBinding` to a `Map`, has nothing to apply to
  here and is the records' alone; the entries' own bindings apply.
- **Every message gets a reader and a writer.** Only `bound()` needs a
  record, so a partial package's unmapped messages and a `flyweightsonly`
  package get them too. Reading part of a message is what they are for; a
  record maps a message whole.

## Encoding

Writers are a typestate all the way down: every required field, every
group, every var-data is a stage whose only way on is the next one, and
`length()` exists only once the last mandatory step is taken. A message
left incomplete does not compile.

```java
int length = writer.wrap(buffer, offset)   // only orderId(long)
        .orderId(42)                       // only side(Side)
        .side(Side.BUY)                    // only price()
        .price().mantissa(10025).exponent(-2)
        .clOrdId("abc")                    // the block complete: optional fields, any order, and fills()
        .fills()
            .entry().fillId(1).quantity(100).allocations().end()
            .entry().fillId(2).quantity(50).allocations().end()
        .end()                             // only note(...)
        .note("hello")                     // only length()
        .length();                         // header included
```

- **Required fields in the record's order.** Each setter returns the stage
  that has only the next required field; the order is the block's
  `layout`, the one a reader sees. A required field appended in a later
  version is a new link, and every writer stops compiling until it sets
  it, the twin of the reader's new permit; `sinceVersion` order is the
  chain's order. Optional fields sit on the block-complete stage, in any
  order, beside the first group or var-data. This is the one place the
  types are stricter than the wire, which does not care in what order a
  block's fields are written; the guide says so.
- **One object, many interfaces.** All of a block's stages are one
  preallocated object implementing every stage interface of the block;
  each setter does `return this` typed as the next. Nothing is allocated
  per step, and a stage can be held and resumed later: entries as they
  arrive, a header now and the trailer when it is known.
- **The length is where the message is complete.** The last mandatory
  step returns the stage that has `length()`, header included: the
  block-complete stage when a block comes last, else the stage after the
  last group's `end()` or the last var-data, which the generator produces
  anyway as their return type. There is no stage named for it; a stage
  carries data.
- **Optional fields are filled on open.** When a block's stage opens, its
  null image, a `static final byte[]` known at generation, is written with
  one `putBytes`, or one per run of contiguous optional fields where a
  wide block has few. Required bytes are overwritten by the chain; unset
  optionals are their null values, as non-negotiable 4 wants, and the
  block is well formed at every point while it is written, which matters
  under `tryClaim`. Filling at the switch instead needs a mask updated per
  setter; the switch is one generated call either way, so a JMH run can
  change the answer without touching the API.
- **No count up front.** sbe-tool's group encoders have
  `resetCountToIndex()`, which rewrites the count at the group's initial
  limit to the entries written; the group opens with room for its maximum
  and `end()` settles it.
- **No group left out.** The only way to `note` is through `fills`; a
  nested group is opened and closed per entry even when empty,
  `.allocations().end()`, which is SBE's rule made visible.
- **Composites chain through.** `Price` appears in many messages, so its
  chain's last step must return the caller's next stage: `price()` returns
  `PriceWriter<N>` and `exponent(int)` returns `N`, a phantom generic,
  erased, one preallocated object per site. The step also takes the face
  record, `price(Price)`, the allocation being the caller's.
- **Bound writing.** Every wire stage that takes a value has `bound()`
  returning its bound twin, every bound stage `wire()`; the twin is a
  second preallocated object per block, not the same one, because the
  wire and bound setters of a `String` field share a signature and differ
  only in what they return, which one class cannot implement twice. Hopping
  is still free at any step: `.orderId(42).bound().price(bigDecimal)`
  runs `PRICE_BINDING.toWire`. A composite with a binding is one bound
  step. A group binding over the whole collection has nothing to apply to,
  as on the reader.
- **Staleness.** The types make the straight path correct; the run-time
  check is for a kept reference only, a block-complete stage calling
  `fills()` after `note()`: the writer's position, as the reader's, never
  a check for completeness.

## The codec over them

The codec is rewritten over the bound stages, so what a component is on
the wire is decided in one place. Today `NewOrderCodec` is four hundred
lines of what the bound stages are: enum and set mapping, composite face
records, bindings with their `BindingContext`, `char` strings and arrays,
absence to `null`. All of that moves into the flyweight emitter; the
codec keeps the record: its constructor and the component order against
the wire order, lists for groups, `encodedLength(value)` without writing
as non-negotiable 6 wants, the header record in `encode(value, header,
…)` and `decodeHeader`, `canDecode`, `lastDecodedLength`.
`decodedLength(buffer, offset)` is the reader's after `wrap`.

```java
RootBlock block = (RootBlock) reader.wrap(buffer, offset).next();
Fills fills = (Fills) reader.next();
for (int i = 0; i < fills.count(); i++) {
    FillsEntry fill = (FillsEntry) reader.next();
    Allocations allocations = (Allocations) reader.next();
    ...                                                        // the same, one level down
    fillList.add(new Fill(fill.bound().fillId(), fill.bound().price(), allocationList));
}
Note note = (Note) reader.next();
return new Order(block.bound().orderId(), ..., fillList, note.bound().value());
```

`encode(value)` is the writer's bound chain, a loop of `.entry().bound()`
over each group's list, and the length its last step returns. The union
codec does not change: it composes member codecs by template id, and the
union reader is not on its path. `CodecModel`'s leaf shapes become the
emitter's; the codec model shrinks to structure.

The point is proof as much as economy: the corpus, every case, every
frozen version, both directions, against the oracles, runs through the
codecs. Rewriting them over the flyweights makes the whole suite prove the
flyweights in the same commit, with the round trips as the criterion that
nothing moved. A JMH run against the current codec decides whether the
liveness check needs its switch.

## Unions

`OrderEntry` declares `String clOrdId()`, and its members carry `clOrdId`
with the same id and type at different offsets. A common field is one
signature delegated by template id, never one offset. A union's reader
dispatches once, at `wrap`, and offers what the interface declares.

```java
OrderEntryReader union = new OrderEntryReader();              // members preallocated

OrderEntryReader.Member m = union.wrap(buffer, offset);       // by the header's template id
switch (m) {                                                  // sealed over the member readers
    case NewOrderReader r     -> for (NewOrderReader.Stage s : r) { ... }
    case ReplaceOrderReader r -> ...
    case CancelOrderReader r  -> ...
}

for (OrderEntryReader.Stage s : union.wrap(buffer, offset)) { // or flat, over the union's stages
    switch (s) {
        case OrderEntryReader.RootBlock b     -> route(b.bound().clOrdId());
        case OrderEntryReader.PartiesEntry p  -> parties.add(p.partyId());   // whichever member, wherever it lies
        case OrderEntryReader.Text t          -> t.copyTo(audit, position);
        default -> {}
    }
}
```

- **`wrap` is the codec's dispatch.** A template id outside the union is
  an `IllegalArgumentException`; `canDecode(buffer, offset)` tells it
  first. `Member` is sealed over the member readers. A nested annotated
  union's `Member` is a permitted subtype of the outer's, so a `switch`
  over `TradingMessage` takes `OrderEntry` as one case or its leaves, as
  the record union's javadoc promises; an unannotated interface between
  flattens, as in the codec.
- **Commonality is declared, not inferred.** The sealed interface's
  abstract methods are the declaration, and javac has already checked
  that every member implements them. Each method that names, on every
  member's record, a component of one kind becomes a union stage
  interface, sealed over the members' corresponding stages, which
  implement it:
  - a **field** on every member's root block: on the union's `RootBlock`,
    wire where the face is the same in every member, bound always, each
    member's binding applied;
  - a **group** on every member: its element record is one record, so the
    entries' block, faces, bindings and nested groups are identical by
    construction; the union has a header stage with `count()` and an
    entry stage with all of it, met wherever the member puts the group;
  - a **var-data** on every member: the wire face is the same whatever
    the length type or encoding, so it is common on both faces, the
    encoding each member's own inside `bound()`.
  - A method that is not one kind on every member yields no union stage;
    the records still satisfy the interface, the reader does not share it.
- **The header is common by wire.** `templateId()`, `version()` and
  `blockLength()` sit on every union's `RootBlock`, so a union that
  declares nothing, `TradingMessage`, still has a root block that answers
  what arrived.
- **`Stage` is sealed over the members' stages,** each member's `Stage`
  extending every union's it belongs to, so the flat loop's `switch` lists
  the union's stages and any member's own. Java's exhaustiveness covers a
  member's stages through the union's case.
- **Writers get nothing from the union.** You pick the member's writer;
  the union is a decode-side dispatch, as its codec is.

## Testing

The corpus through the codecs is the proof, above. Beside it, sbe-tool's
`OtfMessageDecoder` walks the IR in exactly the reader's order, one level
down: `onBeginMessage`, `onGroupHeader`, `onBeginGroup`, `onVarData` are
the root block, the header, the entry and the var-data, and
`decodeFields`, `decodeGroups` and `decodeData` are the state machine the
reader's `next()` implements over blocks. It is on the test classpath. A
listener that records `(kind, name, groupIndex, length)` against the same
buffer is the oracle for the reader's sequence, its skipping and its
version handling, run across every frozen version, for the readers of
messages no record maps, which the codecs never touch. The generator
derives the sequence the way the OTF walk does, and `notes.md` cites it.

## Open

- **Presence.** `hasFoo()` beside an optional field, false too above the
  acting version, is the garbage-free shape sbe-tool's users know; a sealed
  `Present | Absent` would allocate per call, and `bound()` boxes.
- **A `String` on the wire var-data stage.** It has `length()`, `copyTo`
  and `wrap` only; the `String` is the record's, on `bound().value()`. But
  sbe-tool's own face has `text()` where the schema names a
  `characterEncoding`, and a reader of a message with no record has no
  other way to a `String` than `copyTo` and `new String`. A `value()` on
  the wire stage, in the schema's encoding, allocating as sbe-tool's does,
  may be wanted; step 0 left it out.
- **Kinds.** A `VarData` interface in the api, `length()`, `copyTo`,
  `wrap`, would let `case VarData v -> v.copyTo(audit, …)` run over any
  message; the other kinds have too little to share. In reserve until an
  audit copier asks.
- **Indexes.** Recorded offsets want a way back in, `reader.at(offset)`.
- **Measuring.** The liveness check and the null template are both
  choices a JMH run may reverse without touching the API.
- **Trying it by hand.** The shape wants trying on `trading`'s
  `ExecutionReport`, hand-written over its flyweights in a test, before
  anything is generated; the writer chain three levels deep most of all.

## Superseded

- **A group as one stage with a cursor.** `hasEntry()` and `nextEntry()`,
  each entry a chain of its own: a loop inside a `case` per level of
  nesting. The flat sequence answers it by its order.
- **An end stage and control on the stages.** The reader ends iteration
  and knows the length; the counts give the structure to whoever wants it.
- **A stage answers only while current.** Stricter than sbe-tool, which
  addresses blocks by offset, and it cost a nested entry its parent's
  fields and a union its common fields past the root block. Under that
  rule a flag per stage could not tell the entry kept from the current
  one; under liveness that edge is accepted, and the flag is the rule's
  whole mechanism.
- **"The project never writes `default`".** The compile-time evolution
  check is the reader's to take; `default` is safe here, and a reader
  after one group has every reason to write it.
- **Records' view as the default, `wire()` as the extra.** It made the
  flyweight a record that does not allocate, pulled bindings, unions and
  shared roots into the core, and hid sbe-tool's names. The wire is the
  default and `bound()` the extra.
- **Views: a record over part of a message, decode-only.** The wire
  reader reads part of a message for anyone, and a partial record would
  have broken 25's rule that a record maps a message whole. Bindings need
  the whole record. The increment went.
- **`View` as the name, and `Root`.** "View" meant the partial record;
  `Reader` and `Writer` name the pair. `Root` was half of sbe-tool's
  phrase; the block is the unit, and `RootBlock` says which.
- **A `Done` stage for `length()`.** A name for a return type, carrying no
  data; the last mandatory step's return type has `length()`.
- **A builder for a block's fields.** Setters in any order on one stage:
  not exhaustive, a required field left out is a mistake the types cannot
  see. The typestate down to the required field is.
- **Required fields as parameters,** `entry(fillId, quantity, price)`:
  exhaustive and unreadable at `ExecutionReport`'s width; the chain is the
  parameter list, named, one per line.
- **Filling unset optionals at the switch, or at `length()`.** `length()`
  is too late for entries, whose offsets are gone; the switch works but
  needs a mask per block updated in every setter. The null template on
  open needs nothing per setter.
- **Commonality by field id.** Would work without a record, but infers,
  and a union is over records anyway; the interface declares it.
- **A kind interface per stage kind,** `Block`, `Entry`, `Group`,
  `VarData`, for skipping generically. `skip()` on `Stage` covers the
  pruning; only `VarData` has a face worth sharing, kept in reserve.
- **The codec and the flyweights side by side.** Two generated copies of
  every leaf conversion, and the flyweights proved only by tests of their
  own. The codec over the bound stages has one copy and the corpus.
- **The OTF decoder as the runtime.** Untyped, every value a `(Token,
  DirectBuffer, index)` read by a switch on the primitive type, fields
  found by name; push and per field, no pruning, the reader inverted into
  a listener; sbe-tool and the IR on the runtime classpath, against
  non-negotiable 7; nothing at compile time, no encoder, nowhere for
  bindings or unions. It is the reference for the sequence and an oracle
  in the tests, and the right tool for a reader of any schema, which the
  guide points to.
