# Retire a field

SBE never removes a field. Once a field has been in a released version of a schema, its bytes stay at their offset in every later version, so that older readers keep finding the fields they know where they expect them. A field can be marked `deprecated`, but it stays on the wire.

Without further help, that field would also stay in the record forever: every caller would have to supply a value nobody reads. This how-to takes a field out of the record while keeping it on the wire.

It uses the `com.example.quotes` schema of the example module, whose `Quote` gained a `tradeCount` in version 2 and retires it in version 4.

## Deprecate the field

Raise the schema version, and mark the field deprecated in that version:

```java
@SbeField(id = 7, primitiveType = UINT32, sinceVersion = 2, deprecated = 4, description = "Trades of the session so far") @Nullable Long tradeCount
```

This changes nothing on the wire. Readers of every version keep working, and the schema records when the field stopped mattering.

## Move it out of the record

Declare the field on the message instead of on a component, complete with its `name` and its type, since there is no component to take them from:

```java
@SbeMessage(
        id = 1,
        layout = {"instrumentId", "bid", "ask", "bidSize", "askSize", "sequence", "tradeCount", "vwap", "venue", "state", "flags"},
        unmapped = @SbeField(id = 7, name = "tradeCount", primitiveType = UINT32, sinceVersion = 2, deprecated = 4, description = "Trades of the session so far"))
public record Quote(
        @SbeField(id = 1) long instrumentId,
        @SbeField(id = 2) long bid,
        @SbeField(id = 3) long ask,
        @SbeField(id = 4, primitiveType = UINT32) long bidSize,
        @SbeField(id = 5, primitiveType = UINT32) long askSize,
        @SbeField(id = 6, primitiveType = UINT64, sinceVersion = 1) long sequence,
        @SbeField(id = 8, presence = OPTIONAL, sinceVersion = 2) @Nullable Double vwap,
        @SbeField(id = 9, sinceVersion = 3) @Nullable Venue venue,
        @SbeField(id = 10, sinceVersion = 3) @Nullable MarketState state,
        @SbeField(id = 11, sinceVersion = 3) @Nullable Set<QuoteFlag> flags) {}
```

Two things happened:

* `unmapped` holds the field's full declaration. The schema still contains it, at the same place, with the same id and type.
* `layout` names the wire order. Without a component, the field has no place among the others, so the message says where every field lies: each component by its Java name, each unmapped field by its `name`, each exactly once.

The generated schema is byte for byte what it was, with the deprecation added:

```xml
<field name="sequence" id="6" type="uint64" sinceVersion="1" description="The sequence number of the update"/>
<field name="tradeCount" id="7" type="uint32" sinceVersion="2" deprecated="4" description="Trades of the session so far"/>
<field name="vwap" id="8" type="double" presence="optional" sinceVersion="2" description="..."/>
```

## What the codec does with it

Encoding writes the field's null value, `4294967295` for a `uint32`, `NaN` for a `double`, `NULL_VAL` for an enum, an empty set for a set. Every reader that knows the field sees a value it can recognise as no value. Decoding skips the field, whatever a writer of any version put there.

A message written by a version 3 writer, with a real trade count in it, decodes to the same `Quote` as one written today:

```java
Quote decoded = new QuoteCodec().decode(buffer, offset); // tradeCount is not read
```

## Fix the order without retiring anything

`layout` works on its own. A record's components lay out in declaration order, and an IDE can reorder record components in one keystroke without the compiler noticing, which silently moves every offset. A `layout` pins the order to a list nothing reorders by accident:

```java
@SbeMessage(id = 1, layout = {"orderId", "quantity"})
public record Order(
        @SbeField(id = 2) int quantity,
        @SbeField(id = 1) long orderId) {}
```

The components may then be declared in any order, and the record's canonical constructor keeps their declared order; the codec maps between the two. The compiler rejects a `layout` that misses a component, names one twice, or names something that does not exist.

## What cannot be retired yet

Groups and variable-length data cannot be declared under `unmapped`. That is planned.
