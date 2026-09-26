# Typed flyweights: the sketch

Increment 27 in `intent.md`. Nobody builds from this file
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

A view over sbe-tool's flyweights for every message: shaped by the record
where one maps the message, by the schema where none does. The view owns
the control: it is wrapped over a
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
  how a field the record declares `unmapped` is reached.
- **`wire()` stops at the block.** It has no group or var-data accessor, so
  it cannot move the limit behind the view's back; that is why it is a
  generated type of ours rather than sbe-tool's decoder handed out.
- **It is the garbage-free path** where a binding allocates, a
  `BigDecimal` or an `Instant`.
- **Bindings are lazy.** A bound accessor runs its binding when it is
  called, never when the stage arrives: arriving reads only what finding
  the stage needs, so a stage the `switch` passes by, or reads through
  `wire()` alone, runs no binding. Nothing is cached, as for var-data: two
  calls to `price()` run `PriceBinding` twice. The binding gets the same
  `BindingContext` constant the codec hands it.
- A var-data stage needs no `wire()`: its offsets, `copyTo` and `wrap` are
  the raw access, `value()` the component's type.

## Messages without a record

A partial package's unmapped messages, and every message of a package
that is a `package-info.java` alone, get a view as well, shaped by the
schema: its stages' methods are the schema's fields as sbe-tool's
flyweight hands them over, under the schema's names, since there is no
record's view to set apart from `wire()`. Records for the messages a
package owns, a safe read in place for all the others.

This may leave views, `intent.md`'s increment 26, without a use of their
own. What a view was for, reading a few fields of a large message someone
else owns, the schema-shaped flyweight does without a record to declare
and without allocating. What a view would still add is a value detached
from the buffer, which is a few lines of the user's own over the
flyweight, in the user's own types.

## Unions

A union gets a view of its own beside its codec, over the members' views;
unions are a Java layer over records, and so is their view. Its `Stage` is
sealed over the members' `Stage` interfaces, and its `Root` over the
members' roots, carrying every method they share:

```java
public final class OrderEventView implements Iterable<OrderEventView.Stage>, Iterator<OrderEventView.Stage> {

    public sealed interface Stage
            permits ExecutionReportView.Stage, CancelRejectView.Stage, RejectView.Stage {}

    /** The first stage of every member, and the methods all of them share. */
    public sealed interface Root extends Stage
            permits ExecutionReportView.Root, CancelRejectView.Root, RejectView.Root {
        long senderId();
        String clOrdId();
    }

    public OrderEventView wrap(DirectBuffer buffer, int offset) { ... }  // header read, member picked by template id
    public boolean canWrap(DirectBuffer buffer, int offset) { ... }      // as canDecode: the header alone
    public Root root() { ... }                                           // the first stage, without advancing
    ...
}

// in each member's view
public sealed interface Stage extends OrderEventView.Stage permits Root, Fills, Fill, ... {}
public static final class Root implements Stage, OrderEventView.Root { ... }
```

A dispatcher routes on the shared root without a `case`:

```java
OrderEventView.Root root = events.wrap(buffer, offset).root();
sessions.get(root.senderId()).onMessage(events);
```

and a read goes flat across messages, a whole message in one case or one
part of another:

```java
for (OrderEventView.Stage stage : events.wrap(buffer, offset)) {
    switch (stage) {
        case ExecutionReportView.Fill fill -> filled += fill.quantity();
        case ExecutionReportView.Stage other -> {}
        case CancelRejectView.Root reject -> onCancelReject(reject.orderId());
        case CancelRejectView.Stage other -> {}
        case RejectView.Stage reject -> {}
    }
}
```

- **Dispatch as the codec's.** `wrap` picks the member by template id into
  views the union view preallocated; an id outside the union is the codec's
  `IllegalArgumentException`, and `canWrap` tells it without throwing.
- **Shared by signature, not by id.** A method every member's root stage
  has with the same signature, the component's name and its Java type
  after the binding, is on the union's `Root`, and each member's root
  implements it its own way: one reads `senderId` through a binding,
  another as a plain `long`, each where its own block has it. Nothing on
  the wire is compared; the union never looks at it.
- **Only the root, and only the stage's own methods.** The root is the one
  stage every member begins with, at a place known without walking;
  `wire()` is the schema's view and stays each member's.
- **Change surfaces at compile time.** A member added without `senderId()`,
  or one record renaming its component, takes the method off the union's
  `Root`, and every dispatcher routing on it stops compiling; a member
  added at all adds a permit, and every `switch` over the union's `Stage`
  stops compiling until it is handled.
- **Several unions, nested unions.** A record in two unions has its view's
  `Stage` and `Root` extend both unions'. A nested union's `Root` shares
  what its members share; the outer union's shares what all the leaf
  messages share, and the inner `Root` extends it.
- **The records' own interfaces stay on the records.** Where every member
  implements a `HasSenderId`, the union's `Root` has `senderId()` by the
  intersection, but it does not claim to be one: whoever holds a
  `HasSenderId` expects a value that lasts, and a stage stops answering
  once the view moves on.
- **A schema-shaped view is in no union.** Unions are declared over
  records, where the user wrote them.
- **A header field is shared already.** Every message of the schema has
  the same header, so a routing field there reads before any dispatch.

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
- **Shaped by the record.** A stage's methods are its record's
  components. Absence follows the codec's rules, decided once in
  `CodecWalk`: a sealed `Present | Absent` where the codec would return
  `null`.
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
- **Views.** Whether increment 26 is still wanted, with the
  schema-shaped flyweight reading any message a record does not map.
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
