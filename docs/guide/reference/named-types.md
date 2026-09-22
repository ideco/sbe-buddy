# Named types

An SBE `type` is a named encoding: a primitive with a length, a character encoding, a null value, a range or a constant value, declared once in the schema's `types` and referenced by fields. In sbe-buddy, `@SbeType` declares the encoding on a `final` class, and a field names it with `type`.

## Declaration

```java
package com.example.quotes;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeType;

/** An instrument symbol, eight ASCII characters padded with NUL. */
@SbeType(primitiveType = CHAR, length = 8, characterEncoding = "US-ASCII", semanticType = "String", description = "An instrument symbol, NUL padded")
public final class Symbol {

    private Symbol() {
    }
}
```

The class carries nothing but the annotation; its name is the type's wire name unless `name` says otherwise. A field of the type names it:

```java
@SbeField(id = 12, type = Symbol.class, sinceVersion = 5, description = "The instrument's symbol") @Nullable String symbol
```

In the schema, the type is declared once and the field refers to it:

```xml
<type name="Symbol" primitiveType="char" length="8" characterEncoding="US-ASCII" semanticType="String" description="An instrument symbol, NUL padded"/>
...
<field name="symbol" id="12" type="Symbol" sinceVersion="5" description="The instrument's symbol"/>
```

`@SbeType` takes every attribute the XSD gives a `type`: `primitiveType`, `length`, `characterEncoding`, `presence`, `nullValue`, `minValue`, `maxValue`, `value`, `valueRef`, `offset`, `semanticType`, `description`, `sinceVersion` and `deprecated`. The class may live in any package; the schema references it by class.

## Scalars

A type of `length` 1 is a scalar, and a field of it has the face of its primitive, `long` for `int64`, `int` for `uint16`, exactly as a field that names the primitive directly. What the type adds is what it declares:

```java
@SbeType(primitiveType = UINT32, presence = OPTIONAL, nullValue = "4294967294", description = "Absent when the size is not disclosed")
public final class Quantity {}

@SbeField(id = 3, type = Quantity.class) @Nullable Long quantity
```

A field without a `presence` of its own takes the type's, so `quantity` is optional without saying so, and the codec writes and reads the type's null value. `minValue` and `maxValue` describe the type in the schema; the codec adds no range check.

## Strings

A `char` type with a `length` is a fixed-length string, and a field of it is a `String`. Decoding reads the bytes up to the first NUL; encoding pads with NUL. The codec refuses, with `IllegalArgumentException`, a string longer than the field and a string with a character above 127, which the flyweight would otherwise write as `?`:

```java
codec.encode(quoteWithSymbol("LONGNAME1"), buffer, 0); // symbol is longer than 8: LONGNAME1
```

The character encoding is `US-ASCII` unless the type says otherwise. A `char` type without a `length` is a single byte, and its field is a `byte`. A string in an encoding other than ASCII is a construct the codec does not cover yet; the compiler names it, and `codecs = false` on `@SbeSchema` keeps the flyweights.

## Arrays

Any other primitive with a `length` is a fixed-length array, and a field of it is the array of its element's face: `short[]` for `int16`, `int[]` for `int32` and `uint16`, `long[]` for `int64`, `uint32` and `uint64`, `float[]` and `double[]`. An `int8` or `uint8` array is `byte[]`, the bytes as they are.

```java
@SbeType(primitiveType = UINT32, length = 5, description = "The sizes at the five best levels, best first")
public final class Depth {}

@SbeField(id = 14, type = Depth.class, sinceVersion = 5, description = "The bid sizes at the five best levels") long @Nullable [] bidDepth
```

The array is exactly `length` long: the codec refuses any other length with `IllegalArgumentException`, and decoding allocates a fresh array of that length. A Java record compares an array component by identity, so a record with an array is equal only to itself unless it overrides `equals`; a test compares such records field by field.

A field of a type with a length cannot be `presence = OPTIONAL`: SBE gives an array no null value, and the compiler rejects it. A field added in a later version is still absent, and `null`, when an older message is decoded, which is why `bidDepth` above is nullable.

## Constants

A type with `presence = CONSTANT` carries its value in the schema and no bytes on the wire. The value is the type's `value`, or a `valueRef` naming an enum's constant, `Side.BUY`:

```java
@SbeType(primitiveType = INT8, presence = CONSTANT, value = "-4", description = "The power of ten every price is scaled by")
public final class PriceExponent {}

@SbeField(id = 13, type = PriceExponent.class, sinceVersion = 5, description = "The exponent of bid, ask and vwap") byte priceExponent
```

The record still carries the component. Decoding fills it with the constant, whatever the message's version, so a constant is never absent and its component is never nullable. Encoding requires the component to equal the constant and refuses anything else with `IllegalArgumentException`: a record cannot claim what the schema contradicts.

A constant `char` type whose `value` is longer than one character is a string constant, and its field a `String`; a field with `presence = CONSTANT` and a `valueRef` may name an enum type directly, and its component is the enum. A constant field must have one or the other, a `valueRef` or a constant type, which the compiler checks.

## Coverage

This page covers named types on message fields: scalars, strings, arrays and constants. A named type on a [composite](composites.md)'s component follows the same rules; a field that wants the type as a Java type of its own names a [binding](bindings.md).
