# Composites

An SBE `composite` is a fixed layout of members, declared once in the schema's `types` and used by fields as one encoding: a price as mantissa and exponent, a timestamp as time and unit. In sbe-buddy, `@SbeComposite` declares it on a record whose components are its members, and a field of the composite holds that record.

## Declaration

```java
package com.example.quotes;

import static net.concini.sbebuddy.PrimitiveType.INT64;
import static net.concini.sbebuddy.PrimitiveType.UINT32;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite(description = "A trade as its price and its size")
public record Trade(
        @SbeType(primitiveType = INT64) long price,
        @SbeType(primitiveType = UINT32) long size) {}
```

Each component carries `@SbeType`, an inline `type` element with every attribute a named type takes, and its Java type is the face of that type: `long` for `int64` and for `uint32`, `String` for a `char` type with a length, an array for any other type with a length. The schema declares the composite once:

```xml
<composite name="Trade" description="A trade as its price and its size">
    <type name="price" primitiveType="int64"/>
    <type name="size" primitiveType="uint32"/>
</composite>
```

`@SbeComposite` takes `name`, `offset`, `semanticType`, `description`, `sinceVersion` and `deprecated`. The record is the named type and its own face, so it is also the domain type: it may carry whatever methods the domain wants, and nothing else is needed to hold it in a message.

## A field of a composite

```java
@SbeField(id = 15, sinceVersion = 6, description = "The last trade of the instrument") @Nullable Trade lastTrade
```

The component's own type names the composite; `type = Trade.class` is needed only where the component is something else, as with a binding below. The codec reads the members into a new `Trade` and writes a `Trade`'s members out, through the composite's own flyweight. A field of a composite may be `presence = OPTIONAL`, but the composite has no null value the codec knows: SBE says an optional composite is null when its first element is, a convention a [binding](bindings.md) applies. With a binding the codec hands it a `null` component and returns what it reads back; without one, it writes and reads the record, and refuses a `null` one, `lastTrade has no null value on the wire; a binding may write one`. A composite field is still absent, and `null`, when a message predates it, which is why `lastTrade` above is nullable.

## Members

A composite holds four kinds of member, and the corpus's `Quote` shows all of them:

```java
@SbeComposite
record Quote(
        @SbeRef Decimal bid,
        @SbeRef(offset = 9) Decimal ask,
        Side side,
        Set<Flags> flags,
        Stamp stamp) {

    @SbeEnum(primitiveType = CHAR, offset = 18)
    enum Side { @SbeEnumValue("B") Buy, @SbeEnumValue("S") Sell }

    @SbeSet(primitiveType = UINT8, offset = 19)
    enum Flags { @SbeChoice(0) firm }

    @SbeComposite(offset = 20)
    record Stamp(
            @SbeType(primitiveType = UINT64) long time,
            @SbeType(primitiveType = UINT8, presence = OPTIONAL, nullValue = "255") Short precision,
            @SbeType(primitiveType = CHAR, presence = CONSTANT, value = "Z") byte zone) {}
}
```

* An inline `type`, `@SbeType` on the component, as above. An optional member, `presence = OPTIONAL` with a `nullValue`, is boxed and decodes to `null` when the wire holds the null value. A constant member carries its `value` or a `valueRef`, takes no bytes, decodes to the constant and is checked on encode.
* A `ref`, `@SbeRef` on a component whose type is a composite, enum, set or type declared at the top level, which is the only place a `ref` resolves. The component is that declaration's face: the record for a composite, the enum, a `Set` of the set's enum, the type's face for a type. `value` names the declaration, `@SbeRef(Flags.class)`, and may be left out only where the component's type is the declaration itself, an enum or a composite, as with `Decimal` above.
* An inline enum, set or composite, a component whose type is an `@SbeEnum` or `@SbeComposite` nested in the record, or a `Set` of an `@SbeSet` nested in it. The declaration is written inside the composite in the schema, and the component is the nested type's face: the enum, the record, the `Set` of the set's enum.

Members lay out in component order, with `offset` where the schema says otherwise; sbe-tool computes the offsets, and the codec never knows them. A member's `sinceVersion` describes the schema; sbe-tool's composite flyweights carry no version guard, so a member is never absent by version, and the codec follows.

## Another Java shape

A field that wants the composite as a type of its own names a [binding](bindings.md) over the face record, the generic interface with the record as `W`:

```java
final class DecimalBinding implements TypeBinding<BigDecimal, Decimal> {

    public Decimal toWire(BigDecimal value, BindingContext context) {
        return new Decimal(value.unscaledValue().longValueExact(), (byte) -value.scale());
    }

    public BigDecimal fromWire(Decimal wire, BindingContext context) {
        return BigDecimal.valueOf(wire.mantissa(), -wire.exponent());
    }
}

@SbeField(id = 2, type = Decimal.class, binding = DecimalBinding.class) BigDecimal last
```

Decoding builds the `Decimal` from the flyweight and hands it to `fromWire`; encoding takes the `Decimal` from `toWire` apart. That is one short-lived record per bound field per call, on either side. Where that matters, make the composite record the domain type instead: it costs nothing, and the same composite may be a plain `Decimal` on one field and a `BigDecimal` on another.

## Layout and unmapped members

`layout` and `unmapped` on `@SbeComposite` work as on a message: `layout` names the members in wire order, each component by its Java name and each unmapped member by its `name`, exactly once, and `unmapped` holds complete `@SbeType` members no component carries, which the codec writes as their null value and never reads. A `ref` or an inline enum, set or composite cannot be unmapped yet.

## What the compiler refuses

* A member whose Java type is not the face of its type: `int is not the face of mantissa, which is long`.
* A primitive component on an optional member, `short cannot hold null, but the member can be absent; use Short`, and a warning for a box on a member that is never absent.
* A constant member without a `value` or a `valueRef`.
* A `ref` whose component is not the face of what it refers to.
* A field of a composite whose component is not the record, `Price is a composite; use Price`, or that is optional.
* A binding on a composite field that does not take the record as `W`.
* A member newer than its composite: `a composite cannot be extended in a later version; declare a new composite and append a field of it`.

A composite does not evolve. SBE has no way to grow one in place, since its size is part of every block that holds it, so a member's `sinceVersion` may not be above its composite's own. The official way is a new composite, carried by a new field appended to the message or the entry; the old field stays as it is, or retires through `unmapped`, which the schema and the flyweights take but the codec does not yet: an unmapped field of a composite type needs `codecs = false` on `@SbeSchema`. Where the message itself should change, a new message over the new composite replaces it, and a [union](unions.md) reads both. A composite introduced in a later version, `@SbeComposite(sinceVersion = 7)`, brings its members along at that version.

## Coverage

This page covers composites as fields of a message. The message header is a composite the schema names on `@SbeSchema`, and has [its own page](headers.md); a group's dimension type is one `@SbeGroup` names. A composite's inline member and ref take a [binding](bindings.md) of their own, as a field does.
