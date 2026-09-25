# Bindings

A binding lets a record hold a component as a type of its own rather than as the wire's face: a `BigDecimal` where the wire holds an `int64` mantissa, a `boolean` where it holds an enum, an `Instant` where it holds a count of milliseconds. The binding is Java only. It contributes nothing to the schema, another reader never sees it, and the component alone says which binding it wants over which wire. sbe-buddy ships none of its own: what a value means is the schema's and yours to decide.

## The interfaces

```java
public interface TypeBinding<J extends @Nullable Object, W> {

    W toWire(J value, BindingContext context);

    J fromWire(W wire, BindingContext context);

    interface OfLong<J extends @Nullable Object> {

        long toWire(J value, BindingContext context);

        J fromWire(long wire, BindingContext context);
    }

    // and OfByte, OfShort, OfInt, OfFloat, OfDouble in the same shape
}
```

`J` is the component's type. The face of the component's wire type decides the interface. A primitive face takes its specialization, so nothing is boxed on the way: `OfLong` for `int64`, `uint64` and `uint32`, `OfInt` for `int32` and `uint16`, `OfShort` for `int16` and `uint8`, `OfByte` for `int8` and `char`, `OfFloat` and `OfDouble`. Every other face takes the generic interface with the face as `W`: `String` for a `char` type with a length or text var-data, `byte[]` for a `uint8` array or bytes, `int[]` and the other arrays, the enum for an enum, `Set<E>` for a set, the record for a composite, and `List<E>` of the entry record for a group.

A binding is stateless and has a no-arg constructor the schema package can call, public when the class lives elsewhere; the codec holds one instance of each binding class it uses.

## The context

Every call hands the binding the `BindingContext` of the component it stands for, so one binding class serves every component it fits, reading from the schema what it would otherwise hard-code:

```java
public record BindingContext(
        String name,
        @Nullable PrimitiveType primitiveType,
        @Nullable String characterEncoding,
        @Nullable String epoch,
        @Nullable String timeUnit,
        @Nullable Presence presence) {}
```

* `name`: the component's name, as the codec's own messages give it, so a binding's exception says which field it refused.
* `primitiveType`: the wire primitive after a named type is resolved; an array's or a string's element, an enum's or a set's encoding; `null` for a composite, a group and binary var-data. A `long` face is an `int64`, a `uint64` or a `uint32`, and this tells which.
* `characterEncoding`: the type's, for a `char` array and text var-data, `US-ASCII` where the schema declares none, as sbe-tool fills it in; `null` otherwise, a single `char` included.
* `epoch` and `timeUnit`: the field's attributes as the schema writes them, `null` where the field leaves them out, and always `null` on a composite's member, where the schema has neither.
* `presence`: the field's or the member's, a field left at the default taking its named type's; `null` for a group and var-data, which have none. A binding over a face without a null value of its own reads it to tell an optional field from a required one.

sbe-buddy interprets none of it. `sbe.xsd` types `epoch` and `timeUnit` as free text: `unix`, the XSD's default for `epoch`, conventionally means a count from midnight, 1 January 1970, UTC, and `second`, `millisecond`, `microsecond` and `nanosecond` are the units sbe-tool documents, but any text is valid SBE, and a field without them has none. What `null` means is the binding's decision, and so is refusing a value it cannot interpret.

`timeUnit` on `@SbeField` is deprecated, as `sbe.xsd` deprecates it on `field`, where it was kept for schemas written against release candidate 2; javac warns wherever it is written. It still works, and still reaches the schema and the context. A unit that belongs to the type, a composite's member, constant or on the wire, is the form SBE 1.0 has; a binding over the composite reads it from the record.

The codec holds one context per bound component, built once from the schema; nothing is allocated per call.

## Declaring one

The quotes example holds prices as decimals over the mantissas the wire carries, scaled by the schema's constant exponent, and names the field when it refuses one:

```java
package com.example.quotes;

import java.math.BigDecimal;
import java.math.RoundingMode;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

public final class Price implements TypeBinding.OfLong<BigDecimal> {

    private static final int SCALE = 4;

    public Price() {
    }

    @Override
    public long toWire(BigDecimal value, BindingContext context) {
        try {
            return value.setScale(SCALE, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(context.name() + " has no wire form with " + SCALE + " decimals: " + value, e);
        }
    }

    @Override
    public BigDecimal fromWire(long wire, BindingContext context) {
        return BigDecimal.valueOf(wire, SCALE);
    }
}
```

