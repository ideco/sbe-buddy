# Typed flyweights

sbe-tool's flyweights read a message in place, without allocating, but they leave the order to you: groups and var-data must be read in wire order, a var-data accessor consumes what it reads, and a group left unread leaves everything after it misplaced. sbe-buddy generates a reader over them for every message of the schema, which takes the message as a sequence of stages in wire order and keeps that order for you.

Readers need no record: a message a `partial` package leaves out, and every message of a package with no record at all, get one too.

## The reader

`NewOrderReader` sits beside sbe-tool's `NewOrderDecoder` in `com.example.trading.sbe`. It hands out the message one stage at a time: the root block, then each group's header, each of its entries, each entry's own groups, and each var-data. `NewOrder` has a group `parties` whose entries hold a group `partySubIds`, so a limit order with two parties, the first with one sub id, reads as:

```
RootBlock, Parties, PartiesEntry, PartySubIds, PartySubIdsEntry, PartiesEntry, PartySubIds
```

The stages are a sealed interface, so a read is one loop and one `switch`:

```java
NewOrderReader reader = new NewOrderReader();            // once, reused for every message

for (NewOrderReader.Stage stage : reader.wrap(buffer, offset)) {
    switch (stage) {
        case NewOrderReader.RootBlock order -> symbol = order.symbol();
        case NewOrderReader.PartiesEntry party -> parties.add(party.partyId());
        default -> {}
    }
}
int length = reader.decodedLength();
```

The `default` is safe: whatever a stage leaves unread, the reader passes over when it moves on, so a reader after one group writes its case and loses nothing.

## The stages

* `RootBlock` has the message's fields, each with sbe-tool's own accessors under sbe-tool's names: `symbol()`, `symbol(int index)` and `getSymbol(byte[], int)` for a `char` array, `side()` and `sideRaw()` for an enum, the flyweight for a set or a composite.
* A group's header is named after the group, `Parties`, and has `count()`.
* An entry is `PartiesEntry`, with `index()`, from 0, and the entry's fields.
* A var-data is named after it, `Text` on `CancelReject`, with `length()`, `copyTo(MutableDirectBuffer, int)`, `copyTo(byte[], int)` and `wrap(DirectBuffer)`, a window over its bytes. It reads again and again: the reader keeps where it lies.

A group with no entries is a header with a count of 0. A group or var-data added in a later version than the message's never comes.

A stage answers while the reader is inside it: the root block for the whole message, a header or an entry while the reader is at it or inside what it holds, so a `PartySubIdsEntry` case can still read its party's `partyId()`, and a var-data while it is current. Reading one after the reader has left it is an `IllegalStateException`. An entry kept past its group's next entry answers for that next entry.

## Skipping

`skip()` on a stage prunes what it contains: on the root block the rest of the message, on a header its entries, on an entry its nested groups and var-data, on a var-data nothing. What follows still comes.

```java
case NewOrderReader.Parties parties -> parties.skip();   // no PartiesEntry comes
```

Only the current stage can skip. And `default -> stage.skip()` is a mistake: a group's header lands in the `default`, is pruned, and the `case` for its entries never runs. Leave `default` empty.

## The reader itself

* `wrap(buffer, offset)` reads the header and stands before the root block; a message of another template is sbe-tool's `IllegalStateException`.
* `rewind()` goes back before the root block of the same message.
* `header()` is sbe-tool's header decoder, for a header's own members; `actingVersion()` the version the message was written at.
* `decodedLength()` is the length of the whole message, header included, at any point of the read.

A reader is mutable and reused: one per thread, as a codec is.
