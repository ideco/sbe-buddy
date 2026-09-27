# Add a field while supporting older messages

Append a field to a message's fixed-length block when you need to extend
the message while continuing to read older versions. Keep the existing
fields in place and record when the new field was introduced.

This guide extends the `PlaceOrder` message from the
[first-message tutorial](../tutorials/getting-started.md). Version 0 has an
`orderId` and a `quantity`. Version 1 will add an optional `clientOrderId`.
Both versions use schema ID 100 and template ID 1.

Before changing the declarations, keep the version 0 schema and some
messages encoded with it for compatibility tests.

## 1. Raise the schema version

Update `src/main/java/com/example/trading/package-info.java`:

```java
@SbeSchema(id = 100, version = 1, baselineVersion = 0)
package com.example.trading;

import net.concini.sbebuddy.SbeSchema;
```

`version = 1` makes the generated codecs write version 1 messages.
`baselineVersion = 0` keeps version 0 messages readable. The baseline
defaults to 0; it is written out here to make the compatibility decision
explicit.

If you use [schema-first mode](map-an-existing-schema.md), keep `resource`
on the annotation and make the corresponding version and field changes in
the XML as well.

## 2. Append the field

Replace `src/main/java/com/example/trading/PlaceOrder.java` with:

```java
package com.example.trading;

import static net.concini.sbebuddy.Presence.OPTIONAL;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
public record PlaceOrder(
        @SbeField(id = 1) long orderId,
        @SbeField(id = 2) int quantity,
        @SbeField(id = 3, presence = OPTIONAL, sinceVersion = 1)
        Long clientOrderId) {}
```

Give the new field an unused ID and set `sinceVersion` to the version that
introduces it. Leave the existing fields' IDs, types and wire order
unchanged. Field IDs do not determine offsets: inserting the component
between existing components would change the layout.

For a message that already has groups or variable-length data, the new
field goes after the existing fixed-length fields and before those other
members. If the message declares an explicit `layout`, update that list to
put the field in this position.

Use `Long`, because the codec needs to represent absence as Java `null`.
Here, absence can mean either that a version 0 message has no such field,
or that a version 1 writer supplied no client order ID.

This example makes the field optional so new writers can also omit its
value. If new writers must always supply it, omit `presence = OPTIONAL`
but keep `Long` while version 0 remains supported. Decoding an old message
still produces `null`; encoding a new message with that required value
missing fails.

## 3. Update callers and build

Pass the new component when constructing a record. For example, change the
tutorial's `Example.java` to construct:

```java
var order = new PlaceOrder(42L, 100, 9001L);
```

Use `new PlaceOrder(42L, 100, null)` when the client order ID is unknown.
Then compile:

```sh
mvn clean compile
```

The new field in the generated schema is:

```xml
<field name="clientOrderId" id="3" type="int64" presence="optional" sinceVersion="1"/>
```

With the tutorial's example, `mvn exec:exec` now reports:

```text
Bytes written: 28
Decoded: PlaceOrder[orderId=42, quantity=100, clientOrderId=9001]
Equal: true
```

An absent optional value still occupies its eight-byte field. Encoding
`null` also produces a 28-byte message.

## 4. Check both reader versions

Run compatibility tests using codecs generated from both schema versions:

| Writer | Reader | Expected result |
| --- | --- | --- |
| Version 0 | Version 1 | Existing values are preserved; `clientOrderId` is `null`. |
| Version 1, with a client order ID | Version 0 | Existing values are preserved; the new field is skipped. |
| Version 1, with a client order ID | Version 1 | The client order ID is preserved. |
| Version 1, with a null client order ID | Version 1 | `clientOrderId` is `null`. |

An older reader uses the encoded block length to get past the appended
field. Also test any groups or variable-length data following your real
message's fixed block, to check that both readers reach them correctly.

Keep the baseline at 0 for as long as version 0 messages need to be read,
including messages in logs or queues. See
[Schema versions and absence](../concepts/schema-versions-and-absence.md)
for what changes when you raise it.
