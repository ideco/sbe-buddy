# Typed flyweights: the sketch

Increment 27 in `intent.md`, after views. Nobody builds from this file
yet; it holds the shape as it was first sketched, so the increment's
`next.md` starts from it. Agents do not read it unless asked to.

## The problem

A message with groups and var-data must be read in wire order, and
sbe-tool's flyweights do not enforce it: reading `note` before walking
`fills` reads garbage and says nothing. sbe-tool's answer is
`sbe.generate.precedence.checks`, a state machine in every flyweight that
throws `Illegal field access order` at run time, opt-in, a check on every
access. The records avoid the problem but allocate the whole message. There
is nothing in between: flyweight speed with record safety.

## The shape

A view over sbe-tool's flyweights, generated from a record or a view, never
from the schema alone. Each block, group entry and var-data is a stage; a
stage offers its own accessors and a `next()` that returns the stage after
it, so the only way to a stage is through the ones before it and wire order
holds by construction. Sealed interfaces appear where the bytes decide what
follows: a group appended in a later version is absent from an older
message.

The example: a root block, a group `fills` whose entries carry a nested
group `allocations`, a group `legs` appended in version 2, var-data `note`.

```java
// generated beside sbe-tool's flyweights, delegating to them
public final class OrderView {

    /** One part of the message, in wire order. */
    public sealed interface Stage permits Root, Fills, Legs, Note, End {
        /** Forward only: whatever this stage left unread is skipped. */
        Stage next();
    }

    /** What can follow fills: legs from version 2, the note before it. */
    public sealed interface AfterFills extends Stage permits Legs, Note {}

    public Root wrap(DirectBuffer buffer, int offset) { ... }   // header read; stages preallocated

    public static final class Root implements Stage {
        public long orderId() { ... }
        public Side side() { ... }
        public PriceDecoder price() { ... }        // sbe-tool's composite flyweight, or bound
        @Override public Fills next() { ... }      // covariant: no branch, no sealed type needed
    }

    public static final class Fills implements Stage {
        public int count() { ... }
        public boolean hasEntry() { ... }
        public Fill nextEntry() { ... }            // skips what the previous entry left unread
        @Override public AfterFills next() { ... } // skips the entries not taken
    }

    public static final class Fill {               // an entry: its block, then its own stages
        public long fillId() { ... }
        public int quantity() { ... }
        public Allocations allocations() { ... }
        public Fills skip() { ... }                // past its nested groups and var-data
    }

    public static final class Legs implements AfterFills { ... @Override public Note next() { ... } }
    public static final class Note implements AfterFills { public String note() { ... } @Override public End next() { ... } }
    public static final class End implements Stage { public int encodedLength() { ... } @Override public End next() { return this; } }
}
```

Taking only what is wanted, straight down the chain:

```java
OrderView view = new OrderView();                  // once; reused for every message

OrderView.Root order = view.wrap(buffer, offset);
long orderId = order.orderId();
long filled = 0;
OrderView.Fills fills = order.next();
while (fills.hasEntry()) {
    filled += fills.nextEntry().quantity();         // allocations never read, skipped
}
// legs and note never touched: nothing to do
```

Where the version branches, the compiler makes the older message a case:

```java
switch (fills.next()) {
    case OrderView.Legs legs -> readLegs(legs);     // a version 2 message
    case OrderView.Note note -> {}                  // version 1: no legs on the wire
}
```

A walk by kind, rather than a fixed chain:

```java
OrderView.Stage stage = view.wrap(buffer, offset);
while (!(stage instanceof OrderView.End)) {
    switch (stage) {
        case OrderView.Root root -> orderId = root.orderId();
        case OrderView.Fills fills -> { while (fills.hasEntry()) filled += fills.nextEntry().quantity(); }
        case OrderView.Legs legs -> {}
        case OrderView.Note note -> remark = note.note();
        case OrderView.End end -> {}
    }
    stage = stage.next();
}
```

## What falls out

- **Order by construction.** Only the current stage is held, and `next()`
  is the only way on.
- **Skipping is free.** `next()`, `nextEntry()` and `skip()` delegate to
  sbe-tool's generated `sbeSkip()` (`JavaGenerator`), which walks an
  entry's nested groups and var-data to its end; no wire logic of ours.
- **Evolution at compile time.** A group appended in a later version adds a
  permit to a sealed stage, and every `switch` over it stops compiling
  until the new case is handled; the project never writes `default`.
- **Garbage-free.** Every stage is a preallocated field of the view;
  `wrap` and `next()` return existing objects. Rereading is a new `wrap`,
  which only sets offsets.
- **Shaped by a record or a view.** Only the components mapped appear,
  under their Java names; what the view leaves out is skipped. Accessors go
  through the components' bindings, `price()` a `BigDecimal` where a
  binding says so. Absence follows the codec's rules, decided once in
  `CodecWalk`: a sealed `Present | Absent`, or a sealed next stage, where
  the codec would return `null`.
- **Composites need nothing new.** Fixed size at a known offset, no order
  inside: a stage returns sbe-tool's composite flyweight, or the bound
  value. The new types are the stages, the structure between blocks.

## Open

- **Stale stages.** Java has no linear types: a stage kept after `next()`
  still answers. A generation counter checked per stage would catch it, at
  about the cost of sbe-tool's precedence checks.
- **Leaving an entry half read.** `skip()` is the declared way out;
  `nextEntry()` with the previous entry's nested stages unread is either a
  silent skip or a mistake to report.
- **Encoding.** A group's count is written before its entries: the types
  can order `fills(count)` after the block and before `note`, but
  "exactly `count` entries" is a run-time check. Decoding first.
- **The walk.** `End.next()` returning itself keeps the loop plain;
  `view.walk(buffer, offset, stage -> ...)` hides it for a lambda per call
  site.
- **Nested entries.** The shape of a group inside an entry, its stages
  returning to the parent group's cursor, wants trying on `trading`'s
  `ExecutionReport`, hand-written first, before anything is generated.
