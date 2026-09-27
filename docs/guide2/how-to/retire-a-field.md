# Retire a field from a record

Use an `unmapped` declaration to remove a field from a Java record while
keeping its place in the SBE schema. The codec will skip it when decoding
and write its null value when encoding.

This guide retires the optional `clientOrderId` introduced in
[Add a field while supporting older messages](add-a-field.md). It remains
in the schema at version 2, but callers no longer supply it to `PlaceOrder`.

Before retiring it, check that consumers can accept messages without its
value. In this example, version 1 readers receive `null` because the field
is optional. Preserving the layout does not preserve the retired data.

## 1. Choose the retirement version

Update `src/main/java/com/example/trading/package-info.java`:

```java
@SbeSchema(id = 100, version = 2, baselineVersion = 0)
package com.example.trading;

import net.concini.sbebuddy.SbeSchema;
```

The baseline stays at 0, so the codec continues to read all three versions.
Retiring a component does not require dropping support for older messages.

## 2. Move the field to an unmapped declaration

Replace `src/main/java/com/example/trading/PlaceOrder.java` with:

```java
package com.example.trading;

import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.INT64;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(
        id = 1,
        layout = {"orderId", "quantity", "clientOrderId"},
        unmapped = @SbeField(
                id = 3,
                name = "clientOrderId",
                primitiveType = INT64,
                presence = OPTIONAL,
                sinceVersion = 1,
                deprecated = 2))
public record PlaceOrder(
        @SbeField(id = 1) long orderId,
        @SbeField(id = 2) int quantity) {}
```

Keep the field's original ID, type, presence and introduction version. Add
`deprecated = 2` to record when it was retired. For your own field, also
preserve any other schema attributes, such as its description or offset.

The unmapped declaration needs an explicit `name` and type because there
is no record component from which to obtain them. The `layout` lists every
component and unmapped field exactly once, in their existing wire order.
Do not reuse the retired field's ID or storage for a different field.

Setting `deprecated` alone would leave the component in the record and the
codec would continue to read and write it. Moving the declaration to
`unmapped` is what removes it from the Java value.

## 3. Update callers and the schema

Remove the retired argument wherever the record is constructed. In the
tutorial's `Example.java`, use:

```java
var order = new PlaceOrder(42L, 100);
```

In [schema-first mode](map-an-existing-schema.md), retain `resource` on
`@SbeSchema`, raise the XML schema version to 2, and add `deprecated="2"`
to the existing field. Keep the field itself and its position in the XML.

Compile the project:

```sh
mvn clean compile
```

The schema still contains:

```xml
<field name="clientOrderId" id="3" type="int64" presence="optional" sinceVersion="1" deprecated="2"/>
```

Running the tutorial's example with `mvn exec:exec` now prints:

```text
Bytes written: 28
Decoded: PlaceOrder[orderId=42, quantity=100]
Equal: true
```

The message still occupies 28 bytes. Its last eight bytes contain the
`int64` null sentinel instead of a client order ID.

## 4. Verify the behavior with older readers

| Writer | Reader | Expected result |
| --- | --- | --- |
| Version 0 | Version 2 | The order ID and quantity are preserved. |
| Version 1, with a client order ID | Version 2 | The order ID and quantity are preserved; the client order ID is discarded. |
| Version 2 | Version 1 | The order ID and quantity are preserved; `clientOrderId` is `null`. |
| Version 2 | Version 0 | The order ID and quantity are preserved. |

Decode a version 1 message with a real client order ID, then encode the
result with the version 2 codec. A version 1 reader should now receive
`null` for that field. This checks that your application can tolerate the
data loss if it forwards decoded messages.

The same approach needs extra care for a required field: older readers may
expose the written null sentinel as an ordinary Java value rather than
`null`. Decide how those consumers will handle it before retiring the field.

Groups and variable-length data cannot be declared unmapped. Record codecs
also do not support unmapped fields of a composite type. This procedure
applies to the optional scalar field shown here.
