# Encode and read multiple messages

Use a generated codec to size messages, write them into a buffer, and
decode them into records. A message codec handles one record type. A union
codec handles several message types from the same schema.

This guide assumes the Maven setup from the
[first-message tutorial](../tutorials/getting-started.md). It builds a batch
of two known messages using one schema version, with some space before
the first message to demonstrate offsets. The receiving section explains
what to do when messages arrive in transport frames instead.

## 1. Declare the messages you want to handle together

Create `src/main/java/com/example/routing/package-info.java`:

```java
@SbeSchema(id = 101, version = 0)
package com.example.routing;

import net.concini.sbebuddy.SbeSchema;
```

Create `src/main/java/com/example/routing/OrderCommand.java`:

```java
package com.example.routing;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;
import net.concini.sbebuddy.SbeUnion;

@SbeUnion
public sealed interface OrderCommand {
    @SbeMessage(id = 1)
    record SubmitOrder(
            @SbeField(id = 1) long orderId,
            @SbeField(id = 2) int quantity) implements OrderCommand {}

    @SbeMessage(id = 2)
    record CancelOrder(
            @SbeField(id = 1) long orderId) implements OrderCommand {}
}
```

The union adds no bytes or declaration to the SBE schema. Each message has
its own template ID. `OrderCommandCodec` selects a record type from that ID
when decoding. The individual `SubmitOrderCodec` and `CancelOrderCodec`
are also generated.

## 2. Size, encode and read the batch

Create `src/main/java/com/example/routing/Example.java`:

```java
package com.example.routing;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;

import com.example.routing.OrderCommand.CancelOrder;
import com.example.routing.OrderCommand.SubmitOrder;

public final class Example {
    public static void main(String[] args) {
        List<OrderCommand> commands = List.of(
                new SubmitOrder(42L, 100),
                new CancelOrder(42L));
        var codec = new OrderCommandCodec();
        int startOffset = 16;

        int capacity = startOffset;
        for (OrderCommand command : commands) {
            capacity += codec.encodedLength(command);
        }
        var buffer = new UnsafeBuffer(new byte[capacity]);

        int writeOffset = startOffset;
        for (OrderCommand command : commands) {
            writeOffset += codec.encode(command, buffer, writeOffset);
        }
        System.out.println("Bytes written: " + (writeOffset - startOffset));

        int readOffset = startOffset;
        while (readOffset < writeOffset) {
            OrderCommand command = codec.decode(buffer, readOffset);
            readOffset += codec.lastDecodedLength();
            switch (command) {
                case SubmitOrder order -> System.out.println(
                        "Submit " + order.orderId() + ", quantity " + order.quantity());
                case CancelOrder cancel -> System.out.println(
                        "Cancel " + cancel.orderId());
            }
        }
    }
}
```

`encodedLength` includes the header and all variable-length parts. Keep the
values unchanged between sizing and encoding. The buffer needs room for
both the starting offset and the encoded bytes. On success, `encode`
returns the number of bytes written, not the next offset.

The decode loop advances by `lastDecodedLength` immediately after each
successful decode. This is appropriate here because the reader knows the
complete layout of both messages. The switch is exhaustive; adding another
message to the union requires updating it.

## 3. Run the application

In the tutorial's `pom.xml`, change the Exec plugin's main-class argument
from `com.example.trading.Example` to `com.example.routing.Example`. Keep
the runtime `--add-opens` argument for Agrona. Run:

```sh
mvn clean compile exec:exec
```

Alongside Maven's output, you should see:

```text
Bytes written: 36
Submit 42, quantity 100
Cancel 42
```

The submit message occupies 20 bytes and the cancel message 16, each
including its own header. The 16 bytes reserved before them are outside
both messages.

For one message type, use its message codec with the same sizing, offset
and encode/decode methods. Reuse codec instances on one thread, such as
one instance per worker. Instances reuse mutable flyweights and must not be
shared between threads.

## 4. Respect message boundaries when receiving data

When a transport supplies a frame length, use that length to locate the
next frame. The standard SBE header carries the fixed block length, not
the complete message length. An older codec can also report a consumed
length that excludes newer groups or data it does not know. Do not use
that length to step through arbitrary newer messages.

Wait for the complete frame before decoding and present only its message
bytes to the codec, for example through a buffer view bounded to the frame.
Use offset 0 for such a view, or the actual header offset in a larger
buffer. The codec is not a transport framing parser.

For bytes using this schema's header layout, `canDecode` checks the schema
ID, supported template IDs and baseline version. It reads only the header:
true does not prove that the body is complete or its values can be decoded.
Handle unsupported messages using your transport's frame boundaries; the
codec cannot determine the length of an arbitrary unknown template.

`decodeHeader` lets you inspect the header separately, including custom
header members, but does not validate its identity. `decodedLength` can
measure known message layouts without constructing records; it is not a
validation step either. The `Codec` API Javadoc describes these methods'
exact contracts.

## 5. Publish only successful encodings

Sizing does not validate every value. Encoding can still reject a null
required component, an invalid constant or another value the schema cannot
represent. A failed encode may leave the buffer partially written, so only
publish or send the message after encoding succeeds.

Buffer and binding exceptions propagate to the caller. After a failed
decode, do not use `lastDecodedLength` to decide where to resume; recover
using the transport's framing.

See [How an SBE message is laid out](../concepts/message-layout.md) for the
lengths involved, and [Schema versions and absence](../concepts/schema-versions-and-absence.md)
for the effects of reading another schema version.
