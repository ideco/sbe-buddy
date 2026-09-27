# Composites

`@SbeComposite` declares a fixed-layout SBE type as a Java record. A field
of that type holds the composite record, or an application value supplied
through a binding.

## Declaration and schema

In a schema package, `PriceEncoding.java` describes a price with four
decimal places:

```java
package com.example.trading;

import static net.concini.sbebuddy.Presence.CONSTANT;
import static net.concini.sbebuddy.PrimitiveType.INT64;
import static net.concini.sbebuddy.PrimitiveType.INT8;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite
public record PriceEncoding(
        @SbeType(primitiveType = INT64) long mantissa,
        @SbeType(primitiveType = INT8, presence = CONSTANT, value = "-4")
        byte exponent) {

    public static final byte EXPONENT = -4;
}
```

A message or group record uses this component, with `SbeField` imported:

```java
@SbeField(id = 1) PriceEncoding price
```

The schema contains:

```xml
<composite name="PriceEncoding">
    <type name="mantissa" primitiveType="int64"/>
    <type name="exponent" primitiveType="int8" presence="constant">-4</type>
</composite>
```

```xml
<field name="price" id="1" type="PriceEncoding"/>
```

## Java representation and members

The composite record is both the schema declaration and the value used by
the codec. Decoding constructs the record; encoding reads its components.
The example occupies eight bytes because the exponent is a constant.

Members can be declared in these forms:

| SBE member | Java declaration |
| --- | --- |
| Inline primitive, string or array type | `@SbeType` on the component |
| Reference to a named type | `@SbeRef` on the component |
| Inline enum or composite | A component whose annotated type is nested directly in the composite record |
| Inline set | A `Set<E>` component whose `@SbeSet` enum is nested directly in the composite record |

For example, `PriceRange.java` uses the named composite twice:

```java
package com.example.trading;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeRef;

@SbeComposite
public record PriceRange(
        @SbeRef PriceEncoding lower,
        @SbeRef PriceEncoding upper) {}
```

```xml
<composite name="PriceRange">
    <ref name="lower" type="PriceEncoding"/>
    <ref name="upper" type="PriceEncoding"/>
</composite>
```

`@SbeRef` can infer the declaration from an enum or composite component.
For a named primitive encoding or set, specify its class explicitly, such
as `@SbeRef(Symbol.class)` on a `String` component. A reference names a
top-level schema type.

## Presence and absence

An optional scalar member uses a boxed component and maps its null sentinel
to Java null. A constant member is supplied on decode and checked on encode.

Making a field of a composite optional does not give the whole record a
null sentinel. A [binding](bindings.md#absence) must define that conversion,
for example using an optional first member to represent a null composite.
Without a binding, the field decodes to a composite record and encoding null
is rejected. A field missing from an older message still decodes to null.

## Layout and evolution

Members follow component order unless `layout` specifies the wire order.
Offsets can leave padding. `unmapped` can retain inline `@SbeType` members
without record components; it cannot declare an unmapped reference or
inline enum, set or composite.

Composite members are not conditionally decoded by acting version. A
composite cannot gain members in a later version: declare a new composite
and append a field using it instead. See
[Schema versions and absence](../concepts/schema-versions-and-absence.md).

The `SbeComposite`, `SbeType` and `SbeRef` API Javadoc covers individual
settings. For a conversion to an application type, follow
[Use BigDecimal for a price](../how-to/use-bigdecimal-for-a-price.md).
