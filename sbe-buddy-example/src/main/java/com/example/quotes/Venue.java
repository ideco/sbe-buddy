package com.example.quotes;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;
import net.concini.sbebuddy.UnknownValue;

/**
 * Where a quote comes from. Venues are added faster than readers update, so a
 * venue this reader does not know decodes to {@link #OTHER} rather than failing
 * the quote.
 */
@SbeEnum(primitiveType = UINT8, description = "Where a quote comes from")
public enum Venue {

	@SbeEnumValue("1")
	XNAS,

	@SbeEnumValue("2")
	XNYS,

	@SbeEnumValue("3")
	XLON,

	@UnknownValue
	OTHER
}
