package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.UINT32;

import java.util.List;

import net.concini.sbebuddy.SbeData;
import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;
import net.concini.sbebuddy.VarStringEncoding;

/** A new single order, with a leg per instrument it trades. */
@SbeMessage(id = 1, semanticType = "D", description = "A new single order")
public record NewOrder(
		@SbeField(id = 1) long orderId,
		@SbeField(id = 2, type = Symbol.class) String symbol,
		@SbeField(id = 3) Side side,
		@SbeField(id = 4) Price price,
		@SbeField(id = 5, primitiveType = UINT32) long quantity,
		@SbeGroup(id = 6, description = "The legs of a multi-leg order") List<Leg> legs,
		@SbeData(id = 7, type = VarStringEncoding.class) String note
) {

	/** One instrument of a multi-leg order, and its share of the whole. */
	public record Leg(
			@SbeField(id = 1) long instrumentId,
			@SbeField(id = 2) int ratio
	) {
	}
}
