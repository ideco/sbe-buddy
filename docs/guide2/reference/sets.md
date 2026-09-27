# Sets

`@SbeSet` declares named bits in an unsigned encoding. A Java enum names
the choices, and a record component holds a `Set` of those choices.

## Declaration and schema

In a schema package, `OrderFlag.java` can declare:

```java
package com.example.trading;

import net.concini.sbebuddy.PrimitiveType;
import net.concini.sbebuddy.SbeChoice;
import net.concini.sbebuddy.SbeSet;

@SbeSet(primitiveType = PrimitiveType.UINT8)
public enum OrderFlag {
    @SbeChoice(value = 0, name = "postOnly") POST_ONLY,
    @SbeChoice(value = 2, name = "hidden") HIDDEN
}
```

A message or group record uses this component, with `java.util.Set` and
`SbeField` imported:

```java
@SbeField(id = 1) Set<OrderFlag> flags
```

The schema contains:

```xml
<set name="OrderFlag" encodingType="uint8">
    <choice name="postOnly">0</choice>
    <choice name="hidden">2</choice>
</set>
```

```xml
<field name="flags" id="1" type="OrderFlag"/>
```

## Java representation and encoding

Declare the component as `Set<OrderFlag>`, not `EnumSet<OrderFlag>` or
`OrderFlag`. You can supply any `Set` implementation; decoding creates an
`EnumSet`. A binding can select another application representation.

Choice values are zero-based bit positions, not masks. The example's two
choices together encode as `5`: bits 0 and 2 are set. Choice positions must
be unique and fit the encoding. Reordering the Java constants does not
change their bits, although schema-first also checks declaration order.

Choose `UINT8`, `UINT16`, `UINT32` or `UINT64` as `primitiveType`, or use
`encodingType` to name an `@SbeType` over one of those primitives. Specify
exactly one of the two. Choice names become names in the generated
flyweight API; `postOnly` produces a readable `postOnly()` accessor.

## Presence and unknown bits

An empty set encodes zero and decodes to an empty set. It is not absent.
A set has no scalar null sentinel. An optional set field needs a
[binding](bindings.md#absence) to define a null representation; without
one, encoding null is rejected even if the field is optional.

A field missing from an older message still decodes to null, as described
in [Schema versions and absence](../concepts/schema-versions-and-absence.md).

If an encoded bit has no declared choice, decoding throws
`IllegalArgumentException`. There is no fallback choice: `@UnknownValue`
applies only to enums. A binding receives the checked set and cannot
intercept unknown bits. Use direct flyweight access when you need to retain
them.

## Related rules

A set field cannot be constant. A constant selected through `valueRef`
refers to an enum value, not a combination of set choices.

The `SbeSet` and `SbeChoice` API Javadoc covers individual settings.
[Enums](enums.md) represent a single choice; sets represent combinations.
