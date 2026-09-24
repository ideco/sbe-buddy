package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

/** Why a cancel was refused. */
@SbeEnum(primitiveType = UINT8)
public enum CxlRejReason {

	@SbeEnumValue("0")
	TOO_LATE,

	@SbeEnumValue("1")
	UNKNOWN_ORDER,

	@SbeEnumValue("99")
	OTHER
}
