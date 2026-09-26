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
var-data go in schema order; the length is the header plus
`encodedLength()`, added by hand.

sbe-tool's precedence checks catch the order at run time, opt-in. The goal
here is that the wrong order does not compile.

## The shape

The wire is the default. Each message gets a view over sbe-tool's own
flyweights, schema-shaped, with sbe-tool's names and faces, so anyone who
knows SBE recognises every method. The view reads the message as a flat
sequence of stages, as a pull parser reads a document: the root block, each
group's header, each of its entries, each var-data. Nesting is the order
the stages come in. The stages are a sealed interface, so a read is one
loop and one flat, exhaustive `switch`.

- **A block is trivial.** A stage's methods are the block's fixed fields,
  each a one-line delegation to sbe-tool's decoder.
- **A group wraps sbe-tool's group decoder.** The header stage holds the
  decoder `decoder.fills()` hands over and answers `count()`; each entry is
  a stage over the same decoder after its `next()`. Order, versions and
  positions stay inside sbe-tool's flyweights; the view only decides which
  stage comes next.
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

    public sealed interface Stage permits Root, Fills, Fill, Allocations, Allocation, Note {}

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
    }

    /** A group's header, around sbe-tool's group decoder. */
    public static final class Fills implements Stage {
        private OrderDecoder.FillsDecoder fills;                      // from decoder.fills(), as sbe-tool hands it
        public int count() { return fills.count(); }
    }

    /** One entry: the same group decoder, after its next(). */
    public static final class Fill implements Stage {
        public long fillId() { return fills.fillId(); }
        public int quantity() { return fills.quantity(); }
        public PriceDecoder price() { return fills.price(); }
        public FillBound bound() { ... }
    }

    public static final class Note implements Stage {
        public int length() { ... }                                   // read without consuming
        public int copyTo(MutableDirectBuffer dst, int dstOffset) { ... }
        public void wrap(DirectBuffer window) { ... }                 // zero-copy over the data
        public NoteBound bound() { ... }                              // value(): the record's String or byte[]
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
        case OrderView.Fills fills -> {}
        case OrderView.Fill fill -> notional = notional.add(fill.bound().price());   // bound where wanted
        case OrderView.Allocations allocations -> {}
        case OrderView.Allocation allocation -> {}
        case OrderView.Note note -> note.copyTo(audit, position);
    }
}
int length = view.decodedLength();
```

## Settled

- **Order by construction.** The view hands out one stage at a time, and
  its iteration is the only way on. Nested groups need nothing of their
  own: their entries come after the entry holding them.
- **Skipping is free.** Moving on skips whatever was left unread, through
  sbe-tool's `sbeSkip()`. An unread var-data costs one addition.
- **An empty group is a header and no entries**, as it is on the wire; an
  empty var-data a stage with a length of 0.
- **A group or var-data absent from an older version never comes.** Its
  case simply does not run.
- **Evolution at compile time.** A group appended later adds a permit, and
  every `switch` stops compiling until it is handled; the project never
  writes `default`.
- **A var-data reads again and again.** The stage knows where its prefix
  and data lie on arrival, from sbe-tool's `noteHeaderLength()`, and reads
  there; the limit moves only when the view does. Nothing is cached.
- **The view owns the control.** It is `Iterable` and its own iterator, as
  sbe-tool's group decoders are; `rewind()` and `decodedLength()` delegate
  to sbe-tool's `sbeRewind()` and `sbeDecodedLength()`. There is no end
  stage. A stage carries data only.
- **A stage answers only while it is current.** The view holds its current
  stage, set by every step, `wrap` and `rewind()`, `null` once iteration
  ends; every accessor, the bound stage's included, checks it with one
  reference comparison. An entry is one object advanced and answers only
  while it is current again, for the entry it then is, so the rule has no
  gap. On by default; a JMH run decides whether it needs a `static final`
  switch, as sbe-tool's checks have.
- **Garbage-free.** Stages and bound stages are preallocated fields of the
  view; bindings allocate only where they build an object themselves.
- **Bound stages carry every component of the record,** so a record's user
  never switches between two objects for one block. A group binding over
  the whole collection, `FillsBinding` to a `Map`, has nothing to apply to
  here and is the records' alone; the entries' own bindings apply.

## Encoding

Writers belong here too, and need care. The same staged shape over
sbe-tool's encoders:

```java
int length = writer.wrap(buffer, offset)           // header written
        .orderId(42).side(Side.BUY)
        .fills()
            .entry().fillId(1).quantity(100)       // the encoder's next(), per entry
            .entry().fillId(2).quantity(50)
        .end()                                     // resetCountToIndex(): the count becomes 2
        .note("hello")                             // reachable only after fills is closed
        .length();                                 // header included
```

- **No count up front.** sbe-tool's group encoders have
  `resetCountToIndex()`, which rewrites the count to the entries written;
  the group opens with room for its maximum and `end()` settles it.
- **No group left out.** The only way to `note` is through `fills`; a group
  closed with no entry is written empty.
- **The length is the writer's,** header included.

The care:

- **Nested groups inside an entry.** `entry()` returns the entry's writer,
  its nested groups reachable after its block, and the entry's end returns
  to the parent group; the chain must stay readable three levels deep.
- **Fields left unwritten.** sbe-tool writes nothing for a field not set,
  so a block keeps whatever the buffer held. Either every block is filled
  with its null values when the stage opens, or a required field not set
  is a mistake the types cannot see; which, and at what cost.
- **Bound writing.** `bound()` on a writer stage, taking the record's types
  through the bindings' `toWire`.
- **Staleness.** The same current-stage rule, the writer's stages kept past
  their `end()`.

## Open

- **Structure is the caller's state.** As with SAX, an `Allocation` does
  not say whose it is. `index()` on entries, and perhaps `parentIndex()`
  on nested ones, covers most of it; an end-of-group stage would cover the
  rest at the price of a case in every `switch`.
- **Presence.** `hasFoo()` beside an optional field, false too above the
  acting version, is the garbage-free shape sbe-tool's users know; a sealed
  `Present | Absent` would allocate per call.
- **Unions.** A union's view picks its member's view by template id, as
  the union codec does, with a sealed `Stage` over the members'. Sharing
  across members, a dispatcher routing on a field every member has, belongs
  on the bound stages, where the Java signatures live: by signature, only
  on the root, the records' own interfaces staying on the records.
- **Views.** Whether `intent.md`'s increment 26 is still wanted, with every
  message readable in place, record or not.
- **Indexes.** Recorded offsets want a way back in, `view.at(offset)`.
- **Trying it by hand.** The shape wants trying on `trading`'s
  `ExecutionReport`, hand-written over its flyweights in a test, before
  anything is generated.

## Superseded

- **A group as one stage with a cursor.** `hasEntry()` and `nextEntry()`,
  each entry a chain of its own: a loop inside a `case` per level of
  nesting. The flat sequence answers it by its order.
- **An end stage and control on the stages.** The view ends iteration and
  knows the length.
- **A stale flag per stage.** An entry is one object advanced, so a flag
  cannot tell the entry kept from the current one; the view's current
  stage can.
- **Records' view as the default, `wire()` as the extra.** It made the
  flyweight a record that does not allocate, pulled bindings, unions and
  shared roots into the core, and hid sbe-tool's names. The wire is the
  default and `bound()` the extra.
