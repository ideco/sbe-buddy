package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.INT64;
import static net.concini.sbebuddy.PrimitiveType.INT8;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

/** A price as a mantissa and the power of ten it is scaled by. */
@SbeComposite(semanticType = "Price", description = "A decimal price as mantissa and exponent")
public record Price(
		@SbeType(primitiveType = INT64) long mantissa,
		@SbeType(primitiveType = INT8) byte exponent
) {
}
