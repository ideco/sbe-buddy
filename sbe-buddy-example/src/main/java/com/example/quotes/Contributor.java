package com.example.quotes;

import static net.concini.sbebuddy.PrimitiveType.INT64;
import static net.concini.sbebuddy.PrimitiveType.UINT32;

import java.math.BigDecimal;

import net.concini.sbebuddy.SbeField;

/**
 * One venue's own quote inside a top-of-book update: an entry of the
 * {@code contributors} group, its prices through the {@link Price} binding as
 * the message's own are.
 */
public record Contributor(
		@SbeField(id = 17, description = "The venue quoting") Venue venue,
		@SbeField(id = 18, primitiveType = INT64, binding = Price.class) BigDecimal bid,
		@SbeField(id = 19, primitiveType = INT64, binding = Price.class) BigDecimal ask,
		@SbeField(id = 20, primitiveType = UINT32) long bidSize,
		@SbeField(id = 21, primitiveType = UINT32) long askSize
) {
}
