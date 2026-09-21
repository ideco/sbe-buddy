package com.example.trading;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

/** Cancel an order already sent. */
@SbeMessage(id = 2, semanticType = "F", description = "Cancel an order already sent")
public record CancelOrder(
		@SbeField(id = 1) long orderId,
		@SbeField(id = 2, type = Symbol.class) String symbol
) {
}
