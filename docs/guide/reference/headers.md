# Headers

Every SBE message is framed in a header: a composite ahead of the message's block that says how to read it. In sbe-buddy a header is an `@SbeComposite` record that implements `MessageHeader`. The api's `DefaultMessageHeader` is the standard one, and a schema names a header of its own on `@SbeSchema`. Every codec of the schema writes and reads that header.

## The standard header

Unless a schema says otherwise, its messages are framed in `DefaultMessageHeader`, eight bytes of four `uint16`:

```java
@SbeComposite(name = "messageHeader")
public record DefaultMessageHeader(
        @SbeType(primitiveType = UINT16) int blockLength,
        @SbeType(primitiveType = UINT16) int templateId,
        @SbeType(primitiveType = UINT16) int schemaId,
        @SbeType(primitiveType = UINT16) int version) implements MessageHeader {}
```

```xml
<sbe:messageSchema package="com.example.trading" id="100" version="2" headerType="messageHeader">
    <types>
        <composite name="messageHeader">
            <type name="blockLength" primitiveType="uint16"/>
            <type name="templateId" primitiveType="uint16"/>
            <type name="schemaId" primitiveType="uint16"/>
            <type name="version" primitiveType="uint16"/>
        </composite>
```

The four are what every header carries: the message's block length, its template id, the schema's id and the version the message was written in. `MessageHeader` is the interface over exactly these four accessors.

## A header of your own

A transport often wants more in front of every message: a sequence number, a sender, a length prefix. Such a header is a record that declares the four standard components, which gives it `MessageHeader`'s accessors, and members of its own:

```java
package com.example.feed;

import static net.concini.sbebuddy.PrimitiveType.CHAR;
import static net.concini.sbebuddy.PrimitiveType.UINT16;
import static net.concini.sbebuddy.PrimitiveType.UINT32;

import net.concini.sbebuddy.MessageHeader;
import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite(name = "applicationHeader")
record ApplicationHeader(
        @SbeType(primitiveType = UINT16) int blockLength,
        @SbeType(primitiveType = UINT16) int templateId,
        @SbeType(primitiveType = UINT16) int schemaId,
        @SbeType(primitiveType = UINT16) int version,
        @SbeType(primitiveType = UINT32) long sequenceNumber,
        @SbeType(primitiveType = CHAR, length = 4, characterEncoding = "ASCII") String sender)
        implements MessageHeader {}
```

The schema names it:

```java
@SbeSchema(id = 1, version = 0, headerType = ApplicationHeader.class)
package com.example.feed;
```

```xml
<sbe:messageSchema package="com.example.feed" id="1" version="0" headerType="applicationHeader">
```

sbe-tool knows a header only by name, the schema's `headerType`, and the schema sbe-buddy writes always names it. The composite may be called anything; its four standard members must be called `blockLength`, `templateId`, `schemaId` and `version`, each a `uint16`, and they keep those names on the wire. A member of the header's own may be anything a composite's member may be, except a composite.

## Where the members sit

The members lie in the record's order, as a composite's do. A header's own member may come before the standard four, as a length prefix does:

```java
@SbeComposite(name = "framingHeader")
record FramingHeader(
        @SbeType(primitiveType = UINT32) long frameLength,
        @SbeType(primitiveType = UINT16) int blockLength,
        @SbeType(primitiveType = UINT16) int templateId,
        @SbeType(primitiveType = UINT16) int schemaId,
        @SbeType(primitiveType = UINT16) int version) implements MessageHeader {}
```

Every standard member then sits four bytes later, and the flyweights and the codec read each at its own offset.

## What the codec writes

Every codec of the schema is a `Codec<T, H>`, `H` the header record, so the header's type shows wherever the codec does:

```java
OrderCodec codec = new OrderCodec();

codec.encode(order, buffer, offset);
codec.encode(order, new ApplicationHeader(0, 0, 0, 0, sequence, "EXCH"), buffer, offset);
```

The standard four are always the message's own. Both `encode`s write the message's block length, template id, schema id and the schema's version, and the ones a header passed in carries are ignored, so a header never has to know what message it frames.

The header's own members come from the header passed in, or, through plain `encode`, as their null value: `4294967295` for the `uint32` sequence number, four zero bytes for the sender. No byte of the header is left as the buffer held it. Passing a `null` header is an `IllegalArgumentException`, `header is required`, and a member the header cannot carry, such as a sender longer than four characters, is refused as it would be in a message.

`encodedLength` counts the header whichever `encode` follows: its length is fixed by the schema.

## Reading the header first

`decodeHeader` reads the whole header, the standard four and every member of its own, and checks nothing:

```java
ApplicationHeader header = codec.decodeHeader(buffer, offset);
if (header.templateId() == OrderDecoder.TEMPLATE_ID) { … }  // the flyweight in com.example.feed.sbe
```

It reads any message of the schema, whichever codec it is called on, so a receiver can read the header before choosing the codec for the message behind it. `decode` still checks the schema id and the template id, and refuses a message that is not its own; it ignores the header's own members.

## Passing a message on

A header read from one message frames the next as it is:

```java
ApplicationHeader header = codec.decodeHeader(in, inOffset);
Order order = codec.decode(in, inOffset);
codec.encode(order, header, out, outOffset);
```

The header's own members carry over unchanged, and the standard four are the message's own, so a message of the current version is written byte for byte as it was read. A message read in an older version is written in the current one: were the standard four taken from the header, it would claim a version its bytes no longer are.

## What the compiler and the codec refuse

* A `headerType` that does not implement `MessageHeader`: javac's own error on the annotation, since the member takes only a `Class<? extends MessageHeader>`.
* A `headerType` that is not an `@SbeComposite` record: `headerType must name an @SbeComposite`.
* A standard member renamed on the wire: `a header's blockLength keeps its wire name, not "length"`.
* A standard member missing, or of another type than `uint16`: sbe-tool's error, reported on the package.
* A composite among the header's own members: the codec refuses it, naming each message. `codecs = false` on `@SbeSchema` keeps the flyweights.

An enum among a header's own members is written as its null value by plain `encode`, and `decodeHeader` reads that back only as the enum's `@UnknownValue` constant; without one it is an `IllegalArgumentException`. Give such an enum an unknown value, or pass a header whenever you encode.

## Coverage

This page covers the header in front of every message. The schema's other attributes, its byte order among them, are [the schemas page's](schemas.md). A header member appended in a later version is planned with the rest of evolution.
