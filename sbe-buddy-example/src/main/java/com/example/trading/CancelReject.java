package com.example.trading;

import net.concini.sbebuddy.SbeData;
import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

/** A cancel the venue refused, with its reason in ISO-8859-1 text. */
@SbeMessage(id = 5, semanticType = "9", description = "A cancel the venue refused")
public record CancelReject(
		@SbeField(id = 37, type = OrderId.class) String orderId,
		@SbeField(id = 11, type = ClOrdId.class) String clOrdId,
		@SbeField(id = 41, type = ClOrdId.class) String origClOrdId,
		@SbeField(id = 39) OrdStatus ordStatus,
		@SbeField(id = 102) CxlRejReason cxlRejReason,
		@SbeData(id = 58, type = VarLatin1Encoding.class) String text
) implements OrderEvent {
}
