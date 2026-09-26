# Typed flyweights: the sketch

Increment 27 in `intent.md`. Nobody builds from this file yet; it holds the
shape as it was sketched, so the increment's `next.md` starts from it.
Agents do not read it unless asked to.

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

The wire is the default. Each message gets a view over sbe-tool's own
flyweights, schema-shaped, with sbe-tool's names and faces, so anyone who
knows SBE recognises every method. The view reads the message as a flat
sequence of stages, as a pull parser reads a document: the root block, each
group's header, each of its entries, each var-data. Nesting is the order
the stages come in. The stages are a sealed interface, so a read is one
loop and one flat `switch`.

- **A block is trivial.** A stage's methods are the block's fixed fields,
  each a one-line delegation to sbe-tool's decoder.
- **A group wraps sbe-tool's group decoder.** The header stage holds the
  decoder `decoder.fills()` hands over and answers `count()`; each entry is
  a stage over the same decoder after its `next()`. Order, versions and
  positions stay inside sbe-tool's flyweights; the view only decides which
  stage comes next.
- **Every stage can `skip()`.** The api has one `Stage` interface with
  `skip()`, which each message's sealed `Stage` extends. `skip()` prunes
  what the stage contains, so its stages never come: on the root the rest
  of the message, on a group header its entries, on an entry its nested
  groups and var-data, on a var-data nothing, a no-op.
- **Each stage offers a bound stage.** `bound()` is the record's view of
  the block: every component of the record, under its Java name, with its
  binding applied, lazily, when the method is called, never cached; an
  unbound component is the face itself. A message without a record has no
  `bound()`.

For a root block, a group `fills` whose entries carry a nested group
`allocations`, and var-data `note`:

```
Root, Fills, Fill, Allocations, Allocation, Allocation, Fill, Allocations, Note
```

```java
// generated beside sbe-tool's flyweights, delegating to them
public final class OrderView implements Iterable<OrderView.Stage>, Iterator<OrderView.Stage> {

    public sealed interface Stage extends net.concini.sbebuddy.Stage
            permits Root, Fills, Fill, Allocations, Allocation, Note {}

    private final OrderDecoder decoder = new OrderDecoder();          // sbe-tool's, one per view

    public OrderView wrap(DirectBuffer buffer, int offset) { ... }    // wrapAndApplyHeader, done once
    public Iterator<Stage> iterator() { return this; }                // as sbe-tool's group decoders do
    public boolean hasNext() { ... }
    public Stage next() { ... }                                       // forward; what was left unread is skipped
    public OrderView rewind() { ... }                                 // sbeRewind(): back to the root
    public int decodedLength() { ... }                                // sbeDecodedLength(): at any point

    /** The root block: sbe-tool's fixed fields, delegated one to one. */
    public static final class Root implements Stage {
        public long orderId() { return decoder.orderId(); }
        public Side side() { return decoder.side(); }
        public PriceDecoder price() { return decoder.price(); }
        public RootBound bound() { ... }
        public void skip() { ... }                                    // sbeSkip(): iteration ends
    }

    /** A group's header, around sbe-tool's group decoder. */
    public static final class Fills implements Stage {
        private OrderDecoder.FillsDecoder fills;                      // from decoder.fills(), as sbe-tool hands it
        public int count() { return fills.count(); }
        public void skip() { ... }                                    // next() and sbeSkip() per entry: no Fill comes
    }

    /** One entry: the same group decoder, after its next(). */
    public static final class Fill implements Stage {
        public int index() { ... }
        public long fillId() { return fills.fillId(); }
        public int quantity() { return fills.quantity(); }
        public PriceDecoder price() { return fills.price(); }
        public FillBound bound() { ... }
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
public static final class FillBound {
    public long fillId() { return fill.fillId(); }
    public BigDecimal price() { return PRICE_BINDING.fromWire(fill.price(), FILLS$PRICE); }
    public int quantity() { return fill.quantity(); }
}
```

```java
OrderView view = new OrderView();                  // once; reused for every message

for (OrderView.Stage stage : view.wrap(buffer, offset)) {
    switch (stage) {
        case OrderView.Root root -> orderId = root.orderId();                        // the wire, the default
        case OrderView.Fill fill -> notional = notional.add(fill.bound().price());   // bound where wanted
        case OrderView.Allocations allocations -> allocations.skip();                // no Allocation comes
        case OrderView.Note note -> note.copyTo(audit, position);
        default -> {}                                                                // safe: unread is skipped
    }
}
int length = view.decodedLength();
```

## Settled

- **Order by construction.** The view hands out one stage at a time, and
  its iteration is the only way on. Nested groups need nothing of their
  own: their entries come after the entry holding them.
- **Unread is skipped, `skip()` prunes.** Moving on passes over whatever
  the current stage holds and was not read, through sbe-tool's
  `skip<Data>()` and `sbeSkip()`; an unread var-data costs one addition.
  That is not optional: the view must move past it to reach the next
  stage. `skip()` is the reader's choice on top: what the stage contains
  never comes. `default -> stage.skip()` is a mistake the guide names: the
  group header lands in `default`, is pruned, and the `case` for its
  entries never runs. `default` stays empty.
