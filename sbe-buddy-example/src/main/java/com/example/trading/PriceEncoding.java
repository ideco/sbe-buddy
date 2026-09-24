package com.example.trading;

import static net.concini.sbebuddy.Presence.CONSTANT;
import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.INT64;
import static net.concini.sbebuddy.PrimitiveType.INT8;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

/**
 * A price in ten-thousandths: the mantissa on the wire, the exponent a constant
 * of the schema. A null mantissa is a price that is not there, SBE's convention
 * for an optional composite, which {@link PriceBinding} applies.
 */
@SbeComposite(semanticType = "Price", description = "A price in ten-thousandths, null when the mantissa is")
public record PriceEncoding(
		@SbeType(primitiveType = INT64, presence = OPTIONAL) @Nullable Long mantissa,
		@SbeType(primitiveType = INT8, presence = CONSTANT, value = "-4") byte exponent
) {

	/** The constant exponent, for a record written in Java. */
	public static final byte EXPONENT = -4;
}
