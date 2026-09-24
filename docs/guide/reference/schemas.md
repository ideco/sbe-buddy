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
| `resource` | none | The schema's XML on the class path, frozen: the records are checked against it and nothing is written; see [Schema-first](schema-first.md) |

The package is the schema's `package`, and the flyweights go to `<package>.sbe`.

## Byte order

```java
@SbeSchema(id = 1, version = 0, byteOrder = BIG_ENDIAN)
package com.example.feed;
```

The order applies to every value on the wire: the header, every field, every array element, an enum's and a set's encoding, a composite's members, a group's dimensions and var-data's lengths. The codec reaches the buffer only through the flyweights, which apply the order, so records and codecs look the same either way.

## The header

Every message of the schema is framed in the header `headerType` names: the standard `DefaultMessageHeader`, or a record of the schema's own that implements `MessageHeader`. Every codec of the schema is a `Codec<T, H>` over it, and can read the header on its own and write the header's own members. [Headers](headers.md) covers declaring one, what the codec writes into it, and reading it first.
