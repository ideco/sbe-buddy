# Unions

A stream rarely carries one kind of message. A union is a sealed interface over messages of one schema, annotated `@SbeUnion`, and it gets a codec of its own: one that encodes any of its messages and decodes whichever one the buffer holds, to the interface. On the wire nothing changes; a union is Java's view of what SBE already is, a tagged union whose tag is the template id.

## Declaration

```java
package com.example.trading;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;
import net.concini.sbebuddy.SbeUnion;

@SbeUnion
public sealed interface OrderCommand permits OrderCommand.PlaceOrder, OrderCommand.CancelOrder {

    long orderId();

    @SbeMessage(id = 1)
    record PlaceOrder(
            @SbeField(id = 1) long orderId,
            @SbeField(id = 2) int quantity) implements OrderCommand {}

    @SbeMessage(id = 2)
    record CancelOrder(
            @SbeField(id = 1) long orderId) implements OrderCommand {}
}
```

The messages may be nested in the interface, as here, or stand on their own in the schema package. The interface may declare what its messages share, such as `orderId()`; the codec ignores it. `@SbeUnion` adds nothing to the schema: the XML holds the two messages and no trace of the interface.

## The codec

The union's codec is named after it and is a `Codec` like any message's:

```java
OrderCommandCodec codec = new OrderCommandCodec();

codec.encode(new PlaceOrder(42L, 100), buffer, offset);    // PlaceOrder's bytes, template 1
OrderCommand command = codec.decode(buffer, offset);       // a PlaceOrder, by the header's template id
```

It owns one codec per message, `PlaceOrderCodec` and `CancelOrderCodec`, and hands each message to its own, so its bytes are exactly theirs and a message written by one is read by the other. The message codecs are still generated and usable alone.

`encodedLength`, `lastDecodedLength` and `decodedLength` answer for the message at hand, and `decodeHeader` reads the schema's header as every codec of the schema does.

## The exhaustive `switch`

A decoded union is handled with a `switch`, and javac checks it covers every message:

```java
switch (codec.decode(buffer, offset)) {
    case PlaceOrder place -> book.place(place.orderId(), place.quantity());
    case CancelOrder cancel -> book.cancel(cancel.orderId());
}
```

No `default` is needed, and none should be written: when a message joins the union, every `switch` that misses it stops compiling. There is no visitor to implement; pattern matching is the visitor.

## Hierarchies

A union may hold sealed interfaces as well as messages, and each annotated interface gets a codec over every message beneath it:

```java
@SbeUnion
public sealed interface Ingress permits OrderCommand, SessionCommand {}

@SbeUnion
public sealed interface OrderCommand extends Ingress permits PlaceOrder, CancelOrder {}

public sealed interface SessionCommand extends Ingress permits Logon, Logout {}
```

`IngressCodec` reads all four messages, `OrderCommandCodec` the two orders. `SessionCommand` carries no annotation, so it gets no codec and its messages flatten into `Ingress`. A caller's `switch` may still take a nested union as one case, and javac checks that it covers the messages through the hierarchy:

```java
switch (ingress.decode(buffer, offset)) {
    case OrderCommand order -> orders.handle(order);
    case Logon logon -> sessions.open(logon.sessionId());
    case Logout logout -> sessions.close(logout.sessionId());
}
```

A record may belong to several unions, an `Audited` union cutting across `Ingress`'s branches, say, and a union reaching one message through two paths holds it once. Every union's codec switches once over all its messages; it never passes a message on to a nested union's codec, since the bytes are the messages' either way. Two unions' codecs are unrelated types: an `OrderCommandCodec` is not a `Codec<Ingress, …>`.

## Replacing a message

A composite cannot grow, since its size is part of every block that holds it (the [composites page](composites.md) says why). When a message's composite needs another member, the message is replaced by a new template over a new composite, and a union takes both:

```java
@SbeUnion
public sealed interface QuoteUpdate permits Quote, QuoteV2 {
    long instrumentId();
}

@SbeMessage(id = 1)
public record Quote(@SbeField(id = 1) long instrumentId, @SbeField(id = 2) Price bid) implements QuoteUpdate {}

@SbeMessage(id = 2, sinceVersion = 1)
public record QuoteV2(@SbeField(id = 1) long instrumentId, @SbeField(id = 2) Level bid) implements QuoteUpdate {}
```

A reader of the current version decodes both templates through `QuoteUpdateCodec`. A reader built at version 0 knows only template 1: it reads every `Quote` a current writer sends, and tells a `QuoteV2` from its own through `canDecode`. A writer keeps sending `Quote` for as long as old readers listen.

## Routing with `canDecode`

Every codec, message or union, answers whether it would take the message at an offset:

```java
if (orders.canDecode(buffer, offset)) {
    handle(orders.decode(buffer, offset));
} else if (sessions.canDecode(buffer, offset)) {
    handle(sessions.decode(buffer, offset));
}
```

It reads the header alone and throws nothing of its own, though a header that lies outside the buffer is the buffer's exception: the schema id must be the schema's, the template id one of the codec's messages, and the version at or above the schema's `baselineVersion`. `decode` checks the same and throws `IllegalArgumentException` on a message that is not its own, naming the union's templates: `not a OrderCommand: schemaId 6, templateId 3; its templates are 1, 2`.

No codec skips a message it cannot read. A message with groups or var-data has a length only its own codec can compute, so stepping over a stranger is the transport's job, which knows the length of what it delivered.

## Null

`encode`, both of them, and `encodedLength` refuse a `null` value with `IllegalArgumentException("value is required")`, on a union's codec and a message's alike.

## What the compiler and the codec refuse

* `@SbeUnion` on anything but a sealed interface: `@SbeUnion goes on a sealed interface`.
* A generic union: `a union is not generic: its codec decodes to one type`.
* A union on a schema with `codecs = false`: `a union needs codecs, and codecs = false on @SbeSchema generates none`.
* A subtype that is neither an `@SbeMessage` record of the schema nor a sealed interface, reported once on the subtype however many unions reach it:
  * a record without `@SbeMessage`: `Note is in the union Orders but carries no @SbeMessage`;
  * a `non-sealed` interface or class: `Open is in the union Orders and non-sealed, which leaves the union open to types its codec cannot know`;
  * a class: `Batch is in the union Orders but is neither an @SbeMessage record nor a sealed interface`.
* `@SbeUnion` in a package whose `package-info.java` carries no `@SbeSchema`, on the annotation.
* A union and a message, or two messages nested in different types, whose codecs would share a name, since codecs are named by simple name in the schema package: `OrderCodec would be generated twice: for the message com.example.trading.Order and the union com.example.trading.Feed.Order`.
* A union over a message the codec cannot generate yet: the message's own problem, and `no codec for Orders: its message Order has none` on the union.

Java itself keeps a sealed interface's subtypes in its package, outside a named module, so a union's messages are its schema's.

## Coverage

This page covers unions of one schema's messages. A union spanning schemas is out of scope, and so is a value for a template the union does not know: such a record would have no body to hold, and every `switch` would carry a case for it.
