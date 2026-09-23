# Schemas

A schema is a Java package: `@SbeSchema` on its `package-info.java` is the SBE `messageSchema` element, and every annotated record and enum in the package belongs to it. This page covers the schema's own attributes, the byte order and the header the messages are framed in.

## Declaration

```java
@SbeSchema(id = 100, version = 2, baselineVersion = 1, description = "Order entry")
package com.example.trading;
```

| Member | Default | What it is |
| --- | --- | --- |
| `id` | | The schema's id, in every message header |
| `version` | | The schema's current version, which encoding always writes |
| `semanticVersion` | none | Free text, written as the schema's `semanticVersion` |
| `description` | none | Written as the schema's `description` |
| `byteOrder` | `LITTLE_ENDIAN` | The order of every multi-byte value of the schema |
| `headerType` | `DefaultMessageHeader.class` | The header every message is framed in |
| `codecs` | `true` | Whether a codec is generated per message; contributes nothing to the schema |
| `baselineVersion` | `0` | The oldest version the codecs still decode; contributes nothing to the schema |

The package is the schema's `package`, and the flyweights go to `<package>.sbe`.

## Byte order

```java
@SbeSchema(id = 1, version = 0, byteOrder = BIG_ENDIAN)
package com.example.feed;
```

The order applies to every value on the wire: the header, every field, every array element, an enum's and a set's encoding, a composite's members, a group's dimensions and var-data's lengths. The codec reaches the buffer only through the flyweights, which apply the order, so records and codecs look the same either way.

## The header

Every message is framed in a header that carries its `blockLength`, `templateId`, `schemaId` and `version`, each a `uint16`. sbe-tool knows the header only by name: the schema's `headerType`, or the composite named `messageHeader` where there is none. The schema sbe-buddy writes always names it.

In Java a header is an `@SbeComposite` record that implements `MessageHeader`. The api's `DefaultMessageHeader` is the standard one, with exactly the four members. A header of your own declares the four as components, which gives it `MessageHeader`'s accessors, and adds members of its own:

```java
@SbeComposite(name = "applicationHeader")
record ApplicationHeader(
        @SbeType(primitiveType = UINT16) int blockLength,
        @SbeType(primitiveType = UINT16) int templateId,
        @SbeType(primitiveType = UINT16) int schemaId,
        @SbeType(primitiveType = UINT16) int version,
        @SbeType(primitiveType = UINT32) long sequenceNumber
) implements MessageHeader {
}
```

```java
@SbeSchema(id = 1, version = 0, headerType = ApplicationHeader.class)
package com.example.feed;
```

```xml
<sbe:messageSchema package="com.example.feed" id="1" version="0" headerType="applicationHeader">
```

A header's own members may come before the standard four, as a length prefix does; the offsets follow the record. A `headerType` that does not implement `MessageHeader` is javac's error on the annotation, and a standard member given another wire name through `name` is refused.

## What the codec writes into a header

Every codec of the schema is a `Codec<T, H>`, `H` the header record:

```java
OrderCodec codec = new OrderCodec();

codec.encode(order, buffer, offset);                  // the header's own members as their null value
codec.encode(order, header, buffer, offset);          // the header's own members from header

ApplicationHeader read = codec.decodeHeader(buffer, offset);
```

The block length, template id, schema id and version are always the message's: both `encode`s write them, and a header passed in has them ignored, so a header read from one message can frame another. `decodeHeader` reads the whole header and checks nothing, so it reads any message of the schema, whichever codec it then goes to. `decode` checks the schema id and template id as before, and ignores the header's own members.

A composite among a header's own members is not supported by the codec yet; set `codecs = false` for such a schema.
