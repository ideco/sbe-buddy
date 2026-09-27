# Variable-length data

`@SbeData` declares a length-prefixed payload after the fixed fields and
repeating groups. Text maps to `String`; binary data maps to `byte[]`.

## Declaration and schema

In a schema package, `OrderNote.java` declares text and binary payloads:

```java
package com.example.trading;

import net.concini.sbebuddy.SbeData;
import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;
import net.concini.sbebuddy.VarDataEncoding;
import net.concini.sbebuddy.VarStringEncoding;

@SbeMessage(id = 8)
public record OrderNote(
        @SbeField(id = 1) long orderId,
        @SbeData(id = 2, type = VarStringEncoding.class) String note,
        @SbeData(id = 3, type = VarDataEncoding.class) byte[] attachment) {}
```

The message contains these data declarations, after `orderId`:

```xml
<data name="note" id="2" type="varStringEncoding"/>
<data name="attachment" id="3" type="varDataEncoding"/>
```

The selected text encoding is declared among the schema's types:

```xml
<composite name="varStringEncoding">
    <type name="length" primitiveType="uint16"/>
    <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
</composite>
```

## Java representations and encodings

| Supplied encoding | Payload | Component |
| --- | --- | --- |
| `VarStringEncoding` | UTF-8 text | `String` |
| `VarAsciiEncoding` | US-ASCII text | `String` |
| `VarDataEncoding` | Binary bytes | `byte[]` |

All three use a two-byte `uint16` length prefix. It counts payload bytes,
excluding the prefix, and allows at most 65,534 bytes. A text's byte count
can differ from `String.length()`.

A custom encoding is an `@SbeComposite` with `length` and `varData`
members. For example, `LargePayloadEncoding.java` allows a larger binary
payload:

```java
package com.example.trading;

import net.concini.sbebuddy.PrimitiveType;
import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite
public record LargePayloadEncoding(
        @SbeType(primitiveType = PrimitiveType.UINT32, maxValue = "1073741824")
        long length,
        @SbeType(primitiveType = PrimitiveType.UINT8, length = 0)
        byte[] varData) {}
```

Select it with `type = LargePayloadEncoding.class` on a `byte[]` data
component. The length type can be `uint8`, `uint16` or `uint32`; `uint32`
requires a declared maximum no greater than `Integer.MAX_VALUE`. The
`length = 0` on `varData` describes the variable payload; it does not make
an ordinary fixed-block field variable-length.

## Text and validation

Encoding rejects oversized payloads, unrepresentable characters and invalid
Java surrogate sequences with `IllegalArgumentException`. These checks
apply during `encodedLength` as well as encoding. The codec supports other
JDK-known character encodings when declared on a custom text encoding.

Decoding uses the flyweight's text conversion. Malformed encoded text can
produce replacement characters, such as U+FFFD for malformed UTF-8; decoding
is not a strict text-validation step.

Decoding binary data creates a new array. Compare array contents when
testing a round trip: Java records' default equality compares arrays by
identity.

## Presence and layout

Data has no optional presence. Empty strings and arrays encode a zero-length
payload and are ordinary values. Encoding null is rejected. A member
introduced after the baseline decodes to null from an older message that
predates it.

A body can contain several data members. All must follow its fields and
groups, both in messages and in group entries. See
[Schema versions and absence](../concepts/schema-versions-and-absence.md)
before appending data to an existing message or entry.

The `SbeData` API Javadoc covers individual settings. A
[binding](bindings.md) can convert the `String` or `byte[]` to another
application type.
