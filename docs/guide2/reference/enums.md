# Enums

`@SbeEnum` declares an SBE enumeration using a Java enum. Each constant has
an explicit wire value; Java ordinals are not used.

## Declaration and schema

In a schema package, `Side.java` can declare:

```java
package com.example.trading;

import net.concini.sbebuddy.PrimitiveType;
import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;
import net.concini.sbebuddy.UnknownValue;

@SbeEnum(primitiveType = PrimitiveType.UINT8)
public enum Side {
    @SbeEnumValue("1") BUY,
    @SbeEnumValue("2") SELL,
    @UnknownValue UNKNOWN
}
```

A message or group record uses the enum directly, with `SbeField` imported:

```java
@SbeField(id = 1) Side side
```

The schema contains:

```xml
<enum name="Side" encodingType="uint8">
    <validValue name="BUY">1</validValue>
    <validValue name="SELL">2</validValue>
</enum>
```

```xml
<field name="side" id="1" type="Side"/>
```

`UNKNOWN` is a Java fallback and adds no value to the schema.

## Java representation and encoding

The component uses the annotated enum. `type = Side.class` is unnecessary
unless a binding changes the component's application type.

Choose exactly one of `primitiveType` or `encodingType` on `@SbeEnum`.
Supported primitives are `CHAR`, `INT8`, `UINT8`, `INT16`, `UINT16` and
`INT32`; `encodingType` names an `@SbeType` declaration over one of them.
Values must fit the encoding and be unique.

`@SbeEnumValue` takes text: `"1"` for an integer encoding or `"B"` for
`CHAR`. The enum and its constants use their Java names in the schema unless
`name` overrides them. Reordering constants does not change their wire
values, although schema-first also checks declaration order.

## Presence and unknown values

Fields are required unless optional presence is selected. An optional enum
field maps its null sentinel to Java `null`; encoding null writes that
sentinel. A required field rejects null when encoding.

An unknown wire value is different from absence:

| Encoded value in the example | Decoded component |
| --- | --- |
| `1` | `Side.BUY` |
| `2` | `Side.SELL` |
| An undeclared value such as `3` | `Side.UNKNOWN` |
| Null sentinel in an optional field | `null` |
| Null sentinel in a required field | `Side.UNKNOWN` |

Without `@UnknownValue`, an unknown value throws `IllegalArgumentException`.
Every constant must have `@SbeEnumValue` or `@UnknownValue`; at most one
constant can be the fallback.

The fallback does not retain the original wire value and cannot be encoded.
A record containing `UNKNOWN` cannot therefore forward that value unchanged.
Use the flyweight's raw accessor when retaining the encoded value matters.
The fallback affects the record codec, not the generated flyweight enum.

## Related rules

A field introduced after the baseline can also decode to null from an older
message; see [Schema versions and absence](../concepts/schema-versions-and-absence.md).
A [binding](bindings.md) receives the decoded enum constant after unknown
values have been handled. It cannot recover the raw value behind a fallback.

The `SbeEnum`, `SbeEnumValue` and `UnknownValue` API Javadoc covers individual
settings. For combinations of independent flags, see [Sets](sets.md).
