# Fields

`@SbeField` maps a record component to a field in a message's or group
entry's fixed-length block. A field can use a primitive encoding or a named
type, enum, set or composite.

## Declaration and schema

In a schema package, `Quote.java` can declare:

```java
package com.example.trading;

import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.UINT16;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 3)
public record Quote(
        @SbeField(id = 1) long instrumentId,
        @SbeField(id = 2, primitiveType = UINT16) int venue,
        @SbeField(id = 3, presence = OPTIONAL) Integer quantity) {}
```

The corresponding message declaration is:

```xml
<sbe:message name="Quote" id="3">
    <field name="instrumentId" id="1" type="int64"/>
    <field name="venue" id="2" type="uint16"/>
    <field name="quantity" id="3" type="int32" presence="optional"/>
</sbe:message>
```

## Java representations

Without `type` or `primitiveType`, numeric components select these encodings:

| Java type | SBE encoding | Bytes |
| --- | --- | ---: |
| `byte` | `int8` | 1 |
| `short` | `int16` | 2 |
| `int` | `int32` | 4 |
| `long` | `int64` | 8 |
| `float` | `float` | 4 |
| `double` | `double` | 8 |

Boxed types select the same encodings. These other encodings must be selected
explicitly with `primitiveType`:

| SBE encoding | `primitiveType` | Java type | Bytes |
| --- | --- | --- | ---: |
| `uint8` | `UINT8` | `short` | 1 |
| `uint16` | `UINT16` | `int` | 2 |
| `uint32` | `UINT32` | `long` | 4 |
| `uint64` | `UINT64` | `long` | 8 |
| `char` | `CHAR` | `byte` | 1 |

Use the listed Java type or its boxed equivalent. `UINT16` requires `int`
or `Integer`, for example, even though its encoding occupies two bytes.

`uint64` uses a `long` holding the unchanged bit pattern. Use Java's unsigned
operations, such as `Long.compareUnsigned`, when interpreting it. Java
`char` and `boolean` have no direct mapping; a boolean can use an
[enum](enums.md) with a [binding](bindings.md).

Use `type = SomeType.class` to select a named declaration. It is mutually
exclusive with `primitiveType`. An enum or composite component identifies
its own declaration, as does `Set<E>` for a set. Strings and arrays need a
[named type](named-types.md) to specify their fixed length.

## Presence and absence

An optional scalar uses a reserved null sentinel. Encoding Java `null`
writes that sentinel; decoding it returns `null`. Its component must use a
boxed type. Boxing alone does not make a field optional.

A required field may also decode to `null` when the message predates its
`sinceVersion`. If those older messages are accepted, its numeric component
must be boxed even though encoding a null required value is rejected.
See [Schema versions and absence](../concepts/schema-versions-and-absence.md).

The sentinel cannot also represent an ordinary optional value. For default
optional `float` and `double` encodings, NaN decodes to `null`.

Optional strings, arrays, sets and composites need a binding to define
their null representation. Their reference pages describe the distinction.

## Layout and validation

Fields precede groups and variable-length data. Their wire order follows
record component order unless `layout` specifies it. IDs identify fields;
they do not determine offsets. `offset` can leave padding, and `blockLength`
on the message or group can reserve space after its fields.

The compiler checks the Java representation. The codec does not check
numeric ranges: schema bounds and reserved values remain the application's
responsibility. Declare custom bounds and null sentinels on a named
`@SbeType`. A field inherits that type's presence unless it explicitly
selects optional or constant presence; selecting `REQUIRED` cannot override
an optional or constant named type.

The `SbeField` API Javadoc covers individual settings. For changes to an
existing message, see [Add a field](../how-to/add-a-field.md) and
[Retire a field](../how-to/retire-a-field.md).
