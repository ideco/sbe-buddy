# Typed flyweights: the sketch

Increment 27 in `intent.md`, after views. Nobody builds from this file
yet; it holds the shape as it was sketched, so the increment's `next.md`
starts from it. Agents do not read it unless asked to.

## The problem

A message with groups and var-data must be read in wire order, and
sbe-tool's flyweights do not enforce it: reading `note` before walking
`fills` reads garbage and says nothing, and a var-data accessor consumes,
so reading one twice reads what follows it. sbe-tool's answer is
`sbe.generate.precedence.checks`, a state machine in every flyweight that
throws `Illegal field access order` at run time, opt-in, a check on every
access. The records avoid the problem but allocate the whole message. There
is nothing in between: flyweight speed with record safety.

## The shape

A view over sbe-tool's flyweights, generated from a record or a view, never
from the schema alone. The view owns the control: it is wrapped over a
message, iterated, rewound, and knows the message's length. The message
reads as one flat sequence of stages, as a pull parser reads a document:
the root block, each group's header, each of its entries, each var-data.
Nesting is the order the stages come in. A stage carries data and nothing
else.

For a root block, a group `fills` whose entries carry a nested group
`allocations`, a group `legs` appended in version 2 and var-data `note`:

```
Root, Fills, Fill, Allocations, Allocation, Allocation, Fill, Allocations, Legs, Leg, Leg, Note
```

```java
// generated beside sbe-tool's flyweights, delegating to them
public final class OrderView implements Iterable<OrderView.Stage>, Iterator<OrderView.Stage> {

    public sealed interface Stage permits Root, Fills, Fill, Allocations, Allocation, Legs, Leg, Note {
        int offset();                          // where this stage's own bytes start
        int length();                          // its own bytes: block, dimensions, or prefix and data
    }

    public OrderView wrap(DirectBuffer buffer, int offset) { ... }   // header read; stages preallocated
    public Iterator<Stage> iterator() { return this; }             // as sbe-tool's group decoders do
    public boolean hasNext() { ... }
    public Stage next() { ... }                                     // forward; what was left unread is skipped
    public Root root() { ... }                                      // the typed chain's start
    public OrderView rewind() { ... }                               // back to the root; every stage held is stale
    public int decodedLength() { ... }                              // from any point, consuming nothing

    public static final class Root implements Stage {
        public long orderId() { ... }
        public Side side() { ... }
        public BigDecimal price() { ... }                           // through PriceBinding
        public RootWire wire() { ... }                              // the block as the schema has it
        public Fills following() { ... }
    }

    public static final class Fills implements Stage {              // the group's header
        public int count() { ... }
        public FillsFollowing following() { ... }                   // sealed Fill | Legs | Note
        public FillsSkipped skip() { ... }                          // sealed Legs | Note: past every entry
    }

    public static final class Fill implements Stage {               // one entry; the same object each time
        public int index() { ... }
        public long fillId() { ... }
        public int quantity() { ... }
        public FillWire wire() { ... }
        public Allocations following() { ... }
        public FillSkipped skip() { ... }                           // past its nested groups and var-data
    }

    public static final class Note implements Stage {
        public int dataOffset() { ... }                             // offset() + noteHeaderLength()
        public int dataLength() { ... }                             // 0 for empty
        public String value() { ... }                               // in the schema's encoding; allocates
        public int copyTo(MutableDirectBuffer dst, int dstOffset) { ... }
        public void wrap(DirectBuffer window) { ... }               // zero-copy over the data
        public int appendTo(Appendable out) { ... }                 // ASCII, as sbe-tool offers it
    }
    ...
}
```

The idiomatic read is one loop and one flat, exhaustive `switch`:

```java
OrderView view = new OrderView();                  // once; reused for every message

for (OrderView.Stage stage : view.wrap(buffer, offset)) {
    switch (stage) {
        case OrderView.Root root -> orderId = root.orderId();
        case OrderView.Fills fills -> {}
        case OrderView.Fill fill -> filled += fill.quantity();
        case OrderView.Allocations allocations -> {}
        case OrderView.Allocation allocation -> accounts.add(allocation.account());
        case OrderView.Legs legs -> {}
        case OrderView.Leg leg -> {}
        case OrderView.Note note -> remark = note.value();
    }
}
int length = view.decodedLength();
```

The typed chain stays beside it, from `view.root()`. The generator knows
what can follow each stage, so `following()` and `skip()` return exactly
that, sealed where an empty group or the acting version decides:

```java
OrderView.Root order = view.wrap(buffer, offset).root();
long orderId = order.orderId();
switch (order.following().skip()) {                 // past every fill
    case OrderView.Legs legs -> readLegs(legs);     // a version 2 message
    case OrderView.Note note -> remark = note.value(); // version 1: no legs on the wire
}
```

## Two views of a block

A stage's own methods are the record's view: the components, under their
Java names, their bindings applied. `wire()` is the schema's view: a
preallocated companion with the block's fields as sbe-tool's flyweight
hands them over, primitives, sbe-tool's enums and sets, composite
flyweights, under the schema's names.

```java
case OrderView.Fill fill -> {
    BigDecimal price = fill.price();                    // through PriceBinding
    long mantissa = fill.wire().price().mantissa();     // sbe-tool's composite flyweight
    Instant at = fill.transactTime();                   // through UtcTimestampBinding
    long nanos = fill.wire().transactTime();            // the uint64 on the wire
    short venue = fill.wire().venueRaw();               // a field the record leaves unmapped
}
```

- **No method twice on one type.** The stage carries the record's methods,
  `wire()` the schema's; an unbound component reads alike through both,
  because both the record and the schema have it.
