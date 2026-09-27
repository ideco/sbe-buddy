# Named types, strings and arrays

`@SbeType` declares a reusable SBE primitive encoding. It can specify a
fixed length, character encoding, bounds, a null sentinel or a constant.
The annotated class describes the encoding; record components carry its
Java representation, such as a `String` or `long[]`.

## Declaration and schema

In a schema package, `Symbol.java` declares an eight-byte ASCII encoding:

```java
package com.example.trading;

import net.concini.sbebuddy.PrimitiveType;
import net.concini.sbebuddy.SbeType;

@SbeType(primitiveType = PrimitiveType.CHAR, length = 8,
        characterEncoding = "US-ASCII")
public final class Symbol {
    private Symbol() {}
}
```

A message or group record uses this component declaration, with `SbeField`
imported:

```java
@SbeField(id = 1, type = Symbol.class) String symbol
```

The schema's type declaration and the field referring to it are:

```xml
<type name="Symbol" primitiveType="char" length="8" characterEncoding="US-ASCII"/>
```

```xml
<field name="symbol" id="1" type="Symbol"/>
```

## Java representations

With the default `length = 1`, a type uses its
[scalar representation](fields.md#java-representations). With a length
greater than one, it uses these representations:

| Element encoding | Component type |
| --- | --- |
| `char` | `String` |
| `int8`, `uint8` | `byte[]` |
| `int16` | `short[]` |
| `int32`, `uint16` | `int[]` |
| `int64`, `uint32`, `uint64` | `long[]` |
| `float` | `float[]` |
| `double` | `double[]` |

For example, `Depth.java` describes three unsigned quantities:

```java
package com.example.trading;

import net.concini.sbebuddy.PrimitiveType;
import net.concini.sbebuddy.SbeType;

@SbeType(primitiveType = PrimitiveType.UINT32, length = 3)
public final class Depth {
    private Depth() {}
}
```

Its component declaration is:

```java
@SbeField(id = 2, type = Depth.class) long[] sizes
```

```xml
<type name="Depth" primitiveType="uint32" length="3"/>
```

An array must have exactly the declared length. Decoding creates a new
array. Java records compare array components by identity, so a round-trip
test should compare their contents, or the record should define the equality
behavior your application needs.

## Fixed-length text

The length counts bytes. Encoding pads unused space with zero bytes;
decoding stops at the first zero byte. A value containing that byte will
therefore not round-trip intact.

The default character encoding is US-ASCII. The codec rejects a string that
does not fit or contains characters the selected encoding cannot represent.
Other JDK-supported encodings, including UTF-8, can be used when they work
with zero-byte padding. Encodings such as UTF-16, which put zero bytes
inside characters, are not supported by record codecs for fixed-length
strings. Use [variable-length data](variable-length-data.md) when the
payload needs a length prefix instead.

## Constants

`PriceExponent.java` declares a constant supplied by the schema:

```java
package com.example.trading;

import net.concini.sbebuddy.Presence;
import net.concini.sbebuddy.PrimitiveType;
import net.concini.sbebuddy.SbeType;

@SbeType(primitiveType = PrimitiveType.INT8,
        presence = Presence.CONSTANT, value = "-4")
public final class PriceExponent {
    private PriceExponent() {}
}
```

```java
@SbeField(id = 3, type = PriceExponent.class) byte exponent
```

```xml
<type name="PriceExponent" primitiveType="int8" presence="constant">-4</type>
```

A constant occupies no bytes. The record still carries its component:
decoding supplies `-4`, and encoding rejects any other value. Constants are
not absent when decoding older versions. A constant can also be supplied
by an enum value using `valueRef`; the `SbeType` and `SbeField` API Javadoc
describes that form.

## Presence and validation

A named scalar can declare `presence`, `nullValue`, `minValue` and
`maxValue`. Fields inherit its presence unless they specify optional or
constant presence themselves. Numeric bounds are schema declarations; the
codec does not add range checks.

Strings and arrays have no scalar null sentinel. An optional field of
either type needs a [binding](bindings.md#absence) to encode Java `null`.
Without one, null is rejected on encoding; decoding returns null only when
an older message predates the field. Empty strings and zero-filled arrays
are ordinary values.

The `SbeType` API Javadoc covers individual settings. For a member declared
inside a composite, see [Composites](composites.md).
