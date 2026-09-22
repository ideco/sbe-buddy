package com.example.quotes;

import static net.concini.sbebuddy.Presence.CONSTANT;
import static net.concini.sbebuddy.PrimitiveType.INT8;

import net.concini.sbebuddy.SbeType;

/**
 * The power of ten every price is scaled by, a constant of the schema: it takes
 * no bytes, and a reader of any version sees it.
 */
@SbeType(primitiveType = INT8, presence = CONSTANT, value = "-4", description = "The power of ten every price is scaled by")
public final class PriceExponent {

	private PriceExponent() {
	}
}
