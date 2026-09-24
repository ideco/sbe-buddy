package com.example.trading;

import static net.concini.sbebuddy.Presence.CONSTANT;
import static net.concini.sbebuddy.PrimitiveType.INT32;
import static net.concini.sbebuddy.PrimitiveType.INT8;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

/** A quantity in whole units, a decimal whose exponent is a constant zero. */
@SbeComposite(semanticType = "Qty", description = "A quantity in whole units")
public record QtyEncoding(
		@SbeType(primitiveType = INT32) int mantissa,
		@SbeType(primitiveType = INT8, presence = CONSTANT, value = "0") byte exponent
) {
}
