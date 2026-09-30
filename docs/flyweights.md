# Typed flyweights: the sketch

Increment 26 in `intent.md`: the design of the typed reader as built, and
under Superseded what was built or planned beside it and cut. `next.md`
holds the increment's closing step.

## The problem

sbe-tool's flyweights are a pain to work with, and people get them wrong
all the time. Decoding: groups and var-data must be read in wire order, or
what follows reads garbage; a var-data accessor consumes, so reading it
twice reads what follows it; a group not walked leaves everything after it
misplaced; an optional field is its null value unless compared; a field
above the acting version reads as its null value, silently.

sbe-tool's precedence checks catch the order at run time, opt-in. Its OTF
decoder walks any message in wire order from the IR, at run time, untyped
and per field. The goal here is that the wrong order does not compile.
Encoding has its own traps, and a writer for them was built and cut; see
Superseded.

## The shape

The wire is the default. Every message of the schema gets a reader over
sbe-tool's own flyweights, records or not, generated beside the codecs in
the schema's package, schema-shaped, with sbe-tool's names and faces, so
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
reader is `<Message>Reader`; sbe-tool's `Decoder` is taken, and the
codec's `Codec`, which it sits beside. The root block is `RootBlock`, sbe-tool's
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
- **Every message gets a reader.** Only `bound()` needs a record, so a
  partial package's unmapped messages and a `flyweightsonly` package get
  one too. Reading part of a message is what it is for; a record maps a
  message whole.

## Testing

The corpus's round trips run through the readers' bound stages beside the
codecs, `ReaderAssert` and `BoundStagesTest`. Beside them, sbe-tool's
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
- **Measuring.** The liveness check is a choice a JMH run may reverse
  without touching the API; `sbe-buddy-benchmarks` measures the codecs.
- **Union readers.** A `<Union>Reader` per `@SbeUnion`, dispatching by
  template id as the union codec does, its `Stage` sealed over the
  members', its root block sharing what the interface declares. Planned as
  a step of the increment and not built; a small step of its own if
  wanted.

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
- **The writer.** `<Message>Writer` over sbe-tool's encoder, a typestate
  down to every required field, so an incomplete message did not compile;
  `bound()` twins on its stages; a sub-chain per composite and set. Built
  in two steps and cut: the fill of a block on open cost the codecs written
  over it about half again on encode, and every way out (filling only what
  a chain may skip, a mask, optionals in wire order) gave up either the
  block being well formed while written or the typestate's guarantee, or
  reopened the design; the bound twins doubled the API of every stage; and
  a binding cannot write into a generated encoder, since the encoder does
  not exist when the binding is compiled, so a bound composite allocated its
  face record per call. Writing stays sbe-tool's encoders' in place, and
  the codec's from a record.
- **The codec over the bound stages.** Planned so one implementation held
  every leaf conversion and the corpus proved the flyweights through the
  codecs. It needed the writer, and measured 10 to 15% slower on decode
  and more on encode; cut with the writer. The codec stays as it was, and
  the reader's bound stages share its leaves through `FaceWriter` instead.
