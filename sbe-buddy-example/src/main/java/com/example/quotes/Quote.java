package com.example.quotes;

import static net.concini.sbebuddy.PrimitiveType.UINT32;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

/**
 * The best bid and offer of one instrument, prices as fixed-point mantissas.
 */
@SbeMessage(id = 1, description = "The best bid and offer of one instrument")
public record Quote(
		@SbeField(id = 1) long instrumentId,
		@SbeField(id = 2) long bid,
		@SbeField(id = 3) long ask,
		@SbeField(id = 4, primitiveType = UINT32) long bidSize,
		@SbeField(id = 5, primitiveType = UINT32) long askSize
) {
}