A binding is a class of its own. A declaration, an `@SbeType`, `@SbeComposite`, `@SbeEnum` or `@SbeSet` class, never implements `TypeBinding`, and a binding never carries a declaration annotation; the compiler refuses both. What is schema and what is Java stay apart.

## Naming it on a component

`binding` names the binding beside what says the wire: `primitiveType` or `type` on a field, since a `BigDecimal` component decides nothing by itself:

```java
@SbeField(id = 2, primitiveType = INT64, binding = Price.class) BigDecimal bid,
@SbeField(id = 3, primitiveType = INT64, binding = Price.class) BigDecimal ask,
```

The schema does not change: `bid` and `ask` are `int64` fields as before. The codec calls `toWire` before the flyweight's setter and `fromWire` after its getter, so the record reads `new BigDecimal("1.0050")` where the wire holds `10050`. Both fields share the codec's one `Price` instance, each with its own context.

Every component that carries a value takes one:

| Where | Annotation | `W` |
| --- | --- | --- |
| A field, of any type | `@SbeField(binding = …)` | the type's face |
| Var-data | `@SbeData(binding = …)` | `String` or `byte[]` |
| A group | `@SbeGroup(binding = …)` | `List<E>`, `E` the entry record |
| A composite's inline member | `@SbeType(binding = …)` on the component | the member's face |
| A composite's ref | `@SbeRef(value = …, binding = …)` | the referred type's face |

A composite's inline enum, set or composite, written as a component of the nested type without an annotation, has nowhere to name a binding; an `@SbeRef` to the same type does.

## An enum as a boolean

An enum's face is the enum; a binding turns it into whatever the record holds. A yes or no on the wire becomes a `boolean`:

```java
@SbeEnum(primitiveType = UINT8)
enum Flag { @SbeEnumValue("0") NO, @SbeEnumValue("1") YES }

final class FlagBinding implements TypeBinding<Boolean, Flag> {

    public Flag toWire(Boolean value, BindingContext context) {
        return value ? Flag.YES : Flag.NO;
    }

    public Boolean fromWire(Flag wire, BindingContext context) {
        return wire == Flag.YES;
    }
}

@SbeField(id = 1, type = Flag.class, binding = FlagBinding.class) boolean urgent,
@SbeField(id = 2, type = Flag.class, presence = OPTIONAL, binding = FlagBinding.class) @Nullable Boolean acknowledged,
```

The unknown-value contract runs before the binding: a raw value no constant names is the enum's `@UnknownValue` constant or an `IllegalArgumentException`, so the binding sees only constants. An optional enum's null value is `null` in the record without calling it. A set works the same way over its `Set<E>`, into a record of flags or a bitmask.

sbe-tool's flyweights cannot take a valid value named after a Java keyword, `false` or `true` among them: the enum's constants are generated with its names, and sbe-tool refuses a keyword there. A schema that spells its boolean `false` and `true` needs sbe-tool's `sbe.keyword.append.token` system property set for javac, which sbe-buddy does not set.

## A group as a map

A group's face is the `List` of its entry record; a binding holds the entries however the record wants them, keyed by an id here, in the order the wire holds them:

```java
final class LegsBinding implements TypeBinding<Map<Integer, Leg>, List<Leg>> {

    public List<Leg> toWire(Map<Integer, Leg> value, BindingContext context) {
        return new ArrayList<>(value.values());
    }

    public Map<Integer, Leg> fromWire(List<Leg> wire, BindingContext context) {
        Map<Integer, Leg> legs = new LinkedHashMap<>();
        for (Leg leg : wire) {
            legs.put(leg.legId(), leg);
        }
        return legs;
    }
}

@SbeGroup(id = 10, binding = LegsBinding.class) Map<Integer, Leg> legs,
```

The entry record comes from the binding's `W`. `encodedLength` walks the entries, so `toWire` runs there and again in `encode`.

## A time read from its field

A binding over a count reads the unit and the epoch the field declares, and refuses what it cannot interpret:

