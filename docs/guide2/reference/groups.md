# Repeating groups

`@SbeGroup` maps a repeating group to a `List` of entry records. Each entry
can contain fixed-length fields, nested groups and variable-length data.

## Declaration and schema

In a schema package, `BasketOrder.java` declares a list of legs followed
by a note:

```java
package com.example.trading;

import java.util.List;

import net.concini.sbebuddy.SbeData;
import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;
import net.concini.sbebuddy.VarStringEncoding;

@SbeMessage(id = 2)
public record BasketOrder(
        @SbeField(id = 1) long orderId,
        @SbeGroup(id = 2) List<Leg> legs,
        @SbeData(id = 5, type = VarStringEncoding.class) String note) {

    public record Leg(
            @SbeField(id = 3) long instrumentId,
            @SbeField(id = 4) int quantity) {}
}
```

The group appears inside the message in the XML:

```xml
<group name="legs" id="2">
    <field name="instrumentId" id="3" type="int64"/>
    <field name="quantity" id="4" type="int32"/>
</group>
```

## Java representation

Without a binding, the component must be declared as `List<E>`, where `E`
is an entry record. It can be nested in the message or declared separately.
The entry needs no `@SbeMessage`: its components describe the group's body.

Entry components use the same `@SbeField`, `@SbeGroup` and `@SbeData`
annotations as message components. A nested group contains a list of
another entry record. A [binding](bindings.md) can convert the list to an
application type such as a map.

## Dimensions and layout

The dimension header precedes all entries and records their count and
fixed block length. The default `GroupSizeEncoding` has two `uint16`
members, making the header four bytes. `dimensionType` selects a custom
composite; it must provide `blockLength` and `numInGroup`, each a `uint8`
or `uint16`.

In the example, each entry's fixed block is twelve bytes. A group's
`blockLength` excludes its dimension header, nested groups and
variable-length data. An explicit value can reserve trailing padding.

Fields precede nested groups, followed by data, within every entry.
`layout` on `@SbeGroup` selects the entry's wire order independently of
its record component order. `unmapped` retains fields without components,
using the same rules as on a message.

For an illustrated byte layout, see
[How an SBE message is laid out](../concepts/message-layout.md).

## Presence and validation

A group has no optional presence. An empty list encodes a dimension header
with count zero and decodes to an empty list. Encoding a null list throws
`IllegalArgumentException`; a null entry throws `NullPointerException`.
The list length must fit the dimension encoding's allowed count.

A group introduced after the baseline decodes to null from messages that
predate it. Fields introduced with the group are available whenever an
entry exists, so required numeric fields at that version can be primitives.
Fields added in a later version than the group need boxed components while
older entries are still accepted.

Appending fields to an entry and appending nested groups or data have
different effects on older readers. Check the
[evolution rules](../concepts/schema-versions-and-absence.md#other-constructs-have-different-limits)
before extending an entry.

The `SbeGroup` API Javadoc covers individual settings. The note in the
example is described under [Variable-length data](variable-length-data.md).
