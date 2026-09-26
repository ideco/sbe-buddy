# Typed flyweights: the sketch

Increment 27 in `intent.md`, after views. Nobody builds from this file
yet; it holds the shape as it was sketched, so the increment's `next.md`
starts from it. Agents do not read it unless asked to.

## The problem

A message with groups and var-data must be read in wire order, and
sbe-tool's flyweights do not enforce it: reading `note` before walking
`fills` reads garbage and says nothing. sbe-tool's answer is
`sbe.generate.precedence.checks`, a state machine in every flyweight that
throws `Illegal field access order` at run time, opt-in, a check on every
access. The records avoid the problem but allocate the whole message. There
is nothing in between: flyweight speed with record safety.

## The shape: a flat sequence of stages

A view over sbe-tool's flyweights, generated from a record or a view, never
from the schema alone. The message reads as one flat sequence of stages, as
a pull parser reads a document: the root block, each group's header, each
of its entries, each var-data, the end. Nesting is the order the stages
come in. For a root block, a group `fills` whose entries carry a nested
group `allocations`, a group `legs` appended in version 2 and var-data
`note`:

```
Root, Fills, Fill, Allocations, Allocation, Allocation, Fill, Allocations, Legs, Leg, Leg, Note, End
```

```java
// generated beside sbe-tool's flyweights, delegating to them
public final class OrderView {

    public sealed interface Stage
            permits Root, Fills, Fill, Allocations, Allocation, Legs, Leg, Note, End {
        /** Forward: into a group's entries, then on; whatever was left unread is skipped. */
        Stage next();
        /** Back to the root block, as a fresh wrap would be; every stage held since is stale. */
        Root rewind();
    }

    public Root wrap(DirectBuffer buffer, int offset) { ... }   // header read; stages preallocated

    public static final class Root implements Stage {
        public long orderId() { ... }
        public Side side() { ... }
        public PriceDecoder price() { ... }        // sbe-tool's composite flyweight, or bound
    }

    public static final class Fills implements Stage {          // the group's header
        public int count() { ... }
        public Stage skip() { ... }                              // past every entry, to what follows the group
    }

    public static final class Fill implements Stage {           // one entry; the same object each time
        public int index() { ... }
        public long fillId() { ... }
        public int quantity() { ... }
        public Stage skip() { ... }                              // past its nested groups and var-data
    }

    public static final class Note implements Stage { public String note() { ... } }
    public static final class End implements Stage { public int encodedLength() { ... } }
    ...
}
```

The idiomatic read is one loop and one flat, exhaustive `switch`:

```java
OrderView view = new OrderView();                  // once; reused for every message

for (OrderView.Stage stage = view.wrap(buffer, offset); !(stage instanceof OrderView.End); stage = stage.next()) {
    switch (stage) {
        case OrderView.Root root -> orderId = root.orderId();
        case OrderView.Fills fills -> {}
        case OrderView.Fill fill -> filled += fill.quantity();
        case OrderView.Allocations allocations -> {}
        case OrderView.Allocation allocation -> accounts.add(allocation.account());
        case OrderView.Legs legs -> {}
        case OrderView.Leg leg -> {}
        case OrderView.Note note -> remark = note.note();
        case OrderView.End end -> {}
    }
}
```

The typed chain stays beside it. The generator knows what can follow each
stage, so `next()` and `skip()` narrow their return types covariantly
where that helps: `Root.next()` returns `Fills`; `Fills.next()` a sealed
`Fill | Legs | Note`, since the group may be empty and `legs` may be
absent, and `Fills.skip()` a sealed `Legs | Note`; `Fill.next()` returns
`Allocations`. The flat `switch` is the common read, the precise
types there for code that steps.

```java
OrderView.Root order = view.wrap(buffer, offset);
long orderId = order.orderId();
switch (order.next().skip()) {                      // past every fill
    case OrderView.Legs legs -> readLegs(legs);     // a version 2 message
    case OrderView.Note note -> remark = note.note(); // version 1: no legs on the wire
}
```

## Settled in the sketch

- **Order by construction.** Only the current stage is held, and `next()`
  is the only way on.
- **Nested groups need nothing of their own.** Their entries come after
  the entry holding them, in wire order; the `switch` stays flat however
  deep the schema goes.
- **An empty group is a header and no entries.** It is on the wire, its
  dimensions with a count of 0, so its header is a stage; nothing follows
  it before the next part. Nothing is skipped and nothing is special.
- **A group absent from an older version never comes.** It is not on the
  wire. In the flat `switch` its case simply does not run; in the typed
  chain the sealed return makes the older message a case.
- **Evolution at compile time.** A group appended in a later version adds
  a permit to `Stage` and to the sealed returns before it, and every
  `switch` stops compiling until the new case is handled; the project never
  writes `default`.
- **Skipping is local and free.** `skip()` on a header passes the whole
  group, on an entry its nested groups and var-data, and `next()` skips
  whatever was left unread. All delegate to sbe-tool's generated
  `sbeSkip()`; no wire logic of ours.
- **Rewind returns the root.** `rewind()` on any stage hands back the root,
  as a fresh `wrap` would, rather than changing what `next()` returns, so
  every type stays true. It delegates to sbe-tool's `sbeRewind()`, which
  `sbeSkip()` itself calls.
- **Garbage-free.** Every stage is a preallocated field of the view, an
  entry the same object advanced; `wrap`, `next()` and `rewind()` return
  existing objects.
- **Shaped by a record or a view.** Only the components mapped appear,
  under their Java names; what the view leaves out is skipped. Accessors go
  through the components' bindings, `price()` a `BigDecimal` where a
  binding says so. Absence follows the codec's rules, decided once in
  `CodecWalk`: a sealed `Present | Absent` where the codec would return
  `null`.
- **Composites need nothing new.** Fixed size at a known offset, no order
  inside: a stage returns sbe-tool's composite flyweight, or the bound
  value. The new types are the stages, the structure between blocks.

## Open

- **Structure is the caller's state.** As with SAX, an `Allocation` does
  not say whose it is; totalling per fill means holding the current fill.
  `index()` on entries, and perhaps `parentIndex()` on nested ones, covers
  most of it. An end-of-group stage would cover the rest at the price of a
  case in every `switch`; left out until something needs it.
- **Stale stages.** Java has no linear types: a stage kept after `next()`
  still answers, and inside a group it answers with the next entry's
  values, since the entry is one object advanced. A generation counter,
  raised by `next()` and `rewind()` and checked per stage, would catch it,
  at about the cost of sbe-tool's precedence checks.
- **Encoding.** A group's count is written before its entries: the types
  can order the header after the block and before what follows, but
  "exactly `count` entries" is a run-time check. Decoding first.
- **Trying it by hand.** The shape wants trying on `trading`'s
  `ExecutionReport`, hand-written over its flyweights in a test, before
  anything is generated.

## The first sketch, superseded

A group was one stage with a cursor over its entries, `hasEntry()` and
`nextEntry()`, and each entry a small chain of its own. It read well for one
level and grew a loop inside a `case` for every level of nesting, and an
empty group or an entry left half read each needed a rule of its own. The
flat sequence answers both by its order.
