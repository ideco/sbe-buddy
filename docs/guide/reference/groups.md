# Groups

An SBE `group` is a repeating block: a count on the wire followed by that many entries, each a fixed block of fields that may hold groups of its own. In sbe-buddy, `@SbeGroup` declares it on a `List<E>` component, and `E` is a record whose components are the entry's fields and groups, written by the rules a message's body follows.

## Declaration

The quotes example appends the venues' own quotes behind the best bid and offer:

```java
@SbeGroup(id = 16, sinceVersion = 7, description = "The venues' own quotes behind the best") @Nullable List<Contributor> contributors
```

The entry is an ordinary record. Its components carry `@SbeField` as a message's do, and every shape a message's field can take is open to it: a primitive, an enum, a set, a composite, a named type, a string, an array, a constant, an optional field, a binding.

```java
package com.example.quotes;

import static net.concini.sbebuddy.PrimitiveType.INT64;
import static net.concini.sbebuddy.PrimitiveType.UINT32;

import java.math.BigDecimal;

import net.concini.sbebuddy.SbeField;

public record Contributor(
        @SbeField(id = 17, description = "The venue quoting") Venue venue,
        @SbeField(id = 18, primitiveType = INT64, binding = Price.class) BigDecimal bid,
        @SbeField(id = 19, primitiveType = INT64, binding = Price.class) BigDecimal ask,
        @SbeField(id = 20, primitiveType = UINT32) long bidSize,
        @SbeField(id = 21, primitiveType = UINT32) long askSize) {}
```

The schema writes the group inside the message, after its fields:

```xml
<group name="contributors" id="16" sinceVersion="7" description="The venues' own quotes behind the best">
    <field name="venue" id="17" type="Venue" description="The venue quoting"/>
    <field name="bid" id="18" type="int64"/>
    <field name="ask" id="19" type="int64"/>
    <field name="bidSize" id="20" type="uint32"/>
    <field name="askSize" id="21" type="uint32"/>
</group>
```

`@SbeGroup` takes `id`, `name`, `dimensionType`, `blockLength`, `semanticType`, `description`, `sinceVersion` and `deprecated`, and the Java side's `layout` and `unmapped` for the entry's body. The entry record may be nested in the message's record or declared on its own; its field ids are a namespace of their own, though keeping them unique across the message reads better. Groups follow every field of a message and precede its var-data, in each message and in each entry.

## Nested groups

An entry may hold groups, however deep. The corpus's `Groups` message has a group of legs, each with its allocations:

```java
@SbeMessage(id = 1)
record Groups(
        @SbeField(id = 1) long orderId,
        @SbeGroup(
                id = 10, blockLength = 8, semanticType = "NoLegs",
                description = "The legs of a multi-leg order",
                layout = {"legId", "legRatio", "allocations"},
                unmapped = @SbeField(id = 15, name = "legRatio", primitiveType = UINT8, deprecated = 1)
        ) List<Leg> legs,
        @SbeGroup(id = 20, sinceVersion = 1) List<Fill> fills
) {

    record Leg(
            @SbeGroup(id = 12, dimensionType = SmallGroupSizeEncoding.class) List<Allocation> allocations,
            @SbeField(id = 11) int legId
    ) {

        record Allocation(
                @SbeField(id = 13) int account,
                @SbeField(id = 14, presence = OPTIONAL) Integer share
        ) {
        }
    }

    record Fill(
            @SbeField(id = 21, type = Cents.class, binding = CentsBinding.class) BigDecimal price
    ) {
    }
}
```

A `Leg` is a list of `Allocation` and a field, in whichever order the record likes: entries are addressed by offset from their start as a message's block is, so the record's component order and the wire's are independent, and `layout` names the wire order when they differ.

## Dimensions and block length

Every group opens with its dimensions, a composite of `blockLength` and `numInGroup`. The default is SBE's standard `groupSizeEncoding`, two `uint16`, which the api provides and the schema writes for you; `dimensionType` names another, such as the corpus's `smallGroupSizeEncoding` of two `uint8`, which holds at most 255 entries. A list longer than the dimension type allows is refused by the flyweight on encode.

`blockLength` declares the entry's block larger than its fields need, as on a message, reserving room; the bytes past the fields are not written by either side and carry whatever the buffer held.

## Layout and unmapped fields

`layout` and `unmapped` on `@SbeGroup` work as on `@SbeMessage`, for the entry's body: `layout` names the entry's fields and groups in wire order, each component by its Java name and each unmapped field by its `name`, exactly once, and `unmapped` holds complete `@SbeField`s the entry record no longer carries, which the codec writes as their null value and never reads. `legRatio` above is one: retired in version 1, still on the wire.

## What a group costs

`encodedLength` counts every group without encoding: the dimensions, the entries times the block length, and inside each entry its own groups the same way. A message with a group is no longer a fixed size, so the length is the record's, not the schema's. `decodedLength` walks the groups on the wire without decoding them, through the flyweight's `sbeDecodedLength`.

## Absence and the empty list

A group has no presence: it cannot be optional, and a group present with zero entries is an empty list, never `null`. Encoding a `null` list is an `IllegalArgumentException`, `contributors is required`, from `encodedLength` and `encode` alike. A `null` entry inside the list is a `NullPointerException`, the record's own.

A group appended above the baseline, as `contributors` was in version 7, is absent from every earlier message: the codec decodes it to `null`, decided on the acting version, and the component is nullable. A current message without contributors carries the dimensions with a count of zero and decodes to an empty list; the two are different messages and different values.

Inside a group the baseline is the group's own `sinceVersion` where that is higher than the schema's: an entry exists only in a message that carries the group, so a field at its group's version is never absent and stays a plain primitive. A field added in a later version than its group would be absent in entries an older writer wrote, and the compiler asks for a box; the codec for it is a later increment's.

An older reader that does not know the group reads the message's block and stops before it: it cannot skip a group it has no schema for, which is SBE's limit, not sbe-buddy's. A newer reader reading an older message skips nothing, since there is nothing there.

## What the compiler and the codec refuse

* `@SbeGroup` on anything but a `List` of a record: `a group must be a List of a record`.
* A group before a field, or after var-data, in the same body.
* A field appended above its group's version on a primitive component: `int cannot hold null, but the field can be absent; use Integer`.
* Until a later increment, the codec refuses, naming the message: a field or a group added above the baseline inside a group, and var-data, in a message or in a group at any depth. `codecs = false` on `@SbeSchema` keeps the flyweights.

## Coverage

This page covers groups of fields and groups. Var-data inside an entry is the variable data page's; a group under `unmapped`, retiring a whole group from the record, is planned.
