# Enums

An SBE enum assigns explicit wire values to named constants. In sbe-buddy, `@SbeEnum` declares the encoding and `@SbeEnumValue` assigns each Java constant its wire value. Java ordinals are never used.

## Declaration

```java
package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;
import net.concini.sbebuddy.UnknownValue;

@SbeEnum(primitiveType = UINT8)
public enum Side {
    @SbeEnumValue("1")
    BUY,

    @SbeEnumValue("2")
    SELL,

    @UnknownValue
    UNKNOWN
}
```

Use the enum directly as a record component in an SBE schema package:

```java
package com.example.trading;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
public record PlaceOrder(
        @SbeField(id = 1) long orderId,
        @SbeField(id = 2) Side side) {}
```

The component type identifies the enum; `@SbeField(type = Side.class)` is unnecessary.

The enum declaration produces this schema fragment:

```xml
<enum name="Side" encodingType="uint8">
    <validValue name="BUY">1</validValue>
    <validValue name="SELL">2</validValue>
</enum>
```

`UNKNOWN` is a Java-side fallback and contributes no schema value.

## Encoding and names

Specify exactly one encoding on `@SbeEnum`:

| Member | Meaning |
|---|---|
| `primitiveType` | The SBE primitive used to encode values, such as `UINT8` or `CHAR` |
| `encodingType` | A declared `@SbeType` class supplying the encoding |

`@SbeEnumValue.value` is a string containing the SBE value: for example, `"1"` for an integer encoding or `"B"` for `CHAR`. Values must satisfy the encoding’s constraints and be unique within the enum.

By default, the schema uses the Java enum’s simple name and its constants’ names. Override these with `@SbeEnum(name = "...")` and `@SbeEnumValue(name = "...", value = "...")` when Java and schema names should differ.

Reordering Java constants does not change their encoded values.

## Unknown values

A reader can encounter a wire value that its enum does not declare, for example when a newer writer adds a constant.

By default, decoding such a value throws `IllegalArgumentException`, identifying the enum and the value. An enum may instead designate one fallback constant with `@UnknownValue`.

For the `Side` declaration above:

| Operation | Result |
|---|---|
| Encode `BUY` | Writes `1` |
| Encode `SELL` | Writes `2` |
| Decode `1` | Returns `BUY` |
| Decode `2` | Returns `SELL` |
| Decode an unrecognised value such as `3` | Returns `UNKNOWN` |
| Encode `UNKNOWN` | Throws `IllegalArgumentException` |

The fallback does not retain the original wire value. A record containing it therefore cannot be encoded unchanged to forward that value.

Every constant must carry either `@SbeEnumValue` or `@UnknownValue`. A constant cannot carry both, and an enum can have at most one fallback.

## Optional and absent fields

Enum fields are required by default. To allow `null`, declare the field with `@SbeField(id = 2, presence = Presence.OPTIONAL)`.

| Situation | Codec behaviour |
|---|---|
| Encode `null` in an optional field | Writes the encoding’s null value |
| Decode the null value in an optional field | Returns `null` |
| Encode `null` in a required field | Throws `IllegalArgumentException` |
| Decode a message whose version predates the field’s `sinceVersion` | Returns `null`, provided the message version is accepted by the schema’s baseline |

Absence and unknown values are distinct. An absent field becomes `null`; an unrecognised value in a present field uses the fallback or throws.

## Schema metadata

The annotations expose the corresponding SBE attributes:

| Annotation | Member | Default |
|---|---|---|
| `@SbeEnum` | `name` | Java enum name |
| | `primitiveType` / `encodingType` | Neither selected; exactly one required |
| | `offset` | `0` |
| | `semanticType`, `description` | Unspecified |
| | `sinceVersion`, `deprecated` | `0` |
| `@SbeEnumValue` | `value` | Required |
| | `name` | Java constant name |
| | `description` | Unspecified |
| | `sinceVersion`, `deprecated` | `0` |

For evolution, preserve existing wire values and their meanings. Adding a value leaves the field’s encoding unchanged, but older readers still need an explicit policy for unknown values. `sinceVersion` records when a value was introduced; it does not give older readers a fallback automatically.

## Direct flyweight access

SBE generates a separate enum in the schema’s `.sbe` package. The record codec maps between that wire representation and your domain enum.

`@UnknownValue` applies to the record codec. It does not change the generated flyweight enum: its typed accessor still rejects unknown values. Use the flyweight’s raw field accessor when you need the original encoded value.

## Current limitation

Constant enum fields declared through `presence = CONSTANT` and `valueRef` are supported for schema and flyweight generation, but not yet by record codecs. Schemas using them must currently set `codecs = false`.
