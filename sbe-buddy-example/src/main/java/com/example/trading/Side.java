package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

/** Which side of the market an order takes. */
@SbeEnum(primitiveType = CHAR)
public enum Side {

	@SbeEnumValue("B")
	BUY,

	@SbeEnumValue("S")
	SELL
}
