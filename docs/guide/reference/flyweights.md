# Typed flyweights

sbe-tool's flyweights read a message in place, without allocating, but they leave the order to you: groups and var-data must be read in wire order, a var-data accessor consumes what it reads, and a group left unread leaves everything after it misplaced. sbe-buddy generates a reader over them for every message of the schema: it takes the message as a sequence of stages in wire order and keeps that order for you.

A reader needs no record: a message a `partial` package leaves out, and every message of a package with no record at all, get one too. It is generated into the schema's own package, beside the codecs, over sbe-tool's flyweights in `.sbe`; where a record maps the message, each stage also offers `bound()`, the record's view of it, below. Writing stays sbe-tool's encoders', in place, or the codec's, from a record.

## The reader

`NewOrderReader` sits in `com.example.trading`, beside `NewOrderCodec`, over sbe-tool's `NewOrderDecoder` in `com.example.trading.sbe`. It hands out the message one stage at a time: the root block, then each group's header, each of its entries, each entry's own groups, and each var-data. `NewOrder` has a group `parties` whose entries hold a group `partySubIds`, so a limit order with two parties, the first with one sub id, reads as:

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

## Bound stages

Where a record maps the message, every stage that carries a component has `bound()`: the record's view of it, every component under its Java name and with its Java type, its binding applied, as the codec reads it. The wire stays the default and garbage-free; the bound stage is the one to reach for where the record's types are wanted.

```java
for (NewOrderReader.Stage stage : reader.wrap(buffer, offset)) {
    switch (stage) {
        case NewOrderReader.RootBlock order -> price = order.bound().price();   // a BigDecimal, through PriceBinding
        case NewOrderReader.PartiesEntry party -> roles.add(party.bound().partyRole());  // the record's PartyRole
        case NewOrderReader.Text text -> reason = text.bound().value();         // the record's String
        default -> {}
    }
}
```

* A block's bound stage, `RootBlockBound` or `PartiesEntryBound`, has an accessor per component of the record the block carries, constants included, and on an entry `index()`. `wire()` goes back to the stage.
* A var-data's, `TextBound`, has `value()`: the record's `String` or `byte[]`, or its binding's type. It reads again and again, as the stage does.
* Each call reads the wire and runs the binding again; nothing is cached, and an accessor that builds an object, a `BigDecimal`, a `Set`, a face record, allocates as the binding does.
* Absence is `null`, as on the record: a primitive that may be absent is boxed, and reads `null` for its null value, and for a field above the acting version, where the wire stage reads sbe-tool's null value.
* A group's header has no bound stage: a binding over the whole group, a `Map` of the entries, has nothing to apply to while the entries come one by one; each entry's components are bound.
* A bound stage answers while its stage does, and refuses as it does.

