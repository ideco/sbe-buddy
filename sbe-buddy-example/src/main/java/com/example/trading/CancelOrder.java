package com.example.trading;

import java.time.Instant;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

/** Cancel an order already sent. */
@SbeMessage(id = 3, semanticType = "F", description = "Cancel an order already sent")
public record CancelOrder(
		@SbeField(id = 41, type = ClOrdId.class) String origClOrdId,
		@SbeField(id = 11, type = ClOrdId.class) String clOrdId,
		@SbeField(id = 55, type = Symbol.class) String symbol,
		@SbeField(id = 54) Side side,
		@SbeField(id = 60, type = UtcTimestamp.class, binding = UtcTimestampBinding.class) Instant transactTime
) implements OrderEntry {
}
