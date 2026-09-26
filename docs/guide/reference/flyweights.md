# Typed flyweights

sbe-tool's flyweights read and write a message in place, without allocating, but they leave the order to you: groups and var-data must be read in wire order, a var-data accessor consumes what it reads, and a group left unread leaves everything after it misplaced; on the way out, a group's count comes before its entries, every group must be written even when empty, and a field left unset keeps whatever the buffer held. sbe-buddy generates a reader and a writer over them for every message of the schema: the reader takes the message as a sequence of stages in wire order and keeps that order for you, and the writer's chain offers only the next step, so a message written out of order, or left incomplete, does not compile.

Readers and writers need no record: a message a `partial` package leaves out, and every message of a package with no record at all, get them too.

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

## The writer

`NewOrderWriter` sits beside sbe-tool's `NewOrderEncoder`. `wrap` returns the first step, and each step returns the only one that may follow it:

```java
NewOrderWriter writer = new NewOrderWriter();            // once, reused for every message

int length = writer.wrap(buffer, offset)
        .clOrdId("ORD-1")                                // the required fields, in wire order
        .account("ACCT-0001")
        .symbol("ACME")
        .side(Side.BUY)
        .ordType(OrdType.LIMIT)
        .timeInForce(TimeInForce.DAY)
        .execInst().postOnly(true).end()                 // a set: its choices, then end()
        .transactTime(nanos)
        .orderQty().mantissa(700)                        // a composite: every member but the constants
        .price().mantissa(996_100L)                      // the block complete: its optional fields, in any order
        .parties()
            .entry().partyId("FIRM-A").partyRole(PartyRole.EXECUTING_FIRM)
                .partySubIds().entry().partySubId("DESK-7").partySubIdType((short) 4).end()
            .entry().partyId("TRADER-12").partyRole(PartyRole.ENTERING_TRADER)
                .partySubIds().end()                     // a nested group is opened in every entry, even empty
        .end()
        .length();                                       // header included; only here once the message is complete
```

* Every required field is a step, in the order of the wire, and so is a field the record leaves `unmapped`: the writer writes the message, not the record. A field appended in a later version is a step too, since the writer always writes the current version.
* Once a block's required fields are written, its optional fields may be set in any order, beside the first group or var-data. An optional field left unset is its null value: every block is filled with null values when it opens.
* Each step has sbe-tool's own setters for the field, under sbe-tool's names: `clOrdId(String)`, `clOrdId(CharSequence)` and `putClOrdId(byte[], int)` for a `char` array, `side(Side)` for an enum. A var-data takes `text(String)` where its type has a character encoding, and `putText` over a `byte[]` or a `DirectBuffer`.
* A composite opens a chain of its members, `price().mantissa(996_100L)`, an optional member offering `mantissaNull()`; a set its choices and `end()`.
* A group opens with `parties()`; `entry()` starts each entry, `end()` closes the group and writes its count. An entry complete takes the next `entry()` or the group's `end()`.
* `length()` is the length of the whole message, header included, and exists only once the message is complete.
* `header()` is sbe-tool's header encoder, for a header's own members, which the writer otherwise writes as their null values.

A stage is one object, reused: nothing is allocated per step, and a stage may be kept, a group to add entries as they come. Once the writer has moved past it, a kept stage refuses with an `IllegalStateException`.