- **`wire()` is the whole block,** not only what the record maps: a fixed
  field sits at a known offset, so any of them is safe to read, and it is
  how a field a view leaves out, or one declared `unmapped`, is reached.
- **`wire()` stops at the block.** It has no group or var-data accessor, so
  it cannot move the limit behind the view's back; that is why it is a
  generated type of ours rather than sbe-tool's decoder handed out.
- **It is the garbage-free path** where a binding allocates, a
  `BigDecimal` or an `Instant`.
- A var-data stage needs no `wire()`: its offsets, `copyTo` and `wrap` are
  the raw access, `value()` the component's type.

## Settled in the sketch

- **Order by construction.** The view hands out one stage at a time, and
  its iteration is the only way on.
- **Nested groups need nothing of their own.** Their entries come after
  the entry holding them, in wire order; the `switch` stays flat however
  deep the schema goes.
- **An empty group is a header and no entries.** It is on the wire, its
  dimensions with a count of 0, so its header is a stage; nothing follows
  it before the next part. An empty var-data is a stage with a
  `dataLength()` of 0, for the same reason.
- **A group or var-data absent from an older version never comes.** It is
  not on the wire. In the flat `switch` its case simply does not run; in
  the typed chain the sealed return makes the older message a case.
- **Evolution at compile time.** A group appended in a later version adds
  a permit to `Stage` and to the sealed returns before it, and every
  `switch` stops compiling until the new case is handled; the project never
  writes `default`.
- **No end stage.** Iteration ends as iteration does, and the length is
  the view's: `decodedLength()` delegates to sbe-tool's
  `sbeDecodedLength()`, which skips to the end, takes the length and
  restores the limit, so it answers at any point.
- **Skipping is local and free.** `skip()` on a header passes the whole
  group, on an entry its nested groups and var-data, and moving on skips
  whatever was left unread. All delegate to sbe-tool's `sbeSkip()`; an
  unread var-data costs one addition, its length already read.
- **Rewind is the view's.** `rewind()` goes back to the root through
  sbe-tool's `sbeRewind()`, as a fresh `wrap` would.
- **A var-data reads again and again.** The stage knows where its prefix
  and its data lie on arrival and reads at those positions, so `value()`,
  `copyTo` and `dataLength()` repeat in any order; the limit moves only
  when the view does. Positions come from sbe-tool's `noteHeaderLength()`,
  no wire numbers of ours. Nothing is cached: `value()` decodes each time.
- **Every stage has its own bytes.** `offset()` and `length()` cover the
  stage's own bytes, known on arrival: the header and root block, a group's
  dimensions, an entry's block, a var-data's prefix and data. A part's
  whole extent, nesting included, is a walk, and `skip()` pays for it: the
  stage after has the end. They serve relays, copying a var-data or an
  entry verbatim, hashing, and diagnostics.
- **A stage answers only while it is current.** The view holds its current
  stage, set by every step, `wrap` and `rewind()`, and `null` once
  iteration ends; every accessor, `wire()`'s included, checks it with one
  reference comparison: `OrderView.Fill is not the current stage; the view
  is at OrderView.Allocation`. An entry is one object advanced, and it
  answers only while it is current again, for the entry it then is, so the
  rule has no gap. It refuses reading the root after moving on, which the
  buffer would allow; the rule stays one sentence, and the idiom reads a
  stage in its own case. On by default; a JMH run decides whether it needs
  sbe-tool's switch, a `static final` flag the JIT folds away.
- **Garbage-free.** Every stage and every `wire()` is a preallocated field
  of the view, an entry the same object advanced; the view is its own
  iterator.
- **Shaped by a record or a view.** Only the components mapped appear as
  the stage's methods; what the view leaves out is skipped, and reachable
  through `wire()` where it is a fixed field. Absence follows the codec's
  rules, decided once in `CodecWalk`: a sealed `Present | Absent` where the
  codec would return `null`.
- **Composites need nothing new.** Fixed size at a known offset, no order
  inside: the stage returns the bound value or the record, `wire()`
  sbe-tool's composite flyweight.

## Open

- **Structure is the caller's state.** As with SAX, an `Allocation` does
  not say whose it is; totalling per fill means holding the current fill's
  values. `index()` on entries, and perhaps `parentIndex()` on nested ones,
  covers most of it. An end-of-group stage would cover the rest at the
  price of a case in every `switch`; left out until something needs it.
- **Names.** `following()` for the typed step, so it does not read as the
  iterator's `next()`; `wire()` over `face()`, the project's own word for
  what sbe-tool's flyweight hands back, because it reads better at the
  call site.
- **Indexes.** Recorded offsets want a way back in, `view.at(offset)`, to
  read an entry found earlier without walking to it again.
- **Encoding.** A group's count is written before its entries: the types
  can order the header after the block and before what follows, but
  "exactly `count` entries" is a run-time check. Decoding first.
- **Trying it by hand.** The shape wants trying on `trading`'s
  `ExecutionReport`, hand-written over its flyweights in a test, before
  anything is generated.

## Superseded

- **A group as one stage with a cursor.** `hasEntry()` and `nextEntry()`,
  each entry a small chain of its own. It read well for one level and grew
  a loop inside a `case` for every level of nesting, and an empty group or
  an entry left half read each needed a rule of its own. The flat sequence
  answers both by its order.
- **An end stage and control on the stages.** `End` carried the length and
  stopped the loop, and every stage had `next()` and `rewind()`. The view
  does both better: iteration ends by itself, `decodedLength()` answers at
  any point, and a stage carries data only.
- **A stale flag per stage.** An entry is one object advanced, so a flag
  cannot tell the entry kept from the current one; the view's current
  stage can.
