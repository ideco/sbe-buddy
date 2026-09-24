# Primitives

SBE primitive fields hold fixed-width values directly in the message buffer. In sbe-buddy, signed integers and floating-point types map from their Java equivalents. Unsigned integers and SBE `char` require an explicit encoding.

## Declaration

```java
package com.example.trading;

import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.UINT16;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
public record Quote(
        @SbeField(id = 1) long instrumentId,
        @SbeField(id = 2) double price,
        @SbeField(id = 3, primitiveType = UINT16) int venue,
        @SbeField(id = 4, presence = OPTIONAL) Integer quantity) {}
```

Within an SBE schema package, this produces the following message declaration:

```xml
<sbe:message name="Quote" id="1">
    <field name="instrumentId" id="1" type="int64"/>
    <field name="price" id="2" type="double"/>
    <field name="venue" id="3" type="uint16"/>
    <field name="quantity" id="4" type="int32" presence="optional"/>
</sbe:message>
```

## Type mapping

When neither `type` nor `primitiveType` is specified, the component’s Java type determines the encoding:

| Java type | SBE encoding | Bytes |
| --------- | ------------ | ----: |
| `byte`    | `int8`       |     1 |
| `short`   | `int16`      |     2 |
| `int`     | `int32`      |     4 |
| `long`    | `int64`      |     8 |
| `float`   | `float`      |     4 |
| `double`  | `double`     |     8 |

Boxed equivalents use the same encoding. Boxing does not make a field optional.

These encodings require an explicit `primitiveType`:

| `primitiveType` | Java type | Bytes |
| --------------- | --------- | ----: |
| `UINT8`         | `short`   |     1 |
| `UINT16`        | `int`     |     2 |
| `UINT32`        | `long`    |     4 |
| `UINT64`        | `long`    |     8 |
| `CHAR`          | `byte`    |     1 |

The Java type must match the table, or its boxed equivalent. For example, `UINT16` requires `int` or `Integer`; using `short` is a compilation error.

Java `boolean` and `char` have no direct mapping. SBE `char` occupies one byte, whereas Java `char` occupies two. Boolean-like values can be represented explicitly with an [enum](enums.md).

## Unsigned values

The Java representations match SBE’s generated flyweights. `UINT8`, `UINT16` and `UINT32` use wider signed Java types so their unsigned values can be represented.

`UINT64` uses a `long` containing the unchanged 64-bit pattern. Values with the highest bit set appear negative under Java’s signed interpretation. Use operations such as `Long.compareUnsigned` and `Long.toUnsignedString` when treating them as unsigned.

The Java representation does not define the valid SBE value range. Reserved null values and any declared bounds still apply.

## Absence

A field is absent when it is optional and holds the null value, or when it was introduced above the schema’s `baselineVersion` and the message being decoded predates it. Absence decodes to `null`, so a field that can be absent takes a boxed component.

Declare optional fields with `presence = OPTIONAL` and a boxed component type.

SBE represents absence using a reserved value in the field’s encoding. The field still occupies its full width; there is no separate presence flag.

| Operation                                  | Codec behaviour                        |
| ------------------------------------------ | -------------------------------------- |
| Encode a non-null value                    | Writes the value through the flyweight |
| Encode `null` in an optional field         | Writes the encoding’s null value       |
| Decode the null value in an optional field | Returns `null`                         |
| Encode `null` in a required boxed field    | Throws `IllegalArgumentException`      |

An optional component declared as a Java primitive is a compilation error.

The reserved value cannot also represent an ordinary value in an optional field: decoding it produces `null`. For the default `float` and `double` encodings, the null value is NaN, so an optional NaN decodes to `null`.

A boxed component on a field that can never be absent is permitted, but produces a compiler warning.

## Named encodings and validation

Use `@SbeField(type = SomeType.class)` to reference a named `@SbeType` encoding. Do not also specify `primitiveType`.

A named encoding can declare attributes such as `minValue`, `maxValue` and `nullValue`. These attributes belong on `@SbeType`, not `@SbeField`.

The processor checks the Java-to-wire type mapping. The record codec does not add numeric range checks around flyweight reads and writes; declaring schema bounds does not create application-level validation.

## Layout and metadata

Fields appear in record component order, unless the message states a `layout`; see [Retire a field](../how-to/retire-a-field.md). Field IDs identify fields; they do not determine byte offsets.

SBE calculates the layout, with explicit `offset` and message `blockLength` available where needed. Byte order belongs to the schema.

Attributes such as `semanticType`, `epoch` and `timeUnit` describe the field in the schema. They do not change its Java representation or perform conversions; a [binding](bindings.md) reads `epoch` and `timeUnit` from its context and interprets them as it will. `timeUnit` is deprecated, as `sbe.xsd` deprecates it.

## Coverage

This page describes scalar primitive fields. Fixed-length strings and arrays, constants, and encodings declared once and named by their fields are [named types](named-types.md).
