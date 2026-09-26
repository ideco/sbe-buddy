# Schemas

A schema is a Java package: `@SbeSchema` on its `package-info.java` is the SBE `messageSchema` element, and every annotated record and enum in the package belongs to it. This page covers the schema's own attributes, the byte order and the header the messages are framed in.

## Declaration

```java
@SbeSchema(id = 100, version = 2, baseline = "trading-v1.xml", description = "Order entry")
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
| `baseline` | none | A released version's XML on the class path, which the schema must stay compatible with and whose version is the oldest the codecs decode; see [The baseline](#the-baseline). Contributes nothing to the schema |
| `resource` | none | The schema's XML on the class path, frozen: the records are checked against it and nothing is written; see [Schema-first](schema-first.md) |
| `partial` | `false` | Whether the records map only some of the resource's messages; see [Mapping some messages](schema-first.md#mapping-some-messages) |

The package is the schema's `package`, and the flyweights go to `<package>.sbe`.

## The baseline

Once a version of the schema has been released, its readers and writers are out there. Check the XML of that version in, under `src/main/resources` beside the package, and name it:

```java
@SbeSchema(id = 100, version = 2, baseline = "trading-v1.xml")
package com.example.trading;
```

The name is resolved as `resource` resolves one: relative to the package, or absolute with a leading slash. The compiler then holds the schema against it, as SBE's extension rules have it. A change the rules allow compiles:

* a field appended to a message or a group, a group after the groups, var-data after the var-data, a message, an enum value or a set choice, each with a `sinceVersion` above the baseline's version;
* a new name or description for anything, and `deprecated` on anything;
* `presence` between `REQUIRED` and `OPTIONAL`;
* a primitive becoming a named type of the same encoding, or the reverse.

A change that breaks a reader of the baseline is an error on the node it is on:

```
the baseline has a field "orderId" (id 1) here, not id 11; the id is its identity, and a field that means something else is appended as a new one
the type differs from the baseline's: int64, not int32
the baseline's Order has a field "quantity" (id 2) the schema lacks; a field stays, unmapped if the record retires it
a field the baseline lacks needs a sinceVersion above the baseline's version 1
the type differs from the baseline's: enum "Side" has the value "S" (Sell), which the schema's lacks; values stay, deprecated if they are retired
```

Nothing is matched by name, so a rename is free: messages match by id, the fields, groups and var-data of each block by position, and types by what they are. An id is a field's identity, the FIX tag it stands for, so a field whose id changes is another field, even where the type fits: it is appended as a new one, and the old one is retired with `unmapped`. A composite and the header never change, and neither do the schema's id and byte order.

The baseline's version is also the oldest the codecs decode. A message below it is refused, and a required field introduced at or below it is never absent, so its component may be a primitive. Without a baseline, the codecs decode every version and nothing is compared. To retire old versions, check in a later one and name it instead.

The check is against the one version named. What was added after it is not checked against anything, so move the baseline forward as versions are released, as far as the oldest version whose readers still matter.

## Byte order

```java
@SbeSchema(id = 1, version = 0, byteOrder = BIG_ENDIAN)
package com.example.feed;
```

The order applies to every value on the wire: the header, every field, every array element, an enum's and a set's encoding, a composite's members, a group's dimensions and var-data's lengths. The codec reaches the buffer only through the flyweights, which apply the order, so records and codecs look the same either way.

## The header

Every message of the schema is framed in the header `headerType` names: the standard `DefaultMessageHeader`, or a record of the schema's own that implements `MessageHeader`. Every codec of the schema is a `Codec<T, H>` over it, and can read the header on its own and write the header's own members. [Headers](headers.md) covers declaring one, what the codec writes into it, and reading it first.
