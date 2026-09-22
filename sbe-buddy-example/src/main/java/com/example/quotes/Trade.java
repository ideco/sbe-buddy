package com.example.quotes;

import static net.concini.sbebuddy.PrimitiveType.INT64;
import static net.concini.sbebuddy.PrimitiveType.UINT32;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

/**
 * A trade as its price, a mantissa scaled by the schema's constant exponent,
 * and its size: a composite, declared once and held by the record as it is.
 */
@SbeComposite(description = "A trade as its price and its size")
public record Trade(
		@SbeType(primitiveType = INT64) long price,
		@SbeType(primitiveType = UINT32) long size
) {
}
