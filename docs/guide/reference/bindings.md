# Bindings

A binding lets a record hold a field as a type of its own rather than as the wire's face: a `BigDecimal` where the wire holds an `int64` mantissa, a `Ticker` record where it holds eight `char`. The binding is Java only. It contributes nothing to the schema, another reader never sees it, and the field alone says which binding it wants over which wire.

## The interface

```java
public interface TypeBinding<J, W> {

    W toWire(J value);

    J fromWire(W wire);
}
```

`J` is the component's type. `W` is the face of the field's wire type, boxed where the face is a primitive, because a type argument is: `Long` for `int64`, `Integer` for `uint16`, `String` for a `char` type with a length, `byte[]` for a `uint8` array. A binding is stateless and has a no-arg constructor the schema package can call, public when the class lives elsewhere; the codec holds one instance of each binding class it uses.

## Declaring one

The quotes example holds prices as decimals over the mantissas the wire carries, scaled by the schema's constant exponent:

```java
package com.example.quotes;

import java.math.BigDecimal;
import java.math.RoundingMode;

import net.concini.sbebuddy.TypeBinding;

public final class Price implements TypeBinding<BigDecimal, Long> {

    private static final int SCALE = 4;

    public Price() {
    }

    @Override
    public Long toWire(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
    }

    @Override
    public BigDecimal fromWire(Long wire) {
        return BigDecimal.valueOf(wire, SCALE);
    }
}
```

A binding is a class of its own. A declaration, an `@SbeType`, `@SbeComposite`, `@SbeEnum` or `@SbeSet` class, never implements `TypeBinding`, and a binding never carries a declaration annotation; the compiler refuses both. What is schema and what is Java stay apart.

## Naming it on the field

`binding` on `@SbeField` names the binding, beside `primitiveType` or `type`, which still say what the wire holds, since a `BigDecimal` component decides nothing by itself:

```java
@SbeField(id = 2, primitiveType = INT64, binding = Price.class) BigDecimal bid,
@SbeField(id = 3, primitiveType = INT64, binding = Price.class) BigDecimal ask,
```

The schema does not change: `bid` and `ask` are `int64` fields as before. The codec calls `toWire` before the flyweight's setter and `fromWire` after its getter, so the record reads `new BigDecimal("1.0050")` where the wire holds `10050`. Both fields share the codec's one `Price` instance.

The same binding serves a field of a named type, since the type's face is what it binds:

```java
@SbeType(primitiveType = INT64)
final class Cents {}

@SbeField(id = 1, type = Cents.class, binding = CentsBinding.class) BigDecimal price,
@SbeField(id = 2, primitiveType = INT64, binding = CentsBinding.class) BigDecimal fee,
```

## A wrapper record

A domain type is often a record around the face. The binding is then two lines each way:

```java
record Ticker(String value) {}

final class TickerBinding implements TypeBinding<Ticker, String> {

    public String toWire(Ticker value) {
        return value.value();
    }

    public Ticker fromWire(String wire) {
        return new Ticker(wire);
    }
}

@SbeField(id = 4, type = Symbol.class, binding = TickerBinding.class) Ticker symbol,
```

The same `Symbol` may be a plain `String` on another message. Nothing ties `Ticker` to `Symbol`: a `Ticker` that arrives as six characters in another schema takes another binding over that type.

## Absence, checks and exceptions

Absence passes through as `null` without calling the binding. An optional field, or one added above the baseline, is `null` in the record when the wire holds the null value or the message predates the field, and `null` in the record writes the null value; `toWire` and `fromWire` see only values.

The checks the codec makes on the wire's face still apply to what the binding hands it: a string longer than its field, or an array of the wrong length, is refused after `toWire`. A binding's own exception passes through the codec unwrapped, as `Price` above lets `BigDecimal` refuse a price with more decimals than the exponent allows.

## What the compiler refuses

* A `binding` class that does not implement `TypeBinding`, is abstract, or has no no-arg constructor the schema package can call.
* A `J` that is not the component's type. A primitive component matches its box.
* A `W` that is not the face of the field's wire type: `CentsBinding binds the wire as Long, but the face of int32 is Integer`.
* A binding on a field of an enum or a set, whose faces are the user's types already.
* A binding on an `unmapped` field, which has no component to bind.
* Two binding classes with one simple name in one message, since the codec names its instance after the class.

## Coverage

This page covers bindings over a primitive's, a string's and an array's face. Bindings over composites and variable-length data, and the built-in bindings for the JDK's `UUID` and time types, have their own pages.