- **`default` is safe.** With sbe-tool, ignoring a group is how a reader
  reads garbage; here an ignored stage is skipped correctly because the
  view owns the order. A reader after one group writes its cases and a
  `default`, and loses nothing.
- **Evolution at compile time is the reader's option.** A group appended
  later adds a permit. A `switch` that lists every stage stops compiling
  until it is handled; one with a `default` does not, and reads the new
  message correctly. The view is right either way.
- **An empty group is a header and no entries**, as it is on the wire; an
  empty var-data a stage with a length of 0.
- **A group or var-data absent from an older version never comes.** Its
  case simply does not run.
- **A var-data reads again and again.** The stage saves `decoder.limit()`,
  reads through sbe-tool's `get<Data>` and restores the limit; the limit
  moves only when the view does. No prefix is decoded by the view, and
  nothing is cached.
- **The view owns the control.** It is `Iterable` and its own iterator, as
  sbe-tool's group decoders are; `rewind()` and `decodedLength()` delegate
  to sbe-tool's `sbeRewind()` and `sbeDecodedLength()`. There is no end
  stage. A stage carries data only.
- **A stage answers while the view is inside it.** Blocks are addressed by
  offset, so a block stays readable as long as its decoder has not moved
  on: the root for the whole message, an entry while the view is at it or
  inside its nested groups and var-data, a group header while the view is
  inside the group, a var-data while it is current. The view keeps one
  slot per nesting level holding the stage open there, and every accessor,
  the bound stage's included, checks its slot with one reference
  comparison. So a nested entry may read the fields of the entry holding
  it, and a union's common fields answer after the fills were read. An
  entry is one object advanced and answers for the entry its group is on,
  so a reference kept across its group's `next()` reads the new entry, not
  garbage; that is the rule's edge and it is accepted. On by default; a
  JMH run decides whether it needs a `static final` switch, as sbe-tool's
  checks have.
- **Garbage-free.** Stages and bound stages are preallocated fields of the
  view; bindings allocate only where they build an object themselves.
- **Bound stages carry every component of the record,** so a record's user
  never switches between two objects for one block. A group binding over
  the whole collection, `FillsBinding` to a `Map`, has nothing to apply to
  here and is the records' alone; the entries' own bindings apply.
- **Every message gets a view.** Only `bound()` needs a record, so a
  partial package's unmapped messages and a `flyweightsonly` package get
  wire views too.

## Encoding

Writers are a typestate all the way down: every required field, every
group, every var-data is a stage whose only way on is the next one, and
`length()` exists only on the last. A message left incomplete does not
compile.

```java
int length = writer.wrap(buffer, offset)   // RootOrderId: only orderId(long)
        .orderId(42)                       // RootSide: only side(Side)
        .side(Side.BUY)                    // RootPrice: only price()
        .price().mantissa(10025).exponent(-2)
        .clOrdId("abc")                    // Root: the optional fields, any order, and fills()
        .fills()
            .entry().fillId(1).quantity(100).allocations().end()
            .entry().fillId(2).quantity(50).allocations().end()
        .end()                             // AfterFills: only note(...)
        .note("hello")                     // Done: only length()
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
  `PriceWriter<RootClOrdId>` and `exponent(int)` returns `N`, a phantom
  generic, erased, one preallocated object per site. The step also takes
  the face record, `price(Price)`, the allocation being the caller's.
- **Bound writing.** Every wire stage has `bound()` returning its bound
  counterpart, every bound stage `wire()`; it is the same object, so
  hopping is free at any step: `.orderId(42).bound().price(bigDecimal)`
  runs `PRICE_BINDING.toWire`. A composite with a binding is one bound
  step. A group binding over the whole collection has nothing to apply to,
  as on the reader.
- **The length is the writer's,** header included, on `Done` alone.
- **Staleness.** The types make the straight path correct; the run-time
  check is for a kept reference only, a `Root` calling `fills()` after
  `note()`: the same slot per nesting level as the reader, never a check
  for completeness.

## Unions

`OrderEntry` declares `String clOrdId()`, and its members carry `clOrdId`
with the same id and type at different offsets. A common field is one
signature delegated by template id, never one offset. A union's view
dispatches once, at `wrap`, and offers what the interface declares.

```java
OrderEntryView union = new OrderEntryView();                  // members preallocated

OrderEntryView.Member m = union.wrap(buffer, offset);         // by the header's template id
switch (m) {                                                  // sealed over the member views
    case NewOrderView v     -> for (NewOrderView.Stage s : v) { ... }
    case ReplaceOrderView v -> ...
    case CancelOrderView v  -> ...
}