```java
final class EpochTime implements TypeBinding.OfLong<Instant> {

    public long toWire(Instant value, BindingContext context) {
        return unit(context).between(Instant.EPOCH, value);
    }

    public Instant fromWire(long wire, BindingContext context) {
        return Instant.EPOCH.plus(wire, unit(context));
    }

    private static ChronoUnit unit(BindingContext context) {
        if (context.epoch() != null && !context.epoch().equals("unix")) {
            throw new IllegalArgumentException(context.name() + " counts from " + context.epoch());
        }
        if (context.timeUnit() == null) {
            throw new IllegalArgumentException(context.name() + " has no timeUnit");
        }
        return switch (context.timeUnit()) {
            case "second" -> ChronoUnit.SECONDS;
            case "millisecond" -> ChronoUnit.MILLIS;
            case "microsecond" -> ChronoUnit.MICROS;
            case "nanosecond" -> ChronoUnit.NANOS;
            default -> throw new IllegalArgumentException(context.name() + " has no time unit " + context.timeUnit());
        };
    }
}

@SbeField(id = 8, primitiveType = INT64, timeUnit = "millisecond", binding = EpochTime.class) Instant sentAt,
@SbeField(id = 9, primitiveType = INT64, timeUnit = "nanosecond", binding = EpochTime.class) Instant stampedAt,
```

One class, two precisions, both stated in the schema. `ChronoUnit.between` truncates; a binding that must not lose precision refuses a value with more of it.

## A UUID over a composite

SBE has no UUID of its own, so its layout is the schema's choice. Two `int64`, most significant first, is one:

```java
@SbeComposite
record OrderId(
        @SbeType(primitiveType = INT64) long msb,
        @SbeType(primitiveType = INT64) long lsb) {}

final class OrderIdBinding implements TypeBinding<UUID, OrderId> {

    public OrderId toWire(UUID value, BindingContext context) {
        return new OrderId(value.getMostSignificantBits(), value.getLeastSignificantBits());
    }

    public UUID fromWire(OrderId wire, BindingContext context) {
        return new UUID(wire.msb(), wire.lsb());
    }
}
```

Sixteen `uint8` in RFC 9562's octet order is another, which a reader in any language takes without knowing Java's two longs: a `byte[]` face and a binding over it.

## Absence, checks and exceptions

Where the wire has a null of its own, absence passes through as `null` without calling the binding. An optional scalar or enum, or a component added above the baseline, is `null` in the record when the wire holds the null value or the message predates it, and `null` in the record writes the null value; `toWire` and `fromWire` see only values. A required component that is `null` is refused before its binding is called.

An optional field of a composite, a set, or a type with a length has no null value on the wire: SBE allows `presence="optional"` there and leaves what null looks like to the schema. The codec hands such a field to `toWire` as it is, `null` included, and calls `fromWire` on whatever it reads; the binding writes its chosen representation of null and returns `null` when it reads it back. The trading example's prices are `null` when the mantissa is its null value, as SBE suggests for a composite whose first element is optional:

```java
public PriceEncoding toWire(@Nullable BigDecimal value, BindingContext context) {
    if (value == null) {
        if (context.presence() != Presence.OPTIONAL) {
            throw new IllegalArgumentException(context.name() + " is required");
        }
        return new PriceEncoding(null, PriceEncoding.EXPONENT);
    }
    // the mantissa at the exponent's scale, or an exception naming the field
}

public @Nullable BigDecimal fromWire(PriceEncoding wire, BindingContext context) {
    Long mantissa = wire.mantissa();
    return mantissa == null ? null : BigDecimal.valueOf(mantissa, -wire.exponent());
}
```

Appended in a later version, such a field is still `null` when the message predates it, without calling the binding, as any field is.

Without a binding, such a field is its face in the record and is `null` after a decode only when the message predates it; a `null` on the way out is refused although the field is optional, `price has no null value on the wire; a binding may write one`.

The checks the codec makes on the wire's face still apply to what the binding hands it: a string longer than its field, an array of the wrong length, a constant other than the schema's, compared on its wire side, is refused after `toWire`. A binding's own exception passes through the codec unwrapped.

## What the compiler refuses

* A `binding` class that implements neither `TypeBinding` nor one of its specializations, is abstract, or has no no-arg constructor the schema package can call.
* A `J` that is not the component's type. A primitive component matches its box.
* An interface that is not the face's: `Price binds the wire as int, but the face of int64 is long; implement TypeBinding.OfLong`, or the generic interface over a primitive face, which would box; for a group, `a group's binding must bind a List of a record`.
* A binding on an `unmapped` field or member, which has no component to bind.
* A binding on an `@SbeType` class, which declares a type rather than uses one.
* Two binding classes with one simple name in one message, since the codec names its instance after the class.

## Coverage

This page covers bindings on every component that carries a value. A binding over a [composite](composites.md) takes its record as `W`.
