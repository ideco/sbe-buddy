# Sets

An SBE set is a bit set: a fixed-width unsigned primitive whose bits are named. In sbe-buddy, `@SbeSet` declares the encoding on a Java enum, `@SbeChoice` gives each constant its bit, and a field of the set is a `Set` of that enum.

## Declaration

```java
package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeChoice;
import net.concini.sbebuddy.SbeSet;

@SbeSet(primitiveType = UINT8)
public enum OrderFlag {

    @SbeChoice(value = 0, name = "postOnly")
    POST_ONLY,

    @SbeChoice(value = 1, name = "hidden")
    HIDDEN,

    @SbeChoice(value = 2, name = "allOrNone")
    ALL_OR_NONE
}
```

Use a `Set` of the enum as a record component in an SBE schema package:

```java
package com.example.trading;

import java.util.Set;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
public record OrderEntry(
        @SbeField(id = 1) long orderId,
        @SbeField(id = 2) Set<OrderFlag> flags) {}
```

The component type identifies the set; `@SbeField(type = OrderFlag.class)` is unnecessary. The component must be a `Set`: `EnumSet` or the bare enum is a compilation error.

The set declaration produces this schema fragment:

```xml
<set name="OrderFlag" encodingType="uint8">
    <choice name="postOnly">0</choice>
    <choice name="hidden">1</choice>
    <choice name="allOrNone">2</choice>
</set>
```

## Encoding and choices

Specify exactly one encoding on `@SbeSet`:

| Member | Meaning |
|---|---|
| `primitiveType` | `UINT8`, `UINT16`, `UINT32` or `UINT64` |
| `encodingType` | A declared `@SbeType` class of one of those |

Unlike an [enum](enums.md), a set takes only the unsigned primitives; `CHAR` and the signed types are a compilation error.

`@SbeChoice.value` is the bit’s zero-based position, not its mask. `POST_ONLY` and `ALL_OR_NONE` above are bits 0 and 2, so a set of the two encodes as `5`. A bit must fit the encoding — bit 8 of a `UINT8` set is a compilation error — and bits must be unique within the set.

By default, the schema uses the Java enum’s simple name and its constants’ names. Override these with `@SbeSet(name = "...")` and `@SbeChoice(value = ..., name = "...")`. A wire name in Java’s own case is worth giving: it decides how the generated flyweight’s choice methods read.

Reordering Java constants does not change their bits.

## Absence

A field is absent when it is optional and holds the null value, or when it was introduced above the schema’s `baselineVersion` and the message being decoded predates it. Absence decodes to `null`.

A set has no null value, so the first cause does not apply: every bit pattern is a set, and none is spare to mean absent. A set field may still be `presence = OPTIONAL`, as SBE allows; a [binding](bindings.md) then decides what represents null, no bit set, say, and without one the codec refuses a `null` component. To the codec a set with no bits is the empty set, not absence.

The second cause applies as it does to any field: a set field introduced above the baseline decodes to `null` from a message that predates it, although the field is required.

| Situation | Codec behaviour |
|---|---|
| Encode a set | Writes one bit per choice the set contains |
| Encode the empty set | Writes zero |
| Decode zero | Returns an empty `Set` |
| Encode `null` | Throws `IllegalArgumentException` |

## Unknown values

A wire value may set a bit that no `@SbeChoice` names, for example when a newer writer adds a flag.

Decoding such a value throws `IllegalArgumentException`, identifying the set and the raw value. A `Set` of the enum has no constant to carry the bit, so there is no fallback to designate: `@UnknownValue`, which an [enum](enums.md) may use, is a compilation error on a set constant.

Keeping an unknown bit therefore means reading the field through the flyweight rather than through the record codec.

## Schema metadata

The annotations expose the corresponding SBE attributes:

| Annotation | Member | Default |
|---|---|---|
| `@SbeSet` | `name` | Java enum name |
| | `primitiveType` / `encodingType` | Neither selected; exactly one required |
| | `offset` | `0` |
| | `semanticType`, `description` | Unspecified |
| | `sinceVersion`, `deprecated` | `0` |
| `@SbeChoice` | `value` | Required |
| | `name` | Java constant name |
| | `description` | Unspecified |
| | `sinceVersion`, `deprecated` | `0` |

A set field has no constant form: `valueRef` names a value of an enum, and sbe-tool rejects it on a set.

## Direct flyweight access

SBE generates a decoder and an encoder for the set itself in the schema’s `.sbe` package, separate from the message flyweights. The decoder has `getRaw()`, `isEmpty()` and a `boolean` method per choice; the encoder has `clear()`, `setRaw()` and a `boolean` setter per choice.

The record codec writes through that pair, clearing it and setting one bit per choice, and reads back into an `EnumSet`. It accepts any `Set` implementation when encoding.

A choice’s methods are named from its wire name with the first character lowercased, so a choice left at a constant name like `ALL_OR_NONE` becomes `aLL_OR_NONE()`. The wire names given above keep them readable as `allOrNone()`.

## Bindings

A field of a set may hold another type through a [binding](bindings.md) over its `Set<E>`, a record of flags or a bitmask. A bit no choice names is refused before the binding is called.
