package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

/** Whether a fill added liquidity to the book or took it. */
@SbeEnum(primitiveType = UINT8)
public enum LastLiquidity {

	@SbeEnumValue("1")
	ADDED,

	@SbeEnumValue("2")
	REMOVED
}
