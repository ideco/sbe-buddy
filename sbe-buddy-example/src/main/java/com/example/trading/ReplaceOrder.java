package com.example.trading;

import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.UINT8;

import java.math.BigDecimal;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

/**
 * Replace an order already sent. The venue's schema still has
 * {@code locateReqd}, which this client no longer carries: it is written as its
 * null value and never read.
 */
@SbeMessage(id = 2, semanticType = "G", description = "Replace an order already sent", layout = {"origClOrdId",
		"clOrdId", "symbol", "side", "ordType", "locateReqd", "orderQty", "price",
		"transactTime"}, unmapped = @SbeField(id = 114, name = "locateReqd", primitiveType = UINT8, description = "Kept by the venue, no longer carried by the record"))
public record ReplaceOrder(
		@SbeField(id = 41, type = ClOrdId.class) String origClOrdId,
		@SbeField(id = 11, type = ClOrdId.class) String clOrdId,
		@SbeField(id = 55, type = Symbol.class) String symbol,
		@SbeField(id = 54) Side side,
		@SbeField(id = 40) OrdType ordType,
		@SbeField(id = 38, type = QtyEncoding.class, binding = QtyBinding.class) long orderQty,
		@SbeField(id = 44, type = PriceEncoding.class, presence = OPTIONAL, binding = PriceBinding.class) @Nullable BigDecimal price,
		@SbeField(id = 60, type = UtcTimestamp.class, binding = UtcTimestampBinding.class) Instant transactTime
) implements OrderEntry {
}