for (OrderEntryView.Stage s : union.wrap(buffer, offset)) {   // or flat, over the union's stages
    switch (s) {
        case OrderEntryView.Root r  -> route(r.bound().clOrdId());
        case OrderEntryView.Party p -> parties.add(p.partyId());        // whichever member, wherever it lies
        case OrderEntryView.Text t  -> t.copyTo(audit, position);
        default -> {}
    }
}
```

- **`wrap` is the codec's dispatch.** A template id outside the union is
  an `IllegalArgumentException`; `canDecode(buffer, offset)` tells it
  first. `Member` is sealed over the member views. A nested annotated
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
  - a **field** on every member's root block: on the union's `Root`, wire
    where the face is the same in every member, bound always, each
    member's binding applied;
  - a **group** on every member: its element record is one record, so the
    entries' block, faces, bindings and nested groups are identical by
    construction; the union has a header stage with `count()` and an
    entry stage with all of it, met wherever the member puts the group;
  - a **var-data** on every member: the wire face is the same whatever
    the length type or encoding, so it is common on both faces, the
    encoding each member's own inside `bound()`.
  - A method that is not one kind on every member yields no union stage;
    the records still satisfy the interface, the view does not share it.
- **The header is common by wire.** `templateId()`, `version()` and
  `blockLength()` sit on every union's `Root`, so a union that declares
  nothing, `TradingMessage`, still has a root that answers what arrived.
- **`Stage` is sealed over the members' stages,** each member's `Stage`
  extending every union's it belongs to, so the flat loop's `switch` lists
  the union's stages and any member's own. Java's exhaustiveness covers a
  member's stages through the union's case.
- **Writers get nothing from the union.** You pick the member's writer;
  the union is a decode-side dispatch, as its codec is.

## Testing

sbe-tool's `OtfMessageDecoder` walks the IR in exactly the view's order,
one level down: `onBeginMessage`, `onGroupHeader`, `onBeginGroup`,
`onVarData` are the root, the header, the entry and the var-data, and
`decodeFields`, `decodeGroups` and `decodeData` are the state machine the
view's `next()` implements over blocks. It is on the test classpath. A
listener that records `(kind, name, groupIndex, length)` against the same
buffer is the oracle for the view's sequence, its skipping and its version
handling, run across every frozen version. The generator derives the
sequence the way the OTF walk does, and `notes.md` cites it.

## Open

- **Structure is the caller's state.** As with SAX, an `Allocation` does
  not say whose it is. The liveness rule lets it read the `Fill` holding
  it, and `index()` covers most of the rest. An end-of-group stage, which
  the OTF decoder keeps as `onEndGroup`, is still not wanted; the oracle
  will show whether readers keep needing it.
- **Presence.** `hasFoo()` beside an optional field, false too above the
  acting version, is the garbage-free shape sbe-tool's users know; a sealed
  `Present | Absent` would allocate per call.
- **Names.** `Fills` and `Fill` need a singular, and sbe-tool has none.
  The element record's name where there is one and `FillsEntry` where not
  is two regimes; `FillsEntry` always keeps sbe-tool's names, as promised.
- **Views.** `intent.md`'s increment 26 looks redundant once every message
  has a wire view and `bound()` per stage, and it conflicts with 25's
  whole-messages rule; 27 would be built over whole records and 26 decided
  after, if at all.
- **Kinds.** A `VarData` interface in the api, `length()`, `copyTo`,
  `wrap`, would let `case VarData v -> v.copyTo(audit, …)` run over any
  message; the other kinds have too little to share. In reserve until an
  audit copier asks.
- **Indexes.** Recorded offsets want a way back in, `view.at(offset)`.
- **Measuring.** The liveness check and the null template are both
  choices a JMH run may reverse without touching the API.
- **Trying it by hand.** The shape wants trying on `trading`'s
  `ExecutionReport`, hand-written over its flyweights in a test, before
  anything is generated; the writer chain three levels deep most of all.

## Superseded

- **A group as one stage with a cursor.** `hasEntry()` and `nextEntry()`,
  each entry a chain of its own: a loop inside a `case` per level of
  nesting. The flat sequence answers it by its order.
- **An end stage and control on the stages.** The view ends iteration and
  knows the length.
- **A stale flag per stage.** An entry is one object advanced, so a flag
  cannot tell the entry kept from the current one; the view's slots can.
- **A stage answers only while current.** Stricter than sbe-tool, which
  addresses blocks by offset, and it cost a nested entry its parent's
  fields and a union its common fields past the root. The slot per nesting
  level is the same one comparison.
- **"The project never writes `default`".** The compile-time evolution
  check is the reader's to take; `default` is safe here, and a reader
  after one group has every reason to write it.
- **Records' view as the default, `wire()` as the extra.** It made the
  flyweight a record that does not allocate, pulled bindings, unions and
  shared roots into the core, and hid sbe-tool's names. The wire is the
  default and `bound()` the extra.
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
- **The OTF decoder as the runtime.** Untyped, every value a `(Token,
  DirectBuffer, index)` read by a switch on the primitive type, fields
  found by name; push and per field, no pruning, the reader inverted into
  a listener; sbe-tool and the IR on the runtime classpath, against
  non-negotiable 7; nothing at compile time, no encoder, nowhere for
  bindings or unions. It is the reference for the sequence and the oracle
  in the tests, and the right tool for a reader of any schema, which the
  guide points to.
