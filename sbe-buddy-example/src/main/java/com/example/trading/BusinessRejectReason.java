package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;

/** Why a message was refused. */
@SbeEnum(primitiveType = UINT8)
public enum BusinessRejectReason {

	@SbeEnumValue("0")
	OTHER,

	@SbeEnumValue("1")
	UNKNOWN_ID,

	@SbeEnumValue("6")
	NOT_AUTHORIZED
}
